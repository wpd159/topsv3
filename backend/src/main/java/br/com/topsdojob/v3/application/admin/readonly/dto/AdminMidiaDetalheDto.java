package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminMidiaDetalheDto(
        UUID id,
        UUID anuncioId,
        UUID arquivoMidiaId,
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
