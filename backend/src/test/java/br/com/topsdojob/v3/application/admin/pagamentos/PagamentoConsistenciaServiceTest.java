package br.com.topsdojob.v3.application.admin.pagamentos;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoConsistenciaResumoDto;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoConciliacaoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEventoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PagamentoConsistenciaServiceTest {

    private final PagamentoConsistenciaService service = new PagamentoConsistenciaService(
            null,
            null,
            null,
            null,
            null,
            new PagamentoEvidenciaProvedorService(),
            new PagamentoSanitizer());

    @Test
    void detectaInconsistenciasSemExporEvidenciaBruta() {
        UUID usuarioId = UUID.fromString("00000000-0000-4000-8000-000000000102");
        PagamentoEntity efiOk = pagamento(
                "00000000-0000-4000-8000-000000000721",
                usuarioId,
                ProvedorPagamento.EFI,
                MetodoPagamento.PIX,
                StatusInternoPagamento.APROVADO,
                "CONCLUIDO",
                "pix-local-721",
                "efi-721");
        PagamentoEntity semCredito = pagamento(
                "00000000-0000-4000-8000-000000000722",
                usuarioId,
                ProvedorPagamento.EFI,
                MetodoPagamento.PIX,
                StatusInternoPagamento.APROVADO,
                "CONCLUIDO",
                "pix-local-722",
                "efi-722");
        PagamentoEntity mpLegado = pagamento(
                "00000000-0000-4000-8000-000000000723",
                usuarioId,
                ProvedorPagamento.MERCADO_PAGO_LEGADO,
                MetodoPagamento.LEGADO,
                StatusInternoPagamento.LEGADO,
                "IMPORTADO",
                null,
                "mp-723");
        PagamentoEntity efiPendenteSemEvidencia = pagamento(
                "00000000-0000-4000-8000-000000000724",
                usuarioId,
                ProvedorPagamento.EFI,
                MetodoPagamento.PIX,
                StatusInternoPagamento.AGUARDANDO_PAGAMENTO,
                "PENDENTE",
                null,
                null);
        PagamentoEntity desconhecido = pagamento(
                "00000000-0000-4000-8000-000000000725",
                usuarioId,
                ProvedorPagamento.DESCONHECIDO,
                MetodoPagamento.DESCONHECIDO,
                StatusInternoPagamento.AGUARDANDO_PAGAMENTO,
                "PENDENTE",
                null,
                null);
        PagamentoEntity statusDivergente = pagamento(
                "00000000-0000-4000-8000-000000000726",
                usuarioId,
                ProvedorPagamento.EFI,
                MetodoPagamento.PIX,
                StatusInternoPagamento.APROVADO,
                "PENDENTE_LOCAL",
                "pix-local-726",
                "efi-726");
        MovimentoCreditoEntity movimentoConciliado = movimento(
                "00000000-0000-4000-8000-000000000731",
                usuarioId,
                "00000000-0000-4000-8000-000000000721");
        MovimentoCreditoEntity creditoSemPagamento = movimento(
                "00000000-0000-4000-8000-000000000732",
                usuarioId,
                null);
        PagamentoConciliacaoEntity conciliacao = conciliacao(
                "00000000-0000-4000-8000-000000000741",
                "00000000-0000-4000-8000-000000000721",
                "00000000-0000-4000-8000-000000000731");
        PagamentoEventoEntity evento = evento(
                "00000000-0000-4000-8000-000000000751",
                "00000000-0000-4000-8000-000000000721",
                ProvedorPagamento.EFI,
                "hash-sintetico");
        PagamentoWebhookEntity webhookA = webhook(ProvedorPagamento.EFI, "pix-local-721", "hash-duplicado");
        PagamentoWebhookEntity webhookB = webhook(ProvedorPagamento.EFI, "pix-local-721", "hash-duplicado");

        AdminPagamentoConsistenciaResumoDto resumo = service.consultar(
                List.of(efiOk, semCredito, mpLegado, efiPendenteSemEvidencia, desconhecido, statusDivergente),
                List.of(movimentoConciliado, creditoSemPagamento),
                List.of(conciliacao),
                List.of(evento),
                List.of(webhookA, webhookB),
                OffsetDateTime.parse("2026-07-02T22:00:00Z"));

        assertThat(resumo.somenteLeitura()).isTrue();
        assertThat(resumo.itens())
                .extracting("codigo")
                .contains(
                        PagamentoConsistenciaCodigo.PAGAMENTO_SEM_TXID.name(),
                        PagamentoConsistenciaCodigo.PAGAMENTO_APROVADO_SEM_CREDITO.name(),
                        PagamentoConsistenciaCodigo.CREDITO_SEM_PAGAMENTO.name(),
                        PagamentoConsistenciaCodigo.PAGAMENTO_MERCADO_PAGO_LEGADO.name(),
                        PagamentoConsistenciaCodigo.PAGAMENTO_PROVEDOR_DESCONHECIDO.name(),
                        PagamentoConsistenciaCodigo.STATUS_PAGAMENTO_INCONSISTENTE.name(),
                        PagamentoConsistenciaCodigo.EVENTO_WEBHOOK_DUPLICADO.name(),
                        PagamentoConsistenciaCodigo.PAGAMENTO_PAYLOAD_SENSIVEL_OCULTO.name());
        assertThat(resumo.toString())
                .doesNotContain("pix-local-721")
                .doesNotContain("efi-721")
                .doesNotContain("identificadorProvedor")
                .doesNotContain("valor");
    }

    private PagamentoEntity pagamento(
            String id,
            UUID usuarioId,
            ProvedorPagamento provedor,
            MetodoPagamento metodo,
            StatusInternoPagamento status,
            String statusProvedor,
            String txid,
            String identificadorProvedor) {
        PagamentoEntity pagamento = novaInstancia(PagamentoEntity.class);
        ReflectionTestUtils.setField(pagamento, "id", UUID.fromString(id));
        ReflectionTestUtils.setField(pagamento, "usuarioId", usuarioId);
        ReflectionTestUtils.setField(pagamento, "provedor", provedor);
        ReflectionTestUtils.setField(pagamento, "metodo", metodo);
        ReflectionTestUtils.setField(pagamento, "statusInterno", status);
        ReflectionTestUtils.setField(pagamento, "statusProvedor", statusProvedor);
        ReflectionTestUtils.setField(pagamento, "txid", txid);
        ReflectionTestUtils.setField(pagamento, "identificadorProvedor", identificadorProvedor);
        return pagamento;
    }

    private MovimentoCreditoEntity movimento(String id, UUID usuarioId, String pagamentoId) {
        MovimentoCreditoEntity movimento = novaInstancia(MovimentoCreditoEntity.class);
        ReflectionTestUtils.setField(movimento, "id", UUID.fromString(id));
        ReflectionTestUtils.setField(movimento, "usuarioId", usuarioId);
        ReflectionTestUtils.setField(movimento, "origem", OrigemMovimentoCredito.PAGAMENTO);
        ReflectionTestUtils.setField(movimento, "referenciaTipo", pagamentoId == null ? null : "PAGAMENTO");
        ReflectionTestUtils.setField(movimento, "referenciaId", pagamentoId == null ? null : UUID.fromString(pagamentoId));
        return movimento;
    }

    private PagamentoConciliacaoEntity conciliacao(String id, String pagamentoId, String movimentoId) {
        PagamentoConciliacaoEntity conciliacao = novaInstancia(PagamentoConciliacaoEntity.class);
        ReflectionTestUtils.setField(conciliacao, "id", UUID.fromString(id));
        ReflectionTestUtils.setField(conciliacao, "pagamentoId", UUID.fromString(pagamentoId));
        ReflectionTestUtils.setField(conciliacao, "movimentoCreditoId", UUID.fromString(movimentoId));
        return conciliacao;
    }

    private PagamentoEventoEntity evento(String id, String pagamentoId, ProvedorPagamento provedor, String payloadHash) {
        PagamentoEventoEntity evento = novaInstancia(PagamentoEventoEntity.class);
        ReflectionTestUtils.setField(evento, "id", UUID.fromString(id));
        ReflectionTestUtils.setField(evento, "pagamentoId", UUID.fromString(pagamentoId));
        ReflectionTestUtils.setField(evento, "provedor", provedor);
        ReflectionTestUtils.setField(evento, "payloadHash", payloadHash);
        return evento;
    }

    private PagamentoWebhookEntity webhook(ProvedorPagamento provedor, String txid, String payloadHash) {
        PagamentoWebhookEntity webhook = novaInstancia(PagamentoWebhookEntity.class);
        ReflectionTestUtils.setField(webhook, "provedor", provedor);
        ReflectionTestUtils.setField(webhook, "txid", txid);
        ReflectionTestUtils.setField(webhook, "payloadHash", payloadHash);
        return webhook;
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
