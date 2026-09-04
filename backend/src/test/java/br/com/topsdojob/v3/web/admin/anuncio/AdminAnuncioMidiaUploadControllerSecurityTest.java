package br.com.topsdojob.v3.web.admin.anuncio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaUploadService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioMidiaUploadDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminAnuncioMidiaUploadController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminAnuncioMidiaUploadControllerSecurityTest {

    private static final UUID ANUNCIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAnuncioMidiaUploadService service;

    @BeforeEach
    void setUp() {
        when(service.enviar(eq(ANUNCIO_ID), any(), eq("upload-1"), any(), any()))
                .thenReturn(new AdminAnuncioMidiaUploadDto(
                        UUID.randomUUID(), ANUNCIO_ID, "FOTO", "GALERIA", 0,
                        "PENDENTE", "PENDENTE", false, "req-1"));
    }

    @Test
    void sucessoExigeTriplaAutoridadeECsrfENaoPermiteCache() throws Exception {
        mockMvc.perform(multipart("/api/admin/anuncios/{anuncioId}/midias", ANUNCIO_ID)
                        .file(foto("arquivo", "foto.jpg"))
                        .header("Idempotency-Key", "upload-1")
                        .with(authentication(token(
                                "ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR")))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.statusArquivo").value("PENDENTE"))
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.bucket").doesNotExist())
                .andExpect(jsonPath("$.chaveObjeto").doesNotExist())
                .andExpect(jsonPath("$.url").doesNotExist())
                .andExpect(jsonPath("$.nomeOriginal").doesNotExist());

        verify(service).enviar(eq(ANUNCIO_ID), any(), eq("upload-1"), any(), any());
    }

    @Test
    void recusaAusenciaDeCadaAutoridadeEDeCsrf() throws Exception {
        for (String[] authorities : List.of(
                new String[] {"ANUNCIO_MODERAR", "MIDIA_REVISAR"},
                new String[] {"ROLE_ADMIN", "MIDIA_REVISAR"},
                new String[] {"ROLE_ADMIN", "ANUNCIO_MODERAR"})) {
            mockMvc.perform(multipart("/api/admin/anuncios/{anuncioId}/midias", ANUNCIO_ID)
                            .file(foto("arquivo", "foto.jpg"))
                            .header("Idempotency-Key", "upload-1")
                            .with(authentication(token(authorities)))
                            .with(csrf().asHeader()))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(multipart("/api/admin/anuncios/{anuncioId}/midias", ANUNCIO_ID)
                        .file(foto("arquivo", "foto.jpg"))
                        .header("Idempotency-Key", "upload-1")
                        .with(authentication(token(
                                "ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR"))))
                .andExpect(status().isForbidden());

        verify(service, never()).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void idempotencyKeyEhObrigatoria() throws Exception {
        mockMvc.perform(multipart("/api/admin/anuncios/{anuncioId}/midias", ANUNCIO_ID)
                        .file(foto("arquivo", "foto.jpg"))
                        .with(authentication(token(
                                "ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR")))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verify(service, never()).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void recusaParteDuplicadaExtraOuCampoDeFormulario() throws Exception {
        var auth = authentication(token("ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR"));
        mockMvc.perform(multipart("/api/admin/anuncios/{anuncioId}/midias", ANUNCIO_ID)
                        .file(foto("arquivo", "foto-1.jpg"))
                        .file(foto("arquivo", "foto-2.jpg"))
                        .header("Idempotency-Key", "upload-1")
                        .with(auth)
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest());

        clearInvocations(service);
        mockMvc.perform(multipart("/api/admin/anuncios/{anuncioId}/midias", ANUNCIO_ID)
                        .file(foto("arquivo", "foto.jpg"))
                        .file(foto("ownerId", "intruso.txt"))
                        .header("Idempotency-Key", "upload-1")
                        .with(authentication(token(
                                "ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR")))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest());

        clearInvocations(service);
        mockMvc.perform(multipart("/api/admin/anuncios/{anuncioId}/midias", ANUNCIO_ID)
                        .file(foto("arquivo", "foto.jpg"))
                        .param("bucket", "publico")
                        .header("Idempotency-Key", "upload-1")
                        .with(authentication(token(
                                "ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR")))
                        .with(csrf().asHeader()))
                .andExpect(status().isBadRequest());

        verify(service, never()).enviar(any(), any(), any(), any(), any());
    }

    private MockMultipartFile foto(String campo, String nome) {
        return new MockMultipartFile(campo, nome, "image/jpeg", new byte[] {1, 2, 3});
    }

    private UsernamePasswordAuthenticationToken token(String... authorities) {
        List<GrantedAuthority> granted = Arrays.stream(authorities)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        AdminUserPrincipal principal = new AdminUserPrincipal(
                UUID.randomUUID(), "Admin", "admin@example.invalid", "hash",
                List.of(PapelUsuario.ADMIN), List.<AdminPermissionDto>of(), granted, true);
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), granted);
    }
}
