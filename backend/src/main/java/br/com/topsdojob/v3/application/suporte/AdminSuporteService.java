package br.com.topsdojob.v3.application.suporte;

import br.com.topsdojob.v3.application.suporte.SuporteDtos.AdminTicketDetalhe;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.AdminTicketResumo;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Indicadores;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Mensagem;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Pagina;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
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
public class AdminSuporteService {

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
    private static final Set<String> FILTROS_STATUS = Set.of(
            "ABERTO",
            "EM_ATENDIMENTO",
            "AGUARDANDO_USUARIO",
            "RESOLVIDO",
            "ENCERRADO",
            "ABERTOS",
            "EM_ANDAMENTO",
            "FECHADOS");
    private static final Set<String> STATUS_MUTAVEIS = Set.of(
            "ABERTO",
            "EM_ATENDIMENTO",
            "AGUARDANDO_USUARIO",
            "RESOLVIDO",
            "ENCERRADO");

    private final SuporteTicketJdbcRepository repository;
    private final AuditoriaEventoRepository auditoriaRepository;

    public AdminSuporteService(
            SuporteTicketJdbcRepository repository,
            AuditoriaEventoRepository auditoriaRepository) {
        this.repository = repository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional(readOnly = true)
    public Pagina<AdminTicketResumo> listar(
            String termo,
            String categoria,
            String status,
            String ordenacao,
            int pagina,
            int tamanho) {
        String categoriaSegura = opcional(categoria, CATEGORIAS, "categoria invalida");
        String statusSeguro = opcional(status, FILTROS_STATUS, "status invalido");
        String ordemSegura = ordem(ordenacao);
        int paginaSegura = pagina(pagina);
        int tamanhoSeguro = tamanho(tamanho);
        String termoSeguro = termo == null || termo.isBlank() ? null : termo.trim();
        if (termoSeguro != null && termoSeguro.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "busca invalida");
        }
        long total = repository.contarAdmin(termoSeguro, categoriaSegura, statusSeguro);
        List<AdminTicketResumo> itens = repository.listarAdmin(
                        termoSeguro,
                        categoriaSegura,
                        statusSeguro,
                        ordemSegura,
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
    public AdminTicketDetalhe detalhar(UUID id) {
        return detalhe(id);
    }

    @Transactional
    public Mensagem responder(
            UUID ticketId,
            String corpo,
            String idempotencyKey,
            AdminUserPrincipal ator,
            String requestId) {
        AdminUserPrincipal admin = ator(ator);
        String chave = chave(idempotencyKey);
        String texto = texto(corpo);
        String requestSeguro = requestId(requestId);
        var repetida = repository.mensagemPorIdempotencia(admin.usuarioId(), chave);
        if (repetida.isPresent()) {
            return validarRepetida(repetida.get(), ticketId, texto, admin.usuarioId());
        }

        SuporteTicketJdbcRepository.TicketRow ticket = repository.porIdComLock(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket nao encontrado"));
        exigirAberto(ticket.status());
        OffsetDateTime agora = agora();
        int inseridas = repository.inserirMensagem(
                uuid("suporte-staff-v1:" + admin.usuarioId() + ":" + chave),
                ticket.id(),
                admin.usuarioId(),
                "STAFF",
                texto,
                false,
                true,
                chave,
                requestSeguro,
                agora);
        if (inseridas == 0) {
            SuporteTicketJdbcRepository.MensagemRow concorrente = repository
                    .mensagemPorIdempotencia(admin.usuarioId(), chave)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "mensagem concorrente nao localizada"));
            return validarRepetida(concorrente, ticketId, texto, admin.usuarioId());
        }
        repository.atualizarStatus(
                ticket.id(),
                "AGUARDANDO_USUARIO",
                admin.usuarioId(),
                null,
                agora);
        auditar(
                admin.usuarioId(),
                "SUPORTE_RESPOSTA_STAFF",
                ticket.id(),
                "{\"status\":\"" + ticket.status() + "\"}",
                "{\"status\":\"AGUARDANDO_USUARIO\",\"mensagemRegistrada\":true}",
                requestSeguro,
                agora);
        SuporteTicketJdbcRepository.MensagemRow mensagem = repository
                .mensagemPorIdempotencia(admin.usuarioId(), chave)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "mensagem nao localizada"));
        return mensagem(mensagem, admin.usuarioId(), false);
    }

    @Transactional
    public AdminTicketDetalhe alterarStatus(
            UUID ticketId,
            String novoStatus,
            AdminUserPrincipal ator,
            String requestId) {
        AdminUserPrincipal admin = ator(ator);
        String status = obrigatorio(novoStatus, STATUS_MUTAVEIS, "status invalido");
        String requestSeguro = requestId(requestId);
        SuporteTicketJdbcRepository.TicketRow ticket = repository.porIdComLock(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket nao encontrado"));
        if (ticket.status().equals(status)) {
            return detalhe(ticket.id());
        }
        if (Set.of("RESOLVIDO", "ENCERRADO").contains(ticket.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ticket finalizado nao pode ser reaberto");
        }
        OffsetDateTime agora = agora();
        OffsetDateTime encerrado = Set.of("RESOLVIDO", "ENCERRADO").contains(status) ? agora : null;
        repository.atualizarStatus(ticket.id(), status, admin.usuarioId(), encerrado, agora);
        auditar(
                admin.usuarioId(),
                "SUPORTE_STATUS_ALTERADO",
                ticket.id(),
                "{\"status\":\"" + ticket.status() + "\"}",
                "{\"status\":\"" + status + "\"}",
                requestSeguro,
                agora);
        return detalhe(ticket.id());
    }

    private AdminTicketDetalhe detalhe(UUID id) {
        SuporteTicketJdbcRepository.AdminTicketRow row = repository.detalheAdmin(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket nao encontrado"));
        UUID responsavelId = row.ticket().responsavelId();
        String responsavelNome = responsavelId == null
                ? null
                : repository.mensagens(id, true).stream()
                        .filter(item -> responsavelId.equals(item.autorId()))
                        .map(SuporteTicketJdbcRepository.MensagemRow::remetente)
                        .findFirst()
                        .orElse("Equipe");
        List<Mensagem> mensagens = repository.mensagens(id, true).stream()
                .map(item -> mensagem(item, responsavelId, false))
                .toList();
        return new AdminTicketDetalhe(
                resumo(row),
                mensagens,
                responsavelId,
                responsavelNome,
                row.ticket().encerradoEm());
    }

    private AdminTicketResumo resumo(SuporteTicketJdbcRepository.AdminTicketRow row) {
        var ticket = row.ticket();
        return new AdminTicketResumo(
                ticket.id(),
                SuporteTicketService.protocolo(ticket.id()),
                ticket.assunto(),
                ticket.categoria(),
                SuporteTicketService.categoriaRotulo(ticket.categoria()),
                ticket.status(),
                SuporteTicketService.statusRotulo(ticket.status()),
                ticket.criadoEm(),
                ticket.atualizadoEm(),
                ticket.totalMensagens(),
                ticket.usuarioId(),
                row.usuarioNome(),
                row.usuarioEmail(),
                row.mensagemInicial());
    }

    private Mensagem validarRepetida(
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

    private Mensagem mensagem(
            SuporteTicketJdbcRepository.MensagemRow row,
            UUID atorId,
            boolean repetida) {
        return new Mensagem(
                row.id(),
                row.origem(),
                row.remetente(),
                row.corpo(),
                row.criadoEm(),
                atorId != null && atorId.equals(row.autorId()),
                repetida,
                row.naoLidaUsuario());
    }

    private void exigirAberto(String status) {
        if (Set.of("RESOLVIDO", "ENCERRADO").contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ticket encerrado nao aceita respostas");
        }
    }

    private AdminUserPrincipal ator(AdminUserPrincipal ator) {
        if (ator == null || !ator.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
        return ator;
    }

    private String texto(String valor) {
        String seguro = valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFKC);
        seguro = seguro.replace("\r\n", "\n").replace('\r', '\n');
        seguro = SCRIPT_BLOCK.matcher(seguro).replaceAll("");
        seguro = HTML_TAG.matcher(seguro).replaceAll("");
        seguro = UNSAFE_CONTROL.matcher(seguro).replaceAll("");
        seguro = seguro.trim();
        if (seguro.isEmpty() || seguro.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "mensagem invalida");
        }
        return seguro;
    }

    private String chave(String valor) {
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

    private String ordem(String valor) {
        String ordem = Objects.requireNonNullElse(valor, "RECENTES").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("RECENTES", "ANTIGOS").contains(ordem)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ordenacao invalida");
        }
        return ordem;
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

    private void auditar(
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

    private UUID uuid(String valor) {
        return UUID.nameUUIDFromBytes(valor.getBytes(StandardCharsets.UTF_8));
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
