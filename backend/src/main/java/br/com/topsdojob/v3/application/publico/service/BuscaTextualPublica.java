package br.com.topsdojob.v3.application.publico.service;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class BuscaTextualPublica {

    static final int LIMITE_CARACTERES = 80;

    private BuscaTextualPublica() {
    }

    static String normalizarParaLike(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        String termo = valor.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (termo.length() > LIMITE_CARACTERES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "busca deve ter no maximo " + LIMITE_CARACTERES + " caracteres");
        }

        String normalizado = Normalizer.normalize(termo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return normalizado
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
