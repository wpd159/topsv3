package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.auth.dto.PublicLoginRequestDto;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import jakarta.servlet.http.HttpSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class CredencialLegadaCompatibilidadeTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000861");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hashBcryptDeContaComSegundoFatorLegadoDesativadoAutenticaSomenteComValorCorreto() {
        String runtimeValue = UUID.randomUUID() + "aA9!";
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String legacyHash = encoder.encode(runtimeValue);
        assertThat(legacyHash).startsWith("$2a$10$").hasSize(60);
        assertThat(encoder.upgradeEncoding(legacyHash)).isFalse();

        UsuarioEntity user = activeUser();
        CredencialUsuarioEntity credential = CredencialUsuarioEntity.criar(
                UUID.randomUUID(), USER_ID, legacyHash, OffsetDateTime.now(ZoneOffset.UTC));
        PublicAuthenticationService service = service(user, credential, encoder);

        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = service.login(
                new PublicLoginRequestDto("legacy@example.invalid", runtimeValue),
                request,
                new MockHttpServletResponse());

        assertThat(response.autenticado()).isTrue();
        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.login(
                new PublicLoginRequestDto("legacy@example.invalid", runtimeValue + "-incorreto"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void contaSemConfirmacaoNaoAutenticaMesmoComHashValido() {
        String runtimeValue = UUID.randomUUID() + "aA9!";
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        UsuarioEntity pending = UsuarioEntity.criarCadastroPublico(
                USER_ID,
                "Conta Pendente",
                "legacy@example.invalid",
                "+5562999999999",
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
        CredencialUsuarioEntity credential = CredencialUsuarioEntity.criar(
                UUID.randomUUID(), USER_ID, encoder.encode(runtimeValue), OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> service(pending, credential, encoder).login(
                new PublicLoginRequestDto("legacy@example.invalid", runtimeValue),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void contaDesativadaNaoAutenticaMesmoComHashValido() {
        String runtimeValue = UUID.randomUUID() + "aA9!";
        PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        UsuarioEntity disabled = mock(UsuarioEntity.class);
        when(disabled.getId()).thenReturn(USER_ID);
        when(disabled.getEmailVerificadoEm()).thenReturn(OffsetDateTime.now(ZoneOffset.UTC));
        when(disabled.getStatus()).thenReturn(StatusUsuario.DESATIVADO);
        when(disabled.getTipoConta()).thenReturn(TipoContaUsuario.ANUNCIANTE);
        when(disabled.getDesativadoEm()).thenReturn(OffsetDateTime.now(ZoneOffset.UTC));
        CredencialUsuarioEntity credential = CredencialUsuarioEntity.criar(
                UUID.randomUUID(), USER_ID, encoder.encode(runtimeValue), OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> service(disabled, credential, encoder).login(
                new PublicLoginRequestDto("legacy@example.invalid", runtimeValue),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private PublicAuthenticationService service(
            UsuarioEntity user,
            CredencialUsuarioEntity credential,
            PasswordEncoder encoder) {
        UsuarioRepository users = mock(UsuarioRepository.class);
        CredencialUsuarioRepository credentials = mock(CredencialUsuarioRepository.class);
        when(users.findByEmailNormalizado("legacy@example.invalid")).thenReturn(Optional.of(user));
        when(credentials.findByUsuarioId(USER_ID)).thenReturn(Optional.of(credential));
        return new PublicAuthenticationService(
                users,
                credentials,
                mock(PapelUsuarioRepository.class),
                encoder,
                new HttpSessionSecurityContextRepository(),
                mock(PublicAccountLifecycleService.class),
                mock(PublicSessionRegistry.class),
                mock(br.com.topsdojob.v3.persistence.repository.AnuncioRepository.class),
                mock(PublicAuthSecurityService.class));
    }

    private UsuarioEntity activeUser() {
        UsuarioEntity user = UsuarioEntity.criarCadastroPublico(
                USER_ID,
                "Conta Legada",
                "legacy@example.invalid",
                "+5562999999999",
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
        user.confirmarEmail(OffsetDateTime.now(ZoneOffset.UTC));
        return user;
    }
}
