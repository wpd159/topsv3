package br.com.topsdojob.v3.web.admin.usuario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioConsultaService;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@WebMvcTest(
        controllers = AdminUsuarioController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminUsuarioControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUsuarioConsultaService service;

    @MockBean
    private AdminUsuarioAtualizacaoService atualizacaoService;

    @Test
    void adminEModeradorComAnuncioLerAcessamListaEDetalhe() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listar(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new AdminPaginaDto<>(List.of(), 0, 20, 0, 0, true));
        when(service.detalhar(any(), any())).thenReturn(detalhe(id));

        for (PapelUsuario papel : List.of(PapelUsuario.ADMIN, PapelUsuario.MODERADOR)) {
            mockMvc.perform(get("/api/admin/usuarios").with(authentication(tokenFor(papel))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens").isArray());
            mockMvc.perform(get("/api/admin/usuarios/{id}", id).with(authentication(tokenFor(papel))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));
        }
    }

    @Test
    void usuarioEOperadorSemPermissaoRecebem403() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .with(user("usuario").roles("USUARIO").authorities(
                                new SimpleGrantedAuthority("ROLE_USUARIO"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/usuarios/{id}", UUID.randomUUID())
                        .with(user("moderador").roles("MODERADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonimoRecebe401() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void somenteAdminComPermissaoECsrfAtualizaTelefone() throws Exception {
        UUID id = UUID.randomUUID();
        AdminUsuarioDetalheDto detail = detalhe(id);
        when(atualizacaoService.atualizarTelefone(any(), any(), any(), any())).thenReturn(detail);

        mockMvc.perform(patch("/api/admin/usuarios/{id}", id)
                        .with(authentication(tokenFor(PapelUsuario.ADMIN, "ANUNCIO_MODERAR")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"+5562999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(patch("/api/admin/usuarios/{id}", id)
                        .with(authentication(tokenFor(PapelUsuario.MODERADOR, "ANUNCIO_MODERAR")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"+5562999999999\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/usuarios/{id}", id)
                        .with(authentication(tokenFor(PapelUsuario.ADMIN, "ANUNCIO_MODERAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"+5562999999999\"}"))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken tokenFor(PapelUsuario papel) {
        return tokenFor(papel, "ANUNCIO_LER");
    }

    private UsernamePasswordAuthenticationToken tokenFor(PapelUsuario papel, String... permissions) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
        authorities.add(new SimpleGrantedAuthority("ANUNCIO_LER"));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        var principal = new AdminUserPrincipal(
                UUID.randomUUID(),
                "Operador",
                "operador@example.invalid",
                "hash",
                List.of(papel),
                authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority -> !authority.startsWith("ROLE_"))
                        .distinct()
                        .map(authority -> new AdminPermissionDto(authority, authority))
                        .toList(),
                authorities,
                true);
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), authorities);
    }

    private AdminUsuarioDetalheDto detalhe(UUID id) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T10:00:00Z");
        return new AdminUsuarioDetalheDto(
                id,
                "QA",
                "QA Civil",
                "qa@example.invalid",
                "+5562999999999",
                "***.***.***-09",
                true,
                null,
                "ATIVO",
                "ANUNCIANTE",
                "SEM_ENVIO",
                false,
                now,
                now,
                null,
                false,
                false,
                List.of(),
                List.of(),
                List.of());
    }
}
