package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class PublicAuthRateLimiterTest {

    private ch.qos.logback.classic.Logger securityLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @AfterEach
    void detachLogAppender() {
        if (securityLogger != null && logAppender != null) {
            securityLogger.detachAppender(logAppender);
        }
    }

    @Test
    void informaRetryAfterERecuperaDepoisDaJanela() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
        PublicAuthRateLimiter limiter = new PublicAuthRateLimiter(clock);

        limiter.require("confirm", "client-a", 2, Duration.ofMinutes(1));
        limiter.require("confirm", "client-a", 2, Duration.ofMinutes(1));

        assertThatThrownBy(() -> limiter.require("confirm", "client-a", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(PublicAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.retryAfterSeconds()).isEqualTo(60L);
                });

        clock.advance(Duration.ofSeconds(60));
        assertThatCode(() -> limiter.require("confirm", "client-a", 2, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void falhasSaoIsoladasPorIpEIdentificador() {
        PublicAuthSecurityService security = new PublicAuthSecurityService(
                new PublicClientIpResolver("127.0.0.0/8,::1/128"),
                new PublicAuthRateLimiter());
        var firstIp = request("198.51.100.60");
        var secondIp = request("198.51.100.61");

        for (int attempt = 0; attempt < 4; attempt++) {
            PublicAuthSecurityService.LoginAttempt login = security.beginLogin(firstIp, " user@example.invalid ");
            security.rejectLogin(login);
        }
        PublicAuthSecurityService.LoginAttempt fifth = security.beginLogin(firstIp, "USER@EXAMPLE.INVALID");
        assertThatThrownBy(() -> security.rejectLogin(fifth))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        assertThatCode(() -> security.beginLogin(secondIp, "user@example.invalid"))
                .doesNotThrowAnyException();
    }

    @Test
    void referenciaDeAuditoriaNaoExpoeIdentificador() {
        String identifier = "user@example.invalid";
        String reference = PublicAuthSecurityService.auditReference(identifier);

        assertThat(reference).hasSize(16).doesNotContain("user", "example", "@");
        assertThat(reference).isEqualTo(PublicAuthSecurityService.auditReference(identifier));
    }

    @Test
    void limitaConsultaDeDuplicidadePorIpERecuperaDepoisDaJanela() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
        PublicAuthSecurityService security = new PublicAuthSecurityService(
                new PublicClientIpResolver("127.0.0.0/8,::1/128"),
                new PublicAuthRateLimiter(clock));
        var request = request("198.51.100.70");

        for (int attempt = 0; attempt < 20; attempt++) {
            security.requireDuplicateLookup(request);
        }

        assertThatThrownBy(() -> security.requireDuplicateLookup(request))
                .isInstanceOfSatisfying(PublicAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.retryAfterSeconds()).isEqualTo(900L);
                });

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> security.requireDuplicateLookup(request)).doesNotThrowAnyException();
    }

    @Test
    void auditoriaNaoRegistraIpOuIdentificadorIntegrais() {
        securityLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PublicAuthSecurityService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        securityLogger.addAppender(logAppender);
        PublicAuthSecurityService security = new PublicAuthSecurityService(
                new PublicClientIpResolver("127.0.0.0/8,::1/128"),
                new PublicAuthRateLimiter());
        String ip = "198.51.100.80";
        String identifier = "full.identifier@example.invalid";

        security.rejectLogin(security.beginLogin(request(ip), identifier));

        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain(ip, identifier, "full.identifier", "example.invalid")
                        .contains("client_ref=", "identifier_ref="));
    }

    @Test
    void mesmoIpCompartilhaLimiteIpSemMisturarLimitePorIdentificador() {
        PublicAuthSecurityService security = new PublicAuthSecurityService(
                new PublicClientIpResolver("127.0.0.0/8,::1/128"),
                new PublicAuthRateLimiter());
        var sharedIp = request("198.51.100.90");

        for (String identifier : new String[] {"first@example.invalid", "second@example.invalid"}) {
            for (int attempt = 0; attempt < 4; attempt++) {
                security.rejectLogin(security.beginLogin(sharedIp, identifier));
            }
            assertThatCode(() -> security.beginLogin(sharedIp, identifier)).doesNotThrowAnyException();
        }

        for (int attempt = 0; attempt < 41; attempt++) {
            String identifier = "spread-" + attempt + "@example.invalid";
            security.rejectLogin(security.beginLogin(sharedIp, identifier));
        }

        PublicAuthSecurityService.LoginAttempt fiftieth =
                security.beginLogin(sharedIp, "spread-final@example.invalid");
        assertThatThrownBy(() -> security.rejectLogin(fiftieth))
                .isInstanceOfSatisfying(PublicAuthException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThatCode(() -> security.beginLogin(request("198.51.100.91"), "first@example.invalid"))
                .doesNotThrowAnyException();
    }

    @Test
    void novaInstanciaAposRestartComecaSemBucketsDeTentativas() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
        PublicAuthRateLimiter beforeRestart = new PublicAuthRateLimiter(clock);
        beforeRestart.require("confirm", "client-a", 1, Duration.ofMinutes(15));
        assertThatThrownBy(() -> beforeRestart.require("confirm", "client-a", 1, Duration.ofMinutes(15)))
                .isInstanceOf(PublicAuthException.class);

        PublicAuthRateLimiter afterRestart = new PublicAuthRateLimiter(clock);
        assertThatCode(() -> afterRestart.require("confirm", "client-a", 1, Duration.ofMinutes(15)))
                .doesNotThrowAnyException();
    }

    private org.springframework.mock.web.MockHttpServletRequest request(String address) {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
