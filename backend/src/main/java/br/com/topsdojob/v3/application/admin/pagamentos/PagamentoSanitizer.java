package br.com.topsdojob.v3.application.admin.pagamentos;

import org.springframework.stereotype.Component;

@Component
public class PagamentoSanitizer {

    public String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public boolean presente(String value) {
        return value != null && !value.isBlank();
    }

    public String mascararEvidenciaTransacao(String value) {
        if (!presente(value)) {
            return null;
        }
        String trimmed = value.trim();
        int suffixStart = Math.max(0, trimmed.length() - 4);
        return "***" + trimmed.substring(suffixStart);
    }
}
