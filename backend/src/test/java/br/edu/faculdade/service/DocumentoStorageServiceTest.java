package br.edu.faculdade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DocumentoStorageServiceTest {
 @TempDir Path temp;
 @Test void pastasArquivosEPersistenciaRespeitamDono() throws Exception {
  var storage=new DocumentoStorageService(new ObjectMapper(),temp);
  var pai=storage.criarPasta(1,"Pasta pai",null);
  var filha=storage.criarPasta(1,"Filha",pai.id());
  var file=storage.enviar(1,new MockMultipartFile("arquivo","teste.txt","text/plain","conteudo".getBytes()),filha.id());
  assertEquals(1,storage.listar(1,pai.id()).pastas().size());
  assertEquals(1,storage.listar(1,filha.id()).arquivos().size());
  assertTrue(storage.listar(2,null).pastas().isEmpty());
  assertThrows(ResponseStatusException.class,()->storage.baixar(2,file.id()));
  assertThrows(ResponseStatusException.class,()->storage.criarPasta(2,"Inválida",pai.id()));
  assertThrows(ResponseStatusException.class,()->storage.excluir(2,file.id()));
  var reiniciado=new DocumentoStorageService(new ObjectMapper(),temp);
  assertEquals("conteudo",new String(reiniciado.baixar(1,file.id())));
  var preservada=reiniciado.criarPasta(1,"Outra pasta",null);
  var outro=reiniciado.enviar(2,new MockMultipartFile("arquivo","outro.txt","text/plain","outra conta".getBytes()),null);
  var conteudo=reiniciado.conteudo(1,file.id());
  reiniciado.excluir(1,pai.id());
  assertFalse(java.nio.file.Files.exists(conteudo));
  assertThrows(ResponseStatusException.class,()->reiniciado.obter(1,filha.id()));
  assertThrows(ResponseStatusException.class,()->reiniciado.obter(1,file.id()));
  assertEquals("Outra pasta",reiniciado.obter(1,preservada.id()).nome());
  assertEquals("outra conta",new String(reiniciado.baixar(2,outro.id())));
  reiniciado.excluir(1,preservada.id());
  assertTrue(reiniciado.listar(1,null).pastas().isEmpty());
 }
 @Test void contasMantemIdentificadorAposReinicio(){
  var arquivo=temp.resolve("usuarios.json");
  var usuarios=new UsuarioService(arquivo);
  var usuario=usuarios.criar(new br.edu.faculdade.dto.Dtos.UsuarioReq("Teste","teste@exemplo.com","senha",br.edu.faculdade.model.Perfil.PROFESSOR,true));
  var reiniciado=new UsuarioService(arquivo);
  var recuperado=reiniciado.listar().stream().filter(u->u.getId().equals(usuario.getId())).findFirst().orElseThrow();
  assertTrue(reiniciado.senhaCorreta(recuperado,"senha"));
  assertFalse(reiniciado.senhaCorreta(recuperado,"outra"));
  var novo=reiniciado.criar(new br.edu.faculdade.dto.Dtos.UsuarioReq("Outro","outro@exemplo.com","senha",br.edu.faculdade.model.Perfil.PROFESSOR,true));
  assertNotEquals(usuario.getId(),novo.getId());
 }
}
