package br.edu.faculdade.service;

import br.edu.faculdade.dto.Dtos.UsuarioReq;
import br.edu.faculdade.model.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

@Service
public class UsuarioService {
 private final Map<Long,Usuario> usuarios=new HashMap<>();
 private final Path arquivo;
 private final ObjectMapper json=new ObjectMapper();
 private long sequencia=1;
 private BancoProjeto banco;
 @org.springframework.beans.factory.annotation.Autowired
 public UsuarioService(org.springframework.beans.factory.ObjectProvider<BancoProjeto> provider){
  this();
  banco=provider.getIfAvailable();
  if(banco!=null){
   banco.importar("usuarios-v1",()->{if(banco.usuarios().isEmpty())banco.usuarios(usuarios.values());});
   usuarios.clear();
   for(var usuario:banco.usuarios()){usuarios.put(usuario.getId(),usuario);sequencia=Math.max(sequencia,usuario.getId()+1);}
  }
 }
 private record Registro(Long id,String nome,String email,String senha,Perfil perfil,boolean ativo) {}
 public UsuarioService(){this(Path.of("data","usuarios.json"));}
 public UsuarioService(Path arquivo){
  this.arquivo=arquivo;
  if(Files.exists(arquivo)){
   try {
    for(var r:json.readValue(arquivo.toFile(),Registro[].class)){
     usuarios.put(r.id(),new Usuario(r.id(),r.nome(),r.email(),r.senha(),r.perfil(),r.ativo()));
     sequencia=Math.max(sequencia,r.id()+1);
    }
   }catch(IOException e){throw new IllegalStateException("Não foi possível carregar os usuários.",e);}
  }else{
   usuarios.put(1L,new Usuario(1L,"Coordenador","admin@faculdade.edu.br",proteger("Admin@123"),Perfil.ADMIN,true));
   sequencia=2;
   salvar(usuarios);
  }
 }
 public synchronized List<Usuario> listar(){return usuarios.values().stream().sorted(Comparator.comparing(Usuario::getId)).toList();}
 public synchronized Usuario criar(UsuarioReq r){
  validar(r,null);
  if(r.senha()==null||r.senha().isBlank())throw new IllegalArgumentException("Informe uma senha.");
  var u=new Usuario(sequencia++,r.nome().trim(),r.email().trim(),proteger(r.senha()),r.perfil(),r.ativo()==null||r.ativo());
  var novos=new HashMap<>(usuarios);novos.put(u.getId(),u);
  salvar(novos);usuarios.put(u.getId(),u);return u;
 }
 public synchronized Usuario editar(Long id,UsuarioReq r){
  validar(r,id);
  var anterior=Optional.ofNullable(usuarios.get(id)).orElseThrow(()->new NoSuchElementException("Usuário não encontrado"));
  if(anterior.getPerfil()==Perfil.ADMIN&&anterior.isAtivo()
    &&(r.perfil()!=Perfil.ADMIN||Boolean.FALSE.equals(r.ativo()))
    &&usuarios.values().stream().filter(u->u.getPerfil()==Perfil.ADMIN&&u.isAtivo()).count()==1)
   throw new IllegalArgumentException("Mantenha pelo menos um administrador ativo.");
  var u=new Usuario(id,r.nome().trim(),r.email().trim(),r.senha()==null||r.senha().isBlank()?anterior.getSenha():proteger(r.senha()),r.perfil(),r.ativo()==null?anterior.isAtivo():r.ativo());
  var novos=new HashMap<>(usuarios);novos.put(id,u);
  salvar(novos);usuarios.put(id,u);return u;
 }
 private void salvar(Map<Long,Usuario> novos){
  if(banco!=null){banco.usuarios(novos.values());return;}
  try{
   Files.createDirectories(arquivo.getParent());
   var registros=novos.values().stream().map(u->new Registro(u.getId(),u.getNome(),u.getEmail(),u.getSenha(),u.getPerfil(),u.isAtivo())).toList();
   var temp=arquivo.resolveSibling("usuarios.tmp");
   json.writeValue(temp.toFile(),registros);
   Files.move(temp,arquivo,StandardCopyOption.REPLACE_EXISTING);
  }catch(IOException e){throw new IllegalStateException("Não foi possível salvar os usuários.",e);}
 }
 private void validar(UsuarioReq r,Long id){
  if(r.nome()==null||r.nome().isBlank()||r.email()==null||r.email().isBlank()||r.perfil()==null)throw new IllegalArgumentException("Preencha nome, e-mail e perfil");
  if(usuarios.values().stream().anyMatch(u->!u.getId().equals(id)&&u.getEmail().equalsIgnoreCase(r.email().trim())))throw new IllegalArgumentException("E-mail já cadastrado.");
 }
 public boolean senhaCorreta(Usuario usuario,String senha){
  if(senha==null)return false;
  String salva=usuario.getSenha();
  if(!salva.startsWith("pbkdf2:"))return salva.equals(senha);
  String[] partes=salva.split(":");
  byte[] sal=Base64.getDecoder().decode(partes[1]);
  return java.security.MessageDigest.isEqual(Base64.getDecoder().decode(partes[2]),derivar(senha,sal));
 }
 private String proteger(String senha){
  byte[] sal=new byte[16];new java.security.SecureRandom().nextBytes(sal);
  return "pbkdf2:"+Base64.getEncoder().encodeToString(sal)+":"+Base64.getEncoder().encodeToString(derivar(senha,sal));
 }
 private byte[] derivar(String senha,byte[] sal){
  var spec=new javax.crypto.spec.PBEKeySpec(senha.toCharArray(),sal,210000,256);
  try{return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
  catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Não foi possível proteger a senha.",e);}
  finally{spec.clearPassword();}
 }
}
