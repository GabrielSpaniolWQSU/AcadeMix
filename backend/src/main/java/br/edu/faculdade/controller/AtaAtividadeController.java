package br.edu.faculdade.controller;

import br.edu.faculdade.service.MuseSparkService;
import br.edu.faculdade.service.WordTextService;
import br.edu.faculdade.service.DocumentoAnaliseService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import br.edu.faculdade.service.MuseSparkService.Analise;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/atas-atividades")
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"},
        allowCredentials = "true", exposedHeaders = {"X-Correcoes-Aplicadas", "X-Correcoes-Ignoradas"})
@RequiredArgsConstructor
public class AtaAtividadeController {
    private final MuseSparkService museSpark;
    private final WordTextService wordText;
    private final DocumentoAnaliseService documentoAnalise;
    private final br.edu.faculdade.service.WordCorrecaoService wordCorrecao;

    @PostMapping(value = "/corrigir-documento", consumes = "multipart/form-data")
    public org.springframework.http.ResponseEntity<byte[]> corrigirDocumento(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "analise", defaultValue = "{}") String analise) {
        var resultado = wordCorrecao.corrigir(arquivo, analise);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header("Content-Disposition", "attachment; filename=\"documento-corrigido.docx\"")
                .header("X-Correcoes-Aplicadas", Integer.toString(resultado.aplicadas()))
                .header("X-Correcoes-Ignoradas", Integer.toString(resultado.ignoradas()))
                .body(resultado.arquivo());
    }

    @PostMapping(value = "/analisar-documento", consumes = "multipart/form-data")
    public DocumentoAnaliseService.Resultado analisarDocumento(@RequestParam("arquivo") MultipartFile arquivo) {
        return documentoAnalise.analisar(wordText.extrair(arquivo));
    }

    @PostMapping("/analisar")
    public Analise analisar(@RequestBody AnaliseReq requisicao) {
        return museSpark.analisar(requisicao.texto());
    }

    public record AnaliseReq(String texto) {}
}
