package br.com.topsdojob.v3.application.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.repository.AgregadoCliqueWhatsappDiarioRepository;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoDiariaRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AdminDashboardHojeServiceTest {

    private final AgregadoVisualizacaoDiariaRepository visualizacaoRepository =
            mock(AgregadoVisualizacaoDiariaRepository.class);
    private final AgregadoCliqueWhatsappDiarioRepository cliqueRepository =
            mock(AgregadoCliqueWhatsappDiarioRepository.class);
    private final AtivacaoBeneficioRepository beneficioRepository =
            mock(AtivacaoBeneficioRepository.class);

    @Test
    void usaAgregadosCanonicosComODiaDeSaoPaulo() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T02:30:00Z"), ZoneOffset.UTC);
        LocalDate diaLocal = LocalDate.of(2026, 7, 27);
        OffsetDateTime agora = OffsetDateTime.parse("2026-07-28T02:30:00Z");
        when(visualizacaoRepository.somarPorData(diaLocal)).thenReturn(31L);
        when(cliqueRepository.somarPorData(diaLocal)).thenReturn(7L);
        when(beneficioRepository.countVigentes(agora)).thenReturn(5L);
        var service = new AdminDashboardHojeService(
                visualizacaoRepository,
                cliqueRepository,
                beneficioRepository,
                clock);

        var result = service.consultar();

        assertThat(result.dataReferencia()).isEqualTo(diaLocal);
        assertThat(result.fusoHorario()).isEqualTo("America/Sao_Paulo");
        assertThat(result.visualizacoes()).isEqualTo(31);
        assertThat(result.cliquesWhatsapp()).isEqualTo(7);
        assertThat(result.beneficiosPremiumVigentes()).isEqualTo(5);
        assertThat(result.calculadoEm()).isEqualTo(agora);
        verify(visualizacaoRepository).somarPorData(diaLocal);
        verify(cliqueRepository).somarPorData(diaLocal);
        verify(beneficioRepository).countVigentes(agora);
    }

    @Test
    void preservaZeroLegitimoDosAgregados() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T15:00:00Z"), ZoneOffset.UTC);
        var service = new AdminDashboardHojeService(
                visualizacaoRepository,
                cliqueRepository,
                beneficioRepository,
                clock);

        var result = service.consultar();

        assertThat(result.visualizacoes()).isZero();
        assertThat(result.cliquesWhatsapp()).isZero();
        assertThat(result.beneficiosPremiumVigentes()).isZero();
    }
}
