package br.com.topsdojob.v3.application.admin.pagamentos;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEventoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PagamentoEvidenciaProvedorServiceTest {

    private final PagamentoEvidenciaProvedorService service = new PagamentoEvidenciaProvedorService();

    @Test
    void classificaEfiComEvidenciaExplicita() {
        PagamentoEntity pagamento = pagamento(ProvedorPagamento.EFI, MetodoPagamento.PIX);

        assertThat(service.classificar(pagamento, List.of(), List.of()))
                .isEqualTo(ProvedorPagamento.EFI);
    }

    @Test
    void naoAssumeMercadoPagoPorSerLegadoSemEvidencia() {
        PagamentoEntity pagamento = pagamento(ProvedorPagamento.DESCONHECIDO, MetodoPagamento.LEGADO);

        assertThat(service.classificar(pagamento, List.of(), List.of()))
                .isEqualTo(ProvedorPagamento.DESCONHECIDO);
    }

    @Test
    void usaEvidenciaExplicitaDeMercadoPagoLegado() {
        PagamentoEntity pagamento = pagamento(ProvedorPagamento.DESCONHECIDO, MetodoPagamento.LEGADO);
        PagamentoEventoEntity evento = novaInstancia(PagamentoEventoEntity.class);
        ReflectionTestUtils.setField(evento, "provedor", ProvedorPagamento.MERCADO_PAGO_LEGADO);
        PagamentoWebhookEntity webhook = novaInstancia(PagamentoWebhookEntity.class);
        ReflectionTestUtils.setField(webhook, "provedor", ProvedorPagamento.MERCADO_PAGO_LEGADO);

        assertThat(service.classificar(pagamento, List.of(evento), List.of(webhook)))
                .isEqualTo(ProvedorPagamento.MERCADO_PAGO_LEGADO);
    }

    private PagamentoEntity pagamento(ProvedorPagamento provedor, MetodoPagamento metodo) {
        PagamentoEntity pagamento = novaInstancia(PagamentoEntity.class);
        ReflectionTestUtils.setField(pagamento, "provedor", provedor);
        ReflectionTestUtils.setField(pagamento, "metodo", metodo);
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
