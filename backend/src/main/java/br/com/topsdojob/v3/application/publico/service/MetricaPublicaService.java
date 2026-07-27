package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.PoliticaContatoPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaResponseDto;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DispositivoMetrica;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MetricaPublicaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricaPublicaService.class);
    private static final String STATUS_REGISTRADO = "REGISTRADO";
    private static final String STATUS_CONTATO_INDISPONIVEL = "CONTATO_INDISPONIVEL";
    private static final String STATUS_METRICA_INDISPONIVEL = "METRICA_INDISPONIVEL";

    private final AnuncioRepository anuncioRepository;
    private final MetricaPublicaPersistenceService persistenceService;
    private final MetricaPublicaHashService hashService;
    private final PoliticaContatoPublicoService politicaContatoService;
    private final ComplianceVisitorAccessService visitorAccessService;

    public MetricaPublicaService(
            AnuncioRepository anuncioRepository,
            MetricaPublicaPersistenceService persistenceService,
            MetricaPublicaHashService hashService,
            PoliticaContatoPublicoService politicaContatoService,
            ComplianceVisitorAccessService visitorAccessService) {
        this.anuncioRepository = anuncioRepository;
        this.persistenceService = persistenceService;
        this.hashService = hashService;
        this.politicaContatoService = politicaContatoService;
        this.visitorAccessService = visitorAccessService;
    }

    public RegistrarVisualizacaoPublicaResponseDto registrarVisualizacao(
            String slug,
            RegistrarVisualizacaoPublicaRequestDto request,
            String idempotencyKey,
            HttpServletRequest httpRequest) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        String chaveIdempotencia = chaveIdempotencia(idempotencyKey);
        AnuncioEntity anuncio = buscarAnuncioPublico(slugSeguro);
        DadosTecnicos dados = dadosTecnicos(request, httpRequest);
        EventoVisualizacaoEntity evento = EventoVisualizacaoEntity.registrar(
                eventoId("visualizacao", anuncio.getId(), chaveIdempotencia),
                anuncio.getId(),
                dados.visitanteHash(),
                dados.ipHash(),
                dados.userAgentHash(),
                hashService.hash("referer", header(httpRequest, "Referer")),
                dados.origemPais(),
                dados.origemUf(),
                dados.origemCidade(),
                dados.dispositivo(),
                RequestIdContext.current(httpRequest),
                OffsetDateTime.now(ZoneOffset.UTC));
        try {
            persistenceService.registrarVisualizacao(evento);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "metrica_publica_visualizacao_persistencia_falhou anuncioId={} requestId={} exception={}",
                    anuncio.getId(),
                    RequestIdContext.current(httpRequest),
                    exception.getClass().getName());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "metrica de visualizacao temporariamente indisponivel");
        }
        return new RegistrarVisualizacaoPublicaResponseDto(
                true,
                anuncio.getSlug(),
                evento.getId().toString(),
                STATUS_REGISTRADO,
                MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    public CliqueWhatsappPublicoResponseDto registrarCliqueWhatsapp(
            String slug,
            CliqueWhatsappPublicoRequestDto request,
            String idempotencyKey,
            HttpServletRequest httpRequest) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        String chaveIdempotencia = chaveIdempotencia(idempotencyKey);
        AnuncioEntity anuncio = buscarAnuncioPublico(slugSeguro);
        if (!visitorAccessService.autorizado(
                httpRequest,
                EscopoConteudoVisitante.WHATSAPP)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "verificacao reforcada necessaria");
        }
        PoliticaContatoPublicoDto politica = politicaContatoService.avaliar(anuncio);
        DadosTecnicos dados = dadosTecnicos(request, httpRequest);
        CliqueWhatsappEntity clique = CliqueWhatsappEntity.registrar(
                eventoId("clique-whatsapp", anuncio.getId(), chaveIdempotencia),
                anuncio.getId(),
                dados.visitanteHash(),
                dados.ipHash(),
                dados.userAgentHash(),
                dados.origemPais(),
                dados.origemUf(),
                dados.origemCidade(),
                dados.dispositivo(),
                politica.disponivel(),
                politica.disponivel() ? null : politica.motivoPublico(),
                RequestIdContext.current(httpRequest),
                OffsetDateTime.now(ZoneOffset.UTC));
        boolean registrado;
        try {
            persistenceService.registrarClique(clique);
            registrado = true;
        } catch (RuntimeException exception) {
            registrado = false;
            LOGGER.error(
                    "metrica_publica_clique_persistencia_falhou anuncioId={} requestId={} exception={}",
                    anuncio.getId(),
                    RequestIdContext.current(httpRequest),
                    exception.getClass().getName());
        }

        return new CliqueWhatsappPublicoResponseDto(
                registrado,
                politica.disponivel(),
                politica.disponivel() ? politicaContatoService.whatsappUrl(anuncio) : null,
                politica.disponivel()
                        ? (registrado ? STATUS_REGISTRADO : STATUS_METRICA_INDISPONIVEL)
                        : STATUS_CONTATO_INDISPONIVEL,
                politica,
                MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    private AnuncioEntity buscarAnuncioPublico(String slug) {
        return anuncioRepository
                .findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                        slug,
                        StatusAnuncio.PUBLICADO,
                        StatusModeracaoAnuncio.APROVADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    }

    private DadosTecnicos dadosTecnicos(RegistrarVisualizacaoPublicaRequestDto request, HttpServletRequest httpRequest) {
        RegistrarVisualizacaoPublicaRequestDto safe = request == null
                ? new RegistrarVisualizacaoPublicaRequestDto(null, null, null, null, null)
                : request;
        return new DadosTecnicos(
                hashService.hash("visitante", safe.visitanteLocalId()),
                hashService.hash("ip", remoteAddress(httpRequest)),
                hashService.hash("user-agent", header(httpRequest, "User-Agent")),
                normalizarPais(safe.origemPais()),
                normalizarUf(safe.origemUf()),
                normalizarCidade(safe.origemCidade()),
                dispositivo(safe.dispositivo(), header(httpRequest, "User-Agent")));
    }

    private DadosTecnicos dadosTecnicos(CliqueWhatsappPublicoRequestDto request, HttpServletRequest httpRequest) {
        CliqueWhatsappPublicoRequestDto safe = request == null
                ? new CliqueWhatsappPublicoRequestDto(null, null, null, null, null)
                : request;
        return new DadosTecnicos(
                hashService.hash("visitante", safe.visitanteLocalId()),
                hashService.hash("ip", remoteAddress(httpRequest)),
                hashService.hash("user-agent", header(httpRequest, "User-Agent")),
                normalizarPais(safe.origemPais()),
                normalizarUf(safe.origemUf()),
                normalizarCidade(safe.origemCidade()),
                dispositivo(safe.dispositivo(), header(httpRequest, "User-Agent")));
    }

    private String normalizarPais(String value) {
        return normalizarUfOuPais(value, "origemPais");
    }

    private String normalizarUf(String value) {
        return normalizarUfOuPais(value, "origemUf");
    }

    private String normalizarUfOuPais(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " invalido");
        }
        return normalized;
    }

    private String normalizarCidade(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "origemCidade invalida");
        }
        return normalized;
    }

    private DispositivoMetrica dispositivo(String informado, String userAgent) {
        if (informado != null && !informado.isBlank()) {
            try {
                return DispositivoMetrica.valueOf(informado.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dispositivo invalido");
            }
        }
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) {
            return DispositivoMetrica.BOT;
        }
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return DispositivoMetrica.TABLET;
        }
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return DispositivoMetrica.MOBILE;
        }
        if (!ua.isBlank()) {
            return DispositivoMetrica.DESKTOP;
        }
        return DispositivoMetrica.DESCONHECIDO;
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private String remoteAddress(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    private String chaveIdempotencia(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
        }
        return normalized;
    }

    private UUID eventoId(String tipo, UUID anuncioId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes(
                ("metrica-publica-v1:" + tipo + ":" + anuncioId + ":" + idempotencyKey)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private record DadosTecnicos(
            String visitanteHash,
            String ipHash,
            String userAgentHash,
            String origemPais,
            String origemUf,
            String origemCidade,
            DispositivoMetrica dispositivo) {
    }
}
