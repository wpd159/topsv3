package br.com.topsdojob.v3.application.publico.auth.dto;

public record PublicRegisterRequestDto(
        String username,
        String email,
        String telefone,
        String dataNascimento,
        String senha,
        String confirmarSenha,
        Boolean acceptedTermsOfUse,
        Boolean acceptedPrivacyPolicy,
        Boolean acceptedPromotionalEmails) {
}
