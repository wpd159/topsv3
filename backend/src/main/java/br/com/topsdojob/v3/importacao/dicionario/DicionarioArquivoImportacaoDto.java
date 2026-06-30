package br.com.topsdojob.v3.importacao.dicionario;

import java.util.List;

import br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao;

public record DicionarioArquivoImportacaoDto(
        TipoArquivoPacoteImportacao tipoArquivo,
        List<CampoPacoteImportacaoDto> campos,
        String observacaoSanitizada) {

    public DicionarioArquivoImportacaoDto {
        campos = List.copyOf(campos == null ? List.of() : campos);
        observacaoSanitizada = normalizarOpcional(observacaoSanitizada);
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
