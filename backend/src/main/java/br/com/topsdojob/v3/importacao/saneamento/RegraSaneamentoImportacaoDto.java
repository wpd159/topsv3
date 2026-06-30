package br.com.topsdojob.v3.importacao.saneamento;

import java.util.List;

public record RegraSaneamentoImportacaoDto(
        String codigo,
        TipoRegraSaneamentoImportacao tipo,
        EscopoRegraSaneamentoImportacao escopo,
        SeveridadeRegraSaneamentoImportacao severidade,
        String descricaoSanitizada,
        boolean exigeEvidencia,
        boolean preservaExistente,
        boolean publicaDado,
        boolean documentoPrivado,
        boolean gratuitoComLimiteArtificial,
        String tipoValorEstrutural,
        List<String> pendenciasSinalizadas) {

    public RegraSaneamentoImportacaoDto {
        codigo = normalizarOpcional(codigo);
        descricaoSanitizada = normalizarOpcional(descricaoSanitizada);
        tipoValorEstrutural = normalizarOpcional(tipoValorEstrutural);
        pendenciasSinalizadas = List.copyOf(pendenciasSinalizadas == null ? List.of() : pendenciasSinalizadas);
    }

    public boolean bloqueante() {
        return severidade == SeveridadeRegraSaneamentoImportacao.BLOQUEANTE;
    }

    public boolean mencionaEvidencia() {
        return exigeEvidencia
                || contem(descricaoSanitizada, "evidencia")
                || contem(descricaoSanitizada, "evidência");
    }

    public boolean tipoValorCreditoIncompativel() {
        if (escopo != EscopoRegraSaneamentoImportacao.CREDITO || tipoValorEstrutural == null) {
            return false;
        }
        String valor = tipoValorEstrutural.toLowerCase();
        return valor.contains("float")
                || valor.contains("decimal")
                || valor.contains("numeric")
                || valor.contains("dinheiro");
    }

    private static boolean contem(String valor, String termo) {
        return valor != null && valor.toLowerCase().contains(termo);
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
