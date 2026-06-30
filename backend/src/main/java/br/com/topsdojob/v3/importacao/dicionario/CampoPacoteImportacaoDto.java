package br.com.topsdojob.v3.importacao.dicionario;

public record CampoPacoteImportacaoDto(
        String nomeLogico,
        TipoCampoImportacao tipo,
        ObrigatoriedadeCampoImportacao obrigatoriedade,
        SensibilidadeCampoImportacao sensibilidade,
        boolean identificador,
        boolean saneavel,
        boolean bloqueante,
        boolean exigeEvidencia,
        String observacaoSanitizada) {

    public CampoPacoteImportacaoDto {
        nomeLogico = normalizarOpcional(nomeLogico);
        observacaoSanitizada = normalizarOpcional(observacaoSanitizada);
    }

    public boolean obrigatorio() {
        return obrigatoriedade == ObrigatoriedadeCampoImportacao.OBRIGATORIO
                || obrigatoriedade == ObrigatoriedadeCampoImportacao.OBRIGATORIO_SE_PRESENTE_ORIGEM;
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
