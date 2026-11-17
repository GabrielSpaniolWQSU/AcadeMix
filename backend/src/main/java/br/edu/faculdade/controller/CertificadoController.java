package br.edu.faculdade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import br.edu.faculdade.controller.GoogleDriveController.PastaCertificados;

@RestController
@RequestMapping("/api/certificados")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true")
public class CertificadoController {
 @org.springframework.beans.factory.annotation.Autowired private AuthController auth;
 @org.springframework.beans.factory.annotation.Autowired private br.edu.faculdade.service.RelatorioHorasService relatorios;
 private final ObjectMapper json;
 private final Path arquivo;
 private Dados dados=new Dados(new LinkedHashMap<>(),new HashMap<>());
 private String pastaFixa="";
 @org.springframework.beans.factory.annotation.Autowired
 public synchronized void configurarPastaFixa(@org.springframework.beans.factory.annotation.Value("${google.drive.certificados-pasta-id:1RrceKdLCZYoy6wiOWSj2J6pplGrlrlje}") String id){
  if(id==null||!id.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Pasta de certificados inválida.");
  pastaFixa=id;
  if(!dados.pastas().containsKey(id)){
   var pastas=new LinkedHashMap<>(dados.pastas());
   pastas.put(id,new PastaCertificados(id,"Certificados",List.of(),List.of()));
   salvar(new Dados(pastas,dados.avaliacoes()));
  }
 }
 public record Avaliacao(String status,Double horasAprovadas,String observacao,String aprovadoEm) {
  public Avaliacao(String status,Double horasAprovadas,String observacao){this(status,horasAprovadas,observacao,null);}
 }
 public record Dados(Map<String,PastaCertificados> pastas,Map<String,Avaliacao> avaliacoes) {}
 private br.edu.faculdade.service.BancoProjeto banco;
 @org.springframework.beans.factory.annotation.Autowired
 public CertificadoController(ObjectMapper json,org.springframework.beans.factory.ObjectProvider<br.edu.faculdade.service.BancoProjeto> provider){
  this(json);
  banco=provider.getIfAvailable();
  if(banco!=null){banco.importar("certificados-v1",()->banco.certificados(dados));dados=banco.certificados();}
 }
 public CertificadoController(ObjectMapper json){this(json,Path.of("data","certificados.json"));}
 public CertificadoController(ObjectMapper json,Path arquivo){
  this.json=json;
  this.arquivo=arquivo;
  if(Files.exists(arquivo))try{dados=json.readValue(arquivo.toFile(),Dados.class);}
  catch(IOException e){throw new IllegalStateException("Não foi possível carregar os certificados.",e);}
 }
 @GetMapping public synchronized Dados listar(){return dados;}
 @GetMapping("/origem") public Map<String,String> origem(){return Map.of("id",pastaFixa,"url","https://drive.google.com/drive/folders/"+pastaFixa);}
 @GetMapping("/relatorio") public synchronized org.springframework.http.ResponseEntity<byte[]> relatorio(jakarta.servlet.http.HttpServletRequest request,
   @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate inicio,
   @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fim){
  if(auth.exigirUsuario(request).getPerfil()!=br.edu.faculdade.model.Perfil.ADMIN)
   throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
  byte[] arquivo=relatorios.gerar(dados,inicio,fim);
  return org.springframework.http.ResponseEntity.ok()
   .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
   .header("Content-Disposition","attachment; filename=\"horas-aprovadas.xlsx\"")
   .body(arquivo);
 }
 public synchronized void registrar(PastaCertificados pasta){
  var pastas=new LinkedHashMap<>(dados.pastas());pastas.put(pasta.id(),pasta);
  salvar(new Dados(pastas,dados.avaliacoes()));
 }
 @DeleteMapping("/pastas/{id}") public synchronized void remover(@PathVariable String id){
  if(id.equals(pastaFixa))throw new IllegalArgumentException("A pasta principal dos certificados é fixa e não pode ser desvinculada.");
  var pastas=new LinkedHashMap<>(dados.pastas());pastas.remove(id);
  salvar(new Dados(pastas,dados.avaliacoes()));
 }
 @PutMapping("/{id}/avaliacao") public synchronized Avaliacao avaliar(@PathVariable String id,@RequestBody Avaliacao avaliacao,jakarta.servlet.http.HttpServletRequest request){
  if(auth.exigirUsuario(request).getPerfil()!=br.edu.faculdade.model.Perfil.ADMIN&&!"APROVADO".equals(avaliacao.status()))
   throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
  if(dados.pastas().values().stream().noneMatch(p->contem(p,id)))throw new NoSuchElementException("Certificado não encontrado.");
  if(!Set.of("APROVADO","REPROVADO").contains(avaliacao.status()==null?"":avaliacao.status()))
   throw new IllegalArgumentException("Status inválido.");
  if(avaliacao.status().equals("APROVADO")&&(avaliacao.horasAprovadas()==null
     ||!Double.isFinite(avaliacao.horasAprovadas())||avaliacao.horasAprovadas()<0))
   throw new IllegalArgumentException("Informe uma quantidade válida de horas.");
  if(avaliacao.status().equals("REPROVADO")&&(avaliacao.observacao()==null||avaliacao.observacao().isBlank()))
   throw new IllegalArgumentException("Informe o motivo da reprovação.");
  var anterior=dados.avaliacoes().get(id);
  String data=anterior!=null&&"APROVADO".equals(anterior.status())&&Objects.equals(anterior.horasAprovadas(),avaliacao.horasAprovadas())&&anterior.aprovadoEm()!=null
   ?anterior.aprovadoEm():java.time.Instant.now().toString();
  var valor=avaliacao.status().equals("APROVADO")?new Avaliacao("APROVADO",avaliacao.horasAprovadas(),null,data)
    :new Avaliacao("REPROVADO",null,avaliacao.observacao().trim());
  var avaliacoes=new HashMap<>(dados.avaliacoes());avaliacoes.put(id,valor);
  salvar(new Dados(dados.pastas(),avaliacoes));return valor;
 }
 private boolean contem(PastaCertificados pasta,String id){
  return pasta.certificados().stream().anyMatch(c->id.equals(c.id()))
    ||pasta.filhas().stream().anyMatch(p->contem(p,id));
 }
 private void salvar(Dados novos){
  if(banco!=null){banco.certificados(novos);dados=novos;return;}
  try{
   Files.createDirectories(arquivo.getParent());
   var temp=arquivo.resolveSibling("certificados.tmp");
   json.writeValue(temp.toFile(),novos);
   Files.move(temp,arquivo,StandardCopyOption.REPLACE_EXISTING);
   dados=novos;
  }catch(IOException e){throw new IllegalStateException("Não foi possível salvar os certificados.",e);}
 }
}
