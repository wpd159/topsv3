package br.com.topsdojob.v3.application.publico.auth;

import br.com.topsdojob.v3.application.publico.auth.dto.PublicAccountActionDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicCodeRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicEmailRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicResetPasswordRequestDto;
import br.com.topsdojob.v3.application.operacional.outbox.OutboxEmailPayloadFactory;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.TokenSegurancaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.TokenSegurancaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicAccountLifecycleService {
    static final String CONFIRMATION = "CONFIRMACAO_EMAIL";
    static final String RESET = "RECUPERACAO_SENHA";
    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern SYMBOL = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    private static final Pattern COMMON = Pattern.compile("(1234|abcd|senha|password|qwerty)", Pattern.CASE_INSENSITIVE);
    private static final String GENERIC_MESSAGE = "Se houver uma conta elegivel, enviaremos as instrucoes ao e-mail informado.";

    private final UsuarioRepository usuarios;
    private final CredencialUsuarioRepository credenciais;
    private final TokenSegurancaRepository tokens;
    private final OutboxEventoRepository outbox;
    private final OutboxEmailPayloadFactory emailPayloads;
    private final PasswordEncoder encoder;
    private final PublicAuthRateLimiter rateLimiter;
    private final PublicSessionRegistry sessions;
    private final Optional<HmlAuthCodeVault> codeVault;
    private final Duration tokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PublicAccountLifecycleService(UsuarioRepository usuarios, CredencialUsuarioRepository credenciais,
            TokenSegurancaRepository tokens, OutboxEventoRepository outbox, PasswordEncoder encoder,
            PublicAuthRateLimiter rateLimiter, PublicSessionRegistry sessions, Optional<HmlAuthCodeVault> codeVault,
            OutboxEmailPayloadFactory emailPayloads,
            @Value("${app.auth.security-token-ttl-seconds:900}") long tokenTtlSeconds) {
        this.usuarios = usuarios;
        this.credenciais = credenciais;
        this.tokens = tokens;
        this.outbox = outbox;
        this.emailPayloads = emailPayloads;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.sessions = sessions;
        this.codeVault = codeVault;
        this.tokenTtl = Duration.ofSeconds(Math.max(1, tokenTtlSeconds));
    }

    @Transactional
    public void issueInitialConfirmation(UsuarioEntity usuario) {
        issue(usuario, CONFIRMATION, "AUTH_CONFIRMACAO_CONTA_SOLICITADA");
    }

    @Transactional
    public PublicAccountActionDto resend(PublicEmailRequestDto request, String clientKey) {
        String email = validEmail(request);
        rateLimiter.require("resend", clientKey + ':' + email, 3, Duration.ofMinutes(15));
        usuarios.findByEmailNormalizado(email)
                .filter(user -> user.getEmailVerificadoEm() == null)
                .ifPresent(user -> issue(user, CONFIRMATION, "AUTH_CONFIRMACAO_CONTA_REENVIADA"));
        return new PublicAccountActionDto(GENERIC_MESSAGE);
    }

    @Transactional
    public PublicAccountActionDto forgot(PublicEmailRequestDto request, String clientKey) {
        String email = validEmail(request);
        rateLimiter.require("forgot", clientKey + ':' + email, 3, Duration.ofMinutes(15));
        usuarios.findByEmailNormalizado(email)
                .filter(user -> user.getDesativadoEm() == null)
                .ifPresent(user -> issue(user, RESET, "AUTH_RECUPERACAO_SENHA_SOLICITADA"));
        return new PublicAccountActionDto(GENERIC_MESSAGE);
    }

    @Transactional(noRollbackFor = PublicAuthException.class)
    public PublicAccountActionDto confirm(PublicCodeRequestDto request, String clientKey) {
        rateLimiter.require("confirm", clientKey + ':' + normalize(request == null ? null : request.email()), 10, Duration.ofMinutes(15));
        UsuarioEntity user = user(request == null ? null : request.email());
        consumeValid(user, CONFIRMATION, request == null ? null : request.codigo());
        if (user.getEmailVerificadoEm() == null) user.confirmarEmail(now());
        return new PublicAccountActionDto("Conta confirmada com sucesso.");
    }

    @Transactional(noRollbackFor = PublicAuthException.class)
    public PublicAccountActionDto validateReset(PublicCodeRequestDto request, String clientKey) {
        rateLimiter.require("validate-reset", clientKey + ':' + normalize(request == null ? null : request.email()), 10, Duration.ofMinutes(15));
        UsuarioEntity user = user(request == null ? null : request.email());
        validateCurrent(user, RESET, request == null ? null : request.codigo());
        return new PublicAccountActionDto("Codigo valido.");
    }

    @Transactional(noRollbackFor = PublicAuthException.class)
    public PublicAccountActionDto reset(PublicResetPasswordRequestDto request, String clientKey) {
        rateLimiter.require("reset", clientKey + ':' + normalize(request == null ? null : request.email()), 10, Duration.ofMinutes(15));
        if (request == null) throw invalidToken();
        validatePassword(request.novaSenha(), request.confirmarSenha());
        UsuarioEntity user = user(request.email());
        consumeValid(user, RESET, request.codigo());
        var credential = credenciais.findByUsuarioId(user.getId()).orElseThrow(this::invalidToken);
        credential.redefinir(encoder.encode(request.novaSenha()), now());
        sessions.invalidateAll(user.getId());
        return new PublicAccountActionDto("Senha redefinida com sucesso.");
    }

    private void issue(UsuarioEntity user, String purpose, String eventType) {
        OffsetDateTime now = now();
        tokens.findAtivosForUpdate(user.getId(), purpose).forEach(token -> token.consumir(now));
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        TokenSegurancaEntity securityRecord = TokenSegurancaEntity.criar(user.getId(), purpose,
                encoder.encode(code), now.plus(tokenTtl), now);
        tokens.save(securityRecord);
        String payload = emailPayloads.auth(
                eventType,
                user.getId(),
                securityRecord.getId(),
                mask(user.getEmailNormalizado()),
                code,
                securityRecord.getExpiraEm());
        UUID outboxId = UUID.randomUUID();
        outbox.save(OutboxEventoEntity.registrarPendente(outboxId, "USUARIO", user.getId(), eventType,
                payload, eventType + ":" + securityRecord.getId(), now));
        codeVault.ifPresent(vault -> vault.put(outboxId, code, securityRecord.getExpiraEm()));
    }

    private void consumeValid(UsuarioEntity user, String purpose, String code) {
        TokenSegurancaEntity securityRecord = validateCurrent(user, purpose, code);
        securityRecord.consumir(now());
    }

    private TokenSegurancaEntity validateCurrent(UsuarioEntity user, String purpose, String code) {
        List<TokenSegurancaEntity> active = tokens.findAtivosForUpdate(user.getId(), purpose);
        if (active.isEmpty()) throw invalidToken();
        TokenSegurancaEntity securityRecord = active.get(0);
        OffsetDateTime now = now();
        if (securityRecord.getExpiraEm().isBefore(now) || securityRecord.getTentativas() >= MAX_ATTEMPTS) {
            securityRecord.consumir(now);
            throw invalidToken();
        }
        if (code == null || !code.matches("\\d{6}") || !encoder.matches(code, securityRecord.getTokenHash())) {
            securityRecord.registrarTentativa();
            if (securityRecord.getTentativas() >= MAX_ATTEMPTS) securityRecord.consumir(now);
            throw invalidToken();
        }
        return securityRecord;
    }

    private UsuarioEntity user(String email) {
        String normalized = normalize(email);
        if (!EMAIL.matcher(normalized).matches()) throw invalidToken();
        return usuarios.findByEmailNormalizado(normalized).orElseThrow(this::invalidToken);
    }

    private String validEmail(PublicEmailRequestDto request) {
        String email = normalize(request == null ? null : request.email());
        if (!EMAIL.matcher(email).matches()) throw new PublicAuthException(HttpStatus.BAD_REQUEST, "E-mail invalido.");
        return email;
    }

    private void validatePassword(String candidate, String confirmation) {
        if (candidate == null || candidate.length() < 8 || !candidate.matches(".*[A-Z].*")
                || !candidate.matches(".*[a-z].*") || !candidate.matches(".*[0-9].*")
                || !SYMBOL.matcher(candidate).find() || COMMON.matcher(candidate).find())
            throw new PublicAuthException(HttpStatus.BAD_REQUEST, "Senha nao atende aos requisitos de seguranca.");
        if (!candidate.equals(confirmation))
            throw new PublicAuthException(HttpStatus.BAD_REQUEST, "Confirmacao de senha invalida.");
    }

    private PublicAuthException invalidToken() {
        return new PublicAuthException(HttpStatus.BAD_REQUEST, "Codigo invalido, expirado ou ja utilizado.");
    }
    private String normalize(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private String mask(String email) {
        int at = email.indexOf('@');
        return at < 1 ? "***" : email.substring(0, 1) + "***" + email.substring(at);
    }
}
