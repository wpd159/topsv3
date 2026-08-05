package br.com.topsdojob.v3.application.publico.anunciante.dto;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MeuAnuncioDto(
        UUID id,
        String slug,
        String titulo,
        String descricao,
        String categoria,
        BigDecimal preco,
        String whatsapp,
        String linkConteudo,
        List<String> locaisAtendimento,
        List<String> servicos,
        boolean atendimentoExclusivamenteVirtual,
        String status,
        String statusModeracao,
        MeuAnuncioLocalizacaoDto localizacao,
        MeuAnuncioCapaDto capa,
        List<MeuAnuncioMidiaDto> midias,
        OffsetDateTime atualizadoEm,
        MeuAnuncioAcoesDto acoesPermitidas,
        VisualizacoesCanonicasDto visualizacoes,
        MeuAnuncioReprovacaoDto reprovacao,
        List<MeuAnuncioBeneficioDto> beneficiosPremium,
        MinhaContaStoryDto storyAtivo) {

    public MeuAnuncioDto(
            UUID id,
            String slug,
            String titulo,
            String descricao,
            String categoria,
            BigDecimal preco,
            String whatsapp,
            String linkConteudo,
            List<String> locaisAtendimento,
            List<String> servicos,
            boolean atendimentoExclusivamenteVirtual,
            String status,
            String statusModeracao,
            MeuAnuncioLocalizacaoDto localizacao,
            MeuAnuncioCapaDto capa,
            List<MeuAnuncioMidiaDto> midias,
            OffsetDateTime atualizadoEm,
            MeuAnuncioAcoesDto acoesPermitidas,
            VisualizacoesCanonicasDto visualizacoes,
            MeuAnuncioReprovacaoDto reprovacao,
            List<MeuAnuncioBeneficioDto> beneficiosPremium) {
        this(
                id, slug, titulo, descricao, categoria, preco, whatsapp, linkConteudo,
                locaisAtendimento, servicos, atendimentoExclusivamenteVirtual, status,
                statusModeracao, localizacao, capa, midias, atualizadoEm, acoesPermitidas,
                visualizacoes, reprovacao, beneficiosPremium, null);
    }
}
