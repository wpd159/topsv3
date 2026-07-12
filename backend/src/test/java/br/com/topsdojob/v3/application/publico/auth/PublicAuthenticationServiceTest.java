package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.auth.dto.PublicLoginRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicProfileUpdateRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicRegisterRequestDto;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class PublicAuthenticationServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000801");
    private static final String SYNTHETIC_CREDENTIAL = "Valor@123X";
    private static final String SYNTHETIC_HASH = "$2a$10$hash-sintetico-nao-real";

    private UsuarioRepository usuarioRepository;
    private CredencialUsuarioRepository credencialRepository;
    private PapelUsuarioRepository papelRepository;
    private PasswordEncoder passwordEncoder;
    private PublicAuthenticationService service;

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        credencialRepository = mock(CredencialUsuarioRepository.class);
        papelRepository = mock(PapelUsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new PublicAuthenticationService(
                usuarioRepository,
                credencialRepository,
                papelRepository,
                passwordEncoder,
                new HttpSessionSecurityContextRepository());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cadastroValidoPersisteUsuarioHashESeuPapelPublico() {
        when(passwordEncoder.encode(SYNTHETIC_CREDENTIAL)).thenReturn(SYNTHETIC_HASH);

        var response = service.register(validRegisterRequest());

        assertThat(response.autenticado()).isTrue();
        assertThat(response.email()).isEqualTo("perfil@example.invalid");
        ArgumentCaptor<CredencialUsuarioEntity> credentialCaptor = ArgumentCaptor.forClass(CredencialUsuarioEntity.class);
        verify(credencialRepository).save(credentialCaptor.capture());
        assertThat(credentialCaptor.getValue().getSenhaHash()).isEqualTo(SYNTHETIC_HASH);
        assertThat(credentialCaptor.getValue().getSenhaHash()).doesNotContain(SYNTHETIC_CREDENTIAL);
        ArgumentCaptor<UsuarioEntity> usuarioCaptor = ArgumentCaptor.forClass(UsuarioEntity.class);
        verify(usuarioRepository).saveAndFlush(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getDataNascimento()).isEqualTo(java.time.LocalDate.of(1990, 1, 1));
        verify(papelRepository).save(any());
    }

    @Test
    void cadastroDuplicadoRetorna409() {
        when(usuarioRepository.existsByEmailNormalizado("perfil@example.invalid")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRegisterRequest()))
                .isInstanceOfSatisfying(PublicAuthException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cadastroInvalidoRetorna400SemPersistir() {
        PublicRegisterRequestDto request = new PublicRegisterRequestDto(
                "Perfil Sintetico",
                "perfil@example.invalid",
                "62999999999",
                "2020-01-01",
                SYNTHETIC_CREDENTIAL,
                SYNTHETIC_CREDENTIAL,
                true,
                true,
                false);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOfSatisfying(PublicAuthException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void loginValidoTrocaSessaoEUsaPrincipalPublico() {
        stubValidAccount();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        String previousSessionId = session.getId();

        var response = service.login(
                new PublicLoginRequestDto("PERFIL@EXAMPLE.INVALID", SYNTHETIC_CREDENTIAL),
                request,
                new MockHttpServletResponse());

        assertThat(response.autenticado()).isTrue();
        assertThat(request.getSession(false).getId()).isNotEqualTo(previousSessionId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isInstanceOf(PublicUserPrincipal.class);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USUARIO");
    }

    @Test
    void loginInvalidoRetorna401Generico() {
        when(usuarioRepository.findByEmailNormalizado("perfil@example.invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(
                new PublicLoginRequestDto("perfil@example.invalid", SYNTHETIC_CREDENTIAL),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(PublicAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getMessage()).isEqualTo("E-mail ou senha invalidos.");
                });
    }

    @Test
    void meRecuperaUsuarioDaSessaoReal() {
        stubValidAccount();
        service.login(
                new PublicLoginRequestDto("perfil@example.invalid", SYNTHETIC_CREDENTIAL),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));

        var response = service.me(authentication);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.username()).isEqualTo("Perfil Sintetico");
    }

    @Test
    void meSemSessaoRetorna401() {
        assertThatThrownBy(() -> service.me(null))
                .isInstanceOfSatisfying(PublicAuthException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void atualizaSomenteNomeETelefoneSuportadosPeloPerfilPublico() {
        UsuarioEntity usuario = activeUser();
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));
        Authentication authentication = publicAuthentication(usuario);

        var response = service.updateProfile(
                new PublicProfileUpdateRequestDto("Perfil Atualizado", "(62) 98888-7777"),
                authentication);

        assertThat(response.username()).isEqualTo("Perfil Atualizado");
        assertThat(response.telefone()).isEqualTo("+5562988887777");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void perfilInvalidoRetorna400SemPersistir() {
        UsuarioEntity usuario = activeUser();
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.updateProfile(
                new PublicProfileUpdateRequestDto("x", "123"),
                publicAuthentication(usuario)))
                .isInstanceOfSatisfying(PublicAuthException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void atualizacaoDePerfilSemSessaoRetorna401() {
        assertThatThrownBy(() -> service.updateProfile(
                new PublicProfileUpdateRequestDto("Perfil Atualizado", "62999999999"),
                null))
                .isInstanceOfSatisfying(PublicAuthException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void perfilDuplicadoRetorna409() {
        UsuarioEntity usuario = activeUser();
        UsuarioEntity outro = UsuarioEntity.criarCadastroPublico(
                UUID.randomUUID(),
                "Outro Perfil",
                "outro@example.invalid",
                "+5562999990000",
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByNomeIgnoreCase("Outro Perfil")).thenReturn(Optional.of(outro));

        assertThatThrownBy(() -> service.updateProfile(
                new PublicProfileUpdateRequestDto("Outro Perfil", "62999999999"),
                publicAuthentication(usuario)))
                .isInstanceOfSatisfying(PublicAuthException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void logoutInvalidaSessao() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);

        var response = service.logout(request);

        assertThat(response.autenticado()).isFalse();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void duplicidadeConsultaEmailNomeETelefoneNormalizados() {
        when(usuarioRepository.existsByEmailNormalizado("perfil@example.invalid")).thenReturn(true);
        when(usuarioRepository.existsByNomeIgnoreCase("Perfil Sintetico")).thenReturn(true);
        when(usuarioRepository.existsByTelefoneNormalizado("+5562999999999")).thenReturn(true);

        var response = service.duplicidade(
                "PERFIL@EXAMPLE.INVALID",
                "Perfil Sintetico",
                "(62) 99999-9999");

        assertThat(response.emailExistente()).isTrue();
        assertThat(response.usernameExistente()).isTrue();
        assertThat(response.telefoneExistente()).isTrue();
    }

    private void stubValidAccount() {
        UsuarioEntity usuario = activeUser();
        CredencialUsuarioEntity credencial = CredencialUsuarioEntity.criar(
                UUID.fromString("00000000-0000-4000-8000-000000000802"),
                USER_ID,
                SYNTHETIC_HASH,
                OffsetDateTime.now(ZoneOffset.UTC));
        when(usuarioRepository.findByEmailNormalizado("perfil@example.invalid")).thenReturn(Optional.of(usuario));
        when(credencialRepository.findByUsuarioId(USER_ID)).thenReturn(Optional.of(credencial));
        when(passwordEncoder.matches(SYNTHETIC_CREDENTIAL, SYNTHETIC_HASH)).thenReturn(true);
    }

    private UsuarioEntity activeUser() {
        return UsuarioEntity.criarCadastroPublico(
                USER_ID,
                "Perfil Sintetico",
                "perfil@example.invalid",
                "+5562999999999",
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Authentication publicAuthentication(UsuarioEntity usuario) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new PublicUserPrincipal(USER_ID, usuario.getNome(), usuario.getEmailNormalizado()),
                null,
                java.util.List.of());
    }

    private PublicRegisterRequestDto validRegisterRequest() {
        return new PublicRegisterRequestDto(
                "Perfil Sintetico",
                "perfil@example.invalid",
                "62999999999",
                "1990-01-01",
                SYNTHETIC_CREDENTIAL,
                SYNTHETIC_CREDENTIAL,
                true,
                true,
                false);
    }
}
