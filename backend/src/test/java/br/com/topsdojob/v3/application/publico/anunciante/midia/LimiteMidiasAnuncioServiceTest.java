package br.com.topsdojob.v3.application.publico.anunciante.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimiteMidiasAnuncioServiceTest {

    private final BeneficioAnuncioConsultaService beneficioService = mock(BeneficioAnuncioConsultaService.class);
    private final LimiteMidiasAnuncioService service = new LimiteMidiasAnuncioService(beneficioService);

    @Test
    void planoBasePermiteQuatroFotosEUmVideo() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of());

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(4, 1, false));
    }

    @Test
    void somenteFotosExtraCincoAtivoOuVencendoAmpliaParaDez() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                calculado("FOTOS_EXTRA", PremiumBeneficioStatusCalculado.ATIVO),
                calculado("FOTOS_EXTRA_5", PremiumBeneficioStatusCalculado.EXPIRADO),
                calculado("FOTOS_EXTRA_5", PremiumBeneficioStatusCalculado.VENCENDO)));

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(10, 1, true));
    }

    @Test
    void beneficioExpiradoNaoAmpliaOLimite() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                calculado("FOTOS_EXTRA_5", PremiumBeneficioStatusCalculado.EXPIRADO)));

        assertThat(service.resolver(anuncioId).maxFotos()).isEqualTo(4);
    }

    private PremiumBeneficioCalculado calculado(String codigo, PremiumBeneficioStatusCalculado status) {
        var beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(), codigo, codigo, codigo, EscopoBeneficioPremium.ANUNCIO,
                false, true, OffsetDateTime.now(ZoneOffset.UTC));
        return new PremiumBeneficioCalculado(null, beneficio, null, status, List.of(), false, false);
    }
}
