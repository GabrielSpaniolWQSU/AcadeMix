package br.edu.faculdade.controller;

import br.edu.faculdade.service.AtaAnexoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/atas-atividades/anexos")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true",exposedHeaders="X-Arquivo-Nome")
@RequiredArgsConstructor
public class AtaAnexoController {
 private final AuthController auth;
 private final AtaAnexoStorageService storage;
 private final br.edu.faculdade.service.GoogleDriveConteudoService sincronizacao;
 @PutMapping("/{id}") public void salvar(@PathVariable long id,@RequestParam MultipartFile arquivo,@RequestHeader("X-Usuario-Id") long dono,HttpServletRequest request) throws IOException {
  long usuario=auth.exigirUsuario(request).getId();
  if(dono!=usuario)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
  storage.salvar(usuario,id,arquivo);
  sincronizacao.pendente(usuario);
 }
 @GetMapping("/{id}") public ResponseEntity<byte[]> baixar(@PathVariable long id,HttpServletRequest request) throws IOException {
  long usuario=auth.exigirUsuario(request).getId();
  var anexo=storage.listar(usuario).stream().filter(a->a.id()==id).findFirst()
    .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
    .header("X-Arquivo-Nome",java.net.URLEncoder.encode(anexo.nome(),java.nio.charset.StandardCharsets.UTF_8))
    .header("Cache-Control","no-store").body(Files.readAllBytes(storage.conteudo(usuario,anexo)));
 }
}
