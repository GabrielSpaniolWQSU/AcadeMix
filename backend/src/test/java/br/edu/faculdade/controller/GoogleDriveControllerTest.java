package br.edu.faculdade.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.*;

class GoogleDriveControllerTest {
    private static final String CALLBACK = "http://localhost:8080/api/google-drive/callback";

    @Test
    void exigeCredenciaisParaIniciar() {
        var controller = new GoogleDriveController("", "", CALLBACK);
        var request = new MockHttpServletRequest();
        assertFalse(controller.status(request).configurado());
        assertTrue(controller.conectar(request).getHeaders().getLocation().toString()
                .endsWith("resultado=sem-configuracao"));
        assertNull(request.getSession(false));
    }

    @Test
    void aceitaRetornoApenasNaSessaoQueIniciouAutorizacaoEConsomeState() {
        var controller = new GoogleDriveController("id", "segredo", CALLBACK);
        var inicio = new MockHttpServletRequest();
        var url = controller.conectar(inicio).getHeaders().getLocation();
        assertEquals("accounts.google.com", url.getHost());
        var parametros = UriComponentsBuilder.fromUri(url).build().getQueryParams();
        assertTrue(parametros.getFirst("scope").contains("https://www.googleapis.com/auth/drive.metadata.readonly"));
        assertTrue(parametros.getFirst("scope").contains("https://www.googleapis.com/auth/drive.file"));
        assertFalse(url.toString().contains("segredo"));
        var state = parametros.getFirst("state");
        assertNotNull(state);

        var outraSessao = new MockHttpServletRequest();
        assertTrue(controller.callback(outraSessao, state, null, "access_denied").getHeaders()
                .getLocation().toString().endsWith("resultado=sessao-expirada"));
        var retorno = new MockHttpServletRequest();
        retorno.setSession((MockHttpSession) inicio.getSession(false));
        assertTrue(controller.callback(retorno, state, null, "access_denied").getHeaders()
                .getLocation().toString().endsWith("resultado=cancelado"));
        assertTrue(controller.callback(retorno, state, null, "access_denied").getHeaders()
                .getLocation().toString().endsWith("resultado=sessao-expirada"));
    }
}
