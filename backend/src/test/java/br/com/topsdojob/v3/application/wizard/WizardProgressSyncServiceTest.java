package br.com.topsdojob.v3.application.wizard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.wizard.WizardProgressJdbcRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class WizardProgressSyncServiceTest {

  private final MeusAnunciosConsultaService meusAnunciosService =
      mock(MeusAnunciosConsultaService.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final WizardProgressJdbcRepository repository =
      mock(WizardProgressJdbcRepository.class);
  private final Authentication authentication = mock(Authentication.class);
  private final UUID usuarioId = UUID.randomUUID();
  private final UsuarioEntity usuario = mock(UsuarioEntity.class);
  private final Clock clock = Clock.fixed(
      Instant.parse("2026-07-29T14:00:00Z"),
      ZoneOffset.UTC);
  private WizardProgressSyncService service;

  @BeforeEach
  void setup() {
    service = new WizardProgressSyncService(
        meusAnunciosService,
        anuncioRepository,
        repository,
        clock);
    when(meusAnunciosService.usuarioAutenticadoParaAtualizacao(authentication)).thenReturn(usuario);
    when(meusAnunciosService.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(usuario.getId()).thenReturn(usuarioId);
  }

  @Test
  void recuperaIdentidadeExataEEstadoAtualSemLockDeEscrita() {
    AnuncioEntity anuncio = anuncioDaSessao("CREATE", StatusAnuncio.PENDENTE_REVISAO);

    var response = service.recuperarAnuncio("wizard-create-recovery", authentication);

    assertThat(response.anuncioId()).isEqualTo(anuncio.getId());
    assertThat(response.slug()).isEqualTo(anuncio.getSlug());
    assertThat(response.status()).isEqualTo("PENDENTE_REVISAO");
    assertThat(response.statusModeracao()).isEqualTo("PENDENTE");
    verify(meusAnunciosService).usuarioAutenticado(authentication);
    verify(meusAnunciosService, never()).usuarioAutenticadoParaAtualizacao(any());
    verify(anuncioRepository, never()).findByIdForModeration(any());
    verify(repository).findSessaoPorUsuario(usuarioId, "wizard-create-recovery");
  }

  @Test
  void recuperacaoAnonimaNaoConsultaNenhumaSessao() {
    doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica obrigatoria"))
        .when(meusAnunciosService).usuarioAutenticado(null);
    assertThatThrownBy(() -> service.recuperarAnuncio("wizard-create-recovery", null))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401 UNAUTHORIZED");
    verifyNoInteractions(repository, anuncioRepository);
  }

  @Test
  void sessaoAusenteNoEscopoDoUsuarioRetorna404SemBuscarAnuncio() {
    assertThatThrownBy(() -> service.recuperarAnuncio("wizard-create-recovery", authentication))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404 NOT_FOUND");
    verify(repository).findSessaoPorUsuario(usuarioId, "wizard-create-recovery");
    verifyNoInteractions(anuncioRepository);
  }

  @Test
  void sessaoCreateAindaSemVinculoRetorna404() {
    when(repository.findSessaoPorUsuario(usuarioId, "wizard-create-recovery"))
        .thenReturn(Optional.of(new WizardProgressJdbcRepository.SessionRow("CREATE", null)));
    assertThatThrownBy(() -> service.recuperarAnuncio("wizard-create-recovery", authentication))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404 NOT_FOUND");
    verifyNoInteractions(anuncioRepository);
  }

  @Test
  void sessaoEditSemVinculoNaoSePassaPorCreateNaoConfirmado() {
    when(repository.findSessaoPorUsuario(usuarioId, "wizard-create-recovery"))
        .thenReturn(Optional.of(new WizardProgressJdbcRepository.SessionRow("EDIT", null)));
    assertThatThrownBy(() -> service.recuperarAnuncio("wizard-create-recovery", authentication))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
    verifyNoInteractions(anuncioRepository);
  }

  @Test
  void recuperacaoNaoExpoeAnuncioDeOutroProprietarioMesmoComVinculoInconsistente() {
    AnuncioEntity anuncio = anuncioDaSessao("CREATE", StatusAnuncio.PENDENTE_REVISAO);
    when(anuncio.getUsuarioId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> service.recuperarAnuncio("wizard-create-recovery", authentication))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
  }

  @ParameterizedTest
  @ValueSource(strings = {"REMOVIDO", "BLOQUEADO"})
  void recuperacaoDeVinculoEncerradoOuBloqueadoRetornaConflito(String status) {
    anuncioDaSessao("CREATE", StatusAnuncio.valueOf(status));
    assertThatThrownBy(() -> service.recuperarAnuncio("wizard-create-recovery", authentication))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
  }

  @Test
  void vinculoComAnuncioAusenteEhConflitoNao404DeCriacaoNaoConfirmada() {
    when(repository.findSessaoPorUsuario(usuarioId, "wizard-create-recovery"))
        .thenReturn(Optional.of(new WizardProgressJdbcRepository.SessionRow("CREATE", UUID.randomUUID())));
    assertThatThrownBy(() -> service.recuperarAnuncio("wizard-create-recovery", authentication))
        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
  }

  private AnuncioEntity anuncioDaSessao(String mode, StatusAnuncio status) {
    UUID anuncioId = UUID.randomUUID();
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    when(anuncio.getId()).thenReturn(anuncioId);
    when(anuncio.getUsuarioId()).thenReturn(usuarioId);
    when(anuncio.getSlug()).thenReturn("anuncio-sintetico-recuperado");
    when(anuncio.getStatus()).thenReturn(status);
    when(anuncio.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.PENDENTE);
    when(repository.findSessaoPorUsuario(usuarioId, "wizard-create-recovery"))
        .thenReturn(Optional.of(new WizardProgressJdbcRepository.SessionRow(mode, anuncioId)));
    when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
    return anuncio;
  }

  @Test
  void sincronizaEtapaSemPersistirConteudoDoFormulario() {
    UUID progressoId = UUID.randomUUID();
    OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-07-29T14:00:00Z");
    when(repository.sincronizar(
        any(),
        eq("wizard-create-123"),
        eq(usuarioId),
        eq(null),
        eq("CREATE"),
        eq("FOTOS"),
        eq(3),
        eq("EM_PREENCHIMENTO"),
        any()))
        .thenReturn(new WizardProgressJdbcRepository.SyncRow(
            progressoId,
            "CREATE",
            "EM_PREENCHIMENTO",
            "FOTOS",
            atualizadoEm));

    var response = service.sincronizar(new SyncRequest(
        "wizard-create-123",
        "create",
        "fotos",
        "EM_PREENCHIMENTO",
        null), authentication);

    assertThat(response.id()).isEqualTo(progressoId);
    assertThat(response.ultimoStep()).isEqualTo("FOTOS");
    verify(repository).sincronizar(
        any(),
        eq("wizard-create-123"),
        eq(usuarioId),
        eq(null),
        eq("CREATE"),
        eq("FOTOS"),
        eq(3),
        eq("EM_PREENCHIMENTO"),
        eq(atualizadoEm));
  }

  @Test
  void conclusaoExigeAnuncioDoUsuarioEPassoConcluido() {
    assertThatThrownBy(() -> service.sincronizar(new SyncRequest(
        "wizard-create-124",
        "CREATE",
        "KYC",
        "AGUARDANDO_MODERACAO",
        null), authentication))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }

  @Test
  void anuncioDeOutroUsuarioEhRecusado() {
    UUID anuncioId = UUID.randomUUID();
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
    when(anuncio.getUsuarioId()).thenReturn(UUID.randomUUID());

    assertThatThrownBy(() -> service.sincronizar(new SyncRequest(
        "wizard-create-125",
        "CREATE",
        "CONCLUIDO",
        "AGUARDANDO_MODERACAO",
        anuncioId.toString()), authentication))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  @Test
  void sessaoNaoPodeMudarDeModoEmRetry() {
    when(repository.sincronizar(
        any(),
        anyString(),
        eq(usuarioId),
        any(),
        anyString(),
        anyString(),
        anyInt(),
        anyString(),
        any()))
        .thenReturn(new WizardProgressJdbcRepository.SyncRow(
            UUID.randomUUID(),
            "CREATE",
            "EM_PREENCHIMENTO",
            "PERFIL",
            OffsetDateTime.parse("2026-07-29T14:00:00Z")));

    assertThatThrownBy(() -> service.sincronizar(new SyncRequest(
        "wizard-edit-1234",
        "EDIT",
        "PERFIL",
        "EM_PREENCHIMENTO",
        null), authentication))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }
}
