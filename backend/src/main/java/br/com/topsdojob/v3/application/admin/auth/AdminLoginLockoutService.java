package br.com.topsdojob.v3.application.admin.auth;

import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminLoginLockoutService {

    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private static final String LOGIN_SCOPE = "admin-login";
    private static final String IP_SCOPE = "admin-login-ip";
    private static final String BLANK_LOGIN = "__blank_login__";
    private static final String UNKNOWN_IP = "__unknown_ip__";

    private final MetricaPublicaHashService hashService;
    private final Clock clock;
    private final ConcurrentMap<String, AttemptBucket> attempts = new ConcurrentHashMap<>();

    @Autowired
    public AdminLoginLockoutService(MetricaPublicaHashService hashService) {
        this(hashService, Clock.systemUTC());
    }

    AdminLoginLockoutService(MetricaPublicaHashService hashService, Clock clock) {
        this.hashService = hashService;
        this.clock = clock;
    }

    public LoginAttemptContext context(String normalizedLogin, HttpServletRequest request) {
        String loginValue = normalizedLogin == null || normalizedLogin.isBlank() ? BLANK_LOGIN : normalizedLogin;
        String ipValue = remoteAddress(request);
        return new LoginAttemptContext(
                key(LOGIN_SCOPE, hashService.hash(LOGIN_SCOPE, loginValue)),
                key(IP_SCOPE, hashService.hash(IP_SCOPE, ipValue)));
    }

    public boolean isBlocked(LoginAttemptContext context) {
        Instant now = clock.instant();
        return isBlocked(context.loginKey(), now) || isBlocked(context.ipKey(), now);
    }

    public void registerFailure(LoginAttemptContext context) {
        Instant now = clock.instant();
        registerFailure(context.loginKey(), now);
        registerFailure(context.ipKey(), now);
    }

    public void registerSuccess(LoginAttemptContext context) {
        attempts.remove(context.loginKey());
        attempts.remove(context.ipKey());
    }

    private boolean isBlocked(String key, Instant now) {
        AttemptBucket bucket = attempts.get(key);
        if (bucket == null) {
            return false;
        }
        if (bucket.blockedUntil() != null && bucket.blockedUntil().isAfter(now)) {
            return true;
        }
        if (bucket.blockedUntil() != null && !bucket.blockedUntil().isAfter(now)) {
            attempts.remove(key, bucket);
        }
        return false;
    }

    private void registerFailure(String key, Instant now) {
        attempts.compute(key, (ignored, current) -> {
            AttemptBucket bucket = current == null || current.firstFailureAt().plus(FAILURE_WINDOW).isBefore(now)
                    ? new AttemptBucket(0, now, null)
                    : current;
            int failures = bucket.failures() + 1;
            Instant blockedUntil = failures >= MAX_FAILURES ? now.plus(BLOCK_DURATION) : bucket.blockedUntil();
            return new AttemptBucket(failures, bucket.firstFailureAt(), blockedUntil);
        });
    }

    private String key(String scope, String hash) {
        return scope + ":" + hash;
    }

    private String remoteAddress(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return UNKNOWN_IP;
        }
        return request.getRemoteAddr().trim();
    }

    public record LoginAttemptContext(String loginKey, String ipKey) {
    }

    private record AttemptBucket(int failures, Instant firstFailureAt, Instant blockedUntil) {
    }
}
