package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.credito.CreditoLancamentoResultado;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryAtivacaoRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class MeuAnuncioStoryAtivacaoServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-03T12:00:00Z");

  private MeusAnunciosConsultaService anuncios;
  private CreditoLedgerOperacaoService ledger;
  private MovimentoCreditoRepository movimentos;
  private StoryConfiguracaoComercialRepository configuracoes;
  private BeneficioPremiumRepository beneficios;
  private GrupoAtivacaoBeneficioRepository grupos;
  private AtivacaoBeneficioRepository ativacoes;
  private StoryAnuncioRepository stories;
  private AuditoriaEventoRepository auditorias;
  private MeuAnuncioStoryAtivacaoService service;
  private Authentication authentication;
  private UUID usuarioId;
  private UUID anuncioId;
  private UUID beneficioId;
  private AnuncioEntity anuncio;
  private BeneficioPremiumEntity beneficio;

  @BeforeEach
  void setUp() {
    anuncios = mock(MeusAnunciosConsultaService.class);
    ledger = mock(CreditoLedgerOperacaoService.class);
    movimentos = mock(MovimentoCreditoRepository.class);
    configuracoes = mock(StoryConfiguracaoComercialRepository.class);
    beneficios = mock(BeneficioPremiumRepository.class);
    grupos = mock(GrupoAtivacaoBeneficioRepository.class);
    ativacoes = mock(AtivacaoBeneficioRepository.class);
    stories = mock(StoryAnuncioRepository.class);
    auditorias = mock(AuditoriaEventoRepository.class);
    authentication = mock(Authentication.class);
    usuarioId = UUID.randomUUID();
    anuncioId = UUID.randomUUID();
    beneficioId = UUID.randomUUID();
    UsuarioEntity usuario = mock(UsuarioEntity.class);
    anuncio = mock(AnuncioEntity.class);
    beneficio = mock(BeneficioPremiumEntity.class);
    when(usuario.getId()).thenReturn(usuarioId);
    when(anuncio.getId()).thenReturn(anuncioId);
    when(anuncio.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
    when(anuncio.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.APROVADO);
    when(beneficio.getId()).thenReturn(beneficioId);
    when(beneficio.getCodigo()).thenReturn("STORIES");
    when(anuncios.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(anuncios.anuncioDoUsuario("qa-story", authentication)).thenReturn(anuncio);
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(beneficio));
    when(grupos.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(grupos.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(ativacoes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(ativacoes.findByAnuncioId(anuncioId)).thenReturn(List.of());
    when(stories.findByAnuncioIdForUpdate(anuncioId)).thenReturn(List.of());
    service = new MeuAnuncioStoryAtivacaoService(
        anuncios, ledger, movimentos, configuracoes, beneficios,
        grupos, ativacoes, stories, auditorias);
  }

  @Test
  void compraComCreditosCriaDireitoSemIniciarVigenciaDoStory() {
    when(ledger.bloquearEConsultarSaldo(usuarioId)).thenReturn(12);
    when(configuracoes.findForUpdate()).thenReturn(Optional.of(configuracao(true, 5)));
    MovimentoCreditoEntity movimento = movimento(5, 12, 7, UUID.randomUUID());
    when(ledger.registrar(
        any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new CreditoLancamentoResultado(movimento, false));

    var resultado = service.ativar(
        "qa-story", new MeuAnuncioStoryAtivacaoRequest(5, 0L),
        "story-compra-1", authentication, "req-story-1");

    assertThat(resultado.custoCreditos()).isEqualTo(5);
    assertThat(resultado.saldoAnterior()).isEqualTo(12);
    assertThat(resultado.saldoAtual()).isEqualTo(7);
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    assertThat(resultado.idempotente()).isFalse();
    verify(ativacoes).save(any());
    verify(auditorias).save(any());
  }

  @Test
  void custoZeroExplicitoCriaDireitoSemDebitoFicticio() {
    when(ledger.bloquearEConsultarSaldo(usuarioId)).thenReturn(3);
    when(configuracoes.findForUpdate()).thenReturn(Optional.of(configuracao(true, 0)));

    var resultado = service.ativar(
        "qa-story", new MeuAnuncioStoryAtivacaoRequest(0, 0L),
        "story-gratis-1", authentication, "req-story-zero");

    assertThat(resultado.saldoAnterior()).isEqualTo(3);
    assertThat(resultado.saldoAtual()).isEqualTo(3);
    assertThat(resultado.custoCreditos()).isZero();
    verify(ledger, never()).registrar(
        any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void configuracaoAusenteOuInativaRecusaNovaCompra() {
    when(ledger.bloquearEConsultarSaldo(usuarioId)).thenReturn(10);
    when(configuracoes.findForUpdate()).thenReturn(Optional.empty());
    assertStatus(() -> service.ativar(
        "qa-story", new MeuAnuncioStoryAtivacaoRequest(5, 0L),
        "story-indisponivel-1", authentication, "req-indisponivel"), HttpStatus.CONFLICT);

    when(configuracoes.findForUpdate()).thenReturn(Optional.of(configuracao(false, 5)));
    assertStatus(() -> service.ativar(
        "qa-story", new MeuAnuncioStoryAtivacaoRequest(5, 0L),
        "story-indisponivel-2", authentication, "req-inativo"), HttpStatus.CONFLICT);

    verify(grupos, never()).save(any());
  }

  @Test
  void snapshotDivergenteDaOfertaRecusaSemDebitar() {
    when(ledger.bloquearEConsultarSaldo(usuarioId)).thenReturn(10);
    when(configuracoes.findForUpdate()).thenReturn(Optional.of(configuracao(true, 6)));

    assertStatus(() -> service.ativar(
        "qa-story", new MeuAnuncioStoryAtivacaoRequest(5, 0L),
        "story-oferta-antiga", authentication, "req-antiga"), HttpStatus.CONFLICT);

    verify(grupos, never()).save(any());
    verify(ledger, never()).registrar(
        any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void saldoInsuficienteRecusaSemCriarDireito() {
    when(ledger.bloquearEConsultarSaldo(usuarioId)).thenReturn(2);
    when(configuracoes.findForUpdate()).thenReturn(Optional.of(configuracao(true, 5)));

    assertStatus(() -> service.ativar(
        "qa-story", new MeuAnuncioStoryAtivacaoRequest(5, 0L),
        "story-sem-saldo", authentication, "req-sem-saldo"), HttpStatus.CONFLICT);

    verify(grupos, never()).save(any());
    verify(ativacoes, never()).save(any());
  }

  private StoryConfiguracaoComercialEntity configuracao(boolean ativa, int custo) {
    return StoryConfiguracaoComercialEntity.criar(ativa, custo, usuarioId, AGORA);
  }

  private MovimentoCreditoEntity movimento(int quantidade, int antes, int depois, UUID referenciaId) {
    return MovimentoCreditoEntity.registrar(
        UUID.randomUUID(), usuarioId, TipoMovimentoCredito.SAIDA,
        DirecaoMovimentoCredito.DEBITO, quantidade, antes, depois,
        OrigemMovimentoCredito.BENEFICIO, "ATIVACAO_BENEFICIO", referenciaId,
        "story-debito", usuarioId, "Story", "req-story", AGORA);
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }
}
