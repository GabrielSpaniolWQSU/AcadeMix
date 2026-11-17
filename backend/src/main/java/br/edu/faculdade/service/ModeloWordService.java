package br.edu.faculdade.service;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import br.edu.faculdade.service.ModeloDocumentoService.*;

@Service
public class ModeloWordService {
 private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
 private String xml(String valor){
  var limpo=new StringBuilder();
  valor.codePoints().filter(c->c==9||c==10||c==13||c>=32&&c<=0xD7FF||c>=0xE000&&c<=0xFFFD||c>=0x10000&&c<=0x10FFFF).forEach(limpo::appendCodePoint);
  return limpo.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
 }
 private String paragrafo(String texto,String propriedades,boolean negrito){
  return "<w:p><w:pPr>"+propriedades+"</w:pPr><w:r><w:rPr>"+(negrito?"<w:b/>":"")+"</w:rPr><w:t xml:space=\"preserve\">"+xml(texto)+"</w:t></w:r></w:p>";
 }
 public byte[] gerar(Pedido dados,Previa previa){
  boolean famper=dados.instituicao()!=null&&java.util.regex.Pattern.compile("(?iu)\\bfamper\\b").matcher(dados.instituicao()).find();
  String fonte="COMPARECIMENTO".equals(dados.modelo())?"Arial":"Times New Roman";
  var corpo=new StringBuilder(paragrafo(previa.titulo(),"<w:pStyle w:val=\"Title\"/><w:keepNext/><w:jc w:val=\"center\"/><w:spacing w:before=\"0\" w:after=\"240\" w:line=\"240\" w:lineRule=\"auto\"/>",true));
  for(String linha:previa.texto().split("\\R",-1)){
   if(linha.isBlank())continue;
   boolean integrante="NOMEACAO".equals(dados.modelo())&&dados.membros().stream().anyMatch(m->linha.startsWith(m.cargo()+": "+m.nome()));
   corpo.append(paragrafo(linha,"<w:widowControl/><w:jc w:val=\"both\"/><w:ind w:firstLine=\""+(integrante?"0":"709")+"\"/><w:spacing w:before=\"0\" w:after=\""+(integrante?"0":"120")+"\" w:line=\"360\" w:lineRule=\"auto\"/>",false));
  }
  corpo.append(paragrafo("Assinaturas","<w:keepNext/><w:spacing w:before=\"240\" w:after=\"0\"/>",true));
  for(var assinatura:previa.assinaturas()){
   corpo.append(paragrafo("________________________________________","<w:keepNext/><w:spacing w:before=\"480\" w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/>",false));
   corpo.append(paragrafo(assinatura.nome(),"<w:keepNext/><w:spacing w:after=\"0\"/>",false));
   corpo.append(paragrafo(assinatura.cargo(),"<w:spacing w:after=\"120\"/>",false));
  }
  corpo.append("<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rHeader\"/><w:footerReference w:type=\"default\" r:id=\"rFooter\"/>"
   +"<w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"2400\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"360\" w:footer=\"600\"/></w:sectPr>");
  String header=famper?"""
   <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing>
   <wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
   <wp:extent cx="5760085" cy="829452"/><wp:docPr id="1" name="Cabeçalho institucional"/>
   <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
   <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="0" name="Instituição"/><pic:cNvPicPr/></pic:nvPicPr>
   <pic:blipFill><a:blip r:embed="rLogo"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
   <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="5760085" cy="829452"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
   </pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
   """:paragrafo(dados.instituicao(),"<w:jc w:val=\"center\"/>",true);
  String rodape=famper?"Avenida Zilda Arns, 183, Bairro Floresta - CEP 85641-154 - Ampére/PR\nFone: (46) 99979-0605 - famper@famper.com.br - www.famper.com.br":dados.instituicao()+" - "+dados.cidade();
  var footer=new StringBuilder();
  for(String linha:rodape.split("\n"))footer.append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:after=\"0\"/></w:pPr><w:r><w:rPr><w:sz w:val=\"18\"/></w:rPr><w:t>"+xml(linha)+"</w:t></w:r></w:p>");
  String namespaces=" xmlns:w=\""+W+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"";
  try(var resultado=new ByteArrayOutputStream();var zip=new ZipOutputStream(resultado)){
   entrada(zip,"[Content_Types].xml","""
    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
    <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
    </Types>
    """);
   entrada(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rDoc\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
   entrada(zip,"word/document.xml","<w:document"+namespaces+"><w:body>"+corpo+"</w:body></w:document>");
   entrada(zip,"word/header1.xml","<w:hdr"+namespaces+">"+header+"</w:hdr>");
   entrada(zip,"word/footer1.xml","<w:ftr"+namespaces+">"+footer+"</w:ftr>");
   entrada(zip,"word/styles.xml","<w:styles xmlns:w=\""+W+"\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\""+fonte+"\" w:hAnsi=\""+fonte+"\"/><w:sz w:val=\"24\"/><w:lang w:val=\"pt-BR\"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:rPr><w:rFonts w:ascii=\""+fonte+"\" w:hAnsi=\""+fonte+"\"/><w:b/><w:color w:val=\"000000\"/><w:sz w:val=\"24\"/></w:rPr></w:style></w:styles>");
   entrada(zip,"word/_rels/document.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+relacao("rHeader","header","header1.xml")+relacao("rFooter","footer","footer1.xml")+relacao("rStyles","styles","styles.xml")+"</Relationships>");
   if(famper){
    entrada(zip,"word/_rels/header1.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+relacao("rLogo","image","media/cabecalho.png")+"</Relationships>");
    try(var logo=getClass().getResourceAsStream("/modelos/cabecalho-famper.png")){
     if(logo==null)throw new IOException("Cabeçalho indisponível.");
     zip.putNextEntry(new ZipEntry("word/media/cabecalho.png"));logo.transferTo(zip);zip.closeEntry();
    }
   }
   zip.finish();return resultado.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Não foi possível gerar o documento Word.",e);}
 }
 private String relacao(String id,String tipo,String destino){return "<Relationship Id=\""+id+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"+tipo+"\" Target=\""+destino+"\"/>";}
 private void entrada(ZipOutputStream zip,String nome,String texto) throws IOException {
  zip.putNextEntry(new ZipEntry(nome));zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+texto).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
 }
}
