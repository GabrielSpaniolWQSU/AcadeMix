package br.edu.faculdade.controller;

import br.edu.faculdade.service.*;
import br.edu.faculdade.service.AtaRegistroService.Registro;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/atas-atividades")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true")
@RequiredArgsConstructor
public class AtaRegistroController {
 private final AuthController auth;
 private final AtaRegistroService registros;
 private final AtaAnexoStorageService anexos;
 private final GoogleDriveConteudoService sincronizacao;
 @GetMapping public List<Registro> listar(HttpServletRequest request){return registros.listar(auth.exigirUsuario(request).getId());}
 @PostMapping(consumes="multipart/form-data")
 public Registro criar(@RequestPart("dados") Registro dados,@RequestPart(value="arquivo",required=false) MultipartFile arquivo,HttpServletRequest request) throws IOException {
  long usuario=auth.exigirUsuario(request).getId();
  var registro=registros.salvar(usuario,dados,false);
  if(arquivo!=null){
   try{
    anexos.salvar(usuario,registro.id(),arquivo);
    registro=registros.anexo(usuario,registro.id(),arquivo.getOriginalFilename());
   }catch(IOException|RuntimeException e){registros.remover(usuario,registro.id());throw e;}
   sincronizacao.pendente(usuario);
  }
  return registro;
 }
 @PostMapping("/importar") public void importar(@RequestBody List<Registro> dados,HttpServletRequest request){
  long usuario=auth.exigirUsuario(request).getId();
  for(var registro:dados)registros.salvar(usuario,registro,true);
 }
 @PutMapping("/{id}") public Registro editarAnexo(@PathVariable long id,@RequestBody Map<String,String> dados,HttpServletRequest request){
  return registros.anexo(auth.exigirUsuario(request).getId(),id,dados.get("arquivoNome"));
 }
}
