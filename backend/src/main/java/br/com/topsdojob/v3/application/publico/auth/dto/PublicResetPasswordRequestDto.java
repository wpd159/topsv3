package br.com.topsdojob.v3.application.publico.auth.dto;
public record PublicResetPasswordRequestDto(String email, String codigo, String novaSenha, String confirmarSenha) {}
