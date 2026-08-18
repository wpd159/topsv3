package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLotePrevalidacaoService.ItemValidado;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminResultadoFotoLoteItemDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminModeracaoFotosLoteItemService {

    private final AdminModeracaoAcaoService moderacaoAcaoService;
    private final AdminAnuncioMidiaCleanupService midiaCleanupService;
    private final AnuncioRepository anuncioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public AdminModeracaoFotosLoteItemService(
            AdminModeracaoAcaoService moderacaoAcaoService,
            AdminAnuncioMidiaCleanupService midiaCleanupService,
            AnuncioRepository anuncioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper) {
        this.moderacaoAcaoService = moderacaoAcaoService;
        this.midiaCleanupService = midiaCleanupService;
        this.anuncioRepository = anuncioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminResultadoFotoLoteItemDto executar(
            UUID anuncioId,
            ItemValidado item,
            AdminUserPrincipal ator,
            String requestId) {
        if (item.decisao() == AdminDecisaoFotoLoteAcao.APROVAR) {
            var resposta = moderacaoAcaoService.decidirMidia(
                    item.mediaId(),
                    new AdminDecidirMidiaRequestDto(
                            anuncioId,
                            AdminDecisaoModeracaoAcao.APROVAR,
                            item.classificacao(),
                            null,
                            item.observacao(),
                            null),
                    ator,
                    requestId);
            return new AdminResultadoFotoLoteItemDto(
                    item.mediaId(),
                    item.decisao().name(),
                    item.classificacao().name(),
                    "APROVADA",
                    resposta.status(),
                    null,
                    null);
        }

        var anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "anuncio nao encontrado"));
        if (anuncio.getStatus() == StatusAnuncio.BLOQUEADO
                || anuncio.getStatus() == StatusAnuncio.REMOVIDO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "estado do anuncio impede exclusao da foto");
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        var limpeza = midiaCleanupService.limparMidia(anuncioId, item.mediaId(), agora);
        if (limpeza.jaProcessado()) {
            return new AdminResultadoFotoLoteItemDto(
                    item.mediaId(),
                    item.decisao().name(),
                    null,
                    "JA_PROCESSADA",
                    "REMOVIDA",
                    null,
                    null);
        }
        Map<String, Object> depois = new LinkedHashMap<>();
        depois.put("anuncioId", anuncioId);
        depois.put("decisao", "EXCLUIR");
        depois.put("statusMidia", "REMOVIDA");
        depois.put("midiasRemovidas", limpeza.midiasRemovidas());
        depois.put("objetosR2Excluidos", limpeza.objetosExcluidos());
        depois.put("objetosR2JaAusentes", limpeza.objetosJaAusentes());
        depois.put("objetosCompartilhadosPreservados", limpeza.objetosCompartilhadosPreservados());
        depois.put("objetosCleanupPosCommit", limpeza.objetosCleanupAgendados());
        depois.put("storiesEncerrados", limpeza.storiesEncerrados());
        depois.put("storageOculto", true);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                ator.usuarioId(),
                "MODERACAO_FOTO_EXCLUIR",
                "ANUNCIO_MIDIA",
                item.mediaId(),
                null,
                json(depois),
                requestId,
                agora));
        return new AdminResultadoFotoLoteItemDto(
                item.mediaId(),
                item.decisao().name(),
                null,
                "EXCLUIDA",
                "REMOVIDA",
                null,
                null);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria da foto", exception);
        }
    }
}
