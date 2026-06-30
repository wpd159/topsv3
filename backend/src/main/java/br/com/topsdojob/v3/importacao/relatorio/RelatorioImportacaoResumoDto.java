package br.com.topsdojob.v3.importacao.relatorio;

import java.util.Map;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;

public record RelatorioImportacaoResumoDto(
        long totalItens,
        long totalPendencias,
        Map<SeveridadePendenciaImportacao, Long> pendenciasPorSeveridade,
        Map<CodigoPendenciaImportacao, Long> pendenciasPorCodigo,
        Map<TipoEntidadeImportacao, Long> itensPorTipo,
        boolean contemPendenciaBloqueante) {

    public RelatorioImportacaoResumoDto {
        if (totalItens < 0) {
            throw new IllegalArgumentException("totalItens nao pode ser negativo");
        }
        if (totalPendencias < 0) {
            throw new IllegalArgumentException("totalPendencias nao pode ser negativo");
        }
        pendenciasPorSeveridade = Map.copyOf(pendenciasPorSeveridade == null ? Map.of() : pendenciasPorSeveridade);
        pendenciasPorCodigo = Map.copyOf(pendenciasPorCodigo == null ? Map.of() : pendenciasPorCodigo);
        itensPorTipo = Map.copyOf(itensPorTipo == null ? Map.of() : itensPorTipo);
    }
}
