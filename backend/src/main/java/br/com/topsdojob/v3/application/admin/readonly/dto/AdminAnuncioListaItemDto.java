package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;

public record AdminAnuncioListaItemDto(
        UUID id,
        String slug,
        String titulo,
        String miniaturaUrl,
        String status,
        String statusModeracao,
        AdminLocalizacaoSanitizadaDto localizacao,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        OffsetDateTime publicadoEm,
        Long midiasTotal,
        Long revisoesTotal,
        boolean contatoConfigurado,
        boolean documentoPendente,
        boolean comercialLimitado,
        List<String> beneficiosPremiumVigentes,
        List<AdminPremiumFilaItemDto> beneficiosPremium,
        VisualizacoesCanonicasDto visualizacoes,
        long cliquesWhatsapp,
        AdminAnuncianteResumoDto anunciante,
        AdminStoryAnuncioAcaoDto storyAcao,
        AdminRevisaoAbertaDto revisaoAberta) {
}
