package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioCicloVidaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioStatusHistoricoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeuAnuncioCicloVidaService {

    private final MeusAnunciosConsultaService consultaService;
    private final AnuncioRepository anuncioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;
    private final AnuncioStatusHistoricoRepository historicoRepository;
    private final DocumentoBuscaAnuncioRepository buscaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final FotoElegivelAnuncioPolicy fotoElegivelPolicy;

    public MeuAnuncioCicloVidaService(
            MeusAnunciosConsultaService consultaService,
            AnuncioRepository anuncioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper,
            AnuncioStatusHistoricoRepository historicoRepository,
            DocumentoBuscaAnuncioRepository buscaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            FotoElegivelAnuncioPolicy fotoElegivelPolicy) {
        this.consultaService = consultaService;
        this.anuncioRepository = anuncioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
        this.historicoRepository = historicoRepository;
        this.buscaRepository = buscaRepository;
        this.revisaoRepository = revisaoRepository;
        this.fotoElegivelPolicy = fotoElegivelPolicy;
    }

    @Transactional
    public MeuAnuncioCicloVidaDto pausar(
            String slug,
            Authentication authentication,
            String requestId) {
        return executar(
                slug,
                authentication,
                requestId,
                "ANUNCIO_PAUSADO_PELO_USUARIO",
                AnuncioEntity::pausarPeloProprietario);
    }

    @Transactional
    public MeuAnuncioCicloVidaDto reativar(
            String slug,
            Authentication authentication,
            String requestId) {
        return executar(
                slug,
                authentication,
                requestId,
                "ANUNCIO_REATIVADO_PELO_USUARIO",
                AnuncioEntity::reativarPeloProprietario);
    }

    @Transactional
    public MeuAnuncioCicloVidaDto remover(
            String slug,
            Authentication authentication,
            String requestId) {
        return executar(
                slug,
                authentication,
                requestId,
                "ANUNCIO_REMOVIDO_PELO_USUARIO",
                AnuncioEntity::removerPeloProprietario);
    }

    /** The caller holds owner, advertisement and media/file locks in that order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void encerrarPorUltimaFoto(
            AnuncioEntity anuncio,
            UUID midiaId,
            String requestId,
            OffsetDateTime agora) {
        var revisoes = revisaoRepository.findByAnuncioId(anuncio.getId());
        if (revisoes.stream().anyMatch(revisao -> revisao.getStatus() == StatusRevisaoAnuncio.EM_ANALISE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio possui revisao em analise");
        }
        String antes = snapshot(anuncio);
        StatusAnuncio anterior = anuncio.getStatus();
        try {
            anuncio.removerPeloProprietario(agora);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio nao pode ser encerrado", exception);
        }
        anuncioRepository.save(anuncio);
        historicoRepository.save(AnuncioStatusHistoricoEntity.registrar(
                UUID.randomUUID(), anuncio.getId(), anterior, anuncio.getStatus(),
                "ULTIMA_FOTO_REMOVIDA", anuncio.getUsuarioId(), agora));
        for (var revisao : revisoes) {
            if (revisao.getStatus() == StatusRevisaoAnuncio.ABERTA) {
                revisao.finalizar(StatusRevisaoAnuncio.CANCELADA, agora);
            }
        }
        revisaoRepository.saveAll(revisoes);
        buscaRepository.findById(anuncio.getId()).ifPresent(documento -> {
            documento.removerDaBusca(agora);
            buscaRepository.save(documento);
        });
        // No object deletion, broad media cleanup or account transition. All records
        // above and the triggering media removal commit (or roll back) together.
        Map<String, Object> depois = new LinkedHashMap<>();
        depois.put("status", anuncio.getStatus().name());
        depois.put("statusModeracao", anuncio.getStatusModeracao().name());
        depois.put("removido", true);
        depois.put("midiaId", midiaId);
        auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
                UUID.randomUUID(), anuncio.getUsuarioId(), "ANUNCIO_ENCERRADO_SEM_FOTOS", "ANUNCIO",
                anuncio.getId(), antes, json(depois), requestId, agora));
    }

    private MeuAnuncioCicloVidaDto executar(
            String slug,
            Authentication authentication,
            String requestId,
            String acaoAuditoria,
            BiConsumer<AnuncioEntity, OffsetDateTime> transicao) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuarioParaAtualizacao(slug, authentication);
        UUID usuarioId = anuncio.getUsuarioId();
        if ("ANUNCIO_REATIVADO_PELO_USUARIO".equals(acaoAuditoria)) {
            fotoElegivelPolicy.validarParaAprovacao(anuncio.getId());
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String antes = snapshot(anuncio);
        try {
            transicao.accept(anuncio, agora);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "transicao de anuncio invalida");
        }
        anuncioRepository.save(anuncio);
        auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
                UUID.randomUUID(),
                usuarioId,
                acaoAuditoria,
                "ANUNCIO",
                anuncio.getId(),
                antes,
                snapshot(anuncio),
                requestId,
                agora));
        return resposta(anuncio);
    }

    private MeuAnuncioCicloVidaDto resposta(AnuncioEntity anuncio) {
        return new MeuAnuncioCicloVidaDto(
                anuncio.getId(),
                anuncio.getSlug(),
                anuncio.getStatus().name(),
                anuncio.getStatusModeracao().name(),
                anuncio.getAtualizadoEm(),
                consultaService.acoesPermitidas(anuncio));
    }

    private String snapshot(AnuncioEntity anuncio) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("status", anuncio.getStatus().name());
        estado.put("statusModeracao", anuncio.getStatusModeracao().name());
        estado.put("removido", anuncio.getRemovidoEm() != null);
        return json(estado);
    }

    private String json(Object estado) {
        try {
            return objectMapper.writeValueAsString(estado);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falha ao registrar auditoria");
        }
    }
}
