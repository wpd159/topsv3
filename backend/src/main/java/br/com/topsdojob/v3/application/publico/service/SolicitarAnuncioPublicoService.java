package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioPublicoRequestDto;
import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioValidationErrorDto;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.application.wizard.WizardCreationSessionPolicy;
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
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.wizard.WizardProgressJdbcRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

@Service
public class SolicitarAnuncioPublicoService {

    private static final int TITULO_MAX = 80;
    private static final int DESCRICAO_MAX = 500;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "uf",
            "cidade",
            "bairro",
            "enderecoResumido",
            "titulo",
            "descricao",
            "preco",
            "categoria",
            "servicos",
            "linkConteudo",
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
    private final AnuncioMidiaRepository midiaRepository;
    private final WizardProgressJdbcRepository wizardRepository;

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
            ObjectMapper objectMapper,
            AnuncioMidiaRepository midiaRepository,
            WizardProgressJdbcRepository wizardRepository) {
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
        this.midiaRepository = midiaRepository;
        this.wizardRepository = wizardRepository;
    }

    @Transactional
    public SolicitarAnuncioPublicoResponseDto solicitar(JsonNode payload, Authentication authentication) {
        return solicitar(payload, authentication, null);
    }

    @Transactional
    public SolicitarAnuncioPublicoResponseDto solicitar(
            JsonNode payload, Authentication authentication, String sessionId) {
        UsuarioEntity usuario = usuarioService.usuarioAutenticadoParaAtualizacao(authentication);
        String sessaoId = sessionId == null ? null : WizardCreationSessionPolicy.sessionId(sessionId);
        if (sessaoId != null) {
            var sessao = wizardRepository.findSessaoPorUsuario(usuario.getId(), sessaoId).orElse(null);
            if (sessao != null) {
                WizardCreationSessionPolicy.exigirCreate(sessao.modo());
                if (sessao.anuncioId() != null) {
                    AnuncioEntity existente = anuncioRepository.findByIdForModeration(sessao.anuncioId())
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.CONFLICT, "vinculo da sessao invalido"));
                    WizardCreationSessionPolicy.anuncioRecuperavel(existente, usuario.getId());
                    return resposta(existente, false);
                }
            }
        }
        kycService.garantirProntoParaAnuncio(usuario.getId());
        ValidatedRequest validated = validar(payload, usuario.getTelefoneNormalizado());
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
                validated.linkConteudo(),
                now);
        anuncio.aplicarModeracao(StatusAnuncio.RASCUNHO, StatusModeracaoAnuncio.NAO_ENVIADO, now);
        anuncioRepository.save(anuncio);
        localizacaoRepository.save(AnuncioLocalizacaoEntity.criarSolicitacaoLocal(
                anuncioId,
                validated.estado().getId(),
                validated.cidadeEntity().getId(),
                validated.bairroEntity() == null ? null : validated.bairroEntity().getId(),
                validated.enderecoResumido(),
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

        if (sessaoId != null) {
            // JDBC shares this transaction; materialize the new FK before binding the session.
            anuncioRepository.flush();
            var row = wizardRepository.sincronizar(UUID.randomUUID(), sessaoId, usuario.getId(), anuncioId,
                    "CREATE", "FOTOS", 3, "EM_PREENCHIMENTO", now);
            WizardCreationSessionPolicy.exigirCreate(row.modo());
        }
        return resposta(anuncio, true);
    }

    private SolicitarAnuncioPublicoResponseDto resposta(AnuncioEntity anuncio, boolean criado) {
        return new SolicitarAnuncioPublicoResponseDto(
                criado,
                anuncio.getId(),
                null,
                anuncio.getSlug(),
                anuncio.getStatus().name(),
                anuncio.getStatusModeracao().name(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                criado ? "rascunho criado; envie ao menos uma foto para encaminhar a revisao"
                        : "anuncio da sessao recuperado; nenhuma nova criacao executada");
    }

    /** Chamado pelo upload confirmado, na mesma transacao e sob o lock do proprietario/anuncio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void concluirCriacaoAposUpload(AnuncioEntity anuncio) {
        if (!anuncio.criacaoNaoEnviada()) return;
        midiaRepository.flush();
        if (midiaRepository.findFotosValidasAtivasIds(anuncio.getId()).isEmpty()) return;
        if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                anuncio.getId(), List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "criacao ja possui revisao aberta");
        }
        AnuncioLocalizacaoEntity localizacao = localizacaoRepository.findByAnuncioId(anuncio.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "localizacao da criacao ausente"));
        EstadoEntity estado = estadoRepository.findById(localizacao.getEstadoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "estado da criacao ausente"));
        CidadeEntity cidade = cidadeRepository.findById(localizacao.getCidadeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "cidade da criacao ausente"));
        OffsetDateTime agora = OffsetDateTime.now();
        revisaoRepository.save(RevisaoAnuncioEntity.abrir(
                UUID.randomUUID(), anuncio.getId(), TipoRevisaoAnuncio.CRIACAO,
                payloadSolicitado(anuncio, localizacao, estado, cidade), anuncio.getUsuarioId(), agora));
        anuncio.remeterParaRevisao(agora);
        anuncioRepository.saveAndFlush(anuncio);
    }

    ValidatedRequest validar(JsonNode payload, String telefoneDaConta) {
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

        String whatsapp = telefoneDaConta(telefoneDaConta, errors);
        String uf = requiredText(request.uf(), "uf", 2, 2, errors).toUpperCase(Locale.ROOT);
        String cidade = requiredText(request.cidade(), "cidade", 3, 80, errors);
        String bairro = optionalText(request.bairro(), 2, 80, "bairro", errors);
        String enderecoResumido = optionalText(
                request.enderecoResumido(), 2, 120, "enderecoResumido", errors);
        if (enderecoResumido != null && contemConteudoAtivo(enderecoResumido)) {
            errors.add(error(
                    "enderecoResumido",
                    "CONTEUDO_NAO_PERMITIDO",
                    "complemento nao pode conter HTML ou JavaScript"));
        }
        String titulo = requiredText(request.titulo(), "titulo", 10, TITULO_MAX, errors);
        if (titulo != null && contemConteudoAtivo(titulo)) {
            errors.add(error(
                    "titulo",
                    "CONTEUDO_NAO_PERMITIDO",
                    "titulo nao pode conter HTML ou JavaScript"));
        }
        String descricao = requiredText(request.descricao(), "descricao", 20, DESCRICAO_MAX, errors);
        String categoria = categoria(request.categoria(), errors);
        Set<ServicoAnuncio> servicos = servicos(request.servicos(), errors);
        String linkConteudo = linkConteudo(request.linkConteudo(), errors);
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
                enderecoResumido,
                titulo,
                descricao,
                preco,
                categoria,
                servicos,
                atendimentoExclusivamenteVirtual,
                linkConteudo,
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

    private String payloadSolicitado(
            AnuncioEntity anuncio, AnuncioLocalizacaoEntity localizacao, EstadoEntity estado, CidadeEntity cidade) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("origem", "ANUNCIE_GRATIS_LOCAL");
        payload.put("titulo", anuncio.getTitulo());
        payload.put("uf", estado.getUf());
        payload.put("cidade", cidade.getNome());
        payload.put("bairroInformado", localizacao.getBairroId() != null);
        payload.put("enderecoResumidoInformado", localizacao.getEnderecoResumido() != null);
        payload.put("statusInicial", anuncio.getStatus().name());
        payload.put("statusModeracaoInicial", anuncio.getStatusModeracao().name());
        payload.put("categoria", anuncio.getCategoria());
        payload.put("servicos", anuncio.getServicos().stream().map(Enum::name).sorted().toList());
        payload.put("atendimentoExclusivamenteVirtual", anuncio.isAtendimentoExclusivamenteVirtual());
        payload.put("linkConteudoInformado", anuncio.getLinkConteudo() != null);
        payload.put("uploadRealExecutado", true);
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
                + (request.bairro() == null ? "" : request.bairro()) + " "
                + (request.enderecoResumido() == null ? "" : request.enderecoResumido()))
                .toLowerCase(Locale.ROOT);
    }

    private boolean contemConteudoAtivo(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return value.indexOf('<') >= 0
                || value.indexOf('>') >= 0
                || normalized.matches(".*\\bjavascript\\s*:.*");
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

    private String telefoneDaConta(String value, List<SolicitarAnuncioValidationErrorDto> errors) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            errors.add(error(
                    "telefone",
                    "TELEFONE_DA_CONTA_OBRIGATORIO",
                    "Cadastre seu telefone em Minha Conta antes de criar um anuncio."));
            return null;
        }
        String normalized = sanitized.replaceAll("[^0-9+]", "");
        if (!normalized.startsWith("+") && normalized.matches("[0-9]+")) {
            normalized = "+" + normalized;
        }
        if (!normalized.matches("\\+[1-9][0-9]{7,14}")) {
            errors.add(error(
                    "telefone",
                    "TELEFONE_DA_CONTA_INVALIDO",
                    "Atualize seu telefone em Minha conta antes de criar um anuncio."));
        }
        return normalized;
    }

    private String linkConteudo(
            String value,
            List<SolicitarAnuncioValidationErrorDto> errors) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            return null;
        }
        if (sanitized.length() > 2048) {
            errors.add(error("linkConteudo", "LINK_CONTEUDO_INVALIDO", "link de conteudo invalido"));
            return sanitized;
        }
        try {
            URI uri = URI.create(sanitized);
            if (uri.getHost() == null
                    || (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme()))) {
                errors.add(error("linkConteudo", "LINK_CONTEUDO_INVALIDO", "link de conteudo invalido"));
            }
        } catch (IllegalArgumentException exception) {
            errors.add(error("linkConteudo", "LINK_CONTEUDO_INVALIDO", "link de conteudo invalido"));
        }
        return sanitized;
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
            String enderecoResumido,
            String titulo,
            String descricao,
            BigDecimal preco,
            String categoria,
            Set<ServicoAnuncio> servicos,
            boolean atendimentoExclusivamenteVirtual,
            String linkConteudo,
            EstadoEntity estado,
            CidadeEntity cidadeEntity,
            BairroEntity bairroEntity) {
    }
}
