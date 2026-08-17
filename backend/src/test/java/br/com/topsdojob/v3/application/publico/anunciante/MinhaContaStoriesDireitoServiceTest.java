package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.stories.AdminStoryConfiguracaoService;
import br.com.topsdojob.v3.application.credito.CreditoLancamentoResultado;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryAtivacaoRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class MinhaContaStoriesDireitoServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-04T12:00:00Z");
  private static final UUID USUARIO_ID = UUID.fromString("51000000-0000-4000-8000-000000000001");
  private static final UUID ANUNCIO_ID = UUID.fromString("51000000-0000-4000-8000-000000000002");
  private static final UUID BENEFICIO_ID = UUID.fromString("51000000-0000-4000-8000-000000000003");
  private static final UUID ADMIN_ID = UUID.fromString("51000000-0000-4000-8000-000000000004");

  private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
  private final MeuAnuncioStoryConsultaService consultaService = mock(MeuAnuncioStoryConsultaService.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final CreditoLedgerOperacaoService ledgerService = mock(CreditoLedgerOperacaoService.class);
  private final MovimentoCreditoRepository movimentoRepository = mock(MovimentoCreditoRepository.class);
  private final StoryConfiguracaoComercialRepository configuracaoRepository =
      mock(StoryConfiguracaoComercialRepository.class);
  private final BeneficioPremiumRepository beneficioRepository = mock(BeneficioPremiumRepository.class);
  private final AdminStoryConfiguracaoService storyConfiguracaoService =
      mock(AdminStoryConfiguracaoService.class);
  private final GrupoAtivacaoBeneficioRepository grupoRepository =
      mock(GrupoAtivacaoBeneficioRepository.class);
  private final AtivacaoBeneficioRepository ativacaoRepository =
      mock(AtivacaoBeneficioRepository.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final Authentication authentication = mock(Authentication.class);
  private final AnuncioEntity anuncio = mock(AnuncioEntity.class);
  private MinhaContaStoriesDireitoService service;

  @BeforeEach
  void setUp() {
    UsuarioEntity usuario = mock(UsuarioEntity.class);
    BeneficioPremiumEntity beneficio = mock(BeneficioPremiumEntity.class);
    when(usuario.getId()).thenReturn(USUARIO_ID);
    when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(anuncio.getId()).thenReturn(ANUNCIO_ID);
    when(anuncio.getUsuarioId()).thenReturn(USUARIO_ID);
    when(anuncio.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
    when(anuncio.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.APROVADO);
    when(anuncio.getRemovidoEm()).thenReturn(null);
    when(anuncioRepository.findById(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
    when(anuncioRepository.findByIdForModeration(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
    when(beneficio.getId()).thenReturn(BENEFICIO_ID);
    when(beneficio.getCodigo()).thenReturn(STORIES);
    when(beneficioRepository.findByCodigo(STORIES)).thenReturn(Optional.of(beneficio));
    when(storyConfiguracaoService.garantirIdentidadeTecnica(any())).thenReturn(beneficio);
    when(consultaService.consultarAtivos(any())).thenReturn(Map.of());
    when(grupoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(grupoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(ativacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(ativacaoRepository.findByAnuncioId(ANUNCIO_ID)).thenReturn(List.of());
    when(ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(USUARIO_ID)).thenReturn(List.of());
    when(storyRepository.findByAnuncioIdForUpdate(ANUNCIO_ID)).thenReturn(List.of());

    service = new MinhaContaStoriesDireitoService(
        usuarioService,
        consultaService,
        anuncioRepository,
        ledgerService,
        movimentoRepository,
        configuracaoRepository,
        beneficioRepository,
        storyConfiguracaoService,
        grupoRepository,
        ativacaoRepository,
        storyRepository,
        auditoriaRepository);
  }

  @Test
  void compraMidiaUploadCriaDireitoDaContaSemAnuncioESemIniciarVigencia() {
    when(ledgerService.bloquearEConsultarSaldo(USUARIO_ID)).thenReturn(12);
    when(configuracaoRepository.findForUpdate()).thenReturn(Optional.of(configuracao(true, 5)));
    MovimentoCreditoEntity movimento = movimento(5, 12, 7, UUID.randomUUID());
    when(ledgerService.registrar(
        any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new CreditoLancamentoResultado(movimento, false));

    var resultado = service.ativar(
        new MinhaContaStoryAtivacaoRequest("MIDIA_UPLOAD", null, 5, 0L),
        "compra-midia-1",
        authentication,
        "req-midia-1");

    assertThat(resultado.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
    assertThat(resultado.anuncioId()).isNull();
    assertThat(resultado.custoCreditos()).isEqualTo(5);
    assertThat(resultado.saldoAnterior()).isEqualTo(12);
    assertThat(resultado.saldoAtual()).isEqualTo(7);
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    assertThat(resultado.idempotente()).isFalse();
    verify(ativacaoRepository).save(any());
    verify(auditoriaRepository).save(any());
  }

  @Test
  void custoZeroCriaDireitoSemDebitoFicticio() {
    when(ledgerService.bloquearEConsultarSaldo(USUARIO_ID)).thenReturn(3);
    when(configuracaoRepository.findForUpdate()).thenReturn(Optional.of(configuracao(true, 0)));

    var resultado = service.ativar(
        new MinhaContaStoryAtivacaoRequest("MIDIA_UPLOAD", null, 0, 0L),
        "compra-gratis-1",
        authentication,
        "req-gratis-1");

    assertThat(resultado.saldoAnterior()).isEqualTo(3);
    assertThat(resultado.saldoAtual()).isEqualTo(3);
    assertThat(resultado.custoCreditos()).isZero();
    verify(ledgerService, never()).registrar(
        any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void ofertaDeMidiaUploadUsaDireitoDaContaMesmoSemAnuncio() {
    AtivacaoBeneficioEntity direito = direito(null, USUARIO_ID);
    when(ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(USUARIO_ID))
        .thenReturn(List.of(direito));

    var oferta = service.consultar("MIDIA_UPLOAD", null, authentication);

    assertThat(oferta.estado()).isEqualTo("DIREITO_DISPONIVEL");
    assertThat(oferta.anuncioId()).isNull();
    assertThat(oferta.direitoDisponivel().ativacaoId()).isEqualTo(direito.getId());
    verify(anuncioRepository, never()).findById(any());
  }

  @Test
  void reservaDireitoPorEscopoERecusaDireitoDeOutraConta() {
    AtivacaoBeneficioEntity direitoConta = direito(null, USUARIO_ID);
    GrupoAtivacaoBeneficioEntity grupoConta = grupo(null, USUARIO_ID);
    UUID grupoContaId = grupoConta.getId();
    when(direitoConta.getGrupoAtivacaoId()).thenReturn(grupoContaId);
    when(ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(USUARIO_ID))
        .thenReturn(List.of(direitoConta));
    when(grupoRepository.findByIdForUpdate(grupoConta.getId())).thenReturn(Optional.of(grupoConta));

    var reservado = service.reservarParaPublicacao(
        ModoConteudoStory.MIDIA_UPLOAD, null, USUARIO_ID, AGORA);

    assertThat(reservado.ativacao()).isSameAs(direitoConta);
    assertThat(reservado.grupo()).isSameAs(grupoConta);

    AtivacaoBeneficioEntity alheio = direito(null, UUID.randomUUID());
    when(ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(USUARIO_ID))
        .thenReturn(List.of(alheio));
    assertStatus(
        () -> service.reservarParaPublicacao(
            ModoConteudoStory.MIDIA_UPLOAD, null, USUARIO_ID, AGORA),
        HttpStatus.CONFLICT);
  }

  @Test
  void direitoHistoricoPreservadoPodeSerReutilizadoSemRomperVinculoDoAnuncio() {
    AtivacaoBeneficioEntity direitoHistorico = direito(ANUNCIO_ID, USUARIO_ID);
    GrupoAtivacaoBeneficioEntity grupoHistorico = grupo(ANUNCIO_ID, USUARIO_ID);
    UUID grupoId = grupoHistorico.getId();
    when(direitoHistorico.getGrupoAtivacaoId()).thenReturn(grupoId);
    when(ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(USUARIO_ID))
        .thenReturn(List.of(direitoHistorico));
    when(storyRepository
        .existsByAtivacaoBeneficioIdAndDireitoPreservadoTrueAndEncerradoEmIsNotNull(
            direitoHistorico.getId()))
        .thenReturn(true);
    when(grupoRepository.findByIdForUpdate(grupoId)).thenReturn(Optional.of(grupoHistorico));

    var reservado = service.reservarParaPublicacao(
        ModoConteudoStory.MIDIA_UPLOAD, null, USUARIO_ID, AGORA);

    assertThat(reservado.ativacao()).isSameAs(direitoHistorico);
    assertThat(reservado.grupo()).isSameAs(grupoHistorico);
    assertThat(reservado.ativacao().getAnuncioId()).isEqualTo(ANUNCIO_ID);
    assertThat(reservado.grupo().getAnuncioId()).isEqualTo(ANUNCIO_ID);
  }
  @Test
  void direitoDeAnuncioNaoPodeSerConsumidoPorOutroEscopo() {
    AtivacaoBeneficioEntity direitoAnuncio = direito(ANUNCIO_ID, USUARIO_ID);
    GrupoAtivacaoBeneficioEntity grupoAnuncio = grupo(ANUNCIO_ID, USUARIO_ID);
    UUID grupoAnuncioId = grupoAnuncio.getId();
    when(direitoAnuncio.getGrupoAtivacaoId()).thenReturn(grupoAnuncioId);
    when(ativacaoRepository.findByAnuncioId(ANUNCIO_ID)).thenReturn(List.of(direitoAnuncio));
    when(grupoRepository.findByIdForUpdate(grupoAnuncio.getId()))
        .thenReturn(Optional.of(grupoAnuncio));

    var reservado = service.reservarParaPublicacao(
        ModoConteudoStory.ANUNCIO, anuncio, USUARIO_ID, AGORA);

    assertThat(reservado.ativacao()).isSameAs(direitoAnuncio);
    verify(ativacaoRepository).findByAnuncioId(ANUNCIO_ID);
    verify(ativacaoRepository, never()).findByUsuarioIdOrderByCriadoEmDesc(USUARIO_ID);
  }

  @Test
  void direitoAdministrativoUsaOrigemAdminSemLedgerEPermaneceIdempotente() {
    String chave = "admin-story-direito-01";

    var primeiro = service.criarDireitoAdministrativoParaPublicacao(
        anuncio, ADMIN_ID, chave, AGORA);

    assertThat(primeiro.grupo().getOrigem()).isEqualTo(OrigemBeneficio.ADMIN);
    assertThat(primeiro.grupo().getUsuarioId()).isEqualTo(USUARIO_ID);
    assertThat(primeiro.grupo().getAnuncioId()).isEqualTo(ANUNCIO_ID);
    assertThat(primeiro.grupo().getAtorUsuarioId()).isEqualTo(ADMIN_ID);
    assertThat(primeiro.ativacao().getOrigem()).isEqualTo(OrigemBeneficio.ADMIN);
    assertThat(primeiro.ativacao().getCustoCreditosSnapshot()).isZero();
    assertThat(primeiro.ativacao().getStatus())
        .isEqualTo(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);

    when(grupoRepository.findByIdempotencyKey("story-admin-direito:" + chave))
        .thenReturn(Optional.of(primeiro.grupo()));
    when(ativacaoRepository.findByGrupoAtivacaoId(primeiro.grupo().getId()))
        .thenReturn(List.of(primeiro.ativacao()));

    var repetido = service.criarDireitoAdministrativoParaPublicacao(
        anuncio, ADMIN_ID, chave, AGORA.plusMinutes(1));

    assertThat(repetido.grupo()).isSameAs(primeiro.grupo());
    assertThat(repetido.ativacao()).isSameAs(primeiro.ativacao());
    verify(grupoRepository, times(1)).save(any());
    verify(ativacaoRepository, times(1)).save(any());
    verify(storyConfiguracaoService, times(2)).garantirIdentidadeTecnica(any());
    verify(ledgerService, never()).bloquearEConsultarSaldo(any());
    verify(ledgerService, never()).registrar(
        any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any());
    verify(movimentoRepository, never()).save(any());
  }

  @Test
  void vigenciaComecaNaPublicacaoETerminaExatamenteEm24Horas() {
    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    GrupoAtivacaoBeneficioEntity grupo = mock(GrupoAtivacaoBeneficioEntity.class);
    var direito = new MinhaContaStoriesDireitoService.DireitoPublicacao(ativacao, grupo);

    OffsetDateTime fim = service.iniciarVigencia(direito, AGORA);

    assertThat(fim).isEqualTo(AGORA.plusHours(24));
    verify(ativacao).iniciarVigenciaExclusiva(AGORA, AGORA.plusHours(24));
    verify(grupo).estenderValidadeAte(AGORA.plusHours(24), AGORA);
    verify(ativacaoRepository).save(ativacao);
    verify(grupoRepository).save(grupo);
  }

  @Test
  void ofertaAtualizadaESaldoInsuficienteNaoCriamDireito() {
    when(ledgerService.bloquearEConsultarSaldo(USUARIO_ID)).thenReturn(4);
    when(configuracaoRepository.findForUpdate()).thenReturn(Optional.of(configuracao(true, 6)));

    assertStatus(
        () -> service.ativar(
            new MinhaContaStoryAtivacaoRequest("MIDIA_UPLOAD", null, 5, 0L),
            "oferta-antiga",
            authentication,
            "req-antiga"),
        HttpStatus.CONFLICT);
    assertStatus(
        () -> service.ativar(
            new MinhaContaStoryAtivacaoRequest("MIDIA_UPLOAD", null, 6, 0L),
            "sem-saldo",
            authentication,
            "req-sem-saldo"),
        HttpStatus.CONFLICT);

    verify(grupoRepository, never()).save(any());
    verify(ativacaoRepository, never()).save(any());
  }

  private StoryConfiguracaoComercialEntity configuracao(boolean ativa, int custo) {
    return StoryConfiguracaoComercialEntity.criar(ativa, custo, USUARIO_ID, AGORA);
  }

  private AtivacaoBeneficioEntity direito(UUID anuncioId, UUID usuarioId) {
    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    when(ativacao.getId()).thenReturn(UUID.randomUUID());
    when(ativacao.getBeneficioId()).thenReturn(BENEFICIO_ID);
    when(ativacao.getUsuarioId()).thenReturn(usuarioId);
    when(ativacao.getAnuncioId()).thenReturn(anuncioId);
    when(ativacao.getGrupoAtivacaoId()).thenReturn(UUID.randomUUID());
    when(ativacao.getOrigem()).thenReturn(OrigemBeneficio.CREDITO);
    when(ativacao.getStatus()).thenReturn(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    when(ativacao.getCustoCreditosSnapshot()).thenReturn(5);
    when(ativacao.getCriadoEm()).thenReturn(AGORA.minusMinutes(1));
    when(ativacao.getRevogadaEm()).thenReturn(null);
    return ativacao;
  }

  private GrupoAtivacaoBeneficioEntity grupo(UUID anuncioId, UUID usuarioId) {
    GrupoAtivacaoBeneficioEntity grupo = mock(GrupoAtivacaoBeneficioEntity.class);
    when(grupo.getId()).thenReturn(UUID.randomUUID());
    when(grupo.getUsuarioId()).thenReturn(usuarioId);
    when(grupo.getAnuncioId()).thenReturn(anuncioId);
    when(grupo.getOrigem()).thenReturn(OrigemBeneficio.CREDITO);
    return grupo;
  }

  private MovimentoCreditoEntity movimento(int quantidade, int antes, int depois, UUID referenciaId) {
    return MovimentoCreditoEntity.registrar(
        UUID.randomUUID(),
        USUARIO_ID,
        TipoMovimentoCredito.SAIDA,
        DirecaoMovimentoCredito.DEBITO,
        quantidade,
        antes,
        depois,
        OrigemMovimentoCredito.BENEFICIO,
        "ATIVACAO_BENEFICIO",
        referenciaId,
        "story-debito",
        USUARIO_ID,
        "Story",
        "req-story",
        AGORA);
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }
}
