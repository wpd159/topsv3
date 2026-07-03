package br.com.topsdojob.v3.application.admin.creditos;

public enum CreditoConsistenciaCodigo {
    CREDITO_OK(false),
    SALDO_INCONSISTENTE(true),
    MOVIMENTO_SEM_ORIGEM(true),
    IDEMPOTENCY_KEY_DUPLICADA(true),
    CREDITO_SEM_PAGAMENTO(true),
    PAGAMENTO_APROVADO_SEM_CREDITO(true),
    REGRA_AJUSTE_CREDITO_PENDENTE(true),
    QUANTIDADE_INVALIDA(true),
    SALDO_NEGATIVO(true),
    PAGAMENTO_NAO_CONFIRMADO(true);

    private final boolean inconsistencia;

    CreditoConsistenciaCodigo(boolean inconsistencia) {
        this.inconsistencia = inconsistencia;
    }

    public boolean inconsistencia() {
        return inconsistencia;
    }
}
