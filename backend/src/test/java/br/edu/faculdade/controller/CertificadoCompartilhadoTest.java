package br.edu.faculdade.controller;

import br.edu.faculdade.service.*;
import br.edu.faculdade.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificadoCompartilhadoTest {
 @TempDir Path temp;
 @Test void registraDataNoServidorEPreservaAoConfirmarMesmaAprovacao(){
  var registros=new CertificadoController(new ObjectMapper(),temp.resolve("datas.json"));
  registros.registrar(new GoogleDriveController.PastaCertificados("p","Aluno",List.of(new GoogleDriveController.Certificado("pdf","Curso.pdf","2020-01-01")),List.of()));
  var auth=mock(AuthController.class);
  when(auth.exigirUsuario(any())).thenReturn(new Usuario(1L,"Admin","a@teste","",Perfil.ADMIN,true));
  org.springframework.test.util.ReflectionTestUtils.setField(registros,"auth",auth);
  var antes=java.time.Instant.now();
  var resultado=registros.avaliar("pdf",new CertificadoController.Avaliacao("APROVADO",10.0,null,"2000-01-01T00:00:00Z"),new org.springframework.mock.web.MockHttpServletRequest());
  assertFalse(java.time.Instant.parse(resultado.aprovadoEm()).isBefore(antes));
  var repetido=registros.avaliar("pdf",new CertificadoController.Avaliacao("APROVADO",10.0,null),new org.springframework.mock.web.MockHttpServletRequest());
  assertEquals(resultado.aprovadoEm(),repetido.aprovadoEm());
  var reiniciado=new CertificadoController(new ObjectMapper(),temp.resolve("datas.json"));
  assertEquals(resultado.aprovadoEm(),reiniciado.listar().avaliacoes().get("pdf").aprovadoEm());
 }
 @Test void origemFixaPersisteEProtegeVinculoSemPerderAvaliacoes(){
  var arquivo=temp.resolve("origem.json");var json=new ObjectMapper();
  var registros=new CertificadoController(json,arquivo);
  String id="1RrceKdLCZYoy6wiOWSj2J6pplGrlrlje";
  registros.configurarPastaFixa(id);
  registros.registrar(new GoogleDriveController.PastaCertificados(id,"Certificados da instituição",List.of(),List.of()));
  assertThrows(IllegalArgumentException.class,()->registros.remover(id));
  var reiniciado=new CertificadoController(json,arquivo);reiniciado.configurarPastaFixa(id);
  assertEquals("Certificados da instituição",reiniciado.listar().pastas().get(id).nome());
  assertEquals(1,reiniciado.listar().pastas().size());assertEquals(id,reiniciado.origem().get("id"));
 }
 @Test void mantemPastasEUsaConexaoSalvaDoAdministrador(){
  var arquivo=temp.resolve("certificados.json");
  var json=new ObjectMapper();
  var registros=new CertificadoController(json,arquivo);
  registros.registrar(new GoogleDriveController.PastaCertificados("pasta","Aluno",
    List.of(new GoogleDriveController.Certificado("pdf","Curso.pdf","2026-11-09")),List.of()));
  var reiniciado=new CertificadoController(json,arquivo);
  assertEquals("Aluno",reiniciado.listar().pastas().get("pasta").nome());
  var usuarios=mock(UsuarioService.class);
  var conexoes=mock(GoogleDriveTokenStore.class);
  var drive=mock(GoogleDriveController.class);
  when(usuarios.listar()).thenReturn(List.of(
    new Usuario(1L,"Admin","a@teste","",Perfil.ADMIN,true),
    new Usuario(2L,"Gerenciador","g@teste","",Perfil.GERENCIADOR_CERTIFICADOS,true)));
  when(conexoes.existe(1L)).thenReturn(true);
  var sincronizacao=new CertificadoSincronizacaoController(reiniciado,drive,usuarios,conexoes);
  assertEquals(1,sincronizacao.sincronizar().pastas().size());
  verify(drive).atualizarCompartilhado(1L,List.of("pasta"));
 }
}
