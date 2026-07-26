package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.publico.dto.ConfirmarIdadePublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.StatusIdadePublicaDto;
import jakarta.servlet.http.Cookie;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;

class IdadePublicaServiceTest {

    @Test
    void confirmaIdadeComCookieHttpOnlySemCpfOuDocumento() {
        EventoVerificacaoEtariaRepository repository = mock(EventoVerificacaoEtariaRepository.class);
        IdadePublicaService service = service(repository);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
        httpRequest.addHeader("User-Agent", "teste");

        IdadePublicaService.ConfirmacaoIdadeResult result = service.confirmar(
                new ConfirmarIdadePublicaRequestDto(LocalDate.now().minusYears(20), true),
                httpRequest);

        assertThat(result.status().confirmada()).isTrue();
        assertThat(result.cookie().isHttpOnly()).isTrue();
        assertThat(result.cookie().isSecure()).isFalse();
        assertThat(result.cookie().toString()).contains("SameSite=Lax");
        assertThat(result.toString()).doesNotContain("cpf").doesNotContain("documento");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(IdadePublicaTokenService.COOKIE_NAME, result.cookie().getValue()));
        StatusIdadePublicaDto status = service.status(request);
        assertThat(status.confirmada()).isTrue();
        ArgumentCaptor<EventoVerificacaoEtariaEntity> evento = ArgumentCaptor.forClass(
                EventoVerificacaoEtariaEntity.class);
        verify(repository).save(evento.capture());
        assertThat(evento.getValue().getResultado().name()).isEqualTo("PERMITIDO");
    }

    @Test
    void negaMenorDeIdadeSemRetornarErro500() {
        EventoVerificacaoEtariaRepository repository = mock(EventoVerificacaoEtariaRepository.class);
        IdadePublicaService service = service(repository);

        assertThatThrownBy(() -> service.confirmar(
                new ConfirmarIdadePublicaRequestDto(LocalDate.now().minusYears(17), true),
                new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        ArgumentCaptor<EventoVerificacaoEtariaEntity> evento = ArgumentCaptor.forClass(
                EventoVerificacaoEtariaEntity.class);
        verify(repository).save(evento.capture());
        assertThat(evento.getValue().getResultado().name()).isEqualTo("NEGADO");
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

    private IdadePublicaService service(EventoVerificacaoEtariaRepository repository) {
        return new IdadePublicaService(
                new IdadePublicaTokenService("valor_local_ficticio_idade", "local"),
                new MetricaPublicaHashService("valor_local_ficticio", "local"),
                repository);
    }
}
