package br.com.topsdojob.v3.application.admin.desempenho;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AdminDesempenhoSanitizerTest {

    private final AdminDesempenhoSanitizer sanitizer = new AdminDesempenhoSanitizer();

    @Test
    void taxaCliqueViewCalculaSemDivisaoPorZero() {
        assertThat(sanitizer.taxaCliqueView(12, 100)).isEqualByComparingTo(new BigDecimal("0.1200"));
        assertThat(sanitizer.taxaCliqueView(5, 0)).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    void sanitizaOrigemSemExporCampoBruto() {
        assertThat(sanitizer.uf("zz")).isEqualTo("ZZ");
        assertThat(sanitizer.uf("estado")).isNull();
        assertThat(sanitizer.texto(" Cidade   Sintetica ")).isEqualTo("Cidade Sintetica");
    }
}
