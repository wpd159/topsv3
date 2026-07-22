package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminMidiaFilaModeracaoTest {

    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AdminMidiaDetalhadaConsultaService service = new AdminMidiaDetalhadaConsultaService(
            midiaRepository,
            arquivoRepository,
            anuncioRepository);

    @Test
    void filaDoAnuncioExcluiStoriesNaConsultaAoBanco() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        ReflectionTestUtils.setField(anuncio, "id", anuncioId);
        when(midiaRepository.findByAnuncioIdAndTipoNot(
                eq(anuncioId), eq(TipoAnuncioMidia.STORY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        service.listarPorAnuncio(anuncio, 0, 20);

        verify(midiaRepository).findByAnuncioIdAndTipoNot(
                eq(anuncioId), eq(TipoAnuncioMidia.STORY), any(Pageable.class));
    }

    @Test
    void storyNaoPossuiDetalheNaModeracaoDeMidias() {
        UUID id = UUID.randomUUID();
        AnuncioMidiaEntity story = entity(AnuncioMidiaEntity.class);
        ReflectionTestUtils.setField(story, "id", id);
        ReflectionTestUtils.setField(story, "tipo", TipoAnuncioMidia.STORY);
        when(midiaRepository.findById(id)).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> service.detalhar(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(arquivoRepository, never()).findById(any());
    }

    private <T> T entity(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
