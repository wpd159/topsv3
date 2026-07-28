package br.com.topsdojob.v3.application.admin.pagamentos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminRelatorioReceitaDtos {

    private AdminRelatorioReceitaDtos() {
    }

    public record Resumo(
            BigDecimal receitaConfirmada,
            long pagamentosConfirmados,
            BigDecimal ticketMedio,
            long creditosVendidos,
            long pagamentosPendentes,
            long pagamentosFalhos,
            long pagamentosCancelados,
            long pagamentosEstornados,
            List<PontoDiario> evolucaoDiaria,
            List<DistribuicaoProduto> distribuicaoPorProduto,
            List<AlertaConciliacao> alertasConciliacao,
            LocalDate dataInicio,
            LocalDate dataFim,
            String timezone,
            OffsetDateTime calculadoEm,
            boolean somenteLeitura) {
    }

    public record PontoDiario(
            LocalDate data,
            BigDecimal receitaConfirmada,
            long pagamentosConfirmados) {
    }

    public record DistribuicaoProduto(
            String codigo,
            String nome,
            BigDecimal receitaConfirmada,
            long pagamentosConfirmados,
            long creditosVendidos) {
    }

    public record AlertaConciliacao(
            String codigo,
            String mensagem,
            long quantidade) {
    }

    public record Transacao(
            UUID id,
            OffsetDateTime data,
            String usuarioNome,
            String usuarioEmailMascarado,
            String tipo,
            String produto,
            BigDecimal valor,
            String moeda,
            Integer creditos,
            String status,
            String metodo,
            String identificadorExternoMascarado,
            boolean receitaConfirmada,
            boolean somenteLeitura) {
    }

    public record PaginaTransacoes(
            List<Transacao> itens,
            long total,
            int pagina,
            int tamanho,
            BigDecimal receitaConfirmadaFiltrada,
            long pagamentosConfirmadosFiltrados,
            LocalDate dataInicio,
            LocalDate dataFim,
            String timezone,
            boolean somenteLeitura) {
    }
}
