package br.com.topsdojob.v3.application.operacional.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.auth.PublicAccountLifecycleService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthenticationService;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicLoginRequestDto;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class AuthSmokeFixtureServiceTest {

    private final Map<UUID, UsuarioEntity> usuarios = new HashMap<>();
    private final Map<UUID, CredencialUsuarioEntity> credenciais = new HashMap<>();
    private final Map<UUID, List<PapelUsuarioEntity>> papeis = new HashMap<>();
    private UsuarioRepository usuarioRepository;
    private CredencialUsuarioRepository credencialRepository;
    private PapelUsuarioRepository papelRepository;
    private PasswordEncoder passwordEncoder;
    private AuthSmokeFixtureService service;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        credencialRepository = mock(CredencialUsuarioRepository.class);
        papelRepository = mock(PapelUsuarioRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();

        when(usuarioRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(usuarios.get(invocation.getArgument(0))));
        when(usuarioRepository.findByEmailNormalizado(anyString()))
                .thenAnswer(invocation -> usuarios.values().stream()
                        .filter(item -> invocation.<String>getArgument(0).equals(item.getEmailNormalizado()))
                        .findFirst());
        when(usuarioRepository.saveAndFlush(any(UsuarioEntity.class))).thenAnswer(invocation -> {
            UsuarioEntity item = invocation.getArgument(0);
            usuarios.put(item.getId(), item);
            return item;
        });
        when(credencialRepository.findByUsuarioId(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(credenciais.get(invocation.getArgument(0))));
        when(credencialRepository.save(any(CredencialUsuarioEntity.class))).thenAnswer(invocation -> {
            CredencialUsuarioEntity item = invocation.getArgument(0);
            credenciais.put(item.getUsuarioId(), item);
            return item;
        });
        when(papelRepository.findByUsuarioId(any(UUID.class)))
                .thenAnswer(invocation -> List.copyOf(papeis.getOrDefault(
                        invocation.getArgument(0),
                        List.of())));
        when(papelRepository.save(any(PapelUsuarioEntity.class))).thenAnswer(invocation -> {
            PapelUsuarioEntity item = invocation.getArgument(0);
            papeis.computeIfAbsent(item.getUsuarioId(), ignored -> new ArrayList<>()).add(item);
            return item;
        });
        service = new AuthSmokeFixtureService(
                "homologacao",
                usuarioRepository,
                credencialRepository,
                papelRepository,
                passwordEncoder);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reconciliaTresContasIdempotentesComBcryptEPapelUsuario() {
        String runtimeValue = "Aa1!" + UUID.randomUUID();

        var primeira = service.reconciliar(runtimeValue);
        var segunda = service.reconciliar(runtimeValue);

        assertThat(primeira).isEqualTo(new AuthSmokeFixtureService.FixtureResult(3, 0, 0));
        assertThat(segunda).isEqualTo(new AuthSmokeFixtureService.FixtureResult(0, 0, 3));
        assertThat(usuarios).hasSize(3);
        assertThat(credenciais).hasSize(3);
        assertThat(papeis).hasSize(3);
        assertThat(credenciais.values()).allSatisfy(item -> {
            assertThat(item.getAlgoritmo()).isEqualTo("BCRYPT");
            assertThat(item.getSenhaHash()).startsWith("$2a$");
            assertThat(passwordEncoder.matches(runtimeValue, item.getSenhaHash())).isTrue();
        });
        assertThat(papeis.values()).allSatisfy(items -> assertThat(items)
                .singleElement()
                .extracting(PapelUsuarioEntity::getPapel)
                .isEqualTo(PapelUsuario.USUARIO));

        UsuarioEntity ativo = porEmail(AuthSmokeFixtureService.ACTIVE_EMAIL);
        assertThat(ativo.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(ativo.getEmailVerificadoEm()).isNotNull();
        assertThat(ativo.getDesativadoEm()).isNull();

        UsuarioEntity pendente = porEmail(AuthSmokeFixtureService.PENDING_EMAIL);
        assertThat(pendente.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(pendente.getEmailVerificadoEm()).isNull();
        assertThat(pendente.getDesativadoEm()).isNull();

        UsuarioEntity desativado = porEmail(AuthSmokeFixtureService.DISABLED_EMAIL);
        assertThat(desativado.getStatus()).isEqualTo(StatusUsuario.DESATIVADO);
        assertThat(desativado.getEmailVerificadoEm()).isNotNull();
        assertThat(desativado.getDesativadoEm()).isNotNull();
    }

    @Test
    void autenticaSomenteContaAtivaConfirmadaComValorCorreto() {
        String runtimeValue = "Aa1!" + UUID.randomUUID();
        service.reconciliar(runtimeValue);
        PublicAuthenticationService authentication = authenticationService();

        var response = authentication.login(
                new PublicLoginRequestDto(AuthSmokeFixtureService.ACTIVE_EMAIL, runtimeValue),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());
        assertThat(response.autenticado()).isTrue();

        SecurityContextHolder.clearContext();
        assertUnauthorized(authentication, AuthSmokeFixtureService.ACTIVE_EMAIL, runtimeValue + "x");
        assertUnauthorized(authentication, AuthSmokeFixtureService.PENDING_EMAIL, runtimeValue);
        assertUnauthorized(authentication, AuthSmokeFixtureService.DISABLED_EMAIL, runtimeValue);
    }

    @Test
    void exigeFlagComSegredoValidoERecusaExecucaoForaDeHomologacao() {
        assertThatThrownBy(() -> service.reconciliar(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FIXTURE_AUTH_SMOKE_RUNTIME_VALUE");

        AuthSmokeFixtureService production = new AuthSmokeFixtureService(
                "producao",
                usuarioRepository,
                credencialRepository,
                papelRepository,
                passwordEncoder);
        assertThatThrownBy(() -> production.reconciliar("Aa1!" + UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora de homologacao");
        assertThat(usuarios).isEmpty();
    }

    private UsuarioEntity porEmail(String email) {
        return usuarios.values().stream()
                .filter(item -> email.equals(item.getEmailNormalizado()))
                .findFirst()
                .orElseThrow();
    }

    private PublicAuthenticationService authenticationService() {
        return new PublicAuthenticationService(
                usuarioRepository,
                credencialRepository,
                papelRepository,
                passwordEncoder,
                new HttpSessionSecurityContextRepository(),
                mock(PublicAccountLifecycleService.class),
                mock(PublicSessionRegistry.class),
                mock(br.com.topsdojob.v3.persistence.repository.AnuncioRepository.class));
    }

    private void assertUnauthorized(PublicAuthenticationService authentication, String email, String value) {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> authentication.login(
                new PublicLoginRequestDto(email, value),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(
                        PublicAuthException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
