package br.edu.faculdade.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/google-drive")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class GoogleDriveController {
    private static final String ESCOPO = "https://www.googleapis.com/auth/drive.metadata.readonly";
    private static final String ESCRITA = "https://www.googleapis.com/auth/drive.file";
    private static final String ARQUIVOS = "https://www.googleapis.com/drive/v3/files";
    private static final String MIME_PASTA = "application/vnd.google-apps.folder";
    private static final String PENDENTE = "googleDriveAutorizacao";
    private static final String CONEXAO = "googleDriveConexao";
    private static final String TELA = "http://localhost:4200/google-drive";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient google;
    @org.springframework.beans.factory.annotation.Autowired
    private CertificadoController certificadosSalvos;
    @org.springframework.beans.factory.annotation.Autowired
    private br.edu.faculdade.service.GoogleDriveTokenStore conexoesSalvas;
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private br.edu.faculdade.service.GoogleDrivePastasService pastasUsuarios;

    public GoogleDriveController(@Value("${google.drive.client-id:}") String clientId,
                                 @Value("${google.drive.client-secret:}") String clientSecret,
                                 @Value("${google.drive.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.google = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping("/status")
    public Status status(HttpServletRequest request) {
        var sessao = request.getSession(false);
        var tokens=recuperar(sessao);
        return new Status(configurado(), tokens!=null, tokens!=null&&permiteEscrita(tokens));
    }

    @GetMapping("/conectar")
    public ResponseEntity<Void> conectar(HttpServletRequest request) {
        if (!configurado()) return voltar("sem-configuracao");

        var state = UUID.randomUUID().toString();
        request.getSession().setAttribute(PENDENTE, new Autorizacao(state, Instant.now().plusSeconds(600)));
        var url = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", ESCOPO+" "+ESCRITA)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent select_account")
                .queryParam("state", state)
                .build().encode().toUri();
        return redirecionar(url);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request,
                                        @RequestParam(required = false) String state,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(required = false) String error) {
        var sessao = request.getSession(false);
        if (!consumirAutorizacao(sessao, state)) return voltar("sessao-expirada");
        if ("access_denied".equals(error)) return voltar("cancelado");
        if (error != null || code == null || code.isBlank()) return voltar("falha");

        var formulario = new LinkedMultiValueMap<String, String>();
        formulario.add("code", code);
        formulario.add("client_id", clientId);
        formulario.add("client_secret", clientSecret);
        formulario.add("redirect_uri", redirectUri);
        formulario.add("grant_type", "authorization_code");

        try {
            var tokens = google.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario).retrieve().body(Tokens.class);
            if (tokens == null || tokens.accessToken() == null || tokens.accessToken().isBlank()
                    || tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
                return voltar("falha");
            }
            if (tokens.scope() != null && !Arrays.asList(tokens.scope().split(" ")).contains(ESCOPO)) {
                return voltar("sem-permissao");
            }
            if(!(sessao.getAttribute("usuarioId") instanceof Long usuario))return voltar("sessao-expirada");
            conexoesSalvas.salvar(usuario,contexto(),tokens.refreshToken());
            sessao.setAttribute(CONEXAO, new Tokens(tokens.accessToken(), tokens.refreshToken(),
                    tokens.scope()==null?ESCOPO+" "+ESCRITA:tokens.scope()));
            pastasUsuarios.solicitar(usuario);
            return voltar("conectado");
        } catch (RestClientException e) {
            return voltar("falha");
        }
    }

    @GetMapping("/pastas/{id}/certificados")
    public PastaCertificados certificados(@PathVariable String id, HttpServletRequest request) {
        if (!id.matches("[A-Za-z0-9_-]{1,200}")) {
            throw new IllegalArgumentException("Link de pasta inválido.");
        }
        var sessao = request.getSession(false);
        var tokens=recuperar(sessao);
        if (tokens==null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            try {
                var pasta=lerPasta(id, tokens.accessToken());
                certificadosSalvos.registrar(pasta);
                return pasta;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() != 401) throw e;
                var atualizados = renovar(tokens);
                if (sessao.getAttribute(CONEXAO) == tokens) sessao.setAttribute(CONEXAO, atualizados);
                var pasta=lerPasta(id, atualizados.accessToken());
                certificadosSalvos.registrar(pasta);
                return pasta;
            }
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            if (codigo == 400 || codigo == 401) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            if (codigo == 403 || codigo == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            throw new IllegalStateException("O Google Drive não respondeu à consulta.");
        } catch (RestClientException e) {
            throw new IllegalStateException("Não foi possível consultar o Google Drive.");
        }
    }

    private PastaCertificados lerPasta(String id, String token) {
        return lerPasta(id, token, true);
    }

    private PastaCertificados lerPasta(String id, String token, boolean incluirFilhas) {
        var pasta = google.get().uri(ARQUIVOS + "/{id}?fields=id,name,mimeType,trashed&supportsAllDrives=true", id)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(ArquivoDrive.class);
        if (pasta == null || pasta.trashed() || !MIME_PASTA.equals(pasta.mimeType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var arquivos = new ArrayList<ArquivoDrive>();
        var paginasConsultadas = new HashSet<String>();
        String pagina = null;
        do {
            if (pagina != null && !paginasConsultadas.add(pagina)) {
                throw new IllegalStateException("O Google Drive repetiu uma página de resultados. Tente novamente.");
            }
            var url = UriComponentsBuilder.fromUriString(ARQUIVOS)
                    .queryParam("q", "{filtro}")
                    .queryParam("fields", "nextPageToken,files(id,name,mimeType,createdTime)")
                    .queryParam("supportsAllDrives", true)
                    .queryParam("includeItemsFromAllDrives", true)
                    .queryParam("pageSize", 100);
            if (pagina != null) url.queryParam("pageToken", "{pagina}");
            var uri = url.encode().buildAndExpand(pagina == null
                    ? new Object[]{filtroCertificados(id)} : new Object[]{filtroCertificados(id), pagina}).toUri();
            var resultado = google.get().uri(uri).headers(h -> h.setBearerAuth(token))
                    .retrieve().body(Pagina.class);
            if (resultado == null) throw new IllegalStateException("O Google Drive retornou uma lista vazia.");
            if (resultado.files() != null) arquivos.addAll(resultado.files());
            pagina = resultado.nextPageToken();
        } while (pagina != null && !pagina.isBlank());
        var filhas = new ArrayList<PastaCertificados>();
        if (incluirFilhas) {
            for (var arquivo : arquivos) {
                if (MIME_PASTA.equals(arquivo.mimeType())) filhas.add(lerPasta(arquivo.id(), token, false));
            }
        }
        return new PastaCertificados(pasta.id(), pasta.name(), arquivos.stream()
                .filter(a -> "application/pdf".equals(a.mimeType()))
                .map(a -> new Certificado(a.id(), a.name(), a.createdTime())).toList(), filhas);
    }

    private String filtroCertificados(String id) {
        return "'" + id + "' in parents and trashed = false and (mimeType = 'application/pdf' or mimeType = '" + MIME_PASTA + "')";
    }

    private boolean temEscopo(Tokens tokens) {
        return tokens.scope() != null && Arrays.asList(tokens.scope().split(" ")).contains(ESCOPO);
    }

    private Tokens renovar(Tokens tokens) {
        var formulario = new LinkedMultiValueMap<String, String>();
        formulario.add("client_id", clientId);
        formulario.add("client_secret", clientSecret);
        formulario.add("grant_type", "refresh_token");
        formulario.add("refresh_token", tokens.refreshToken());
        var resposta = google.post().uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulario).retrieve().body(Tokens.class);
        if (resposta == null || resposta.accessToken() == null || resposta.accessToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return new Tokens(resposta.accessToken(), tokens.refreshToken(),
                resposta.scope() == null ? tokens.scope() : resposta.scope());
    }

    @DeleteMapping("/conexao")
    public ResponseEntity<Void> desconectar(HttpServletRequest request) {
        var sessao = request.getSession(false);
        if (sessao != null) {
            if(sessao.getAttribute("usuarioId") instanceof Long usuario)conexoesSalvas.remover(usuario);
            sessao.removeAttribute(PENDENTE);
            sessao.removeAttribute(CONEXAO);
        }
        return ResponseEntity.noContent().build();
    }

    private String contexto(){return clientId+"|"+ESCOPO;}

    public void atualizarCompartilhado(long administrador,List<String> pastas){
        String refresh=conexoesSalvas.carregar(administrador,contexto());
        if(refresh==null)throw new IllegalStateException("Peça ao administrador para conectar o Google Drive.");
        try{
            var tokens=renovar(new Tokens("",refresh,ESCOPO));
            if(!temEscopo(tokens))throw new IllegalStateException("O administrador precisa autorizar novamente o Google Drive.");
            for(String id:pastas)certificadosSalvos.registrar(lerPasta(id,tokens.accessToken()));
        }catch(RestClientException e){
            throw new IllegalStateException("Não foi possível atualizar os certificados. O administrador deve conferir a conexão com o Google Drive.");
        }
    }

    private Tokens recuperar(HttpSession sessao){
        if(sessao==null||!configurado())return null;
        synchronized(sessao){
            if(sessao.getAttribute(CONEXAO) instanceof Tokens tokens&&temEscopo(tokens))return tokens;
            if(!(sessao.getAttribute("usuarioId") instanceof Long usuario))return null;
            String refresh=conexoesSalvas.carregar(usuario,contexto());
            if(refresh==null)return null;
            try {
                var tokens=renovar(new Tokens("",refresh,ESCOPO));
                if(!temEscopo(tokens))return null;
                sessao.setAttribute(CONEXAO,tokens);
                return tokens;
            }catch(RestClientResponseException e){
                if(e.getStatusCode().value()==400||e.getStatusCode().value()==401){
                    conexoesSalvas.remover(usuario);
                    return null;
                }
                throw new IllegalStateException("Não foi possível renovar a conexão com o Google Drive.");
            }catch(RestClientException e){
                throw new IllegalStateException("O Google está indisponível. Tente novamente.");
            }
        }
    }

    private boolean configurado() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    private boolean consumirAutorizacao(HttpSession sessao, String state) {
        if (sessao == null) return false;
        synchronized (sessao) {
            if (!(sessao.getAttribute(PENDENTE) instanceof Autorizacao pendente)
                    || !pendente.state().equals(state)) return false;
            sessao.removeAttribute(PENDENTE);
            return Instant.now().isBefore(pendente.expira());
        }
    }

    private ResponseEntity<Void> voltar(String resultado) {
        return redirecionar(URI.create(TELA + "?resultado=" + resultado));
    }

    private ResponseEntity<Void> redirecionar(URI url) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(url)
                .header("Cache-Control", "no-store").build();
    }

    public record Status(boolean configurado, boolean conectado, boolean escrita) {}
    @GetMapping("/pastas-usuarios")
    public br.edu.faculdade.service.GoogleDrivePastasService.Situacao situacaoPastas(){return pastasUsuarios.status();}
    @PostMapping("/pastas-usuarios")
    public br.edu.faculdade.service.GoogleDrivePastasService.Situacao prepararPastas(HttpServletRequest request){
        pastasUsuarios.solicitar((Long)request.getSession().getAttribute("usuarioId"));
        return pastasUsuarios.status();
    }
    private boolean permiteEscrita(Tokens tokens){
        return tokens.scope()!=null&&Arrays.asList(tokens.scope().split(" ")).contains(ESCRITA);
    }
    public record ConexaoEscrita(String token,String conta) {}
    public ConexaoEscrita abrirEscrita(long administrador){
        String refresh=conexoesSalvas.carregar(administrador,contexto());
        if(refresh==null)throw new IllegalStateException("Conecte a conta Google do administrador.");
        try{
            var tokens=renovar(new Tokens("",refresh,ESCOPO));
            if(!permiteEscrita(tokens))throw new IllegalStateException("Autorize a criação de pastas em Google Drive → Autorizar criação de pastas.");
            var conta=google.get().uri("https://www.googleapis.com/drive/v3/about?fields=user(permissionId)")
                    .headers(h->h.setBearerAuth(tokens.accessToken())).retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
            String id=conta==null?"":conta.at("/user/permissionId").asText();
            if(id.isBlank())throw new IllegalStateException("Não foi possível identificar a conta Google.");
            return new ConexaoEscrita(tokens.accessToken(),id);
        }catch(RestClientException e){throw new IllegalStateException("Não foi possível acessar o Drive. Confira a conexão do administrador.");}
    }
    public String reservarIdPasta(String token){
        var resultado=google.get().uri(ARQUIVOS+"/generateIds?count=1&space=drive&type=files")
                .headers(h->h.setBearerAuth(token)).retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
        String id=resultado==null?"":resultado.at("/ids/0").asText();
        if(id.isBlank())throw new IllegalStateException("O Drive não retornou o identificador da pasta.");
        return id;
    }
    public void garantirPasta(String token,String id,String nome,String pai){
        try{
            conferirPasta(token,id,pai);
            return;
        }catch(RestClientResponseException e){if(e.getStatusCode().value()!=404)throw e;}
        var dados=new java.util.HashMap<String,Object>();
        dados.put("id",id);dados.put("name",nome);dados.put("mimeType",MIME_PASTA);
        if(pai!=null)dados.put("parents",List.of(pai));
        try{
            google.post().uri(ARQUIVOS).headers(h->h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON).body(dados).retrieve().toBodilessEntity();
        }catch(RestClientResponseException e){
            if(e.getStatusCode().value()!=409)throw e;
        }
        conferirPasta(token,id,pai);
    }
    private void conferirPasta(String token,String id,String pai){
        var pasta=google.get().uri(ARQUIVOS+"/{id}?fields=mimeType,trashed,parents",id)
                .headers(h->h.setBearerAuth(token)).retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
        boolean paiCorreto=pai==null;
        if(pai!=null&&pasta!=null){
            paiCorreto=false;
            for(var parent:pasta.path("parents"))if(parent.asText().equals(pai))paiCorreto=true;
        }
        if(pasta==null||pasta.path("trashed").asBoolean()||!MIME_PASTA.equals(pasta.path("mimeType").asText())||!paiCorreto)
            throw new IllegalStateException("Uma pasta vinculada foi movida ou enviada à lixeira. Restaure-a no Drive.");
    }
    public void enviarArquivo(String token,String id,String nome,String pai,java.nio.file.Path arquivo) throws java.io.IOException{
        boolean existente;
        try{
            google.get().uri(ARQUIVOS+"/{id}?fields=id",id).headers(h->h.setBearerAuth(token)).retrieve().toBodilessEntity();
            existente=true;
        }catch(RestClientResponseException e){if(e.getStatusCode().value()!=404)throw e;existente=false;}
        try{
            upload(token,id,nome,pai,arquivo,existente);
        }catch(RestClientResponseException e){
            if(existente||e.getStatusCode().value()!=409)throw e;
            upload(token,id,nome,pai,arquivo,true);
        }
    }
    private void upload(String token,String id,String nome,String pai,java.nio.file.Path arquivo,boolean atualizar) throws java.io.IOException{
        String boundary="gestao-"+UUID.randomUUID();
        var metadata=new java.util.HashMap<String,Object>();
        metadata.put("name",nome);
        if(!atualizar){metadata.put("id",id);metadata.put("parents",List.of(pai));}
        String mime=java.net.URLConnection.guessContentTypeFromName(nome);
        if(mime==null)mime="application/octet-stream";
        metadata.put("mimeType",mime);
        byte[] inicio=("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                +new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata)
                +"\r\n--"+boundary+"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] fim=("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url="https://www.googleapis.com/upload/drive/v3/files"+(atualizar?"/"+id:"")+"?uploadType=multipart";
        google.method(atualizar?org.springframework.http.HttpMethod.PATCH:org.springframework.http.HttpMethod.POST)
                .uri(url).headers(h->h.setBearerAuth(token))
                .contentType(MediaType.parseMediaType("multipart/related; boundary="+boundary))
                .body((org.springframework.http.StreamingHttpOutputMessage.Body) out->{
                    out.write(inicio);java.nio.file.Files.copy(arquivo,out);out.write(fim);
                }).retrieve().toBodilessEntity();
    }
    public void lixeira(String token,String id){
        try{
            google.patch().uri(ARQUIVOS+"/{id}",id).headers(h->h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON).body(java.util.Map.of("trashed",true)).retrieve().toBodilessEntity();
        }catch(RestClientResponseException e){if(e.getStatusCode().value()!=404)throw e;}
    }
    public record PastaCertificados(String id, String nome, List<Certificado> certificados, List<PastaCertificados> filhas) {}
    public record Certificado(String id, String nome, String enviadoEm) {}
    private record ArquivoDrive(String id, String name, String mimeType, boolean trashed, String createdTime) {}
    private record Pagina(String nextPageToken, List<ArquivoDrive> files) {}
    private record Autorizacao(String state, Instant expira) {}
    private record Tokens(@JsonProperty("access_token") String accessToken,
                          @JsonProperty("refresh_token") String refreshToken,
                          String scope) {}
}
