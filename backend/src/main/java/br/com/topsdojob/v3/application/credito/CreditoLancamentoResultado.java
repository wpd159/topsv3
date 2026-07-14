package br.com.topsdojob.v3.application.credito;

import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;

public record CreditoLancamentoResultado(
        MovimentoCreditoEntity movimento,
        boolean idempotente) {
}
