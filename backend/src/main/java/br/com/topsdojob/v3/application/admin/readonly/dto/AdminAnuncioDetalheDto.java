package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminAnuncioDetalheDto(
        UUID id,
        String slug,
        String titulo,
        String descricaoResumo,
        String descricao,
        String status,
        String statusModeracao,
        String categoria,
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
        boolean comercialLimitado,
        BigDecimal preco,
        String whatsapp,
        List<String> locaisAtendimento,
        List<String> servicos,
        AdminAnuncianteResumoDto anunciante,
        AdminRevisaoAbertaDto revisaoAberta) {
}
