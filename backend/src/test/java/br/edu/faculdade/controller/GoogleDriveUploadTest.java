package br.edu.faculdade.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.*;
import java.nio.file.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

class GoogleDriveUploadTest {
 @TempDir Path temp;
 @Test void criaComConteudoEAtualizaMesmoId() throws Exception {
  var builder=RestClient.builder();
  var server=MockRestServiceServer.bindTo(builder).build();
  var drive=new GoogleDriveController("id","secret","http://localhost/callback");
  ReflectionTestUtils.setField(drive,"google",builder.build());
  var arquivo=temp.resolve("arquivo.txt");Files.writeString(arquivo,"conteudo real");
  String consulta="https://www.googleapis.com/drive/v3/files/id-remoto?fields=id";
  server.expect(requestTo(consulta)).andRespond(withStatus(HttpStatus.NOT_FOUND));
  server.expect(requestTo("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"))
   .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer token"))
   .andExpect(content().string(containsString("conteudo real")))
   .andExpect(content().string(containsString("\"parents\":[\"pasta\"]")))
   .andRespond(withSuccess());
  server.expect(requestTo(consulta)).andRespond(withSuccess("{\"id\":\"id-remoto\"}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://www.googleapis.com/upload/drive/v3/files/id-remoto?uploadType=multipart"))
   .andExpect(method(HttpMethod.PATCH)).andExpect(content().string(containsString("conteudo real")))
   .andRespond(withSuccess());
  drive.enviarArquivo("token","id-remoto","arquivo.txt","pasta",arquivo);
  drive.enviarArquivo("token","id-remoto","arquivo.txt","pasta",arquivo);
  server.verify();
 }
 @Test void clienteRealSuportaPatchParaAtualizarEExcluir()throws Exception{
  var servidor=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var metodo=new java.util.concurrent.atomic.AtomicReference<String>();
  var corpo=new java.util.concurrent.atomic.AtomicReference<String>();
  servidor.createContext("/arquivo",exchange->{
   metodo.set(exchange.getRequestMethod());corpo.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   exchange.sendResponseHeaders(204,-1);exchange.close();
  });
  servidor.start();
  try{
   var controller=new GoogleDriveController("id","secret","http://localhost/callback");
   var cliente=(RestClient)ReflectionTestUtils.getField(controller,"google");
   cliente.patch().uri("http://127.0.0.1:"+servidor.getAddress().getPort()+"/arquivo")
    .contentType(MediaType.APPLICATION_JSON).body(java.util.Map.of("trashed",true)).retrieve().toBodilessEntity();
   org.junit.jupiter.api.Assertions.assertEquals("PATCH",metodo.get());
   org.junit.jupiter.api.Assertions.assertEquals("{\"trashed\":true}",corpo.get());
  }finally{servidor.stop(0);}
 }
}
