package br.com.topsdojob.v3.importacao.mapeamento;

import java.util.List;
import java.util.Objects;

import br.com.topsdojob.v3.importacao.model.DecisaoUrlImportacao;
import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;

public record LinhaMapaUrlDto(
        String urlAtualPath,
        String urlDestinoPath,
        TipoEntidadeImportacao tipoEntidade,
        String idLegado,
        DecisaoUrlImportacao decisao,
        List<PendenciaImportacaoDto> pendencias) {

    public LinhaMapaUrlDto {
        urlAtualPath = exigirPath(urlAtualPath, "urlAtualPath");
        urlDestinoPath = normalizarOpcional(urlDestinoPath);
        Objects.requireNonNull(tipoEntidade, "tipoEntidade deve ser informado");
        idLegado = normalizarOpcional(idLegado);
        Objects.requireNonNull(decisao, "decisao deve ser informada");
        if (decisao == DecisaoUrlImportacao.SEM_DECISAO) {
            throw new IllegalArgumentException("decisao deve ser definida para URL importada");
        }
        pendencias = List.copyOf(pendencias == null ? List.of() : pendencias);
    }

    private static String exigirPath(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " deve ser informado");
        }
        String normalizado = valor.trim();
        if (!normalizado.startsWith("/")) {
            throw new IllegalArgumentException(campo + " deve ser path relativo iniciado por /");
        }
        return normalizado;
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
