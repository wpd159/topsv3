package br.com.topsdojob.v3.application.denuncia;

import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Anuncio;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Detalhe;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Historico;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Indicadores;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Pagina;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Resumo;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDenunciaService {

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
    private static final Set<String> STATUS = Set.of("PENDENTE", "PUNIDA", "IGNORADA");
    private static final Set<String> STATUS_FINAIS = Set.of("PUNIDA", "IGNORADA");

    private final DenunciaJdbcRepository repository;
    private final AuditoriaEventoRepository auditoriaRepository;

    public AdminDenunciaService(
            DenunciaJdbcRepository repository,
            AuditoriaEventoRepository auditoriaRepository) {
        this.repository = repository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional(readOnly = true)
    public Pagina<Resumo> listar(
            String termo,
            String motivo,
            String status,
            LocalDate inicio,
            LocalDate fim,
            int pagina,
            int tamanho) {
        String motivoSeguro = opcional(motivo, MOTIVOS, "motivo invalido");
        String statusSeguro = opcional(status, STATUS, "status invalido");
        String termoSeguro = termo == null || termo.isBlank() ? null : termo.trim();
        if (termoSeguro != null && termoSeguro.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "busca invalida");
        }
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodo invalido");
        }
        int paginaSegura = pagina(pagina);
        int tamanhoSeguro = tamanho(tamanho);
        OffsetDateTime inicioUtc = inicio == null
                ? null
                : inicio.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime fimExclusivoUtc = fim == null
                ? null
                : fim.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        long total = repository.contar(
                termoSeguro,
                motivoSeguro,
                statusSeguro,
                inicioUtc,
                fimExclusivoUtc);
        List<Resumo> itens = repository.listar(
                        termoSeguro,
                        motivoSeguro,
                        statusSeguro,
                        inicioUtc,
                        fimExclusivoUtc,
                        tamanhoSeguro,
                        (long) paginaSegura * tamanhoSeguro)
                .stream()
                .map(this::resumo)
                .toList();
        int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / tamanhoSeguro);
        return new Pagina<>(itens, paginaSegura, tamanhoSeguro, total, totalPaginas);
    }

    @Transactional(readOnly = true)
    public Indicadores indicadores() {
        return repository.indicadores();
    }

    @Transactional(readOnly = true)
    public Detalhe detalhar(UUID id) {
        return detalhe(id);
    }

    @Transactional
    public Detalhe alterarStatus(
            UUID id,
            String novoStatus,
            String providencia,
            AdminUserPrincipal ator,
            String requestId) {
        AdminUserPrincipal admin = ator(ator);
        String status = obrigatorio(novoStatus, STATUS_FINAIS, "status invalido");
        String texto = providencia(providencia);
        String requestSeguro = requestId(requestId);
        DenunciaJdbcRepository.DenunciaRow atual = repository.porIdComLock(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "denuncia nao encontrada"));

        if (status.equals(atual.status()) && texto.equals(atual.providencia())) {
            return detalhe(atual.id());
        }
        if (!"PENDENTE".equals(atual.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "denuncia ja possui decisao administrativa");
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        repository.atualizarStatus(
                atual.id(),
                status,
                texto,
                admin.usuarioId(),
                agora);
        auditar(admin, atual, status, texto, requestSeguro, agora);
        return detalhe(atual.id());
    }

    private Detalhe detalhe(UUID id) {
        DenunciaJdbcRepository.DenunciaRow row = repository.porId(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "denuncia nao encontrada"));
        List<Historico> historico = repository.historico(id).stream()
                .map(item -> new Historico(
                        item.id(),
                        item.acao(),
                        acaoRotulo(item.acao()),
                        item.status(),
                        item.providencia(),
                        item.atorNome(),
                        item.criadoEm(),
                        item.requestId()))
                .toList();
        return new Detalhe(
                resumo(row),
                row.descricao(),
                row.providencia(),
                row.responsavelNome(),
                row.decididaEm(),
                new Anuncio(
                        row.anuncioId(),
                        row.anuncioTitulo(),
                        row.anuncioSlug(),
                        row.anuncioStatus(),
                        row.anuncioStatusModeracao()),
                historico);
    }

    private Resumo resumo(DenunciaJdbcRepository.DenunciaRow row) {
        return new Resumo(
                row.id(),
                protocolo(row.id()),
                row.anuncioId(),
                row.anuncioTitulo(),
                row.anuncioSlug(),
                row.motivo(),
                motivoRotulo(row.motivo()),
                row.status(),
                statusRotulo(row.status()),
                row.denuncianteUsuarioId() == null
                        ? "Visitante"
                        : Objects.requireNonNullElse(row.denuncianteNome(), "Usuario autenticado"),
                mascararEmail(row.denuncianteEmail()),
                row.criadoEm(),
                row.atualizadoEm());
    }

    private void auditar(
            AdminUserPrincipal ator,
            DenunciaJdbcRepository.DenunciaRow antes,
            String status,
            String providencia,
            String requestId,
            OffsetDateTime agora) {
        String acao = "DENUNCIA_STATUS_ALTERADO";
        if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(acao, antes.id(), requestId)) {
            return;
        }
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                ator.usuarioId(),
                acao,
                "DENUNCIA_ANUNCIO",
                antes.id(),
                "{\"status\":\"" + antes.status() + "\"}",
                "{\"status\":\"" + status + "\",\"providencia\":\""
                        + jsonSeguro(providencia) + "\"}",
                requestId,
                agora));
    }

    private AdminUserPrincipal ator(AdminUserPrincipal ator) {
        if (ator == null || !ator.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
        return ator;
    }

    private String providencia(String valor) {
        String texto = valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFKC);
        texto = texto.replace("\r\n", "\n").replace('\r', '\n');
        texto = SCRIPT_BLOCK.matcher(texto).replaceAll("");
        texto = HTML_TAG.matcher(texto).replaceAll("");
        texto = UNSAFE_CONTROL.matcher(texto).replaceAll("");
        texto = texto.trim();
        if (texto.length() < 3 || texto.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "providencia invalida");
        }
        return texto;
    }

    private String opcional(String valor, Set<String> permitidos, String erro) {
        if (valor == null || valor.isBlank() || "TODOS".equalsIgnoreCase(valor)) {
            return null;
        }
        return obrigatorio(valor, permitidos, erro);
    }

    private String obrigatorio(String valor, Set<String> permitidos, String erro) {
        String seguro = Objects.requireNonNullElse(valor, "").trim().toUpperCase(Locale.ROOT);
        if (!permitidos.contains(seguro)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, erro);
        }
        return seguro;
    }

    private String requestId(String valor) {
        String requestId = Objects.requireNonNullElse(valor, "").trim();
        if (!REQUEST_ID.matcher(requestId).matches()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "requestId invalido");
        }
        return requestId;
    }

    private int pagina(int valor) {
        if (valor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagina invalida");
        }
        return valor;
    }

    private int tamanho(int valor) {
        if (valor < 1 || valor > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tamanho de pagina invalido");
        }
        return valor;
    }

    private String mascararEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        String[] partes = email.split("@", 2);
        String local = partes[0];
        String inicio = local.isEmpty() ? "" : local.substring(0, 1);
        return inicio + "***@" + partes[1];
    }

    private String jsonSeguro(String valor) {
        return valor.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    public static String protocolo(UUID id) {
        return "#" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public static String motivoRotulo(String motivo) {
        return switch (motivo) {
            case "CONTEUDO_INADEQUADO" -> "Conteudo inadequado";
            case "PERFIL_FALSO" -> "Perfil falso";
            case "GOLPE" -> "Golpe / Scam";
            case "SPAM" -> "Spam";
            default -> "Outros";
        };
    }

    public static String statusRotulo(String status) {
        return switch (status) {
            case "PENDENTE" -> "Pendente";
            case "PUNIDA" -> "Providencia registrada";
            case "IGNORADA" -> "Sem providencia";
            default -> status;
        };
    }

    private String acaoRotulo(String acao) {
        return switch (acao) {
            case "DENUNCIA_CRIADA" -> "Denuncia recebida";
            case "DENUNCIA_STATUS_ALTERADO" -> "Providencia administrativa registrada";
            default -> "Evento administrativo";
        };
    }
}
