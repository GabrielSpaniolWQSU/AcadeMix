package br.edu.faculdade.dto;

import br.edu.faculdade.model.Perfil;

public final class Dtos {
 private Dtos() {}
 public record UsuarioReq(String nome, String email, String senha, Perfil perfil, Boolean ativo) {}
}
