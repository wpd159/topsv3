package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioCicloVidaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
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
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeuAnuncioCicloVidaService {

    private final MeusAnunciosConsultaService consultaService;
    private final AnuncioRepository anuncioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public MeuAnuncioCicloVidaService(
            MeusAnunciosConsultaService consultaService,
            AnuncioRepository anuncioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper) {
        this.consultaService = consultaService;
        this.anuncioRepository = anuncioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
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

    private MeuAnuncioCicloVidaDto executar(
            String slug,
            Authentication authentication,
            String requestId,
            String acaoAuditoria,
            BiConsumer<AnuncioEntity, OffsetDateTime> transicao) {
        UUID usuarioId = consultaService.usuarioAutenticado(authentication).getId();
        AnuncioEntity anuncio = anuncioRepository
                .findBySlugForLifecycle(consultaService.slugSeguro(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        if (!usuarioId.equals(anuncio.getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "anuncio pertence a outro usuario");
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
        try {
            return objectMapper.writeValueAsString(estado);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falha ao registrar auditoria");
        }
    }
}
