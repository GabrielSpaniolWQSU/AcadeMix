package br.edu.faculdade.service;

import br.edu.faculdade.service.ModeloDocumentoService.*;
import br.edu.faculdade.controller.*;
import br.edu.faculdade.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ModeloDocumentoServiceTest {
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 private final ModeloDocumentoService modelos=new ModeloDocumentoService();
 private final ModeloWordService word=new ModeloWordService();
 private ObjectNode dados(String modelo) throws Exception {
  var dados=(ObjectNode)json.readTree("""
   {"instituicao":"Faculdade de Ampére - FAMPER","cidade":"Ampére/PR","curso":"Ciência da Computação",
    "data":"2026-11-16","inicio":"19:15","fim":"22:30","local":"Sala de reuniões",
    "assunto":"Planejamento da semana acadêmica","participantes":"Representantes de turma e professores.",
    "discussoes":"Foram discutidas propostas de palestras e oficinas.",
    "decisoes":"A equipe organizará as propostas até 20 de novembro. A programação será deliberada na próxima reunião.",
    "responsavel":"Pessoa Responsável","secretario":"Pessoa Secretária","mandato":"um ano, a partir desta data",
    "nomeParticipante":"Pessoa Participante","rg":"EXEMPLO RG","cpf":"EXEMPLO CPF","dataEmissao":"2026-11-17",
    "registroId":1790000000000001,"membros":[
      {"cargo":"Presidente","nome":"Pessoa Um","periodo":"4º período"},
      {"cargo":"Vice-presidente","nome":"Pessoa Dois","periodo":"6º período"},
      {"cargo":"Secretário Geral","nome":"Pessoa Três","periodo":"6º período"},
      {"cargo":"Secretário Adjunto","nome":"Pessoa Quatro","periodo":"6º período"},
      {"cargo":"1º Suplente","nome":"Pessoa Cinco","periodo":"6º período"},
      {"cargo":"2º Suplente","nome":"Pessoa Seis","periodo":"4º período"}
    ]}
   """);
  dados.put("modelo",modelo);return dados;
 }
 private Pedido pedido(ObjectNode dados) throws Exception{return json.treeToValue(dados,Pedido.class);}
 private Pedido revisado(String modelo) throws Exception{
  var dados=dados(modelo);var previa=modelos.previa(pedido(dados));
  dados.put("titulo",previa.titulo());dados.put("texto",previa.texto());dados.put("revisado",true);
  return pedido(dados);
 }
 @Test void geraTresModelosValidosSemAssinaturasDosExemplos() throws Exception {
  for(String modelo:List.of("NOMEACAO","COMPARECIMENTO","ASSUNTO")){
   var pedido=revisado(modelo);var previa=modelos.revisada(pedido);
   byte[] arquivo=word.gerar(pedido,previa);
   String texto=new WordTextService().extrair(new MockMultipartFile("arquivo","modelo.docx","",arquivo));
   assertTrue(texto.contains(previa.titulo()));
   assertTrue(texto.contains("Pessoa"));
   assertFalse(texto.contains("ANA CRISTINA DA SILVA"));
   assertFalse(texto.contains("JOSE ADEMIR"));
   String saida=System.getProperty("modelos.qa");
   if(saida!=null){Files.createDirectories(Path.of(saida));Files.write(Path.of(saida,modelo+".docx"),arquivo);}
  }
 }
 @Test void mantemCamposAusentesExplicitosESolicitaRevisao() throws Exception {
  var dados=dados("ASSUNTO");dados.put("participantes","");dados.put("decisoes","");
  var p=pedido(dados);var previa=modelos.previa(p);
  assertTrue(previa.texto().contains("[PREENCHER PARTICIPANTES]"));
  assertTrue(previa.texto().contains("[PREENCHER DECISÕES"));
  assertThrows(IllegalArgumentException.class,()->modelos.revisada(p));
 }
 @Test void rejeitaDatasIncoerentesEModeloDesconhecido() throws Exception {
  var dados=dados("COMPARECIMENTO");dados.put("fim","18:00");
  assertThrows(IllegalArgumentException.class,()->modelos.previa(pedido(dados)));
  dados.put("fim","22:30");dados.put("dataEmissao","2026-11-15");
  assertThrows(IllegalArgumentException.class,()->modelos.previa(pedido(dados)));
  dados.put("modelo","outro");
  assertThrows(IllegalArgumentException.class,()->modelos.previa(pedido(dados)));
 }
 @Test void preservaCaracteresEspeciaisNoWord() throws Exception {
  var dados=dados("ASSUNTO");dados.put("instituicao","Instituição & Escola <Exemplo>");
  var p=pedido(dados);var previa=modelos.previa(p);
  var bytes=word.gerar(p,previa);
  assertTrue(new WordTextService().extrair(new MockMultipartFile("arquivo","modelo.docx","",bytes)).contains("Instituição & Escola <Exemplo>"));
 }
 @Test void aplicaRecuoEspacamentoECabecalhoSemRepetirTitulo() throws Exception {
  var dados=dados("ASSUNTO");dados.put("instituicao","Faculdade de - famper");
  dados.put("assunto","ATA DE NOMEAÇÃO DO CENTRO ACADÊMICO");
  var p=pedido(dados);var previa=modelos.previa(p);
  assertEquals("ATA DE NOMEAÇÃO DO CENTRO ACADÊMICO",previa.titulo());
  assertTrue(previa.texto().contains("Discussões: Foram discutidas"));
  var partes=new HashMap<String,String>();
  try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(word.gerar(p,previa)))){
   java.util.zip.ZipEntry entrada;
   while((entrada=zip.getNextEntry())!=null)partes.put(entrada.getName(),new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
  }
  assertTrue(partes.containsKey("word/media/cabecalho.png"));
  var documento=partes.get("word/document.xml");
  assertTrue(documento.contains("w:firstLine=\"709\""));
  assertTrue(documento.contains("w:after=\"120\" w:line=\"360\""));
  assertTrue(documento.contains("<w:widowControl/>"));
  assertFalse(documento.contains("Discussões</w:t>"));
 }
 @Test void salvarDuasVezesNaoDuplicaRegistroNemAnexo() throws Exception {
  var auth=mock(AuthController.class);var registros=mock(AtaRegistroService.class);
  var anexos=mock(AtaAnexoStorageService.class);var sync=mock(GoogleDriveConteudoService.class);
  var p=revisado("NOMEACAO");
  var pronto=new AtaRegistroService.Registro(p.registroId(),"ATA",p.titulo(),"Documento gerado.",p.data(),"ata-nomeacao.docx");
  when(auth.exigirUsuario(any())).thenReturn(new Usuario(2L,"Conta","conta@teste","",Perfil.PROFESSOR,true));
  when(registros.listar(2)).thenReturn(List.of(),List.of(pronto));
  when(registros.salvar(eq(2L),any(),eq(true))).thenReturn(pronto);
  when(registros.anexo(2,p.registroId(),"ata-nomeacao.docx")).thenReturn(pronto);
  var controller=new ModeloDocumentoController(modelos,word,mock(MuseSparkService.class),json,auth,registros,anexos,sync);
  controller.salvar(p,new MockHttpServletRequest());controller.salvar(p,new MockHttpServletRequest());
  verify(anexos,times(1)).salvar(eq(2L),eq(p.registroId()),any());
 }
}
