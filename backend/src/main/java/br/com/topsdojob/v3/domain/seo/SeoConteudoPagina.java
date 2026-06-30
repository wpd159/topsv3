package br.com.topsdojob.v3.domain.seo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SeoConteudoPagina(
    UUID id,
    UUID seoUrlId,
    String chave,
    String titulo,
    String corpoMarkdown,
    SeoTipos.StatusConteudo status,
    SeoTipos.OrigemConteudo origem,
    UUID criadoPor,
    UUID aprovadoPor,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm,
    OffsetDateTime aprovadoEm,
    Integer versao) {
  public static final String TABELA = "seo_conteudo_pagina";
}
