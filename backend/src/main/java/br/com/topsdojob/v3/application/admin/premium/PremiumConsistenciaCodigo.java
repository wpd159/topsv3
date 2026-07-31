package br.com.topsdojob.v3.application.admin.premium;

public enum PremiumConsistenciaCodigo {
    PREMIUM_OK(false),
    BENEFICIO_ATIVO(false),
    BENEFICIO_EXPIRADO(false),
    BENEFICIO_VENCE_EM_BREVE(false),
    BENEFICIO_PENDENTE(false),
    BENEFICIO_AGUARDANDO_MODERACAO(false),
    BENEFICIO_INATIVO(false),
    BENEFICIO_CANCELADO_OU_REVOGADO(false),
    BENEFICIO_CATALOGO_INATIVO(false),
    GRUPO_PENDENTE(false),
    GRUPO_INATIVO(false),
    BENEFICIO_SEM_GRUPO(true),
    BENEFICIO_NAO_ENCONTRADO(true),
    BENEFICIO_FORA_JANELA_GRUPO(true),
    VINCULO_GRUPO_DIVERGENTE(true),
    ORIGEM_GRUPO_DIVERGENTE(true),
    STATUS_INVALIDO(true),
    REFERENCIA_TEMPORAL_INVALIDA(true),
    GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO(true),
    BENEFICIO_EXPIRADO_ANTES_DO_GRUPO(false),
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
