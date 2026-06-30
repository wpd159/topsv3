package br.com.topsdojob.v3.domain.usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessaoUsuario(
    UUID id,
    UUID usuarioId,
    String tokenSessaoHash,
    String dispositivoHash,
    String ipCriacaoHash,
    String userAgentHash,
    OffsetDateTime criadaEm,
    OffsetDateTime ultimoUsoEm,
    OffsetDateTime expiraInatividadeEm,
    OffsetDateTime expiraAbsolutaEm,
    OffsetDateTime revogadaEm,
    String motivoRevogacao,
    Integer versao) {
  public static final String TABELA = "sessao_usuario";
}
