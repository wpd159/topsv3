package br.com.topsdojob.v3.importacao.relatorio;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.topsdojob.v3.importacao.mapeamento.MapeamentoLegadoV3Dto;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;

public final class ImportacaoRelatorioBuilder {

    private final Map<TipoEntidadeImportacao, Long> itensPorTipo =
            new EnumMap<>(TipoEntidadeImportacao.class);
    private final Map<SeveridadePendenciaImportacao, Long> pendenciasPorSeveridade =
            new EnumMap<>(SeveridadePendenciaImportacao.class);
    private final Map<CodigoPendenciaImportacao, Long> pendenciasPorCodigo =
            new EnumMap<>(CodigoPendenciaImportacao.class);
    private long totalItens;
    private long totalPendencias;
    private boolean contemPendenciaBloqueante;

    public ImportacaoRelatorioBuilder adicionarItem(TipoEntidadeImportacao tipoEntidade) {
        Objects.requireNonNull(tipoEntidade, "tipoEntidade deve ser informado");
        totalItens++;
        incrementar(itensPorTipo, tipoEntidade);
        return this;
    }

    public ImportacaoRelatorioBuilder adicionarMapeamento(MapeamentoLegadoV3Dto mapeamento) {
        Objects.requireNonNull(mapeamento, "mapeamento deve ser informado");
        adicionarItem(mapeamento.tipoEntidade());
        adicionarPendencias(mapeamento.pendencias());
        return this;
    }

    public ImportacaoRelatorioBuilder adicionarPendencia(PendenciaImportacaoDto pendencia) {
        Objects.requireNonNull(pendencia, "pendencia deve ser informada");
        totalPendencias++;
        incrementar(pendenciasPorCodigo, pendencia.codigo());
        incrementar(pendenciasPorSeveridade, pendencia.severidade());
        if (pendencia.severidade() == SeveridadePendenciaImportacao.BLOQUEANTE) {
            contemPendenciaBloqueante = true;
        }
        return this;
    }

    public ImportacaoRelatorioBuilder adicionarPendencias(List<PendenciaImportacaoDto> pendencias) {
        if (pendencias == null) {
            return this;
        }
        pendencias.forEach(this::adicionarPendencia);
        return this;
    }

    public RelatorioImportacaoResumoDto build() {
        return new RelatorioImportacaoResumoDto(
                totalItens,
                totalPendencias,
                pendenciasPorSeveridade,
                pendenciasPorCodigo,
                itensPorTipo,
                contemPendenciaBloqueante);
    }

    private static <E extends Enum<E>> void incrementar(Map<E, Long> mapa, E chave) {
        mapa.merge(chave, 1L, Long::sum);
    }
}
