package br.com.topsdojob.v3.application.admin.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoCreateRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoUpdateRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumOpcaoUpdateRequest;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import br.com.topsdojob.v3.application.premium.dto.PremiumOpcaoDto;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminPremiumCatalogoServiceTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-02T15:00:00Z");

    private final BeneficioPremiumRepository beneficios = mock(BeneficioPremiumRepository.class);
    private final BeneficioPremiumOpcaoRepository opcoes = mock(BeneficioPremiumOpcaoRepository.class);
    private final PremiumCatalogoService catalogo = mock(PremiumCatalogoService.class);
    private final AdminCreditoOperacaoService auditoria = mock(AdminCreditoOperacaoService.class);
    private AdminPremiumCatalogoService service;

    @BeforeEach
    void setUp() {
        service = new AdminPremiumCatalogoService(beneficios, opcoes, catalogo, auditoria);
    }

    @Test
    void criaStoriesComDuracaoECustoExplicitamenteDefinidosPeloAdmin() {
        AtomicReference<UUID> beneficioId = new AtomicReference<>();
        when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.empty());
        when(beneficios.saveAndFlush(any())).thenAnswer(invocation -> {
            BeneficioPremiumEntity entity = invocation.getArgument(0);
            beneficioId.set(entity.getId());
            return entity;
        });
        when(opcoes.findByBeneficioIdOrderByOrdemExibicaoAscDuracaoDiasAsc(any()))
                .thenReturn(List.of());
        when(catalogo.catalogoAdministrativo()).thenAnswer(invocation -> List.of(
                catalogo(
                        beneficioId.get(),
                        List.of(new PremiumOpcaoDto(
                                UUID.randomUUID(), 3, 9, true, 4)))));

        PremiumCatalogoDto resultado = service.criarBeneficio(
                new AdminPremiumCatalogoCreateRequest(
                        "stories",
                        "Stories",
                        "Publicacao comercial de Story",
                        true,
                        40,
                        List.of(new AdminPremiumOpcaoUpdateRequest(3, 9, true, 4))),
                admin(),
                "req-catalogo-create");

        ArgumentCaptor<BeneficioPremiumEntity> beneficioSalvo =
                ArgumentCaptor.forClass(BeneficioPremiumEntity.class);
        verify(beneficios).saveAndFlush(beneficioSalvo.capture());
        assertThat(beneficioSalvo.getValue().getCodigo()).isEqualTo("STORIES");
        assertThat(beneficioSalvo.getValue().getEscopo()).isEqualTo(EscopoBeneficioPremium.ANUNCIO);
        assertThat(beneficioSalvo.getValue().getAfetaRanking()).isFalse();
        assertThat(resultado.opcoes()).singleElement().satisfies(opcao -> {
            assertThat(opcao.duracaoDias()).isEqualTo(3);
            assertThat(opcao.custoCreditos()).isEqualTo(9);
        });

        ArgumentCaptor<BeneficioPremiumOpcaoEntity> opcaoSalva =
                ArgumentCaptor.forClass(BeneficioPremiumOpcaoEntity.class);
        verify(opcoes).save(opcaoSalva.capture());
        assertThat(opcaoSalva.getValue().getDuracaoDias()).isEqualTo(3);
        assertThat(opcaoSalva.getValue().getCustoCreditos()).isEqualTo(9);
        verify(auditoria).auditar(
                eq(admin().usuarioId()),
                eq("PREMIUM_CATALOGO_CRIAR"),
                eq("BENEFICIO_PREMIUM"),
                eq(beneficioId.get()),
                any(),
                any(),
                eq("req-catalogo-create"));
    }

    @Test
    void retryComCodigoJaExistenteNaoDuplicaBeneficio() {
        when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(beneficioStories()));

        assertThatThrownBy(() -> service.criarBeneficio(
                criarRequest(List.of(new AdminPremiumOpcaoUpdateRequest(5, 12, true, 0))),
                admin(),
                "req-catalogo-retry"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(beneficios, never()).saveAndFlush(any());
        verify(opcoes, never()).save(any());
        verify(auditoria, never()).auditar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void atualizaDuracaoArbitrariaEDesativaOpcaoOmitidaSemExcluir() {
        BeneficioPremiumEntity beneficio = beneficioStories();
        BeneficioPremiumOpcaoEntity anterior = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(),
                beneficio.getId(),
                7,
                20,
                true,
                1,
                AGORA.minusDays(5));
        when(beneficios.findByIdForUpdate(beneficio.getId())).thenReturn(Optional.of(beneficio));
        when(opcoes.findByBeneficioIdOrderByOrdemExibicaoAscDuracaoDiasAsc(beneficio.getId()))
                .thenReturn(List.of(anterior));
        when(catalogo.catalogoAdministrativo()).thenReturn(List.of(
                catalogo(
                        beneficio.getId(),
                        List.of(new PremiumOpcaoDto(UUID.randomUUID(), 45, 31, true, 2)))));

        PremiumCatalogoDto resultado = service.atualizarBeneficio(
                beneficio.getId(),
                new AdminPremiumCatalogoUpdateRequest(
                        "Stories",
                        "Publicacao comercial de Story",
                        true,
                        40,
                        List.of(new AdminPremiumOpcaoUpdateRequest(45, 31, true, 2))),
                admin(),
                "req-catalogo-update");

        assertThat(anterior.getAtivo()).isFalse();
        ArgumentCaptor<BeneficioPremiumOpcaoEntity> salvas =
                ArgumentCaptor.forClass(BeneficioPremiumOpcaoEntity.class);
        verify(opcoes, times(2)).save(salvas.capture());
        assertThat(salvas.getAllValues())
                .anySatisfy(opcao -> {
                    assertThat(opcao.getDuracaoDias()).isEqualTo(45);
                    assertThat(opcao.getCustoCreditos()).isEqualTo(31);
                    assertThat(opcao.getAtivo()).isTrue();
                });
        assertThat(resultado.opcoes()).extracting(PremiumOpcaoDto::duracaoDias).containsExactly(45);
        verify(opcoes, never()).delete(any());
    }

    @Test
    void rejeitaDuracaoNaoPositivaOuDuplicadaSemPersistir() {
        BeneficioPremiumEntity beneficio = beneficioStories();
        when(beneficios.findByIdForUpdate(beneficio.getId())).thenReturn(Optional.of(beneficio));

        assertThatThrownBy(() -> service.atualizarBeneficio(
                beneficio.getId(),
                atualizarRequest(List.of(new AdminPremiumOpcaoUpdateRequest(0, 1, true, 0))),
                admin(),
                "req-zero"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.atualizarBeneficio(
                beneficio.getId(),
                atualizarRequest(List.of(
                        new AdminPremiumOpcaoUpdateRequest(2, 1, true, 0),
                        new AdminPremiumOpcaoUpdateRequest(2, 2, true, 1))),
                admin(),
                "req-duplicate"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(opcoes, never()).save(any());
        verify(beneficios, never()).saveAndFlush(any());
    }

    @Test
    void moderadorNaoAlteraCatalogoMesmoChamandoServiceDiretamente() {
        assertThatThrownBy(() -> service.criarBeneficio(
                criarRequest(List.of(new AdminPremiumOpcaoUpdateRequest(2, 4, true, 0))),
                moderador(),
                "req-forbidden"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(beneficios, never()).saveAndFlush(any());
    }

    @Test
    void criacaoConcorrenteRetornaConflitoSemCriarOpcoesOuAuditoria() {
        when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.empty());
        when(beneficios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("codigo duplicado"));

        assertThatThrownBy(() -> service.criarBeneficio(
                criarRequest(List.of(new AdminPremiumOpcaoUpdateRequest(2, 4, true, 0))),
                admin(),
                "req-concorrente"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(opcoes, never()).save(any());
        verify(auditoria, never()).auditar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void atualizacaoSerializaOpcoesComLockPessimistaNoBeneficio() throws NoSuchMethodException {
        var method = BeneficioPremiumRepository.class.getMethod("findByIdForUpdate", UUID.class);
        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private AdminPremiumCatalogoCreateRequest criarRequest(
            List<AdminPremiumOpcaoUpdateRequest> opcoesRequest) {
        return new AdminPremiumCatalogoCreateRequest(
                "STORIES",
                "Stories",
                "Publicacao comercial de Story",
                false,
                40,
                opcoesRequest);
    }

    private AdminPremiumCatalogoUpdateRequest atualizarRequest(
            List<AdminPremiumOpcaoUpdateRequest> opcoesRequest) {
        return new AdminPremiumCatalogoUpdateRequest(
                "Stories",
                "Publicacao comercial de Story",
                false,
                40,
                opcoesRequest);
    }

    private BeneficioPremiumEntity beneficioStories() {
        return BeneficioPremiumEntity.criarCatalogo(
                UUID.randomUUID(),
                "STORIES",
                "Stories",
                "Publicacao comercial de Story",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                40,
                AGORA);
    }

    private PremiumCatalogoDto catalogo(UUID id, List<PremiumOpcaoDto> opcoesDto) {
        return new PremiumCatalogoDto(
                id,
                "STORIES",
                "Stories",
                "Publicacao comercial de Story",
                "ANUNCIO",
                false,
                true,
                40,
                opcoesDto);
    }

    private AdminUserPrincipal admin() {
        return principal(PapelUsuario.ADMIN);
    }

    private AdminUserPrincipal moderador() {
        return principal(PapelUsuario.MODERADOR);
    }

    private AdminUserPrincipal principal(PapelUsuario papel) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + papel.name()),
                new SimpleGrantedAuthority("PREMIUM_GERENCIAR"));
        return new AdminUserPrincipal(
                UUID.fromString("00000000-0000-4000-8000-000000000901"),
                "Operador QA",
                "operador.qa@example.invalid",
                "hash",
                List.of(papel),
                authorities.stream()
                        .map(authority -> new AdminPermissionDto(
                                authority.getAuthority(),
                                authority.getAuthority()))
                        .toList(),
                authorities,
                true);
    }
}
