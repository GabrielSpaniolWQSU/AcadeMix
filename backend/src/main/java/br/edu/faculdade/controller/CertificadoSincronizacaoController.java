package br.edu.faculdade.controller;

import br.edu.faculdade.model.Perfil;
import br.edu.faculdade.service.UsuarioService;
import br.edu.faculdade.service.GoogleDriveTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/certificados")
@CrossOrigin(origins={"http://localhost:4200","http://127.0.0.1:4200"},allowCredentials="true")
@RequiredArgsConstructor
public class CertificadoSincronizacaoController {
    private final CertificadoController certificados;
    private final GoogleDriveController drive;
    private final UsuarioService usuarios;
    private final GoogleDriveTokenStore conexoes;

    @GetMapping("/sincronizar")
    public synchronized CertificadoController.Dados sincronizar(){
        var pastas=new ArrayList<>(certificados.listar().pastas().keySet());
        if(pastas.isEmpty())return certificados.listar();
        var administrador=usuarios.listar().stream()
                .filter(u->u.isAtivo()&&u.getPerfil()==Perfil.ADMIN&&conexoes.existe(u.getId()))
                .findFirst().orElseThrow(()->new IllegalStateException("Peça ao administrador para conectar o Google Drive."));
        drive.atualizarCompartilhado(administrador.getId(),pastas);
        return certificados.listar();
    }
}
