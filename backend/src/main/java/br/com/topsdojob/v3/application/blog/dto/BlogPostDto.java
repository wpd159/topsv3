package br.com.topsdojob.v3.application.blog.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BlogPostDto(
    UUID id,
    String titulo,
    String slug,
    String resumo,
    String conteudo,
    String categoria,
    UUID categoriaId,
    String categoriaSlug,
    String imagemUrl,
    UUID imagemCapaId,
    String autorNome,
    String status,
    String seoTitle,
    String seoDescription,
    String ogImageUrl,
    UUID imagemOgId,
    BigDecimal sitemapPriority,
    String changeFrequency,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    long versao) {
}
