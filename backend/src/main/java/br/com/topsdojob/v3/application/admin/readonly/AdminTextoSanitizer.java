package br.com.topsdojob.v3.application.admin.readonly;

import java.util.regex.Pattern;

public final class AdminTextoSanitizer {

    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+" + "@" + "[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern CONTATO = Pattern.compile("\\+?[0-9][0-9 .()\\-]{7,}[0-9]");
    private static final Pattern DOCUMENTO = Pattern.compile("\\b[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}\\b|\\b[0-9]{11}\\b");
    private static final Pattern SEGREDO = Pattern.compile(
            "(?i)\\b(token|senha|password|secret|authorization|certificado)\\b\\s*[:=]\\s*[^\\s,;]+");

    private AdminTextoSanitizer() {
    }

    public static String resumo(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = EMAIL.matcher(value).replaceAll("[email-mascarado]");
        sanitized = DOCUMENTO.matcher(sanitized).replaceAll("[documento-mascarado]");
        sanitized = CONTATO.matcher(sanitized).replaceAll("[contato-mascarado]");
        sanitized = SEGREDO.matcher(sanitized).replaceAll("[segredo-removido]");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }
}
