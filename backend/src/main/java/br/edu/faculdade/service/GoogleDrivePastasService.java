package br.edu.faculdade.service;

import br.edu.faculdade.controller.GoogleDriveController;
import br.edu.faculdade.model.Perfil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

@Service
public class GoogleDrivePastasService {
    private final GoogleDriveController drive;
    private final UsuarioService usuarios;
    private final GoogleDriveTokenStore conexoes;
    private final ObjectMapper json;
    private final Path arquivo;
    private Map<String,Map<String,Pasta>> contas=new LinkedHashMap<>();
    private volatile boolean solicitado=true;
    private volatile Long administradorPreferido;
    private String contaAtual;
    private String mensagem="Aguardando preparação das pastas.";
    private Set<Long> preparados=new HashSet<>();
    private volatile Situacao ultima=new Situacao("Aguardando preparação das pastas.",0,null);
    public record Pasta(String id,boolean pronta) {}
    public record Situacao(String mensagem,int usuariosPreparados,String pastaPrincipal) {}

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleDrivePastasService(GoogleDriveController drive,UsuarioService usuarios,
            GoogleDriveTokenStore conexoes,ObjectMapper json){
        this(drive,usuarios,conexoes,json,Path.of("data","google-drive","pastas-usuarios.json"));
    }
    public GoogleDrivePastasService(GoogleDriveController drive,UsuarioService usuarios,
            GoogleDriveTokenStore conexoes,ObjectMapper json,Path arquivo){
        this.drive=drive;this.usuarios=usuarios;this.conexoes=conexoes;this.json=json;this.arquivo=arquivo;
        if(Files.exists(arquivo))try{
            contas=json.readValue(arquivo.toFile(),new TypeReference<Map<String,Map<String,Pasta>>>(){});
        }catch(IOException e){throw new IllegalStateException("Não foi possível carregar os vínculos das pastas do Drive.",e);}
        var selecao=arquivo.resolveSibling("pastas-administrador.txt");
        if(Files.exists(selecao))try{administradorPreferido=Long.parseLong(Files.readString(selecao).trim());}
        catch(IOException|NumberFormatException e){throw new IllegalStateException("Não foi possível carregar a conta responsável pelas pastas.",e);}
    }
    public void solicitar(){solicitado=true;}
    public synchronized void solicitar(long administrador){
        try{
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo.resolveSibling("pastas-administrador.txt"),Long.toString(administrador));
            administradorPreferido=administrador;
            solicitado=true;
        }catch(IOException e){throw new IllegalStateException("Não foi possível salvar a conta responsável pelas pastas.",e);}
    }
    public Situacao status(){return ultima;}
    public record Contexto(GoogleDriveController.ConexaoEscrita conexao,String documentos,String atas) {}
    public synchronized Contexto contextoUsuario(long usuario){
        if(!preparados.contains(usuario)||solicitado)processar();
        if(!preparados.contains(usuario))throw new IllegalStateException(mensagem);
        var admin=usuarios.listar().stream().filter(u->u.isAtivo()&&u.getPerfil()==Perfil.ADMIN&&conexoes.existe(u.getId())
                &&(administradorPreferido==null||administradorPreferido.equals(u.getId())))
                .findFirst().orElseThrow(()->new IllegalStateException("O administrador precisa conectar o Drive."));
        var conexao=drive.abrirEscrita(admin.getId());
        if(!conexao.conta().equals(contaAtual)){solicitado=true;throw new IllegalStateException("Aguardando preparação da nova conta Google.");}
        var pastas=contas.get(contaAtual);
        return new Contexto(conexao,pastas.get("usuario-"+usuario+"-documentos").id(),pastas.get("usuario-"+usuario+"-atas").id());
    }
    private void atualizarStatus(){
        var pasta=contaAtual==null?null:contas.get(contaAtual).get("raiz");
        ultima=new Situacao(mensagem,preparados.size(),pasta!=null&&pasta.pronta()?pasta.id():null);
    }
    @Scheduled(fixedDelay=30000,initialDelay=10000)
    public synchronized void processar(){
        var lista=usuarios.listar();
        if(!solicitado&&lista.stream().allMatch(u->preparados.contains(u.getId())))return;
        try{
            var admin=lista.stream().filter(u->u.isAtivo()&&u.getPerfil()==Perfil.ADMIN&&conexoes.existe(u.getId())
                            &&(administradorPreferido==null||administradorPreferido.equals(u.getId())))
                    .findFirst().orElseThrow(()->new IllegalStateException("Conecte o Google Drive como administrador e autorize a criação de pastas."));
            var conexao=drive.abrirEscrita(admin.getId());
            contaAtual=conexao.conta();
            contas.computeIfAbsent(contaAtual,k->new LinkedHashMap<>());
            preparados=new HashSet<>();
            String raiz=garantir(conexao,"raiz","Gestão Acadêmica",null);
            mensagem="Preparando as pastas dos usuários…";
            atualizarStatus();
            for(var usuario:lista){
                String chave="usuario-"+usuario.getId();
                String pasta=garantir(conexao,chave,usuario.getNome(),raiz);
                garantir(conexao,chave+"-documentos","Documentos",pasta);
                garantir(conexao,chave+"-atas","Atas e Atividades",pasta);
                preparados.add(usuario.getId());
                atualizarStatus();
            }
            mensagem="Pastas preparadas. Novos usuários receberão suas pastas automaticamente.";
            solicitado=false;
        }catch(IllegalStateException e){
            mensagem=e.getMessage();
        }catch(Exception e){
            mensagem="Não foi possível preparar todas as pastas. O sistema tentará novamente automaticamente.";
        }finally{atualizarStatus();}
    }
    private String garantir(GoogleDriveController.ConexaoEscrita conexao,String chave,String nome,String pai){
        var pastas=contas.get(conexao.conta());
        var pasta=pastas.get(chave);
        if(pasta==null){
            pasta=new Pasta(drive.reservarIdPasta(conexao.token()),false);
            pastas.put(chave,pasta);
            try{salvar();}catch(RuntimeException e){pastas.remove(chave);throw e;}
        }
        if(!pasta.pronta()){
            drive.garantirPasta(conexao.token(),pasta.id(),nome,pai);
            pastas.put(chave,new Pasta(pasta.id(),true));
            try{salvar();}catch(RuntimeException e){pastas.put(chave,pasta);throw e;}
        }
        return pasta.id();
    }
    private void salvar(){
        try{
            Files.createDirectories(arquivo.getParent());
            var temporario=arquivo.resolveSibling("pastas-usuarios.tmp");
            json.writeValue(temporario.toFile(),contas);
            Files.move(temporario,arquivo,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException e){throw new IllegalStateException("Não foi possível salvar os vínculos das pastas. O sistema tentará novamente.",e);}
    }
}
