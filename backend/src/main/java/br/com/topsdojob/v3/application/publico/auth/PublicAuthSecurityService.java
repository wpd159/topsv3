package br.com.topsdojob.v3.application.publico.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PublicAuthSecurityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicAuthSecurityService.class);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(15);
    private static final int LOGIN_IDENTIFIER_LIMIT = 5;
    private static final int LOGIN_IP_LIMIT = 50;
    private static final int DUPLICATE_IP_LIMIT = 20;

    private final PublicClientIpResolver clientIpResolver;
    private final PublicAuthRateLimiter rateLimiter;

    public PublicAuthSecurityService(
            PublicClientIpResolver clientIpResolver,
            PublicAuthRateLimiter rateLimiter) {
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
    }

    public LoginAttempt beginLogin(HttpServletRequest request, String normalizedIdentifier) {
        LoginAttempt attempt = new LoginAttempt(
                clientKey(request),
                normalizeIdentifier(normalizedIdentifier));
        try {
            rateLimiter.check("login-ip", attempt.clientIp(), LOGIN_IP_LIMIT, LOGIN_WINDOW);
            rateLimiter.check("login-client-id", attempt.combinedKey(), LOGIN_IDENTIFIER_LIMIT, LOGIN_WINDOW);
            return attempt;
        } catch (PublicAuthException exception) {
            audit("RATE_LIMITED", attempt);
            throw exception;
        }
    }

    public void rejectLogin(LoginAttempt attempt) {
        audit("REJECTED", attempt);
        rateLimiter.recordFailure("login-ip", attempt.clientIp(), LOGIN_IP_LIMIT, LOGIN_WINDOW);
        rateLimiter.recordFailure(
                "login-client-id",
                attempt.combinedKey(),
                LOGIN_IDENTIFIER_LIMIT,
                LOGIN_WINDOW);
    }

    public void acceptLogin(LoginAttempt attempt) {
        rateLimiter.reset("login-client-id", attempt.combinedKey());
        audit("ACCEPTED", attempt);
    }

    public void requireDuplicateLookup(HttpServletRequest request) {
        String clientIp = clientKey(request);
        try {
            rateLimiter.require("duplicate-check", clientIp, DUPLICATE_IP_LIMIT, DUPLICATE_WINDOW);
        } catch (PublicAuthException exception) {
            LOGGER.warn("public_auth_security operation=DUPLICATE_CHECK outcome=RATE_LIMITED client_ref={}",
                    auditReference(clientIp));
            throw exception;
        }
    }

    public String clientKey(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private void audit(String outcome, LoginAttempt attempt) {
        LOGGER.info(
                "public_auth_security operation=LOGIN outcome={} client_ref={} identifier_ref={}",
                outcome,
                auditReference(attempt.clientIp()),
                auditReference(attempt.normalizedIdentifier()));
    }

    static String auditReference(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "unknown" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "invalid";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record LoginAttempt(String clientIp, String normalizedIdentifier) {
        String combinedKey() {
            return clientIp + '|' + normalizedIdentifier;
        }
    }
}
