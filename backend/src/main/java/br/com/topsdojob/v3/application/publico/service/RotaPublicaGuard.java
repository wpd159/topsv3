package br.com.topsdojob.v3.application.publico.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class RotaPublicaGuard {

    private RotaPublicaGuard() {
    }

    static String slug(String value, String fieldName) {
        String normalized = required(value, fieldName).toLowerCase();
        if (!normalized.matches("[a-z0-9][a-z0-9-]{1,120}")) {
            throw badRequest(fieldName + " invalido");
        }
        return normalized;
    }

    static String username(String value) {
        String normalized = required(value, "username").toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{32}")) {
            throw badRequest("username invalido");
        }
        return normalized;
    }

    static String uf(String value) {
        String normalized = required(value, "uf").toUpperCase();
        if (!normalized.matches("[A-Z]{2}")) {
            throw badRequest("uf invalida");
        }
        return normalized;
    }

    static String caminhoPublico(String value) {
        String normalized = required(value, "caminho");
        if (!normalized.startsWith("/")) {
            throw badRequest("caminho deve iniciar com /");
        }
        if (normalized.startsWith("/anuncio/")
                || normalized.startsWith("/perfil/")
                || normalized.startsWith("/acompanhante/")
                || normalized.startsWith("/ads/")) {
            throw badRequest("rota publica alternativa proibida");
        }
        if (!normalized.startsWith("/anuncios/")
                && !normalized.startsWith("/acompanhantes/")
                && !normalized.equals("/sitemap.xml")
                && !normalized.equals("/robots.txt")
                && !normalized.equals("/")) {
            throw badRequest("rota publica nao suportada neste bloco");
        }
        return normalized;
    }

    static void page(int pagina, int tamanho) {
        if (pagina < 0) {
            throw badRequest("pagina nao pode ser negativa");
        }
        if (tamanho < 1 || tamanho > 50) {
            throw badRequest("tamanho deve estar entre 1 e 50");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + " deve ser informado");
        }
        return value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
