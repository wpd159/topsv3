package br.com.topsdojob.v3.application.admin.pagamentos;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RelatorioReceitaFiltro(
        LocalDate dataInicio,
        LocalDate dataFim,
        OffsetDateTime instanteInicio,
        OffsetDateTime instanteFimExclusivo,
        Status status,
        Metodo metodo,
        String usuario,
        String produto) {

    public enum Status {
        TODOS,
        CONFIRMADO,
        PENDENTE,
        FALHO,
        CANCELADO,
        EXPIRADO,
        ESTORNADO,
        LEGADO
    }

    public enum Metodo {
        TODOS,
        PIX,
        LEGADO,
        DESCONHECIDO
    }
}
