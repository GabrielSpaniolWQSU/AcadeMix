package br.edu.faculdade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuseSparkRespostaTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void aceitaStringsEObjetosNaMesmaAnalise() throws Exception{
  var resultado=json.readValue("""
      {"errosPortugues":[{"original":"fizerm","correcao":"fizeram","explicacao":"Erro de grafia"},"Revisar pontuação"],
       "errosGramaticais":[],"sugestoes":[{"descricao":"Corrigir concordância","detalhes":["os aluno","os alunos"]}]}
      """,MuseSparkService.Analise.class);
  assertEquals("original: fizerm; correcao: fizeram; explicacao: Erro de grafia",resultado.errosPortugues().get(0));
  assertEquals("Revisar pontuação",resultado.errosPortugues().get(1));
  assertEquals("descricao: Corrigir concordância; detalhes: os aluno; os alunos",resultado.sugestoes().get(0));
 }
 @Test void preservaListasDeStringsEVazias() throws Exception{
  var resultado=json.readValue("""
      {"errosPortugues":[],"errosGramaticais":["Concordância"],"sugestoes":[]}
      """,MuseSparkService.Analise.class);
  assertTrue(resultado.errosPortugues().isEmpty());
  assertEquals("Concordância",resultado.errosGramaticais().get(0));
 }
 @Test void rejeitaObjetoSemTexto(){
  assertThrows(JsonProcessingException.class,()->json.readValue("""
      {"errosPortugues":[{"posicao":10}],"errosGramaticais":[],"sugestoes":[]}
      """,MuseSparkService.Analise.class));
 }
}
