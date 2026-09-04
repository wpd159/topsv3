package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminRemeterRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminReclassificarMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.premium.BeneficioFotosExtrasModeracaoService;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService.PreviewGenerationException;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.DecisaoModeracaoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DecisaoModeracao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminModeracaoAcaoService {

    private static final int MOTIVO_MAX_LENGTH = 2_000;
    private static final String MOTIVO_APROVACAO_AUTOMATICA =
            "Revisao aberta automaticamente pela operacao unica de aprovacao e publicacao.";

    private final RevisaoAnuncioRepository revisaoRepository;
    private final AnuncioRepository anuncioRepository;
    private final UsuarioRepository usuarioRepository;
    private final AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final DocumentoUsuarioRepository documentoUsuarioRepository;
    private final DecisaoModeracaoRepository decisaoRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final OutboxEventoRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MidiaStorageAprovacaoService midiaStorageAprovacaoService;
    private final BeneficioFotosExtrasModeracaoService fotosExtrasModeracaoService;
    private final FotoElegivelAnuncioPolicy fotoElegivelAnuncioPolicy;
    private final String canonicalDomain;
    private final Clock clock;

    @Autowired
    public AdminModeracaoAcaoService(
            RevisaoAnuncioRepository revisaoRepository,
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            DocumentoUsuarioRepository documentoUsuarioRepository,
            DecisaoModeracaoRepository decisaoRepository,
            AuditoriaEventoRepository auditoriaRepository,
            OutboxEventoRepository outboxRepository,
            ObjectMapper objectMapper,
            MidiaStorageAprovacaoService midiaStorageAprovacaoService,
            BeneficioFotosExtrasModeracaoService fotosExtrasModeracaoService,
            FotoElegivelAnuncioPolicy fotoElegivelAnuncioPolicy,
            @Value("${app.canonical-domain:http://localhost}") String canonicalDomain) {
        this(
                revisaoRepository,
                anuncioRepository,
                usuarioRepository,
                bloqueioJuridicoRepository,
                anuncioMidiaRepository,
                arquivoMidiaRepository,
                documentoUsuarioRepository,
                decisaoRepository,
                auditoriaRepository,
                outboxRepository,
                objectMapper,
                midiaStorageAprovacaoService,
                fotosExtrasModeracaoService,
                fotoElegivelAnuncioPolicy,
                canonicalDomain,
                Clock.systemUTC());
    }

    AdminModeracaoAcaoService(
            RevisaoAnuncioRepository revisaoRepository,
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            DocumentoUsuarioRepository documentoUsuarioRepository,
            DecisaoModeracaoRepository decisaoRepository,
            AuditoriaEventoRepository auditoriaRepository,
            OutboxEventoRepository outboxRepository,
            ObjectMapper objectMapper,
            MidiaStorageAprovacaoService midiaStorageAprovacaoService,
            BeneficioFotosExtrasModeracaoService fotosExtrasModeracaoService,
            FotoElegivelAnuncioPolicy fotoElegivelAnuncioPolicy,
            String canonicalDomain,
            Clock clock) {
        this.revisaoRepository = revisaoRepository;
        this.anuncioRepository = anuncioRepository;
        this.usuarioRepository = usuarioRepository;
        this.bloqueioJuridicoRepository = bloqueioJuridicoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.documentoUsuarioRepository = documentoUsuarioRepository;
        this.decisaoRepository = decisaoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.midiaStorageAprovacaoService = midiaStorageAprovacaoService;
        this.fotosExtrasModeracaoService = fotosExtrasModeracaoService;
        this.fotoElegivelAnuncioPolicy = fotoElegivelAnuncioPolicy;
        this.canonicalDomain = canonicalDomain;
        this.clock = clock;
    }

    @Transactional
    public AdminAcaoModeracaoResponseDto aprovarEPublicarAnuncio(
            UUID anuncioId,
            AdminUserPrincipal actor,
            String requestId) {
        validarAtor(actor);
        AnuncioEntity anuncio = carregarContextoDecisaoFinal(
                        anuncioId,
                        AdminDecisaoModeracaoAcao.APROVAR)
                .anuncio();
        fotoElegivelAnuncioPolicy.validarParaAprovacao(anuncio.getId());

        if (anuncio.getStatus() == StatusAnuncio.PUBLICADO
                && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO) {
            return respostaAprovacaoIdempotente(anuncio, requestId);
        }
        if (anuncio.getStatus() == StatusAnuncio.APROVADO
                && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO) {
            return regularizarAprovacaoSemPublicacao(anuncio, actor, requestId);
        }
        if (anuncio.getStatus() != StatusAnuncio.PENDENTE_REVISAO
                || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.PENDENTE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "estado do anuncio impede aprovacao e publicacao");
        }

        RevisaoAnuncioEntity revisao = revisaoRepository
                .findFirstByAnuncioIdAndStatusInOrderByCriadoEmDesc(
                        anuncioId,
                        List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE))
                .orElseGet(() -> {
                    OffsetDateTime agora = agora();
                    RevisaoAnuncioEntity criada = RevisaoAnuncioEntity.abrir(
                            UUID.randomUUID(),
                            anuncioId,
                            TipoRevisaoAnuncio.EDICAO,
                            payloadRevisaoLocal(MOTIVO_APROVACAO_AUTOMATICA),
                            actor.usuarioId(),
                            agora);
                    return revisaoRepository.save(criada);
                });

        return decidirRevisaoCarregada(
                revisao,
                anuncio,
                AdminDecisaoModeracaoAcao.APROVAR,
                null,
                actor,
                requestId);
    }

    @Transactional
    public AdminAcaoModeracaoResponseDto decidirRevisao(
            UUID id,
            AdminDecidirRevisaoRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        validarAtor(actor);
        AdminDecisaoModeracaoAcao decisao = validarDecisao(request == null ? null : request.decisao());
        String motivo = motivoSeguroObrigatorioQuandoNecessario(
                decisao,
                request == null ? null : request.motivo(),
                request == null ? null : request.observacao());
        RevisaoAnuncioEntity referencia = revisaoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revisao nao encontrada"));
        AnuncioEntity anuncio = decisao == AdminDecisaoModeracaoAcao.APROVAR
                        || decisao == AdminDecisaoModeracaoAcao.REPROVAR
                ? carregarContextoDecisaoFinal(referencia.getAnuncioId(), decisao).anuncio()
                : anuncioRepository.findByIdForModeration(referencia.getAnuncioId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "anuncio da revisao nao encontrado"));
        if (decisao == AdminDecisaoModeracaoAcao.APROVAR) {
            fotoElegivelAnuncioPolicy.validarParaAprovacao(anuncio.getId());
        }
        RevisaoAnuncioEntity revisao = revisaoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revisao nao encontrada"));
        if (!revisao.getAnuncioId().equals(anuncio.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "revisao nao pertence ao anuncio informado");
        }
        return decidirRevisaoCarregada(revisao, anuncio, decisao, motivo, actor, requestId);
    }

    private AdminAcaoModeracaoResponseDto decidirRevisaoCarregada(
            RevisaoAnuncioEntity revisao,
            AnuncioEntity anuncio,
            AdminDecisaoModeracaoAcao decisao,
            String motivo,
            AdminUserPrincipal actor,
            String requestId) {
        if (!revisaoAberta(revisao.getStatus()) || revisao.getFinalizadoEm() != null) {
            if (decisao == AdminDecisaoModeracaoAcao.APROVAR) {
                return repetirOuRegularizarAprovacao(revisao, anuncio, actor, requestId);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "revisao ja finalizada");
        }
        if (decisaoRepository.existsByRevisaoAnuncioId(revisao.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "decisao de revisao ja registrada");
        }
        if (anuncio.getStatus() == StatusAnuncio.BLOQUEADO
                || anuncio.getStatus() == StatusAnuncio.REMOVIDO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "estado do anuncio impede decisao de moderacao");
        }
        if ((decisao == AdminDecisaoModeracaoAcao.APROVAR
                        || decisao == AdminDecisaoModeracaoAcao.REPROVAR)
                && (anuncio.getStatus() != StatusAnuncio.PENDENTE_REVISAO
                        || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.PENDENTE)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    decisao == AdminDecisaoModeracaoAcao.APROVAR
                            ? "estado do anuncio impede aprovacao e publicacao"
                            : "estado do anuncio impede reprovacao");
        }
        if (decisao == AdminDecisaoModeracaoAcao.APROVAR) {
            validarKycAprovado(anuncio.getUsuarioId());
        }
        OffsetDateTime agora = agora();
        String antes = snapshotRevisao(revisao, anuncio, null, null);

        if (decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE) {
            garantirSolicitacaoAjusteNaoDuplicada(revisao.getId());
        }

        if (decisao == AdminDecisaoModeracaoAcao.APROVAR || decisao == AdminDecisaoModeracaoAcao.REPROVAR) {
            StatusRevisaoAnuncio novoStatusRevisao = decisao == AdminDecisaoModeracaoAcao.APROVAR
                    ? StatusRevisaoAnuncio.APROVADA
                    : StatusRevisaoAnuncio.REJEITADA;

            revisao.finalizar(novoStatusRevisao, agora);
            if (decisao == AdminDecisaoModeracaoAcao.APROVAR) {
                try {
                    anuncio.aprovarEPublicarAdministrativamente(agora);
                } catch (IllegalStateException exception) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "estado do anuncio impede aprovacao e publicacao",
                            exception);
                }
            } else {
                anuncio.aplicarModeracao(
                        StatusAnuncio.REJEITADO,
                        StatusModeracaoAnuncio.REJEITADO,
                        agora);
            }

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
                null,
                true,
                false,
                false,
                requestId,
                agora,
                mensagemRevisao(decisao));
    }

    @Transactional(noRollbackFor = PreviewGenerationException.class)
    public AdminAcaoModeracaoResponseDto decidirMidia(
            UUID id,
            AdminDecidirMidiaRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        validarAtor(actor);
        if (request == null || request.anuncioId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "anuncio obrigatorio para decidir midia");
        }
        AdminDecisaoModeracaoAcao decisao = validarDecisao(request.decisao());
        String motivo = motivoSeguroObrigatorioQuandoNecessario(
                decisao,
                request.motivo(),
                request.observacao());
        AnuncioMidiaRepository.ReferenciaMidiaProjection referencia =
                anuncioMidiaRepository.findReferenciaById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "midia nao encontrada"));
        if (!request.anuncioId().equals(referencia.getAnuncioId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "midia nao pertence ao anuncio informado");
        }
        if (referencia.getArquivoMidiaId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "arquivo da midia nao encontrado");
        }

        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(request.anuncioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio da midia nao encontrado"));
        List<AnuncioMidiaEntity> vinculosBloqueados =
                anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncio.getId());
        AnuncioMidiaEntity midia = vinculosBloqueados.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "midia nao pertence ao anuncio informado"));
        if (!anuncio.getId().equals(midia.getAnuncioId())
                || !referencia.getArquivoMidiaId().equals(midia.getArquivoMidiaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "associacao da midia mudou durante a decisao");
        }
        List<AnuncioMidiaEntity> associacoesAntesDoArquivo =
                anuncioMidiaRepository.findByArquivoMidiaId(referencia.getArquivoMidiaId());
        if (possuiAssociacaoAtivaComOutroAnuncio(associacoesAntesDoArquivo, anuncio.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "arquivo associado a outro anuncio nao pode ser moderado");
        }
        List<UUID> vinculoIdsReafirmados = associacoesAntesDoArquivo.stream()
                .filter(item -> anuncio.getId().equals(item.getAnuncioId()))
                .map(AnuncioMidiaEntity::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (!vinculoIdsReafirmados.isEmpty()) {
            anuncioMidiaRepository.findByIdInForUpdate(vinculoIdsReafirmados);
        }
        ArquivoMidiaEntity arquivo = arquivoMidiaRepository
                .findByIdInForUpdate(List.of(referencia.getArquivoMidiaId())).stream()
                .filter(item -> referencia.getArquivoMidiaId().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "arquivo da midia nao encontrado"));

        if (!anuncio.getId().equals(midia.getAnuncioId())
                || !referencia.getArquivoMidiaId().equals(midia.getArquivoMidiaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "associacao da midia mudou durante a decisao");
        }
        if (anuncio.getStatus() == StatusAnuncio.BLOQUEADO || anuncio.getStatus() == StatusAnuncio.REMOVIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "estado do anuncio impede moderacao de midia");
        }
        if (midia.getTipo() == TipoAnuncioMidia.STORY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "story nao participa da moderacao de midia");
        }
        if (midia.getStatus() != StatusAnuncioMidia.PENDENTE
                && midia.getStatus() != StatusAnuncioMidia.AJUSTE_SOLICITADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "midia ja finalizada");
        }
        List<AnuncioMidiaEntity> associacoesRevalidadas =
                anuncioMidiaRepository.findByArquivoMidiaId(midia.getArquivoMidiaId());
        if (possuiAssociacaoAtivaComOutroAnuncio(associacoesRevalidadas, anuncio.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "arquivo associado a outro anuncio nao pode ser moderado");
        }
        if (anuncioMidiaRepository.existsDocumentoUsuarioHistoricoPorArquivoId(midia.getArquivoMidiaId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo privado nao moderavel como midia publica");
        }
        OffsetDateTime agora = agora();
        VisibilidadeMidia visibilidade = visibilidadeParaDecisao(midia, request, decisao);
        if (decisao == AdminDecisaoModeracaoAcao.APROVAR && visibilidade == VisibilidadeMidia.LIVRE) {
            motivo = null;
        }
        garantirDerivadoMarcadoParaFotoLivre(midia, arquivo, decisao, visibilidade);
        String antes = snapshotMidia(midia, arquivo, null, null);

        StatusAnuncioMidia novoStatusMidia = switch (decisao) {
            case APROVAR -> StatusAnuncioMidia.PUBLICAVEL;
            case REPROVAR -> StatusAnuncioMidia.REJEITADA;
            case SOLICITAR_AJUSTE -> StatusAnuncioMidia.AJUSTE_SOLICITADO;
        };
        StatusArquivoMidia novoStatusArquivo = switch (decisao) {
            case APROVAR -> StatusArquivoMidia.VALIDADO;
            case REPROVAR -> StatusArquivoMidia.REJEITADO;
            case SOLICITAR_AJUSTE -> StatusArquivoMidia.PENDENTE;
        };
        if (decisao == AdminDecisaoModeracaoAcao.APROVAR
                && midia.getTipo() == TipoAnuncioMidia.FOTO) {
            midiaStorageAprovacaoService.prepararAprovacao(arquivo, visibilidade);
        }
        midia.aplicarDecisao(novoStatusMidia, visibilidade, agora);
        arquivo.aplicarDecisao(novoStatusArquivo);
        if (decisao == AdminDecisaoModeracaoAcao.APROVAR
                && midia.getTipo() == TipoAnuncioMidia.FOTO) {
            fotosExtrasModeracaoService.iniciarSeCapacidadeAdicionalAprovada(
                    anuncio.getId(),
                    midia.getId(),
                    actor.usuarioId(),
                    requestId,
                    agora);
        }

        if (decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE) {
            registrarOutboxLocal(
                    "ANUNCIO_MIDIA",
                    midia.getId(),
                    "MODERACAO_MIDIA_SOLICITAR_AJUSTE",
                    snapshotMidia(midia, arquivo, decisao, motivo),
                    "MODERACAO_MIDIA_SOLICITAR_AJUSTE:" + midia.getId() + ":" + agora,
                    agora);
        }

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
                enumName(midia.getVisibilidadeMidia()),
                true,
                false,
                false,
                requestId,
                agora,
                mensagemMidia(decisao));
    }

    private boolean possuiAssociacaoAtivaComOutroAnuncio(
            List<AnuncioMidiaEntity> associacoes,
            UUID anuncioId) {
        return associacoes.stream()
                .anyMatch(item -> item.getStatus() != StatusAnuncioMidia.REMOVIDA
                        && !anuncioId.equals(item.getAnuncioId()));
    }

    @Transactional(noRollbackFor = PreviewGenerationException.class)
    public AdminAcaoModeracaoResponseDto reclassificarMidia(
            UUID id,
            AdminReclassificarMidiaRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        validarAtor(actor);
        VisibilidadeMidia novaVisibilidade = request == null ? null : request.visibilidadeMidia();
        if (novaVisibilidade == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibilidade obrigatoria");
        }
        String motivo = motivoSeguroObrigatorio(
                request.motivo(),
                null,
                "motivo obrigatorio para reclassificar midia");
        AnuncioMidiaEntity midia = anuncioMidiaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "midia nao encontrada"));
        if (midia.getTipo() == TipoAnuncioMidia.STORY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "story nao participa da moderacao de midia");
        }
        if (midia.getTipo() == TipoAnuncioMidia.VIDEO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "video exige visibilidade RESTRITA_18");
        }
        if (midia.getStatus() != StatusAnuncioMidia.PUBLICAVEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "somente foto finalizada pode ser reclassificada");
        }
        if (midia.getVisibilidadeMidia() == novaVisibilidade) {
            return new AdminAcaoModeracaoResponseDto(
                    UUID.randomUUID(),
                    "ANUNCIO_MIDIA",
                    midia.getId(),
                    "RECLASSIFICAR",
                    midia.getStatus().name(),
                    novaVisibilidade.name(),
                    false,
                    false,
                    false,
                    requestId,
                    agora(),
                    "classificacao ja estava aplicada");
        }
        if (documentoUsuarioRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(
                midia.getArquivoMidiaId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo privado nao moderavel como midia publica");
        }

        ArquivoMidiaEntity arquivo = arquivoMidiaRepository.findByIdForUpdate(midia.getArquivoMidiaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "arquivo da midia nao encontrado"));
        if (arquivo.getStatusArquivo() != StatusArquivoMidia.VALIDADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "arquivo da foto nao esta validado");
        }
        if (novaVisibilidade == VisibilidadeMidia.LIVRE) {
            garantirDerivadoMarcado(arquivo);
        }

        OffsetDateTime agora = agora();
        String antes = snapshotMidia(midia, arquivo, null, motivo);
        midiaStorageAprovacaoService.prepararReclassificacao(
                arquivo,
                midia.getVisibilidadeMidia(),
                novaVisibilidade);
        midia.aplicarDecisao(StatusAnuncioMidia.PUBLICAVEL, novaVisibilidade, agora);
        String depois = snapshotMidia(midia, arquivo, null, motivo);
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                actor.usuarioId(),
                "MODERACAO_MIDIA_RECLASSIFICAR",
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
                "RECLASSIFICAR",
                midia.getStatus().name(),
                novaVisibilidade.name(),
                true,
                false,
                false,
                requestId,
                agora,
                "foto reclassificada com storage reconciliado");
    }

    @Transactional
    public AdminAcaoModeracaoResponseDto remeterAnuncioParaRevisao(
            UUID id,
            AdminRemeterRevisaoRequestDto request,
            AdminUserPrincipal actor,
            String requestId) {
        validarAtor(actor);
        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        if (anuncio.getStatus() == StatusAnuncio.BLOQUEADO
                || anuncio.getStatus() == StatusAnuncio.REMOVIDO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "estado do anuncio impede abertura de revisao");
        }
        String motivo = motivoSeguroObrigatorio(
                request == null ? null : request.motivo(),
                request == null ? null : request.observacao(),
                "motivo obrigatorio para remeter revisao");
        if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                anuncio.getId(),
                List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio ja possui revisao aberta");
        }

        OffsetDateTime agora = agora();
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
                null,
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

    private void validarAtor(AdminUserPrincipal actor) {
        if (actor == null || actor.usuarioId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ator administrativo invalido");
        }
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

    private ContextoPublicacao carregarContextoDecisaoFinal(
            UUID anuncioId,
            AdminDecisaoModeracaoAcao decisao) {
        AnuncioEntity referencia = anuncioRepository.findById(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "anuncio da revisao nao encontrado"));
        UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(referencia.getUsuarioId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "proprietario do anuncio nao encontrado"));
        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "anuncio da revisao nao encontrado"));
        if (!usuario.getId().equals(anuncio.getUsuarioId())
                || usuario.getStatus() != StatusUsuario.ATIVO
                || usuario.getDesativadoEm() != null
                || usuario.getExcluidoEm() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    decisao == AdminDecisaoModeracaoAcao.APROVAR
                            ? "O proprietario esta suspenso. Desbloqueie o usuario antes de aprovar o anuncio."
                            : "estado do proprietario impede reprovacao");
        }
        if (bloqueioJuridicoRepository.findAtivoPorAnuncioForUpdate(anuncio.getId()).isPresent()
                || bloqueioJuridicoRepository.findAtivoPorUsuarioForUpdate(
                        usuario.getId(),
                        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    decisao == AdminDecisaoModeracaoAcao.APROVAR
                            ? "bloqueio juridico impede aprovacao e publicacao"
                            : "bloqueio juridico impede reprovacao");
        }
        return new ContextoPublicacao(anuncio, usuario);
    }

    private void validarKycAprovado(UUID usuarioId) {
        var documentos = documentoUsuarioRepository
                .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(usuarioId);
        if (documentos.isEmpty() || documentos.get(0).getEnvioId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "documentacao KYC ainda nao foi enviada");
        }

        var envioAtual = documentoUsuarioRepository
                .findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(
                        documentos.get(0).getEnvioId());
        boolean aprovado = !envioAtual.isEmpty()
                && envioAtual.stream()
                        .allMatch(documento -> documento.getStatus() == StatusDocumentoUsuario.VALIDADO);
        if (!aprovado) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "documentacao KYC ainda nao foi aprovada");
        }
    }

    private AdminAcaoModeracaoResponseDto repetirOuRegularizarAprovacao(
            RevisaoAnuncioEntity revisao,
            AnuncioEntity anuncio,
            AdminUserPrincipal actor,
            String requestId) {
        if (revisao.getStatus() != StatusRevisaoAnuncio.APROVADA
                || revisao.getFinalizadoEm() == null
                || !decisaoRepository.existsByRevisaoAnuncioId(revisao.getId())
                || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.APROVADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "revisao ja finalizada");
        }

        OffsetDateTime agora = agora();
        boolean regularizada = anuncio.getStatus() == StatusAnuncio.APROVADO;
        if (regularizada) {
            String antes = snapshotRevisao(revisao, anuncio, null, null);
            try {
                anuncio.aprovarEPublicarAdministrativamente(agora);
            } catch (IllegalStateException exception) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "estado do anuncio impede regularizacao da publicacao",
                        exception);
            }
            auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                    UUID.randomUUID(),
                    actor.usuarioId(),
                    "MODERACAO_REVISAO_PUBLICACAO_REGULARIZAR",
                    "REVISAO_ANUNCIO",
                    revisao.getId(),
                    antes,
                    snapshotRevisao(revisao, anuncio, AdminDecisaoModeracaoAcao.APROVAR, null),
                    requestId,
                    agora));
        } else if (anuncio.getStatus() != StatusAnuncio.PUBLICADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "estado do anuncio impede repeticao da aprovacao");
        }

        return new AdminAcaoModeracaoResponseDto(
                UUID.randomUUID(),
                "REVISAO_ANUNCIO",
                revisao.getId(),
                AdminDecisaoModeracaoAcao.APROVAR.name(),
                revisao.getStatus().name(),
                null,
                regularizada,
                false,
                false,
                requestId,
                agora,
                regularizada
                        ? "anuncio aprovado anteriormente e publicado agora"
                        : "anuncio ja estava aprovado e publicado");
    }

    private AdminAcaoModeracaoResponseDto regularizarAprovacaoSemPublicacao(
            AnuncioEntity anuncio,
            AdminUserPrincipal actor,
            String requestId) {
        OffsetDateTime agora = agora();
        String antes = snapshotAnuncio(anuncio, null, null);
        try {
            anuncio.aprovarEPublicarAdministrativamente(agora);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "estado do anuncio impede regularizacao da publicacao",
                    exception);
        }
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                actor.usuarioId(),
                "MODERACAO_ANUNCIO_PUBLICACAO_REGULARIZAR",
                "ANUNCIO",
                anuncio.getId(),
                antes,
                snapshotAnuncio(anuncio, null, null),
                requestId,
                agora));
        return new AdminAcaoModeracaoResponseDto(
                UUID.randomUUID(),
                "ANUNCIO",
                anuncio.getId(),
                AdminDecisaoModeracaoAcao.APROVAR.name(),
                anuncio.getStatus().name(),
                null,
                true,
                false,
                false,
                requestId,
                agora,
                "anuncio aprovado anteriormente e publicado agora");
    }

    private AdminAcaoModeracaoResponseDto respostaAprovacaoIdempotente(
            AnuncioEntity anuncio,
            String requestId) {
        OffsetDateTime agora = agora();
        return new AdminAcaoModeracaoResponseDto(
                UUID.randomUUID(),
                "ANUNCIO",
                anuncio.getId(),
                AdminDecisaoModeracaoAcao.APROVAR.name(),
                anuncio.getStatus().name(),
                null,
                false,
                false,
                false,
                requestId,
                agora,
                "anuncio ja estava aprovado e publicado");
    }

    private VisibilidadeMidia visibilidadeParaDecisao(
            AnuncioMidiaEntity midia,
            AdminDecidirMidiaRequestDto request,
            AdminDecisaoModeracaoAcao decisao) {
        VisibilidadeMidia solicitada = request == null ? null : request.visibilidadeMidia();
        if (midia.getTipo() == TipoAnuncioMidia.VIDEO) {
            if (solicitada == VisibilidadeMidia.LIVRE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "video exige visibilidade RESTRITA_18");
            }
            return VisibilidadeMidia.RESTRITA_18;
        }
        if (decisao == AdminDecisaoModeracaoAcao.APROVAR && solicitada == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibilidade obrigatoria para aprovar foto");
        }
        return solicitada != null ? solicitada : midia.getVisibilidadeMidia();
    }

    private void garantirDerivadoMarcadoParaFotoLivre(
            AnuncioMidiaEntity midia,
            ArquivoMidiaEntity arquivo,
            AdminDecisaoModeracaoAcao decisao,
            VisibilidadeMidia visibilidade) {
        if (decisao != AdminDecisaoModeracaoAcao.APROVAR
                || midia.getTipo() != TipoAnuncioMidia.FOTO
                || visibilidade != VisibilidadeMidia.LIVRE
                || !"R2".equals(arquivo.getStorageProvider())) {
            return;
        }
        if (!derivadoMarcadoValido(arquivo)
                && fotoImportadaProcessadaComChecksum(midia, arquivo)) {
            return;
        }
        garantirDerivadoMarcado(arquivo);
    }

    private boolean fotoImportadaProcessadaComChecksum(
            AnuncioMidiaEntity midia,
            ArquivoMidiaEntity arquivo) {
        String sha256 = arquivo.getSha256();
        return sha256 != null
                && sha256.matches("[0-9a-fA-F]{64}")
                && anuncioMidiaRepository.existsFotoImportadaProcessadaComChecksum(midia.getId(), sha256);
    }

    private void garantirDerivadoMarcado(ArquivoMidiaEntity arquivo) {
        if (!derivadoMarcadoValido(arquivo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "foto livre nao possui derivado marcado valido");
        }
    }

    private boolean derivadoMarcadoValido(ArquivoMidiaEntity arquivo) {
        return arquivo.getPipelineVersao() != null
                && arquivo.getPipelineVersao() >= 1
                && arquivo.getMarcaDaguaVersao() != null
                && !arquivo.getMarcaDaguaVersao().isBlank()
                && arquivo.getProcessadoEm() != null
                && arquivo.getSha256Origem() != null
                && arquivo.getSha256Origem().matches("[0-9a-f]{64}");
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
        values.put("visibilidadeMidia", enumName(midia.getVisibilidadeMidia()));
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
        if (decisao == AdminDecisaoModeracaoAcao.REPROVAR
                || decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE) {
            values.put("anuncioTitulo", AdminModeracaoSanitizer.texto(anuncio.getTitulo(), 80));
            values.put("linkEdicao", linkEdicaoAnuncio(anuncio));
            values.put("destinatarioUsuarioId", anuncio.getUsuarioId());
            values.put("destinatarioLogico", "ANUNCIANTE_VINCULADA_AO_ANUNCIO");
            values.put("communicationVersion", 1);
        }
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

    private String linkEdicaoAnuncio(AnuncioEntity anuncio) {
        String base = canonicalDomain == null ? "" : canonicalDomain.trim().replaceAll("/+$", "");
        String slug = anuncio.getSlug();
        try {
            URI uri = URI.create(base);
            if (!List.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || slug == null
                    || !slug.matches("[a-z0-9][a-z0-9-]{1,120}")) {
                throw new IllegalArgumentException("dominio ou slug invalido");
            }
            return base + "/meus-anuncios/" + slug + "/editar";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "dominio canonico indisponivel para notificacao",
                    exception);
        }
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

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String mensagemRevisao(AdminDecisaoModeracaoAcao decisao) {
        return switch (decisao) {
            case APROVAR -> "anuncio aprovado e publicado";
            case REPROVAR -> "anuncio reprovado; notificacao registrada na outbox";
            case SOLICITAR_AJUSTE -> "ajuste solicitado localmente; revisao permanece aberta para decisao final futura";
        };
    }

    private String mensagemMidia(AdminDecisaoModeracaoAcao decisao) {
        return switch (decisao) {
            case APROVAR -> "midia aprovada localmente";
            case REPROVAR -> "midia reprovada localmente";
            case SOLICITAR_AJUSTE -> "ajuste de midia solicitado localmente";
        };
    }

    private record ContextoPublicacao(AnuncioEntity anuncio, UsuarioEntity usuario) {
    }
}
