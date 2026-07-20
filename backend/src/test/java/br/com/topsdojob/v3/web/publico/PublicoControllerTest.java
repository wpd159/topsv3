package br.com.topsdojob.v3.web.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.PaginacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SitemapAnuncioPublicoDto;
import br.com.topsdojob.v3.application.publico.service.ListagemPublicaConsultaService;
import br.com.topsdojob.v3.application.publico.service.SeoPublicoConsultaService;
import br.com.topsdojob.v3.application.publico.service.SitemapPublicoConsultaService;
import java.util.List;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PublicoControllerTest {

    @Test
    void listagemPublicaNaoExpoeCampoSensivel() {
        ListagemPublicaConsultaService service = mock(ListagemPublicaConsultaService.class);
        ListaAnunciosPublicaDto resposta = new ListaAnunciosPublicaDto(
                List.of(),
                new PaginacaoPublicaDto(0, 20, 0, 0, "123"),
                new LocalizacaoPublicaDto("SP", "Sao Paulo", "Sao Paulo", "sao-paulo", null, null, null),
                new SeoRotaPublicaDto(
                        "Titulo",
                        "Descricao",
                        "/acompanhantes/sp/sao-paulo",
                        "NOINDEX_FOLLOW",
                        "CIDADE",
                        false));
        when(service.porCidade("sp", "sao-paulo", 0, 20, "123")).thenReturn(resposta);

        ListaAnunciosPublicaDto dto = new ListagemPublicaController(service)
                .porCidade("sp", "sao-paulo", 0, 20, "123");

        assertThat(dto.toString())
                .doesNotContain("cpf")
                .doesNotContain("email")
                .doesNotContain("pagamento")
                .doesNotContain("credito")
                .doesNotContain("storage");
        verify(service).porCidade("sp", "sao-paulo", 0, 20, "123");
    }

    @Test
    void controllerSeoUsaEndpointPublicoSemRotaAlternativa() {
        SeoPublicoConsultaService service = mock(SeoPublicoConsultaService.class);
        SeoRotaPublicaDto resposta = new SeoRotaPublicaDto(
                "Titulo",
                "Descricao",
                "/anuncios/slug-local",
                "NOINDEX_FOLLOW",
                "ANUNCIO",
                false);
        when(service.buscarPorCaminho("/anuncios/slug-local")).thenReturn(resposta);

        SeoRotaPublicaDto dto = new SeoPublicoController(service, mock(SitemapPublicoConsultaService.class))
                .porRota("/anuncios/slug-local");

        assertThat(dto.canonicalPath()).isEqualTo("/anuncios/slug-local");
        assertThat(dto.canonicalPath()).doesNotContain("/perfil/");
        assertThat(dto.canonicalPath()).doesNotContain("/ads/");
        assertThat(dto.canonicalPath()).doesNotContain("/anuncio/");
    }

    @Test
    void controllerSitemapExpoeSomenteContratoLeveDoBackend() {
        SeoPublicoConsultaService seoService = mock(SeoPublicoConsultaService.class);
        SitemapPublicoConsultaService sitemapService = mock(SitemapPublicoConsultaService.class);
        SitemapAnuncioPublicoDto entrada = new SitemapAnuncioPublicoDto(
                "perfil-publico",
                "GO",
                "goiania",
                "setor-bueno",
                OffsetDateTime.parse("2026-07-11T12:00:00-03:00"),
                true,
                true);
        when(sitemapService.listarAnunciosIndexaveis()).thenReturn(List.of(entrada));

        var resposta = new SeoPublicoController(seoService, sitemapService).sitemap();

        assertThat(resposta).containsExactly(entrada);
        assertThat(resposta.toString())
                .doesNotContain("storage")
                .doesNotContain("contato")
                .doesNotContain("midia");
        verify(sitemapService).listarAnunciosIndexaveis();
    }
}
