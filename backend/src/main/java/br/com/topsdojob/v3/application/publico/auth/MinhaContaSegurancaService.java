package br.com.topsdojob.v3.application.publico.auth;

import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioExclusaoService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoElegibilidadeDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.application.publico.auth.dto.MinhaContaAlterarSenhaRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.MinhaContaExcluirRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicAccountActionDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.TokenSegurancaRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MinhaContaSegurancaService {

    public static final String CONFIRMACAO_EXCLUSAO = "EXCLUIR MINHA CONTA";

    private final CredencialUsuarioRepository credenciais;
    private final TokenSegurancaRepository tokens;
    private final AuditoriaEventoRepository auditorias;
    private final PasswordEncoder encoder;
    private final PublicSessionRegistry sessions;
    private final PublicAuthRateLimiter rateLimiter;
    private final AdminUsuarioExclusaoService exclusaoService;

    public MinhaContaSegurancaService(
            CredencialUsuarioRepository credenciais,
            TokenSegurancaRepository tokens,
            AuditoriaEventoRepository auditorias,
            PasswordEncoder encoder,
            PublicSessionRegistry sessions,
            PublicAuthRateLimiter rateLimiter,
            AdminUsuarioExclusaoService exclusaoService) {
        this.credenciais = credenciais;
        this.tokens = tokens;
        this.auditorias = auditorias;
        this.encoder = encoder;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.exclusaoService = exclusaoService;
    }

    @Transactional
    public PublicAccountActionDto alterarSenha(
            MinhaContaAlterarSenhaRequestDto request,
            Authentication authentication,
            String requestId) {
        return alterarSenhaDoUsuario(
                request,
                usuarioId(authentication),
                requestId);
    }

    @Transactional
    public PublicAccountActionDto alterarSenhaAdministrativa(
            MinhaContaAlterarSenhaRequestDto request,
            Authentication authentication,
            String requestId) {
        return alterarSenhaDoUsuario(
                request,
                usuarioIdAdministrativo(authentication),
                requestId);
    }

    private PublicAccountActionDto alterarSenhaDoUsuario(
            MinhaContaAlterarSenhaRequestDto request,
            UUID usuarioId,
            String requestId) {
        rateLimiter.require(
                "minha-conta-senha",
                usuarioId.toString(),
                10,
                Duration.ofMinutes(15));
        if (request == null) {
            throw passwordBadRequest("Preencha os dados obrigatórios.");
        }
        var credencial = credenciais.findByUsuarioIdForUpdate(usuarioId)
                .orElseThrow(this::senhaAtualIncorreta);
        if (request.senhaAtual() == null
                || !encoder.matches(request.senhaAtual(), credencial.getSenhaHash())) {
            throw senhaAtualIncorreta();
        }
        try {
            PublicPasswordPolicy.validate(request.novaSenha(), request.confirmarSenha());
        } catch (PublicAuthException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().toLowerCase(java.util.Locale.ROOT)
                    .contains("requisitos de seguran")) {
                throw passwordBadRequest("A nova senha não atende aos requisitos de segurança.");
            }
            throw exception;
        }
        if (encoder.matches(request.novaSenha(), credencial.getSenhaHash())) {
            throw passwordBadRequest("A nova senha deve ser diferente da senha atual.");
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        credencial.redefinir(encoder.encode(request.novaSenha()), agora);
        tokens.findTodosAtivosForUpdate(usuarioId).forEach(token -> token.consumir(agora));
        auditorias.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                usuarioId,
                "USUARIO_ALTEROU_SENHA",
                "USUARIO",
                usuarioId,
                "{\"credencialAlterada\":false,\"dadosPessoaisOcultos\":true}",
                "{\"credencialAlterada\":true,\"tokensRevogados\":true,\"dadosPessoaisOcultos\":true}",
                requestId,
                agora));
        invalidarSessoesAposCommit(usuarioId);
        return new PublicAccountActionDto(
                "Sua senha foi alterada com sucesso. Entre novamente.");
    }

    @Transactional(readOnly = true)
    public AdminUsuarioExclusaoElegibilidadeDto elegibilidade(Authentication authentication) {
        return exclusaoService.elegibilidade(usuarioId(authentication));
    }

    @Transactional
    public AdminUsuarioExclusaoResultadoDto excluir(
            MinhaContaExcluirRequestDto request,
            String idempotencyKey,
            Authentication authentication,
            String requestId) {
        UUID usuarioId = usuarioId(authentication);
        rateLimiter.require(
                "minha-conta-exclusao",
                usuarioId.toString(),
                5,
                Duration.ofMinutes(30));
        if (request == null
                || !CONFIRMACAO_EXCLUSAO.equals(request.confirmacao())
                || !request.cienteConsequencias()) {
            throw badRequest(
                    "Confirme as consequências e digite EXCLUIR MINHA CONTA.");
        }
        var credencial = credenciais.findByUsuarioIdForUpdate(usuarioId)
                .orElseThrow(() -> unauthorized("A senha atual está incorreta."));
        if (request.senhaAtual() == null
                || !encoder.matches(request.senhaAtual(), credencial.getSenhaHash())) {
            throw unauthorized("A senha atual está incorreta.");
        }
        return exclusaoService.excluirPeloProprioUsuario(
                usuarioId,
                idempotencyKey,
                requestId);
    }

    private UUID usuarioId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PublicUserPrincipal principal)) {
            throw unauthorized("Sessão autenticada obrigatória.");
        }
        return principal.usuarioId();
    }

    private UUID usuarioIdAdministrativo(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminUserPrincipal principal)) {
            throw unauthorized("Sessão administrativa autenticada obrigatória.");
        }
        return principal.usuarioId();
    }

    private void invalidarSessoesAposCommit(UUID usuarioId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sessions.invalidateAll(usuarioId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessions.invalidateAll(usuarioId);
            }
        });
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private PublicAuthException senhaAtualIncorreta() {
        return new PublicAuthException(HttpStatus.UNAUTHORIZED, "Senha atual incorreta.");
    }

    private PublicAuthException passwordBadRequest(String message) {
        return new PublicAuthException(HttpStatus.BAD_REQUEST, message);
    }
}
