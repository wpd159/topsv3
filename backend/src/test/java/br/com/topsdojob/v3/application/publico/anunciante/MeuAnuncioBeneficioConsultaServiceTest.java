package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioBeneficioDto;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeuAnuncioBeneficioConsultaServiceTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-03T18:30:00Z");
    private final BeneficioAnuncioConsultaService beneficioService =
            mock(BeneficioAnuncioConsultaService.class);
    private final BeneficioPremiumOpcaoRepository opcaoRepository =
            mock(BeneficioPremiumOpcaoRepository.class);
    private final MeuAnuncioBeneficioConsultaService service = new MeuAnuncioBeneficioConsultaService(
            beneficioService,
            opcaoRepository,
            Clock.fixed(Instant.parse("2026-08-03T18:30:00Z"), ZoneOffset.UTC));
    private UUID anuncioId;
    private UUID usuarioId;
    private BeneficioPremiumEntity beneficio;
    private BeneficioPremiumOpcaoEntity opcao;

    @BeforeEach
    void setUp() {
        anuncioId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                FOTOS_EXTRA_5,
                "Fotos extras",
                "Capacidade adicional",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                AGORA.minusYears(1));
        opcao = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(), beneficio.getId(), 7, 10, true, 1, AGORA.minusYears(1));
        when(opcaoRepository.findAllById(any())).thenReturn(List.of(opcao));
    }

    @Test
    void exibeEsperaSemFabricarDatasOuPrazo() {
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
                UUID.randomUUID(),
                beneficio.getId(),
                opcao.getId(),
                usuarioId,
                anuncioId,
                UUID.randomUUID(),
                10,
                "card-aguardando",
                AGORA.minusDays(3));
        preparar(ativacao, PremiumBeneficioStatusCalculado.PENDENTE);

        MeuAnuncioBeneficioDto dto = unico();

        assertThat(dto.nome()).isEqualTo("Mais fotos");
        assertThat(dto.status()).isEqualTo("AGUARDANDO_MODERACAO");
        assertThat(dto.inicioEm()).isNull();
        assertThat(dto.fimEm()).isNull();
        assertThat(dto.duracaoDias()).isEqualTo(7);
        assertThat(dto.diasRestantes()).isNull();
        assertThat(dto.motivoEspera()).isEqualTo("AGUARDANDO_APROVACAO_MODERACAO");
    }

    @Test
    void exibeInicioFimEDiasRestantesCalculadosNoBackend() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-08-02T18:30:00Z");
        OffsetDateTime fim = OffsetDateTime.parse("2026-08-09T18:30:00Z");
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarCompraComCreditos(
                UUID.randomUUID(),
                beneficio.getId(),
                opcao.getId(),
                usuarioId,
                anuncioId,
                UUID.randomUUID(),
                inicio,
                fim,
                10,
                "card-ativo",
                inicio);
        preparar(ativacao, PremiumBeneficioStatusCalculado.ATIVO);

        MeuAnuncioBeneficioDto dto = unico();

        assertThat(dto.status()).isEqualTo("ATIVO");
        assertThat(dto.inicioEm()).isEqualTo(inicio);
        assertThat(dto.fimEm()).isEqualTo(fim);
        assertThat(dto.duracaoDias()).isEqualTo(7);
        assertThat(dto.diasRestantes()).isEqualTo(6);
        assertThat(dto.motivoEspera()).isNull();
    }

    @Test
    void exibeExpiradoSemConverterErroEmEstadoTemporal() {
        OffsetDateTime inicio = OffsetDateTime.parse("2026-07-20T15:00:00Z");
        OffsetDateTime fim = OffsetDateTime.parse("2026-07-27T15:00:00Z");
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarCompraComCreditos(
                UUID.randomUUID(),
                beneficio.getId(),
                opcao.getId(),
                usuarioId,
                anuncioId,
                UUID.randomUUID(),
                inicio,
                fim,
                10,
                "card-expirado",
                inicio);
        preparar(ativacao, PremiumBeneficioStatusCalculado.EXPIRADO);

        MeuAnuncioBeneficioDto dto = unico();

        assertThat(dto.status()).isEqualTo("EXPIRADO");
        assertThat(dto.fimEm()).isEqualTo(fim);
        assertThat(dto.diasRestantes()).isNull();
        verify(beneficioService).consultarCalculadosPorAnuncio(any(), eq(AGORA));
    }

    private void preparar(
            AtivacaoBeneficioEntity ativacao,
            PremiumBeneficioStatusCalculado status) {
        when(beneficioService.consultarCalculadosPorAnuncio(any(), eq(AGORA))).thenReturn(Map.of(
                anuncioId,
                List.of(new PremiumBeneficioCalculado(
                        ativacao,
                        beneficio,
                        null,
                        status,
                        List.of(),
                        status == PremiumBeneficioStatusCalculado.VENCENDO,
                        false))));
    }

    private MeuAnuncioBeneficioDto unico() {
        return service.consultarEmLote(List.of(anuncioId)).get(anuncioId).get(0);
    }
}
