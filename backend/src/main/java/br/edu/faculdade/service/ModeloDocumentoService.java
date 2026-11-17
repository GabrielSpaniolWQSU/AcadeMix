package br.edu.faculdade.service;

import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ModeloDocumentoService {
 public static final String INSTITUICAO="Faculdade de Ampére - FAMPER";
 public record Membro(String cargo,String nome,String periodo) {}
 public record Pedido(String modelo,String instituicao,String cidade,String curso,LocalDate data,
   LocalTime inicio,LocalTime fim,String local,String assunto,String participantes,String discussoes,
   String decisoes,String responsavel,String secretario,String mandato,String nomeParticipante,
   String rg,String cpf,LocalDate dataEmissao,List<Membro> membros,String titulo,String texto,
   boolean revisado,Long registroId) {}
 public record Previa(String titulo,String texto,List<Membro> assinaturas) {}
 private String data(LocalDate data){return data.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu",Locale.forLanguageTag("pt-BR")));}
 private String hora(LocalTime hora){return hora.format(DateTimeFormatter.ofPattern("HH'h'mm"));}
 private String pontuar(String texto){return texto.matches("(?s).*[.!?…]$")?texto:texto+".";}
 private String campo(String valor,String rotulo){
  if(valor!=null&&valor.length()>20000)throw new IllegalArgumentException("Reduza o campo "+rotulo+" para até 20.000 caracteres.");
  return valor==null||valor.isBlank()?"[PREENCHER "+rotulo+"]":valor.trim();
 }
 private String obrigatorio(String valor,String nome){
  if(valor==null||valor.isBlank()||valor.length()>10000)throw new IllegalArgumentException("Preencha "+nome+" (até 10.000 caracteres).");
  return valor.trim();
 }
 public Previa previa(Pedido p){
  obrigatorio(p.instituicao(),"a instituição");obrigatorio(p.cidade(),"a cidade");obrigatorio(p.local(),"o local");
  if(p.data()==null||p.inicio()==null)throw new IllegalArgumentException("Informe a data e o horário.");
  if(p.fim()!=null&&p.fim().isBefore(p.inicio()))throw new IllegalArgumentException("O término deve ser igual ou posterior ao início.");
  String abertura="No dia "+data(p.data())+", às "+hora(p.inicio())+", em "+p.local().trim()+", na instituição "+p.instituicao().trim();
  String titulo,texto;
  List<Membro> assinaturas;
  switch(p.modelo()==null?"":p.modelo()){
   case "NOMEACAO" -> {
    String curso=obrigatorio(p.curso(),"o curso"),mandato=obrigatorio(p.mandato(),"a duração do mandato");
    if(p.membros()==null||p.membros().isEmpty()||p.membros().size()>20)throw new IllegalArgumentException("Informe entre 1 e 20 integrantes.");
    var linhas=new ArrayList<String>();
    for(var m:p.membros()){
     if(m==null)throw new IllegalArgumentException("Integrante inválido.");
     linhas.add(obrigatorio(m.cargo(),"o cargo")+": "+obrigatorio(m.nome(),"o nome do integrante")
       +(m.periodo()==null||m.periodo().isBlank()?"":" - "+m.periodo().trim()));
    }
    titulo="ATA DE NOMEAÇÃO DO CENTRO ACADÊMICO";
    texto=abertura+", reuniram-se os alunos do curso de "+curso+" para a nomeação da diretoria do Centro Acadêmico, com mandato de "+mandato+"."
     +"\n\nSegue a composição da diretoria:\n\n"+String.join("\n",linhas)
     +"\n\n"+campo(p.decisoes(),"REGISTRO DA NOMEAÇÃO E DAS DELIBERAÇÕES")
     +"\n\nNada mais havendo a tratar, encerrou-se a reunião"+(p.fim()==null?"":" às "+hora(p.fim()))+", lavrando-se a presente ata.";
    assinaturas=p.membros();
   }
   case "COMPARECIMENTO" -> {
    if(p.fim()==null||p.dataEmissao()==null)throw new IllegalArgumentException("Informe o término da aula e a data de emissão.");
    if(p.dataEmissao().isBefore(p.data()))throw new IllegalArgumentException("A emissão deve ocorrer a partir da data da aula.");
    titulo="DECLARAÇÃO DE COMPARECIMENTO";
    texto="Declaro, para os devidos fins, que "+obrigatorio(p.nomeParticipante(),"o nome do participante")
      +", portador(a) do RG nº "+obrigatorio(p.rg(),"o RG")+" e CPF nº "+obrigatorio(p.cpf(),"o CPF")
      +", participou da aula realizada no dia "+data(p.data())+", das "+hora(p.inicio())+" às "+hora(p.fim())
      +", em "+p.local().trim()+"."
      +"\n\nPor ser esta a expressão da verdade, firmamos a presente declaração."
      +"\n\n"+p.cidade().trim()+", "+data(p.dataEmissao())+".";
    assinaturas=List.of(new Membro("Responsável pela emissão",obrigatorio(p.responsavel(),"o responsável pela emissão"),""));
   }
   case "ASSUNTO" -> {
    if(p.assunto()!=null&&p.assunto().length()>150)throw new IllegalArgumentException("Use até 150 caracteres no assunto.");
    String assunto=obrigatorio(p.assunto(),"o assunto");
    titulo=(assunto.matches("(?iu)^ata\\b.*")?assunto:"ATA DE REUNIÃO SOBRE "+assunto).toUpperCase(Locale.forLanguageTag("pt-BR"));
    texto=pontuar(abertura+", realizou-se reunião para tratar de "+p.assunto().trim())
     +"\n\nParticipantes: "+pontuar(campo(p.participantes(),"PARTICIPANTES"))
     +"\n\nCondução da reunião: "+pontuar(campo(p.responsavel(),"RESPONSÁVEL PELA REUNIÃO"))
     +"\n\nDiscussões: "+pontuar(campo(p.discussoes(),"ASSUNTOS DISCUTIDOS"))
     +"\n\nDecisões e encaminhamentos: "+pontuar(campo(p.decisoes(),"DECISÕES, RESPONSÁVEIS E PRAZOS"))
     +"\n\nEncerramento: "+(p.fim()==null?"[PREENCHER HORÁRIO DE TÉRMINO]":hora(p.fim()))+"."
     +"\n\nAta redigida por "+pontuar(campo(p.secretario(),"RESPONSÁVEL PELA REDAÇÃO"));
    assinaturas=List.of(new Membro("Responsável pela reunião",campo(p.responsavel(),"NOME"),""),
      new Membro("Responsável pela redação",campo(p.secretario(),"NOME"),""));
   }
   default -> throw new IllegalArgumentException("Selecione um dos modelos disponíveis.");
  }
  return new Previa(titulo,texto,assinaturas);
 }
 public Previa revisada(Pedido p){
  var base=previa(p);
  if(!p.revisado())throw new IllegalArgumentException("Revise e confirme o conteúdo antes de gerar o documento.");
  if(p.titulo()==null||p.titulo().isBlank()||p.titulo().length()>300||p.texto()==null||p.texto().isBlank()||p.texto().length()>60000)
   throw new IllegalArgumentException("Informe um título de até 300 caracteres e um texto de até 60.000 caracteres.");
  return new Previa(p.titulo().trim(),p.texto().trim(),base.assinaturas());
 }
}
