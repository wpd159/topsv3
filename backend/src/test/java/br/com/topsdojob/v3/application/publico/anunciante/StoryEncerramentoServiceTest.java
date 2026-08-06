package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryRemocaoRequest;
import br.com.topsdojob.v3.application.publico.anunciante.dto.StoryEncerramentoDto;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class StoryEncerramentoServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-04T19:00:00Z");
  private static final UUID USUARIO_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID OUTRO_USUARIO_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");
  private static final UUID ADMIN_ID = UUID.fromString("20000000-0000-4000-8000-000000000003");

  private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final StoryFalhaTecnicaService falhaTecnicaService = mock(StoryFalhaTecnicaService.class);
  private final AtivacaoBeneficioRepository ativacaoRepository = mock(AtivacaoBeneficioRepository.class);
  private final GrupoAtivacaoBeneficioRepository grupoRepository = mock(GrupoAtivacaoBeneficioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final StoryMidiaCleanupService cleanupService = mock(StoryMidiaCleanupService.class);
  private final Authentication authentication = mock(Authentication.class);
  private final AdminUserPrincipal admin = mock(AdminUserPrincipal.class);
  private StoryEncerramentoService service;
  private StoryAnuncioEntity story;

  @BeforeEach
  void setUp() {
    UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
        USUARIO_ID,
        "Conta QA",
        "qa-story-delete@example.invalid",
        "+5562999999999",
        java.time.LocalDate.of(1990, 1, 1),
        AGORA.minusYears(1));
    usuario.confirmarEmail(AGORA.minusYears(1));
    when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(admin.usuarioId()).thenReturn(ADMIN_ID);
    story = story(USUARIO_ID, UUID.randomUUID());
    when(storyRepository.findByIdForUpdate(story.getId())).thenReturn(Optional.of(story));
    when(storyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(cleanupService.limpar(any(), any(), any()))
        .thenReturn(StoryMidiaCleanupService.Resultado.CONCLUIDO);

    service = new StoryEncerramentoService(
        usuarioService,
        storyRepository,
        falhaTecnicaService,
        ativacaoRepository,
        grupoRepository,
        auditoriaRepository,
        cleanupService,
        new ObjectMapper().findAndRegisterModules(),
        Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC));
  }

  @Test
  void exclusaoVoluntariaEhLogicaIdempotenteENaoRestauraDireito() {
    when(falhaTecnicaService.comprovada(story)).thenReturn(false);

    StoryEncerramentoDto primeira = service.encerrarProprio(
        story.getId(), authentication, "request-exclusao");
    StoryEncerramentoDto repetida = service.encerrarProprio(
        story.getId(), authentication, "request-exclusao-retry");

    assertThat(primeira.repetido()).isFalse();
    assertThat(primeira.status()).isEqualTo(StatusStoryAnuncio.REMOVIDO.name());
    assertThat(primeira.origem()).isEqualTo("USUARIO");
    assertThat(primeira.direitoPreservado()).isFalse();
    assertThat(repetida.repetido()).isTrue();
    assertThat(story.getEncerradoEm()).isEqualTo(AGORA);
    verify(storyRepository).save(story);
    verify(auditoriaRepository).save(any());
    verify(cleanupService, times(2)).limpar(any(), any(), any());
    verifyNoDireitoRestaurado();
  }

  @Test
  void apenasProprietarioPodeExcluirStory() {
    story = story(OUTRO_USUARIO_ID, UUID.randomUUID());
    when(storyRepository.findByIdForUpdate(story.getId())).thenReturn(Optional.of(story));

    assertStatus(
        () -> service.encerrarProprio(story.getId(), authentication, "request-forbidden"),
        HttpStatus.FORBIDDEN);

    verify(storyRepository, never()).save(any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void falhaTecnicaComprovadaRestauraDireitoSemGerarCredito() {
    UUID ativacaoId = story.getAtivacaoBeneficioId();
    UUID grupoId = UUID.randomUUID();
    AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarFixtureHomologacao(
        ativacaoId,
        UUID.randomUUID(),
        USUARIO_ID,
        null,
        grupoId,
        OrigemBeneficio.CREDITO,
        AGORA.minusHours(1),
        AGORA.plusHours(23),
        StatusAtivacaoBeneficio.ATIVA,
        5,
        BigDecimal.ZERO,
        "direito-tecnico",
        AGORA.minusHours(1));
    GrupoAtivacaoBeneficioEntity grupo = GrupoAtivacaoBeneficioEntity.criarFixtureHomologacao(
        grupoId,
        TipoGrupoAtivacaoBeneficio.PACOTE,
        OrigemBeneficio.CREDITO,
        USUARIO_ID,
        null,
        AGORA.minusHours(1),
        AGORA.plusHours(23),
        StatusGrupoAtivacaoBeneficio.ATIVO,
        "grupo-tecnico",
        AGORA.minusHours(1));
    when(falhaTecnicaService.comprovada(story)).thenReturn(true);
    when(ativacaoRepository.findByIdForUpdate(ativacaoId)).thenReturn(Optional.of(ativacao));
    when(grupoRepository.findByIdForUpdate(grupoId)).thenReturn(Optional.of(grupo));
    when(ativacaoRepository.findByGrupoAtivacaoId(grupoId)).thenReturn(List.of(ativacao));

    StoryEncerramentoDto resposta = service.encerrarProprio(
        story.getId(), authentication, "request-falha");
    StoryEncerramentoDto repetida = service.encerrarProprio(
        story.getId(), authentication, "request-falha-retry");

    assertThat(resposta.direitoPreservado()).isTrue();
    assertThat(resposta.motivo()).isEqualTo("FALHA_TECNICA");
    assertThat(repetida.repetido()).isTrue();
    assertThat(repetida.direitoPreservado()).isTrue();
    assertThat(ativacao.getStatus()).isEqualTo(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    assertThat(ativacao.getInicioEm()).isNull();
    assertThat(ativacao.getFimEm()).isNull();
    verify(ativacaoRepository).save(ativacao);
    verify(grupoRepository).save(grupo);
  }

  @Test
  void direitoHistoricoEhRestauradoSemDesvincularAnuncioDaAtivacao() {
    UUID anuncioHistoricoId = UUID.randomUUID();
    UUID ativacaoId = UUID.randomUUID();
    UUID grupoId = UUID.randomUUID();
    story = StoryAnuncioEntity.criarFixtureHomologacao(
        UUID.randomUUID(),
        UUID.randomUUID(),
        AGORA.minusHours(1),
        AGORA.plusHours(23),
        0,
        USUARIO_ID,
        AGORA.minusHours(1));
    ReflectionTestUtils.setField(story, "ativacaoBeneficioId", ativacaoId);
    ReflectionTestUtils.setField(story, "anuncioId", anuncioHistoricoId);
    when(storyRepository.findByIdForUpdate(story.getId())).thenReturn(Optional.of(story));
    AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarFixtureHomologacao(
        ativacaoId,
        UUID.randomUUID(),
        USUARIO_ID,
        anuncioHistoricoId,
        grupoId,
        OrigemBeneficio.CREDITO,
        AGORA.minusHours(1),
        AGORA.plusHours(23),
        StatusAtivacaoBeneficio.ATIVA,
        5,
        BigDecimal.ZERO,
        "direito-historico",
        AGORA.minusHours(1));
    GrupoAtivacaoBeneficioEntity grupo = GrupoAtivacaoBeneficioEntity.criarFixtureHomologacao(
        grupoId,
        TipoGrupoAtivacaoBeneficio.PACOTE,
        OrigemBeneficio.CREDITO,
        USUARIO_ID,
        anuncioHistoricoId,
        AGORA.minusHours(1),
        AGORA.plusHours(23),
        StatusGrupoAtivacaoBeneficio.ATIVO,
        "grupo-historico",
        AGORA.minusHours(1));
    when(falhaTecnicaService.comprovada(story)).thenReturn(true);
    when(ativacaoRepository.findByIdForUpdate(ativacaoId)).thenReturn(Optional.of(ativacao));
    when(grupoRepository.findByIdForUpdate(grupoId)).thenReturn(Optional.of(grupo));
    when(ativacaoRepository.findByGrupoAtivacaoId(grupoId)).thenReturn(List.of(ativacao));

    StoryEncerramentoDto resposta = service.encerrarProprio(
        story.getId(), authentication, "request-falha-historica");

    assertThat(resposta.direitoPreservado()).isTrue();
    assertThat(ativacao.getStatus()).isEqualTo(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    assertThat(ativacao.getAnuncioId()).isEqualTo(anuncioHistoricoId);
    assertThat(grupo.getAnuncioId()).isEqualTo(anuncioHistoricoId);
    verify(ativacaoRepository).save(ativacao);
    verify(grupoRepository, never()).save(grupo);
  }

  @Test
  void falhaDoCleanupDepoisDoEncerramentoNaoConverteSucessoEmErro() {
    when(falhaTecnicaService.comprovada(story)).thenReturn(false);
    when(cleanupService.limpar(any(), any(), any()))
        .thenThrow(new IllegalStateException("storage indisponivel"));

    StoryEncerramentoDto resposta = service.encerrarProprio(
        story.getId(), authentication, "request-cleanup-falhou");

    assertThat(resposta.repetido()).isFalse();
    assertThat(resposta.status()).isEqualTo(StatusStoryAnuncio.REMOVIDO.name());
    assertThat(story.getEncerradoEm()).isEqualTo(AGORA);
    verify(storyRepository).save(story);
    verify(auditoriaRepository).save(any());
    verify(cleanupService).limpar(story.getId(), USUARIO_ID, "request-cleanup-falhou");
  }

  @Test
  void adminExigeMotivoDescricaoParaOutroEProvaParaErroTecnico() {
    assertStatus(
        () -> service.removerComoAdmin(story.getId(), null, admin, "request-sem-motivo"),
        HttpStatus.BAD_REQUEST);
    assertStatus(
        () -> service.removerComoAdmin(
            story.getId(), new AdminStoryRemocaoRequest("OUTRO", " "), admin, "request-outro"),
        HttpStatus.BAD_REQUEST);

    when(falhaTecnicaService.comprovada(story)).thenReturn(false);
    assertStatus(
        () -> service.removerComoAdmin(
            story.getId(),
            new AdminStoryRemocaoRequest("ERRO_TECNICO", null),
            admin,
            "request-erro"),
        HttpStatus.CONFLICT);

    StoryEncerramentoDto removida = service.removerComoAdmin(
        story.getId(),
        new AdminStoryRemocaoRequest("VIOLACAO_REGRAS", null),
        admin,
        "request-admin");
    StoryEncerramentoDto repetida = service.removerComoAdmin(
        story.getId(),
        new AdminStoryRemocaoRequest("VIOLACAO_REGRAS", null),
        admin,
        "request-admin-retry");

    assertThat(removida.origem()).isEqualTo("ADMIN");
    assertThat(removida.motivo()).isEqualTo("VIOLACAO_REGRAS");
    assertThat(repetida.repetido()).isTrue();
    verify(auditoriaRepository).save(any());
  }

  private StoryAnuncioEntity story(UUID usuarioId, UUID ativacaoId) {
    return StoryAnuncioEntity.criarMidiaUpload(
        UUID.randomUUID(),
        UUID.randomUUID(),
        ativacaoId,
        "story-delete-qa",
        "a".repeat(64),
        AGORA.minusHours(1),
        AGORA.plusHours(23),
        usuarioId);
  }

  private void verifyNoDireitoRestaurado() {
    verify(ativacaoRepository, never()).findByIdForUpdate(any());
    verify(ativacaoRepository, never()).save(any());
    verify(grupoRepository, never()).save(any());
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }
}
