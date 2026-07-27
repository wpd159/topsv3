package br.com.topsdojob.v3.application.publico.chat;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthException;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatConversaDetalheDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatConversaDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatMensagemDto;
import br.com.topsdojob.v3.application.publico.chat.dto.ChatNaoLidasDto;
import br.com.topsdojob.v3.persistence.entity.chat.ChatConversaEntity;
import br.com.topsdojob.v3.persistence.entity.chat.ChatMensagemEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.chat.ChatConversaRepository;
import br.com.topsdojob.v3.persistence.repository.chat.ChatMensagemRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatInternoService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,160}$");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile(
            "(?is)<\\s*script\\b[^>]*>.*?<\\s*/\\s*script\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<\\s*/?\\s*[a-z][^>]*>");
    private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
    private static final int MESSAGE_MAX_LENGTH = 2000;

    private final MeusAnunciosConsultaService usuarioAutenticadoService;
    private final UsuarioRepository usuarioRepository;
    private final ChatConversaRepository conversaRepository;
    private final ChatMensagemRepository mensagemRepository;
    private final PublicAuthRateLimiter rateLimiter;

    public ChatInternoService(
            MeusAnunciosConsultaService usuarioAutenticadoService,
            UsuarioRepository usuarioRepository,
            ChatConversaRepository conversaRepository,
            ChatMensagemRepository mensagemRepository,
            PublicAuthRateLimiter rateLimiter) {
        this.usuarioAutenticadoService = usuarioAutenticadoService;
        this.usuarioRepository = usuarioRepository;
        this.conversaRepository = conversaRepository;
        this.mensagemRepository = mensagemRepository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public List<ChatConversaDto> listar(Authentication authentication) {
        UUID usuarioId = usuario(authentication).getId();
        return conversaRepository.listarResumos(usuarioId).stream()
                .map(this::toConversaDto)
                .toList();
    }

    @Transactional
    public ChatConversaDto iniciar(
            String username,
            Authentication authentication,
            String requestId) {
        UsuarioEntity ator = usuario(authentication);
        UsuarioEntity participante = participanteAtivo(username);
        if (ator.getId().equals(participante.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "conversa consigo mesmo nao permitida");
        }

        Participantes participantes = ordenar(ator.getId(), participante.getId());
        conversaRepository.inserirSeAusente(
                UUID.randomUUID(),
                participantes.a(),
                participantes.b(),
                requestIdSeguro(requestId),
                agora());

        ChatConversaEntity conversa = conversaRepository
                .findByParticipanteAIdAndParticipanteBId(participantes.a(), participantes.b())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "conversa nao localizada"));
        return resumo(conversa.getId(), ator.getId());
    }

    @Transactional(readOnly = true)
    public ChatConversaDetalheDto detalhar(UUID conversaId, Authentication authentication) {
        UsuarioEntity ator = usuario(authentication);
        ChatConversaEntity conversa = conversaAutorizada(conversaId, ator.getId(), false);
        UsuarioEntity outro = usuarioRepository.findById(conversa.outroParticipante(ator.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participante nao encontrado"));
        List<ChatMensagemDto> mensagens = mensagemRepository
                .findByConversaIdOrderByCriadoEmAscIdAsc(conversa.getId())
                .stream()
                .map(mensagem -> toMensagemDto(
                        mensagem,
                        ator.getId(),
                        ator.getNome(),
                        outro.getNome(),
                        false))
                .toList();
        return new ChatConversaDetalheDto(resumo(conversa.getId(), ator.getId()), mensagens);
    }

    @Transactional
    public ChatMensagemDto enviar(
            UUID conversaId,
            String corpo,
            String idempotencyKey,
            Authentication authentication,
            String requestId) {
        UsuarioEntity ator = usuario(authentication);
        String chave = idempotencyKeySeguro(idempotencyKey);
        String texto = textoSeguro(corpo);

        var repetida = mensagemRepository.findByRemetenteUsuarioIdAndIdempotencyKey(
                ator.getId(),
                chave);
        if (repetida.isPresent()) {
            return validarRepeticao(repetida.get(), conversaId, texto, ator.getId());
        }
        limitarEnvio(ator.getId());

        ChatConversaEntity conversa = conversaAutorizada(conversaId, ator.getId(), true);
        UUID destinatarioId = conversa.outroParticipante(ator.getId());
        OffsetDateTime agora = agora();
        UUID mensagemId = UUID.nameUUIDFromBytes(
                ("chat-mensagem-v1:" + ator.getId() + ":" + chave)
                        .getBytes(StandardCharsets.UTF_8));
        int inseridas = mensagemRepository.inserirSeAusente(
                mensagemId,
                conversa.getId(),
                ator.getId(),
                destinatarioId,
                texto,
                chave,
                requestIdSeguro(requestId),
                agora);
        ChatMensagemEntity mensagem = mensagemRepository
                .findByRemetenteUsuarioIdAndIdempotencyKey(ator.getId(), chave)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "mensagem nao localizada"));
        if (inseridas == 0) {
            return validarRepeticao(mensagem, conversaId, texto, ator.getId());
        }
        conversa.registrarMensagem(agora);
        UsuarioEntity destinatario = usuarioRepository.findById(destinatarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "destinatario nao encontrado"));
        return toMensagemDto(
                mensagem,
                ator.getId(),
                ator.getNome(),
                destinatario.getNome(),
                false);
    }

    @Transactional
    public ChatNaoLidasDto marcarComoLida(UUID conversaId, Authentication authentication) {
        UsuarioEntity ator = usuario(authentication);
        conversaAutorizada(conversaId, ator.getId(), true);
        mensagemRepository.marcarComoLidas(conversaId, ator.getId(), agora());
        return naoLidas(authentication);
    }

    @Transactional(readOnly = true)
    public ChatNaoLidasDto naoLidas(Authentication authentication) {
        UUID usuarioId = usuario(authentication).getId();
        return new ChatNaoLidasDto(
                mensagemRepository.countByDestinatarioUsuarioIdAndLidoEmIsNull(usuarioId));
    }

    private UsuarioEntity usuario(Authentication authentication) {
        return usuarioAutenticadoService.usuarioAutenticado(authentication);
    }

    private UsuarioEntity participanteAtivo(String username) {
        String seguro = username == null ? "" : username.trim();
        if (seguro.isEmpty() || seguro.length() > 120) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "username invalido");
        }
        UsuarioEntity participante = usuarioRepository.findByNomeIgnoreCase(seguro)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
        if (participante.getStatus() != StatusUsuario.ATIVO
                || participante.getTipoConta() != TipoContaUsuario.ANUNCIANTE
                || participante.getDesativadoEm() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado");
        }
        return participante;
    }

    private ChatConversaEntity conversaAutorizada(UUID conversaId, UUID usuarioId, boolean lock) {
        ChatConversaEntity conversa = (lock
                ? conversaRepository.findByIdForUpdate(conversaId)
                : conversaRepository.findById(conversaId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "conversa nao encontrada"));
        if (!conversa.contem(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "conversa pertence a outros participantes");
        }
        return conversa;
    }

    private ChatConversaDto resumo(UUID conversaId, UUID usuarioId) {
        return conversaRepository.listarResumos(usuarioId).stream()
                .map(this::toConversaDto)
                .filter(item -> conversaId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "conversa nao encontrada"));
    }

    private ChatConversaDto toConversaDto(Object[] row) {
        if (row == null || row.length != 5) {
            throw new IllegalStateException("resumo de conversa invalido");
        }
        return new ChatConversaDto(
                uuid(row[0]),
                text(row[1]),
                nullableText(row[2]),
                offsetDateTime(row[3]),
                number(row[4]));
    }

    private UUID uuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(text(value));
    }

    private String text(Object value) {
        if (value == null) {
            throw new IllegalStateException("valor obrigatorio ausente no resumo da conversa");
        }
        return value.toString();
    }

    private String nullableText(Object value) {
        return value == null ? null : value.toString();
    }

    private OffsetDateTime offsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value.toString());
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(text(value));
    }

    private ChatMensagemDto validarRepeticao(
            ChatMensagemEntity mensagem,
            UUID conversaId,
            String corpo,
            UUID atorId) {
        if (!conversaId.equals(mensagem.getConversaId())
                || !corpo.equals(mensagem.getCorpo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key reutilizada com outra mensagem");
        }
        UsuarioEntity ator = usuarioRepository.findById(atorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "remetente nao encontrado"));
        UsuarioEntity outro = usuarioRepository.findById(
                        atorId.equals(mensagem.getRemetenteUsuarioId())
                                ? mensagem.getDestinatarioUsuarioId()
                                : mensagem.getRemetenteUsuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participante nao encontrado"));
        return toMensagemDto(
                mensagem,
                atorId,
                ator.getNome(),
                outro.getNome(),
                true);
    }

    private ChatMensagemDto toMensagemDto(
            ChatMensagemEntity mensagem,
            UUID atorId,
            String atorUsername,
            String outroUsername,
            boolean repetida) {
        boolean minha = atorId.equals(mensagem.getRemetenteUsuarioId());
        String remetenteUsername = minha ? atorUsername : outroUsername;
        return new ChatMensagemDto(
                mensagem.getId(),
                mensagem.getConversaId(),
                remetenteUsername,
                mensagem.getCorpo(),
                mensagem.getCriadoEm(),
                mensagem.getLidoEm(),
                minha,
                repetida);
    }

    private String textoSeguro(String corpo) {
        String texto = corpo == null ? "" : Normalizer.normalize(corpo, Normalizer.Form.NFKC);
        texto = texto.replace("\r\n", "\n").replace('\r', '\n');
        texto = SCRIPT_BLOCK.matcher(texto).replaceAll("");
        texto = HTML_TAG.matcher(texto).replaceAll("");
        texto = UNSAFE_CONTROL.matcher(texto).replaceAll("");
        texto = texto.trim();
        if (texto.isEmpty() || texto.length() > MESSAGE_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "mensagem invalida");
        }
        return texto;
    }

    private void limitarEnvio(UUID usuarioId) {
        try {
            rateLimiter.require(
                    "chat-send",
                    usuarioId.toString(),
                    30,
                    Duration.ofMinutes(1));
        } catch (PublicAuthException exception) {
            throw new ResponseStatusException(exception.status(), exception.getMessage());
        }
    }

    private String idempotencyKeySeguro(String value) {
        String seguro = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(seguro).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
        }
        return seguro;
    }

    private String requestIdSeguro(String requestId) {
        String seguro = Objects.requireNonNullElse(requestId, "").trim();
        if (!seguro.matches("^[A-Za-z0-9._:-]{8,128}$")) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "requestId invalido");
        }
        return seguro;
    }

    private Participantes ordenar(UUID primeiro, UUID segundo) {
        String a = primeiro.toString().toLowerCase(Locale.ROOT);
        String b = segundo.toString().toLowerCase(Locale.ROOT);
        return a.compareTo(b) < 0
                ? new Participantes(primeiro, segundo)
                : new Participantes(segundo, primeiro);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record Participantes(UUID a, UUID b) {
    }
}
