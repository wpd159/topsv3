package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAnuncioDetalheDto(
        UUID id,
        String slug,
        String titulo,
        String descricaoResumo,
        String status,
        String statusModeracao,
        String categoria,
        String classificacaoConteudo,
        AdminLocalizacaoSanitizadaDto localizacao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        OffsetDateTime publicadoEm,
        OffsetDateTime ultimaPublicacaoEm,
        Long midiasTotal,
        Long revisoesTotal,
        boolean contatoConfigurado,
        boolean documentoPendente,
        boolean precoInformado,
        boolean comercialLimitado) {
}
