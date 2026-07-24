package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.ListaStoriesPublicosDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class StoryPublicoServiceTest {

    @Test
    void storySemIdadeConfirmadaEntregaSomentePreviewBorrada() {
        UUID anuncioId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioMidiaRepository anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
        IdadePublicaService idadeService = mock(IdadePublicaService.class);
        MidiaPublicaUrlService urlService = mock(MidiaPublicaUrlService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", vinculoId);
        set(vinculo, "anuncioId", anuncioId);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.STORY);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.STORY);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        set(arquivo, "mimeType", "image/jpeg");
        StoryAnuncioEntity story = entity(StoryAnuncioEntity.class);
        set(story, "anuncioMidiaId", vinculoId);
        set(story, "status", StatusStoryAnuncio.PUBLICADO);
        set(story, "ordem", 1);
        when(idadeService.idadeConfirmada(request)).thenReturn(false);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq("anuncio-local"),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(Optional.of(anuncio));
        when(anuncioMidiaRepository.findByAnuncioId(anuncioId)).thenReturn(List.of(vinculo));
        when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
        when(storyRepository.findByAnuncioMidiaIdIn(java.util.Set.of(vinculoId))).thenReturn(List.of(story));
        when(urlService.resolverPreviewRestrita(arquivo))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica(
                        "/restritas-borradas/preview.jpg",
                        null));

        StoryPublicoService service = new StoryPublicoService(
                anuncioRepository,
                anuncioMidiaRepository,
                arquivoRepository,
                storyRepository,
                idadeService,
                urlService);

        ListaStoriesPublicosDto response = service.listar("anuncio-local", request);

        assertThat(response.idadeConfirmada()).isFalse();
        assertThat(response.autorizado()).isFalse();
        assertThat(response.stories()).singleElement().satisfies(item -> {
            assertThat(item.urlPublica()).isEqualTo("/restritas-borradas/preview.jpg");
            assertThat(item.visibilidadeMidia()).isEqualTo("RESTRITA_18");
        });
        assertThat(response.politica().motivoPublico()).isEqualTo("IDADE_NAO_CONFIRMADA");
        verify(urlService, never()).resolver(vinculo, arquivo);
    }

    @Test
    void storyRestritoApareceComIdadeConfirmadaSemStorageOuHash() {
        UUID anuncioId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        UUID vinculoId = UUID.randomUUID();
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioMidiaRepository anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
        IdadePublicaService idadeService = mock(IdadePublicaService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", vinculoId);
        set(vinculo, "anuncioId", anuncioId);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.STORY);
        set(vinculo, "finalidade", FinalidadeAnuncioMidia.STORY);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "bucket", "bucket-privado");
        set(arquivo, "chaveObjeto", "synthetic/story.bin");
        set(arquivo, "sha256", "a".repeat(64));
        set(arquivo, "mimeType", "video/mp4");
        set(arquivo, "duracaoMs", 1000);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        StoryAnuncioEntity story = entity(StoryAnuncioEntity.class);
        set(story, "anuncioMidiaId", vinculoId);
        set(story, "status", StatusStoryAnuncio.PUBLICADO);
        set(story, "ordem", 1);

        when(idadeService.idadeConfirmada(request)).thenReturn(true);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq("anuncio-local"),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(Optional.of(anuncio));
        when(anuncioMidiaRepository.findByAnuncioId(anuncioId)).thenReturn(List.of(vinculo));
        when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
        when(storyRepository.findByAnuncioMidiaIdIn(java.util.Set.of(vinculoId))).thenReturn(List.of(story));

        StoryPublicoService service = new StoryPublicoService(
                anuncioRepository,
                anuncioMidiaRepository,
                arquivoRepository,
                storyRepository,
                idadeService,
                new MidiaPublicaUrlService());

        ListaStoriesPublicosDto response = service.listar("anuncio-local", request);

        assertThat(response.autorizado()).isTrue();
        assertThat(response.stories()).hasSize(1);
        assertThat(response.stories().get(0).pendenciaMidia())
                .isEqualTo(MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
        assertThat(response.toString())
                .doesNotContain("bucket-privado")
                .doesNotContain("synthetic/story.bin")
                .doesNotContain("aaaaaaaa");
    }
}
