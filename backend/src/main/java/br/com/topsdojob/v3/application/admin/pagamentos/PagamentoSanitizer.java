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

    public String mascararEmail(String value) {
        if (!presente(value) || !value.contains("@")) {
            return null;
        }
        String[] partes = value.trim().split("@", 2);
        String local = partes[0];
        String prefixo = local.substring(0, Math.min(2, local.length()));
        return prefixo + "***@" + partes[1];
    }
}
