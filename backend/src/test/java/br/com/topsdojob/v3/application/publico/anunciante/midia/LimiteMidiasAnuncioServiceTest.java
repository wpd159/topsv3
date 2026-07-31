package br.com.topsdojob.v3.application.publico.anunciante.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
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
    void planoBasePermiteQuatroFotosESemVideo() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of());

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(4, 0, false, false));
    }

    @Test
    void somenteCodigoCanonicoAtivoOuVencendoAmpliaParaDez() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                calculado("FOTOS_EXTRA", PremiumBeneficioStatusCalculado.ATIVO),
                calculado("FOTOS_EXTRA_5", PremiumBeneficioStatusCalculado.EXPIRADO),
                calculado("FOTOS_EXTRA_5", PremiumBeneficioStatusCalculado.VENCENDO)));

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(10, 0, true, false));
    }

    @Test
    void codigoLegadoNaoAmpliaOLimite() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                calculado("FOTOS_EXTRA", PremiumBeneficioStatusCalculado.ATIVO)));

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(4, 0, false, false));
    }

    @Test
    void fotosExtrasAguardandoModeracaoReservamCapacidadeSemEfeitoPublico() {
        UUID anuncioId = UUID.randomUUID();
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                "FOTOS_EXTRA_5",
                "Mais fotos",
                "Mais fotos",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                OffsetDateTime.now(ZoneOffset.UTC));
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
                UUID.randomUUID(),
                beneficio.getId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                anuncioId,
                UUID.randomUUID(),
                10,
                "limite-fotos-aguardando",
                OffsetDateTime.now(ZoneOffset.UTC));
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                new PremiumBeneficioCalculado(
                        ativacao,
                        beneficio,
                        null,
                        PremiumBeneficioStatusCalculado.PENDENTE,
                        List.of(),
                        false,
                        false)));

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(10, 0, true, false));
    }

    @Test
    void beneficioExpiradoNaoAmpliaOLimite() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                calculado("FOTOS_EXTRA_5", PremiumBeneficioStatusCalculado.EXPIRADO)));

        assertThat(service.resolver(anuncioId).maxFotos()).isEqualTo(4);
    }

    @Test
    void videoExigeBeneficioCanonicoAtivoOuVencendo() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                calculado("VIDEO_1", PremiumBeneficioStatusCalculado.VENCENDO)));

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(4, 1, false, true));
    }

    @Test
    void beneficioVideoExpiradoNaoLiberaUpload() {
        UUID anuncioId = UUID.randomUUID();
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(List.of(
                calculado("VIDEO_1", PremiumBeneficioStatusCalculado.EXPIRADO)));

        assertThat(service.resolver(anuncioId))
                .isEqualTo(new LimiteMidiasAnuncioService.Resultado(4, 0, false, false));
    }

    private PremiumBeneficioCalculado calculado(String codigo, PremiumBeneficioStatusCalculado status) {
        var beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(), codigo, codigo, codigo, EscopoBeneficioPremium.ANUNCIO,
                false, true, OffsetDateTime.now(ZoneOffset.UTC));
        return new PremiumBeneficioCalculado(null, beneficio, null, status, List.of(), false, false);
    }
}
