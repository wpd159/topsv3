package br.com.topsdojob.v3.importacao.pacote;

import java.util.List;

import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;

public record ResultadoValidacaoPacoteImportacao(
        boolean valido,
        List<PendenciaImportacaoDto> pendencias) {

    public ResultadoValidacaoPacoteImportacao {
        pendencias = List.copyOf(pendencias == null ? List.of() : pendencias);
    }

    public static ResultadoValidacaoPacoteImportacao aprovado() {
        return new ResultadoValidacaoPacoteImportacao(true, List.of());
    }

    public static ResultadoValidacaoPacoteImportacao comPendencias(List<PendenciaImportacaoDto> pendencias) {
        List<PendenciaImportacaoDto> copia = List.copyOf(pendencias == null ? List.of() : pendencias);
        boolean valido = copia.stream().noneMatch(ResultadoValidacaoPacoteImportacao::bloqueiaValidacao);
        return new ResultadoValidacaoPacoteImportacao(valido, copia);
    }

    public List<PendenciaImportacaoDto> alertas() {
        return pendencias.stream()
                .filter(pendencia -> pendencia.severidade() == SeveridadePendenciaImportacao.ALERTA)
                .toList();
    }

    public List<PendenciaImportacaoDto> errosOuBloqueios() {
        return pendencias.stream()
                .filter(ResultadoValidacaoPacoteImportacao::bloqueiaValidacao)
                .toList();
    }

    public boolean possuiPendenciaBloqueante() {
        return pendencias.stream()
                .anyMatch(pendencia -> pendencia.severidade() == SeveridadePendenciaImportacao.BLOQUEANTE);
    }

    private static boolean bloqueiaValidacao(PendenciaImportacaoDto pendencia) {
        return pendencia.severidade() == SeveridadePendenciaImportacao.ERRO
                || pendencia.severidade() == SeveridadePendenciaImportacao.BLOQUEANTE;
    }
}
