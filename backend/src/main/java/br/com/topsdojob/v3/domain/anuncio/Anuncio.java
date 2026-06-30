package br.com.topsdojob.v3.domain.anuncio;

import br.com.topsdojob.v3.domain.shared.ClassificacaoConteudo;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Anuncio(
    UUID id,
    UUID usuarioId,
    String slug,
    String titulo,
    String descricao,
    AnuncioTipos.Status status,
    AnuncioTipos.StatusModeracao statusModeracao,
    String categoria,
    ClassificacaoConteudo classificacaoConteudo,
    BigDecimal preco,
    String whatsappNormalizado,
    OffsetDateTime publicadoEm,
    OffsetDateTime ultimaPublicacaoEm,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm,
    OffsetDateTime removidoEm,
    UUID origemImportacaoId,
    Integer versao) {
  public static final String TABELA = "anuncio";
}
