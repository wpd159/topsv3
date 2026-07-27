package br.com.topsdojob.v3.application.blog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BlogPostRequest(
    UUID categoriaId,
    String titulo,
    String slug,
    String resumo,
    String conteudo,
    String autorNome,
    String seoTitle,
    String seoDescription,
    BigDecimal sitemapPriority,
    String changeFrequency,
    UUID imagemCapaId,
    UUID imagemOgId,
    Long versao) {
}
