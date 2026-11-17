package br.edu.faculdade.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentoAnaliseServiceTest {
    @Test void enviaDocumentoLongoCompletoEmUmaUnicaChamada() {
        var ia = mock(MuseSparkService.class);
        String texto = "a".repeat(11999) + "😀" + "b".repeat(110000);
        when(ia.analisar(texto)).thenReturn(new MuseSparkService.Analise(List.of("Erro"), List.of(), List.of("Revisar")));
        var resultado = new DocumentoAnaliseService(ia).analisar(texto);
        assertEquals(texto.length(), resultado.caracteresAnalisados());
        assertEquals(List.of("Erro"), resultado.errosPortugues());
        assertEquals(List.of("Revisar"), resultado.sugestoes());
        verify(ia).analisar(texto);
        verifyNoMoreInteractions(ia);
    }
    @Test void rejeitaTextoVazioSemChamarIA() {
        var ia = mock(MuseSparkService.class);
        var service = new DocumentoAnaliseService(ia);
        assertThrows(IllegalArgumentException.class, () -> service.analisar(" "));
        assertThrows(IllegalArgumentException.class, () -> service.analisar(null));
        verifyNoInteractions(ia);
    }
    @Test void preservaFalhaDaIA() {
        var ia = mock(MuseSparkService.class);
        when(ia.analisar(anyString())).thenThrow(new IllegalStateException("HTTP 429"));
        var erro = assertThrows(IllegalStateException.class, () -> new DocumentoAnaliseService(ia).analisar("Texto"));
        assertEquals("HTTP 429", erro.getMessage());
        verify(ia).analisar("Texto");
    }
    @Test void rejeitaRespostaIncompleta() {
        var ia = mock(MuseSparkService.class);
        when(ia.analisar(anyString())).thenReturn(new MuseSparkService.Analise(null, List.of(), List.of()));
        assertThrows(IllegalStateException.class, () -> new DocumentoAnaliseService(ia).analisar("Texto"));
    }
}