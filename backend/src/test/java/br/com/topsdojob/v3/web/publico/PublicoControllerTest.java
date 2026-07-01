package br.com.topsdojob.v3.web.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.PaginacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.service.ListagemPublicaConsultaService;
import br.com.topsdojob.v3.application.publico.service.SeoPublicoConsultaService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicoControllerTest {

    @Test
    void listagemPublicaNaoExpoeCampoSensivel() {
        ListagemPublicaConsultaService service = mock(ListagemPublicaConsultaService.class);
        ListaAnunciosPublicaDto resposta = new ListaAnunciosPublicaDto(
                List.of(),
                new PaginacaoPublicaDto(0, 20, 0, 0),
                new SeoRotaPublicaDto(
                        "Titulo",
                        "Descricao",
                        "/acompanhantes/sp/sao-paulo",
                        "NOINDEX_FOLLOW",
                        "CIDADE",
                        false));
        when(service.porCidade("sp", "sao-paulo", 0, 20)).thenReturn(resposta);

        ListaAnunciosPublicaDto dto = new ListagemPublicaController(service)
                .porCidade("sp", "sao-paulo", 0, 20);

        assertThat(dto.toString())
                .doesNotContain("cpf")
                .doesNotContain("email")
                .doesNotContain("pagamento")
                .doesNotContain("credito")
                .doesNotContain("storage");
        verify(service).porCidade("sp", "sao-paulo", 0, 20);
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

        SeoRotaPublicaDto dto = new SeoPublicoController(service).porRota("/anuncios/slug-local");

        assertThat(dto.canonicalPath()).isEqualTo("/anuncios/slug-local");
        assertThat(dto.canonicalPath()).doesNotContain("/perfil/");
        assertThat(dto.canonicalPath()).doesNotContain("/ads/");
        assertThat(dto.canonicalPath()).doesNotContain("/anuncio/");
    }
}
