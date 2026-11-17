package br.edu.faculdade.service;

import br.edu.faculdade.controller.CertificadoController.*;
import br.edu.faculdade.controller.GoogleDriveController.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class RelatorioHorasServiceTest {
 private Map<String,String> partes(byte[] arquivo)throws IOException{
  var partes=new HashMap<String,String>();
  try(var zip=new ZipInputStream(new ByteArrayInputStream(arquivo))){
   ZipEntry e;while((e=zip.getNextEntry())!=null)partes.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));
  }return partes;
 }
 @Test void exportaColunasNumericasComTotalSomenteDeAprovados()throws Exception{
  var gabriel=new PastaCertificados("g","Gabriel Luiz Klee Spaniol",List.of(new Certificado("1","Java.pdf",""),new Certificado("2","Evento.pdf","")),List.of());
  var joao=new PastaCertificados("j","João Matheus",List.of(new Certificado("3","Curso.pdf","")),List.of());
  var outro=new PastaCertificados("m","=SUM(1,2) & Ma theus",List.of(new Certificado("4","Outro.pdf","")),List.of());
  var raiz=new PastaCertificados("r","Certificados",List.of(),List.of(gabriel,joao,outro));
  var dados=new Dados(Map.of("r",raiz,"g",gabriel),Map.of("1",new Avaliacao("APROVADO",75.5,null),"2",new Avaliacao("PENDENTE",100.0,null),"3",new Avaliacao("REPROVADO",50.0,"Inválido"),"4",new Avaliacao("APROVADO",0.0,null)));
  byte[] arquivo=new RelatorioHorasService().gerar(dados);var p=partes(arquivo);String sheet=p.get("xl/worksheets/sheet1.xml");
  var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
  var doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(sheet.getBytes(StandardCharsets.UTF_8)));
  var xpath=javax.xml.xpath.XPathFactory.newInstance().newXPath();
  assertEquals("75.5",xpath.evaluate("//*[local-name()='c'][@r='B5']/*[local-name()='v']",doc));
  assertEquals("2.0",xpath.evaluate("//*[local-name()='c'][@r='B4']/*[local-name()='v']",doc));
  assertEquals("SUM(B8:B9)",xpath.evaluate("//*[local-name()='c'][@r='B10']/*[local-name()='f']",doc));
  assertTrue(sheet.contains("inlineStr"));assertTrue(sheet.contains("=SUM(1,2) &amp; Ma theus"));
  assertFalse(sheet.contains("<f>=SUM(1,2)"));assertTrue(sheet.contains("autoFilter ref=\"A7:B9\""));
  assertTrue(sheet.contains("state=\"frozen\""));assertTrue(p.get("xl/styles.xml").contains("0C4A83"));
  String pasta=System.getProperty("relatorio.qa");if(pasta!=null){Files.createDirectories(Path.of(pasta));Files.write(Path.of(pasta,"horas-aprovadas.xlsx"),arquivo);}
 }
 @Test void exportaSemAlunosSemIntervaloOuFormulaInvalida()throws Exception{
  var sheet=partes(new RelatorioHorasService().gerar(new Dados(Map.of(),Map.of()))).get("xl/worksheets/sheet1.xml");
  assertTrue(sheet.contains("Nenhuma aprovação encontrada"));assertTrue(sheet.contains("<f>0</f><v>0.0</v>"));
  assertFalse(sheet.contains("SUM(B8:B7)"));assertFalse(sheet.contains("<autoFilter"));
 }
 @Test void filtraPelaAprovacaoComLimitesInclusivosESemInventarDataAntiga()throws Exception{
  var pasta=new PastaCertificados("p","Aluno",List.of(new Certificado("a","A.pdf","2020-01-01"),new Certificado("b","B.pdf",""),new Certificado("c","C.pdf",""),new Certificado("d","D.pdf","")),List.of());
  var dados=new Dados(Map.of("p",pasta),Map.of(
   "a",new Avaliacao("APROVADO",10.0,null,"2026-11-01T03:00:00Z"),
   "b",new Avaliacao("APROVADO",20.0,null,"2026-11-16T02:59:59Z"),
   "c",new Avaliacao("APROVADO",40.0,null,"2026-11-16T03:00:00Z"),
   "d",new Avaliacao("APROVADO",80.0,null)));
  var inicio=java.time.LocalDate.of(2026,11,1);var fim=java.time.LocalDate.of(2026,11,15);
  String sheet=partes(new RelatorioHorasService().gerar(dados,inicio,fim)).get("xl/worksheets/sheet1.xml");
  assertTrue(sheet.contains("<f>B9</f><v>30.0</v>"));
  assertTrue(sheet.contains("01/11/2026 até 15/11/2026"));
  assertTrue(sheet.contains("1 aprovação(ões) sem data"));
  String historico=partes(new RelatorioHorasService().gerar(dados)).get("xl/worksheets/sheet1.xml");
  assertTrue(historico.contains("<f>B9</f><v>150.0</v>"));
  assertThrows(IllegalArgumentException.class,()->new RelatorioHorasService().gerar(dados,fim,inicio));
 }
}
