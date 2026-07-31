package br.com.topsdojob.v3.application.admin.premium;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BeneficioFotosExtrasModeracaoServiceTest {

    private static final OffsetDateTime COMPRA = OffsetDateTime.parse("2026-07-31T13:00:00Z");
    private static final OffsetDateTime APROVACAO = OffsetDateTime.parse("2026-08-02T18:30:00Z");

    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final BeneficioPremiumRepository beneficioRepository = mock(BeneficioPremiumRepository.class);
    private final BeneficioPremiumOpcaoRepository opcaoRepository = mock(BeneficioPremiumOpcaoRepository.class);
    private final AtivacaoBeneficioRepository ativacaoRepository = mock(AtivacaoBeneficioRepository.class);
    private final GrupoAtivacaoBeneficioRepository grupoRepository = mock(GrupoAtivacaoBeneficioRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final BeneficioFotosExtrasModeracaoService service = new BeneficioFotosExtrasModeracaoService(
            midiaRepository,
            beneficioRepository,
            opcaoRepository,
            ativacaoRepository,
            grupoRepository,
            auditoriaRepository,
            new ObjectMapper().findAndRegisterModules());
    private UUID anuncioId;
    private UUID beneficioId;
    private AtivacaoBeneficioEntity ativacao;
    private GrupoAtivacaoBeneficioEntity grupo;

    @BeforeEach
    void setUp() {
        anuncioId = UUID.randomUUID();
        beneficioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        BeneficioPremiumEntity beneficio = entity(BeneficioPremiumEntity.class);
        set(beneficio, "id", beneficioId);
        set(beneficio, "codigo", FOTOS_EXTRA_5);
        BeneficioPremiumOpcaoEntity opcao = BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(), beneficioId, 7, 10, true, 1, COMPRA);
        grupo = GrupoAtivacaoBeneficioEntity.criarCompraComCreditos(
                UUID.randomUUID(),
                usuarioId,
                anuncioId,
                COMPRA,
                COMPRA.plusDays(7),
                "grupo-fotos-extras",
                COMPRA);
        ativacao = AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
                UUID.randomUUID(),
                beneficioId,
                opcao.getId(),
                usuarioId,
                anuncioId,
                grupo.getId(),
                10,
                "ativacao-fotos-extras",
                COMPRA);

        when(beneficioRepository.findByCodigo(FOTOS_EXTRA_5)).thenReturn(Optional.of(beneficio));
        when(opcaoRepository.findById(opcao.getId())).thenReturn(Optional.of(opcao));
        when(grupoRepository.findByIdForUpdate(grupo.getId())).thenReturn(Optional.of(grupo));
        when(ativacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(grupoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void compraMantemDatasNulasAteFotoAlemDoLimiteSerAprovada() {
        assertThat(ativacao.getStatus()).isEqualTo(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
        assertThat(ativacao.getInicioEm()).isNull();
        assertThat(ativacao.getFimEm()).isNull();

        when(midiaRepository.countByAnuncioIdAndTipoAndStatus(any(), any(), any())).thenReturn(4L);

        assertThat(service.iniciarSeCapacidadeAdicionalAprovada(
                anuncioId, UUID.randomUUID(), UUID.randomUUID(), "req-base", APROVACAO)).isFalse();
        assertThat(ativacao.getInicioEm()).isNull();
        assertThat(ativacao.getFimEm()).isNull();
        verify(ativacaoRepository, never()).save(any());
    }

    @Test
    void aprovacaoEmDoisDeAgostoIniciaSeteDiasAteNoveDeAgosto() {
        when(midiaRepository.countByAnuncioIdAndTipoAndStatus(any(), any(), any())).thenReturn(5L);
        when(ativacaoRepository.findAguardandoModeracaoForUpdate(any(), any(), any()))
                .thenReturn(List.of(ativacao));

        boolean iniciou = service.iniciarSeCapacidadeAdicionalAprovada(
                anuncioId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "req-aprovacao",
                APROVACAO);

        assertThat(iniciou).isTrue();
        assertThat(ativacao.getStatus()).isEqualTo(StatusAtivacaoBeneficio.ATIVA);
        assertThat(ativacao.getInicioEm()).isEqualTo(APROVACAO);
        assertThat(ativacao.getFimEm()).isEqualTo(OffsetDateTime.parse("2026-08-09T18:30:00Z"));
        assertThat(ativacao.getFimEm()).isNotEqualTo(OffsetDateTime.parse("2026-08-07T13:00:00Z"));
        assertThat(grupo.getValidadeFimEm()).isEqualTo(ativacao.getFimEm());
        verify(auditoriaRepository).save(any());
    }

    @Test
    void retryNaoReiniciaNemProrrogaDatas() {
        when(midiaRepository.countByAnuncioIdAndTipoAndStatus(any(), any(), any())).thenReturn(5L);
        when(ativacaoRepository.findAguardandoModeracaoForUpdate(any(), any(), any()))
                .thenReturn(List.of(ativacao))
                .thenReturn(List.of());
        UUID midiaId = UUID.randomUUID();
        UUID atorId = UUID.randomUUID();
        service.iniciarSeCapacidadeAdicionalAprovada(
                anuncioId, midiaId, atorId, "req-primeira", APROVACAO);
        OffsetDateTime inicio = ativacao.getInicioEm();
        OffsetDateTime fim = ativacao.getFimEm();

        boolean repetida = service.iniciarSeCapacidadeAdicionalAprovada(
                anuncioId, midiaId, atorId, "req-retry", APROVACAO.plusHours(2));

        assertThat(repetida).isFalse();
        assertThat(ativacao.getInicioEm()).isEqualTo(inicio);
        assertThat(ativacao.getFimEm()).isEqualTo(fim);
    }
}
