package br.edu.faculdade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WordCorrecaoServiceTest {
    private byte[] zip(Map<String,byte[]> entries) throws Exception {
        var data=new ByteArrayOutputStream();
        try(var out=new ZipOutputStream(data)) {
            for(var e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}
        }
        return data.toByteArray();
    }
    private Map<String,byte[]> unzip(byte[] bytes) throws Exception {
        var entries=new HashMap<String,byte[]>();
        try(var in=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for(var e=in.getNextEntry();e!=null;e=in.getNextEntry())entries.put(e.getName(),in.readAllBytes());
        }
        return entries;
    }
    private MockMultipartFile arquivo(String texto) throws Exception {
        String xml="<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:tbl><w:tr><w:tc><w:p><w:pPr><w:pStyle w:val=\"Titulo\"/></w:pPr>"+texto+"</w:p></w:tc></w:tr></w:tbl></w:body></w:document>";
        return new MockMultipartFile("arquivo","ata.docx","",zip(Map.of("word/document.xml",xml.getBytes(StandardCharsets.UTF_8),"word/media/image.png",new byte[]{1,2,3},"word/styles.xml","estilos".getBytes(StandardCharsets.UTF_8))));
    }
    private WordCorrecaoService service(MuseSparkService ia) {return new WordCorrecaoService(new WordTextService(),ia,new ObjectMapper());}
    @Test void corrigeEntreRunsEPreservaTabelaEstilosImagemEOriginal() throws Exception {
        var ia=mock(MuseSparkService.class);
        when(ia.corrigir(anyString(),eq("analise"))).thenReturn(new MuseSparkService.Correcoes(List.of(new MuseSparkService.Correcao(0,"Os aluno participou","Os alunos participaram"))));
        var original=arquivo("<w:r><w:rPr><w:b/></w:rPr><w:t>Os alu</w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t>no participou da reunião.</w:t></w:r>");
        byte[] antes=original.getBytes().clone();
        var corrigido=service(ia).corrigir(original,"analise").arquivo();
        assertEquals("Os alunos participaram da reunião.",new WordTextService().extrair(new MockMultipartFile("arquivo","copia.docx","",corrigido)));
        assertArrayEquals(antes,original.getBytes());
        var entries=unzip(corrigido);var old=unzip(antes);
        assertArrayEquals(old.get("word/media/image.png"),entries.get("word/media/image.png"));
        assertArrayEquals(old.get("word/styles.xml"),entries.get("word/styles.xml"));
        String xml=new String(entries.get("word/document.xml"),StandardCharsets.UTF_8);
        assertTrue(xml.contains("<w:tbl>"));assertTrue(xml.contains("<w:b/>"));assertTrue(xml.contains("<w:i/>"));assertTrue(xml.contains("Titulo"));
        verify(ia).corrigir(anyString(),eq("analise"));
    }
    @Test void aplicaTrechoUnicoMesmoComSugestoesInvalidasOuAmbiguas() throws Exception {
        var ia=mock(MuseSparkService.class);
        when(ia.corrigir(anyString(),anyString())).thenReturn(new MuseSparkService.Correcoes(List.of(
                new MuseSparkService.Correcao(0,"erro","certo"),
                new MuseSparkService.Correcao(0,"ausente","presente"),
                new MuseSparkService.Correcao(0,"Os aluno","Os alunos"))));
        var doc=arquivo("<w:r><w:t>erro erro. Os aluno chegaram.</w:t></w:r>");
        var resultado=service(ia).corrigir(doc,"{}");
        assertEquals(1,resultado.aplicadas());
        assertEquals(2,resultado.ignoradas());
        assertEquals("erro erro. Os alunos chegaram.",new WordTextService().extrair(
                new MockMultipartFile("arquivo","copia.docx","",resultado.arquivo())));
    }
    @Test void aplicaVariasCorrecoesSemDeslocarIndices() throws Exception {
        var ia=mock(MuseSparkService.class);
        when(ia.corrigir(anyString(),anyString())).thenReturn(new MuseSparkService.Correcoes(List.of(new MuseSparkService.Correcao(0,"Os aluno","Os alunos"),new MuseSparkService.Correcao(0,"das prova","das provas"))));
        var doc=arquivo("<w:r><w:t>Os aluno falaram das prova.</w:t></w:r>");
        var output=service(ia).corrigir(doc,"{}").arquivo();
        assertEquals("Os alunos falaram das provas.",new WordTextService().extrair(new MockMultipartFile("arquivo","copia.docx","",output)));
    }
    @Test void corrigeFraseMesmoQuandoIaSimplificaEspacos() throws Exception {
        var ia=mock(MuseSparkService.class);
        when(ia.corrigir(anyString(),anyString())).thenReturn(new MuseSparkService.Correcoes(List.of(
                new MuseSparkService.Correcao(0,"por quÊ fizerm aquilo no meio da escola",
                        "porque fizeram aquilo no meio da escola."))));
        var doc=arquivo("<w:r><w:t>Os alunos estavam errados por quÊ</w:t></w:r>"
                +"<w:r><w:t>  fizerm aquilo no meio da escola</w:t></w:r>");
        var resultado=service(ia).corrigir(doc,"{}");
        assertEquals(1,resultado.aplicadas());
        assertEquals(0,resultado.ignoradas());
        assertEquals("Os alunos estavam errados porque fizeram aquilo no meio da escola.",
                new WordTextService().extrair(new MockMultipartFile("arquivo","copia.docx","",resultado.arquivo())));
    }
    @Test void semCorrecoesMantemArquivoIdentico() throws Exception {
        var ia=mock(MuseSparkService.class);
        when(ia.corrigir(anyString(),anyString())).thenReturn(new MuseSparkService.Correcoes(List.of()));
        var doc=arquivo("<w:r><w:t>Texto correto.</w:t></w:r>");
        assertArrayEquals(doc.getBytes(),service(ia).corrigir(doc,"{}").arquivo());
    }
}
