package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.SeoPublicoMapper;
import br.com.topsdojob.v3.persistence.repository.SeoUrlRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SeoPublicoConsultaServiceTest {

    @Test
    void dtoSeoPreservaCanonicalPathSemDominioProducao() {
        SeoUrlRepository repository = mock(SeoUrlRepository.class);
        when(repository.findByCaminhoPublico("/anuncios/slug-local")).thenReturn(Optional.empty());
        SeoPublicoConsultaService service = new SeoPublicoConsultaService(repository, new SeoPublicoMapper());

        SeoRotaPublicaDto dto = service.buscarPorCaminho("/anuncios/slug-local");

        assertThat(dto.canonicalPath()).isEqualTo("/anuncios/slug-local");
        assertThat(dto.canonicalPath()).doesNotContain("topsdojob.com");
        assertThat(dto.robots()).isEqualTo("NOINDEX_FOLLOW");
    }

    @Test
    void rejeitaRotaAlternativaProibida() {
        SeoPublicoConsultaService service = new SeoPublicoConsultaService(
                mock(SeoUrlRepository.class),
                new SeoPublicoMapper());

        assertThatThrownBy(() -> service.buscarPorCaminho("/perfil/slug-local"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void aceitaArquivosPublicosSeoLocais() {
        SeoUrlRepository repository = mock(SeoUrlRepository.class);
        when(repository.findByCaminhoPublico("/sitemap.xml")).thenReturn(Optional.empty());
        when(repository.findByCaminhoPublico("/robots.txt")).thenReturn(Optional.empty());
        SeoPublicoConsultaService service = new SeoPublicoConsultaService(repository, new SeoPublicoMapper());

        assertThat(service.buscarPorCaminho("/sitemap.xml").canonicalPath()).isEqualTo("/sitemap.xml");
        assertThat(service.buscarPorCaminho("/robots.txt").canonicalPath()).isEqualTo("/robots.txt");
    }

    @Test
    void rejeitaUrlAbsolutaDeProducaoNoSeoLocal() {
        SeoPublicoConsultaService service = new SeoPublicoConsultaService(
                mock(SeoUrlRepository.class),
                new SeoPublicoMapper());

        assertThatThrownBy(() -> service.buscarPorCaminho("https://topsdojob.com/anuncios/x"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
