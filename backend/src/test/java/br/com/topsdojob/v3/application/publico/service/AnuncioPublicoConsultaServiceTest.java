package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.SeoPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.SeoUrlRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AnuncioPublicoConsultaServiceTest {

    @Test
    void retorna404ParaAnuncioInexistente() {
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                "slug-local",
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO))
                .thenReturn(Optional.empty());

        AnuncioPublicoConsultaService service = new AnuncioPublicoConsultaService(
                anuncioRepository,
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioMidiaRepository.class),
                mock(ArquivoMidiaRepository.class),
                new AnuncioPublicoMapper(),
                new MidiaPublicaMapper(new MidiaPublicaUrlService()),
                new SeoPublicoConsultaService(mock(SeoUrlRepository.class), new SeoPublicoMapper()),
                mock(IdadePublicaService.class),
                mock(PremiumPublicoMapper.class));

        assertThatThrownBy(() -> service.buscarPorSlug("slug-local"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
