package br.edu.faculdade.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentoAnaliseService {
    private final MuseSparkService museSpark;

    public Resultado analisar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O documento não contém texto para analisar.");
        }
        var analise = museSpark.analisar(texto);
        if (analise == null || analise.errosPortugues() == null || analise.errosGramaticais() == null || analise.sugestoes() == null) {
            throw new IllegalStateException("A IA retornou uma análise em formato incompleto.");
        }
        return new Resultado(analise.errosPortugues(), analise.errosGramaticais(), analise.sugestoes(), texto.length());
    }

    public record Resultado(List<String> errosPortugues, List<String> errosGramaticais,
                            List<String> sugestoes, int caracteresAnalisados) {}
}