package br.com.topsdojob.v3.web.admin.creditos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.creditos.AdminPlanoCreditoService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoConsistenciaService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoLedgerConsultaService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoSaldoConsultaService;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.Resumo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminCreditosController.class, properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminPlanoCreditoControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreditoSaldoConsultaService saldoService;
    @MockBean
    private CreditoLedgerConsultaService ledgerService;
    @MockBean
    private CreditoConsistenciaService consistenciaService;
    @MockBean
    private AdminCreditoOperacaoService operacaoService;
    @MockBean
    private AdminPlanoCreditoService planoService;

    @Test
    void adminComPermissaoConsultaEModeradorRecebe403() throws Exception {
        when(planoService.listar(anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/creditos/pacotes")
                        .with(authentication(token(PapelUsuario.ADMIN, true, false))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/creditos/pacotes")
                        .with(authentication(token(PapelUsuario.MODERADOR, false, true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void criacaoExigeAdminGerenciarECsrf() throws Exception {
        when(planoService.criar(any(), any(), any())).thenReturn(resumo());
        String body = """
                {
                  "codigo":"PACOTE_QA",
                  "nome":"Plano QA",
                  "descricao":"Homologacao",
                  "quantidadeCreditos":100,
                  "valor":49.90,
                  "ativo":false,
                  "ordemExibicao":10
                }
                """;

        mockMvc.perform(post("/api/admin/creditos/pacotes")
                        .with(authentication(token(PapelUsuario.ADMIN, true, true)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/creditos/pacotes")
                        .with(authentication(token(PapelUsuario.ADMIN, true, true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/creditos/pacotes")
                        .with(authentication(token(PapelUsuario.MODERADOR, true, true)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken token(
            PapelUsuario papel,
            boolean ler,
            boolean gerenciar) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
        if (ler) authorities.add(new SimpleGrantedAuthority("FINANCEIRO_LER"));
        if (gerenciar) authorities.add(new SimpleGrantedAuthority("FINANCEIRO_GERENCIAR"));
        AdminUserPrincipal principal = new AdminUserPrincipal(
                UUID.randomUUID(),
                "Operador QA",
                "operador.qa@example.invalid",
                "hash",
                List.of(papel),
                authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority -> !authority.startsWith("ROLE_"))
                        .map(authority -> new AdminPermissionDto(authority, authority))
                        .toList(),
                authorities,
                true);
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), authorities);
    }

    private Resumo resumo() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        return new Resumo(
                UUID.randomUUID(),
                "PACOTE_QA",
                "Plano QA",
                "Homologacao",
                100,
                new BigDecimal("49.90"),
                "BRL",
                false,
                10,
                0,
                now,
                now);
    }
}
