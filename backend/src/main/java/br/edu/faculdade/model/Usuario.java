package br.edu.faculdade.model;
import com.fasterxml.jackson.annotation.JsonIgnore;import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor public class Usuario {Long id;String nome;String email;@JsonIgnore String senha;Perfil perfil;boolean ativo=true;}
