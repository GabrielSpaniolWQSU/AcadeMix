package br.edu.faculdade.controller;

import br.edu.faculdade.service.UsuarioService;
import br.edu.faculdade.model.Usuario;
import br.edu.faculdade.model.Perfil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"}, allowCredentials="true")
@RequiredArgsConstructor
public class AuthController {
    private final UsuarioService usuarios;
    public record Login(String email, String senha) {}
    @GetMapping("/me")
    public Usuario atual(HttpServletRequest request) { return exigirUsuario(request); }
    @PostMapping("/login")
    public Usuario login(@RequestBody Login dados, HttpServletRequest request) {
        var usuario = usuarios.listar().stream()
                .filter(u -> u.isAtivo() && u.getEmail().equalsIgnoreCase(dados.email())
                        && usuarios.senhaCorreta(u,dados.senha()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var sessao=request.getSession();
        if(!usuario.getId().equals(sessao.getAttribute("usuarioId"))){
            sessao.removeAttribute("googleDriveConexao");
            sessao.removeAttribute("googleDriveAutorizacao");
        }
        sessao.setAttribute("usuarioId", usuario.getId());
        request.changeSessionId();
        return usuario;
    }
    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        var sessao=request.getSession(false);
        if(sessao!=null)sessao.invalidate();
    }
    public Usuario exigirUsuario(HttpServletRequest request) {
        var sessao=request.getSession(false);
        var id=sessao==null?null:sessao.getAttribute("usuarioId");
        return usuarios.listar().stream().filter(u -> u.isAtivo() && u.getId().equals(id))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    public void exigirAdmin(HttpServletRequest request) {
        if(exigirUsuario(request).getPerfil()!=Perfil.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    public void exigirEditorAvisos(HttpServletRequest request) {
        var perfil=exigirUsuario(request).getPerfil();
        if(perfil!=Perfil.ADMIN && perfil!=Perfil.COORDENADOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
