package br.com.topsdojob.v3.importacao.plano;

import java.util.List;

public record PlanoExecucaoImportacaoDto(
        String identificadorLogico,
        String versao,
        boolean dryRunEstrutural,
        List<EtapaImportacaoDto> etapas,
        String observacaoSanitizada) {

    public PlanoExecucaoImportacaoDto {
        identificadorLogico = normalizarOpcional(identificadorLogico);
        versao = normalizarOpcional(versao);
        etapas = List.copyOf(etapas == null ? List.of() : etapas);
        observacaoSanitizada = normalizarOpcional(observacaoSanitizada);
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
