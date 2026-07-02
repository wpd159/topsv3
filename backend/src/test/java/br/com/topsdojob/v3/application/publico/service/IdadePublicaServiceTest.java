package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.publico.dto.ConfirmarIdadePublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.StatusIdadePublicaDto;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class IdadePublicaServiceTest {

    @Test
    void confirmaIdadeComCookieHttpOnlySemCpfOuDocumento() {
        IdadePublicaService service = new IdadePublicaService(
                new IdadePublicaTokenService("valor_local_ficticio_idade", "local"));

        IdadePublicaService.ConfirmacaoIdadeResult result = service.confirmar(
                new ConfirmarIdadePublicaRequestDto(LocalDate.now().minusYears(20), true));

        assertThat(result.status().confirmada()).isTrue();
        assertThat(result.cookie().isHttpOnly()).isTrue();
        assertThat(result.cookie().isSecure()).isFalse();
        assertThat(result.cookie().toString()).contains("SameSite=Lax");
        assertThat(result.toString()).doesNotContain("cpf").doesNotContain("documento");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(IdadePublicaTokenService.COOKIE_NAME, result.cookie().getValue()));
        StatusIdadePublicaDto status = service.status(request);
        assertThat(status.confirmada()).isTrue();
    }

    @Test
    void negaMenorDeIdadeSemRetornarErro500() {
        IdadePublicaService service = new IdadePublicaService(
                new IdadePublicaTokenService("valor_local_ficticio_idade", "local"));

        assertThatThrownBy(() -> service.confirmar(
                new ConfirmarIdadePublicaRequestDto(LocalDate.now().minusYears(17), true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void segredoFicticioFalhaForaDeLocal() {
        assertThatThrownBy(() -> new IdadePublicaTokenService("valor_local_ficticio_idade", "producao"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AGE_GATE_SIGNING_VALUE");
        assertThatThrownBy(() -> new MetricaPublicaHashService("valor_local_ficticio", "producao"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_EVENT_HASH_SALT");
    }

    @Test
    void segredoFicticioFalhaSemAppEnvLocalExplicito() {
        assertThatThrownBy(() -> new IdadePublicaTokenService("valor_local_ficticio_idade", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AGE_GATE_SIGNING_VALUE");
        assertThatThrownBy(() -> new MetricaPublicaHashService("valor_local_ficticio", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_EVENT_HASH_SALT");
    }

    @Test
    void cookieFicaSecureForaDeLocalComValorConfigurado() {
        IdadePublicaTokenService comprovanteService = new IdadePublicaTokenService("valor_configurado_nao_ficticio", "producao");

        assertThat(comprovanteService.cookieSecure()).isTrue();
    }
}
