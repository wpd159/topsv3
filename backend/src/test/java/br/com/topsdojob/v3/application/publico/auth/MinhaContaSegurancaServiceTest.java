package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioExclusaoService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.publico.auth.dto.MinhaContaAlterarSenhaRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.MinhaContaExcluirRequestDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.TokenSegurancaEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.TokenSegurancaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class MinhaContaSegurancaServiceTest {

    private static final UUID USUARIO_ID =
            UUID.fromString("00000000-0000-4000-8000-000000004501");
    private CredencialUsuarioRepository credenciais;
    private TokenSegurancaRepository tokens;
    private AuditoriaEventoRepository auditorias;
    private PasswordEncoder encoder;
    private PublicSessionRegistry sessions;
    private AdminUsuarioExclusaoService exclusao;
    private MinhaContaSegurancaService service;

    @BeforeEach
    void setUp() {
        credenciais = mock(CredencialUsuarioRepository.class);
        tokens = mock(TokenSegurancaRepository.class);
        auditorias = mock(AuditoriaEventoRepository.class);
        encoder = mock(PasswordEncoder.class);
        sessions = mock(PublicSessionRegistry.class);
        exclusao = mock(AdminUsuarioExclusaoService.class);
        service = new MinhaContaSegurancaService(
                credenciais,
                tokens,
                auditorias,
                encoder,
                sessions,
                new PublicAuthRateLimiter(),
                exclusao);
    }

    @Test
    void alteraSenhaConsomeTokensAuditaEInvalidaTodasAsSessoes() {
        CredencialUsuarioEntity credencial = credencial();
        TokenSegurancaEntity registroRecuperacao = TokenSegurancaEntity.criar(
                USUARIO_ID,
                "RECUPERACAO_SENHA",
                "hash-opaco",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                OffsetDateTime.now(ZoneOffset.UTC));
        when(credenciais.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(credencial));
        when(tokens.findTodosAtivosForUpdate(USUARIO_ID)).thenReturn(List.of(registroRecuperacao));
        when(encoder.matches("Atual@123", "hash-atual")).thenReturn(true);
        when(encoder.matches("Nova@456A", "hash-atual")).thenReturn(false);
        when(encoder.encode("Nova@456A")).thenReturn("hash-novo");

        var response = service.alterarSenha(
                new MinhaContaAlterarSenhaRequestDto("Atual@123", "Nova@456A", "Nova@456A"),
                authentication(),
                "request-password-4501");

        assertThat(response.message())
                .isEqualTo("Sua senha foi alterada com sucesso. Entre novamente.");
        assertThat(credencial.getSenhaHash()).isEqualTo("hash-novo");
        assertThat(registroRecuperacao.getConsumidoEm()).isNotNull();
        verify(sessions).invalidateAll(USUARIO_ID);
        ArgumentCaptor<AuditoriaEventoEntity> audit =
                ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditorias).save(audit.capture());
        assertThat(audit.getValue().getAcao()).isEqualTo("USUARIO_ALTEROU_SENHA");
        assertThat(audit.getValue().getRequestId()).isEqualTo("request-password-4501");
        assertThat(audit.getValue().getDepoisJson())
                .contains("\"tokensRevogados\":true")
                .doesNotContain("Atual@123", "Nova@456A", "hash-novo");
    }

    @Test
    void recusaSenhaAtualIncorretaSemAlterarCredencial() {
        CredencialUsuarioEntity credencial = credencial();
        when(credenciais.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(credencial));
        when(encoder.matches("Errada@123", "hash-atual")).thenReturn(false);

        assertThatThrownBy(() -> service.alterarSenha(
                new MinhaContaAlterarSenhaRequestDto("Errada@123", "Nova@456A", "Nova@456A"),
                authentication(),
                "request-password-wrong"))
                .isInstanceOfSatisfying(PublicAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getMessage()).isEqualTo("Senha atual incorreta.");
                });
        assertThat(credencial.getSenhaHash()).isEqualTo("hash-atual");
        verify(auditorias, never()).save(any());
        verify(sessions, never()).invalidateAll(any());
    }

    @Test
    void adminAlteraSomenteAPropriaSenhaEPreservaPapelEPermissoes() {
        CredencialUsuarioEntity credencial = credencial();
        Authentication admin = adminAuthentication();
        when(credenciais.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(credencial));
        when(tokens.findTodosAtivosForUpdate(USUARIO_ID)).thenReturn(List.of());
        when(encoder.matches("Atual@123", "hash-atual")).thenReturn(true);
        when(encoder.matches("Nova@456A", "hash-atual")).thenReturn(false);
        when(encoder.encode("Nova@456A")).thenReturn("hash-admin-novo");

        var response = service.alterarSenhaAdministrativa(
                new MinhaContaAlterarSenhaRequestDto("Atual@123", "Nova@456A", "Nova@456A"),
                admin,
                "request-admin-password-4501");

        assertThat(response.message())
                .isEqualTo("Sua senha foi alterada com sucesso. Entre novamente.");
        assertThat(credencial.getSenhaHash()).isEqualTo("hash-admin-novo");
        assertThat(admin.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN", "ADMIN_CONFIGURAR");
        verify(credenciais).findByUsuarioIdForUpdate(USUARIO_ID);
        verify(sessions).invalidateAll(USUARIO_ID);
    }

    @Test
    void adminComSenhaAtualIncorretaNaoAlteraHashNemAudita() {
        CredencialUsuarioEntity credencial = credencial();
        when(credenciais.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(credencial));
        when(encoder.matches("Incorreta@1", "hash-atual")).thenReturn(false);

        assertThatThrownBy(() -> service.alterarSenhaAdministrativa(
                new MinhaContaAlterarSenhaRequestDto(
                        "Incorreta@1",
                        "Nova@456A",
                        "Nova@456A"),
                adminAuthentication(),
                "request-admin-password-wrong"))
                .isInstanceOfSatisfying(PublicAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getMessage()).isEqualTo("Senha atual incorreta.");
                });

        assertThat(credencial.getSenhaHash()).isEqualTo("hash-atual");
        verify(auditorias, never()).save(any());
        verify(sessions, never()).invalidateAll(any());
    }

    @Test
    void recusaSenhaFracaDivergenteEIgualAAtualComMensagensEspecificas() {
        CredencialUsuarioEntity credencial = credencial();
        when(credenciais.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(credencial));
        when(encoder.matches(eq("Atual@123"), eq("hash-atual"))).thenReturn(true);

        assertReason(
                new MinhaContaAlterarSenhaRequestDto("Atual@123", "fraca", "fraca"),
                "A nova senha não atende aos requisitos de segurança.");
        assertReason(
                new MinhaContaAlterarSenhaRequestDto("Atual@123", "Nova@456A", "Outra@456A"),
                "As senhas não coincidem.");

        when(encoder.matches("Atual@123", "hash-atual")).thenReturn(true);
        assertReason(
                new MinhaContaAlterarSenhaRequestDto("Atual@123", "Atual@123", "Atual@123"),
                "A nova senha deve ser diferente da senha atual.");
    }

    @Test
    void exclusaoExigeSenhaConfirmacaoConsentimentoEIdempotencia() {
        CredencialUsuarioEntity credencial = credencial();
        when(credenciais.findByUsuarioIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(credencial));
        when(encoder.matches("Atual@123", "hash-atual")).thenReturn(true);
        var expected = new AdminUsuarioExclusaoResultadoDto(
                USUARIO_ID, true, "EXCLUSAO_FISICA", false);
        when(exclusao.excluirPeloProprioUsuario(
                USUARIO_ID,
                "delete-own-account-4501",
                "request-delete-4501")).thenReturn(expected);

        var response = service.excluir(
                new MinhaContaExcluirRequestDto(
                        "Atual@123",
                        MinhaContaSegurancaService.CONFIRMACAO_EXCLUSAO,
                        true),
                "delete-own-account-4501",
                authentication(),
                "request-delete-4501");

        assertThat(response).isEqualTo(expected);
        verify(exclusao).excluirPeloProprioUsuario(
                USUARIO_ID,
                "delete-own-account-4501",
                "request-delete-4501");
    }

    @Test
    void principalDaSessaoEhUnicaFonteDoTitular() {
        when(exclusao.elegibilidade(USUARIO_ID)).thenReturn(null);

        service.elegibilidade(authentication());

        verify(exclusao).elegibilidade(USUARIO_ID);
    }

    private void assertReason(MinhaContaAlterarSenhaRequestDto request, String reason) {
        assertThatThrownBy(() -> service.alterarSenha(
                request,
                authentication(),
                "request-password-invalid-" + UUID.randomUUID()))
                .satisfies(exception -> {
                    if (exception instanceof PublicAuthException publicAuth) {
                        assertThat(publicAuth.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(publicAuth.getMessage()).isEqualTo(reason);
                    } else {
                        assertThat(exception).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) exception).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(((ResponseStatusException) exception).getReason()).isEqualTo(reason);
                    }
                });
    }

    private CredencialUsuarioEntity credencial() {
        return CredencialUsuarioEntity.criar(
                UUID.randomUUID(),
                USUARIO_ID,
                "hash-atual",
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                new PublicUserPrincipal(
                        USUARIO_ID,
                        "qa-preprod",
                        "qa@example.invalid"),
                null,
                List.of());
    }

    private Authentication adminAuthentication() {
        AdminUserPrincipal principal = new AdminUserPrincipal(
                USUARIO_ID,
                "Admin Sintético",
                "admin@example.invalid",
                "hash-atual",
                List.of(PapelUsuario.ADMIN),
                List.of(new AdminPermissionDto(
                        "ADMIN_CONFIGURAR",
                        "Permissão sintética")),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ADMIN_CONFIGURAR")),
                true);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
    }
}
