package br.com.topsdojob.v3.application.admin.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminDashboardAnalyticsDtos {

    private AdminDashboardAnalyticsDtos() {
    }

    public record DesempenhoDiario(
            int dias,
            LocalDate inicio,
            LocalDate fim,
            String fusoHorario,
            List<PontoDiario> serieDiaria,
            ResumoDia hoje,
            ResumoDia ontem,
            BigDecimal variacaoVisualizacoesPct,
            BigDecimal variacaoCliquesPct,
            long totalVisualizacoes,
            long totalCliquesWhatsapp,
            OffsetDateTime calculadoEm) {
    }

    public record PontoDiario(
            LocalDate data,
            long visualizacoes,
            long cliquesWhatsapp,
            BigDecimal conversaoPct) {
    }

    public record ResumoDia(
            LocalDate data,
            long visualizacoes,
            long cliquesWhatsapp,
            BigDecimal conversaoPct) {
    }

    public record TopWhatsappHoje(
            LocalDate dataReferencia,
            String fusoHorario,
            int limite,
            boolean temMais,
            List<TopWhatsappItem> itens,
            OffsetDateTime calculadoEm) {
    }

    public record TopWhatsappItem(
            UUID anuncioId,
            String titulo,
            String slug,
            String cidade,
            String uf,
            long cliquesWhatsappHoje,
            String miniaturaUrl,
            boolean publicado) {
    }

    public record Analises(
            List<Insight> alertasPrioritarios,
            List<Insight> oportunidadesComerciais,
            List<AnuncioDesempenho> topConversao,
            List<AnuncioDesempenho> piorConversaoComTrafego,
            List<CidadeDesempenho> desempenhoPorCidade,
            List<ClassificacaoDesempenho> desempenhoPorClassificacao,
            int minimoVisualizacoesPiorConversao,
            OffsetDateTime calculadoEm) {
    }

    public record Insight(
            String codigo,
            String titulo,
            String descricao,
            String href) {
    }

    public record AnuncioDesempenho(
            UUID anuncioId,
            String titulo,
            String slug,
            String cidade,
            String uf,
            long visualizacoes,
            long cliquesWhatsapp,
            BigDecimal conversaoPct) {
    }

    public record CidadeDesempenho(
            String cidade,
            String cidadeSlug,
            String uf,
            long anunciosPublicadosAtivos,
            long visualizacoes,
            long cliquesWhatsapp,
            BigDecimal conversaoPct) {
    }

    public record ClassificacaoDesempenho(
            String classificacao,
            long anunciosPublicadosAtivos,
            long visualizacoes,
            long cliquesWhatsapp,
            BigDecimal conversaoPct) {
    }
}
