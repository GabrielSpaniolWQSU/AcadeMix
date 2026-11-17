package br.edu.faculdade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvisoExpiracaoTest {
 @SuppressWarnings("unchecked")
 private AvisoController preparar(AuthController auth){
  var controller=new AvisoController(auth,new ObjectMapper().findAndRegisterModules());
  var avisos=(Map<Long,AvisoController.Aviso>)ReflectionTestUtils.getField(controller,"avisos");
  avisos.clear();
  for(int dia:List.of(15,16,17))avisos.put((long)dia,new AvisoController.Aviso((long)dia,"Aviso "+dia,"Texto",LocalDate.of(2026,11,dia),"Geral"));
  return controller;
 }
 private void horario(AvisoController controller,String instante){ReflectionTestUtils.setField(controller,"relogio",Clock.fixed(Instant.parse(instante),ZoneId.of("America/Sao_Paulo")));}
 @Test void visivelAteOFimDaDataDeExpiracaoNoHorarioDeBrasilia(){
  var controller=preparar(mock(AuthController.class));var request=new MockHttpServletRequest();
  horario(controller,"2026-11-17T02:59:59Z");
  assertEquals(List.of(17L,16L),controller.listar("ativos",request).stream().map(AvisoController.Aviso::id).toList());
  assertEquals(List.of(15L),controller.listar("expirados",request).stream().map(AvisoController.Aviso::id).toList());
  horario(controller,"2026-11-17T03:00:00Z");
  assertEquals(List.of(17L),controller.listar("ativos",request).stream().map(AvisoController.Aviso::id).toList());
  assertEquals(List.of(16L,15L),controller.listar("expirados",request).stream().map(AvisoController.Aviso::id).toList());
  assertEquals(3,controller.listar("todos",request).size());
 }
 @Test void professorConsultaAtivosMasNaoHistoricoExpirado(){
  var auth=mock(AuthController.class);var controller=preparar(auth);var request=new MockHttpServletRequest();
  horario(controller,"2026-11-16T12:00:00Z");
  doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(auth).exigirEditorAvisos(request);
  assertEquals(2,controller.listar("ativos",request).size());
  assertThrows(ResponseStatusException.class,()->controller.listar("expirados",request));
  assertThrows(ResponseStatusException.class,()->controller.listar("todos",request));
  assertThrows(IllegalArgumentException.class,()->controller.listar("invalida",request));
 }
}
