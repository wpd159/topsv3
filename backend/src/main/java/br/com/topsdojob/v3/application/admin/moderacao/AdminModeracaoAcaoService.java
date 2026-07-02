package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminRemeterRevisaoRequestDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.DecisaoModeracaoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DecisaoModeracao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminModeracaoAcaoService {

    private static final int MOTIVO_MAX_LENGTH = 240;

    private final RevisaoAnuncioRepository revisaoRepository;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final DocumentoUsuarioRepository documentoUsuarioRepository;
    private final DecisaoModeracaoRepository decisaoRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final OutboxEventoRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AdminModeracaoAcaoService(
            RevisaoAnuncioRepository revisaoRepository,
            AnuncioRepository anuncioRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            DocumentoUsuarioRepository documentoUsuarioRepository,
            DecisaoModeracaoRepository decisaoRepository,
            AuditoriaEventoRepository auditoriaRepository,
            OutboxEventoRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.revisaoRepository = revisaoRepository;
        this.anuncioRepository = anuncioRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.documentoUsuarioRepository = documentoUsuarioRepository;
        this.decisaoRepository = decisaoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdminAcaoModeracaoResponseDto decidirRevisao(
            UUID id,
            AdminDecidirRevisaoRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        AdminDecisaoModeracaoAcao decisao = validarDecisao(request == null ? null : request.decisao());
        String motivo = motivoSeguroObrigatorioQuandoNecessario(
                decisao,
                request == null ? null : request.motivo(),
                request == null ? null : request.observacao());
        RevisaoAnuncioEntity revisao = revisaoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revisao nao encontrada"));
        if (!revisaoAberta(revisao.getStatus()) || revisao.getFinalizadoEm() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "revisao ja finalizada");
        }
        if (decisaoRepository.existsByRevisaoAnuncioId(revisao.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "decisao de revisao ja registrada");
        }

        AnuncioEntity anuncio = anuncioRepository.findById(revisao.getAnuncioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio da revisao nao encontrado"));
        OffsetDateTime agora = OffsetDateTime.now();
        ClassificacaoConteudo classificacao = classificacaoOuAtual(request.classificacaoConteudo(), anuncio.getClassificacaoConteudo());
        String antes = snapshotRevisao(revisao, anuncio, null, null);

        if (decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE) {
            garantirSolicitacaoAjusteNaoDuplicada(revisao.getId());
        }

        if (decisao == AdminDecisaoModeracaoAcao.APROVAR || decisao == AdminDecisaoModeracaoAcao.REPROVAR) {
            StatusRevisaoAnuncio novoStatusRevisao = decisao == AdminDecisaoModeracaoAcao.APROVAR
                    ? StatusRevisaoAnuncio.APROVADA
                    : StatusRevisaoAnuncio.REJEITADA;
            StatusAnuncio novoStatusAnuncio = decisao == AdminDecisaoModeracaoAcao.APROVAR
                    ? statusAposAprovacao(anuncio.getStatus())
                    : StatusAnuncio.REJEITADO;
            StatusModeracaoAnuncio novoStatusModeracao = decisao == AdminDecisaoModeracaoAcao.APROVAR
                    ? StatusModeracaoAnuncio.APROVADO
                    : statusModeracaoReprovada(classificacao);

            revisao.finalizar(novoStatusRevisao, agora);
            anuncio.aplicarModeracao(novoStatusAnuncio, novoStatusModeracao, classificacao, agora);

            decisaoRepository.save(DecisaoModeracaoEntity.registrar(
                    UUID.randomUUID(),
                    revisao.getId(),
                    decisaoModeracao(decisao),
                    motivo,
                    actor.usuarioId(),
                    agora));
        }

        if (decisao == AdminDecisaoModeracaoAcao.REPROVAR) {
            registrarOutboxLocal(
                    "REVISAO_ANUNCIO",
                    revisao.getId(),
                    "MODERACAO_REPROVADA",
                    outboxPayload(revisao, anuncio, decisao, motivo, null),
                    "MODERACAO_REPROVADA:" + revisao.getId(),
                    agora);
        } else if (decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE) {
            registrarOutboxLocal(
                    "REVISAO_ANUNCIO",
                    revisao.getId(),
                    "MODERACAO_SOLICITAR_AJUSTE",
                    outboxPayload(revisao, anuncio, decisao, motivo, null),
                    "MODERACAO_SOLICITAR_AJUSTE:" + revisao.getId(),
                    agora);
        }

        String depois = snapshotRevisao(revisao, anuncio, decisao, motivo);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                actor.usuarioId(),
                "MODERACAO_REVISAO_DECIDIR",
                "REVISAO_ANUNCIO",
                revisao.getId(),
                antes,
                depois,
                requestId,
                agora));

        return new AdminAcaoModeracaoResponseDto(
                UUID.randomUUID(),
                "REVISAO_ANUNCIO",
                revisao.getId(),
                decisao.name(),
                revisao.getStatus().name(),
                anuncio.getClassificacaoConteudo().name(),
                true,
                false,
                false,
                requestId,
                agora,
                mensagemRevisao(decisao));
    }

    @Transactional
    public AdminAcaoModeracaoResponseDto decidirMidia(
            UUID id,
            AdminDecidirMidiaRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        AdminDecisaoModeracaoAcao decisao = validarDecisao(request == null ? null : request.decisao());
        String motivo = motivoSeguroObrigatorioQuandoNecessario(
                decisao,
                request == null ? null : request.motivo(),
                request == null ? null : request.observacao());
        if (decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "solicitar ajuste de midia pendente sem status compativel no schema atual");
        }
        AnuncioMidiaEntity midia = anuncioMidiaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "midia nao encontrada"));
        if (midia.getStatus() != StatusAnuncioMidia.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "midia ja finalizada");
        }
        if (documentoUsuarioRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(midia.getArquivoMidiaId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo privado nao moderavel como midia publica");
        }

        ArquivoMidiaEntity arquivo = arquivoMidiaRepository.findById(midia.getArquivoMidiaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "arquivo da midia nao encontrado"));
        OffsetDateTime agora = OffsetDateTime.now();
        ClassificacaoConteudo classificacao = classificacaoOuAtual(request.classificacaoConteudo(), midia.getClassificacaoConteudo());
        String antes = snapshotMidia(midia, arquivo, null, null);

        StatusAnuncioMidia novoStatusMidia = decisao == AdminDecisaoModeracaoAcao.APROVAR
                ? StatusAnuncioMidia.PUBLICAVEL
                : StatusAnuncioMidia.REJEITADA;
        StatusArquivoMidia novoStatusArquivo = decisao == AdminDecisaoModeracaoAcao.APROVAR
                ? StatusArquivoMidia.VALIDADO
                : StatusArquivoMidia.REJEITADO;
        midia.aplicarDecisao(novoStatusMidia, classificacao, agora);
        arquivo.aplicarDecisao(novoStatusArquivo, classificacao);

        String depois = snapshotMidia(midia, arquivo, decisao, motivo);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                actor.usuarioId(),
                "MODERACAO_MIDIA_DECIDIR",
                "ANUNCIO_MIDIA",
                midia.getId(),
                antes,
                depois,
                requestId,
                agora));

        return new AdminAcaoModeracaoResponseDto(
                UUID.randomUUID(),
                "ANUNCIO_MIDIA",
                midia.getId(),
                decisao.name(),
                midia.getStatus().name(),
                midia.getClassificacaoConteudo().name(),
                true,
                false,
                false,
                requestId,
                agora,
                mensagemMidia(decisao));
    }

    @Transactional
    public AdminAcaoModeracaoResponseDto remeterAnuncioParaRevisao(
            UUID id,
            AdminRemeterRevisaoRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        AnuncioEntity anuncio = anuncioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        String motivo = motivoSeguroObrigatorio(
                request == null ? null : request.motivo(),
                request == null ? null : request.observacao(),
                "motivo obrigatorio para remeter revisao");
        if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                anuncio.getId(),
                List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio ja possui revisao aberta");
        }

        OffsetDateTime agora = OffsetDateTime.now();
        String antes = snapshotAnuncio(anuncio, null, null);
        UUID revisaoId = UUID.randomUUID();
        RevisaoAnuncioEntity revisao = RevisaoAnuncioEntity.abrir(
                revisaoId,
                anuncio.getId(),
                TipoRevisaoAnuncio.EDICAO,
                payloadRevisaoLocal(motivo),
                actor.usuarioId(),
                agora);
        anuncio.remeterParaRevisao(agora);
        revisaoRepository.save(revisao);
        registrarOutboxLocal(
                "ANUNCIO",
                anuncio.getId(),
                "ANUNCIO_REMETIDO_REVISAO",
                outboxPayload(revisao, anuncio, null, motivo, revisaoId),
                "ANUNCIO_REMETIDO_REVISAO:" + revisaoId,
                agora);

        String depois = snapshotAnuncio(anuncio, revisao, motivo);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                actor.usuarioId(),
                "ANUNCIO_REMETER_REVISAO",
                "ANUNCIO",
                anuncio.getId(),
                antes,
                depois,
                requestId,
                agora));

        return new AdminAcaoModeracaoResponseDto(
                UUID.randomUUID(),
                "ANUNCIO",
                anuncio.getId(),
                "REMETER_REVISAO",
                anuncio.getStatus().name(),
                anuncio.getClassificacaoConteudo().name(),
                true,
                false,
                false,
                requestId,
                agora,
                "anuncio remetido para revisao local; comunicacao real nao enviada");
    }

    private AdminDecisaoModeracaoAcao validarDecisao(AdminDecisaoModeracaoAcao decisao) {
        if (decisao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisao obrigatoria");
        }
        return decisao;
    }

    private String motivoSeguroObrigatorioQuandoNecessario(
            AdminDecisaoModeracaoAcao decisao,
            String motivo,
            String observacao) {
        String motivoSanitizado = AdminModeracaoSanitizer.texto(motivo, MOTIVO_MAX_LENGTH);
        if ((decisao == AdminDecisaoModeracaoAcao.REPROVAR || decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE)
                && (motivoSanitizado == null || motivoSanitizado.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo obrigatorio para reprovar ou solicitar ajuste");
        }
        String observacaoSanitizada = AdminModeracaoSanitizer.texto(observacao, MOTIVO_MAX_LENGTH);
        return motivoSeguro(motivoSanitizado, observacaoSanitizada);
    }

    private String motivoSeguroObrigatorio(
            String motivo,
            String observacao,
            String mensagemErro) {
        String motivoSanitizado = AdminModeracaoSanitizer.texto(motivo, MOTIVO_MAX_LENGTH);
        if (motivoSanitizado == null || motivoSanitizado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagemErro);
        }
        String observacaoSanitizada = AdminModeracaoSanitizer.texto(observacao, MOTIVO_MAX_LENGTH);
        return motivoSeguro(motivoSanitizado, observacaoSanitizada);
    }

    private DecisaoModeracao decisaoModeracao(AdminDecisaoModeracaoAcao decisao) {
        return switch (decisao) {
            case APROVAR -> DecisaoModeracao.APROVAR;
            case REPROVAR -> DecisaoModeracao.REJEITAR;
            case SOLICITAR_AJUSTE -> throw new IllegalStateException("solicitar ajuste nao e decisao final");
        };
    }

    private boolean revisaoAberta(StatusRevisaoAnuncio status) {
        return status == StatusRevisaoAnuncio.ABERTA || status == StatusRevisaoAnuncio.EM_ANALISE;
    }

    private StatusAnuncio statusAposAprovacao(StatusAnuncio atual) {
        if (atual == StatusAnuncio.PENDENTE_REVISAO || atual == StatusAnuncio.RASCUNHO) {
            return StatusAnuncio.APROVADO;
        }
        return atual;
    }

    private StatusModeracaoAnuncio statusModeracaoReprovada(ClassificacaoConteudo classificacao) {
        return classificacao == ClassificacaoConteudo.BLOQUEADO
                ? StatusModeracaoAnuncio.BLOQUEADO
                : StatusModeracaoAnuncio.REJEITADO;
    }

    private ClassificacaoConteudo classificacaoOuAtual(
            ClassificacaoConteudo solicitada,
            ClassificacaoConteudo atual) {
        if (solicitada != null) {
            return solicitada;
        }
        return atual == null ? ClassificacaoConteudo.LIVRE : atual;
    }

    private String motivoSeguro(String motivo, String observacao) {
        String combinado = String.join(
                " ",
                motivo == null ? "" : motivo,
                observacao == null ? "" : observacao).trim();
        return AdminModeracaoSanitizer.texto(combinado, MOTIVO_MAX_LENGTH);
    }

    private String snapshotRevisao(
            RevisaoAnuncioEntity revisao,
            AnuncioEntity anuncio,
            AdminDecisaoModeracaoAcao decisao,
            String motivoSanitizado) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("revisaoId", revisao.getId());
        values.put("anuncioId", anuncio.getId());
        values.put("statusRevisao", enumName(revisao.getStatus()));
        values.put("statusAnuncio", enumName(anuncio.getStatus()));
        values.put("statusModeracao", enumName(anuncio.getStatusModeracao()));
        values.put("classificacaoConteudo", enumName(anuncio.getClassificacaoConteudo()));
        values.put("decisao", enumName(decisao));
        values.put("motivoSanitizado", motivoSanitizado);
        values.put("payloadSolicitadoOculto", true);
        values.put("emailRealEnviado", false);
        values.put("hardDeleteExecutado", false);
        return toJson(values);
    }

    private String snapshotMidia(
            AnuncioMidiaEntity midia,
            ArquivoMidiaEntity arquivo,
            AdminDecisaoModeracaoAcao decisao,
            String motivoSanitizado) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("midiaId", midia.getId());
        values.put("anuncioId", midia.getAnuncioId());
        values.put("statusMidia", enumName(midia.getStatus()));
        values.put("statusArquivo", enumName(arquivo.getStatusArquivo()));
        values.put("tipo", enumName(midia.getTipo()));
        values.put("classificacaoConteudo", enumName(midia.getClassificacaoConteudo()));
        values.put("decisao", enumName(decisao));
        values.put("motivoSanitizado", motivoSanitizado);
        values.put("arquivoPrivadoOculto", true);
        values.put("storyExigeIdade", midia.getTipo() == TipoAnuncioMidia.STORY);
        values.put("storageOculto", true);
        values.put("hardDeleteExecutado", false);
        return toJson(values);
    }

    private String snapshotAnuncio(
            AnuncioEntity anuncio,
            RevisaoAnuncioEntity revisao,
            String motivoSanitizado) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("anuncioId", anuncio.getId());
        values.put("revisaoId", revisao == null ? null : revisao.getId());
        values.put("statusAnuncio", enumName(anuncio.getStatus()));
        values.put("statusModeracao", enumName(anuncio.getStatusModeracao()));
        values.put("classificacaoConteudo", enumName(anuncio.getClassificacaoConteudo()));
        values.put("motivoSanitizado", motivoSanitizado);
        values.put("payloadSolicitadoOculto", true);
        values.put("emailRealEnviado", false);
        values.put("whatsappRealEnviado", false);
        values.put("hardDeleteExecutado", false);
        return toJson(values);
    }

    private String outboxPayload(
            RevisaoAnuncioEntity revisao,
            AnuncioEntity anuncio,
            AdminDecisaoModeracaoAcao decisao,
            String motivoSanitizado,
            UUID revisaoCriadaId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("anuncioId", anuncio.getId());
        values.put("revisaoId", revisao == null ? revisaoCriadaId : revisao.getId());
        values.put("decisao", enumName(decisao));
        values.put("statusAnuncio", enumName(anuncio.getStatus()));
        values.put("statusModeracao", enumName(anuncio.getStatusModeracao()));
        values.put("motivoSanitizado", motivoSanitizado);
        values.put("emailRealEnviado", false);
        values.put("whatsappRealEnviado", false);
        values.put("hardDeleteExecutado", false);
        values.put("envioExternoPendente", true);
        return toJson(values);
    }

    private String payloadRevisaoLocal(String motivoSanitizado) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("origem", "admin-local");
        values.put("motivoSanitizado", motivoSanitizado);
        values.put("dadoReal", false);
        values.put("envioExterno", false);
        return toJson(values);
    }

    private void registrarOutboxLocal(
            String aggregateTipo,
            UUID aggregateId,
            String tipoEvento,
            String payloadJson,
            String idempotencyKey,
            OffsetDateTime agora) {
        if (outboxRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        outboxRepository.save(OutboxEventoEntity.registrarPendente(
                UUID.randomUUID(),
                aggregateTipo,
                aggregateId,
                tipoEvento,
                payloadJson,
                idempotencyKey,
                agora));
    }

    private void garantirSolicitacaoAjusteNaoDuplicada(UUID revisaoId) {
        String idempotencyKey = "MODERACAO_SOLICITAR_AJUSTE:" + revisaoId;
        if (outboxRepository.existsByTipoEventoAndIdempotencyKeyAndStatus(
                "MODERACAO_SOLICITAR_AJUSTE",
                idempotencyKey,
                StatusOutbox.PENDENTE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "solicitacao de ajuste ja registrada para revisao");
        }
    }

    private String toJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falha ao registrar auditoria");
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String mensagemRevisao(AdminDecisaoModeracaoAcao decisao) {
        return switch (decisao) {
            case APROVAR -> "revisao aprovada localmente";
            case REPROVAR -> "revisao reprovada localmente; e-mail real pendente para fase futura";
            case SOLICITAR_AJUSTE -> "ajuste solicitado localmente; revisao permanece aberta para decisao final futura";
        };
    }

    private String mensagemMidia(AdminDecisaoModeracaoAcao decisao) {
        return decisao == AdminDecisaoModeracaoAcao.APROVAR
                ? "midia aprovada localmente"
                : "midia reprovada localmente";
    }
}
