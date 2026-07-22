package br.com.topsdojob.v3.web.publico.anunciante;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioCicloVidaService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhasMidiasService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAcoesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioCicloVidaDto;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = MeusAnunciosController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class MeusAnunciosCicloVidaCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeusAnunciosConsultaService consultaService;

    @MockBean
    private MeuAnuncioAtualizacaoService atualizacaoService;

    @MockBean
    private MeuAnuncioCicloVidaService cicloVidaService;

    @MockBean
    private MinhasMidiasService midiasService;

    @Test
    void mutacoesSemCsrfSaoRecusadas() throws Exception {
        mockMvc.perform(post("/api/public/minha-conta/anuncios/perfil-teste/pausar"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/public/minha-conta/anuncios/perfil-teste/reativar"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/public/minha-conta/anuncios/perfil-teste"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutacaoComCsrfChegaAoContratoCanonico() throws Exception {
        MeuAnuncioCicloVidaDto resposta = new MeuAnuncioCicloVidaDto(
                UUID.fromString("00000000-0000-4000-8000-000000001211"),
                "perfil-teste",
                "PAUSADO",
                "APROVADO",
                OffsetDateTime.parse("2026-07-22T12:00:00Z"),
                new MeuAnuncioAcoesDto(false, true, true));
        when(cicloVidaService.pausar(eq("perfil-teste"), any(), anyString())).thenReturn(resposta);

        mockMvc.perform(post("/api/public/minha-conta/anuncios/perfil-teste/pausar").with(csrf()))
                .andExpect(status().isOk());

        verify(cicloVidaService).pausar(eq("perfil-teste"), any(), anyString());
    }
}
