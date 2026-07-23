package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncianteDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncianteResumoDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioMetricasDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminBloqueioJuridicoDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalizacaoSanitizadaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalidadeFiltroDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminModeracaoHistoricoItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPremiumFilaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminRevisaoAbertaDto;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioBloqueioJuridicoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioDetalhadoConsultaService {

    private static final int DESCRICAO_RESUMO_MAX = 180;
    private static final Set<Integer> TAMANHOS_FILA_PERMITIDOS = Set.of(20, 30, 50, 100);
    private static final UUID ID_LOCALIZACAO_NEUTRO = new UUID(0L, 0L);
    private static final List<StatusRevisaoAnuncio> REVISOES_ABERTAS = List.of(
            StatusRevisaoAnuncio.ABERTA,
            StatusRevisaoAnuncio.EM_ANALISE);

    private final AnuncioRepository anuncioRepository;
    private final AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final DocumentoUsuarioRepository documentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final AtivacaoBeneficioRepository ativacaoBeneficioRepository;
    private final AdminLocalizacaoConsultaSupport localizacaoSupport;
    private final AdminMidiaDetalhadaConsultaService midiaService;
    private final VisualizacaoTotalCanonicaService visualizacaoService;
    private final CliqueWhatsappRepository cliqueRepository;
    private final BeneficioAnuncioConsultaService beneficioService;
    private final MidiaPublicaUrlService midiaPublicaUrlService;
    private final AdminKycService kycService;
    private final ObjectMapper objectMapper;

    public AdminAnuncioDetalhadoConsultaService(
            AnuncioRepository anuncioRepository,
            AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            DocumentoUsuarioRepository documentoRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            AtivacaoBeneficioRepository ativacaoBeneficioRepository,
            AdminLocalizacaoConsultaSupport localizacaoSupport,
            AdminMidiaDetalhadaConsultaService midiaService,
            VisualizacaoTotalCanonicaService visualizacaoService,
            CliqueWhatsappRepository cliqueRepository,
            BeneficioAnuncioConsultaService beneficioService,
            MidiaPublicaUrlService midiaPublicaUrlService,
            AdminKycService kycService,
            ObjectMapper objectMapper) {
        this.anuncioRepository = anuncioRepository;
        this.bloqueioJuridicoRepository = bloqueioJuridicoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
        this.revisaoRepository = revisaoRepository;
        this.documentoRepository = documentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.ativacaoBeneficioRepository = ativacaoBeneficioRepository;
        this.localizacaoSupport = localizacaoSupport;
        this.midiaService = midiaService;
        this.visualizacaoService = visualizacaoService;
        this.cliqueRepository = cliqueRepository;
        this.beneficioService = beneficioService;
        this.midiaPublicaUrlService = midiaPublicaUrlService;
        this.kycService = kycService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminAnuncioListaItemDto> listar(
            int page,
            int size,
            AdminAnuncioSituacao situacao,
            String uf,
            String cidade,
            String bairro,
            String termo,
            AdminAnuncioOrdenacao ordenacao,
            boolean comercialLimitado) {
        int tamanho = validarTamanhoFila(size);
        AdminAnuncioOrdenacao ordem = ordenacao == null
                ? AdminAnuncioOrdenacao.MAIS_RECENTES
                : ordenacao;
        AdminAnuncioSituacao situacaoOperacional = situacao == null
                ? AdminAnuncioSituacao.PENDENTES_MODERACAO
                : situacao;
        Set<UUID> idsLocalizacao = localizacaoSupport.filtrarAnuncioIds(uf, cidade, bairro);
        if (idsLocalizacao != null && idsLocalizacao.isEmpty()) {
            return new AdminPaginaDto<>(List.of(), Math.max(page, 0), tamanho, 0, 0, true);
        }
        Page<AnuncioEntity> result = anuncioRepository.findFilaAdministrativa(
                situacaoOperacional.name(),
                idsLocalizacao != null,
                idsLocalizacao == null ? List.of(ID_LOCALIZACAO_NEUTRO) : idsLocalizacao,
                termoSeguro(termo),
                ordem.name(),
                PageRequest.of(Math.max(page, 0), tamanho));
        Map<UUID, AdminLocalizacaoSanitizadaDto> localizacoes = localizacaoSupport.carregar(
                result.getContent().stream().map(AnuncioEntity::getId).toList());
        Map<UUID, UsuarioEntity> anunciantes = carregarAnunciantes(result.getContent());
        Map<UUID, RevisaoAnuncioEntity> revisoesAbertas = carregarRevisoesAbertas(result.getContent());
        List<UUID> anuncioIds = result.getContent().stream().map(AnuncioEntity::getId).toList();
        Map<UUID, Long> midias = contarMidias(anuncioIds);
        Map<UUID, Long> revisoes = contarRevisoes(anuncioIds);
        Map<UUID, Boolean> documentosPendentes = documentosPendentes(result.getContent());
        Map<UUID, Long> cliques = contarCliques(anuncioIds);
        Map<UUID, VisualizacoesCanonicasDto> visualizacoes = visualizacaoService.calcularEmLote(anuncioIds);
        Map<UUID, List<PremiumBeneficioCalculado>> beneficios = beneficioService
                .consultarCalculadosPorAnuncio(anuncioIds);
        Map<UUID, String> miniaturas = miniaturasSeguras(anuncioIds);
        return new AdminPaginaDto<>(
                result.getContent().stream()
                        .map(anuncio -> item(
                                anuncio,
                                localizacoes.get(anuncio.getId()),
                                anunciantes.get(anuncio.getUsuarioId()),
                                revisoesAbertas.get(anuncio.getId()),
                                miniaturas.get(anuncio.getId()),
                                midias.getOrDefault(anuncio.getId(), 0L),
                                revisoes.getOrDefault(anuncio.getId(), 0L),
                                documentosPendentes.getOrDefault(anuncio.getUsuarioId(), false),
                                nomesBeneficiosVigentes(beneficios.getOrDefault(anuncio.getId(), List.of())),
                                beneficiosFila(beneficios.getOrDefault(anuncio.getId(), List.of())),
                                visualizacoes.get(anuncio.getId()),
                                cliques.getOrDefault(anuncio.getId(), 0L),
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
        VisualizacoesCanonicasDto visualizacoes = visualizacaoService.calcular(anuncio.getId());
        long cliques = cliqueRepository.countByAnuncioIdAndPermitidoTrue(anuncio.getId());
        List<String> beneficios = beneficiosVigentes(List.of(anuncio.getId()))
                .getOrDefault(anuncio.getId(), List.of());
        List<AdminModeracaoHistoricoItemDto> historico = historico(anuncio.getId());
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
                anuncioMidiaRepository.countByAnuncioIdAndTipoNot(anuncio.getId(), TipoAnuncioMidia.STORY),
                revisoesTotal,
                anuncio.getWhatsappNormalizado() != null,
                documentoPendente(anuncio),
                anuncio.getPreco() != null,
                comercialLimitado,
                comercialLimitado ? null : anuncio.getPreco(),
                comercialLimitado ? null : anuncio.getWhatsappNormalizado(),
                comercialLimitado ? List.of() : enumNames(anuncio.getLocaisAtendimento()),
                comercialLimitado ? List.of() : enumNames(anuncio.getServicos()),
                anuncianteDetalhe(anunciante),
                new AdminAnuncioMetricasDto(
                        visualizacoes,
                        cliques,
                        ctr(visualizacoes, cliques),
                        beneficios,
                        historico.stream().findFirst().orElse(null)),
                revisaoAberta(revisaoAberta),
                bloqueioJuridico(anuncio, anunciante));
    }

    @Transactional(readOnly = true)
    public List<AdminLocalidadeFiltroDto> localidadesFiltro() {
        return localizacaoSupport.localidadesFiltro();
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminMidiaListaItemDto> listarMidiasDoAnuncio(UUID anuncioId, int page, int size) {
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        return midiaService.listarPorAnuncio(anuncio, page, size);
    }

    @Transactional(readOnly = true)
    public List<AdminKycEnvioDto> documentosDoAnunciante(UUID anuncioId) {
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        if (anuncio.getUsuarioId() == null) return List.of();
        return kycService.listarPorUsuario(anuncio.getUsuarioId());
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
        Set<UUID> ativacaoIds = ativacaoBeneficioRepository.findByAnuncioId(anuncio.getId()).stream()
                .map(AtivacaoBeneficioEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<UUID> recursos = new ArrayList<>();
        recursos.add(anuncio.getId());
        if (anuncio.getUsuarioId() != null) {
            recursos.add(anuncio.getUsuarioId());
        }
        recursos.addAll(revisaoIds);
        recursos.addAll(midiaIds);
        recursos.addAll(ativacaoIds);
        return auditoriaRepository.findByRecursoIdInOrderByCriadoEmDesc(recursos, PageRequest.of(0, 100)).stream()
                .filter(evento -> alvoPermitido(
                        evento,
                        anuncio.getId(),
                        anuncio.getUsuarioId(),
                        revisaoIds,
                        midiaIds,
                        ativacaoIds))
                .filter(this::eventoAdministrativoRelevante)
                .map(this::historicoItem)
                .toList();
    }

    private AdminAnuncioListaItemDto item(
            AnuncioEntity anuncio,
            AdminLocalizacaoSanitizadaDto localizacao,
            UsuarioEntity anunciante,
            RevisaoAnuncioEntity revisaoAberta,
            String miniaturaUrl,
            long midiasTotal,
            long revisoesTotal,
            boolean documentoPendente,
            List<String> beneficios,
            List<AdminPremiumFilaItemDto> beneficiosPremium,
            VisualizacoesCanonicasDto visualizacoes,
            long cliques,
            boolean comercialLimitado) {
        return new AdminAnuncioListaItemDto(
                anuncio.getId(),
                anuncio.getSlug(),
                AdminTextoSanitizer.resumo(anuncio.getTitulo(), 120),
                miniaturaUrl,
                enumName(anuncio.getStatus()),
                enumName(anuncio.getStatusModeracao()),
                localizacao,
                anuncio.getCriadoEm(),
                anuncio.getAtualizadoEm(),
                anuncio.getPublicadoEm(),
                midiasTotal,
                comercialLimitado ? null : revisoesTotal,
                anuncio.getWhatsappNormalizado() != null,
                documentoPendente,
                comercialLimitado,
                beneficios,
                beneficiosPremium,
                visualizacoes,
                cliques,
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
                AdminTextoSanitizer.resumo(usuario.getNomeCivil(), 160),
                usuario.getEmailNormalizado(),
                usuario.getTelefoneNormalizado(),
                enumName(usuario.getStatus()));
    }

    private AdminAnuncianteDetalheDto anuncianteDetalhe(UsuarioEntity usuario) {
        if (usuario == null) return null;
        return new AdminAnuncianteDetalheDto(
                usuario.getId(),
                usuario.getNome(),
                usuario.getNomeCivil(),
                usuario.getEmailNormalizado(),
                formatarCpf(usuario.getCpfNormalizado()),
                usuario.getTelefoneNormalizado(),
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

    private List<String> enumNames(Collection<? extends Enum<?>> values) {
        if (values == null) return List.of();
        return values.stream().map(Enum::name).sorted().toList();
    }

    private boolean eventoAdministrativoRelevante(AuditoriaEventoEntity evento) {
        String acao = evento.getAcao();
        return acao != null && (acao.startsWith("MODERACAO_")
                || acao.startsWith("PREMIUM_ATIVACAO_")
                || acao.startsWith("STORY_ADMIN_")
                || acao.contains("BLOQUEIO_JURIDICO")
                || acao.equals("ANUNCIO_REATIVADO_ADMINISTRATIVAMENTE")
                || acao.equals("ANUNCIO_REMETER_REVISAO")
                || acao.equals("ANUNCIO_EDICAO_ADMINISTRATIVA"));
    }

    private boolean alvoPermitido(
            AuditoriaEventoEntity evento,
            UUID anuncioId,
            UUID usuarioId,
            Set<UUID> revisaoIds,
            Set<UUID> midiaIds,
            Set<UUID> ativacaoIds) {
        if (evento.getRecursoTipo() == null) return false;
        return switch (evento.getRecursoTipo()) {
            case "ANUNCIO" -> anuncioId.equals(evento.getRecursoId());
            case "USUARIO" -> usuarioId != null && usuarioId.equals(evento.getRecursoId());
            case "REVISAO_ANUNCIO" -> revisaoIds.contains(evento.getRecursoId());
            case "ANUNCIO_MIDIA" -> midiaIds.contains(evento.getRecursoId());
            case "ATIVACAO_BENEFICIO" -> ativacaoIds.contains(evento.getRecursoId());
            case "STORY_SELECAO_ADMINISTRATIVA" -> anuncioId.equals(evento.getRecursoId());
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
                text(snapshot, "categoria"),
                text(snapshot, "observacaoInterna"),
                primeiroTexto(snapshot, "statusRevisao", "statusMidia", "statusAnuncio", "statusUsuario"),
                evento.getAtorUsuarioId(),
                evento.getRequestId(),
                enumName(evento.getResultado()),
                evento.getCriadoEm());
    }

    private AdminBloqueioJuridicoDto bloqueioJuridico(
            AnuncioEntity anuncio,
            UsuarioEntity anunciante) {
        AnuncioBloqueioJuridicoEntity bloqueio = bloqueioJuridicoRepository
                .findFirstByAnuncioIdAndAnuncioDesbloqueadoEmIsNullOrderByBloqueadoEmDesc(anuncio.getId())
                .orElseGet(() -> anunciante == null
                        ? null
                        : bloqueioJuridicoRepository
                                .findFirstByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNullOrderByBloqueadoEmDesc(
                                        anunciante.getId(),
                                        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO)
                                .orElse(null));
        if (bloqueio == null) return null;
        UsuarioEntity responsavel = usuarioRepository.findById(bloqueio.getBloqueadoPorId()).orElse(null);
        return new AdminBloqueioJuridicoDto(
                bloqueio.getId(),
                bloqueio.getAnuncioId(),
                bloqueio.getUsuarioId(),
                enumName(bloqueio.getEscopo()),
                enumName(bloqueio.getCategoria()),
                bloqueio.getMotivo(),
                bloqueio.getObservacaoInterna(),
                bloqueio.getBloqueadoPorId(),
                responsavel == null ? null : responsavel.getNome(),
                bloqueio.getBloqueadoEm(),
                anuncio.getId().equals(bloqueio.getAnuncioId()) && bloqueio.anuncioBloqueado(),
                bloqueio.usuarioBloqueado());
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

    private Map<UUID, Long> contarMidias(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) return Map.of();
        return anuncioMidiaRepository.countByAnuncioIdInAndTipoNot(anuncioIds, TipoAnuncioMidia.STORY).stream()
                .collect(Collectors.toMap(
                        AnuncioMidiaRepository.ContagemPorAnuncioProjection::getAnuncioId,
                        AnuncioMidiaRepository.ContagemPorAnuncioProjection::getTotalMidias));
    }

    private Map<UUID, Long> contarRevisoes(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) return Map.of();
        return revisaoRepository.countByAnuncioIdIn(anuncioIds).stream()
                .collect(Collectors.toMap(
                        RevisaoAnuncioRepository.ContagemPorAnuncioProjection::getAnuncioId,
                        RevisaoAnuncioRepository.ContagemPorAnuncioProjection::getTotalRevisoes));
    }

    private Map<UUID, Long> contarCliques(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) return Map.of();
        return cliqueRepository.countPermitidosPorAnuncioIdIn(anuncioIds).stream()
                .collect(Collectors.toMap(
                        CliqueWhatsappRepository.ContagemPorAnuncioProjection::getAnuncioId,
                        CliqueWhatsappRepository.ContagemPorAnuncioProjection::getTotalCliques));
    }

    private Map<UUID, Boolean> documentosPendentes(Collection<AnuncioEntity> anuncios) {
        List<UUID> usuarioIds = anuncios.stream()
                .map(AnuncioEntity::getUsuarioId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (usuarioIds.isEmpty()) return Map.of();
        return documentoRepository.countByUsuarioIdInAndStatusIn(
                        usuarioIds,
                        List.of(StatusDocumentoUsuario.PENDENTE, StatusDocumentoUsuario.EM_ANALISE)).stream()
                .collect(Collectors.toMap(
                        DocumentoUsuarioRepository.ContagemPorUsuarioProjection::getUsuarioId,
                        item -> item.getTotalDocumentos() > 0));
    }

    private Map<UUID, List<String>> beneficiosVigentes(Collection<UUID> anuncioIds) {
        if (anuncioIds == null || anuncioIds.isEmpty()) return Map.of();
        Map<UUID, List<PremiumBeneficioCalculado>> calculados = beneficioService
                .consultarCalculadosPorAnuncio(anuncioIds);
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        calculados.forEach((anuncioId, itens) -> result.put(anuncioId, nomesBeneficiosVigentes(itens)));
        return Map.copyOf(result);
    }

    private List<String> nomesBeneficiosVigentes(Collection<PremiumBeneficioCalculado> itens) {
        return itens.stream()
                .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO)
                .map(PremiumBeneficioCalculado::beneficio)
                .filter(java.util.Objects::nonNull)
                .map(item -> item.getNome())
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<AdminPremiumFilaItemDto> beneficiosFila(Collection<PremiumBeneficioCalculado> itens) {
        return itens.stream()
                .filter(item -> item.ativacao() != null && item.beneficio() != null)
                .map(item -> new AdminPremiumFilaItemDto(
                        item.ativacao().getId(),
                        item.beneficio().getCodigo(),
                        item.beneficio().getNome(),
                        item.status().name(),
                        item.ativacao().getInicioEm(),
                        item.ativacao().getFimEm()))
                .toList();
    }

    private Map<UUID, String> miniaturasSeguras(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) return Map.of();
        List<AnuncioMidiaEntity> candidatas = anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds).stream()
                .filter(item -> item.getTipo() == TipoAnuncioMidia.FOTO)
                .filter(item -> item.getStatus() == StatusAnuncioMidia.PUBLICAVEL)
                .filter(item -> item.getVisibilidadeMidia() == VisibilidadeMidia.LIVRE)
                .sorted(Comparator
                        .comparing(AnuncioMidiaEntity::getOrdem, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AnuncioMidiaEntity::getCriadoEm, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AnuncioMidiaEntity::getId))
                .toList();
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(candidatas.stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, String> result = new LinkedHashMap<>();
        for (AnuncioMidiaEntity vinculo : candidatas) {
            if (result.containsKey(vinculo.getAnuncioId())) continue;
            ArquivoMidiaEntity arquivo = arquivos.get(vinculo.getArquivoMidiaId());
            if (arquivo == null || arquivo.getStatusArquivo() != StatusArquivoMidia.VALIDADO) continue;
            MidiaPublicaUrlService.ResultadoUrlPublica resolvida = midiaPublicaUrlService.resolver(vinculo, arquivo);
            if (resolvida.pendenciaMidia() == null && resolvida.urlPublica() != null) {
                result.put(vinculo.getAnuncioId(), resolvida.urlPublica());
            }
        }
        return Map.copyOf(result);
    }

    private BigDecimal ctr(VisualizacoesCanonicasDto visualizacoes, long cliques) {
        if (visualizacoes == null
                || visualizacoes.situacao() == VisualizacoesCanonicasDto.Situacao.HISTORICO_PENDENTE) {
            return null;
        }
        long total = visualizacoes.total();
        if (total == 0) return new BigDecimal("0.00");
        return BigDecimal.valueOf(cliques)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private String formatarCpf(String cpf) {
        if (cpf == null || !cpf.matches("[0-9]{11}")) return null;
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9)
                + "-" + cpf.substring(9);
    }

    private boolean documentoPendente(AnuncioEntity anuncio) {
        if (anuncio.getUsuarioId() == null) {
            return false;
        }
        return documentoRepository.countByUsuarioIdAndStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNull(
                anuncio.getUsuarioId(),
                List.of(StatusDocumentoUsuario.PENDENTE, StatusDocumentoUsuario.EM_ANALISE)) > 0;
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

    private int validarTamanhoFila(int size) {
        if (!TAMANHOS_FILA_PERMITIDOS.contains(size)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "tamanho da pagina deve ser 20, 30, 50 ou 100");
        }
        return size;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
