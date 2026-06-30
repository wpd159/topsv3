package br.com.topsdojob.v3.importacao.saneamento;

import java.util.List;

import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;

public record ResultadoRegraSaneamentoImportacao(
        String codigoRegra,
        boolean valido,
        List<PendenciaImportacaoDto> pendencias,
        String observacaoSanitizada) {

    public ResultadoRegraSaneamentoImportacao {
        codigoRegra = normalizarOpcional(codigoRegra);
        pendencias = List.copyOf(pendencias == null ? List.of() : pendencias);
        observacaoSanitizada = normalizarOpcional(observacaoSanitizada);
    }

    public static ResultadoRegraSaneamentoImportacao valido(String codigoRegra) {
        return new ResultadoRegraSaneamentoImportacao(codigoRegra, true, List.of(), "validacao estrutural em memoria");
    }

    public static ResultadoRegraSaneamentoImportacao invalido(
            String codigoRegra,
            List<PendenciaImportacaoDto> pendencias) {
        return new ResultadoRegraSaneamentoImportacao(codigoRegra, false, pendencias, "pendencias estruturais");
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
