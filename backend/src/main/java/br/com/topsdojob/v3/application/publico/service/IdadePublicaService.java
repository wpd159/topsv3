package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.ConfirmarIdadePublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.StatusIdadePublicaDto;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
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
    private final MetricaPublicaHashService hashService;
    private final EventoVerificacaoEtariaRepository eventoRepository;

    public IdadePublicaService(
            IdadePublicaTokenService tokenService,
            MetricaPublicaHashService hashService,
            EventoVerificacaoEtariaRepository eventoRepository) {
        this.tokenService = tokenService;
        this.hashService = hashService;
        this.eventoRepository = eventoRepository;
    }

    public ConfirmacaoIdadeResult confirmar(
            ConfirmarIdadePublicaRequestDto request,
            HttpServletRequest httpRequest) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ResultadoVerificacaoEtaria resultado = validarRequest(request);
        registrar(resultado, httpRequest, now);
        if (resultado != ResultadoVerificacaoEtaria.PERMITIDO) {
            String motivo = resultado == ResultadoVerificacaoEtaria.NEGADO
                    ? "idade minima nao confirmada"
                    : "confirmacao de idade invalida";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, motivo);
        }
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

    private ResultadoVerificacaoEtaria validarRequest(ConfirmarIdadePublicaRequestDto request) {
        if (request == null
                || request.dataNascimento() == null
                || !Boolean.TRUE.equals(request.declaracaoMaioridade())) {
            return ResultadoVerificacaoEtaria.INDETERMINADO;
        }
        LocalDate limite = LocalDate.now(ZoneOffset.UTC).minusYears(IDADE_MINIMA);
        if (request.dataNascimento().isAfter(limite)) {
            return ResultadoVerificacaoEtaria.NEGADO;
        }
        return ResultadoVerificacaoEtaria.PERMITIDO;
    }

    private void registrar(
            ResultadoVerificacaoEtaria resultado,
            HttpServletRequest request,
            OffsetDateTime now) {
        eventoRepository.save(EventoVerificacaoEtariaEntity.registrar(
                resultado,
                hashService.hash("ip", request == null ? null : request.getRemoteAddr()),
                hashService.hash("user-agent", request == null ? null : request.getHeader("User-Agent")),
                request == null ? "" : RequestIdContext.current(request),
                now));
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
