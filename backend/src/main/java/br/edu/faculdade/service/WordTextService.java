package br.edu.faculdade.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.zip.ZipInputStream;

@Service
public class WordTextService {
    private static final int MAX_XML = 32 * 1024 * 1024;
    private static final int MAX_TEXT = 1_000_000;
    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    public String extrair(MultipartFile arquivo) {
        if (arquivo.isEmpty()) throw new IllegalArgumentException("O anexo está vazio.");
        if (arquivo.getSize() > 100L * 1024 * 1024) throw new IllegalArgumentException("Envie um Word de até 100 MB.");
        String nome = arquivo.getOriginalFilename();
        if (nome == null || !nome.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("Para analisar o Word, salve o documento como .docx e anexe novamente. O formato .doc antigo não é suportado.");
        }
        try (var zip = new ZipInputStream(arquivo.getInputStream())) {
            long total = 0;
            int entries = 0;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 10000) throw new IllegalArgumentException("O documento contém entradas demais.");
                boolean document = entry.getName().equals("word/document.xml");
                var data = document ? new java.io.ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (total > 200L * 1024 * 1024 || (data != null && data.size() + count > MAX_XML)) {
                        throw new IllegalArgumentException("O conteúdo descompactado do Word excede o limite de análise.");
                    }
                    if (data != null) data.write(buffer, 0, count);
                }
                if (document) return lerXml(data.toByteArray());
            }
            throw new IllegalArgumentException("O arquivo não é um documento Word .docx válido.");
        } catch (IllegalArgumentException e) { throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Não foi possível ler o Word. Verifique se está válido e sem senha.");
        }
    }

    private String lerXml(byte[] xml) throws Exception {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        var text = new StringBuilder();
        boolean inText = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) throw new IllegalArgumentException("O Word contém XML não suportado.");
                if (event == XMLStreamConstants.START_ELEMENT && WORD_NS.equals(reader.getNamespaceURI())) {
                    switch (reader.getLocalName()) {
                        case "t" -> inText = true;
                        case "tab" -> text.append('\t');
                        case "br", "cr" -> text.append('\n');
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && inText) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && WORD_NS.equals(reader.getNamespaceURI())) {
                    if (reader.getLocalName().equals("t")) inText = false;
                    if (reader.getLocalName().equals("p") || reader.getLocalName().equals("tr")) text.append('\n');
                    if (reader.getLocalName().equals("tc")) text.append('\t');
                }
                if (text.length() > MAX_TEXT) throw new IllegalArgumentException("O Word excede o limite de 1 milhão de caracteres de texto.");
            }
        } finally { reader.close(); }
        if (text.toString().isBlank()) throw new IllegalArgumentException("O Word não contém texto extraível. Documentos com apenas imagens precisam de reconhecimento de texto.");
        return text.toString().trim();
    }
}
