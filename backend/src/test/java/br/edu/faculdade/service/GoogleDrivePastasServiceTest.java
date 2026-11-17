package br.edu.faculdade.service;

import br.edu.faculdade.controller.GoogleDriveController;
import br.edu.faculdade.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GoogleDrivePastasServiceTest {
 @TempDir Path temp;
 private final GoogleDriveController drive=mock(GoogleDriveController.class);
 private final UsuarioService usuarios=mock(UsuarioService.class);
 private final GoogleDriveTokenStore conexoes=mock(GoogleDriveTokenStore.class);
 private final AtomicInteger ids=new AtomicInteger();
 private Usuario admin(){return new Usuario(1L,"Mesmo Nome","a@teste","",Perfil.ADMIN,true);}
 private GoogleDrivePastasService service(){
  when(conexoes.existe(1)).thenReturn(true);
  when(drive.abrirEscrita(1)).thenReturn(new GoogleDriveController.ConexaoEscrita("token","conta"));
  doAnswer(invocation->"id-"+ids.incrementAndGet()).when(drive).reservarIdPasta("token");
  return new GoogleDrivePastasService(drive,usuarios,conexoes,new ObjectMapper(),temp.resolve("pastas.json"));
 }
 @Test void preparaExistentesNovosEHomonimosSemDuplicarAposReiniciar(){
  when(usuarios.listar()).thenReturn(List.of(admin(),new Usuario(2L,"Mesmo Nome","b@teste","",Perfil.PROFESSOR,true)));
  var service=service();
  service.processar();
  assertEquals(2,service.status().usuariosPreparados());
  assertEquals(7,ids.get());
  service.processar();
  assertEquals(7,ids.get());
  var reiniciado=service();
  reiniciado.processar();
  assertEquals(7,ids.get());
  when(usuarios.listar()).thenReturn(List.of(admin(),new Usuario(2L,"Mesmo Nome","b@teste","",Perfil.PROFESSOR,true),
    new Usuario(3L,"Novo","c@teste","",Perfil.COORDENADOR,true)));
  reiniciado.processar();
  assertEquals(3,reiniciado.status().usuariosPreparados());
  assertEquals(10,ids.get());
 }
 @Test void falhaParcialRetomaMesmoIdentificador(){
  when(usuarios.listar()).thenReturn(List.of(admin()));
  doThrow(new IllegalStateException("Falha temporária")).doNothing().when(drive)
    .garantirPasta(anyString(),anyString(),eq("Documentos"),anyString());
  var service=service();
  service.processar();
  assertEquals(0,service.status().usuariosPreparados());
  assertEquals(3,ids.get());
  var reiniciado=service();
  reiniciado.processar();
  assertEquals(1,reiniciado.status().usuariosPreparados());
  assertEquals(4,ids.get());
  verify(drive,times(2)).garantirPasta("token","id-3","Documentos","id-2");
 }
 @Test void aguardaPermissaoSemReservarPastas(){
  when(usuarios.listar()).thenReturn(List.of(admin()));
  var service=service();
  when(drive.abrirEscrita(1)).thenThrow(new IllegalStateException("Autorize a criação de pastas."));
  service.processar();
  assertEquals("Autorize a criação de pastas.",service.status().mensagem());
  verify(drive,never()).reservarIdPasta(anyString());
 }
}
