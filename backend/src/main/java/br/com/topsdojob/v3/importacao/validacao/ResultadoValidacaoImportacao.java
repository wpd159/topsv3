package br.com.topsdojob.v3.importacao.validacao;

import java.util.List;

import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;

public record ResultadoValidacaoImportacao(
        boolean valido,
        List<PendenciaImportacaoDto> pendencias) {

    public ResultadoValidacaoImportacao {
        pendencias = List.copyOf(pendencias == null ? List.of() : pendencias);
    }

    public static ResultadoValidacaoImportacao aprovado() {
        return new ResultadoValidacaoImportacao(true, List.of());
    }

    public static ResultadoValidacaoImportacao comPendencias(List<PendenciaImportacaoDto> pendencias) {
        return new ResultadoValidacaoImportacao(false, pendencias);
    }
}
