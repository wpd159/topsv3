package br.com.topsdojob.v3.importacao;

import java.util.List;
import java.util.Objects;

import br.com.topsdojob.v3.importacao.mapeamento.MapeamentoLegadoV3Dto;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.relatorio.ImportacaoRelatorioBuilder;
import br.com.topsdojob.v3.importacao.relatorio.RelatorioImportacaoResumoDto;
import br.com.topsdojob.v3.importacao.validacao.ResultadoValidacaoImportacao;

public final class ImportacaoSaneadorEstrutural {

    public RelatorioImportacaoResumoDto montarResumoEmMemoria(List<MapeamentoLegadoV3Dto> mapeamentos) {
        ImportacaoRelatorioBuilder builder = new ImportacaoRelatorioBuilder();
        List<MapeamentoLegadoV3Dto> itens = List.copyOf(mapeamentos == null ? List.of() : mapeamentos);
        itens.forEach(builder::adicionarMapeamento);
        return builder.build();
    }

    public ResultadoValidacaoImportacao validarPendenciasEmMemoria(List<MapeamentoLegadoV3Dto> mapeamentos) {
        List<PendenciaImportacaoDto> pendencias = List.copyOf(mapeamentos == null
                ? List.of()
                : mapeamentos.stream()
                        .filter(Objects::nonNull)
                        .flatMap(mapeamento -> mapeamento.pendencias().stream())
                        .toList());
        if (pendencias.isEmpty()) {
            return ResultadoValidacaoImportacao.aprovado();
        }
        return ResultadoValidacaoImportacao.comPendencias(pendencias);
    }
}
