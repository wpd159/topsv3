package br.com.topsdojob.v3.application.publico.auth;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

final class PublicPasswordPolicy {
    private static final Pattern SYMBOL = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    private static final Pattern COMMON =
            Pattern.compile("(1234|abcd|senha|password|qwerty)", Pattern.CASE_INSENSITIVE);

    private PublicPasswordPolicy() {
    }

    static void validate(String candidate, String confirmation) {
        if (candidate == null
                || candidate.length() < 8
                || !candidate.matches(".*[A-Z].*")
                || !candidate.matches(".*[a-z].*")
                || !candidate.matches(".*[0-9].*")
                || !SYMBOL.matcher(candidate).find()
                || COMMON.matcher(candidate).find()) {
            throw new PublicAuthException(
                    HttpStatus.BAD_REQUEST,
                    "Senha não atende aos requisitos de segurança.");
        }
        if (!candidate.equals(confirmation)) {
            throw new PublicAuthException(HttpStatus.BAD_REQUEST, "As senhas não coincidem.");
        }
    }
}
