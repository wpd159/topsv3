package br.com.topsdojob.v3.application.publico;

import java.text.Normalizer;
import java.util.Locale;

public final class EnderecoResumidoPublicoPolicy {

    private static final String MARCADOR_TECNICO = "endereco sintetico local";

    private EnderecoResumidoPublicoPolicy() {
    }

    public static String projetar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        if (texto.isEmpty()) {
            return null;
        }
        String comparacao = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        return MARCADOR_TECNICO.equals(comparacao) ? null : texto;
    }
}
