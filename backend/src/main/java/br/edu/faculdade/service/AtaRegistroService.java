package br.edu.faculdade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.io.IOException;

@Service
public class AtaRegistroService {
 public record Registro(Long id,String tipo,String titulo,String descricao,LocalDate data,String arquivoNome) {}
 private final BancoProjeto banco;
 private final ObjectMapper json;
 public AtaRegistroService(ObjectMapper json,org.springframework.beans.factory.ObjectProvider<BancoProjeto> provider){
  this.json=json;this.banco=provider.getIfAvailable();
 }
 private Path arquivo(long usuario){return Path.of("data","atas",usuario+".json");}
 private List<Registro> locais(long usuario){
  try{return Files.exists(arquivo(usuario))?new ArrayList<>(Arrays.asList(json.readValue(arquivo(usuario).toFile(),Registro[].class))):new ArrayList<>();}
  catch(IOException e){throw new IllegalStateException("Não foi possível carregar as atas.",e);}
 }
 public synchronized List<Registro> listar(long usuario){
  if(banco==null)return locais(usuario);
  banco.importar("atas-registros-"+usuario+"-v1",()->{
   for(var r:locais(usuario))banco.ata(usuario,r.id(),r.tipo(),r.titulo(),r.descricao(),r.data(),r.arquivoNome());
  });
  return banco.atas(usuario);
 }
 public synchronized Registro salvar(long usuario,Registro dados,boolean importar){
  if(dados.titulo()==null||dados.titulo().isBlank()||dados.descricao()==null||dados.descricao().isBlank()
    ||dados.data()==null||!Set.of("ATA","ATIVIDADE").contains(dados.tipo()==null?"":dados.tipo()))
   throw new IllegalArgumentException("Preencha título, descrição, tipo e data.");
  var registros=new ArrayList<>(listar(usuario));
  long id=importar&&dados.id()!=null?dados.id():Math.max(System.currentTimeMillis(),registros.stream().mapToLong(Registro::id).max().orElse(0)+1);
  if(id<=0)throw new IllegalArgumentException("Identificador inválido.");
  var existente=registros.stream().filter(r->r.id()==id).findFirst();
  if(importar&&existente.isPresent())return existente.get();
  var registro=new Registro(id,dados.tipo(),dados.titulo().trim(),dados.descricao().trim(),dados.data(),dados.arquivoNome());
  registros.add(registro);
  if(banco!=null)banco.ata(usuario,id,registro.tipo(),registro.titulo(),registro.descricao(),registro.data(),registro.arquivoNome());
  else gravar(usuario,registros);
  return registro;
 }
 public synchronized Registro anexo(long usuario,long id,String nome){
  var registros=new ArrayList<>(listar(usuario));
  var anterior=registros.stream().filter(r->r.id()==id).findFirst().orElseThrow(()->new NoSuchElementException("Ata não encontrada."));
  var registro=new Registro(id,anterior.tipo(),anterior.titulo(),anterior.descricao(),anterior.data(),nome);
  registros.set(registros.indexOf(anterior),registro);
  if(banco!=null)banco.nomeAnexo(usuario,id,nome);else gravar(usuario,registros);
  return registro;
 }
 public synchronized void remover(long usuario,long id){
  if(banco!=null)banco.excluirAta(usuario,id);
  else gravar(usuario,listar(usuario).stream().filter(r->r.id()!=id).toList());
 }
 private void gravar(long usuario,List<Registro> registros){
  try{
   var destino=arquivo(usuario);Files.createDirectories(destino.getParent());
   var temp=destino.resolveSibling(usuario+".tmp");json.writeValue(temp.toFile(),registros);
   Files.move(temp,destino,StandardCopyOption.REPLACE_EXISTING);
  }catch(IOException e){throw new IllegalStateException("Não foi possível salvar a ata.",e);}
 }
}
