package br.edu.faculdade.controller;

import br.edu.faculdade.model.Perfil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.*;

@Configuration
public class PermissoesConfig implements WebMvcConfigurer {
 private final AuthController auth;
 public PermissoesConfig(AuthController auth){this.auth=auth;}
 public static boolean permitido(Perfil perfil,String area,String metodo){
  if(perfil==Perfil.ADMIN)return true;
  return switch(area){
   case "documentos","atas-atividades" -> perfil==Perfil.COORDENADOR||perfil==Perfil.PROFESSOR;
   case "avisos" -> perfil==Perfil.COORDENADOR||(perfil==Perfil.PROFESSOR&&metodo.equals("GET"));
   case "certificados" -> perfil==Perfil.GERENCIADOR_CERTIFICADOS
      &&(metodo.equals("GET")||metodo.equals("PUT"));
   default -> false;
  };
 }
 @Override public void addInterceptors(InterceptorRegistry registry){
  registry.addInterceptor(new HandlerInterceptor(){
   @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
    if(request.getMethod().equals("OPTIONS"))return true;
    String caminho=request.getRequestURI().substring(request.getContextPath().length());
    if(caminho.startsWith("/api/auth/"))return true;
    String[] partes=caminho.split("/");
    var perfil=auth.exigirUsuario(request).getPerfil();
    if(partes.length<3||!permitido(perfil,partes[2],request.getMethod()))
     throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return true;
   }
  }).addPathPatterns("/api/**");
 }
}
