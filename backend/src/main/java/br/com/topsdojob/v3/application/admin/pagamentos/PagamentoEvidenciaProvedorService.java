package br.com.topsdojob.v3.application.admin.pagamentos;

import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEventoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoWebhookEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class PagamentoEvidenciaProvedorService {

    public ProvedorPagamento classificar(
            PagamentoEntity pagamento,
            Collection<PagamentoEventoEntity> eventos,
            Collection<PagamentoWebhookEntity> webhooks) {
        ProvedorPagamento declarado = pagamento == null ? null : pagamento.getProvedor();
        if (declarado == ProvedorPagamento.EFI || contemProvedor(eventos, webhooks, ProvedorPagamento.EFI)) {
            return ProvedorPagamento.EFI;
        }
        if (declarado == ProvedorPagamento.MERCADO_PAGO_LEGADO
                || contemProvedor(eventos, webhooks, ProvedorPagamento.MERCADO_PAGO_LEGADO)) {
            return ProvedorPagamento.MERCADO_PAGO_LEGADO;
        }
        if (declarado == ProvedorPagamento.OUTRO_LEGADO
                || contemProvedor(eventos, webhooks, ProvedorPagamento.OUTRO_LEGADO)) {
            return ProvedorPagamento.OUTRO_LEGADO;
        }
        return ProvedorPagamento.DESCONHECIDO;
    }

    private boolean contemProvedor(
            Collection<PagamentoEventoEntity> eventos,
            Collection<PagamentoWebhookEntity> webhooks,
            ProvedorPagamento provedor) {
        return eventos.stream().anyMatch(item -> item.getProvedor() == provedor)
                || webhooks.stream().anyMatch(item -> item.getProvedor() == provedor);
    }
}
