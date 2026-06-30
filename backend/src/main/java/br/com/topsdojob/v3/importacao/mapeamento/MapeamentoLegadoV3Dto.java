package br.com.topsdojob.v3.importacao.mapeamento;

import java.util.List;
import java.util.Objects;

import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.StatusImportacaoItem;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;

public record MapeamentoLegadoV3Dto(
        TipoEntidadeImportacao tipoEntidade,
        String origemLegado,
        String idLegado,
        String idV3,
        StatusImportacaoItem status,
        List<PendenciaImportacaoDto> pendencias) {

    public MapeamentoLegadoV3Dto {
        Objects.requireNonNull(tipoEntidade, "tipoEntidade deve ser informado");
        origemLegado = exigirTexto(origemLegado, "origemLegado");
        idLegado = exigirTexto(idLegado, "idLegado");
        idV3 = normalizarOpcional(idV3);
        Objects.requireNonNull(status, "status deve ser informado");
        pendencias = List.copyOf(pendencias == null ? List.of() : pendencias);
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " deve ser informado");
        }
        return valor.trim();
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
