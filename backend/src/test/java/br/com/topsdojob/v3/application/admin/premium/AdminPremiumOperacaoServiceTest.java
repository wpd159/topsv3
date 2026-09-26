package br.com.topsdojob.v3.application.admin.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarRequest;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarLoteItemRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarLoteRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminPremiumOperacaoServiceTest {

    private final AtivacaoBeneficioRepository ativacaoRepository = mock(AtivacaoBeneficioRepository.class);
    private final MovimentoCreditoRepository movimentoRepository = mock(MovimentoCreditoRepository.class);
    private final AdminCreditoOperacaoService creditoService = mock(AdminCreditoOperacaoService.class);
    private final BeneficioPremiumRepository beneficioRepository = mock(BeneficioPremiumRepository.class);
    private final BeneficioPremiumOpcaoRepository opcaoRepository = mock(BeneficioPremiumOpcaoRepository.class);
    private final GrupoAtivacaoBeneficioRepository grupoRepository = mock(GrupoAtivacaoBeneficioRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository =
            mock(AnuncioBloqueioJuridicoRepository.class);
    private final BeneficioAnuncioConsultaService consultaService = mock(BeneficioAnuncioConsultaService.class);
    private final AdminPremiumOperacaoService service = new AdminPremiumOperacaoService(
            ativacaoRepository,
            movimentoRepository,
            creditoService,
            beneficioRepository,
            opcaoRepository,
            grupoRepository,
            anuncioRepository,
            bloqueioJuridicoRepository,
            consultaService,
            mock(ArquivoPublicidadeRegistroService.class));

    @BeforeEach
    void salvarEntidadesSemAlterarArgumentos() {
        when(grupoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ativacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ativaBeneficioAdministrativoDoCatalogoSemDebitarCreditos() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID beneficioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "anuncio-premium-admin",
                "Anuncio Premium administrativo",
                "Descricao valida para ativacao administrativa",
                "MASSAGENS",
                null,
                null,
                agora.minusDays(2));
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "ANUNCIO_TOPO",
                "Anuncio no topo",
                "Beneficio de demonstracao",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                true,
                agora.minusDays(1));
        BeneficioPremiumOpcaoEntity opcao = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(), beneficioId, 7, 20, true, 0, agora.minusDays(1));
        when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(beneficioRepository.findById(beneficioId)).thenReturn(Optional.of(beneficio));
        when(opcaoRepository.findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(beneficioId, 7))
                .thenReturn(Optional.of(opcao));
        when(consultaService.consultarCalculados(anuncioId)).thenReturn(List.of());

        var response = service.ativarManual(
                anuncioId,
                new AdminPremiumAtivarRequest(beneficioId, 7, "Cortesia administrativa autorizada"),
                "operacao-001",
                admin(),
                "req-premium-admin");

        assertThat(response.status()).isEqualTo("ATIVA");
        assertThat(response.creditosEstornados()).isZero();
        assertThat(response.idempotente()).isFalse();
        ArgumentCaptor<GrupoAtivacaoBeneficioEntity> grupo =
                ArgumentCaptor.forClass(GrupoAtivacaoBeneficioEntity.class);
        verify(grupoRepository).save(grupo.capture());
        assertThat(grupo.getValue().getObservacao()).isEqualTo("Cortesia administrativa autorizada");
        verifyNoInteractions(movimentoRepository);
        verify(creditoService).auditar(
                any(),
                eq("PREMIUM_ATIVACAO_ADMINISTRATIVA"),
                eq("ATIVACAO_BENEFICIO"),
                any(),
                eq(Map.of("status", "INEXISTENTE")),
                any(),
                eq("req-premium-admin"));
    }

    @Test
    void concessaoAdministrativaDeFotosExtrasAguardaModeracaoSemDatas() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID beneficioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "fotos-extras-admin",
                "Fotos extras administrativas",
                "Descricao valida para concessao administrativa",
                "MASSAGENS",
                null,
                null,
                agora.minusDays(2));
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "FOTOS_EXTRA_5",
                "Mais fotos",
                "Capacidade adicional de fotos",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                agora.minusDays(1));
        BeneficioPremiumOpcaoEntity opcao = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(), beneficioId, 7, 20, true, 0, agora.minusDays(1));
        when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(beneficioRepository.findById(beneficioId)).thenReturn(Optional.of(beneficio));
        when(opcaoRepository.findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(beneficioId, 7))
                .thenReturn(Optional.of(opcao));
        when(consultaService.consultarCalculados(anuncioId)).thenReturn(List.of());

        var response = service.ativarManual(
                anuncioId,
                new AdminPremiumAtivarRequest(beneficioId, 7, null),
                "operacao-fotos-espera",
                admin(),
                "req-fotos-espera");

        ArgumentCaptor<AtivacaoBeneficioEntity> salva =
                ArgumentCaptor.forClass(AtivacaoBeneficioEntity.class);
        verify(ativacaoRepository).save(salva.capture());
        assertThat(response.status()).isEqualTo("AGUARDANDO_MODERACAO");
        assertThat(salva.getValue().getStatus())
                .isEqualTo(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
        assertThat(salva.getValue().getInicioEm()).isNull();
        assertThat(salva.getValue().getFimEm()).isNull();
        verifyNoInteractions(movimentoRepository);
    }

@Test
    void ativacaoAdministrativaGenericaRejeitaStories() {
        UUID anuncioId = UUID.randomUUID();
        UUID beneficioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "STORIES",
                "Stories",
                "Identidade tecnica de Stories",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                agora.minusDays(1));
        when(beneficioRepository.findById(beneficioId)).thenReturn(Optional.of(beneficio));
        when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(anuncioRepository.findByIdForModeration(anuncioId))
                .thenReturn(Optional.of(anuncioElegivel(anuncioId)));

        assertThatThrownBy(() -> service.ativarManual(
                anuncioId,
                new AdminPremiumAtivarRequest(beneficioId, 7, null),
                "operacao-stories-generica",
                admin(),
                "req-stories-generica"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(opcaoRepository, movimentoRepository);
        verify(grupoRepository, never()).save(any());
        verify(ativacaoRepository, never()).save(any());
    }

    @Test
    void ativacaoAdministrativaGenericaEmLoteRejeitaStories() {
        UUID anuncioId = UUID.randomUUID();
        UUID beneficioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "STORIES",
                "Stories",
                "Identidade tecnica de Stories",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                agora.minusDays(1));
        when(beneficioRepository.findById(beneficioId)).thenReturn(Optional.of(beneficio));
        when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(anuncioRepository.findByIdForModeration(anuncioId))
                .thenReturn(Optional.of(anuncioElegivel(anuncioId)));
        AdminPremiumAtivarLoteRequest request = new AdminPremiumAtivarLoteRequest(
                List.of(new AdminPremiumAtivarLoteItemRequest(beneficioId, 7)),
                null);

        assertThatThrownBy(() -> service.ativarManualLote(
                anuncioId,
                request,
                "operacao-lote-stories-generica",
                admin(),
                "req-lote-stories-generica"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(opcaoRepository, movimentoRepository);
        verify(grupoRepository, never()).save(any());
        verify(ativacaoRepository, never()).save(any());
    }
    @Test
    void ativaBeneficioSemObservacaoEPersisteNullSemFabricarTexto() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID beneficioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "premium-sem-observacao",
                "Anuncio Premium sem observacao",
                "Descricao valida para ativacao sem observacao",
                "MASSAGENS",
                null,
                null,
                agora.minusDays(2));
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                beneficioId,
                "ANUNCIO_TOPO",
                "Anuncio no topo",
                "Beneficio sintetico",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                true,
                agora.minusDays(1));
        BeneficioPremiumOpcaoEntity opcao = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(), beneficioId, 7, 20, true, 0, agora.minusDays(1));
        when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(beneficioRepository.findById(beneficioId)).thenReturn(Optional.of(beneficio));
        when(opcaoRepository.findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(beneficioId, 7))
                .thenReturn(Optional.of(opcao));
        when(consultaService.consultarCalculados(anuncioId)).thenReturn(List.of());

        service.ativarManual(
                anuncioId,
                new AdminPremiumAtivarRequest(beneficioId, 7, null),
                "operacao-sem-observacao",
                admin(),
                "req-sem-observacao");

        ArgumentCaptor<GrupoAtivacaoBeneficioEntity> grupo =
                ArgumentCaptor.forClass(GrupoAtivacaoBeneficioEntity.class);
        verify(grupoRepository).save(grupo.capture());
        assertThat(grupo.getValue().getObservacao()).isNull();
        ArgumentCaptor<Map<String, Object>> depois = ArgumentCaptor.forClass(Map.class);
        verify(creditoService).auditar(
                any(),
                eq("PREMIUM_ATIVACAO_ADMINISTRATIVA"),
                eq("ATIVACAO_BENEFICIO"),
                any(),
                any(),
                depois.capture(),
                eq("req-sem-observacao"));
        assertThat(depois.getValue()).containsEntry("observacaoRegistrada", false);
        verifyNoInteractions(movimentoRepository);
    }

    @Test
    void bloqueioJuridicoDoUsuarioImpedeNovaAtivacaoPremium() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "premium-bloqueado",
                "Anuncio Premium bloqueado",
                "Descricao valida para bloqueio juridico",
                "MASSAGENS",
                null,
                null,
                OffsetDateTime.now().minusDays(2));
        when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(bloqueioJuridicoRepository.existsByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNull(
                usuarioId,
                EscopoBloqueioJuridico.ANUNCIO_E_USUARIO)).thenReturn(true);

        assertThatThrownBy(() -> service.ativarManual(
                anuncioId,
                new AdminPremiumAtivarRequest(UUID.randomUUID(), 7, null),
                "operacao-bloqueada",
                admin(),
                "req-bloqueada"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(grupoRepository, never()).save(any());
        verify(ativacaoRepository, never()).save(any());
        verifyNoInteractions(movimentoRepository);
    }

    @Test
    void retryComMesmaChaveRetornaAtivacaoExistenteSemNovaGravacao() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID beneficioId = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        OffsetDateTime inicio = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        GrupoAtivacaoBeneficioEntity grupo = GrupoAtivacaoBeneficioEntity.criarAdministrativa(
                grupoId,
                usuarioId,
                anuncioId,
                UUID.randomUUID(),
                inicio,
                inicio.plusDays(7),
                "premium-admin:ator:operacao-002",
                "observacao",
                inicio);
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarAdministrativa(
                UUID.randomUUID(),
                beneficioId,
                UUID.randomUUID(),
                usuarioId,
                anuncioId,
                grupoId,
                UUID.randomUUID(),
                inicio,
                inicio.plusDays(7),
                "ativacao-repetida",
                inicio);
        when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(grupo));
        when(ativacaoRepository.findByGrupoAtivacaoId(grupoId)).thenReturn(List.of(ativacao));

        var response = service.ativarManual(
                anuncioId,
                new AdminPremiumAtivarRequest(beneficioId, 7, "observacao"),
                "operacao-002",
                admin(),
                "req-retry");

        assertThat(response.idempotente()).isTrue();
        assertThat(response.beneficioId()).isEqualTo(beneficioId);
        verify(grupoRepository, never()).save(any());
        verify(ativacaoRepository, never()).save(any());
        verifyNoInteractions(movimentoRepository);
    }

    @Test
    void concorrenciaComMesmaChaveEhReconciliadaDepoisDoLockDoAnuncio() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID beneficioId = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        OffsetDateTime inicio = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "anuncio-premium-concorrente",
                "Anuncio Premium concorrente",
                "Descricao valida para reconciliacao concorrente",
                "MASSAGENS",
                null,
                null,
                inicio);
        GrupoAtivacaoBeneficioEntity grupo = GrupoAtivacaoBeneficioEntity.criarAdministrativa(
                grupoId,
                usuarioId,
                anuncioId,
                UUID.randomUUID(),
                inicio,
                inicio.plusDays(7),
                "premium-admin:ator:operacao-concorrente",
                "observacao",
                inicio);
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarAdministrativa(
                UUID.randomUUID(),
                beneficioId,
                UUID.randomUUID(),
                usuarioId,
                anuncioId,
                grupoId,
                UUID.randomUUID(),
                inicio,
                inicio.plusDays(7),
                "ativacao-concorrente",
                inicio);
        when(grupoRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.empty(), Optional.of(grupo));
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(ativacaoRepository.findByGrupoAtivacaoId(grupoId)).thenReturn(List.of(ativacao));

        var response = service.ativarManual(
                anuncioId,
                new AdminPremiumAtivarRequest(beneficioId, 7, "observacao"),
                "operacao-concorrente",
                admin(),
                "req-concorrente");

        assertThat(response.idempotente()).isTrue();
        verify(grupoRepository, never()).save(any());
        verify(ativacaoRepository, never()).save(any());
        verifyNoInteractions(movimentoRepository);
    }

    @Test
    void desativaBeneficioAdministrativoSemMovimentarLedger() {
        UUID ativacaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        OffsetDateTime inicio = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarAdministrativa(
                ativacaoId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                anuncioId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                inicio,
                inicio.plusDays(7),
                "ativacao-admin-cancelar",
                inicio);
        when(ativacaoRepository.findByIdForUpdate(ativacaoId)).thenReturn(Optional.of(ativacao));

        var response = service.cancelar(
                ativacaoId,
                "Cortesia administrativa encerrada",
                "cancelamento-001",
                admin(),
                "req-cancelamento");

        assertThat(response.status()).isEqualTo(StatusAtivacaoBeneficio.REVOGADA.name());
        assertThat(response.creditosEstornados()).isZero();
        assertThat(ativacao.getStatus()).isEqualTo(StatusAtivacaoBeneficio.REVOGADA);
        verify(ativacaoRepository).save(ativacao);
        verifyNoInteractions(movimentoRepository);
        verify(creditoService).auditar(
                any(),
                eq("PREMIUM_ATIVACAO_CANCELAR"),
                eq("ATIVACAO_BENEFICIO"),
                eq(ativacaoId),
                any(),
                any(),
                eq("req-cancelamento"));
    }

    @Test
    void ativaVariosBeneficiosAtomicamenteERetryNaoDuplica() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID primeiroId = UUID.randomUUID();
        UUID segundoId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "anuncio-premium-lote",
                "Anuncio Premium em lote",
                "Descricao valida para ativacao administrativa em lote",
                "MASSAGENS",
                null,
                null,
                agora.minusDays(2));
        BeneficioPremiumEntity primeiro = BeneficioPremiumEntity.criarFixtureHomologacao(
                primeiroId,
                "ANUNCIO_TOPO",
                "Anuncio no topo",
                "Primeiro beneficio",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                true,
                agora.minusDays(1));
        BeneficioPremiumEntity segundo = BeneficioPremiumEntity.criarFixtureHomologacao(
                segundoId,
                "OCULTAR_IDADE",
                "Ocultar idade",
                "Segundo beneficio",
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true,
                agora.minusDays(1));
        BeneficioPremiumOpcaoEntity primeiraOpcao = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(), primeiroId, 7, 20, true, 0, agora.minusDays(1));
        BeneficioPremiumOpcaoEntity segundaOpcao = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(), segundoId, 14, 30, true, 0, agora.minusDays(1));
        Map<String, GrupoAtivacaoBeneficioEntity> grupos = new HashMap<>();
        Map<UUID, List<AtivacaoBeneficioEntity>> ativacoes = new HashMap<>();
        when(grupoRepository.findByIdempotencyKey(any()))
                .thenAnswer(invocation -> Optional.ofNullable(grupos.get(invocation.getArgument(0))));
        when(grupoRepository.save(any())).thenAnswer(invocation -> {
            GrupoAtivacaoBeneficioEntity grupo = invocation.getArgument(0);
            grupos.put(grupo.getIdempotencyKey(), grupo);
            return grupo;
        });
        when(ativacaoRepository.save(any())).thenAnswer(invocation -> {
            AtivacaoBeneficioEntity ativacao = invocation.getArgument(0);
            ativacoes.put(ativacao.getGrupoAtivacaoId(), List.of(ativacao));
            return ativacao;
        });
        when(ativacaoRepository.findByGrupoAtivacaoId(any()))
                .thenAnswer(invocation -> ativacoes.getOrDefault(invocation.getArgument(0), List.of()));
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(beneficioRepository.findById(primeiroId)).thenReturn(Optional.of(primeiro));
        when(beneficioRepository.findById(segundoId)).thenReturn(Optional.of(segundo));
        when(opcaoRepository.findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(primeiroId, 7))
                .thenReturn(Optional.of(primeiraOpcao));
        when(opcaoRepository.findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(segundoId, 14))
                .thenReturn(Optional.of(segundaOpcao));
        when(consultaService.consultarCalculados(anuncioId)).thenReturn(List.of());
        AdminUserPrincipal administrador = admin();
        AdminPremiumAtivarLoteRequest request = new AdminPremiumAtivarLoteRequest(
                List.of(
                        new AdminPremiumAtivarLoteItemRequest(primeiroId, 7),
                        new AdminPremiumAtivarLoteItemRequest(segundoId, 14)),
                "Cortesia administrativa em lote");

        var primeiraExecucao = service.ativarManualLote(
                anuncioId, request, "operacao-lote-001", administrador, "req-lote");
        var retry = service.ativarManualLote(
                anuncioId, request, "operacao-lote-001", administrador, "req-lote-retry");

        assertThat(primeiraExecucao.ativacoes()).hasSize(2).allMatch(item -> !item.idempotente());
        assertThat(primeiraExecucao.idempotente()).isFalse();
        assertThat(retry.ativacoes()).hasSize(2).allMatch(item -> item.idempotente());
        assertThat(retry.idempotente()).isTrue();
        verify(grupoRepository, times(2)).save(any());
        verify(ativacaoRepository, times(2)).save(any());
        verify(creditoService, times(2)).auditar(
                any(), eq("PREMIUM_ATIVACAO_ADMINISTRATIVA"), eq("ATIVACAO_BENEFICIO"),
                any(), any(), any(), eq("req-lote"));
        verifyNoInteractions(movimentoRepository);
    }

    private AnuncioEntity anuncioElegivel(UUID anuncioId) {
        return AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                UUID.randomUUID(),
                "anuncio-story-bloqueio-generico",
                "Anuncio para validar bloqueio generico",
                "Descricao valida para o teste de bloqueio generico",
                "MASSAGENS",
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    }
    private AdminUserPrincipal admin() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin",
                "admin@example.invalid",
                "hash-sintetico",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true);
    }
}
