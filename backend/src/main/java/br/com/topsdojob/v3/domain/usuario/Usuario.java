package br.com.topsdojob.v3.domain.usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Usuario(
    UUID id,
    String nome,
    String emailNormalizado,
    String telefoneNormalizado,
    UsuarioTipos.Status status,
    UsuarioTipos.TipoConta tipoConta,
    OffsetDateTime emailVerificadoEm,
    OffsetDateTime telefoneVerificadoEm,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm,
    OffsetDateTime desativadoEm,
    Integer versao) {
  public static final String TABELA = "usuario";
}
