package br.edu.faculdade.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class WordTextServiceTest {
    private final WordTextService service = new WordTextService();

    private MockMultipartFile documento(String corpo) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"+corpo+"</w:body></w:document>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new MockMultipartFile("arquivo", "ata.docx", "application/octet-stream", bytes.toByteArray());
    }

    @Test void extraiParagrafosETabelas() throws Exception {
        assertEquals("Os alunos participaram.\nData: 03/11", service.extrair(documento(
            "<w:p><w:r><w:t>Os alunos </w:t></w:r><w:r><w:t>participaram.</w:t></w:r></w:p>"+
            "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>Data: 03/11</w:t></w:r></w:p></w:tc></w:tr></w:tbl>")));
    }
    @Test void rejeitaDocumentoSemTexto() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.extrair(documento("<w:p/>")));
    }
    @Test void rejeitaArquivoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> service.extrair(new MockMultipartFile("arquivo", "ata.docx", "", new byte[]{1,2,3})));
    }
    @Test void rejeitaDocAntigo() {
        assertThrows(IllegalArgumentException.class, () -> service.extrair(new MockMultipartFile("arquivo", "ata.doc", "", new byte[]{1})));
    }
    @Test void aceitaMaisDeCemMilCaracteres() throws Exception {
        String texto = "a".repeat(110000);
        assertEquals(texto,service.extrair(documento("<w:p><w:r><w:t>"+texto+"</w:t></w:r></w:p>")));
    }
    @Test void aceitaArquivoMaiorQueDezMb() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/media/image.bin"));
            byte[] imagem = new byte[11 * 1024 * 1024];
            new java.util.Random(1).nextBytes(imagem);
            zip.write(imagem);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Ata extensa</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertTrue(bytes.size() > 10 * 1024 * 1024);
        assertEquals("Ata extensa",service.extrair(new MockMultipartFile("arquivo","ata.docx","",bytes.toByteArray())));
    }
}
