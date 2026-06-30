package br.com.topsdojob.v3.importacao.manifesto;

import java.util.List;
import java.util.Objects;

import br.com.topsdojob.v3.importacao.model.PendenciaImportacaoDto;
import br.com.topsdojob.v3.importacao.model.StatusImportacaoItem;

public record ManifestoMidiaItemDto(
        String origemLegado,
        String idLegado,
        String caminhoLegadoSanitizado,
        String checksumSha256,
        String bucketDestino,
        String chaveDestino,
        StatusImportacaoItem status,
        List<PendenciaImportacaoDto> pendencias) {

    public ManifestoMidiaItemDto {
        origemLegado = exigirTexto(origemLegado, "origemLegado");
        idLegado = exigirTexto(idLegado, "idLegado");
        caminhoLegadoSanitizado = exigirTexto(caminhoLegadoSanitizado, "caminhoLegadoSanitizado");
        checksumSha256 = normalizarOpcional(checksumSha256);
        bucketDestino = normalizarOpcional(bucketDestino);
        chaveDestino = normalizarOpcional(chaveDestino);
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
