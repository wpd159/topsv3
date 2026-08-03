package br.com.topsdojob.v3.application.admin.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoUpdateRequest;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        when(beneficioRepository.findById(beneficioId)).thenReturn(Optional.of(beneficio));
        AdminUserPrincipal admin = mock(AdminUserPrincipal.class);
        when(admin.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarBeneficio(
                beneficioId,
                new AdminPremiumCatalogoUpdateRequest(null, null, null, null, null),
                admin,
                "req-catalogo-stories"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(opcaoRepository, catalogoService, auditoriaService);
    }
}