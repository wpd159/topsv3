package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncianteResumoDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalizacaoSanitizadaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminModeracaoHistoricoItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminRevisaoAbertaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioDetalhadoConsultaService {

    private static final int DESCRICAO_RESUMO_MAX = 180;
    private static final List<StatusRevisaoAnuncio> REVISOES_ABERTAS = List.of(
            StatusRevisaoAnuncio.ABERTA,
            StatusRevisaoAnuncio.EM_ANALISE);

    private final AnuncioRepository anuncioRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final DocumentoUsuarioRepository documentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final AdminLocalizacaoConsultaSupport localizacaoSupport;
    private final AdminMidiaDetalhadaConsultaService midiaService;
    private final ObjectMapper objectMapper;

    public AdminAnuncioDetalhadoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            DocumentoUsuarioRepository documentoRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            AdminLocalizacaoConsultaSupport localizacaoSupport,
            AdminMidiaDetalhadaConsultaService midiaService,
            ObjectMapper objectMapper) {
        this.anuncioRepository = anuncioRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.revisaoRepository = revisaoRepository;
        this.documentoRepository = documentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.localizacaoSupport = localizacaoSupport;
        this.midiaService = midiaService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminAnuncioListaItemDto> listar(
            int page,
            int size,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            String uf,
            String cidade,
            String bairro,
            String termo,
            boolean comercialLimitado) {
        Set<UUID> idsLocalizacao = localizacaoSupport.filtrarAnuncioIds(uf, cidade, bairro);
        if (idsLocalizacao != null && idsLocalizacao.isEmpty()) {
            return new AdminPaginaDto<>(List.of(), Math.max(page, 0), Math.max(1, Math.min(size, AdminReadOnlyPageRequest.MAX_SIZE)), 0, 0, true);
        }
        var pageable = AdminReadOnlyPageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("atualizadoEm"), Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        Page<AnuncioEntity> result = anuncioRepository.findAll(
                anuncioSpec(status, statusModeracao, idsLocalizacao, termo),
                pageable);
        Map<UUID, AdminLocalizacaoSanitizadaDto> localizacoes = localizacaoSupport.carregar(
                result.getContent().stream().map(AnuncioEntity::getId).toList());
        Map<UUID, UsuarioEntity> anunciantes = carregarAnunciantes(result.getContent());
        Map<UUID, RevisaoAnuncioEntity> revisoesAbertas = carregarRevisoesAbertas(result.getContent());
        return new AdminPaginaDto<>(
                result.getContent().stream()
                        .map(anuncio -> item(
                                anuncio,
                                localizacoes.get(anuncio.getId()),
                                anunciantes.get(anuncio.getUsuarioId()),
                                revisoesAbertas.get(anuncio.getId()),
                                comercialLimitado))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminAnuncioDetalheDto detalhar(UUID id, boolean comercialLimitado) {
        AnuncioEntity anuncio = anuncioRepository.findById(id)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        AdminLocalizacaoSanitizadaDto localizacao = localizacaoSupport.carregar(List.of(anuncio.getId()))
                .get(anuncio.getId());
        Long revisoesTotal = comercialLimitado ? null : revisaoRepository.countByAnuncioId(anuncio.getId());
        String descricaoResumo = comercialLimitado
                ? null
                : AdminTextoSanitizer.resumo(anuncio.getDescricao(), DESCRICAO_RESUMO_MAX);
        String descricao = comercialLimitado ? null : anuncio.getDescricao();
        UsuarioEntity anunciante = anuncio.getUsuarioId() == null
                ? null
                : usuarioRepository.findById(anuncio.getUsuarioId()).orElse(null);
        RevisaoAnuncioEntity revisaoAberta = revisaoRepository
                .findFirstByAnuncioIdAndStatusInOrderByCriadoEmDesc(anuncio.getId(), REVISOES_ABERTAS)
                .orElse(null);
        return new AdminAnuncioDetalheDto(
                anuncio.getId(),
                anuncio.getSlug(),
                AdminTextoSanitizer.resumo(anuncio.getTitulo(), 120),
                descricaoResumo,
                descricao,
                enumName(anuncio.getStatus()),
                enumName(anuncio.getStatusModeracao()),
                anuncio.getCategoria(),
                localizacao,
                anuncio.getCriadoEm(),
                anuncio.getAtualizadoEm(),
                anuncio.getPublicadoEm(),
                anuncio.getUltimaPublicacaoEm(),
                anuncioMidiaRepository.countByAnuncioId(anuncio.getId()),
                revisoesTotal,
                anuncio.getWhatsappNormalizado() != null,
                documentoPendente(anuncio),
                anuncio.getPreco() != null,
                comercialLimitado,
                comercialLimitado ? null : anuncio.getPreco(),
                comercialLimitado ? null : anuncio.getWhatsappNormalizado(),
                comercialLimitado ? List.of() : enumNames(anuncio.getLocaisAtendimento()),
                comercialLimitado ? List.of() : enumNames(anuncio.getServicos()),
                anunciante(anunciante),
                revisaoAberta(revisaoAberta));
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminMidiaListaItemDto> listarMidiasDoAnuncio(UUID anuncioId, int page, int size) {
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        return midiaService.listarPorAnuncio(anuncio, page, size);
    }

    @Transactional(readOnly = true)
    public List<AdminModeracaoHistoricoItemDto> historico(UUID anuncioId) {
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        List<RevisaoAnuncioEntity> revisoes = revisaoRepository.findByAnuncioId(anuncio.getId());
        List<AnuncioMidiaEntity> midias = anuncioMidiaRepository.findByAnuncioId(anuncio.getId()).stream()
                .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
                .toList();
        Set<UUID> revisaoIds = revisoes.stream().map(RevisaoAnuncioEntity::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> midiaIds = midias.stream().map(AnuncioMidiaEntity::getId).collect(java.util.stream.Collectors.toSet());
        List<UUID> recursos = new ArrayList<>();
        recursos.add(anuncio.getId());
        recursos.addAll(revisaoIds);
        recursos.addAll(midiaIds);
        return auditoriaRepository.findByRecursoIdInOrderByCriadoEmDesc(recursos, PageRequest.of(0, 100)).stream()
                .filter(evento -> alvoPermitido(evento, anuncio.getId(), revisaoIds, midiaIds))
                .filter(this::eventoDeModeracao)
                .map(this::historicoItem)
                .toList();
    }

    private AdminAnuncioListaItemDto item(
            AnuncioEntity anuncio,
            AdminLocalizacaoSanitizadaDto localizacao,
            UsuarioEntity anunciante,
            RevisaoAnuncioEntity revisaoAberta,
            boolean comercialLimitado) {
        return new AdminAnuncioListaItemDto(
                anuncio.getId(),
                anuncio.getSlug(),
                AdminTextoSanitizer.resumo(anuncio.getTitulo(), 120),
                enumName(anuncio.getStatus()),
                enumName(anuncio.getStatusModeracao()),
                localizacao,
                anuncio.getCriadoEm(),
                anuncio.getAtualizadoEm(),
                anuncio.getPublicadoEm(),
                anuncioMidiaRepository.countByAnuncioId(anuncio.getId()),
                comercialLimitado ? null : revisaoRepository.countByAnuncioId(anuncio.getId()),
                anuncio.getWhatsappNormalizado() != null,
                documentoPendente(anuncio),
                comercialLimitado,
                anunciante(anunciante),
                revisaoAberta(revisaoAberta));
    }

    private Map<UUID, UsuarioEntity> carregarAnunciantes(Collection<AnuncioEntity> anuncios) {
        Map<UUID, UsuarioEntity> result = new LinkedHashMap<>();
        usuarioRepository.findAllById(anuncios.stream()
                        .map(AnuncioEntity::getUsuarioId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .forEach(usuario -> result.put(usuario.getId(), usuario));
        return result;
    }

    private Map<UUID, RevisaoAnuncioEntity> carregarRevisoesAbertas(Collection<AnuncioEntity> anuncios) {
        Map<UUID, RevisaoAnuncioEntity> result = new LinkedHashMap<>();
        if (anuncios == null || anuncios.isEmpty()) return result;
        revisaoRepository.findByAnuncioIdInAndStatusInOrderByCriadoEmDesc(
                        anuncios.stream().map(AnuncioEntity::getId).toList(), REVISOES_ABERTAS)
                .forEach(revisao -> result.putIfAbsent(revisao.getAnuncioId(), revisao));
        return result;
    }

    private AdminAnuncianteResumoDto anunciante(UsuarioEntity usuario) {
        if (usuario == null) return null;
        return new AdminAnuncianteResumoDto(
                usuario.getId(),
                AdminTextoSanitizer.resumo(usuario.getNome(), 100),
                emailMascarado(usuario.getEmailNormalizado()),
                enumName(usuario.getStatus()));
    }

    private AdminRevisaoAbertaDto revisaoAberta(RevisaoAnuncioEntity revisao) {
        if (revisao == null) return null;
        return new AdminRevisaoAbertaDto(
                revisao.getId(),
                enumName(revisao.getTipo()),
                enumName(revisao.getStatus()),
                revisao.getCriadoEm());
    }

    private String emailMascarado(String email) {
        if (email == null || !email.contains("@")) return null;
        int separator = email.indexOf('@');
        String local = email.substring(0, separator);
        String domain = email.substring(separator + 1);
        String visible = local.isEmpty() ? "*" : local.substring(0, 1);
        return visible + "***@" + domain;
    }

    private List<String> enumNames(Collection<? extends Enum<?>> values) {
        if (values == null) return List.of();
        return values.stream().map(Enum::name).sorted().toList();
    }

    private boolean eventoDeModeracao(AuditoriaEventoEntity evento) {
        String acao = evento.getAcao();
        return acao != null && (acao.startsWith("MODERACAO_") || acao.equals("ANUNCIO_REMETER_REVISAO"));
    }

    private boolean alvoPermitido(
            AuditoriaEventoEntity evento,
            UUID anuncioId,
            Set<UUID> revisaoIds,
            Set<UUID> midiaIds) {
        if (evento.getRecursoTipo() == null) return false;
        return switch (evento.getRecursoTipo()) {
            case "ANUNCIO" -> anuncioId.equals(evento.getRecursoId());
            case "REVISAO_ANUNCIO" -> revisaoIds.contains(evento.getRecursoId());
            case "ANUNCIO_MIDIA" -> midiaIds.contains(evento.getRecursoId());
            default -> false;
        };
    }

    private AdminModeracaoHistoricoItemDto historicoItem(AuditoriaEventoEntity evento) {
        JsonNode snapshot = parseSnapshot(evento.getDepoisJson());
        return new AdminModeracaoHistoricoItemDto(
                evento.getId(),
                evento.getRecursoTipo(),
                evento.getRecursoId(),
                evento.getAcao(),
                text(snapshot, "decisao"),
                text(snapshot, "motivoSanitizado"),
                primeiroTexto(snapshot, "statusRevisao", "statusMidia", "statusAnuncio"),
                evento.getAtorUsuarioId(),
                evento.getRequestId(),
                enumName(evento.getResultado()),
                evento.getCriadoEm());
    }

    private JsonNode parseSnapshot(String json) {
        if (json == null || json.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String primeiroTexto(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : AdminTextoSanitizer.resumo(value.asText(), 240);
    }

    private boolean documentoPendente(AnuncioEntity anuncio) {
        if (anuncio.getUsuarioId() == null) {
            return false;
        }
        return documentoRepository.countByUsuarioIdAndStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNull(
                anuncio.getUsuarioId(),
                List.of(StatusDocumentoUsuario.PENDENTE, StatusDocumentoUsuario.EM_ANALISE)) > 0;
    }

    private Specification<AnuncioEntity> anuncioSpec(
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            Set<UUID> idsLocalizacao,
            String termo) {
        return (root, query, builder) -> {
            var predicate = builder.isNull(root.get("removidoEm"));
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (statusModeracao != null) {
                predicate = builder.and(predicate, builder.equal(root.get("statusModeracao"), statusModeracao));
            }
            if (idsLocalizacao != null) {
                predicate = builder.and(predicate, root.get("id").in(idsLocalizacao));
            }
            String termoSeguro = termoSeguro(termo);
            if (termoSeguro != null) {
                String like = "%" + termoSeguro.toLowerCase() + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("slug")), like),
                        builder.like(builder.lower(root.get("titulo")), like)));
            }
            return predicate;
        };
    }

    private String termoSeguro(String termo) {
        if (termo == null || termo.isBlank()) {
            return null;
        }
        String value = termo.trim();
        if (value.length() > 80 || !value.matches("[A-Za-z0-9 ._-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "termo invalido");
        }
        return value;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
