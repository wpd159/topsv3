package br.com.topsdojob.v3.web.admin.anuncio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioProprietarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoException;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioErroCampoDto;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminAnuncioAtualizacaoController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminAnuncioAtualizacaoCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAnuncioAtualizacaoService service;

    @MockBean
    private AdminAnuncioProprietarioAtualizacaoService proprietarioService;

    @Test
    void edicaoSemCsrfERecusada() throws Exception {
        mockMvc.perform(put("/api/admin/anuncios/{id}", UUID.randomUUID())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminComAutoridadeECsrfChegaAoContrato() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/admin/anuncios/{id}", id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(service).atualizar(eq(id), any(), isNull(), anyString());
    }

    @Test
    void moderadorNaoEditaMesmoComCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/anuncios/{id}", UUID.randomUUID())
                        .with(user("moderador").authorities(
                                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void proprietarioSemCsrfERecusado() throws Exception {
        mockMvc.perform(patch("/api/admin/anuncios/{id}/proprietario", UUID.randomUUID())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa QA\",\"cpf\":\"52998224725\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminComAutoridadeECsrfAtualizaSomenteProprietarioCanonico() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/anuncios/{id}/proprietario", id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa QA\",\"cpf\":\"52998224725\"}"))
                .andExpect(status().isOk());

        verify(proprietarioService).atualizar(eq(id), any(), isNull(), anyString());
    }

    @Test
    void moderadorNaoAtualizaProprietarioMesmoComCsrf() throws Exception {
        mockMvc.perform(patch("/api/admin/anuncios/{id}/proprietario", UUID.randomUUID())
                        .with(user("moderador").authorities(
                                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa QA\",\"cpf\":\"52998224725\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cpfDuplicadoRetorna409ComMensagemAdministrativaClara() throws Exception {
        UUID id = UUID.randomUUID();
        when(proprietarioService.atualizar(eq(id), any(), isNull(), anyString()))
                .thenThrow(new AdminUsuarioAtualizacaoException(
                        HttpStatus.CONFLICT,
                        "CPF já vinculado a outro usuário.",
                        new AdminUsuarioErroCampoDto(
                                "cpf",
                                "CPF_JA_CADASTRADO",
                                "CPF já vinculado a outro usuário.")));

        mockMvc.perform(patch("/api/admin/anuncios/{id}/proprietario", id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pessoa QA\",\"cpf\":\"52998224725\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("CPF já vinculado a outro usuário."))
                .andExpect(jsonPath("$.erros[0].campo").value("cpf"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ANUNCIO_MODERAR"));
    }
}
