package br.com.topsdojob.v3.application.admin.anuncio;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminBloqueioJuridicoRequest;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminDesbloqueioJuridicoRequest;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioBloqueioJuridicoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.CategoriaBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminAnuncioJuridicoServiceTest {

  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final AnuncioBloqueioJuridicoRepository bloqueioRepository =
      mock(AnuncioBloqueioJuridicoRepository.class);
  private final AnuncioStatusHistoricoRepository statusHistoricoRepository =
      mock(AnuncioStatusHistoricoRepository.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final StorySelecaoAdministrativaRepository storyAdminRepository =
      mock(StorySelecaoAdministrativaRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final PublicSessionRegistry sessionRegistry = mock(PublicSessionRegistry.class);
  private final FotoElegivelAnuncioPolicy fotoElegivelAnuncioPolicy = mock(FotoElegivelAnuncioPolicy.class);
  private AdminAnuncioJuridicoService service;

  @BeforeEach
  void preparar() {
    service = new AdminAnuncioJuridicoService(
        anuncioRepository,
        usuarioRepository,
        bloqueioRepository,
        statusHistoricoRepository,
        storyRepository,
        storyAdminRepository,
        auditoriaRepository,
        sessionRegistry,
        fotoElegivelAnuncioPolicy,
        new ObjectMapper());
    when(bloqueioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(statusHistoricoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(storyAdminRepository.bloquearAtivasDosAnuncios(any())).thenReturn(List.of());
  }

  @Test
  void reativaSomentePausadoAprovadoComProprietarioAtivo() {
    Fixture fixture = fixture(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);

    var resultado = service.reativar(fixture.anuncio().getId(), admin(), "req-reativar");

    assertThat(resultado.statusAnuncio()).isEqualTo("PUBLICADO");
    assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
    verify(statusHistoricoRepository).save(any());
    ArgumentCaptor<AuditoriaEventoEntity> auditoria = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(auditoriaRepository).save(auditoria.capture());
    assertThat(auditoria.getValue().getAcao()).isEqualTo("ANUNCIO_REATIVADO_ADMINISTRATIVAMENTE");
    assertThat(auditoria.getValue().getRequestId()).isEqualTo("req-reativar");
    verify(sessionRegistry, never()).invalidateAll(any());
    var ordem = inOrder(anuncioRepository, usuarioRepository, fotoElegivelAnuncioPolicy);
    ordem.verify(anuncioRepository).findUsuarioIdById(fixture.anuncio().getId());
    ordem.verify(usuarioRepository).findByIdForUpdate(fixture.usuario().getId());
    ordem.verify(anuncioRepository).findByUsuarioIdForLegalBlock(fixture.usuario().getId());
    ordem.verify(fotoElegivelAnuncioPolicy).validarParaReativacao(fixture.anuncio().getId());
    ordem.verify(anuncioRepository).save(fixture.anuncio());
    verify(anuncioRepository, never()).findById(any());
    verify(fotoElegivelAnuncioPolicy, never()).validarParaAprovacao(any());
  }

  @Test
  void reativacaoSemFotoAprovadaElegivelNaoProduzEfeitos() {
    Fixture fixture = fixture(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);
    String mensagem = FotoElegivelAnuncioPolicy.MENSAGEM_SEM_FOTO_APROVADA_REATIVACAO;
    doThrow(new ResponseStatusException(HttpStatus.CONFLICT, mensagem))
        .when(fotoElegivelAnuncioPolicy).validarParaReativacao(fixture.anuncio().getId());

    assertThatThrownBy(() -> service.reativar(fixture.anuncio().getId(), admin(), "req-foto-invalida"))
        .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
          assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.getReason()).isEqualTo(mensagem);
        });
    assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PAUSADO);

    verify(anuncioRepository, never()).save(any());
    verify(statusHistoricoRepository, never()).save(any());
    verify(auditoriaRepository, never()).save(any());
    verify(sessionRegistry, never()).invalidateAll(any());
  }

  @Test
  void reativacaoReconfereAnuncioEncerradoAoAdquirirLockSemConsultarFoto() {
    Fixture fixture = fixture(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);
    when(anuncioRepository.findByUsuarioIdForLegalBlock(fixture.usuario().getId())).thenAnswer(invocation -> {
      fixture.anuncio().removerPeloProprietario(OffsetDateTime.now());
      return List.of(fixture.anuncio());
    });

    assertThatThrownBy(() -> service.reativar(fixture.anuncio().getId(), admin(), "req-encerrado"))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.REMOVIDO);
    assertThat(fixture.anuncio().getRemovidoEm()).isNotNull();
    verify(anuncioRepository, never()).findById(any());
    verify(fotoElegivelAnuncioPolicy, never()).validarParaReativacao(any());
    verify(fotoElegivelAnuncioPolicy, never()).validarParaAprovacao(any());
    verify(anuncioRepository, never()).save(any());
    verify(statusHistoricoRepository, never()).save(any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void reativacaoRecusaBloqueadoRemovidoNaoAprovadoEProprietarioSuspenso() {
    List<Fixture> invalidos = List.of(
        fixture(StatusAnuncio.BLOQUEADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO),
        fixture(StatusAnuncio.REMOVIDO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO),
        fixture(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.PENDENTE, StatusUsuario.ATIVO),
        fixture(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.SUSPENSO));

    for (Fixture fixture : invalidos) {
      assertThatThrownBy(() -> service.reativar(fixture.anuncio().getId(), admin(), "req-invalida"))
          .isInstanceOfSatisfying(ResponseStatusException.class, error ->
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    verify(statusHistoricoRepository, never()).save(any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void bloqueiaSomenteAnuncioSemAlterarContaOuExcluirEvidencia() {
    Fixture fixture = fixture(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);

    var resultado = service.bloquearAnuncio(
        fixture.anuncio().getId(),
        bloqueioRequest(),
        admin(),
        "req-bloqueio-anuncio");

    assertThat(resultado.statusAnuncio()).isEqualTo("BLOQUEADO");
    assertThat(resultado.statusUsuario()).isEqualTo("ATIVO");
    assertThat(resultado.anunciosPausados()).isZero();
    assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.BLOQUEADO);
    assertThat(fixture.usuario().getStatus()).isEqualTo(StatusUsuario.ATIVO);
    ArgumentCaptor<AnuncioBloqueioJuridicoEntity> bloqueio =
        ArgumentCaptor.forClass(AnuncioBloqueioJuridicoEntity.class);
    verify(bloqueioRepository).save(bloqueio.capture());
    assertThat(bloqueio.getValue().getEscopo()).isEqualTo(EscopoBloqueioJuridico.ANUNCIO);
    assertThat(bloqueio.getValue().getCategoria()).isEqualTo(CategoriaBloqueioJuridico.FRAUDE);
    assertThat(bloqueio.getValue().getObservacaoInterna()).isEqualTo("nota interna sanitizada");
    verify(sessionRegistry, never()).invalidateAll(any());
  }

  @Test
  void bloqueiaAnuncioEUsuarioPausaDemaisPublicaveisESuspendeStories() {
    Fixture fixture = fixture(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);
    AnuncioEntity outro = anuncio(
        UUID.randomUUID(),
        fixture.usuario().getId(),
        StatusAnuncio.PUBLICADO,
        StatusModeracaoAnuncio.APROVADO);
    AnuncioEntity pendente = anuncio(
        UUID.randomUUID(),
        fixture.usuario().getId(),
        StatusAnuncio.PENDENTE_REVISAO,
        StatusModeracaoAnuncio.PENDENTE);
    when(anuncioRepository.findByUsuarioIdForLegalBlock(fixture.usuario().getId()))
        .thenReturn(List.of(fixture.anuncio(), outro, pendente));
    StoryAnuncioEntity story = StoryAnuncioEntity.criarAnuncio(
        UUID.randomUUID(),
        outro.getId(),
        UUID.randomUUID(),
        "story-direto-bloqueio",
        "a".repeat(64),
        OffsetDateTime.now().minusHours(1),
        OffsetDateTime.now().plusHours(23),
        fixture.usuario().getId());
    when(storyRepository.findByAnuncioIdsForUpdate(any(Set.class))).thenReturn(List.of(story));
    StorySelecaoAdministrativaEntity storyAdmin = entity(StorySelecaoAdministrativaEntity.class);
    OffsetDateTime agora = OffsetDateTime.now();
    set(storyAdmin, "anuncioId", outro.getId());
    set(storyAdmin, "ativa", true);
    set(storyAdmin, "ativadoPor", admin().usuarioId());
    set(storyAdmin, "ativadoEm", agora);
    set(storyAdmin, "expiraEm", agora.plusHours(24));
    when(storyAdminRepository.bloquearAtivasDosAnuncios(any())).thenReturn(List.of(storyAdmin));

    var resultado = service.bloquearAnuncioEUsuario(
        fixture.anuncio().getId(),
        bloqueioRequest(),
        admin(),
        "req-bloqueio-usuario");

    assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.BLOQUEADO);
    assertThat(outro.getStatus()).isEqualTo(StatusAnuncio.PAUSADO);
    assertThat(pendente.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
    assertThat(fixture.usuario().getStatus()).isEqualTo(StatusUsuario.SUSPENSO);
    assertThat(story.getStatus()).isEqualTo(StatusStoryAnuncio.EXPIRADO);
    assertThat(storyAdmin.isAtiva()).isFalse();
    assertThat(resultado.anunciosPausados()).isEqualTo(1);
    assertThat(resultado.storiesSuspensos()).isEqualTo(1);
    assertThat(resultado.storyAdministrativoSuspenso()).isTrue();
    verify(sessionRegistry).invalidateAll(fixture.usuario().getId());
  }

  @Test
  void desbloqueiaAnuncioParaPausadoSemPublicacaoAutomatica() {
    Fixture fixture = fixture(StatusAnuncio.BLOQUEADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);
    AnuncioBloqueioJuridicoEntity bloqueio = bloqueioAtivo(fixture, EscopoBloqueioJuridico.ANUNCIO, null);
    when(bloqueioRepository.findAtivoPorAnuncioForUpdate(fixture.anuncio().getId()))
        .thenReturn(Optional.of(bloqueio));

    var resultado = service.desbloquearAnuncio(
        fixture.anuncio().getId(),
        new AdminDesbloqueioJuridicoRequest("risco encerrado"),
        admin(),
        "req-desbloqueio-anuncio");

    assertThat(resultado.statusAnuncio()).isEqualTo("PAUSADO");
    assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PAUSADO);
    assertThat(bloqueio.anuncioBloqueado()).isFalse();
    verify(storyRepository, never()).saveAll(any());
  }

  @Test
  void desbloqueiaUsuarioSemRepublicarAnunciosOuStories() {
    Fixture fixture = fixture(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.SUSPENSO);
    AnuncioBloqueioJuridicoEntity bloqueio = bloqueioAtivo(
        fixture,
        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO,
        StatusUsuario.ATIVO);
    bloqueio.desbloquearAnuncio(admin().usuarioId(), "req-anuncio-ja-desbloqueado", OffsetDateTime.now());
    when(bloqueioRepository.findAtivoPorUsuarioForUpdate(
        fixture.usuario().getId(), EscopoBloqueioJuridico.ANUNCIO_E_USUARIO))
        .thenReturn(Optional.of(bloqueio));

    var resultado = service.desbloquearUsuario(
        fixture.anuncio().getId(),
        new AdminDesbloqueioJuridicoRequest(null),
        admin(),
        "req-desbloqueio-usuario");

    assertThat(resultado.statusUsuario()).isEqualTo("ATIVO");
    assertThat(fixture.usuario().getStatus()).isEqualTo(StatusUsuario.ATIVO);
    assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PAUSADO);
    assertThat(bloqueio.usuarioBloqueado()).isFalse();
    verify(storyRepository, never()).saveAll(any());
  }

  @Test
  void desbloqueioPreservaAnunciosPendentesERejeitados() {
    assertDesbloqueioPreservaStatus(StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE);
    assertDesbloqueioPreservaStatus(StatusAnuncio.REJEITADO, StatusModeracaoAnuncio.REJEITADO);
  }

  @Test
  void bloqueioRepetidoRetorna409EAlvoInexistenteRetorna404() {
    Fixture fixture = fixture(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);
    when(bloqueioRepository.findAtivoPorAnuncioForUpdate(fixture.anuncio().getId()))
        .thenReturn(Optional.of(bloqueioAtivo(fixture, EscopoBloqueioJuridico.ANUNCIO, null)));

    assertThatThrownBy(() -> service.bloquearAnuncio(
        fixture.anuncio().getId(), bloqueioRequest(), admin(), "req-repetido"))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    UUID inexistente = UUID.randomUUID();
    when(anuncioRepository.findUsuarioIdById(inexistente)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.reativar(inexistente, admin(), "req-404"))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void usuarioComumNaoExecutaIntervencaoJuridica() {
    Fixture fixture = fixture(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, StatusUsuario.ATIVO);
    AdminUserPrincipal usuario = new AdminUserPrincipal(
        UUID.randomUUID(),
        "Usuario",
        "usuario@example.invalid",
        "hash",
        List.of(PapelUsuario.USUARIO),
        List.of(),
        List.of(new SimpleGrantedAuthority("ROLE_USUARIO")),
        true);

    assertThatThrownBy(() -> service.bloquearAnuncio(
        fixture.anuncio().getId(), bloqueioRequest(), usuario, "req-403"))
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(anuncioRepository, never()).saveAll(any());
  }

  private Fixture fixture(
      StatusAnuncio status,
      StatusModeracaoAnuncio moderacao,
      StatusUsuario statusUsuario) {
    UUID usuarioId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
        usuarioId,
        "Usuario sintetico",
        usuarioId + "@example.invalid",
        "+5562999999999",
        LocalDate.of(1990, 1, 1),
        OffsetDateTime.now().minusYears(1));
    set(usuario, "status", statusUsuario);
    AnuncioEntity anuncio = anuncio(anuncioId, usuarioId, status, moderacao);
    when(anuncioRepository.findUsuarioIdById(anuncioId)).thenReturn(Optional.of(usuarioId));
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(anuncioRepository.findByUsuarioIdForLegalBlock(usuarioId)).thenReturn(List.of(anuncio));
    when(bloqueioRepository.findAtivoPorAnuncioForUpdate(anuncioId)).thenReturn(Optional.empty());
    when(bloqueioRepository.findAtivoPorUsuarioForUpdate(
        usuarioId, EscopoBloqueioJuridico.ANUNCIO_E_USUARIO)).thenReturn(Optional.empty());
    return new Fixture(anuncio, usuario);
  }

  private void assertDesbloqueioPreservaStatus(
      StatusAnuncio status,
      StatusModeracaoAnuncio moderacao) {
    Fixture fixture = fixture(status, moderacao, StatusUsuario.SUSPENSO);
    AnuncioBloqueioJuridicoEntity bloqueio = bloqueioAtivo(
        fixture,
        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO,
        StatusUsuario.ATIVO);
    bloqueio.desbloquearAnuncio(
        admin().usuarioId(),
        "req-anuncio-ja-desbloqueado-" + status,
        OffsetDateTime.now());
    when(bloqueioRepository.findAtivoPorUsuarioForUpdate(
        fixture.usuario().getId(), EscopoBloqueioJuridico.ANUNCIO_E_USUARIO))
        .thenReturn(Optional.of(bloqueio));

    service.desbloquearUsuario(
        fixture.anuncio().getId(),
        new AdminDesbloqueioJuridicoRequest(null),
        admin(),
        "req-preserva-" + status);

    assertThat(fixture.usuario().getStatus()).isEqualTo(StatusUsuario.ATIVO);
    assertThat(fixture.anuncio().getStatus()).isEqualTo(status);
  }

  private AnuncioEntity anuncio(
      UUID anuncioId,
      UUID usuarioId,
      StatusAnuncio status,
      StatusModeracaoAnuncio moderacao) {
    AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
        anuncioId,
        usuarioId,
        "anuncio-" + anuncioId,
        "Anuncio sintetico juridico",
        "Descricao sintetica suficientemente longa para o teste juridico",
        "MASSAGENS",
        BigDecimal.TEN,
        "+5562999999999",
        OffsetDateTime.now().minusDays(1));
    set(anuncio, "status", status);
    set(anuncio, "statusModeracao", moderacao);
    if (status == StatusAnuncio.REMOVIDO) {
      set(anuncio, "removidoEm", OffsetDateTime.now().minusHours(1));
    }
    return anuncio;
  }

  private AnuncioBloqueioJuridicoEntity bloqueioAtivo(
      Fixture fixture,
      EscopoBloqueioJuridico escopo,
      StatusUsuario statusAnterior) {
    return AnuncioBloqueioJuridicoEntity.registrar(
        UUID.randomUUID(),
        fixture.anuncio().getId(),
        fixture.usuario().getId(),
        escopo,
        CategoriaBloqueioJuridico.FRAUDE,
        statusAnterior,
        "motivo juridico valido",
        null,
        admin().usuarioId(),
        "req-bloqueio-original",
        OffsetDateTime.now().minusHours(1));
  }

  private AdminBloqueioJuridicoRequest bloqueioRequest() {
    return new AdminBloqueioJuridicoRequest(
        CategoriaBloqueioJuridico.FRAUDE,
        "evidencia juridica confirmada",
        "nota interna sanitizada");
  }

  private AdminUserPrincipal admin() {
    return new AdminUserPrincipal(
        UUID.randomUUID(),
        "Admin juridico",
        "admin@example.invalid",
        "hash",
        List.of(PapelUsuario.ADMIN),
        List.of(),
        List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ANUNCIO_MODERAR")),
        true);
  }

  private record Fixture(AnuncioEntity anuncio, UsuarioEntity usuario) {
  }
}
