package br.edu.faculdade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class DocumentoStorageService {
    private final ObjectMapper json;
    private final Path raiz;
    @org.springframework.beans.factory.annotation.Autowired
    public DocumentoStorageService(ObjectMapper json,org.springframework.beans.factory.ObjectProvider<BancoProjeto> provider) {
        this(json,Path.of("data","documentos"));banco=provider.getIfAvailable();
    }
    public DocumentoStorageService(ObjectMapper json) {this(json,Path.of("data","documentos"));}
    private BancoProjeto banco;
    public DocumentoStorageService(ObjectMapper json,Path raiz) { this.json=json;this.raiz=raiz; }
    public record Item(String id,String nome,String paiId,boolean pasta,long tamanho) {}
    public record Lista(List<Item> pastas,List<Item> arquivos) {}
    public synchronized List<Item> todos(long usuario) throws IOException {return List.copyOf(itens(usuario));}
    public synchronized Path conteudo(long usuario,String id) throws IOException {
        if(obter(usuario,id).pasta())throw ausente();
        return pastaUsuario(usuario).resolve(id+".bin");
    }
    public synchronized Lista listar(long usuario,String paiId) throws IOException {
        validarPai(usuario,paiId);
        var itens=itens(usuario).stream().filter(i->Objects.equals(i.paiId(),paiId))
                .sorted(Comparator.comparing(Item::nome,String.CASE_INSENSITIVE_ORDER)).toList();
        return new Lista(itens.stream().filter(Item::pasta).toList(),itens.stream().filter(i->!i.pasta()).toList());
    }
    public synchronized Item criarPasta(long usuario,String nome,String paiId) throws IOException {
        validarPai(usuario,paiId);
        var item=new Item(UUID.randomUUID().toString(),nomeValido(nome),paiId,true,0);
        salvar(usuario,item);
        return item;
    }
    public synchronized Item enviar(long usuario,MultipartFile arquivo,String paiId) throws IOException {
        validarPai(usuario,paiId);
        if(arquivo.getSize()>100L*1024*1024)throw new IllegalArgumentException("O arquivo excede 100 MB.");
        var item=new Item(UUID.randomUUID().toString(),nomeValido(arquivo.getOriginalFilename()),paiId,false,arquivo.getSize());
        Files.createDirectories(pastaUsuario(usuario));
        var destino=pastaUsuario(usuario).resolve(item.id()+".bin");
        try {
            try(var entrada=arquivo.getInputStream()) { Files.copy(entrada,destino); }
            salvar(usuario,item);
        } catch(IOException|RuntimeException e) { Files.deleteIfExists(destino);throw e; }
        return item;
    }
    public synchronized Item obter(long usuario,String id) throws IOException {
        if(id==null||!id.matches("[a-f0-9-]{36}"))throw ausente();
        if(banco!=null)return itens(usuario).stream().filter(i->id.equals(i.id())).findFirst().orElseThrow(this::ausente);
        var caminho=pastaUsuario(usuario).resolve(id+".json");
        if(!Files.isRegularFile(caminho))throw ausente();
        return json.readValue(caminho.toFile(),Item.class);
    }
    public synchronized byte[] baixar(long usuario,String id) throws IOException {
        var item=obter(usuario,id);
        if(item.pasta())throw ausente();
        return Files.readAllBytes(pastaUsuario(usuario).resolve(id+".bin"));
    }
    public synchronized void excluir(long usuario,String id) throws IOException {
        var item=obter(usuario,id);
        var arvore=new ArrayList<Item>();
        arvore.add(item);
        if(item.pasta()){
            var filhos=new HashMap<String,List<Item>>();
            for(var atual:itens(usuario))if(atual.paiId()!=null)
                filhos.computeIfAbsent(atual.paiId(),chave->new ArrayList<>()).add(atual);
            var visitados=new HashSet<String>();visitados.add(id);
            for(int i=0;i<arvore.size();i++){
                var atual=arvore.get(i);
                if(atual.pasta())for(var filho:filhos.getOrDefault(atual.id(),List.of()))
                    if(visitados.add(filho.id()))arvore.add(obter(usuario,filho.id()));
            }
        }
        // Remove os filhos antes dos pais, respeitando os vínculos do banco.
        for(int i=arvore.size()-1;i>=0;i--){
            String alvo=arvore.get(i).id();
            if(banco!=null)banco.excluirDocumento(usuario,alvo);
            else Files.delete(pastaUsuario(usuario).resolve(alvo+".json"));
            Files.deleteIfExists(pastaUsuario(usuario).resolve(alvo+".bin"));
        }
    }
    private List<Item> itens(long usuario) throws IOException {
        if(banco!=null){
            banco.importar("documentos-"+usuario+"-v1",()->{
                try{
                    var pendentes=new ArrayList<>(itensLocais(usuario));
                    var importados=new HashSet<String>();
                    while(!pendentes.isEmpty()){
                        int antes=pendentes.size();
                        var iterador=pendentes.iterator();
                        while(iterador.hasNext()){
                            var item=iterador.next();
                            if(item.paiId()==null||importados.contains(item.paiId())){
                                banco.documento(usuario,item);importados.add(item.id());iterador.remove();
                            }
                        }
                        if(antes==pendentes.size())throw new IllegalStateException("Pastas antigas com vínculo inválido.");
                    }
                }catch(IOException e){throw new IllegalStateException("Não foi possível importar os documentos.",e);}
            });
            return banco.documentos(usuario);
        }
        return itensLocais(usuario);
    }
    private List<Item> itensLocais(long usuario) throws IOException {
        var pasta=pastaUsuario(usuario);
        if(!Files.exists(pasta))return List.of();
        var itens=new ArrayList<Item>();
        try(var caminhos=Files.list(pasta)) {
            for(var caminho:caminhos.filter(p->p.toString().endsWith(".json")).toList())
                itens.add(json.readValue(caminho.toFile(),Item.class));
        }
        return itens;
    }
    private Path pastaUsuario(long usuario){return raiz.resolve(Long.toString(usuario));}
    private void validarPai(long usuario,String paiId) throws IOException {
        if(paiId!=null&&!obter(usuario,paiId).pasta())throw ausente();
    }
    private void salvar(long usuario,Item item) throws IOException {
        if(banco!=null){itens(usuario);banco.documento(usuario,item);return;}
        var pasta=pastaUsuario(usuario);
        Files.createDirectories(pasta);
        var temporario=pasta.resolve(item.id()+".tmp");
        try {
            json.writeValue(temporario.toFile(),item);
            Files.move(temporario,pasta.resolve(item.id()+".json"));
        } finally {Files.deleteIfExists(temporario);}
    }
    private String nomeValido(String nome) {
        if(nome==null||nome.isBlank()||nome.length()>255||nome.chars().anyMatch(c->c<32))
            throw new IllegalArgumentException("Informe um nome válido com até 255 caracteres.");
        return nome.trim();
    }
    private ResponseStatusException ausente(){return new ResponseStatusException(HttpStatus.NOT_FOUND);}
}
