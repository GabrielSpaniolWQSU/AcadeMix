package br.edu.faculdade.service;

import br.edu.faculdade.controller.GoogleDriveController;
import br.edu.faculdade.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GoogleDriveConteudoServiceTest {
 @TempDir Path temp;
 private final GoogleDriveController drive=mock(GoogleDriveController.class);
 private final GoogleDrivePastasService pastas=mock(GoogleDrivePastasService.class);
 private final AtaAnexoStorageService atas=mock(AtaAnexoStorageService.class);
 private final UsuarioService usuarios=mock(UsuarioService.class);
 private final AtomicInteger ids=new AtomicInteger();
 private final ObjectMapper json=new ObjectMapper();
 private DocumentoStorageService documentos;
 private GoogleDriveConteudoService iniciar() throws Exception {
  if(documentos==null)documentos=new DocumentoStorageService(json,temp.resolve("docs"));
  when(usuarios.listar()).thenReturn(List.of(new Usuario(1L,"Usuário","u@teste","",Perfil.PROFESSOR,true)));
  when(pastas.contextoUsuario(1)).thenReturn(new GoogleDrivePastasService.Contexto(
    new GoogleDriveController.ConexaoEscrita("token","conta"),"documentos","atas"));
  when(atas.listar(1)).thenReturn(List.of());
  doAnswer(invocation->"drive-"+ids.incrementAndGet()).when(drive).reservarIdPasta("token");
  return new GoogleDriveConteudoService(drive,pastas,documentos,atas,usuarios,json,temp.resolve("sync.json"));
 }
 @Test void sincronizaPastasFilhasArquivosEExclusaoSemDuplicar() throws Exception {
  var service=iniciar();
  var pai=documentos.criarPasta(1,"Pai",null);
  var filha=documentos.criarPasta(1,"Filha",pai.id());
  var item=documentos.enviar(1,new MockMultipartFile("arquivo","teste.txt","text/plain","conteudo".getBytes()),filha.id());
  service.processar();
  assertEquals(3,ids.get());
  verify(drive).garantirPasta("token","drive-1","Pai","documentos");
  verify(drive).garantirPasta("token","drive-2","Filha","drive-1");
  verify(drive).enviarArquivo(eq("token"),eq("drive-3"),eq("teste.txt"),eq("drive-2"),any());
  var reiniciado=iniciar();
  reiniciado.processar();
  assertEquals(3,ids.get());
  verify(drive,times(1)).enviarArquivo(anyString(),anyString(),anyString(),anyString(),any());
  documentos.excluir(1,item.id());
  reiniciado.processar();
  verify(drive).lixeira("token","drive-3");
 }
 @Test void arquivoQueFalhouRetomaMesmoIdAposReinicio() throws Exception{
  var service=iniciar();
  documentos.enviar(1,new MockMultipartFile("arquivo","teste.txt","text/plain","conteudo".getBytes()),null);
  doThrow(new java.io.IOException("falha")).doNothing().when(drive).enviarArquivo(anyString(),anyString(),anyString(),anyString(),any());
  service.processar();
  assertEquals(1,ids.get());
  var reiniciado=iniciar();
  reiniciado.processar();
  assertEquals(1,ids.get());
  verify(drive,times(2)).enviarArquivo(eq("token"),eq("drive-1"),eq("teste.txt"),eq("documentos"),any());
 }
 @Test void anexoVaiParaAtasEAtualizaMesmoArquivo() throws Exception{
  var service=iniciar();
  var conteudo=temp.resolve("ata.docx");Files.writeString(conteudo,"original");
  when(atas.listar(1)).thenReturn(List.of(new AtaAnexoStorageService.Anexo(10,"ata.docx","hash-1",8)));
  when(atas.conteudo(eq(1L),any())).thenReturn(conteudo);
  service.processar();
  when(atas.listar(1)).thenReturn(List.of(new AtaAnexoStorageService.Anexo(10,"revisada.docx","hash-2",9)));
  service.processar();
  assertEquals(1,ids.get());
  verify(drive).enviarArquivo("token","drive-1","ata.docx","atas",conteudo);
  verify(drive).enviarArquivo("token","drive-1","revisada.docx","atas",conteudo);
 }
 @Test void excluirPastaEnviaArvoreParaLixeiraSemEsperarAgendamento()throws Exception{
  var service=iniciar();
  var pai=documentos.criarPasta(1,"Pai",null);
  var filha=documentos.criarPasta(1,"Filha",pai.id());
  documentos.enviar(1,new MockMultipartFile("arquivo","teste.txt","text/plain","conteudo".getBytes()),filha.id());
  service.processar();clearInvocations(drive);
  documentos.excluir(1,pai.id());
  org.springframework.test.util.ReflectionTestUtils.setField(service,"executor",new org.springframework.core.task.SyncTaskExecutor());
  service.exclusaoPendente(1);
  verify(drive).lixeira("token","drive-1");
  verify(drive).lixeira("token","drive-2");
  verify(drive).lixeira("token","drive-3");
  verify(drive,never()).enviarArquivo(anyString(),anyString(),anyString(),anyString(),any());
  assertTrue(service.status(1).contains("lixeira"));
  clearInvocations(drive);iniciar().processar();verifyNoInteractions(drive);
 }
 @Test void exclusaoQueFalhouRetomaAposReinicioAntesDeEnviarOutrosArquivos()throws Exception{
  var service=iniciar();var pai=documentos.criarPasta(1,"Pai",null);service.processar();
  documentos.excluir(1,pai.id());
  doThrow(new IllegalStateException("Drive indisponível")).doNothing().when(drive).lixeira("token","drive-1");
  service.sincronizarExclusoes(1);
  assertTrue(service.status(1).contains("pendente"));
  documentos.enviar(1,new MockMultipartFile("arquivo","novo.txt","text/plain","novo".getBytes()),null);
  doThrow(new java.io.IOException("Falha no envio")).when(drive).enviarArquivo(anyString(),anyString(),anyString(),anyString(),any());
  var reiniciado=iniciar();reiniciado.processar();
  verify(drive,times(2)).lixeira("token","drive-1");
  clearInvocations(drive);reiniciado.processar();verify(drive,never()).lixeira(anyString(),anyString());
 }
}
