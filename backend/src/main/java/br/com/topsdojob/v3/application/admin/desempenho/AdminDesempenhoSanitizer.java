package br.com.topsdojob.v3.application.admin.desempenho;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class AdminDesempenhoSanitizer {

    private static final int TEXT_LIMIT = 120;

    public BigDecimal taxaCliqueView(long cliques, long visualizacoes) {
        if (visualizacoes <= 0 || cliques <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(cliques)
                .divide(BigDecimal.valueOf(visualizacoes), 4, RoundingMode.HALF_UP);
    }

    public String texto(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= TEXT_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, TEXT_LIMIT);
    }

    public String uf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.length() == 2 ? normalized : null;
    }
}
