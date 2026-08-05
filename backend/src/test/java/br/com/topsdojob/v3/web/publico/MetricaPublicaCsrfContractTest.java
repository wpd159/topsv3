package br.com.topsdojob.v3.web.publico;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.PoliticaContatoPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaResponseDto;
import br.com.topsdojob.v3.application.publico.service.AnuncioPublicoConsultaService;
import br.com.topsdojob.v3.application.publico.service.ListagemPublicaConsultaService;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaService;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(
        controllers = AnuncioPublicoController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class MetricaPublicaCsrfContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnuncioPublicoConsultaService consultaService;

    @MockBean
    private ListagemPublicaConsultaService listagemService;

    @MockBean
    private MetricaPublicaService metricaService;

    @Test
    void metricasSaoRecusadasSemCsrf() throws Exception {
        mockMvc.perform(post("/api/public/anuncios/anuncio-local/visualizacao")
                        .header("Idempotency-Key", "view-csrf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/public/anuncios/anuncio-local/clique-whatsapp")
                        .header("Idempotency-Key", "click-csrf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chaveIdempotenteEObrigatoriaNosDoisContratos() throws Exception {
        when(metricaService.registrarVisualizacao(
                eq("anuncio-local"), any(), isNull(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida"));
        when(metricaService.registrarCliqueWhatsapp(
                eq("anuncio-local"), any(), isNull(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida"));

        mockMvc.perform(post("/api/public/anuncios/anuncio-local/visualizacao")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/public/anuncios/anuncio-local/clique-whatsapp")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void csrfEChaveValidosChegamAoServicoCanonico() throws Exception {
        when(metricaService.registrarVisualizacao(
                eq("anuncio-local"), any(), eq("view-contract-1"), any()))
                .thenReturn(new RegistrarVisualizacaoPublicaResponseDto(
                        true,
                        "anuncio-local",
                        "00000000-0000-0000-0000-000000000001",
                        "REGISTRADO",
                        MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN));
        when(metricaService.registrarCliqueWhatsapp(
                eq("anuncio-local"), any(), eq("click-contract-1"), any()))
                .thenReturn(new CliqueWhatsappPublicoResponseDto(
                        true,
                        true,
                        "https://wa.me/5500000000000",
                        "REGISTRADO",
                        new PoliticaContatoPublicoDto(true, "DISPONIVEL", null),
                        MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN));

        mockMvc.perform(post("/api/public/anuncios/anuncio-local/visualizacao")
                        .with(csrf())
                        .header("Idempotency-Key", "view-contract-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/public/anuncios/anuncio-local/clique-whatsapp")
                        .with(csrf())
                        .header("Idempotency-Key", "click-contract-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(metricaService).registrarVisualizacao(
                eq("anuncio-local"), any(), eq("view-contract-1"), any());
        verify(metricaService).registrarCliqueWhatsapp(
                eq("anuncio-local"), any(), eq("click-contract-1"), any());
    }
}
