package br.edu.faculdade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.security.*;

@Service
public class AtaAnexoStorageService {
    private final ObjectMapper json;
    private final Path raiz=Path.of("data","atas-anexos");
    public record Anexo(long id,String nome,String hash,long tamanho) {}
    public AtaAnexoStorageService(ObjectMapper json){this.json=json;}
    private BancoProjeto banco;
    @org.springframework.beans.factory.annotation.Autowired
    public AtaAnexoStorageService(ObjectMapper json,org.springframework.beans.factory.ObjectProvider<BancoProjeto> provider){
        this(json);banco=provider.getIfAvailable();
    }
    public synchronized void salvar(long usuario,long id,MultipartFile arquivo) throws IOException {
        if(id<=0||arquivo.getOriginalFilename()==null||arquivo.getOriginalFilename().isBlank())
            throw new IllegalArgumentException("Anexo inválido.");
        if(arquivo.getSize()>100L*1024*1024)throw new IllegalArgumentException("O arquivo excede 100 MB.");
        var pasta=raiz.resolve(Long.toString(usuario));
        Files.createDirectories(pasta);
        var temporario=Files.createTempFile(pasta,"anexo-",".tmp");
        try{
            try(var entrada=arquivo.getInputStream()){Files.copy(entrada,temporario,StandardCopyOption.REPLACE_EXISTING);}
            String hash=hash(temporario);
            var destino=pasta.resolve(hash+".bin");
            if(!Files.exists(destino))Files.move(temporario,destino);
            var registro=new Anexo(id,arquivo.getOriginalFilename(),hash,arquivo.getSize());
            if(banco!=null){listar(usuario);banco.anexo(usuario,registro);return;}
            var meta=pasta.resolve(id+".tmp");
            json.writeValue(meta.toFile(),registro);
            Files.move(meta,pasta.resolve(id+".json"),StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporario);}
    }
    public synchronized List<Anexo> listar(long usuario) throws IOException {
        if(banco!=null){
            banco.importar("anexos-"+usuario+"-v1",()->{
                try{for(var anexo:listarLocais(usuario))banco.anexo(usuario,anexo);}
                catch(IOException e){throw new IllegalStateException("Não foi possível importar os anexos.",e);}
            });
            return banco.anexos(usuario);
        }
        return listarLocais(usuario);
    }
    private List<Anexo> listarLocais(long usuario) throws IOException {
        var pasta=raiz.resolve(Long.toString(usuario));
        if(!Files.exists(pasta))return List.of();
        var resultado=new ArrayList<Anexo>();
        try(var arquivos=Files.list(pasta)){
            for(var arquivo:arquivos.filter(p->p.toString().endsWith(".json")).toList())
                resultado.add(json.readValue(arquivo.toFile(),Anexo.class));
        }
        return resultado;
    }
    public synchronized Path conteudo(long usuario,Anexo anexo){return raiz.resolve(Long.toString(usuario)).resolve(anexo.hash()+".bin");}
    public static String hash(Path arquivo) throws IOException{
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            try(var entrada=Files.newInputStream(arquivo)){
                byte[] buffer=new byte[65536];int n;
                while((n=entrada.read(buffer))!=-1)digest.update(buffer,0,n);
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
