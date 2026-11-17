package br.edu.faculdade.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleDriveTokenStoreTest {
 @TempDir Path temp;
 @Test void salvaProtegidoRestauraPorUsuarioEDesconecta() throws Exception {
  var store=new GoogleDriveTokenStore(temp);
  store.salvar(1,"cliente-escopo","refresh-ficticio");
  assertFalse(new String(Files.readAllBytes(temp.resolve("1.token"))).contains("refresh-ficticio"));
  var reiniciado=new GoogleDriveTokenStore(temp);
  assertEquals("refresh-ficticio",reiniciado.carregar(1,"cliente-escopo"));
  assertNull(reiniciado.carregar(2,"cliente-escopo"));
  assertThrows(IllegalStateException.class,()->reiniciado.carregar(1,"outro-cliente"));
  reiniciado.remover(1);
  assertNull(reiniciado.carregar(1,"cliente-escopo"));
 }
}
