package br.com.topsdojob.v3.application.denuncia;

import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.CriarDenunciaRequest;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.CriarDenunciaResponse;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DenunciaPublicaService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,160}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "(?is)<\\s*script\\b[^>]*>.*?<\\s*/\\s*script\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<\\s*/?\\s*[a-z][^>]*>");
    private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
    private static final Set<String> MOTIVOS = Set.of(
            "CONTEUDO_INADEQUADO",
            "PERFIL_FALSO",
            "GOLPE",
            "SPAM",
            "OUTROS");

    private final AnuncioRepository anuncioRepository;
    private final UsuarioRepository usuarioRepository;
    private final DenunciaJdbcRepository repository;
    private final PublicAuthRateLimiter rateLimiter;
    private final MetricaPublicaHashService hashService;
    private final AuditoriaEventoRepository auditoriaRepository;

    public DenunciaPublicaService(
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            DenunciaJdbcRepository repository,
            PublicAuthRateLimiter rateLimiter,
            MetricaPublicaHashService hashService,
            AuditoriaEventoRepository auditoriaRepository) {
        this.anuncioRepository = anuncioRepository;
        this.usuarioRepository = usuarioRepository;
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.hashService = hashService;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional
    public CriarDenunciaResponse criar(
            CriarDenunciaRequest request,
            String idempotencyKey,
            Authentication authentication,
            HttpServletRequest httpRequest,
            String requestId) {
        UUID anuncioId = request == null ? null : request.anuncioId();
        AnuncioEntity anuncio = anuncioPublico(anuncioId);
        String motivo = motivo(request == null ? null : request.motivo());
        String descricao = descricao(request == null ? null : request.descricao());
        String chave = idempotencyKey(idempotencyKey);
        String requestSeguro = requestId(requestId);
        UUID denuncianteId = usuario(authentication).map(UsuarioEntity::getId).orElse(null);
        String ipHash = hashService.hash("denuncia-ip", remoteAddress(httpRequest));
        String userAgentHash = hashService.hash("denuncia-user-agent", header(httpRequest, "User-Agent"));
        String contextoHash = hashService.hash(
                "denuncia-contexto",
                denuncianteId == null
                        ? "anonimo|" + Objects.toString(ipHash, "") + "|" + Objects.toString(userAgentHash, "")
                        : "usuario|" + denuncianteId);

        Optional<DenunciaJdbcRepository.DenunciaRow> repetida =
                repository.porIdempotencia(anuncio.getId(), contextoHash, chave);
        if (repetida.isPresent()) {
            validarRepetida(repetida.get(), motivo, descricao);
            return resposta(repetida.get(), true);
        }
        limitar(contextoHash);

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID denunciaId = uuid(
                "denuncia-publica-v1:" + anuncio.getId() + ":" + contextoHash + ":" + chave);
        int inseridos = repository.inserir(
                denunciaId,
                anuncio.getId(),
                denuncianteId,
                contextoHash,
                motivo,
                descricao,
                chave,
                requestSeguro,
                ipHash,
                userAgentHash,
                agora);
        DenunciaJdbcRepository.DenunciaRow row =
                repository.porIdempotencia(anuncio.getId(), contextoHash, chave)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "denuncia nao localizada"));
        if (inseridos == 0) {
            validarRepetida(row, motivo, descricao);
            return resposta(row, true);
        }
        auditarCriacao(row, requestSeguro, agora);
        return resposta(row, false);
    }

    private AnuncioEntity anuncioPublico(UUID anuncioId) {
        if (anuncioId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "anuncio obrigatorio");
        }
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "anuncio nao encontrado"));
        if (anuncio.getRemovidoEm() != null
                || anuncio.getStatus() != StatusAnuncio.PUBLICADO
                || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.APROVADO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado");
        }
        return anuncio;
    }

    private Optional<UsuarioEntity> usuario(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmailNormalizado(
                Objects.toString(authentication.getName(), "").trim().toLowerCase(Locale.ROOT));
    }

    private void validarRepetida(
            DenunciaJdbcRepository.DenunciaRow row,
            String motivo,
            String descricao) {
        if (!motivo.equals(row.motivo()) || !Objects.equals(descricao, row.descricao())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key reutilizada com outra denuncia");
        }
    }

    private CriarDenunciaResponse resposta(
            DenunciaJdbcRepository.DenunciaRow row,
            boolean repetida) {
        return new CriarDenunciaResponse(
                row.id(),
                AdminDenunciaService.protocolo(row.id()),
                row.status(),
                row.criadoEm(),
                repetida);
    }

    private void auditarCriacao(
            DenunciaJdbcRepository.DenunciaRow row,
            String requestId,
            OffsetDateTime agora) {
        String acao = "DENUNCIA_CRIADA";
        if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(acao, row.id(), requestId)) {
            return;
        }
        auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
                UUID.randomUUID(),
                row.denuncianteUsuarioId(),
                acao,
                "DENUNCIA_ANUNCIO",
                row.id(),
                "{}",
                "{\"status\":\"PENDENTE\",\"motivo\":\"" + row.motivo()
                        + "\",\"anuncioId\":\"" + row.anuncioId() + "\"}",
                requestId,
                agora));
    }

    private String motivo(String valor) {
        String motivo = Objects.requireNonNullElse(valor, "").trim().toUpperCase(Locale.ROOT);
        if (!MOTIVOS.contains(motivo)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "motivo invalido");
        }
        return motivo;
    }

    private String descricao(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = Normalizer.normalize(valor, Normalizer.Form.NFKC);
        texto = texto.replace("\r\n", "\n").replace('\r', '\n');
        texto = SCRIPT_BLOCK.matcher(texto).replaceAll("");
        texto = HTML_TAG.matcher(texto).replaceAll("");
        texto = UNSAFE_CONTROL.matcher(texto).replaceAll("");
        texto = texto.trim();
        if (texto.isEmpty()) {
            return null;
        }
        if (texto.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "descricao invalida");
        }
        return texto;
    }

    private String idempotencyKey(String valor) {
        String chave = Objects.requireNonNullElse(valor, "").trim();
        if (!IDEMPOTENCY_KEY.matcher(chave).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
        }
        return chave;
    }

    private String requestId(String valor) {
        String requestId = Objects.requireNonNullElse(valor, "").trim();
        if (!REQUEST_ID.matcher(requestId).matches()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "requestId invalido");
        }
        return requestId;
    }

    private void limitar(String contextoHash) {
        try {
            rateLimiter.require("denuncia-criar", contextoHash, 5, Duration.ofMinutes(10));
        } catch (PublicAuthException exception) {
            throw new ResponseStatusException(exception.status(), exception.getMessage());
        }
    }

    private String remoteAddress(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private UUID uuid(String valor) {
        return UUID.nameUUIDFromBytes(valor.getBytes(StandardCharsets.UTF_8));
    }
}
