package br.com.topsdojob.v3.application.admin.desempenho;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoComparativoPremiumDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoDiarioDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminDesempenhoPremiumComparativoServiceTest {

    private final AdminDesempenhoPremiumComparativoService service =
            new AdminDesempenhoPremiumComparativoService(new AdminDesempenhoSanitizer());

    @Test
    void comparaPremiumComOrganicoSemPromessaGarantidaOuLimiteGratuito() {
        AdminDesempenhoComparativoPremiumDto dto = service.comparar(List.of(
                new AdminDesempenhoDiarioDto(LocalDate.parse("2026-07-01"), 40, 4, new BigDecimal("0.1000"), false, true),
                new AdminDesempenhoDiarioDto(LocalDate.parse("2026-07-02"), 80, 12, new BigDecimal("0.1500"), true, true)),
                List.of("DESTAQUE"));

        assertThat(dto.visualizacoesOrganicas()).isEqualTo(40);
        assertThat(dto.cliquesComPremium()).isEqualTo(12);
        assertThat(dto.beneficiosExposicaoAtivos()).containsExactly("DESTAQUE");
        assertThat(dto.promessaResultadoGarantido()).isFalse();
        assertThat(dto.gratuitoLimitado()).isFalse();
        assertThat(dto.mensagemSegura().toLowerCase())
                .doesNotContain("garantido")
                .doesNotContain("contratacao garantida")
                .contains("resultados variam");
    }
}
