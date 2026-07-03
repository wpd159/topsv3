package br.com.topsdojob.v3.application.admin.creditos;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoConsistenciaResumoDto;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.credito.SaldoCreditoUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoConciliacaoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CreditoConsistenciaServiceTest {

    private final CreditoConsistenciaService service =
            new CreditoConsistenciaService(null, null, null, null);

    @Test
    void detectaInconsistenciasSemExporDadoFinanceiroSensivel() {
        UUID usuarioId = UUID.fromString("00000000-0000-4000-8000-000000000101");
        UUID pagamentoAprovadoSemCreditoId = UUID.fromString("00000000-0000-4000-8000-000000000201");
        UUID pagamentoPendenteId = UUID.fromString("00000000-0000-4000-8000-000000000202");
        MovimentoCreditoEntity movimentoOk = movimento(
                "00000000-0000-4000-8000-000000000301",
                usuarioId,
                TipoMovimentoCredito.ENTRADA,
                DirecaoMovimentoCredito.CREDITO,
                10,
                0,
                10,
                OrigemMovimentoCredito.PAGAMENTO,
                "PAGAMENTO",
                pagamentoPendenteId,
                "chave-a");
        MovimentoCreditoEntity semPagamento = movimento(
                "00000000-0000-4000-8000-000000000302",
                usuarioId,
                TipoMovimentoCredito.ENTRADA,
                DirecaoMovimentoCredito.CREDITO,
                5,
                10,
                15,
                OrigemMovimentoCredito.PAGAMENTO,
                null,
                null,
                "chave-b");
        MovimentoCreditoEntity ajuste = movimento(
                "00000000-0000-4000-8000-000000000303",
                usuarioId,
                TipoMovimentoCredito.AJUSTE,
                DirecaoMovimentoCredito.CREDITO,
                3,
                15,
                18,
                OrigemMovimentoCredito.AJUSTE_ADMIN,
                null,
                null,
                "chave-b");
        MovimentoCreditoEntity quantidadeInvalida = movimento(
                "00000000-0000-4000-8000-000000000304",
                usuarioId,
                TipoMovimentoCredito.SAIDA,
                DirecaoMovimentoCredito.DEBITO,
                0,
                18,
                -1,
                null,
                null,
                null,
                null);
        SaldoCreditoUsuarioEntity saldo = saldo(usuarioId, 99);
        PagamentoEntity aprovadoSemCredito = pagamento(pagamentoAprovadoSemCreditoId, usuarioId, StatusInternoPagamento.APROVADO);
        PagamentoEntity pendenteReferenciado = pagamento(pagamentoPendenteId, usuarioId, StatusInternoPagamento.AGUARDANDO_PAGAMENTO);

        AdminCreditoConsistenciaResumoDto resumo = service.consultar(
                List.of(movimentoOk, semPagamento, ajuste, quantidadeInvalida),
                List.of(saldo),
                List.of(aprovadoSemCredito, pendenteReferenciado),
                List.of(),
                OffsetDateTime.parse("2026-07-02T21:00:00Z"));

        assertThat(resumo.somenteLeitura()).isTrue();
        assertThat(resumo.itens())
                .extracting("codigo")
                .contains(
                        CreditoConsistenciaCodigo.PAGAMENTO_NAO_CONFIRMADO.name(),
                        CreditoConsistenciaCodigo.CREDITO_SEM_PAGAMENTO.name(),
                        CreditoConsistenciaCodigo.REGRA_AJUSTE_CREDITO_PENDENTE.name(),
                        CreditoConsistenciaCodigo.IDEMPOTENCY_KEY_DUPLICADA.name(),
                        CreditoConsistenciaCodigo.QUANTIDADE_INVALIDA.name(),
                        CreditoConsistenciaCodigo.MOVIMENTO_SEM_ORIGEM.name(),
                        CreditoConsistenciaCodigo.SALDO_NEGATIVO.name(),
                        CreditoConsistenciaCodigo.SALDO_INCONSISTENTE.name(),
                        CreditoConsistenciaCodigo.PAGAMENTO_APROVADO_SEM_CREDITO.name());
        assertThat(resumo.toString())
                .doesNotContain("txid")
                .doesNotContain("identificadorProvedor")
                .doesNotContain("valor")
                .doesNotContain("cpf")
                .doesNotContain("whatsapp");
    }

    @Test
    void movimentoConsistenteSemAlertaQuandoSaldoFecha() {
        UUID usuarioId = UUID.fromString("00000000-0000-4000-8000-000000000101");
        MovimentoCreditoEntity movimento = movimento(
                "00000000-0000-4000-8000-000000000401",
                usuarioId,
                TipoMovimentoCredito.ENTRADA,
                DirecaoMovimentoCredito.CREDITO,
                10,
                0,
                10,
                OrigemMovimentoCredito.CAMPANHA,
                null,
                null,
                "chave-ok");

        AdminCreditoConsistenciaResumoDto resumo = service.consultar(
                List.of(movimento),
                List.of(saldo(usuarioId, 10)),
                List.of(),
                List.of(),
                OffsetDateTime.parse("2026-07-02T21:00:00Z"));

        assertThat(resumo.itens()).isEmpty();
    }

    private MovimentoCreditoEntity movimento(
            String id,
            UUID usuarioId,
            TipoMovimentoCredito tipo,
            DirecaoMovimentoCredito direcao,
            Integer quantidade,
            Integer saldoAntes,
            Integer saldoDepois,
            OrigemMovimentoCredito origem,
            String referenciaTipo,
            UUID referenciaId,
            String chaveOperacional) {
        MovimentoCreditoEntity movimento = novaInstancia(MovimentoCreditoEntity.class);
        ReflectionTestUtils.setField(movimento, "id", UUID.fromString(id));
        ReflectionTestUtils.setField(movimento, "usuarioId", usuarioId);
        ReflectionTestUtils.setField(movimento, "tipo", tipo);
        ReflectionTestUtils.setField(movimento, "direcao", direcao);
        ReflectionTestUtils.setField(movimento, "quantidade", quantidade);
        ReflectionTestUtils.setField(movimento, "saldoAntes", saldoAntes);
        ReflectionTestUtils.setField(movimento, "saldoDepois", saldoDepois);
        ReflectionTestUtils.setField(movimento, "origem", origem);
        ReflectionTestUtils.setField(movimento, "referenciaTipo", referenciaTipo);
        ReflectionTestUtils.setField(movimento, "referenciaId", referenciaId);
        ReflectionTestUtils.setField(movimento, "idempotencyKey", chaveOperacional);
        ReflectionTestUtils.setField(movimento, "criadoEm", OffsetDateTime.parse("2026-07-02T21:00:00Z"));
        return movimento;
    }

    private SaldoCreditoUsuarioEntity saldo(UUID usuarioId, Integer saldoAtual) {
        SaldoCreditoUsuarioEntity saldo = novaInstancia(SaldoCreditoUsuarioEntity.class);
        ReflectionTestUtils.setField(saldo, "usuarioId", usuarioId);
        ReflectionTestUtils.setField(saldo, "saldoAtual", saldoAtual);
        ReflectionTestUtils.setField(saldo, "atualizadoEm", OffsetDateTime.parse("2026-07-02T21:00:00Z"));
        ReflectionTestUtils.setField(saldo, "versao", 1);
        return saldo;
    }

    private PagamentoEntity pagamento(UUID id, UUID usuarioId, StatusInternoPagamento status) {
        PagamentoEntity pagamento = novaInstancia(PagamentoEntity.class);
        ReflectionTestUtils.setField(pagamento, "id", id);
        ReflectionTestUtils.setField(pagamento, "usuarioId", usuarioId);
        ReflectionTestUtils.setField(pagamento, "statusInterno", status);
        return pagamento;
    }

    private <T> T novaInstancia(Class<T> tipo) {
        try {
            java.lang.reflect.Constructor<T> constructor = tipo.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Falha ao criar instancia de teste", exception);
        }
    }
}
