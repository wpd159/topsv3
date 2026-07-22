package br.com.topsdojob.v3.application.admin.stories;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryCandidatoDto;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStorySelecaoDto;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService.MidiaElegivel;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class AdminStorySelecaoService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String RECURSO_TIPO = "STORY_SELECAO_ADMINISTRATIVA";
    private static final Duration DURACAO_ADMINISTRATIVA = Duration.ofHours(24);

    private final StorySelecaoAdministrativaRepository selecaoRepository;
    private final AnuncioRepository anuncioRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final StoryMidiaElegibilidadeService elegibilidadeService;
    private final ObjectMapper objectMapper;

    public AdminStorySelecaoService(
            StorySelecaoAdministrativaRepository selecaoRepository,
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            StoryMidiaElegibilidadeService elegibilidadeService,
            ObjectMapper objectMapper) {
        this.selecaoRepository = selecaoRepository;
        this.anuncioRepository = anuncioRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.elegibilidadeService = elegibilidadeService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminStorySelecaoDto consultar() {
        StorySelecaoAdministrativaEntity selecao = selecaoRepository.atual().orElse(null);
        return selecao == null || !ativaNoInstante(selecao, agora()) ? inativa() : toDto(selecao);
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminStoryCandidatoDto> listarCandidatos(int page, int size, String termo) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        StorySelecaoAdministrativaEntity selecao = selecaoRepository.atual().orElse(null);
        UUID selecionadoId = selecao != null && ativaNoInstante(selecao, agora()) ? selecao.getAnuncioId() : null;
        Page<AnuncioEntity> candidatos = anuncioRepository.findAll(
                candidatoSpec(termo),
                PageRequest.of(safePage, safeSize, Sort.by(
                        Sort.Order.desc("atualizadoEm"),
                        Sort.Order.asc("id"))));
        List<AdminStoryCandidatoDto> itens = candidatos.getContent().stream()
                .map(anuncio -> candidato(anuncio, selecionadoId))
                .toList();
        return new AdminPaginaDto<>(
                itens,
                candidatos.getNumber(),
                candidatos.getSize(),
                candidatos.getTotalElements(),
                candidatos.getTotalPages(),
                candidatos.isLast());
    }

    @Transactional
    public AdminStorySelecaoDto ativar(UUID anuncioId, AdminUserPrincipal ator, String requestId) {
        validarAtor(ator);
        AnuncioEntity anuncio = anuncioPublicavel(anuncioId);
        List<MidiaElegivel> midias = elegibilidadeService.listar(anuncioId);
        if (midias.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "anuncio sem foto ou video aprovado");
        }

        StorySelecaoAdministrativaEntity selecao = bloquearSelecao(true);
        if (ativaNoInstante(selecao, agora()) && anuncioId.equals(selecao.getAnuncioId())) {
            return toDto(selecao);
        }
        String antes = snapshot(selecao, null);
        String acao = ativaNoInstante(selecao, agora()) ? "STORY_ADMIN_SUBSTITUIR" : "STORY_ADMIN_ATIVAR";
        OffsetDateTime agora = agora();
        selecao.ativar(anuncioId, ator.usuarioId(), agora);
        selecaoRepository.save(selecao);
        String depois = snapshot(selecao, midias);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                ator.usuarioId(),
                acao,
                RECURSO_TIPO,
                anuncio.getId(),
                antes,
                depois,
                requestId,
                agora));
        return toDto(selecao, anuncio, midias);
    }

    @Transactional
    public AdminStorySelecaoDto desativar(AdminUserPrincipal ator, String requestId) {
        validarAtor(ator);
        StorySelecaoAdministrativaEntity selecao = bloquearSelecao(false);
        if (selecao == null || !ativaNoInstante(selecao, agora())) {
            return inativa();
        }
        UUID anuncioAnteriorId = selecao.getAnuncioId();
        String antes = snapshot(selecao, null);
        OffsetDateTime agora = agora();
        selecao.desativar(agora);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                ator.usuarioId(),
                "STORY_ADMIN_DESATIVAR",
                RECURSO_TIPO,
                anuncioAnteriorId,
                antes,
                snapshot(selecao, List.of()),
                requestId,
                agora));
        return toDto(selecao);
    }

    private StorySelecaoAdministrativaEntity bloquearSelecao(boolean criarQuandoAusente) {
        selecaoRepository.bloquearOperacao();
        StorySelecaoAdministrativaEntity atual = selecaoRepository.bloquearSingleton().orElse(null);
        if (atual != null || !criarQuandoAusente) {
            return atual;
        }
        return StorySelecaoAdministrativaEntity.nova(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private AnuncioEntity anuncioPublicavel(UUID anuncioId) {
        return anuncioRepository.findById(anuncioId)
                .filter(anuncio -> anuncio.getRemovidoEm() == null)
                .filter(anuncio -> anuncio.getStatus() == StatusAnuncio.PUBLICADO)
                .filter(anuncio -> anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "anuncio nao esta publico e aprovado"));
    }

    private AdminStorySelecaoDto toDto(StorySelecaoAdministrativaEntity selecao) {
        if (!ativaNoInstante(selecao, agora())) {
            return inativa();
        }
        AnuncioEntity anuncio = anuncioRepository.findById(selecao.getAnuncioId()).orElse(null);
        List<MidiaElegivel> midias = anuncio == null ? List.of() : elegibilidadeService.listar(anuncio.getId());
        return toDto(selecao, anuncio, midias);
    }

    private AdminStorySelecaoDto inativa() {
        return new AdminStorySelecaoDto(
                false, null, null, null, 0, 0, null, null, "RESTRITA_18", null, null);
    }

    private AdminStorySelecaoDto toDto(
            StorySelecaoAdministrativaEntity selecao,
            AnuncioEntity anuncio,
            List<MidiaElegivel> midias) {
        UsuarioEntity ator = selecao.getAtivadoPor() == null
                ? null
                : usuarioRepository.findById(selecao.getAtivadoPor()).orElse(null);
        return new AdminStorySelecaoDto(
                ativaNoInstante(selecao, agora()),
                selecao.getAnuncioId(),
                anuncio == null ? null : anuncio.getSlug(),
                anuncio == null ? null : anuncio.getTitulo(),
                contar(midias, TipoAnuncioMidia.FOTO),
                contar(midias, TipoAnuncioMidia.VIDEO),
                selecao.getAtivadoEm(),
                expiraEm(selecao),
                "RESTRITA_18",
                selecao.getAtivadoPor(),
                ator == null ? null : ator.getEmailNormalizado());
    }

    private AdminStoryCandidatoDto candidato(AnuncioEntity anuncio, UUID selecionadoId) {
        List<MidiaElegivel> midias = elegibilidadeService.listar(anuncio.getId());
        return new AdminStoryCandidatoDto(
                anuncio.getId(),
                anuncio.getSlug(),
                anuncio.getTitulo(),
                contar(midias, TipoAnuncioMidia.FOTO),
                contar(midias, TipoAnuncioMidia.VIDEO),
                anuncio.getId().equals(selecionadoId));
    }

    private long contar(List<MidiaElegivel> midias, TipoAnuncioMidia tipo) {
        return midias.stream().filter(item -> item.vinculo().getTipo() == tipo).count();
    }

    private Specification<AnuncioEntity> candidatoSpec(String termo) {
        return (root, query, builder) -> {
            var predicate = builder.and(
                    builder.isNull(root.get("removidoEm")),
                    builder.equal(root.get("status"), StatusAnuncio.PUBLICADO),
                    builder.equal(root.get("statusModeracao"), StatusModeracaoAnuncio.APROVADO));
            if (termo != null && !termo.isBlank()) {
                String seguro = termo.trim();
                if (seguro.length() > 80 || !seguro.matches("[A-Za-z0-9 ._-]+")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "termo invalido");
                }
                String like = "%" + seguro.toLowerCase() + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("slug")), like),
                        builder.like(builder.lower(root.get("titulo")), like)));
            }
            return predicate;
        };
    }

    private String snapshot(StorySelecaoAdministrativaEntity selecao, List<MidiaElegivel> midias) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("ativa", selecao.isAtiva());
        values.put("anuncioId", selecao.getAnuncioId());
        values.put("ativadoPor", selecao.getAtivadoPor());
        values.put("ativadoEm", selecao.getAtivadoEm() == null ? null : selecao.getAtivadoEm().toString());
        values.put("expiraEm", expiraEm(selecao) == null ? null : expiraEm(selecao).toString());
        values.put("classificacao", "RESTRITA_18");
        if (midias != null) {
            values.put("fotosAprovadas", contar(midias, TipoAnuncioMidia.FOTO));
            values.put("videosAprovados", contar(midias, TipoAnuncioMidia.VIDEO));
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria de Stories", exception);
        }
    }

    private void validarAtor(AdminUserPrincipal ator) {
        if (ator == null || ator.usuarioId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
    }

    private boolean ativaNoInstante(StorySelecaoAdministrativaEntity selecao, OffsetDateTime instante) {
        OffsetDateTime expiraEm = expiraEm(selecao);
        return selecao != null
                && selecao.isAtiva()
                && expiraEm != null
                && expiraEm.isAfter(instante);
    }

    private OffsetDateTime expiraEm(StorySelecaoAdministrativaEntity selecao) {
        return selecao == null || selecao.getAtivadoEm() == null
                ? null
                : selecao.getAtivadoEm().plus(DURACAO_ADMINISTRATIVA);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
