package br.edu.faculdade.controller;

import br.edu.faculdade.model.Perfil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PermissoesTest {
 @Test void administradorTemTodasAsAreas(){
  for(var area:new String[]{"documentos","atas-atividades","certificados","avisos","usuarios","google-drive"})
   for(var metodo:new String[]{"GET","POST","PUT","DELETE"})assertTrue(PermissoesConfig.permitido(Perfil.ADMIN,area,metodo));
 }
 @Test void coordenadorTemAreasIndividuaisEAvisos(){
  for(var area:new String[]{"documentos","atas-atividades","avisos"})
   for(var metodo:new String[]{"GET","POST","PUT","DELETE"})assertTrue(PermissoesConfig.permitido(Perfil.COORDENADOR,area,metodo));
  for(var area:new String[]{"certificados","usuarios","google-drive"})
   for(var metodo:new String[]{"GET","POST","PUT","DELETE"})assertFalse(PermissoesConfig.permitido(Perfil.COORDENADOR,area,metodo));
 }
 @Test void professorSoLeAvisos(){
  for(var area:new String[]{"documentos","atas-atividades"})
   for(var metodo:new String[]{"GET","POST","PUT","DELETE"})assertTrue(PermissoesConfig.permitido(Perfil.PROFESSOR,area,metodo));
  assertTrue(PermissoesConfig.permitido(Perfil.PROFESSOR,"avisos","GET"));
  for(var metodo:new String[]{"POST","PUT","DELETE"})assertFalse(PermissoesConfig.permitido(Perfil.PROFESSOR,"avisos",metodo));
  for(var area:new String[]{"certificados","usuarios","google-drive"})assertFalse(PermissoesConfig.permitido(Perfil.PROFESSOR,area,"GET"));
 }
 @Test void gerenciadorSomenteConsultaEAvaliaCertificados(){
  assertTrue(PermissoesConfig.permitido(Perfil.GERENCIADOR_CERTIFICADOS,"certificados","GET"));
  assertTrue(PermissoesConfig.permitido(Perfil.GERENCIADOR_CERTIFICADOS,"certificados","PUT"));
  for(var metodo:new String[]{"POST","DELETE"})assertFalse(PermissoesConfig.permitido(Perfil.GERENCIADOR_CERTIFICADOS,"certificados",metodo));
  for(var area:new String[]{"documentos","atas-atividades","avisos","usuarios","google-drive"})assertFalse(PermissoesConfig.permitido(Perfil.GERENCIADOR_CERTIFICADOS,area,"GET"));
 }
}
