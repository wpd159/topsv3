package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.DocumentoBuscaAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeuAnuncioAtualizacaoService {

    private static final int TITULO_MAX = 80;
    private static final int DESCRICAO_MAX = 600;
    private static final Pattern CONTATO_NO_TITULO = Pattern.compile(
            "(?i)(\\+?\\d[\\d .()_-]{7,}\\d|whats|telefone|instagram|insta\\b|telegram|t\\.me|onlyfans|facebook|http|www\\.|@)");

    private final MeusAnunciosConsultaService consultaService;
    private final KycPublicoService kycService;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final DocumentoBuscaAnuncioRepository documentoBuscaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final ObjectMapper objectMapper;

    public MeuAnuncioAtualizacaoService(
            MeusAnunciosConsultaService consultaService,
            KycPublicoService kycService,
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            DocumentoBuscaAnuncioRepository documentoBuscaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            ObjectMapper objectMapper) {
        this.consultaService = consultaService;
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
    public MeuAnuncioDto atualizar(
            String slug,
            MeuAnuncioAtualizacaoRequestDto request,
            Authentication authentication) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuario(slug, authentication);
        kycService.garantirProntoParaAnuncio(anuncio.getUsuarioId());
        ValidatedRequest validado = validar(request);
        if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                anuncio.getId(),
                List.of(StatusRevisaoAnuncio.EM_ANALISE))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "anuncio possui revisao em analise e nao pode ser alterado agora");
        }

        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(validado.uf())
                .orElseThrow(() -> badRequest("uf nao encontrada"));
        CidadeEntity cidade = cidadeRepository.findByEstadoIdAndSlug(estado.getId(), slugify(validado.cidade()))
                .orElseThrow(() -> badRequest("cidade nao encontrada para a uf informada"));
        BairroEntity bairro = validado.bairro() == null
                ? null
                : bairroRepository.findByCidadeIdAndSlug(cidade.getId(), slugify(validado.bairro()))
                        .orElseThrow(() -> badRequest("bairro nao encontrado para a cidade informada"));

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        anuncio.atualizarPeloProprietario(
                validado.titulo(),
                validado.descricao(),
                validado.categoria(),
                validado.preco(),
                validado.whatsapp(),
                validado.locaisAtendimento(),
                validado.servicos(),
                agora);
        anuncioRepository.save(anuncio);

        AnuncioLocalizacaoEntity localizacao = localizacaoRepository.findByAnuncioId(anuncio.getId()).orElse(null);
        if (localizacao == null) {
            localizacao = AnuncioLocalizacaoEntity.criarEdicaoProprietario(
                    anuncio.getId(), estado.getId(), cidade.getId(), bairro == null ? null : bairro.getId(), agora);
        } else {
            localizacao.atualizarLocalidade(
                    estado.getId(), cidade.getId(), bairro == null ? null : bairro.getId(), agora);
        }
        localizacaoRepository.save(localizacao);

        DocumentoBuscaAnuncioEntity documento = documentoBuscaRepository.findById(anuncio.getId()).orElse(null);
        if (documento == null) {
            documento = DocumentoBuscaAnuncioEntity.criarSolicitacaoLocal(
                    anuncio.getId(),
                    textoBusca(validado),
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    validado.categoria(),
                    validado.preco(),
                    agora);
        } else {
            documento.atualizarAposEdicao(
                    textoBusca(validado),
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    validado.categoria(),
                    validado.preco(),
                    agora);
        }
        documentoBuscaRepository.save(documento);

        String payload = payloadRevisao(validado, estado, cidade, bairro);
        RevisaoAnuncioEntity revisaoAberta = revisaoRepository
                .findFirstByAnuncioIdAndStatusOrderByCriadoEmDesc(
                        anuncio.getId(), StatusRevisaoAnuncio.ABERTA)
                .orElse(null);
        if (revisaoAberta == null) {
            revisaoRepository.save(RevisaoAnuncioEntity.abrir(
                    UUID.randomUUID(),
                    anuncio.getId(),
                    TipoRevisaoAnuncio.EDICAO,
                    payload,
                    anuncio.getUsuarioId(),
                    agora));
        } else {
            revisaoAberta.atualizarSolicitacaoAberta(payload);
            revisaoRepository.save(revisaoAberta);
        }

        return consultaService.detalhar(anuncio.getSlug(), authentication);
    }

    private ValidatedRequest validar(MeuAnuncioAtualizacaoRequestDto request) {
        if (request == null) {
            throw badRequest("payload obrigatorio");
        }
        String titulo = textoObrigatorio(request.titulo(), "titulo", 10, TITULO_MAX);
        if (CONTATO_NO_TITULO.matcher(titulo).find()) {
            throw badRequest("titulo nao pode conter contato, rede social ou URL");
        }
        String descricao = textoObrigatorio(request.descricao(), "descricao", 20, DESCRICAO_MAX);
        String categoria = CategoriaAnuncio.porCodigo(request.categoria())
                .map(Enum::name)
                .orElseThrow(() -> badRequest("categoria invalida"));
        BigDecimal preco = request.preco();
        if (preco != null) {
            if (preco.compareTo(BigDecimal.ZERO) <= 0 || preco.compareTo(new BigDecimal("999999.99")) > 0) {
                throw badRequest("preco invalido");
            }
            preco = preco.setScale(2, RoundingMode.HALF_UP);
        }
        String uf = textoObrigatorio(request.uf(), "uf", 2, 2).toUpperCase(Locale.ROOT);
        if (!uf.matches("[A-Z]{2}")) {
            throw badRequest("uf invalida");
        }
        String cidade = textoObrigatorio(request.cidade(), "cidade", 2, 80);
        String bairro = textoOpcional(request.bairro(), "bairro", 2, 80);
        Set<LocalAtendimentoAnuncio> locais = enums(
                request.locaisAtendimento(), LocalAtendimentoAnuncio.class, "locaisAtendimento");
        Set<ServicoAnuncio> servicos = enums(request.servicos(), ServicoAnuncio.class, "servicos");
        String whatsapp = whatsapp(request.whatsapp());
        return new ValidatedRequest(
                titulo, descricao, categoria, preco, uf, cidade, bairro, locais, servicos, whatsapp);
    }

    private String textoObrigatorio(String value, String campo, int minimo, int maximo) {
        String texto = sanitize(value);
        if (texto == null || texto.length() < minimo || texto.length() > maximo) {
            throw badRequest(campo + " deve ter entre " + minimo + " e " + maximo + " caracteres");
        }
        return texto;
    }

    private String textoOpcional(String value, String campo, int minimo, int maximo) {
        String texto = sanitize(value);
        if (texto != null && (texto.length() < minimo || texto.length() > maximo)) {
            throw badRequest(campo + " deve ter entre " + minimo + " e " + maximo + " caracteres");
        }
        return texto;
    }

    private String whatsapp(String value) {
        String texto = sanitize(value);
        if (texto == null) {
            return null;
        }
        String normalizado = texto.replaceAll("[^0-9+]", "");
        if (!normalizado.startsWith("+") && normalizado.matches("[0-9]+")) {
            normalizado = "+" + normalizado;
        }
        if (!normalizado.matches("\\+[1-9][0-9]{7,14}")) {
            throw badRequest("whatsapp invalido");
        }
        return normalizado;
    }

    private <E extends Enum<E>> Set<E> enums(List<String> values, Class<E> enumType, String campo) {
        if (values == null) {
            return Set.of();
        }
        Set<E> resultado = new LinkedHashSet<>();
        for (String value : values) {
            try {
                resultado.add(Enum.valueOf(enumType, value == null ? "" : value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw badRequest(campo + " contem valor invalido");
            }
        }
        return Set.copyOf(resultado);
    }

    private String payloadRevisao(
            ValidatedRequest request,
            EstadoEntity estado,
            CidadeEntity cidade,
            BairroEntity bairro) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("titulo", request.titulo());
        payload.put("descricao", request.descricao());
        payload.put("categoria", request.categoria());
        payload.put("preco", request.preco());
        payload.put("uf", estado.getUf());
        payload.put("cidade", cidade.getSlug());
        payload.put("bairro", bairro == null ? null : bairro.getSlug());
        payload.put("locaisAtendimento", request.locaisAtendimento().stream().map(Enum::name).sorted().toList());
        payload.put("servicos", request.servicos().stream().map(Enum::name).sorted().toList());
        payload.put("contatoInformado", request.whatsapp() != null);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falha ao preparar revisao");
        }
    }

    private String textoBusca(ValidatedRequest request) {
        return String.join(
                " ",
                request.titulo(),
                request.descricao(),
                request.cidade(),
                request.bairro() == null ? "" : request.bairro())
                .toLowerCase(Locale.ROOT);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return sanitized.isEmpty() ? null : sanitized;
    }

    private String slugify(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ValidatedRequest(
            String titulo,
            String descricao,
            String categoria,
            BigDecimal preco,
            String uf,
            String cidade,
            String bairro,
            Set<LocalAtendimentoAnuncio> locaisAtendimento,
            Set<ServicoAnuncio> servicos,
            String whatsapp) {
    }
}
