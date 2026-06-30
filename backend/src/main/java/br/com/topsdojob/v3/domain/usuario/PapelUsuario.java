package br.com.topsdojob.v3.domain.usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PapelUsuario(
    UUID usuarioId,
    UsuarioTipos.Papel papel,
    UUID criadoPor,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "papel_usuario";
}
