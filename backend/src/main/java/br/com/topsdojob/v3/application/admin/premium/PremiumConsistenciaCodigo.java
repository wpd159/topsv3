package br.com.topsdojob.v3.application.admin.premium;

public enum PremiumConsistenciaCodigo {
    PREMIUM_OK(false),
    BENEFICIO_ATIVO(false),
    BENEFICIO_EXPIRADO(false),
    BENEFICIO_VENCE_EM_BREVE(false),
    BENEFICIO_PENDENTE(false),
    BENEFICIO_SEM_GRUPO(false),
    GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO(true),
    BENEFICIO_EXPIRADO_ANTES_DO_GRUPO(true),
    GRUPO_SEM_BENEFICIOS(true),
    DATA_INVALIDA(true),
    ORIGEM_DESCONHECIDA(true);

    private final boolean inconsistencia;

    PremiumConsistenciaCodigo(boolean inconsistencia) {
        this.inconsistencia = inconsistencia;
    }

    public boolean inconsistencia() {
        return inconsistencia;
    }
}
