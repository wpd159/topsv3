package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminMidiaListaItemDto(
        UUID id,
        UUID anuncioId,
        String slugAnuncio,
        String tipo,
        String finalidade,
        Integer ordem,
        String status,
        String classificacaoConteudo,
        String statusArquivo,
        String mimeType,
        Long tamanhoBytes,
        Integer largura,
        Integer altura,
        Integer duracaoMs,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        boolean arquivoPrivadoOculto) {
}
