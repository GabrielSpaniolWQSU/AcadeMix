package br.edu.faculdade.controller;

import br.edu.faculdade.service.GoogleDriveConteudoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true")
@RequiredArgsConstructor
public class DocumentoSincronizacaoController {
 private final AuthController auth;
 private final GoogleDriveConteudoService sincronizacao;
 @GetMapping({"/api/documentos/sincronizacao","/api/atas-atividades/sincronizacao"})
 public Map<String,String> status(HttpServletRequest request){
  return Map.of("mensagem",sincronizacao.status(auth.exigirUsuario(request).getId()));
 }
}
