package br.com.topsdojob.v3.domain.seo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SeoUrl(
    UUID id,
    String caminhoPublico,
    String canonicalPath,
    SeoTipos.TipoUrl tipo,
    String entidadeTipo,
    UUID entidadeId,
    SeoTipos.StatusEsperado statusEsperado,
    Boolean indexavel,
    Boolean incluirSitemap,
    SeoTipos.QualidadeStatus qualidadeStatus,
    OffsetDateTime ultimaValidacaoEm,
    String motivoNoindex,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm,
    Integer versao) {
  public static final String TABELA = "seo_url";
}
