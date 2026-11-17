package br.edu.faculdade.controller;

import br.edu.faculdade.service.*;
import br.edu.faculdade.service.ModeloDocumentoService.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/atas-atividades/modelos")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true")
@RequiredArgsConstructor
public class ModeloDocumentoController {
 private final ModeloDocumentoService modelos;
 private final ModeloWordService word;
 private final MuseSparkService ia;
 private final ObjectMapper json;
 private final AuthController auth;
 private final AtaRegistroService registros;
 private final AtaAnexoStorageService anexos;
 private final GoogleDriveConteudoService sincronizacao;
 @GetMapping("/cabecalho") public ResponseEntity<org.springframework.core.io.Resource> cabecalho(){
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(new org.springframework.core.io.ClassPathResource("modelos/cabecalho-famper.png"));
 }
 @PostMapping("/previa") public Previa previa(@RequestBody Pedido pedido){return modelos.previa(pedido);}
 @PostMapping("/organizar") public Previa organizar(@RequestBody Pedido pedido) throws IOException {
  if(!"ASSUNTO".equals(pedido.modelo()))throw new IllegalArgumentException("A organização por IA é usada na ata por assunto.");
  var previa=modelos.previa(pedido);
  String texto=pedido.texto()==null||pedido.texto().isBlank()?previa.texto():pedido.texto();
  if(texto.length()>60000)throw new IllegalArgumentException("Reduza o texto para até 60.000 caracteres.");
  var rascunho=ia.redigirAta(json.writeValueAsString(pedido),texto);
  return new Previa(previa.titulo(),rascunho.texto(),previa.assinaturas());
 }
 @PostMapping("/baixar") public ResponseEntity<byte[]> baixar(@RequestBody Pedido pedido){
  var previa=modelos.revisada(pedido);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
   .header("Content-Disposition","attachment; filename=\""+nome(pedido)+"\"").body(word.gerar(pedido,previa));
 }
 @PostMapping("/salvar") public synchronized AtaRegistroService.Registro salvar(@RequestBody Pedido pedido,HttpServletRequest request) throws IOException {
  long usuario=auth.exigirUsuario(request).getId();
  var previa=modelos.revisada(pedido);
  if(pedido.registroId()==null||pedido.registroId()<=0||pedido.registroId()>9007199254740991L)
   throw new IllegalArgumentException("Reabra o assistente para iniciar a geração.");
  var existente=registros.listar(usuario).stream().filter(r->r.id().equals(pedido.registroId())).findFirst();
  if(existente.isPresent()){
   var registro=existente.get();
   if(!nome(pedido).equals(registro.arquivoNome())||!registro.titulo().equals(previa.titulo()))
    throw new IllegalArgumentException("Esse identificador já foi utilizado. Reabra o assistente.");
   return registro;
  }
  byte[] conteudo=word.gerar(pedido,previa);
  String tipo="COMPARECIMENTO".equals(pedido.modelo())?"ATIVIDADE":"ATA";
  var dados=new AtaRegistroService.Registro(pedido.registroId(),tipo,previa.titulo(),
   "Documento criado pelo assistente com o modelo "+pedido.modelo()+".",pedido.data(),null);
  var registro=registros.salvar(usuario,dados,true);
  try{
   anexos.salvar(usuario,registro.id(),new ArquivoGerado(nome(pedido),conteudo));
   registro=registros.anexo(usuario,registro.id(),nome(pedido));
  }catch(IOException|RuntimeException e){registros.remover(usuario,registro.id());throw e;}
  sincronizacao.pendente(usuario);
  return registro;
 }
 private String nome(Pedido pedido){return switch(pedido.modelo()){
  case "NOMEACAO" -> "ata-nomeacao.docx";
  case "COMPARECIMENTO" -> "declaracao-comparecimento.docx";
  default -> "ata-por-assunto.docx";
 };}
 private record ArquivoGerado(String nome,byte[] conteudo) implements MultipartFile {
  public String getName(){return "arquivo";}
  public String getOriginalFilename(){return nome;}
  public String getContentType(){return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";}
  public boolean isEmpty(){return conteudo.length==0;}
  public long getSize(){return conteudo.length;}
  public byte[] getBytes(){return conteudo;}
  public InputStream getInputStream(){return new ByteArrayInputStream(conteudo);}
  public void transferTo(File destino) throws IOException {Files.write(destino.toPath(),conteudo);}
 }
}
