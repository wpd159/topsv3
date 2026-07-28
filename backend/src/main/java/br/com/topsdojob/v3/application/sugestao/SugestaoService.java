package br.com.topsdojob.v3.application.sugestao;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.AlterarStatusRequest;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Criacao;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.CriarRequest;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Detalhe;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Historico;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Indicadores;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Pagina;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Resumo;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SugestaoService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,160}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "(?is)<\\s*script\\b[^>]*>.*?<\\s*/\\s*script\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<\\s*/?\\s*[a-z][^>]*>");
    private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
    private static final Set<String> TIPOS = Set.of("FEATURE", "BUG");
    private static final Set<String> STATUS = Set.of(
            "PENDENTE", "EM_ANALISE", "RESOLVIDO", "RECUSADO");
    private static final Set<String> STATUS_MUTAVEIS = Set.of(
            "EM_ANALISE", "RESOLVIDO", "RECUSADO");
    private static final Set<String> TERMINAIS = Set.of("RESOLVIDO", "RECUSADO");

    private final MeusAnunciosConsultaService usuarioService;
    private final SugestaoJdbcRepository repository;
    private final PublicAuthRateLimiter rateLimiter;
    private final AuditoriaEventoRepository auditoriaRepository;

    public SugestaoService(
            MeusAnunciosConsultaService usuarioService,
            SugestaoJdbcRepository repository,
            PublicAuthRateLimiter rateLimiter,
            AuditoriaEventoRepository auditoriaRepository) {
        this.usuarioService = usuarioService;
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional
    public Criacao criar(
            CriarRequest request,
            String idempotencyKey,
            Authentication authentication,
            String requestId) {
        UsuarioEntity usuario = usuarioService.usuarioAutenticado(authentication);
        String tipo = tipo(request == null ? null : request.tipo());
        String titulo = texto(request == null ? null : request.titulo(), 5, 160, "titulo");
        String descricao = texto(
                request == null ? null : request.descricao(), 10, 3000, "descricao");
        String chave = idempotencyKey(idempotencyKey);
        String requestSeguro = requestId(requestId);

        var existente = repository.porIdempotencia(usuario.getId(), chave);
        if (existente.isPresent()) {
            validarRepetida(existente.get(), tipo, titulo, descricao);
            return criacao(existente.get(), true);
        }

        rateLimiter.require(
                "sugestao-criar",
                usuario.getId().toString(),
                5,
                Duration.ofMinutes(10));

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID id = UUID.nameUUIDFromBytes(
                ("feedback-sugestao-v1:" + usuario.getId() + ":" + chave)
                        .getBytes(StandardCharsets.UTF_8));
        int inseridos = repository.inserir(
                id, usuario.getId(), tipo, titulo, descricao, chave, requestSeguro, agora);
        var criada = repository.porIdempotencia(usuario.getId(), chave)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "sugestao nao localizada"));
        if (inseridos == 0) {
            validarRepetida(criada, tipo, titulo, descricao);
            return criacao(criada, true);
        }
        auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
                UUID.randomUUID(),
                usuario.getId(),
                "SUGESTAO_CRIADA",
                "FEEDBACK_SUGESTAO",
                criada.id(),
                "{}",
                "{\"status\":\"PENDENTE\",\"tipo\":\"" + tipo + "\"}",
                requestSeguro,
                agora));
        return criacao(criada, false);
    }

    @Transactional(readOnly = true)
    public Pagina<Resumo> listar(
            String termo,
            String status,
            int pagina,
            int tamanho) {
        String statusSeguro = filtroStatus(status);
        int paginaSegura = pagina(pagina);
        int tamanhoSeguro = tamanho(tamanho);
        long total = repository.contar(termo, statusSeguro);
        List<Resumo> itens = repository.listar(
                        termo,
                        statusSeguro,
                        tamanhoSeguro,
                        (long) paginaSegura * tamanhoSeguro)
                .stream()
                .map(this::resumo)
                .toList();
        return pagina(itens, paginaSegura, tamanhoSeguro, total);
    }

    @Transactional(readOnly = true)
    public Indicadores indicadores() {
        return repository.indicadores();
    }

    @Transactional(readOnly = true)
    public Detalhe detalhar(UUID id) {
        return detalhe(repository.porId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "sugestao nao encontrada")));
    }

    @Transactional
    public Detalhe alterarStatus(
            UUID id,
            AlterarStatusRequest request,
            AdminUserPrincipal ator,
            String requestId) {
        AdminUserPrincipal responsavel = ator(ator);
        String novoStatus = statusMutavel(request == null ? null : request.status());
        String providencia = texto(
                request == null ? null : request.providencia(),
                3,
                2000,
                "providencia");
        String requestSeguro = requestId(requestId);
        var atual = repository.porIdComLock(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "sugestao nao encontrada"));
        if (novoStatus.equals(atual.status())) {
            return detalhe(atual);
        }
        if (TERMINAIS.contains(atual.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "sugestao ja possui decisao final");
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime decididoEm = TERMINAIS.contains(novoStatus) ? agora : null;
        repository.atualizarStatus(
                id,
                novoStatus,
                providencia,
                responsavel.usuarioId(),
                decididoEm,
                agora);
        if (!auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(
                "SUGESTAO_STATUS_ALTERADO", id, requestSeguro)) {
            auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                    UUID.randomUUID(),
                    responsavel.usuarioId(),
                    "SUGESTAO_STATUS_ALTERADO",
                    "FEEDBACK_SUGESTAO",
                    id,
                    "{\"status\":\"" + atual.status() + "\"}",
                    "{\"status\":\"" + novoStatus + "\",\"providencia\":\""
                            + json(providencia) + "\"}",
                    requestSeguro,
                    agora));
        }
        return detalhar(id);
    }

    private Detalhe detalhe(SugestaoJdbcRepository.SugestaoRow item) {
        List<Historico> historico = repository.historico(item.id()).stream()
                .map(evento -> new Historico(
                        evento.id(),
                        evento.acao(),
                        acaoRotulo(evento.acao()),
                        evento.status(),
                        evento.providencia(),
                        evento.atorNome(),
                        evento.criadoEm(),
                        evento.requestId()))
                .toList();
        return new Detalhe(
                resumo(item),
                item.descricao(),
                item.providencia(),
                item.responsavelNome(),
                item.decididoEm(),
                historico);
    }

    private Resumo resumo(SugestaoJdbcRepository.SugestaoRow item) {
        return new Resumo(
                item.id(),
                protocolo(item.id()),
                item.titulo(),
                item.tipo(),
                tipoRotulo(item.tipo()),
                item.status(),
                statusRotulo(item.status()),
                item.usuarioNome(),
                mascararEmail(item.usuarioEmail()),
                item.criadoEm(),
                item.atualizadoEm());
    }

    private Criacao criacao(SugestaoJdbcRepository.SugestaoRow item, boolean repetida) {
        return new Criacao(
                item.id(),
                protocolo(item.id()),
                item.status(),
                statusRotulo(item.status()),
                item.criadoEm(),
                repetida);
    }

    private void validarRepetida(
            SugestaoJdbcRepository.SugestaoRow item,
            String tipo,
            String titulo,
            String descricao) {
        if (!tipo.equals(item.tipo())
                || !titulo.equals(item.titulo())
                || !descricao.equals(item.descricao())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key reutilizada com outra sugestao");
        }
    }

    private AdminUserPrincipal ator(AdminUserPrincipal ator) {
        if (ator == null || !ator.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "sessao administrativa obrigatoria");
        }
        return ator;
    }

    private String texto(String valor, int minimo, int maximo, String campo) {
        String seguro = valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFKC);
        seguro = seguro.replace("\r\n", "\n").replace('\r', '\n');
        seguro = SCRIPT_BLOCK.matcher(seguro).replaceAll("");
        seguro = HTML_TAG.matcher(seguro).replaceAll("");
        seguro = UNSAFE_CONTROL.matcher(seguro).replaceAll("");
        seguro = seguro.trim();
        if (seguro.length() < minimo || seguro.length() > maximo) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    campo + " invalido");
        }
        return seguro;
    }

    private String tipo(String valor) {
        String seguro = Objects.requireNonNullElse(valor, "")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(seguro)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "tipo invalido");
        }
        return seguro;
    }

    private String filtroStatus(String valor) {
        if (valor == null || valor.isBlank() || "TODOS".equalsIgnoreCase(valor)) {
            return null;
        }
        String seguro = valor.trim().toUpperCase(Locale.ROOT);
        if (!STATUS.contains(seguro)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalido");
        }
        return seguro;
    }

    private String statusMutavel(String valor) {
        String seguro = Objects.requireNonNullElse(valor, "")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!STATUS_MUTAVEIS.contains(seguro)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalido");
        }
        return seguro;
    }

    private String idempotencyKey(String valor) {
        String seguro = Objects.requireNonNullElse(valor, "").trim();
        if (!IDEMPOTENCY_KEY.matcher(seguro).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key invalida");
        }
        return seguro;
    }

    private String requestId(String valor) {
        String seguro = Objects.requireNonNullElse(valor, "").trim();
        if (!REQUEST_ID.matcher(seguro).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "requestId invalido");
        }
        return seguro;
    }

    private int pagina(int valor) {
        if (valor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagina invalida");
        }
        return valor;
    }

    private int tamanho(int valor) {
        if (valor < 1 || valor > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "tamanho de pagina invalido");
        }
        return valor;
    }

    private <T> Pagina<T> pagina(
            List<T> itens,
            int pagina,
            int tamanho,
            long total) {
        int totalPaginas = total == 0
                ? 0
                : (int) Math.ceil((double) total / tamanho);
        return new Pagina<>(itens, pagina, tamanho, total, totalPaginas);
    }

    private String mascararEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String[] partes = email.split("@", 2);
        String inicial = partes[0].isEmpty() ? "" : partes[0].substring(0, 1);
        return inicial + "***@" + partes[1];
    }

    private String json(String valor) {
        return valor.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    public static String protocolo(UUID id) {
        return "#" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public static String tipoRotulo(String tipo) {
        return "BUG".equals(tipo) ? "Bug" : "Sugestao";
    }

    public static String statusRotulo(String status) {
        return switch (status) {
            case "PENDENTE" -> "Nova";
            case "EM_ANALISE" -> "Em analise";
            case "RESOLVIDO" -> "Aceita";
            case "RECUSADO" -> "Recusada";
            default -> status;
        };
    }

    private String acaoRotulo(String acao) {
        return switch (acao) {
            case "SUGESTAO_CRIADA" -> "Sugestao recebida";
            case "SUGESTAO_STATUS_ALTERADO" -> "Tratamento administrativo atualizado";
            default -> "Evento administrativo";
        };
    }
}
