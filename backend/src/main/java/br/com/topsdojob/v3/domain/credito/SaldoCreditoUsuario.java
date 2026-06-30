package br.com.topsdojob.v3.domain.credito;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SaldoCreditoUsuario(
    UUID usuarioId,
    Integer saldoAtual,
    OffsetDateTime atualizadoEm,
    Integer versao) {
  public static final String TABELA = "saldo_credito_usuario";
}
