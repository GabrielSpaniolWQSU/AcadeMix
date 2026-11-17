package br.edu.faculdade.controller;

import br.edu.faculdade.service.UsuarioService;
import br.edu.faculdade.dto.Dtos.UsuarioReq;
import br.edu.faculdade.model.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AvisoPermissaoTest { @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @Test void validaSenhaEPerfilNoServidor() {
        var usuarios=new UsuarioService(temp.resolve("usuarios.json"));
        var auth=new AuthController(usuarios);
        var request=new MockHttpServletRequest();
        assertEquals(401,assertThrows(ResponseStatusException.class,
                ()->auth.login(new AuthController.Login("admin@faculdade.edu.br","errada"),request)).getStatusCode().value());
        usuarios.criar(new UsuarioReq("Professor","professor@teste.local","senha",Perfil.PROFESSOR,true));
        auth.login(new AuthController.Login("professor@teste.local","senha"),request);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->auth.exigirAdmin(request)).getStatusCode().value());
        auth.login(new AuthController.Login("admin@faculdade.edu.br","Admin@123"),request);
        assertDoesNotThrow(()->auth.exigirAdmin(request));
        auth.logout(request);
        assertEquals(401,assertThrows(ResponseStatusException.class,()->auth.exigirAdmin(request)).getStatusCode().value());
    }
}
