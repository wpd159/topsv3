package br.com.topsdojob.v3.importacao.pacote;

import java.time.Instant;
import java.util.List;

public record PacoteEntradaImportacaoDto(
        String identificadorLogico,
        String versao,
        Instant extraidoEm,
        String origem,
        List<ArquivoPacoteImportacaoDto> arquivos,
        String observacaoSanitizada) {

    public PacoteEntradaImportacaoDto {
        identificadorLogico = normalizarOpcional(identificadorLogico);
        versao = normalizarOpcional(versao);
        origem = normalizarOpcional(origem);
        arquivos = List.copyOf(arquivos == null ? List.of() : arquivos);
        observacaoSanitizada = normalizarOpcional(observacaoSanitizada);
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
