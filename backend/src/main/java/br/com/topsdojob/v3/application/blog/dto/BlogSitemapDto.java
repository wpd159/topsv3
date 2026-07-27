package br.com.topsdojob.v3.application.blog.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BlogSitemapDto(
    String slug,
    OffsetDateTime publishedAt,
    OffsetDateTime updatedAt,
    BigDecimal priority,
    String changeFrequency) {
}
