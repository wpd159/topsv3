package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.compliance.dto.VisitorChallengeRequestDto;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ComplianceChallengeContextServiceTest {

  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final StorySelecaoAdministrativaRepository storyAdminRepository =
      mock(StorySelecaoAdministrativaRepository.class);

  private ComplianceChallengeContextService service;

  @BeforeEach
  void setUp() {
    service = new ComplianceChallengeContextService(
        anuncioRepository,
        midiaRepository,
        storyRepository,
        storyAdminRepository);
  }

  @Test
  void aceitaSomenteStoryPublicadoVigenteECanonico() {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    UUID anuncioId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    UUID storyId = UUID.randomUUID();
    AnuncioEntity anuncio = anuncioPublicado(anuncioId, agora);
    AnuncioMidiaEntity midia = midiaStory(midiaId, anuncioId, agora);
    StoryAnuncioEntity story = StoryAnuncioEntity.criarFixtureHomologacao(
        storyId,
        midiaId,
        agora.minusMinutes(1),
        agora.plusHours(1),
        1,
        UUID.randomUUID(),
        agora.minusMinutes(1));
    when(storyRepository.findByIdAndStatus(
        storyId,
        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio.PUBLICADO))
        .thenReturn(Optional.of(story));
    when(midiaRepository.findById(midiaId)).thenReturn(Optional.of(midia));
    when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));

    ComplianceChallengeContextService.Contexto contexto = service.validar(
        request(anuncioId, storyId.toString()),
        EscopoConteudoVisitante.STORY);

    assertThat(contexto.anuncioId()).isEqualTo(anuncioId);
    assertThat(contexto.midiaId()).isEqualTo(midiaId);
    assertThat(contexto.storyReferencia()).isEqualTo(storyId.toString());
  }

  @Test
  void recusaStoryExpiradoOuInexistente() {
    UUID anuncioId = UUID.randomUUID();
    UUID storyId = UUID.randomUUID();
    when(storyRepository.findByIdAndStatus(
        storyId,
        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio.PUBLICADO))
        .thenReturn(Optional.empty());

    assertStatus(
        () -> service.validar(
            request(anuncioId, storyId.toString()),
            EscopoConteudoVisitante.STORY),
        HttpStatus.NOT_FOUND);
  }

  @Test
  void recusaStoryVinculadoAOutroAnuncio() {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    UUID anuncioInformado = UUID.randomUUID();
    UUID anuncioReal = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    UUID storyId = UUID.randomUUID();
    StoryAnuncioEntity story = StoryAnuncioEntity.criarFixtureHomologacao(
        storyId,
        midiaId,
        agora.minusMinutes(1),
        agora.plusHours(1),
        1,
        UUID.randomUUID(),
        agora.minusMinutes(1));
    when(storyRepository.findByIdAndStatus(
        storyId,
        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio.PUBLICADO))
        .thenReturn(Optional.of(story));
    when(midiaRepository.findById(midiaId))
        .thenReturn(Optional.of(midiaStory(midiaId, anuncioReal, agora)));

    assertStatus(
        () -> service.validar(
            request(anuncioInformado, storyId.toString()),
            EscopoConteudoVisitante.STORY),
        HttpStatus.CONFLICT);
  }

  @Test
  void aceitaStoryAdministrativoSomenteQuandoSelecaoEstaAtiva() {
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    UUID anuncioId = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    StorySelecaoAdministrativaEntity selecao = mock(StorySelecaoAdministrativaEntity.class);
    when(selecao.isAtiva()).thenReturn(true);
    when(selecao.getAnuncioId()).thenReturn(anuncioId);
    when(selecao.getAtivadoEm()).thenReturn(agora.minusHours(1));
    when(selecao.getExpiraEm()).thenReturn(agora.plusHours(23));
    when(storyAdminRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenReturn(List.of(selecao));
    when(midiaRepository.findById(midiaId)).thenReturn(Optional.of(
        AnuncioMidiaEntity.criarFixtureHomologacao(
            midiaId,
            anuncioId,
            UUID.randomUUID(),
            TipoAnuncioMidia.FOTO,
            FinalidadeAnuncioMidia.GALERIA,
            1,
            StatusAnuncioMidia.PUBLICAVEL,
            VisibilidadeMidia.LIVRE,
            agora)));
    when(anuncioRepository.findById(anuncioId))
        .thenReturn(Optional.of(anuncioPublicado(anuncioId, agora)));

    ComplianceChallengeContextService.Contexto contexto = service.validar(
        request(anuncioId, "administrativo:" + midiaId),
        EscopoConteudoVisitante.STORY);

    assertThat(contexto.midiaId()).isEqualTo(midiaId);
    assertThat(contexto.storyReferencia()).isEqualTo("administrativo:" + midiaId);
  }

  @Test
  void recusaReferenciaDeStoryFabricada() {
    assertStatus(
        () -> service.validar(
            request(UUID.randomUUID(), "story-inexistente"),
            EscopoConteudoVisitante.STORY),
        HttpStatus.BAD_REQUEST);
  }

  private VisitorChallengeRequestDto request(UUID anuncioId, String storyId) {
    return new VisitorChallengeRequestDto(
        "REINFORCED",
        "STORY",
        anuncioId,
        null,
        storyId,
        "/stories",
        "story-context-test");
  }

  private AnuncioEntity anuncioPublicado(UUID anuncioId, OffsetDateTime agora) {
    return AnuncioEntity.criarFixtureHomologacao(
        anuncioId,
        UUID.randomUUID(),
        "anuncio-" + anuncioId,
        "Anuncio",
        "Descricao",
        StatusAnuncio.PUBLICADO,
        StatusModeracaoAnuncio.APROVADO,
        agora);
  }

  private AnuncioMidiaEntity midiaStory(
      UUID midiaId,
      UUID anuncioId,
      OffsetDateTime agora) {
    return AnuncioMidiaEntity.criarFixtureHomologacao(
        midiaId,
        anuncioId,
        UUID.randomUUID(),
        TipoAnuncioMidia.STORY,
        FinalidadeAnuncioMidia.STORY,
        1,
        StatusAnuncioMidia.PUBLICAVEL,
        VisibilidadeMidia.RESTRITA_18,
        agora);
  }

  private void assertStatus(Runnable action, HttpStatus expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(expected));
  }
}
