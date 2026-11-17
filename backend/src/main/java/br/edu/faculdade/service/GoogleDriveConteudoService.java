package br.edu.faculdade.service;

import br.edu.faculdade.controller.GoogleDriveController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

@Service
public class GoogleDriveConteudoService {
 private final GoogleDriveController drive;
 private final GoogleDrivePastasService pastas;
 private final DocumentoStorageService documentos;
 private final AtaAnexoStorageService atas;
 private final UsuarioService usuarios;
 private final ObjectMapper json;
 private final Path arquivo;
 private Map<String,Map<String,Vinculo>> planos=new HashMap<>();
 private final Map<Long,String> mensagens=new java.util.concurrent.ConcurrentHashMap<>();
 @org.springframework.beans.factory.annotation.Autowired
 @org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor")
 private org.springframework.core.task.TaskExecutor executor;
 public record Vinculo(String id,String hash,boolean pasta) {}
 @org.springframework.beans.factory.annotation.Autowired
 public GoogleDriveConteudoService(GoogleDriveController drive,GoogleDrivePastasService pastas,
     DocumentoStorageService documentos,AtaAnexoStorageService atas,UsuarioService usuarios,ObjectMapper json){
  this(drive,pastas,documentos,atas,usuarios,json,Path.of("data","google-drive","conteudos.json"));
 }
 public GoogleDriveConteudoService(GoogleDriveController drive,GoogleDrivePastasService pastas,
     DocumentoStorageService documentos,AtaAnexoStorageService atas,UsuarioService usuarios,ObjectMapper json,Path arquivo){
  this.drive=drive;this.pastas=pastas;this.documentos=documentos;this.atas=atas;this.usuarios=usuarios;this.json=json;this.arquivo=arquivo;
  if(Files.exists(arquivo))try{planos=json.readValue(arquivo.toFile(),new TypeReference<Map<String,Map<String,Vinculo>>>(){});}
  catch(IOException e){throw new IllegalStateException("Não foi possível carregar a sincronização do Drive.",e);}
 }
 public String status(long usuario){return mensagens.getOrDefault(usuario,"Arquivos salvos no servidor. Sincronização automática com o Drive a cada 30 segundos.");}
 public void pendente(long usuario){mensagens.put(usuario,"Alterações salvas no servidor. Aguardando envio automático ao Google Drive.");}
 public void exclusaoPendente(long usuario){
  mensagens.put(usuario,"Exclusão salva. Removendo também as pastas e arquivos do Google Drive…");
  try{executor.execute(()->sincronizarExclusoes(usuario));}
  catch(org.springframework.core.task.TaskRejectedException e){mensagens.put(usuario,"Excluído do projeto. A remoção no Google Drive será tentada novamente automaticamente.");}
 }
 public synchronized void sincronizarExclusoes(long usuario){
  try{
   if(planos.keySet().stream().anyMatch(k->k.endsWith(":"+usuario))){
    var contexto=pastas.contextoUsuario(usuario);
    var plano=planos.get(contexto.conexao().conta()+":"+usuario);
    if(plano==null)throw new IllegalStateException("Reconecte a conta do Drive utilizada para sincronizar os arquivos.");
    var presentes=new HashSet<String>();
    documentos.todos(usuario).forEach(item->presentes.add("doc-"+item.id()));
    removerAusentes(plano,presentes,contexto.conexao().token(),true);
   }
   mensagens.put(usuario,"Exclusão concluída. As pastas e arquivos sincronizados foram enviados à lixeira do Google Drive.");
  }catch(Exception e){
   mensagens.put(usuario,"Excluído do projeto. A remoção no Google Drive está pendente e será tentada novamente automaticamente.");
  }
 }
 @Scheduled(fixedDelay=30000,initialDelay=15000)
 public synchronized void processar(){
  for(var usuario:usuarios.listar()){
   try{
    var docs=documentos.todos(usuario.getId());
    var anexos=atas.listar(usuario.getId());
    if(docs.isEmpty()&&anexos.isEmpty()&&planos.keySet().stream().noneMatch(k->k.endsWith(":"+usuario.getId())))continue;
    mensagens.put(usuario.getId(),"Sincronizando pastas e arquivos com o Google Drive…");
    var contexto=pastas.contextoUsuario(usuario.getId());
    String conta=contexto.conexao().conta()+":"+usuario.getId();
    var plano=planos.computeIfAbsent(conta,k->new HashMap<>());
    var presentes=new HashSet<String>();
    docs.forEach(item->presentes.add("doc-"+item.id()));
    anexos.forEach(item->presentes.add("ata-"+item.id()));
    removerAusentes(plano,presentes,contexto.conexao().token(),false);
    var indice=new HashMap<String,DocumentoStorageService.Item>();
    docs.forEach(item->indice.put(item.id(),item));
    for(var item:docs)sincronizarDocumento(usuario.getId(),item,indice,new HashSet<>(),contexto,plano);
    for(var anexo:anexos)enviar(plano,"ata-"+anexo.id(),anexo.nome(),contexto.atas(),
      atas.conteudo(usuario.getId(),anexo),anexo.hash(),contexto.conexao().token());
    mensagens.put(usuario.getId(),"Pastas e arquivos sincronizados com o Google Drive.");
   }catch(IllegalStateException e){mensagens.put(usuario.getId(),e.getMessage());}
   catch(Exception e){mensagens.put(usuario.getId(),"Arquivos preservados no servidor. Não foi possível concluir o envio ao Drive; haverá uma nova tentativa automática.");}
  }
 }
 private void removerAusentes(Map<String,Vinculo> plano,Set<String> presentes,String token,boolean somenteDocumentos){
  var removidos=plano.keySet().stream().filter(k->!presentes.contains(k)&&(!somenteDocumentos||k.startsWith("doc-")))
   .sorted(Comparator.comparing(k->plano.get(k).pasta())).toList();
  for(String chave:removidos){
   drive.lixeira(token,plano.get(chave).id());
   var anterior=plano.remove(chave);
   try{salvar();}catch(RuntimeException e){plano.put(chave,anterior);throw e;}
  }
 }
 private String sincronizarDocumento(long usuario,DocumentoStorageService.Item item,Map<String,DocumentoStorageService.Item> indice,
     Set<String> caminho,GoogleDrivePastasService.Contexto contexto,Map<String,Vinculo> plano) throws IOException {
  if(!caminho.add(item.id()))throw new IllegalStateException("A estrutura de pastas contém um ciclo.");
  String pai=contexto.documentos();
  if(item.paiId()!=null){
   var pasta=indice.get(item.paiId());
   if(pasta==null||!pasta.pasta())throw new IllegalStateException("A pasta de origem não foi encontrada.");
   pai=sincronizarDocumento(usuario,pasta,indice,caminho,contexto,plano);
  }
  caminho.remove(item.id());
  String chave="doc-"+item.id();
  String token=contexto.conexao().token();
  if(!item.pasta()){
   var conteudo=documentos.conteudo(usuario,item.id());
   return enviar(plano,chave,item.nome(),pai,conteudo,AtaAnexoStorageService.hash(conteudo),token);
  }
  var vinculo=planejar(plano,chave,true,token);
  if(!"pronta".equals(vinculo.hash())){
   drive.garantirPasta(token,vinculo.id(),item.nome(),pai);
   atualizar(plano,chave,new Vinculo(vinculo.id(),"pronta",true));
  }
  return vinculo.id();
 }
 private String enviar(Map<String,Vinculo> plano,String chave,String nome,String pai,Path conteudo,String hash,String token) throws IOException {
  hash=hash+":"+nome;
  var vinculo=planejar(plano,chave,false,token);
  if(!hash.equals(vinculo.hash())){
   drive.enviarArquivo(token,vinculo.id(),nome,pai,conteudo);
   atualizar(plano,chave,new Vinculo(vinculo.id(),hash,false));
  }
  return vinculo.id();
 }
 private Vinculo planejar(Map<String,Vinculo> plano,String chave,boolean pasta,String token){
  var vinculo=plano.get(chave);
  if(vinculo==null){vinculo=new Vinculo(drive.reservarIdPasta(token),null,pasta);atualizar(plano,chave,vinculo);}
  return vinculo;
 }
 private void atualizar(Map<String,Vinculo> plano,String chave,Vinculo valor){
  var anterior=plano.put(chave,valor);
  try{salvar();}catch(RuntimeException e){if(anterior==null)plano.remove(chave);else plano.put(chave,anterior);throw e;}
 }
 private void salvar(){
  try{
   Files.createDirectories(arquivo.getParent());
   var temp=arquivo.resolveSibling("conteudos.tmp");
   json.writeValue(temp.toFile(),planos);
   Files.move(temp,arquivo,StandardCopyOption.REPLACE_EXISTING);
  }catch(IOException e){throw new IllegalStateException("Não foi possível registrar a sincronização. O sistema tentará novamente.",e);}
 }
}
