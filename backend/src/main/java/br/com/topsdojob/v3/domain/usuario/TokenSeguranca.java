package br.com.topsdojob.v3.domain.usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TokenSeguranca(
    UUID id,
    UUID usuarioId,
    UsuarioTipos.TipoTokenSeguranca tipo,
    String tokenHash,
    OffsetDateTime expiraEm,
    Integer tentativas,
    OffsetDateTime consumidoEm,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "token_seguranca";
}
