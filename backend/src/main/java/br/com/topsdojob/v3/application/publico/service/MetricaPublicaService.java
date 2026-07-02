package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoRequestDto;
import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.PoliticaContatoPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaResponseDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DispositivoMetrica;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MetricaPublicaService {

    private static final String STATUS_REGISTRADO = "REGISTRADO";
    private static final String STATUS_CONTATO_INDISPONIVEL = "CONTATO_INDISPONIVEL";

    private final AnuncioRepository anuncioRepository;
    private final EventoVisualizacaoRepository eventoVisualizacaoRepository;
    private final CliqueWhatsappRepository cliqueWhatsappRepository;
    private final MetricaPublicaHashService hashService;
    private final PoliticaContatoPublicoService politicaContatoService;
    private final IdadePublicaService idadeService;

    public MetricaPublicaService(
            AnuncioRepository anuncioRepository,
            EventoVisualizacaoRepository eventoVisualizacaoRepository,
            CliqueWhatsappRepository cliqueWhatsappRepository,
            MetricaPublicaHashService hashService,
            PoliticaContatoPublicoService politicaContatoService,
            IdadePublicaService idadeService) {
        this.anuncioRepository = anuncioRepository;
        this.eventoVisualizacaoRepository = eventoVisualizacaoRepository;
        this.cliqueWhatsappRepository = cliqueWhatsappRepository;
        this.hashService = hashService;
        this.politicaContatoService = politicaContatoService;
        this.idadeService = idadeService;
    }

    @Transactional
    public RegistrarVisualizacaoPublicaResponseDto registrarVisualizacao(
            String slug,
            RegistrarVisualizacaoPublicaRequestDto request,
            HttpServletRequest httpRequest) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        boolean idadeConfirmada = idadeService.idadeConfirmada(httpRequest);
        AnuncioEntity anuncio = buscarAnuncioPublico(slugSeguro, idadeConfirmada);
        DadosTecnicos dados = dadosTecnicos(request, httpRequest);
        EventoVisualizacaoEntity evento = EventoVisualizacaoEntity.registrar(
                UUID.randomUUID(),
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
        EventoVisualizacaoEntity salvo = eventoVisualizacaoRepository.save(evento);
        return new RegistrarVisualizacaoPublicaResponseDto(
                true,
                anuncio.getSlug(),
                salvo.getId().toString(),
                STATUS_REGISTRADO,
                MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    @Transactional
    public CliqueWhatsappPublicoResponseDto registrarCliqueWhatsapp(
            String slug,
            CliqueWhatsappPublicoRequestDto request,
            HttpServletRequest httpRequest) {
        String slugSeguro = RotaPublicaGuard.slug(slug, "slug");
        boolean idadeConfirmada = idadeService.idadeConfirmada(httpRequest);
        AnuncioEntity anuncio = buscarAnuncioPublico(slugSeguro, idadeConfirmada);
        PoliticaContatoPublicoDto politica = politicaContatoService.avaliar(anuncio, idadeConfirmada);
        DadosTecnicos dados = dadosTecnicos(request, httpRequest);
        CliqueWhatsappEntity clique = CliqueWhatsappEntity.registrar(
                UUID.randomUUID(),
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
        cliqueWhatsappRepository.save(clique);

        return new CliqueWhatsappPublicoResponseDto(
                true,
                politica.disponivel(),
                politica.disponivel() ? politicaContatoService.whatsappUrl(anuncio, idadeConfirmada) : null,
                politica.disponivel() ? STATUS_REGISTRADO : STATUS_CONTATO_INDISPONIVEL,
                politica,
                MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    private AnuncioEntity buscarAnuncioPublico(String slug, boolean idadeConfirmada) {
        return anuncioRepository
                .findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                        slug,
                        StatusAnuncio.PUBLICADO,
                        StatusModeracaoAnuncio.APROVADO)
                .filter(anuncio -> classificacaoPublicavel(anuncio.getClassificacaoConteudo(), idadeConfirmada))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    }

    private boolean classificacaoPublicavel(ClassificacaoConteudo classificacao, boolean idadeConfirmada) {
        return classificacao == ClassificacaoConteudo.LIVRE
                || (idadeConfirmada && classificacao == ClassificacaoConteudo.BLOQUEADO);
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
