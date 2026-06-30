package br.com.topsdojob.v3.domain.documento;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentoUsuario(
    UUID id,
    UUID usuarioId,
    UUID arquivoMidiaId,
    DocumentoTipos.TipoDocumento tipo,
    DocumentoTipos.StatusDocumento status,
    DocumentoTipos.PoliticaRetencao politicaRetencao,
    OffsetDateTime retencaoAte,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm,
    UUID validadoPor,
    OffsetDateTime validadoEm,
    OffsetDateTime removidoEm,
    OffsetDateTime expurgadoEm) {
  public static final String TABELA = "documento_usuario";
}
