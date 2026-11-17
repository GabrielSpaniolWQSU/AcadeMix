package br.edu.faculdade.service;

import br.edu.faculdade.model.*;
import br.edu.faculdade.controller.AvisoController.Aviso;
import br.edu.faculdade.controller.CertificadoController.*;
import br.edu.faculdade.controller.GoogleDriveController.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
@Profile("mysql")
@DependsOnDatabaseInitialization
public class BancoProjeto {
 private final JdbcTemplate db;
 private final TransactionTemplate transacao;
 public BancoProjeto(JdbcTemplate db,org.springframework.transaction.PlatformTransactionManager manager){
  this.db=db;this.transacao=new TransactionTemplate(manager);
  db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) conexao->{
   try(var colunas=conexao.getMetaData().getColumns(conexao.getCatalog(),null,"avaliacoes_certificados","aprovado_em")){
    if(!colunas.next())try(var comando=conexao.createStatement()){comando.executeUpdate("ALTER TABLE avaliacoes_certificados ADD COLUMN aprovado_em VARCHAR(40) NULL");}
   }
   return null;
  });
 }
 public void importar(String chave,Runnable importacao){
  transacao.executeWithoutResult(status->{
   if(db.queryForObject("SELECT COUNT(*) FROM migracoes_dados WHERE chave=?",Long.class,chave)>0)return;
   importacao.run();
   db.update("INSERT INTO migracoes_dados(chave) VALUES (?)",chave);
  });
 }
 private boolean existe(String tabela,String coluna,Object valor){
  return db.queryForObject("SELECT COUNT(*) FROM "+tabela+" WHERE "+coluna+"=?",Long.class,valor)>0;
 }
 public List<Usuario> usuarios(){
  return db.query("SELECT * FROM usuarios ORDER BY id",(r,n)->new Usuario(r.getLong("id"),r.getString("nome"),
   r.getString("email"),r.getString("senha"),Perfil.valueOf(r.getString("perfil")),r.getBoolean("ativo")));
 }
 public void usuarios(Collection<Usuario> usuarios){
  transacao.executeWithoutResult(status->{for(var u:usuarios){
   if(existe("usuarios","id",u.getId()))db.update("UPDATE usuarios SET nome=?,email=?,senha=?,perfil=?,ativo=? WHERE id=?",
    u.getNome(),u.getEmail(),u.getSenha(),u.getPerfil().name(),u.isAtivo(),u.getId());
   else db.update("INSERT INTO usuarios(id,nome,email,senha,perfil,ativo) VALUES (?,?,?,?,?,?)",
    u.getId(),u.getNome(),u.getEmail(),u.getSenha(),u.getPerfil().name(),u.isAtivo());
  }});
 }
 public List<Aviso> avisos(){
  return db.query("SELECT * FROM avisos",(r,n)->new Aviso(r.getLong("id"),r.getString("titulo"),
    r.getString("descricao"),r.getDate("data").toLocalDate(),r.getString("curso")));
 }
 public void avisos(Collection<Aviso> avisos){
  transacao.executeWithoutResult(status->{
   db.update("DELETE FROM avisos");
   for(var a:avisos)db.update("INSERT INTO avisos(id,titulo,descricao,data,curso) VALUES (?,?,?,?,?)",
     a.id(),a.titulo(),a.descricao(),a.data(),a.curso());
  });
 }
 public List<DocumentoStorageService.Item> documentos(long usuario){
  return db.query("SELECT * FROM documentos WHERE usuario_id=?",(r,n)->new DocumentoStorageService.Item(
    r.getString("id"),r.getString("nome"),r.getString("pai_id"),r.getBoolean("pasta"),r.getLong("tamanho")),usuario);
 }
 public void documento(long usuario,DocumentoStorageService.Item item){
  db.update("INSERT INTO documentos(usuario_id,id,nome,pai_id,pasta,tamanho) VALUES (?,?,?,?,?,?)",
   usuario,item.id(),item.nome(),item.paiId(),item.pasta(),item.tamanho());
 }
 public void excluirDocumento(long usuario,String id){db.update("DELETE FROM documentos WHERE usuario_id=? AND id=?",usuario,id);}
 public List<AtaAnexoStorageService.Anexo> anexos(long usuario){
  return db.query("SELECT * FROM anexos_atas WHERE usuario_id=?",(r,n)->new AtaAnexoStorageService.Anexo(
   r.getLong("id"),r.getString("nome"),r.getString("hash"),r.getLong("tamanho")),usuario);
 }
 public void anexo(long usuario,AtaAnexoStorageService.Anexo a){
  transacao.executeWithoutResult(status->{
   db.update("DELETE FROM anexos_atas WHERE usuario_id=? AND id=?",usuario,a.id());
   db.update("INSERT INTO anexos_atas(usuario_id,id,nome,hash,tamanho) VALUES (?,?,?,?,?)",usuario,a.id(),a.nome(),a.hash(),a.tamanho());
  });
 }
 public Dados certificados(){
  var pastas=new LinkedHashMap<String,PastaCertificados>();
  var linhas=db.queryForList("SELECT * FROM pastas_certificados WHERE pai_id IS NULL");
  for(var linha:linhas){String id=(String)linha.get("id");pastas.put(id,lerPasta(id));}
  var avaliacoes=new HashMap<String,Avaliacao>();
  db.query("SELECT * FROM avaliacoes_certificados",r->{
   Double horas=r.getObject("horas_aprovadas")==null?null:r.getDouble("horas_aprovadas");
   avaliacoes.put(r.getString("certificado_id"),new Avaliacao(r.getString("status"),horas,r.getString("observacao"),r.getString("aprovado_em")));
  });
  return new Dados(pastas,avaliacoes);
 }
 private PastaCertificados lerPasta(String id){
  String nome=db.queryForObject("SELECT nome FROM pastas_certificados WHERE id=?",String.class,id);
  var arquivos=db.query("SELECT * FROM certificados WHERE pasta_id=?",(r,n)->new Certificado(r.getString("id"),r.getString("nome"),r.getString("enviado_em")),id);
  var filhas=db.queryForList("SELECT id FROM pastas_certificados WHERE pai_id=?",String.class,id).stream().map(this::lerPasta).toList();
  return new PastaCertificados(id,nome,arquivos,filhas);
 }
 public void certificados(Dados dados){
  transacao.executeWithoutResult(status->{
   db.update("DELETE FROM certificados");db.update("DELETE FROM pastas_certificados");db.update("DELETE FROM avaliacoes_certificados");
   var vistos=new HashSet<String>();
   for(var p:dados.pastas().values())gravarPasta(p,p.id(),null,vistos);
   dados.avaliacoes().forEach((id,a)->db.update("INSERT INTO avaliacoes_certificados(certificado_id,status,horas_aprovadas,observacao,aprovado_em) VALUES (?,?,?,?,?)",id,a.status(),a.horasAprovadas(),a.observacao(),a.aprovadoEm()));
  });
 }
 private void gravarPasta(PastaCertificados p,String raiz,String pai,Set<String> vistos){
  if(!vistos.add(p.id()))return;
  db.update("INSERT INTO pastas_certificados(id,nome,raiz_id,pai_id) VALUES (?,?,?,?)",p.id(),p.nome(),raiz,pai);
  for(var c:p.certificados()){
   if(!existe("certificados","id",c.id()))db.update("INSERT INTO certificados(id,pasta_id,nome,enviado_em) VALUES (?,?,?,?)",c.id(),p.id(),c.nome(),c.enviadoEm());
  }
  for(var filha:p.filhas())gravarPasta(filha,raiz,p.id(),vistos);
 }
 public List<AtaRegistroService.Registro> atas(long usuario){
  return db.query("SELECT * FROM atas_atividades WHERE usuario_id=? ORDER BY data DESC,id DESC",(r,n)->
   new AtaRegistroService.Registro(r.getLong("id"),r.getString("tipo"),r.getString("titulo"),r.getString("descricao"),
     r.getDate("data").toLocalDate(),r.getString("arquivo_nome")),usuario);
 }
 public void ata(long usuario,long id,String tipo,String titulo,String descricao,java.time.LocalDate data,String arquivoNome){
  transacao.executeWithoutResult(status->{
   db.update("DELETE FROM atas_atividades WHERE usuario_id=? AND id=?",usuario,id);
   db.update("INSERT INTO atas_atividades(usuario_id,id,tipo,titulo,descricao,data,arquivo_nome) VALUES (?,?,?,?,?,?,?)",usuario,id,tipo,titulo,descricao,data,arquivoNome);
  });
 }
 public void nomeAnexo(long usuario,long id,String nome){db.update("UPDATE atas_atividades SET arquivo_nome=? WHERE usuario_id=? AND id=?",nome,usuario,id);}
 public void excluirAta(long usuario,long id){db.update("DELETE FROM atas_atividades WHERE usuario_id=? AND id=?",usuario,id);}
}
