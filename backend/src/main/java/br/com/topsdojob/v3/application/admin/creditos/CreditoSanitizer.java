package br.com.topsdojob.v3.application.admin.creditos;

import org.springframework.stereotype.Component;

@Component
public class CreditoSanitizer {

    public boolean chaveOperacionalPresente(String valor) {
        return valor != null && !valor.isBlank();
    }

    public String referenciaTipo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
