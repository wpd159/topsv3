package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAnuncioListaItemDto(
        UUID id,
        String slug,
        String titulo,
        String status,
        String statusModeracao,
        String classificacaoConteudo,
        AdminLocalizacaoSanitizadaDto localizacao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        OffsetDateTime publicadoEm,
        Long midiasTotal,
        Long revisoesTotal,
        boolean contatoConfigurado,
        boolean documentoPendente,
        boolean comercialLimitado) {
}
