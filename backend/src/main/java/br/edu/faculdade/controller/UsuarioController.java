package br.edu.faculdade.controller;

import br.edu.faculdade.dto.Dtos.UsuarioReq;
import br.edu.faculdade.model.Usuario;
import br.edu.faculdade.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true")
@RequiredArgsConstructor
public class UsuarioController {
 private final UsuarioService service;
 private final AuthController auth;
 @GetMapping public List<Usuario> listar(HttpServletRequest request){auth.exigirAdmin(request);return service.listar();}
 @PostMapping public Usuario criar(@RequestBody UsuarioReq r,HttpServletRequest request){auth.exigirAdmin(request);return service.criar(r);}
 @PutMapping("/{id}") public Usuario editar(@PathVariable Long id,@RequestBody UsuarioReq r,HttpServletRequest request){auth.exigirAdmin(request);return service.editar(id,r);}
}
