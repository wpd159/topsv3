package br.com.topsdojob.v3.web.admin.premium;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.premium.AdminPremiumCatalogoService;
import br.com.topsdojob.v3.application.admin.premium.AdminPremiumOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumConsistenciaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumStatusConsultaService;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminPremiumController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminPremiumOperacaoCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PremiumStatusConsultaService statusService;

    @MockBean
    private BeneficioAnuncioConsultaService beneficioService;

    @MockBean
    private PremiumConsistenciaService consistenciaService;

    @MockBean
    private PremiumCatalogoService catalogoService;

    @MockBean
    private AdminPremiumCatalogoService catalogoAdminService;

    @MockBean
    private AdminPremiumOperacaoService operacaoService;

    @Test
    void ativacaoSemCsrfERecusada() throws Exception {
        mockMvc.perform(post("/api/admin/premium/anuncios/{id}/ativacoes", UUID.randomUUID())
                        .with(admin())
                        .header("Idempotency-Key", "premium-csrf-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminComAutoridadeECsrfChegaAoContrato() throws Exception {
        UUID anuncioId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/premium/anuncios/{id}/ativacoes", anuncioId)
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", "premium-csrf-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isOk());

        verify(operacaoService).ativarManual(
                eq(anuncioId), any(), eq("premium-csrf-002"), isNull(), anyString());
    }

    @Test
    void loteExigeCsrfEEncaminhaUmaUnicaOperacaoIdempotente() throws Exception {
        UUID anuncioId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/premium/anuncios/{id}/ativacoes/lote", anuncioId)
                        .with(admin())
                        .header("Idempotency-Key", "teste-lote-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadLote()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/premium/anuncios/{id}/ativacoes/lote", anuncioId)
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", "teste-lote-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadLote()))
                .andExpect(status().isOk());

        verify(operacaoService).ativarManualLote(
                eq(anuncioId), any(), eq("teste-lote-retry"), isNull(), anyString());
    }

    @Test
    void moderadorNaoAtivaMesmoComCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/premium/anuncios/{id}/ativacoes", UUID.randomUUID())
                        .with(user("moderador").authorities(
                                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                                new SimpleGrantedAuthority("PREMIUM_GERENCIAR")))
                        .with(csrf())
                        .header("Idempotency-Key", "premium-csrf-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isForbidden());
    }

    private String payload() {
        return "{\"beneficioId\":\"" + UUID.randomUUID()
                + "\",\"duracaoDias\":7,\"observacao\":\"Cortesia administrativa\"}";
    }

    private String payloadLote() {
        return "{\"beneficios\":[{\"beneficioId\":\"" + UUID.randomUUID()
                + "\",\"duracaoDias\":7}],\"observacao\":\"Cortesia administrativa\"}";
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("PREMIUM_GERENCIAR"));
    }
}
