package br.com.topsdojob.v3.application.admin.stories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusStoriesConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesPublicacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuStoryGerenciadoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AdminStoriesGestaoServiceTest {

  private static final UUID STORY_ID = UUID.fromString("61000000-0000-4000-8000-000000000001");
  private static final UUID USUARIO_ID = UUID.fromString("61000000-0000-4000-8000-000000000002");
  private static final UUID ANUNCIO_ID = UUID.fromString("61000000-0000-4000-8000-000000000003");
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-07T15:00:00Z");

  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final MeusStoriesConsultaService consultaService = mock(MeusStoriesConsultaService.class);
  private final MinhaContaStoriesPublicacaoService publicacaoService =
      mock(MinhaContaStoriesPublicacaoService.class);
  private final AdminStoriesGestaoService service = new AdminStoriesGestaoService(
      storyRepository, usuarioRepository, consultaService, publicacaoService);

  @Test
  void combinaUsuarioBuscaEPaginacaoComCargaDeUsuariosEmLote() {
    StoryAnuncioEntity story = mock(StoryAnuncioEntity.class);
    UsuarioEntity usuario = mock(UsuarioEntity.class);
    PageRequest pagina = PageRequest.of(2, 15);
    MeuStoryGerenciadoDto estado = new MeuStoryGerenciadoDto(
        STORY_ID,
        "ANUNCIO",
        "PUBLICADO",
        "ATIVO",
        AGORA,
        AGORA.plusHours(24),
        "anuncio-de-teste",
        "Anuncio de teste",
        null,
        false,
        true,
        false,
        null,
        false);
    when(story.getId()).thenReturn(STORY_ID);
    when(story.getCriadoPor()).thenReturn(USUARIO_ID);
    when(storyRepository.findGestaoAdministrativa(
        USUARIO_ID, "anuncio de teste", pagina))
        .thenReturn(new PageImpl<>(List.of(story), pagina, 31));
    when(consultaService.mapear(List.of(story))).thenReturn(List.of(estado));
    when(usuario.getId()).thenReturn(USUARIO_ID);
    when(usuario.getNome()).thenReturn("wesley");
    when(usuarioRepository.findAllById(List.of(USUARIO_ID))).thenReturn(List.of(usuario));

    var resultado = service.listar(
        USUARIO_ID, "  ANUNCIO   DE TESTE  ", 2, 15);

    assertThat(resultado.pagina()).isEqualTo(2);
    assertThat(resultado.tamanho()).isEqualTo(15);
    assertThat(resultado.totalElementos()).isEqualTo(31);
    assertThat(resultado.totalPaginas()).isEqualTo(3);
    assertThat(resultado.itens()).singleElement().satisfies(item -> {
      assertThat(item.id()).isEqualTo(STORY_ID);
      assertThat(item.usuarioUsername()).isEqualTo("wesley");
      assertThat(item.anuncioTitulo()).isEqualTo("Anuncio de teste");
    });
    verify(storyRepository).findGestaoAdministrativa(
        USUARIO_ID, "anuncio de teste", pagina);
    verify(consultaService).mapear(List.of(story));
    verify(usuarioRepository, times(1)).findAllById(List.of(USUARIO_ID));
  }

  @Test
  void publicacaoAdministrativaDelegaAoWriterCanonico() {
    AdminUserPrincipal admin = mock(AdminUserPrincipal.class);
    MinhaContaStoryDto esperado = new MinhaContaStoryDto(
        STORY_ID,
        ANUNCIO_ID,
        "ANUNCIO",
        null,
        "PUBLICADO",
        AGORA,
        AGORA.plusHours(24),
        null);
    when(publicacaoService.publicarAdministrativamente(
        ANUNCIO_ID, "admin-story-service-01", admin, "req-admin-story"))
        .thenReturn(esperado);

    var resultado = service.publicar(
        ANUNCIO_ID, "admin-story-service-01", admin, "req-admin-story");

    assertThat(resultado).isSameAs(esperado);
    verify(publicacaoService).publicarAdministrativamente(
        eq(ANUNCIO_ID), eq("admin-story-service-01"), eq(admin), eq("req-admin-story"));
  }
}
