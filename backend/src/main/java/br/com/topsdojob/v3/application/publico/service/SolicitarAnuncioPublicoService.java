package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioPublicoRequestDto;
import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioValidationErrorDto;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.DocumentoBuscaAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

@Service
public class SolicitarAnuncioPublicoService {

    static final String WHATSAPP_SINTETICO_PERMITIDO = "+5500000000000";
    private static final int TITULO_MAX = 80;
    private static final int DESCRICAO_MAX = 600;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "whatsapp",
            "uf",
            "cidade",
            "bairro",
            "titulo",
            "descricao",
            "preco",
            "categoria",
            "servicos",
            "atendimentoExclusivamenteVirtual",
            "aceiteTermos",
            "confirmacaoIdade");
    private static final Set<String> DANGEROUS_FIELDS = Set.of(
            "id",
            "anuncioId",
            "usuarioId",
            "status",
            "statusModeracao",
            "visibilidadeMidia",
            "publicadoEm",
            "premium",
            "premiumObrigatorio",
            "pagamentoId",
            "creditoId",
            "pix",
            "efi",
            "storageKey",
            "arquivo",
            "foto",
            "video",
            "documento",
            "cpf",
            "cnpj",
            "payload",
            "papel",
            "role");
    private static final Pattern PHONE_OR_SOCIAL_IN_TITLE = Pattern.compile(
            "(?i)(\\+?\\d[\\d .()_-]{7,}\\d|whats|telefone|instagram|insta\\b|telegram|t\\.me|onlyfans|facebook|http|www\\.|@)");
    private static final Pattern SPAM_IN_TITLE = Pattern.compile("(?i)(clique aqui|imperdivel|urgente|100%|gratis gratis)");

    private final MeusAnunciosConsultaService usuarioService;
    private final KycPublicoService kycService;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final DocumentoBuscaAnuncioRepository documentoBuscaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final ObjectMapper objectMapper;

    public SolicitarAnuncioPublicoService(
            MeusAnunciosConsultaService usuarioService,
            KycPublicoService kycService,
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            DocumentoBuscaAnuncioRepository documentoBuscaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            ObjectMapper objectMapper) {
        this.usuarioService = usuarioService;
        this.kycService = kycService;
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.documentoBuscaRepository = documentoBuscaRepository;
        this.revisaoRepository = revisaoRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SolicitarAnuncioPublicoResponseDto solicitar(JsonNode payload, Authentication authentication) {
        UsuarioEntity usuario = usuarioService.usuarioAutenticado(authentication);
        kycService.garantirProntoParaAnuncio(usuario.getId());
        ValidatedRequest validated = validar(payload);
        OffsetDateTime now = OffsetDateTime.now();

        UUID anuncioId = UUID.randomUUID();
        String slug = nextSlug(validated.titulo());
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuario.getId(),
                slug,
                validated.titulo(),
                validated.descricao(),
                validated.categoria(),
                validated.preco(),
                validated.whatsapp(),
                validated.servicos(),
                validated.atendimentoExclusivamenteVirtual(),
                now);
        anuncioRepository.save(anuncio);
        localizacaoRepository.save(AnuncioLocalizacaoEntity.criarSolicitacaoLocal(
                anuncioId,
                validated.estado().getId(),
                validated.cidadeEntity().getId(),
                validated.bairroEntity() == null ? null : validated.bairroEntity().getId(),
                now));
        documentoBuscaRepository.save(DocumentoBuscaAnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                textoBusca(validated),
                validated.estado().getId(),
                validated.cidadeEntity().getId(),
                validated.bairroEntity() == null ? null : validated.bairroEntity().getId(),
                validated.categoria(),
                validated.preco(),
                now));

        UUID revisaoId = UUID.randomUUID();
        revisaoRepository.save(RevisaoAnuncioEntity.abrir(
                revisaoId,
                anuncioId,
                TipoRevisaoAnuncio.CRIACAO,
                payloadSolicitado(validated),
                usuario.getId(),
                now));

        return new SolicitarAnuncioPublicoResponseDto(
                true,
                anuncioId,
                revisaoId,
                slug,
                StatusAnuncio.PENDENTE_REVISAO.name(),
                StatusModeracaoAnuncio.PENDENTE.name(),
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                "solicitacao local criada para revisao");
    }

    ValidatedRequest validar(JsonNode payload) {
        List<SolicitarAnuncioValidationErrorDto> errors = new ArrayList<>();
        if (payload == null || payload.isNull() || !payload.isObject()) {
            errors.add(error("payload", "PAYLOAD_INVALIDO", "payload deve ser um objeto JSON"));
            throw new SolicitarAnuncioValidationException(errors);
        }

        Iterator<String> fieldNames = payload.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (DANGEROUS_FIELDS.contains(field)) {
                errors.add(error(field, "CAMPO_PERIGOSO", "campo nao pode ser enviado neste fluxo local"));
            } else if (!ALLOWED_FIELDS.contains(field)) {
                errors.add(error(field, "CAMPO_NAO_PERMITIDO", "campo nao aceito no contrato local"));
            }
        }

        SolicitarAnuncioPublicoRequestDto request = null;
        try {
            request = objectMapper.treeToValue(payload, SolicitarAnuncioPublicoRequestDto.class);
        } catch (JsonProcessingException exception) {
            errors.add(error("payload", "PAYLOAD_INVALIDO", "payload nao corresponde ao contrato local"));
        }
        if (request == null) {
            throw new SolicitarAnuncioValidationException(errors);
        }

        String whatsapp = syntheticWhatsapp(request.whatsapp(), errors);
        String uf = requiredText(request.uf(), "uf", 2, 2, errors).toUpperCase(Locale.ROOT);
        String cidade = requiredText(request.cidade(), "cidade", 3, 80, errors);
        String bairro = optionalText(request.bairro(), 2, 80, "bairro", errors);
        String titulo = requiredText(request.titulo(), "titulo", 10, TITULO_MAX, errors);
        String descricao = requiredText(request.descricao(), "descricao", 20, DESCRICAO_MAX, errors);
        String categoria = categoria(request.categoria(), errors);
        Set<ServicoAnuncio> servicos = servicos(request.servicos(), errors);
        if (CategoriaAnuncio.VENDA_DE_CONTEUDO.name().equals(categoria)) {
            categoria = CategoriaAnuncio.ACOMPANHANTE_FEMININA.name();
            Set<ServicoAnuncio> normalizados = new java.util.LinkedHashSet<>(servicos);
            normalizados.add(ServicoAnuncio.VIDEOCHAMADA);
            servicos = Set.copyOf(normalizados);
        }
        boolean atendimentoExclusivamenteVirtual =
                Boolean.TRUE.equals(request.atendimentoExclusivamenteVirtual());
        if (atendimentoExclusivamenteVirtual
                && !servicos.contains(ServicoAnuncio.VIDEOCHAMADA)) {
            errors.add(error(
                    "atendimentoExclusivamenteVirtual",
                    "EXCLUSIVIDADE_VIRTUAL_INVALIDA",
                    "atendimento exclusivamente virtual exige o servico VIDEOCHAMADA"));
        }
        BigDecimal preco = preco(request.preco(), errors);

        if (titulo != null && PHONE_OR_SOCIAL_IN_TITLE.matcher(titulo).find()) {
            errors.add(error("titulo", "TITULO_CONTATO_OU_REDE_SOCIAL", "titulo nao pode conter telefone, WhatsApp ou rede social"));
        }
        if (titulo != null && SPAM_IN_TITLE.matcher(titulo).find()) {
            errors.add(error("titulo", "TITULO_SPAM", "titulo contem padrao de spam evidente"));
        }
        if (!Boolean.TRUE.equals(request.aceiteTermos())) {
            errors.add(error("aceiteTermos", "ACEITE_TERMOS_OBRIGATORIO", "aceite de termos e obrigatorio"));
        }
        if (!Boolean.TRUE.equals(request.confirmacaoIdade())) {
            errors.add(error("confirmacaoIdade", "CONFIRMACAO_IDADE_OBRIGATORIA", "confirmacao local de idade e obrigatoria"));
        }
        if (uf != null && !uf.matches("[A-Z]{2}")) {
            errors.add(error("uf", "UF_INVALIDA", "uf deve conter duas letras"));
        }

        if (!errors.isEmpty()) {
            throw new SolicitarAnuncioValidationException(errors);
        }

        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(uf)
                .orElseGet(() -> {
                    errors.add(error("uf", "LOCALIDADE_SINTETICA_NAO_ENCONTRADA", "uf sintetica local nao encontrada"));
                    return null;
                });
        CidadeEntity cidadeEntity = estado == null ? null : cidadeRepository.findByEstadoIdAndSlug(estado.getId(), slugify(cidade))
                .orElseGet(() -> {
                    errors.add(error("cidade", "LOCALIDADE_SINTETICA_NAO_ENCONTRADA", "cidade sintetica local nao encontrada"));
                    return null;
                });
        BairroEntity bairroEntity = null;
        if (cidadeEntity != null && bairro != null) {
            bairroEntity = bairroRepository.findByCidadeIdAndSlug(cidadeEntity.getId(), slugify(bairro))
                    .orElseGet(() -> {
                        errors.add(error("bairro", "LOCALIDADE_SINTETICA_NAO_ENCONTRADA", "bairro sintetico local nao encontrado"));
                        return null;
                    });
        }
        if (!errors.isEmpty()) {
            throw new SolicitarAnuncioValidationException(errors);
        }

        return new ValidatedRequest(
                whatsapp,
                uf,
                cidade,
                bairro,
                titulo,
                descricao,
                preco,
                categoria,
                servicos,
                atendimentoExclusivamenteVirtual,
                estado,
                cidadeEntity,
                bairroEntity);
    }

    private String nextSlug(String title) {
        String base = slugify(title);
        if (base.length() > 70) {
            base = base.substring(0, 70).replaceAll("-+$", "");
        }
        for (int index = 0; index < 20; index++) {
            String candidate = base + "-solicitacao-" + UUID.randomUUID().toString().substring(0, 8);
            if (!anuncioRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        return base + "-solicitacao-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String payloadSolicitado(ValidatedRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("origem", "ANUNCIE_GRATIS_LOCAL");
        payload.put("titulo", request.titulo());
        payload.put("uf", request.uf());
        payload.put("cidade", request.cidade());
        payload.put("bairroInformado", request.bairro() != null);
        payload.put("statusInicial", StatusAnuncio.PENDENTE_REVISAO.name());
        payload.put("statusModeracaoInicial", StatusModeracaoAnuncio.PENDENTE.name());
        payload.put("categoria", request.categoria());
        payload.put("servicos", request.servicos().stream().map(Enum::name).sorted().toList());
        payload.put("atendimentoExclusivamenteVirtual", request.atendimentoExclusivamenteVirtual());
        payload.put("uploadRealExecutado", false);
        payload.put("pagamentoCriado", false);
        payload.put("creditoCriado", false);
        payload.put("premiumObrigatorio", false);
        payload.put("publicacaoAutomaticaExecutada", false);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String textoBusca(ValidatedRequest request) {
        return (request.titulo() + " " + request.descricao() + " " + request.cidade() + " "
                + (request.bairro() == null ? "" : request.bairro())).toLowerCase(Locale.ROOT);
    }

    private String requiredText(
            String value,
            String field,
            int min,
            int max,
            List<SolicitarAnuncioValidationErrorDto> errors) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            errors.add(error(field, "CAMPO_OBRIGATORIO", field + " deve ser informado"));
            return null;
        }
        if (sanitized.length() < min || sanitized.length() > max) {
            errors.add(error(field, "TAMANHO_INVALIDO", field + " deve ter entre " + min + " e " + max + " caracteres"));
        }
        return sanitized;
    }

    private String optionalText(
            String value,
            int min,
            int max,
            String field,
            List<SolicitarAnuncioValidationErrorDto> errors) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            return null;
        }
        if (sanitized.length() < min || sanitized.length() > max) {
            errors.add(error(field, "TAMANHO_INVALIDO", field + " deve ter entre " + min + " e " + max + " caracteres"));
        }
        return sanitized;
    }

    private String syntheticWhatsapp(String value, List<SolicitarAnuncioValidationErrorDto> errors) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            errors.add(error("whatsapp", "WHATSAPP_OBRIGATORIO", "WhatsApp sintetico local deve ser informado"));
            return null;
        }
        String normalized = sanitized.replaceAll("[^0-9+]", "");
        if (!normalized.startsWith("+") && normalized.matches("[0-9]+")) {
            normalized = "+" + normalized;
        }
        if (!WHATSAPP_SINTETICO_PERMITIDO.equals(normalized)) {
            errors.add(error("whatsapp", "WHATSAPP_NAO_SINTETICO", "usar somente WhatsApp sintetico local permitido"));
        }
        return normalized;
    }

    private BigDecimal preco(BigDecimal value, List<SolicitarAnuncioValidationErrorDto> errors) {
        if (value == null) {
            errors.add(error("preco", "PRECO_OBRIGATORIO", "preco sintetico deve ser informado"));
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(error("preco", "PRECO_INVALIDO", "preco deve ser maior que zero"));
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String categoria(String value, List<SolicitarAnuncioValidationErrorDto> errors) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            errors.add(error("categoria", "CATEGORIA_OBRIGATORIA", "categoria deve ser informada"));
            return null;
        }
        CategoriaAnuncio categoria = CategoriaAnuncio.porCodigo(sanitized).orElse(null);
        if (categoria == null) {
            errors.add(error("categoria", "CATEGORIA_INVALIDA", "categoria invalida"));
            return null;
        }
        return categoria.name();
    }

    private Set<ServicoAnuncio> servicos(
            List<String> values,
            List<SolicitarAnuncioValidationErrorDto> errors) {
        if (values == null) {
            return Set.of();
        }
        Set<ServicoAnuncio> resultado = new java.util.LinkedHashSet<>();
        for (String value : values) {
            try {
                resultado.add(ServicoAnuncio.valueOf(
                        value == null ? "" : value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                errors.add(error("servicos", "SERVICO_INVALIDO", "servicos contem valor invalido"));
            }
        }
        return Set.copyOf(resultado);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private String slugify(String value) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            return "solicitacao-local";
        }
        String normalized = Normalizer.normalize(sanitized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
        return normalized.isBlank() ? "solicitacao-local" : normalized;
    }

    private SolicitarAnuncioValidationErrorDto error(String field, String code, String message) {
        return new SolicitarAnuncioValidationErrorDto(field, code, message);
    }

    record ValidatedRequest(
            String whatsapp,
            String uf,
            String cidade,
            String bairro,
            String titulo,
            String descricao,
            BigDecimal preco,
            String categoria,
            Set<ServicoAnuncio> servicos,
            boolean atendimentoExclusivamenteVirtual,
            EstadoEntity estado,
            CidadeEntity cidadeEntity,
            BairroEntity bairroEntity) {
    }
}
