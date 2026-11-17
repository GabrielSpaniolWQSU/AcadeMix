package br.edu.faculdade.service;

import br.edu.faculdade.model.*;
import br.edu.faculdade.controller.AvisoController.Aviso;
import br.edu.faculdade.controller.CertificadoController.*;
import br.edu.faculdade.controller.GoogleDriveController.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class BancoProjetoTest {
 private BancoProjeto banco;
 @Test void migraAprovacoesAntigasSemInventarDatas(){
  var fonte=new org.h2.jdbcx.JdbcDataSource();
  fonte.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  var jdbc=new JdbcTemplate(fonte);
  jdbc.execute("CREATE TABLE avaliacoes_certificados(certificado_id VARCHAR(255),status VARCHAR(20),horas_aprovadas DOUBLE,observacao VARCHAR(255))");
  jdbc.update("INSERT INTO avaliacoes_certificados VALUES ('antigo','APROVADO',75,NULL)");
  new BancoProjeto(jdbc,new DataSourceTransactionManager(fonte));
  assertNull(jdbc.queryForObject("SELECT aprovado_em FROM avaliacoes_certificados WHERE certificado_id='antigo'",String.class));
  assertEquals(75.0,jdbc.queryForObject("SELECT horas_aprovadas FROM avaliacoes_certificados WHERE certificado_id='antigo'",Double.class));
 }
 @BeforeEach void preparar(){
  var fonte=new org.h2.jdbcx.JdbcDataSource();
  fonte.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(fonte);
  banco=new BancoProjeto(new JdbcTemplate(fonte),new DataSourceTransactionManager(fonte));
  banco.usuarios(List.of(new Usuario(1L,"Admin","a@teste","hash",Perfil.ADMIN,true),
    new Usuario(2L,"Professor","b@teste","hash",Perfil.PROFESSOR,true)));
 }
 @Test void documentosRespeitamDonoEPastaPai(){
  String pai=UUID.randomUUID().toString();
  banco.documento(1,new DocumentoStorageService.Item(pai,"Pai",null,true,0));
  String filho=UUID.randomUUID().toString();
  banco.documento(1,new DocumentoStorageService.Item(filho,"Filho",pai,true,0));
  assertEquals(2,banco.documentos(1).size());
  assertTrue(banco.documentos(2).isEmpty());
  assertThrows(DataIntegrityViolationException.class,()->banco.documento(2,new DocumentoStorageService.Item(UUID.randomUUID().toString(),"Indevida",pai,true,0)));
  assertThrows(DataIntegrityViolationException.class,()->banco.excluirDocumento(1,pai));
 }
 @Test void importacaoUmaVezETransacaoSemPerda(){
  var aviso=new Aviso(1L,"Original","Texto",LocalDate.of(2026,1,1),"Geral");
  banco.importar("avisos",()->banco.avisos(List.of(aviso)));
  banco.importar("avisos",()->fail("Importação repetida"));
  assertThrows(DataIntegrityViolationException.class,()->banco.avisos(List.of(
    new Aviso(2L,null,"Inválido",LocalDate.now(),"Geral"))));
  assertEquals("Original",banco.avisos().getFirst().titulo());
 }
 @Test void excluiArvoreDeDocumentosSemAfetarOutrasPastas(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp)throws Exception{
  var storage=new DocumentoStorageService(new com.fasterxml.jackson.databind.ObjectMapper(),temp);
  org.springframework.test.util.ReflectionTestUtils.setField(storage,"banco",banco);
  var pai=storage.criarPasta(1,"Pai",null);
  var filha=storage.criarPasta(1,"Filha",pai.id());
  var neta=storage.criarPasta(1,"Neta",filha.id());
  var arquivo=storage.enviar(1,new org.springframework.mock.web.MockMultipartFile("arquivo","teste.txt","text/plain","teste".getBytes()),neta.id());
  var preservada=storage.criarPasta(1,"Preservada",null);
  var outraConta=storage.criarPasta(2,"Outra conta",null);
  var caminho=storage.conteudo(1,arquivo.id());
  storage.excluir(1,pai.id());
  assertEquals(List.of(preservada),storage.todos(1));
  assertEquals(List.of(outraConta),storage.todos(2));
  assertFalse(java.nio.file.Files.exists(caminho));
 }
 @Test void certificadosEAtasPersistemVinculosEAvaliacoes(){
  var filha=new PastaCertificados("filha","Aluno",List.of(new Certificado("pdf","Curso.pdf","2026-11-15")),List.of());
  var pasta=new PastaCertificados("raiz","Certificados",List.of(),List.of(filha));
  banco.certificados(new Dados(Map.of("raiz",pasta),Map.of("pdf",new Avaliacao("APROVADO",30.0,null,"2026-11-16T15:00:00Z"))));
  var lido=banco.certificados();
  assertEquals("Aluno",lido.pastas().get("raiz").filhas().getFirst().nome());
  assertEquals(30.0,lido.avaliacoes().get("pdf").horasAprovadas());
  assertEquals("2026-11-16T15:00:00Z",lido.avaliacoes().get("pdf").aprovadoEm());
  banco.ata(2,10,"ATA","Título","Descrição",LocalDate.of(2026,11,15),"ata.docx");
  assertEquals("ata.docx",banco.atas(2).getFirst().arquivoNome());
  assertTrue(banco.atas(1).isEmpty());
  banco.anexo(2,new AtaAnexoStorageService.Anexo(10,"ata.docx","hash",123));
  assertEquals(123,banco.anexos(2).getFirst().tamanho());
 }
}
