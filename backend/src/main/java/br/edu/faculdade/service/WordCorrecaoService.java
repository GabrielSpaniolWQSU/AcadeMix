package br.edu.faculdade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.XMLConstants;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
public class WordCorrecaoService {
    private final WordTextService wordText;
    private final MuseSparkService ia;
    private final ObjectMapper json;
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern ESPACOS = Pattern.compile("[\\p{Zs}\\t]+");

    public record Resultado(byte[] arquivo, int aplicadas, int ignoradas) {}

    public Resultado corrigir(MultipartFile arquivo, String analise) {
        wordText.extrair(arquivo); // Reutiliza a validação de tamanho, formato e XML.
        try {
            byte[] xml = null;
            try (var zip = new ZipInputStream(arquivo.getInputStream())) {
                for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                    if (e.getName().equals("word/document.xml")) { xml = zip.readAllBytes(); break; }
                }
            }
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            var paragrafos = document.getElementsByTagNameNS(W, "p");
            var textos = new ArrayList<List<Element>>();
            var dados = new ArrayList<Map<String,Object>>();
            for (int i=0;i<paragrafos.getLength();i++) {
                var elementos = new ArrayList<Element>();
                var nodes = ((Element)paragrafos.item(i)).getElementsByTagNameNS(W,"t");
                for(int j=0;j<nodes.getLength();j++) {
                    Element element = (Element)nodes.item(j);
                    Node parent = element.getParentNode();
                    while(parent != null && !(parent instanceof Element p && W.equals(p.getNamespaceURI()) && "p".equals(p.getLocalName()))) parent=parent.getParentNode();
                    if(parent==paragrafos.item(i)) elementos.add(element);
                }
                textos.add(elementos);
                String texto = juntar(elementos);
                if(!texto.isBlank()) dados.add(Map.of("id",i,"texto",texto));
            }
            var resposta = ia.corrigir(json.writeValueAsString(dados), analise);
            if(resposta==null || resposta.correcoes()==null) throw new IllegalStateException("A IA retornou correções incompletas.");
            record Edicao(int inicio,int fim,String texto) {}
            var edicoes = new HashMap<Integer,List<Edicao>>();
            int ignoradas = 0;
            int aplicadas = 0;
            for(var correcao:resposta.correcoes()) {
                if(correcao==null || correcao.paragrafo()<0 || correcao.paragrafo()>=textos.size() || correcao.original()==null || correcao.original().isBlank() || correcao.corrigido()==null || correcao.corrigido().isBlank()) {
                    ignoradas++;
                    continue;
                }
                if (correcao.original().equals(correcao.corrigido())) continue;
                String texto=juntar(textos.get(correcao.paragrafo()));
                int inicio=texto.indexOf(correcao.original());
                int fim = inicio + correcao.original().length();
                if (inicio < 0) {
                    var trecho = ESPACOS.matcher(correcao.original());
                    var expressao = new StringBuilder();
                    int posicao = 0;
                    while (trecho.find()) {
                        expressao.append(Pattern.quote(correcao.original().substring(posicao, trecho.start())))
                                .append(ESPACOS.pattern());
                        posicao = trecho.end();
                    }
                    expressao.append(Pattern.quote(correcao.original().substring(posicao)));
                    var busca = Pattern.compile(expressao.toString()).matcher(texto);
                    if (busca.find()) {
                        inicio = busca.start();
                        fim = busca.end();
                        if (busca.find(inicio + 1)) inicio = -1;
                    }
                } else if (texto.indexOf(correcao.original(), inicio + 1) >= 0) {
                    inicio = -1;
                }
                if (inicio < 0) {
                    ignoradas++;
                    continue;
                }
                var lista = edicoes.computeIfAbsent(correcao.paragrafo(),k->new ArrayList<>());
                var edicao = new Edicao(inicio,fim,correcao.corrigido());
                if (!lista.contains(edicao)) lista.add(edicao);
            }
            for(var entry:edicoes.entrySet()) {
                var lista=entry.getValue();
                lista.sort(Comparator.comparingInt(Edicao::inicio));
                var seguras = new ArrayList<Edicao>();
                int maiorFimAnterior = 0;
                for (int i = 0; i < lista.size(); i++) {
                    var edicao = lista.get(i);
                    boolean sobreposta = edicao.inicio() < maiorFimAnterior
                            || (i + 1 < lista.size() && lista.get(i + 1).inicio() < edicao.fim());
                    maiorFimAnterior = Math.max(maiorFimAnterior, edicao.fim());
                    if (sobreposta) ignoradas++;
                    else seguras.add(edicao);
                }
                for (int i = seguras.size() - 1; i >= 0; i--) {
                    var edicao = seguras.get(i);
                    substituir(textos.get(entry.getKey()),edicao.inicio(),edicao.fim(),edicao.texto());
                    aplicadas++;
                }
            }
            if(aplicadas == 0) return new Resultado(arquivo.getBytes(), aplicadas, ignoradas);
            var tf=TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var xmlCorrigido=new ByteArrayOutputStream();
            tf.newTransformer().transform(new DOMSource(document),new StreamResult(xmlCorrigido));
            var output=new ByteArrayOutputStream();
            try(var zip=new ZipInputStream(arquivo.getInputStream());var out=new ZipOutputStream(output)) {
                for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()) {
                    out.putNextEntry(new ZipEntry(e.getName()));
                    if(e.getName().equals("word/document.xml")) out.write(xmlCorrigido.toByteArray());
                    else zip.transferTo(out);
                    out.closeEntry();
                }
            }
            return new Resultado(output.toByteArray(), aplicadas, ignoradas);
        } catch(IllegalArgumentException | IllegalStateException e) { throw e;
        } catch(Exception e) { throw new IllegalStateException("Não foi possível gerar a cópia corrigida do Word.",e); }
    }

    private static String juntar(List<Element> elementos) {
        var texto=new StringBuilder();
        elementos.forEach(e->texto.append(e.getTextContent()));
        return texto.toString();
    }
    private static void substituir(List<Element> elementos,int inicio,int fim,String novo) {
        int pos=0;
        boolean inserido=false;
        for(var e:elementos) {
            String texto=e.getTextContent();
            int proximo=pos+texto.length();
            if(pos<fim && proximo>inicio) {
                String prefixo=texto.substring(0,Math.max(0,inicio-pos));
                String sufixo=texto.substring(Math.min(texto.length(),fim-pos));
                e.setTextContent(prefixo+(inserido?"":novo)+sufixo);
                e.setAttributeNS(XMLConstants.XML_NS_URI,"xml:space","preserve");
                inserido=true;
            }
            pos=proximo;
        }
    }
}
