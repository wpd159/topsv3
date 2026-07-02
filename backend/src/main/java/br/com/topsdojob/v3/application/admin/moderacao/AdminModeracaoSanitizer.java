package br.com.topsdojob.v3.application.admin.moderacao;

import java.util.regex.Pattern;

final class AdminModeracaoSanitizer {

    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+" + "@" + "[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern CONTATO = Pattern.compile("\\+?[0-9][0-9 .()\\-]{7,}[0-9]");
    private static final Pattern CPF = Pattern.compile("\\b[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}\\b");

    private AdminModeracaoSanitizer() {
    }

    static String texto(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = EMAIL.matcher(value).replaceAll("[email-mascarado]");
        sanitized = CPF.matcher(sanitized).replaceAll("[documento-mascarado]");
        sanitized = CONTATO.matcher(sanitized).replaceAll("[contato-mascarado]");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }
}
