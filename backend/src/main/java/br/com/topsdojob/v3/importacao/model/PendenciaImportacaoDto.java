package br.com.topsdojob.v3.importacao.model;

import java.util.Objects;

public record PendenciaImportacaoDto(
        TipoEntidadeImportacao tipoEntidade,
        String idLegado,
        CodigoPendenciaImportacao codigo,
        SeveridadePendenciaImportacao severidade,
        String detalheSanitizado) {

    public PendenciaImportacaoDto {
        Objects.requireNonNull(tipoEntidade, "tipoEntidade deve ser informado");
        Objects.requireNonNull(codigo, "codigo deve ser informado");
        if (severidade == null) {
            severidade = codigo.severidadePadrao();
        }
        idLegado = normalizarOpcional(idLegado);
        detalheSanitizado = normalizarOpcional(detalheSanitizado);
    }

    public static PendenciaImportacaoDto de(
            TipoEntidadeImportacao tipoEntidade,
            String idLegado,
            CodigoPendenciaImportacao codigo,
            String detalheSanitizado) {
        return new PendenciaImportacaoDto(
                tipoEntidade,
                idLegado,
                codigo,
                codigo.severidadePadrao(),
                detalheSanitizado);
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
