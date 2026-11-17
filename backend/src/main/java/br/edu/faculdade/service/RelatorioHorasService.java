package br.edu.faculdade.service;

import br.edu.faculdade.controller.CertificadoController.Dados;
import br.edu.faculdade.controller.GoogleDriveController.PastaCertificados;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

@Service
public class RelatorioHorasService {
 private record Aluno(String nome,double horas) {}
 private String xml(String texto){
  var limpo=new StringBuilder();
  texto.codePoints().filter(c->c==9||c==10||c==13||c>=32&&c<=0xD7FF||c>=0xE000&&c<=0xFFFD||c>=0x10000&&c<=0x10FFFF).forEach(limpo::appendCodePoint);
  return limpo.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
 }
 private String texto(String celula,String valor,int estilo){return "<c r=\""+celula+"\" s=\""+estilo+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+xml(valor)+"</t></is></c>";}
 private String numero(String celula,double valor,int estilo){return "<c r=\""+celula+"\" s=\""+estilo+"\"><v>"+valor+"</v></c>";}
 private String formula(String celula,String valor,double calculado,int estilo){return "<c r=\""+celula+"\" s=\""+estilo+"\"><f>"+valor+"</f><v>"+calculado+"</v></c>";}
 private String linha(int numero,String celulas,int altura){return "<row r=\""+numero+"\" ht=\""+altura+"\" customHeight=\"1\">"+celulas+"</row>";}
 public byte[] gerar(Dados dados){
  return gerar(dados,null,null);
 }
 public byte[] gerar(Dados dados,LocalDate inicio,LocalDate fim){
  if(inicio!=null&&fim!=null&&fim.isBefore(inicio))throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
  var resumo=new LinkedHashMap<String,Aluno>();
  var certificadosVistos=new HashSet<String>();
  int semData=0;
  for(var raiz:dados.pastas().values()){
   var pastas=new ArrayList<PastaCertificados>();
   if(raiz.filhas().isEmpty()||!raiz.certificados().isEmpty())pastas.add(raiz);
   pastas.addAll(raiz.filhas());
   for(var pasta:pastas){
    for(var c:pasta.certificados()){
     var a=dados.avaliacoes().get(c.id());
     if(a==null||!"APROVADO".equals(a.status())||a.horasAprovadas()==null||!Double.isFinite(a.horasAprovadas())||a.horasAprovadas()<0||!certificadosVistos.add(c.id()))continue;
     LocalDate data=null;
     if(a.aprovadoEm()!=null)try{data=Instant.parse(a.aprovadoEm()).atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();}catch(java.time.format.DateTimeParseException ignorado){}
     if(data==null)semData++;
     if((inicio!=null||fim!=null)&&(data==null||(inicio!=null&&data.isBefore(inicio))||(fim!=null&&data.isAfter(fim))))continue;
     var anterior=resumo.get(pasta.id());
     resumo.put(pasta.id(),new Aluno(pasta.nome(),(anterior==null?0:anterior.horas())+a.horasAprovadas()));
    }
   }
  }
  var alunos=new ArrayList<>(resumo.values());
  var ordem=java.text.Collator.getInstance(Locale.forLanguageTag("pt-BR"));
  alunos.sort((a,b)->ordem.compare(a.nome(),b.nome()));
  double total=alunos.stream().mapToDouble(Aluno::horas).sum();
  int ultima=7+alunos.size(), linhaTotal=Math.max(9,ultima+1);
  String soma=alunos.isEmpty()?"0":"SUM(B8:B"+ultima+")";
  String gerado=ZonedDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
  var linhas=new StringBuilder();
  linhas.append(linha(1,texto("A1","HORAS APROVADAS",1)+texto("B1","",1),36));
  linhas.append(linha(2,texto("A2","Certificados acadêmicos | Gerado em "+gerado,2),24));
  var formato=DateTimeFormatter.ofPattern("dd/MM/yyyy");
  String periodo=inicio==null&&fim==null?"Todo o histórico, incluindo aprovações antigas sem data.":"Data da aprovação: "+(inicio==null?"início do histórico":inicio.format(formato))+" até "+(fim==null?"a data atual":fim.format(formato))+". "+semData+" aprovação(ões) sem data fora do período.";
  linhas.append(linha(3,texto("A3",periodo,2),32));
  linhas.append(linha(4,texto("A4","Alunos com aprovações",3)+numero("B4",alunos.size(),4),26));
  linhas.append(linha(5,texto("A5","Total de horas aprovadas",3)+formula("B5","B"+linhaTotal,total,5),26));
  linhas.append(linha(7,texto("A7","Aluno",6)+texto("B7","Horas aprovadas",6),28));
  for(int i=0;i<alunos.size();i++){
   int r=i+8;var aluno=alunos.get(i);boolean alternada=i%2==1;
   linhas.append(linha(r,texto("A"+r,aluno.nome(),alternada?9:7)+numero("B"+r,aluno.horas(),alternada?10:8),Math.max(26,((aluno.nome().length()+54)/55)*15+8)));
  }
  if(alunos.isEmpty())linhas.append(linha(8,texto("A8","Nenhuma aprovação encontrada",7),26));
  linhas.append(linha(linhaTotal,texto("A"+linhaTotal,"TOTAL",11)+formula("B"+linhaTotal,soma,total,12),28));
  String ns="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  String planilha="<worksheet xmlns=\""+ns+"\"><dimension ref=\"A1:B"+linhaTotal+"\"/><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"><pane ySplit=\"7\" topLeftCell=\"A8\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"
   +"<sheetFormatPr defaultRowHeight=\"24\"/><cols><col min=\"1\" max=\"1\" width=\"60\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"23\" customWidth=\"1\"/></cols><sheetData>"+linhas+"</sheetData>"
   +(alunos.isEmpty()?"":"<autoFilter ref=\"A7:B"+ultima+"\"/>")
   +"<mergeCells count=\"3\"><mergeCell ref=\"A1:B1\"/><mergeCell ref=\"A2:B2\"/><mergeCell ref=\"A3:B3\"/></mergeCells>"
   +"<printOptions horizontalCentered=\"1\"/><pageMargins left=\"0.3\" right=\"0.3\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/><pageSetup paperSize=\"9\" orientation=\"portrait\" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>";
  try(var saida=new ByteArrayOutputStream();var zip=new ZipOutputStream(saida)){
   entrada(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
   entrada(zip,"_rels/.rels",rels(rel("rWorkbook","officeDocument","xl/workbook.xml")));
   entrada(zip,"xl/workbook.xml","<workbook xmlns=\""+ns+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Horas aprovadas\" sheetId=\"1\" r:id=\"rSheet\"/></sheets><definedNames><definedName name=\"_xlnm.Print_Titles\" localSheetId=\"0\">'Horas aprovadas'!$7:$7</definedName><definedName name=\"_xlnm.Print_Area\" localSheetId=\"0\">'Horas aprovadas'!$A$1:$B$"+linhaTotal+"</definedName></definedNames><calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>");
   entrada(zip,"xl/_rels/workbook.xml.rels",rels(rel("rSheet","worksheet","worksheets/sheet1.xml")+rel("rStyles","styles","styles.xml")));
   entrada(zip,"xl/worksheets/sheet1.xml",planilha);
   entrada(zip,"xl/styles.xml",estilos(ns));
   zip.finish();return saida.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Não foi possível gerar o relatório de horas.",e);}
 }
 private String estilos(String ns){
  String fontes="<fonts count=\"4\">"+fonte(11,"24334B",false)+fonte(20,"FFFFFF",true)+fonte(11,"FFFFFF",true)+fonte(11,"125AA8",true)+"</fonts>";
  String preenchimentos="<fills count=\"5\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"+fill("0C4A83")+fill("E8F1FB")+fill("F3F6FA")+"</fills>";
  String bordas="<borders count=\"2\"><border/><border><bottom style=\"thin\"><color rgb=\"FFDCE3EC\"/></bottom></border></borders>";
  return "<styleSheet xmlns=\""+ns+"\"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.00\"/></numFmts>"+fontes+preenchimentos+bordas
   +"<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"13\">"
   +xf(0,0,0,0,false)+xf(1,2,0,0,false)+xf(0,0,0,0,false)+xf(3,3,0,0,false)+xf(3,3,0,0,true)+xf(3,3,0,164,true)+xf(2,2,0,0,false)
   +xf(0,0,1,0,false)+xf(0,0,1,164,true)+xf(0,4,1,0,false)+xf(0,4,1,164,true)+xf(2,2,0,0,false)+xf(2,2,0,164,true)
   +"</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
 }
 private String fonte(int tamanho,String cor,boolean negrito){return "<font><sz val=\""+tamanho+"\"/><color rgb=\"FF"+cor+"\"/><name val=\"Calibri\"/>"+(negrito?"<b/>":"")+"</font>";}
 private String fill(String cor){return "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF"+cor+"\"/><bgColor indexed=\"64\"/></patternFill></fill>";}
 private String xf(int fonte,int fill,int borda,int formato,boolean direita){return "<xf numFmtId=\""+formato+"\" fontId=\""+fonte+"\" fillId=\""+fill+"\" borderId=\""+borda+"\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" horizontal=\""+(direita?"right":"left")+"\" indent=\"1\" wrapText=\"1\"/></xf>";}
 private String rel(String id,String tipo,String destino){return "<Relationship Id=\""+id+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"+tipo+"\" Target=\""+destino+"\"/>";}
 private String rels(String conteudo){return "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+conteudo+"</Relationships>";}
 private void entrada(ZipOutputStream zip,String nome,String conteudo)throws IOException{zip.putNextEntry(new ZipEntry(nome));zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+conteudo).getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
