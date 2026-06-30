package br.com.topsdojob.v3.importacao.plano;

public record DependenciaEtapaImportacaoDto(
        TipoEtapaImportacao tipoEtapa,
        boolean obrigatoria,
        String justificativaSanitizada,
        String alternativaAuditavelSanitizada) {

    public DependenciaEtapaImportacaoDto {
        justificativaSanitizada = normalizarOpcional(justificativaSanitizada);
        alternativaAuditavelSanitizada = normalizarOpcional(alternativaAuditavelSanitizada);
    }

    public boolean possuiAlternativaAuditavel() {
        return alternativaAuditavelSanitizada != null;
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
