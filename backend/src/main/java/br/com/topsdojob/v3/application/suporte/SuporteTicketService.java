package br.com.topsdojob.v3.application.suporte;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.CriarTicketRequest;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Mensagem;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.NaoLidas;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Pagina;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.TicketDetalhe;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.TicketResumo;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
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
public class SuporteTicketService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,160}$");
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "(?is)<\\s*script\\b[^>]*>.*?<\\s*/\\s*script\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<\\s*/?\\s*[a-z][^>]*>");
    private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
    private static final Set<String> CATEGORIAS = Set.of(
            "ERRO_NO_SISTEMA",
            "PROBLEMAS_COM_PAGAMENTO",
            "ACESSO_CONTA",
            "SUGESTAO",
            "OUTROS");
    private static final Set<String> GRUPOS = Set.of("TODOS", "ABERTOS", "ENCERRADOS");

    private final MeusAnunciosConsultaService usuarioService;
    private final SuporteTicketJdbcRepository repository;
    private final PublicAuthRateLimiter rateLimiter;
    private final AuditoriaEventoRepository auditoriaRepository;

    public SuporteTicketService(
            MeusAnunciosConsultaService usuarioService,
            SuporteTicketJdbcRepository repository,
            PublicAuthRateLimiter rateLimiter,
            AuditoriaEventoRepository auditoriaRepository) {
        this.usuarioService = usuarioService;
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional(readOnly = true)
    public Pagina<TicketResumo> listar(
            String grupo,
            int pagina,
            int tamanho,
            Authentication authentication) {
        UUID usuarioId = usuario(authentication).getId();
        String grupoSeguro = grupoSeguro(grupo);
        int paginaSegura = paginaSegura(pagina);
        int tamanhoSeguro = tamanhoSeguro(tamanho);
        long total = repository.contarDoUsuario(usuarioId, grupoSeguro);
        List<TicketResumo> itens = repository.listarDoUsuario(
                        usuarioId,
                        grupoSeguro,
                        tamanhoSeguro,
                        (long) paginaSegura * tamanhoSeguro)
                .stream()
                .map(this::resumo)
                .toList();
        return pagina(itens, paginaSegura, tamanhoSeguro, total);
    }

    @Transactional
    public TicketDetalhe detalhar(UUID ticketId, Authentication authentication) {
        UUID usuarioId = usuario(authentication).getId();
        SuporteTicketJdbcRepository.TicketRow ticket = ticketDoUsuario(ticketId, usuarioId, false);
        repository.marcarRespostasComoLidas(ticket.id(), usuarioId);
        return detalhe(repository.porId(ticket.id()).orElseThrow(), usuarioId, false, false);
    }

    @Transactional(readOnly = true)
    public NaoLidas naoLidas(Authentication authentication) {
        UUID usuarioId = usuario(authentication).getId();
        return new NaoLidas(repository.contarNaoLidasDoUsuario(usuarioId));
    }

    @Transactional
    public TicketDetalhe criar(
            CriarTicketRequest request,
            String idempotencyKey,
            Authentication authentication,
            String requestId) {
        UsuarioEntity ator = usuario(authentication);
        String chave = chaveSegura(idempotencyKey);
        String requestSeguro = requestIdSeguro(requestId);
        String assunto = textoSeguro(request == null ? null : request.assunto(), 6, 160, "assunto");
        String categoria = categoriaSegura(request == null ? null : request.categoria());
        String descricao = textoSeguro(request == null ? null : request.descricao(), 10, 4000, "descricao");

        var repetido = repository.porCriacao(ator.getId(), chave);
        if (repetido.isPresent()) {
            validarCriacaoRepetida(repetido.get(), assunto, categoria, descricao);
            return detalhe(repetido.get(), ator.getId(), false, true);
        }
        limitar("suporte-criar", ator.getId(), 5, Duration.ofMinutes(10));

        OffsetDateTime agora = agora();
        UUID ticketId = uuid("suporte-ticket-v1:" + ator.getId() + ":" + chave);
        int inseridos = repository.inserirTicket(
                ticketId,
                ator.getId(),
                assunto,
                categoria,
                chave,
                requestSeguro,
                agora);
        SuporteTicketJdbcRepository.TicketRow ticket = repository.porCriacao(ator.getId(), chave)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ticket nao localizado"));
        if (inseridos == 0) {
            validarCriacaoRepetida(ticket, assunto, categoria, descricao);
            return detalhe(ticket, ator.getId(), false, true);
        }

        repository.inserirMensagem(
                uuid("suporte-mensagem-inicial-v1:" + ticket.id()),
                ticket.id(),
                ator.getId(),
                "USUARIO",
                descricao,
                false,
                false,
                chave,
                requestSeguro,
                agora);
        registrarAuditoria(
                ator.getId(),
                "SUPORTE_TICKET_CRIADO",
                ticket.id(),
                "{}",
                "{\"status\":\"ABERTO\",\"categoria\":\"" + categoria + "\"}",
                requestSeguro,
                agora);
        repository.tocar(ticket.id(), agora);
        return detalhar(ticket.id(), authentication);
    }

    @Transactional
    public Mensagem responder(
            UUID ticketId,
            String corpo,
            String idempotencyKey,
            Authentication authentication,
            String requestId) {
        UsuarioEntity ator = usuario(authentication);
        String chave = chaveSegura(idempotencyKey);
        String texto = textoSeguro(corpo, 1, 4000, "mensagem");
        String requestSeguro = requestIdSeguro(requestId);

        var repetida = repository.mensagemPorIdempotencia(ator.getId(), chave);
        if (repetida.isPresent()) {
            return validarMensagemRepetida(repetida.get(), ticketId, texto, ator.getId());
        }
        limitar("suporte-responder", ator.getId(), 30, Duration.ofMinutes(1));

        SuporteTicketJdbcRepository.TicketRow ticket = ticketDoUsuario(ticketId, ator.getId(), true);
        exigirAberto(ticket.status());
        OffsetDateTime agora = agora();
        int inseridas = repository.inserirMensagem(
                uuid("suporte-mensagem-v1:" + ator.getId() + ":" + chave),
                ticket.id(),
                ator.getId(),
                "USUARIO",
                texto,
                false,
                false,
                chave,
                requestSeguro,
                agora);
        if (inseridas == 0) {
            SuporteTicketJdbcRepository.MensagemRow concorrente = repository
                    .mensagemPorIdempotencia(ator.getId(), chave)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "mensagem concorrente nao localizada"));
            return validarMensagemRepetida(concorrente, ticketId, texto, ator.getId());
        }
        if ("AGUARDANDO_USUARIO".equals(ticket.status())) {
            repository.atualizarStatus(ticket.id(), "EM_ATENDIMENTO", null, null, agora);
        } else {
            repository.tocar(ticket.id(), agora);
        }
        registrarAuditoria(
                ator.getId(),
                "SUPORTE_MENSAGEM_USUARIO",
                ticket.id(),
                "{\"status\":\"" + ticket.status() + "\"}",
                "{\"mensagemRegistrada\":true}",
                requestSeguro,
                agora);
        SuporteTicketJdbcRepository.MensagemRow mensagem = repository
                .mensagemPorIdempotencia(ator.getId(), chave)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "mensagem nao localizada"));
        return mensagem(mensagem, ator.getId(), false);
    }

    @Transactional
    public TicketDetalhe encerrar(
            UUID ticketId,
            Authentication authentication,
            String requestId) {
        UsuarioEntity ator = usuario(authentication);
        String requestSeguro = requestIdSeguro(requestId);
        SuporteTicketJdbcRepository.TicketRow ticket = ticketDoUsuario(ticketId, ator.getId(), true);
        if ("ENCERRADO".equals(ticket.status())) {
            return detalhe(ticket, ator.getId(), false, true);
        }
        if ("RESOLVIDO".equals(ticket.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ticket ja resolvido pela equipe");
        }
        OffsetDateTime agora = agora();
        repository.atualizarStatus(ticket.id(), "ENCERRADO", null, agora, agora);
        registrarAuditoria(
                ator.getId(),
                "SUPORTE_TICKET_ENCERRADO_USUARIO",
                ticket.id(),
                "{\"status\":\"" + ticket.status() + "\"}",
                "{\"status\":\"ENCERRADO\"}",
                requestSeguro,
                agora);
        return detalhe(repository.porId(ticket.id()).orElseThrow(), ator.getId(), false, false);
    }

    private UsuarioEntity usuario(Authentication authentication) {
        return usuarioService.usuarioAutenticado(authentication);
    }

    private SuporteTicketJdbcRepository.TicketRow ticketDoUsuario(
            UUID ticketId,
            UUID usuarioId,
            boolean lock) {
        if (ticketId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticket obrigatorio");
        }
        SuporteTicketJdbcRepository.TicketRow ticket = (lock
                ? repository.porIdComLock(ticketId)
                : repository.porId(ticketId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket nao encontrado"));
        if (!usuarioId.equals(ticket.usuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ticket pertence a outro usuario");
        }
        return ticket;
    }

    private TicketDetalhe detalhe(
            SuporteTicketJdbcRepository.TicketRow ticket,
            UUID atorId,
            boolean incluirPrivadas,
            boolean repetida) {
        List<Mensagem> mensagens = repository.mensagens(ticket.id(), incluirPrivadas).stream()
                .map(item -> mensagem(item, atorId, repetida))
                .toList();
        return new TicketDetalhe(resumo(ticket), mensagens);
    }

    private void validarCriacaoRepetida(
            SuporteTicketJdbcRepository.TicketRow ticket,
            String assunto,
            String categoria,
            String descricao) {
        List<SuporteTicketJdbcRepository.MensagemRow> mensagens = repository.mensagens(ticket.id(), false);
        String mensagemInicial = mensagens.isEmpty() ? "" : mensagens.get(0).corpo();
        if (!assunto.equals(ticket.assunto())
                || !categoria.equals(ticket.categoria())
                || !descricao.equals(mensagemInicial)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key reutilizada com outro ticket");
        }
    }

    private Mensagem validarMensagemRepetida(
            SuporteTicketJdbcRepository.MensagemRow mensagem,
            UUID ticketId,
            String corpo,
            UUID atorId) {
        if (!ticketId.equals(mensagem.ticketId()) || !corpo.equals(mensagem.corpo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key reutilizada com outra mensagem");
        }
        return mensagem(mensagem, atorId, true);
    }

    private TicketResumo resumo(SuporteTicketJdbcRepository.TicketRow ticket) {
        return new TicketResumo(
                ticket.id(),
                protocolo(ticket.id()),
                ticket.assunto(),
                ticket.categoria(),
                categoriaRotulo(ticket.categoria()),
                ticket.status(),
                statusRotulo(ticket.status()),
                ticket.criadoEm(),
                ticket.atualizadoEm(),
                ticket.totalMensagens(),
                ticket.naoLidas());
    }

    private Mensagem mensagem(
            SuporteTicketJdbcRepository.MensagemRow item,
            UUID atorId,
            boolean repetida) {
        String remetente = "STAFF".equals(item.origem())
                ? "Equipe de suporte"
                : item.remetente();
        return new Mensagem(
                item.id(),
                item.origem(),
                remetente,
                item.corpo(),
                item.criadoEm(),
                atorId.equals(item.autorId()),
                repetida,
                item.naoLidaUsuario());
    }

    private void exigirAberto(String status) {
        if (Set.of("RESOLVIDO", "ENCERRADO").contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ticket encerrado nao aceita mensagens");
        }
    }

    private String textoSeguro(String valor, int minimo, int maximo, String campo) {
        String texto = valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFKC);
        texto = texto.replace("\r\n", "\n").replace('\r', '\n');
        texto = SCRIPT_BLOCK.matcher(texto).replaceAll("");
        texto = HTML_TAG.matcher(texto).replaceAll("");
        texto = UNSAFE_CONTROL.matcher(texto).replaceAll("");
        texto = texto.trim();
        if (texto.length() < minimo || texto.length() > maximo) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, campo + " invalido");
        }
        return texto;
    }

    private String categoriaSegura(String valor) {
        String categoria = Objects.requireNonNullElse(valor, "").trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIAS.contains(categoria)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "categoria invalida");
        }
        return categoria;
    }

    private String grupoSeguro(String valor) {
        String grupo = Objects.requireNonNullElse(valor, "TODOS").trim().toUpperCase(Locale.ROOT);
        if (!GRUPOS.contains(grupo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filtro de tickets invalido");
        }
        return grupo;
    }

    private String chaveSegura(String valor) {
        String chave = Objects.requireNonNullElse(valor, "").trim();
        if (!IDEMPOTENCY_KEY.matcher(chave).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
        }
        return chave;
    }

    private String requestIdSeguro(String valor) {
        String requestId = Objects.requireNonNullElse(valor, "").trim();
        if (!REQUEST_ID.matcher(requestId).matches()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "requestId invalido");
        }
        return requestId;
    }

    private void limitar(String operacao, UUID usuarioId, int limite, Duration janela) {
        try {
            rateLimiter.require(operacao, usuarioId.toString(), limite, janela);
        } catch (PublicAuthException exception) {
            throw new ResponseStatusException(exception.status(), exception.getMessage());
        }
    }

    private void registrarAuditoria(
            UUID atorId,
            String acao,
            UUID ticketId,
            String antes,
            String depois,
            String requestId,
            OffsetDateTime agora) {
        if (auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(acao, ticketId, requestId)) {
            return;
        }
        auditoriaRepository.save(AuditoriaEventoEntity.registrarSuporte(
                UUID.randomUUID(),
                atorId,
                acao,
                ticketId,
                antes,
                depois,
                requestId,
                agora));
    }

    private <T> Pagina<T> pagina(List<T> itens, int numero, int tamanho, long total) {
        int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / tamanho);
        return new Pagina<>(itens, numero, tamanho, total, totalPaginas);
    }

    private int paginaSegura(int valor) {
        if (valor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagina invalida");
        }
        return valor;
    }

    private int tamanhoSeguro(int valor) {
        if (valor < 1 || valor > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tamanho de pagina invalido");
        }
        return valor;
    }

    private UUID uuid(String valor) {
        return UUID.nameUUIDFromBytes(valor.getBytes(StandardCharsets.UTF_8));
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static String categoriaRotulo(String categoria) {
        return switch (categoria) {
            case "ERRO_NO_SISTEMA" -> "Erro no sistema";
            case "PROBLEMAS_COM_PAGAMENTO" -> "Pagamento";
            case "ACESSO_CONTA" -> "Acesso / Conta";
            case "SUGESTAO" -> "Sugestao";
            default -> "Outros";
        };
    }

    public static String statusRotulo(String status) {
        return switch (status) {
            case "ABERTO" -> "Aberto";
            case "EM_ATENDIMENTO" -> "Em atendimento";
            case "AGUARDANDO_USUARIO" -> "Aguardando resposta";
            case "RESOLVIDO" -> "Resolvido";
            case "ENCERRADO" -> "Encerrado";
            default -> status;
        };
    }

    public static String protocolo(UUID id) {
        return "#" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
