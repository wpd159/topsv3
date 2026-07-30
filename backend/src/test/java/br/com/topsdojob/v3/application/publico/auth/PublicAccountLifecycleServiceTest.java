package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.auth.dto.PublicCodeRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicEmailRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicResetPasswordRequestDto;
import br.com.topsdojob.v3.application.operacional.outbox.OutboxEmailPayloadFactory;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.TokenSegurancaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.TokenSegurancaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class PublicAccountLifecycleServiceTest {
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final CredencialUsuarioRepository credentials = mock(CredencialUsuarioRepository.class);
    private final TokenSegurancaRepository tokens = mock(TokenSegurancaRepository.class);
    private final OutboxEventoRepository outbox = mock(OutboxEventoRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PublicSessionRegistry sessions = mock(PublicSessionRegistry.class);
    private final HmlAuthCodeVault vault = mock(HmlAuthCodeVault.class);
    private final OutboxEmailPayloadFactory emailPayloads = mock(OutboxEmailPayloadFactory.class);
    private PublicAccountLifecycleService service;
    private UsuarioEntity user;

    @BeforeEach void setup() {
        service = new PublicAccountLifecycleService(users, credentials, tokens, outbox, encoder,
                new PublicAuthRateLimiter(), sessions, Optional.of(vault), emailPayloads, 900);
        user = UsuarioEntity.criarCadastroPublico(UUID.randomUUID(), "Perfil", "perfil@example.invalid",
                "+5562999999999", null, OffsetDateTime.now(ZoneOffset.UTC));
        when(users.findByEmailNormalizado("perfil@example.invalid")).thenReturn(Optional.of(user));
        when(emailPayloads.auth(anyString(), any(), any(), anyString(), anyString(), any()))
                .thenReturn("{\"communicationVersion\":1}");
    }

    @Test void solicitacaoNaoEnumeraEmailInexistente() {
        String existing = service.forgot(new PublicEmailRequestDto("perfil@example.invalid"), "ip-a").message();
        String missing = service.forgot(new PublicEmailRequestDto("ausente@example.invalid"), "ip-b").message();
        assertThat(existing).isEqualTo(missing);
        verify(outbox).save(any());
    }

    @Test void confirmacaoValidaConsomeTokenEAtivaConta() {
        TokenSegurancaEntity securityRecord = token(PublicAccountLifecycleService.CONFIRMATION);
        when(tokens.findFirstByUsuarioIdAndTipoOrderByCriadoEmDesc(
                user.getId(), PublicAccountLifecycleService.CONFIRMATION))
                .thenReturn(Optional.of(securityRecord));
        when(encoder.matches("123456", "hash-protegido")).thenReturn(true);
        service.confirm(new PublicCodeRequestDto("perfil@example.invalid", "123456"), "ip-c");
        assertThat(securityRecord.getConsumidoEm()).isNotNull();
        assertThat(user.getEmailVerificadoEm()).isNotNull();
    }

    @Test void tokenInvalidoRetorna400EContaTentativa() {
        TokenSegurancaEntity securityRecord = token(PublicAccountLifecycleService.RESET);
        when(tokens.findFirstByUsuarioIdAndTipoOrderByCriadoEmDesc(
                user.getId(), PublicAccountLifecycleService.RESET))
                .thenReturn(Optional.of(securityRecord));
        assertThatThrownBy(() -> service.validateReset(new PublicCodeRequestDto("perfil@example.invalid", "000000"), "ip-d"))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(securityRecord.getTentativas()).isEqualTo(1);
    }

    @Test void tokenExpiradoOuReutilizadoRetorna400SemErroInterno() {
        TokenSegurancaEntity expired = TokenSegurancaEntity.criar(user.getId(), PublicAccountLifecycleService.RESET,
                "hash-protegido", OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(20));
        when(tokens.findFirstByUsuarioIdAndTipoOrderByCriadoEmDesc(
                user.getId(), PublicAccountLifecycleService.RESET))
                .thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.validateReset(
                new PublicCodeRequestDto("perfil@example.invalid", "123456"), "ip-expired"))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        error -> assertThat(error.getMessage()).isEqualTo("Código expirado."));
        assertThat(expired.getConsumidoEm()).isNotNull();
        assertThatThrownBy(() -> service.validateReset(
                new PublicCodeRequestDto("perfil@example.invalid", "123456"), "ip-reused"))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        error -> assertThat(error.getMessage()).isEqualTo("Código já utilizado."));
    }

    @Test void rateLimitBloqueiaExcessoCom429() {
        PublicAuthRateLimiter limiter = new PublicAuthRateLimiter();
        for (int index = 0; index < 3; index++) limiter.require("request", "same-key", 3, java.time.Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.require("request", "same-key", 3, java.time.Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test void redefinicaoTrocaHashConsomeTokenEInvalidaSessoes() {
        TokenSegurancaEntity securityRecord = token(PublicAccountLifecycleService.RESET);
        CredencialUsuarioEntity credential = CredencialUsuarioEntity.criar(UUID.randomUUID(), user.getId(), "hash-antigo", OffsetDateTime.now(ZoneOffset.UTC));
        when(tokens.findFirstByUsuarioIdAndTipoOrderByCriadoEmDesc(
                user.getId(), PublicAccountLifecycleService.RESET))
                .thenReturn(Optional.of(securityRecord));
        when(encoder.matches("123456", "hash-protegido")).thenReturn(true);
        when(encoder.encode("Nova@Forte9")).thenReturn("hash-novo");
        when(credentials.findByUsuarioId(user.getId())).thenReturn(Optional.of(credential));
        service.reset(new PublicResetPasswordRequestDto("perfil@example.invalid", "123456", "Nova@Forte9", "Nova@Forte9"), "ip-e");
        assertThat(credential.getSenhaHash()).isEqualTo("hash-novo");
        assertThat(securityRecord.getConsumidoEm()).isNotNull();
        verify(sessions).invalidateAll(user.getId());
    }

    private TokenSegurancaEntity token(String purpose) {
        return TokenSegurancaEntity.criar(user.getId(), purpose, "hash-protegido",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5), OffsetDateTime.now(ZoneOffset.UTC));
    }

}
