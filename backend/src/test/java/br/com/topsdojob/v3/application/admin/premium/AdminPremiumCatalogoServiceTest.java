package br.com.topsdojob.v3.application.admin.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoUpdateRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumOpcaoUpdateRequest;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminPremiumCatalogoServiceTest {

    private final BeneficioPremiumRepository beneficioRepository = mock(BeneficioPremiumRepository.class);
    private final BeneficioPremiumOpcaoRepository opcaoRepository =
            mock(BeneficioPremiumOpcaoRepository.class);
    private final PremiumCatalogoService catalogoService = mock(PremiumCatalogoService.class);
    private final AdminCreditoOperacaoService auditoriaService = mock(AdminCreditoOperacaoService.class);
    private final AdminPremiumCatalogoService service = new AdminPremiumCatalogoService(
            beneficioRepository,
            opcaoRepository,
            catalogoService,
            auditoriaService);

    @Test
    void catalogoPremiumGenericoRejeitaAlteracaoDeStories() {
        UUID beneficioId = UUID.randomUUID();
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "STORIES",
                "Stories",
                "Identidade tecnica de Stories",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                OffsetDateTime.now(ZoneOffset.UTC));
        when(beneficioRepository.findByIdForUpdate(beneficioId)).thenReturn(Optional.of(beneficio));
        AdminUserPrincipal admin = mock(AdminUserPrincipal.class);
        when(admin.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarBeneficio(
                beneficioId,
                new AdminPremiumCatalogoUpdateRequest(null, null, null, null, null, null),
                admin,
                "req-catalogo-stories"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(opcaoRepository, catalogoService, auditoriaService);
    }

    @Test
    void catalogoPremiumRejeitaMarcadorAusenteComBadRequestControlado() {
        UUID beneficioId = UUID.randomUUID();
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "ANUNCIO_TOPO",
                "Anuncio no topo",
                "Prioridade de exibicao",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                true,
                OffsetDateTime.now(ZoneOffset.UTC));
        when(beneficioRepository.findByIdForUpdate(beneficioId)).thenReturn(Optional.of(beneficio));
        AdminUserPrincipal admin = mock(AdminUserPrincipal.class);
        when(admin.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarBeneficio(
                beneficioId,
                new AdminPremiumCatalogoUpdateRequest(
                        "Anuncio no topo",
                        "Prioridade de exibicao",
                        true,
                        0,
                        null,
                        List.of(new AdminPremiumOpcaoUpdateRequest(1, 10, true, 0))),
                admin,
                "req-catalogo-sem-marcador"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(opcaoRepository, catalogoService, auditoriaService);
    }
    @Test
    void catalogoPremiumAceitaRoundTripComPrecisaoCompativelComPostgres() {
        UUID beneficioId = UUID.randomUUID();
        OffsetDateTime marcadorBanco = OffsetDateTime.parse("2026-08-03T12:34:56.123456Z");
        OffsetDateTime marcadorRecebido = OffsetDateTime.parse("2026-08-03T12:34:56.123456789Z");
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "ANUNCIO_TOPO",
                "Anuncio no topo",
                "Prioridade de exibicao",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                true,
                marcadorBanco);
        when(beneficioRepository.findByIdForUpdate(beneficioId)).thenReturn(Optional.of(beneficio));
        when(opcaoRepository.findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(beneficioId, 1))
                .thenReturn(Optional.empty());
        when(catalogoService.catalogoAdministrativo()).thenAnswer(ignorada -> List.of(new PremiumCatalogoDto(
                beneficioId,
                beneficio.getCodigo(),
                beneficio.getNome(),
                beneficio.getDescricao(),
                "ANUNCIO",
                true,
                true,
                0,
                beneficio.getAtualizadoEm(),
                List.of())));
        AdminUserPrincipal admin = mock(AdminUserPrincipal.class);
        when(admin.isEnabled()).thenReturn(true);

        PremiumCatalogoDto resultado = service.atualizarBeneficio(
                beneficioId,
                new AdminPremiumCatalogoUpdateRequest(
                        "Anuncio no topo",
                        "Prioridade de exibicao",
                        true,
                        0,
                        marcadorRecebido,
                        List.of(new AdminPremiumOpcaoUpdateRequest(1, 10, true, 0))),
                admin,
                "req-catalogo-round-trip");

        assertThat(resultado.atualizadoEm()).isEqualTo(beneficio.getAtualizadoEm());
        assertThat(resultado.atualizadoEm().getNano() % 1_000).isZero();
        assertThat(resultado.atualizadoEm()).isAfter(marcadorBanco);
    }
    @Test
    void catalogoPremiumRejeitaAtualizacaoConcorrenteSemSobrescreverOpcoes() {
        UUID beneficioId = UUID.randomUUID();
        OffsetDateTime atualizadoEm = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "ANUNCIO_TOPO",
                "Anuncio no topo",
                "Prioridade de exibicao",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                true,
                atualizadoEm);
        when(beneficioRepository.findByIdForUpdate(beneficioId)).thenReturn(Optional.of(beneficio));
        AdminUserPrincipal admin = mock(AdminUserPrincipal.class);
        when(admin.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarBeneficio(
                beneficioId,
                new AdminPremiumCatalogoUpdateRequest(
                        null,
                        null,
                        null,
                        null,
                        atualizadoEm.minusSeconds(1),
                        null),
                admin,
                "req-catalogo-concorrente"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getReason()).contains("outra sessao");
                        });

        verifyNoInteractions(opcaoRepository, catalogoService, auditoriaService);
    }
}
