package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.ConfirmarIdadePublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.StatusIdadePublicaDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdadePublicaService {

    private static final int IDADE_MINIMA = 18;
    private static final String MOTIVO_CONFIRMADA = "IDADE_CONFIRMADA";
    private static final String MOTIVO_NAO_CONFIRMADA = "IDADE_NAO_CONFIRMADA";

    private final IdadePublicaTokenService tokenService;

    public IdadePublicaService(IdadePublicaTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public ConfirmacaoIdadeResult confirmar(ConfirmarIdadePublicaRequestDto request) {
        validarRequest(request);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String idadeCookieValor = tokenService.emitir(now);
        Optional<OffsetDateTime> expiresAt = tokenService.validar(idadeCookieValor, now);
        ResponseCookie cookie = ResponseCookie.from(IdadePublicaTokenService.COOKIE_NAME, idadeCookieValor)
                .httpOnly(true)
                .secure(tokenService.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(tokenService.ttl())
                .build();
        return new ConfirmacaoIdadeResult(
                new StatusIdadePublicaDto(true, expiresAt.orElse(now.plus(tokenService.ttl())), MOTIVO_CONFIRMADA),
                cookie);
    }

    public StatusIdadePublicaDto status(HttpServletRequest request) {
        Optional<OffsetDateTime> expiresAt = tokenService.validar(cookieValue(request), OffsetDateTime.now(ZoneOffset.UTC));
        if (expiresAt.isEmpty()) {
            return new StatusIdadePublicaDto(false, null, MOTIVO_NAO_CONFIRMADA);
        }
        return new StatusIdadePublicaDto(true, expiresAt.get(), MOTIVO_CONFIRMADA);
    }

    public boolean idadeConfirmada(HttpServletRequest request) {
        return status(request).confirmada();
    }

    private void validarRequest(ConfirmarIdadePublicaRequestDto request) {
        if (request == null
                || request.dataNascimento() == null
                || !Boolean.TRUE.equals(request.declaracaoMaioridade())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirmacao de idade invalida");
        }
        LocalDate limite = LocalDate.now(ZoneOffset.UTC).minusYears(IDADE_MINIMA);
        if (request.dataNascimento().isAfter(limite)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idade minima nao confirmada");
        }
    }

    private String cookieValue(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> IdadePublicaTokenService.COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public record ConfirmacaoIdadeResult(
            StatusIdadePublicaDto status,
            ResponseCookie cookie) {
    }
}
