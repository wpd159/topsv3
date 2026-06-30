package br.com.topsdojob.v3.domain.comercial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ComercialContato(
    UUID id,
    UUID usuarioId,
    UUID anuncioId,
    UUID statusId,
    String nomeContato,
    String emailNormalizado,
    String telefoneNormalizado,
    ComercialTipos.OrigemContato origem,
    String campanhaCodigo,
    UUID responsavelUsuarioId,
    OffsetDateTime proximaAcaoEm,
    Boolean cortesiaConcedida,
    String observacaoResumida,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm,
    Integer versao) {
  public static final String TABELA = "comercial_contato";
}
