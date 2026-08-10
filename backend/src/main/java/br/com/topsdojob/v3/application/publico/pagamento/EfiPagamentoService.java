package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPagamentoHistoricoDto;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutDto;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutRequest;
import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiPagamentoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EfiPagamentoService.class);

    private final MeusAnunciosConsultaService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PlanoCreditoRepository planoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final EfiPixGateway gateway;
    private final EfiPagamentoConciliacaoService conciliacaoService;
    private final PublicAuthRateLimiter rateLimiter;
    private final TransactionTemplate transactions;

    public EfiPagamentoService(
            MeusAnunciosConsultaService usuarioService,
            UsuarioRepository usuarioRepository,
            PlanoCreditoRepository planoRepository,
            PagamentoRepository pagamentoRepository,
            EfiPixGateway gateway,
            EfiPagamentoConciliacaoService conciliacaoService,
            PublicAuthRateLimiter rateLimiter,
            PlatformTransactionManager transactionManager) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.planoRepository = planoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.gateway = gateway;
        this.conciliacaoService = conciliacaoService;
        this.rateLimiter = rateLimiter;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public EfiPixCheckoutDto criar(
            EfiPixCheckoutRequest request,
            String idempotencyKey,
            Authentication authentication) {
        return criar(request, idempotencyKey, authentication, null);
    }

    public EfiPixCheckoutDto criar(
            EfiPixCheckoutRequest request,
            String idempotencyKey,
            Authentication authentication,
            String requestId) {
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        rateLimiter.require("efi-pix-criar", usuarioId.toString(), 10, Duration.ofMinutes(5));
        if (request == null || request.planoCreditoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plano de credito obrigatorio");
        }
        String chave = chavePagamento(usuarioId, idempotencyKey);
        PagamentoEntity existente = pagamentoRepository.findByIdempotencyKey(chave).orElse(null);
        if (existente != null) {
            validarMesmoPlano(existente, request.planoCreditoId());
            return processarRemoto(
                    existente,
                    nomePlano(existente.getPlanoCreditoId()),
                    true,
                    false,
                    requestId);
        }

        AmbientePagamento ambiente = ambienteGatewayObrigatorio();
        IntencaoPagamento intencao = Objects.requireNonNull(transactions.execute(ignored -> prepararIntencao(
                usuarioId,
                request.planoCreditoId(),
                chave,
                ambiente)));
        return processarRemoto(
                intencao.pagamento(),
                intencao.planoNome(),
                intencao.idempotente(),
                !intencao.idempotente(),
                requestId);
    }

    public EfiPixCheckoutDto consultar(UUID pagamentoId, Authentication authentication) {
        PagamentoEntity pagamento = pagamentoDoUsuario(pagamentoId, authentication);
        rateLimiter.require(
                "efi-pix-consultar",
                pagamento.getUsuarioId().toString(),
                30,
                Duration.ofMinutes(5));
        if (!podeRetentar(pagamento)) {
            return dto(pagamento, null, true);
        }
        return consultarGateway(pagamento, false);
    }

    public EfiPixCheckoutDto conciliar(
            UUID pagamentoId,
            Authentication authentication,
            String requestId) {
        PagamentoEntity pagamento = pagamentoDoUsuario(pagamentoId, authentication);
        rateLimiter.require(
                "efi-pix-conciliar",
                pagamento.getUsuarioId().toString(),
                30,
                Duration.ofMinutes(5));
        try {
            EfiPagamentoConciliacaoService.ConciliacaoResultado resultado = conciliacaoService.conciliar(
                    pagamento.getTxid(),
                    null,
                    null,
                    OrigemConciliacaoPagamento.CONSULTA_PROVEDOR,
                    requestId);
            if (resultado.erroResumido() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "pagamento requer conciliacao administrativa");
            }
            return dto(resultado.pagamento(), resultado.cobranca(), resultado.idempotente());
        } catch (EfiPixGatewayException exception) {
            registrarIndisponibilidade(pagamento, requestId, "conciliacao", exception);
            if (exception.isCobrancaNaoEncontrada() && podeCriarRemotamente(pagamento)) {
                return processarRemoto(
                        pagamento,
                        nomePlano(pagamento.getPlanoCreditoId()),
                        true,
                        true,
                        requestId);
            }
            throw indisponivel(exception);
        }
    }

    @Transactional(readOnly = true)
    public List<EfiPagamentoHistoricoDto> historico(Authentication authentication) {
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        List<PagamentoEntity> pagamentos = pagamentoRepository
                .findByUsuarioIdAndProvedorAndMetodoOrderByCriadoEmDesc(
                        usuarioId,
                        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento.EFI,
                        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento.PIX,
                        PageRequest.of(0, 50));
        Map<UUID, PlanoCreditoEntity> planos = planoRepository.findAllById(
                        pagamentos.stream()
                                .map(PagamentoEntity::getPlanoCreditoId)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(PlanoCreditoEntity::getId, Function.identity()));
        return pagamentos.stream()
                .map(pagamento -> {
                    PlanoCreditoEntity plano = planos.get(pagamento.getPlanoCreditoId());
                    return new EfiPagamentoHistoricoDto(
                            pagamento.getId(),
                            pagamento.getPlanoCreditoId(),
                            plano == null ? "Pacote de creditos" : plano.getNome(),
                            pagamento.getQuantidadeCreditos(),
                            pagamento.getValor(),
                            pagamento.getCriadoEm(),
                            pagamento.getExpiracaoEm(),
                            statusPublico(pagamento),
                            identificacaoSanitizada(pagamento.getTxid()),
                            pagamento.getAprovadoEm());
                })
                .toList();
    }

    private IntencaoPagamento prepararIntencao(
            UUID usuarioId,
            UUID planoId,
            String chave,
            AmbientePagamento ambiente) {
        usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica invalida"));
        PagamentoEntity existente = pagamentoRepository.findByIdempotencyKey(chave).orElse(null);
        if (existente != null) {
            validarMesmoPlano(existente, planoId);
            return new IntencaoPagamento(existente, nomePlano(existente.getPlanoCreditoId()), true);
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        PagamentoEntity reutilizavel = cobrancaReutilizavel(usuarioId, ambiente, agora);
        if (reutilizavel != null) {
            if (!reutilizavel.getPlanoCreditoId().equals(planoId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "existe cobranca Pix pendente para outro pacote");
            }
            return new IntencaoPagamento(
                    reutilizavel,
                    nomePlano(reutilizavel.getPlanoCreditoId()),
                    true);
        }
        PlanoCreditoEntity plano = planoRepository.findById(planoId)
                .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                .filter(item -> item.getValor() != null && item.getValor().signum() > 0)
                .filter(item -> item.getQuantidadeCreditos() != null && item.getQuantidadeCreditos() > 0)
                .filter(item -> "BRL".equals(item.getMoeda()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pacote de credito indisponivel"));

        PagamentoEntity pagamento = pagamentoRepository.saveAndFlush(PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                plano.getId(),
                ambiente,
                txid(usuarioId, chave),
                plano.getValor().setScale(2),
                plano.getQuantidadeCreditos(),
                chave,
                agora));
        return new IntencaoPagamento(pagamento, plano.getNome(), false);
    }

    private EfiPixCheckoutDto processarRemoto(
            PagamentoEntity pagamento,
            String planoNome,
            boolean idempotente,
            boolean criarSemConsulta,
            String requestId) {
        if (!podeRetentar(pagamento)) {
            return dto(pagamento, null, idempotente);
        }
        AmbientePagamento ambienteGateway = ambienteGatewayObrigatorio();
        if (pagamento.getAmbiente() == null || pagamento.getAmbiente() != ambienteGateway) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ambiente Efi divergente");
        }

        EfiPixGateway.CobrancaPix cobranca;
        try {
            cobranca = localizarOuCriarCobranca(
                    pagamento,
                    planoNome,
                    criarSemConsulta);
        } catch (EfiPixGatewayException exception) {
            registrarIndisponibilidade(pagamento, requestId, "checkout", exception);
            if (!exception.isConfiguracao() && !exception.isCobrancaNaoEncontrada()) {
                marcarFalhaTransitoriaSemMascararErro(pagamento, requestId);
            }
            throw indisponivel(exception);
        }
        validarCobrancaDoPagamento(pagamento, cobranca, ambienteGateway);
        String status = statusNormalizado(cobranca.status());
        if ("ATIVA".equals(status)) {
            PagamentoEntity atualizado = pagamento.getStatusInterno() == StatusInternoPagamento.AGUARDANDO_PAGAMENTO
                    ? pagamento
                    : finalizarCobrancaAtiva(pagamento.getTxid(), cobranca, ambienteGateway);
            return dto(atualizado, cobranca, idempotente);
        }

        EfiPagamentoConciliacaoService.ConciliacaoResultado resultado = conciliacaoService.aplicarCobrancaConsultada(
                cobranca,
                null,
                null,
                OrigemConciliacaoPagamento.CONSULTA_PROVEDOR,
                requestId);
        if (resultado.erroResumido() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "pagamento requer conciliacao administrativa");
        }
        return dto(resultado.pagamento(), cobranca, idempotente);
    }

    private EfiPixGateway.CobrancaPix localizarOuCriarCobranca(
            PagamentoEntity pagamento,
            String planoNome,
            boolean criarSemConsulta) {
        if (criarSemConsulta) {
            return gateway.criarCobranca(
                    pagamento.getTxid(),
                    pagamento.getValor(),
                    "Compra de " + planoNomeSeguro(planoNome));
        }
        try {
            return gateway.consultarCobranca(pagamento.getTxid());
        } catch (EfiPixGatewayException exception) {
            if (!exception.isCobrancaNaoEncontrada() || !podeCriarRemotamente(pagamento)) {
                throw exception;
            }
            return gateway.criarCobranca(
                    pagamento.getTxid(),
                    pagamento.getValor(),
                    "Compra de " + planoNomeSeguro(planoNome));
        }
    }

    private PagamentoEntity finalizarCobrancaAtiva(
            String txid,
            EfiPixGateway.CobrancaPix cobranca,
            AmbientePagamento ambienteGateway) {
        return Objects.requireNonNull(transactions.execute(ignored -> {
            PagamentoEntity atual = pagamentoRepository.findByTxidForUpdate(txid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento Efi nao encontrado"));
            if (!podeRetentar(atual)) {
                return atual;
            }
            validarCobrancaDoPagamento(atual, cobranca, ambienteGateway);
            atual.aguardarPagamento(
                    cobranca.identificadorLocalizacao(),
                    "ATIVA",
                    cobranca.expiracaoEm(),
                    OffsetDateTime.now(ZoneOffset.UTC));
            return atual;
        }));
    }

    private EfiPixCheckoutDto consultarGateway(PagamentoEntity pagamento, boolean idempotente) {
        AmbientePagamento ambienteGateway = ambienteGatewayObrigatorio();
        if (pagamento.getAmbiente() == null || pagamento.getAmbiente() != ambienteGateway) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ambiente Efi divergente");
        }
        try {
            EfiPixGateway.CobrancaPix cobranca = gateway.consultarCobranca(pagamento.getTxid());
            validarCobrancaDoPagamento(pagamento, cobranca, ambienteGateway);
            return dto(pagamento, cobranca, idempotente);
        } catch (EfiPixGatewayException exception) {
            throw indisponivel(exception);
        }
    }

    private PagamentoEntity pagamentoDoUsuario(UUID pagamentoId, Authentication authentication) {
        if (pagamentoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagamento obrigatorio");
        }
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        return pagamentoRepository.findByIdAndUsuarioId(pagamentoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pagamento nao encontrado"));
    }

    private void validarCobrancaDoPagamento(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca,
            AmbientePagamento ambienteGateway) {
        if (cobranca == null
                || !pagamento.getTxid().equals(cobranca.txid())
                || cobranca.valorOriginal() == null
                || pagamento.getValor().compareTo(cobranca.valorOriginal()) != 0
                || pagamento.getAmbiente() == null
                || ambienteGateway == null
                || pagamento.getAmbiente() != ambienteGateway
                || pagamento.getAmbiente() != cobranca.ambiente()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cobranca Efi divergente");
        }
    }

    private AmbientePagamento ambienteGatewayObrigatorio() {
        try {
            AmbientePagamento ambiente = gateway.ambiente();
            if (ambiente == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ambiente Efi indisponivel");
            }
            return ambiente;
        } catch (EfiPixGatewayException exception) {
            throw indisponivel(exception);
        }
    }

    private EfiPixCheckoutDto dto(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca,
            boolean idempotente) {
        PlanoCreditoEntity plano = planoRepository.findById(pagamento.getPlanoCreditoId()).orElse(null);
        return new EfiPixCheckoutDto(
                pagamento.getId(),
                pagamento.getPlanoCreditoId(),
                plano == null ? "Pacote de creditos" : plano.getNome(),
                identificacaoSanitizada(pagamento.getTxid()),
                statusPublico(pagamento),
                pagamento.getValor(),
                pagamento.getQuantidadeCreditos(),
                pagamento.getCriadoEm(),
                cobranca == null ? pagamento.getExpiracaoEm() : cobranca.expiracaoEm(),
                pagamento.getAprovadoEm(),
                cobranca == null ? null : cobranca.pixCopiaECola(),
                cobranca == null ? null : cobranca.imagemQrCode(),
                pagamento.getCreditadoEm() != null,
                idempotente);
    }

    private String statusPublico(PagamentoEntity pagamento) {
        return switch (pagamento.getStatusInterno()) {
            case CRIADO, AGUARDANDO_PAGAMENTO -> "PENDENTE";
            case APROVADO -> "APROVADO";
            case EXPIRADO -> "EXPIRADO";
            case CANCELADO, ESTORNADO -> "CANCELADO";
            case ERRO -> EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO.equals(
                    pagamento.getStatusProvedor()) ? "PENDENTE" : "FALHO";
            case LEGADO -> "FALHO";
        };
    }

    private PagamentoEntity cobrancaReutilizavel(
            UUID usuarioId,
            AmbientePagamento ambiente,
            OffsetDateTime agora) {
        return pagamentoRepository.findByUsuarioIdAndProvedorAndMetodoOrderByCriadoEmDesc(
                        usuarioId,
                        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento.EFI,
                        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento.PIX,
                        PageRequest.of(0, 50)).stream()
                .filter(pagamento -> pagamento.getAmbiente() == ambiente)
                .filter(this::podeRetentar)
                .filter(pagamento -> pagamento.getExpiracaoEm() == null
                        || pagamento.getExpiracaoEm().isAfter(agora))
                .findFirst()
                .orElse(null);
    }

    private boolean podeRetentar(PagamentoEntity pagamento) {
        if (pagamento == null || pagamento.getCreditadoEm() != null) {
            return false;
        }
        return pagamento.getStatusInterno() == StatusInternoPagamento.CRIADO
                || pagamento.getStatusInterno() == StatusInternoPagamento.AGUARDANDO_PAGAMENTO
                || (pagamento.getStatusInterno() == StatusInternoPagamento.ERRO
                && EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO.equals(pagamento.getStatusProvedor()));
    }

    private boolean podeCriarRemotamente(PagamentoEntity pagamento) {
        return pagamento.getIdentificadorProvedor() == null
                && (pagamento.getStatusInterno() == StatusInternoPagamento.CRIADO
                || (pagamento.getStatusInterno() == StatusInternoPagamento.ERRO
                && EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO.equals(pagamento.getStatusProvedor())));
    }

    private void marcarFalhaTransitoriaSemMascararErro(PagamentoEntity pagamento, String requestId) {
        try {
            conciliacaoService.registrarFalhaTransitoria(pagamento.getTxid(), requestId);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "efi_pagamento_falha_transitoria_nao_registrada pagamento={} requestId={} tipo={}",
                    referenciaSegura(pagamento.getTxid()),
                    requestIdSeguro(requestId),
                    exception.getClass().getSimpleName());
        }
    }

    private void registrarIndisponibilidade(
            PagamentoEntity pagamento,
            String requestId,
            String etapa,
            EfiPixGatewayException exception) {
        LOGGER.warn(
                "efi_pagamento_indisponivel pagamento={} requestId={} etapa={} httpStatus={} configuracao={} tipo={}",
                referenciaSegura(pagamento.getTxid()),
                requestIdSeguro(requestId),
                etapa,
                exception.getHttpStatus() == null ? "SEM_RESPOSTA" : exception.getHttpStatus(),
                exception.isConfiguracao(),
                exception.getClass().getSimpleName());
    }

    private String identificacaoSanitizada(String txid) {
        String valor = txid == null ? "" : txid.trim();
        String sufixo = valor.substring(Math.max(0, valor.length() - 8));
        return "PIX **** " + sufixo;
    }

    private String referenciaSegura(String txid) {
        String valor = txid == null ? "" : txid.trim();
        return "***" + valor.substring(Math.max(0, valor.length() - 4));
    }

    private String requestIdSeguro(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "ausente";
        }
        String valor = requestId.trim();
        return valor.substring(0, Math.min(valor.length(), 120));
    }

    private String nomePlano(UUID planoId) {
        return planoRepository.findById(planoId)
                .map(PlanoCreditoEntity::getNome)
                .orElse("Pacote de creditos");
    }

    private String planoNomeSeguro(String nome) {
        String valor = nome == null ? "Pacote de creditos" : nome.trim();
        return valor.isBlank() ? "Pacote de creditos" : valor;
    }

    private void validarMesmoPlano(PagamentoEntity pagamento, UUID planoId) {
        if (!pagamento.getPlanoCreditoId().equals(planoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outro pacote");
        }
    }

    private String chavePagamento(UUID usuarioId, String key) {
        String limpa = CreditoLedgerOperacaoService.chaveObrigatoria(key);
        String chave = "efi-checkout:" + usuarioId + ":" + limpa;
        if (chave.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chave de idempotencia invalida");
        }
        return chave;
    }

    private String txid(UUID usuarioId, String chave) {
        try {
            byte[] input = (usuarioId + ":" + chave).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private String statusNormalizado(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException indisponivel(EfiPixGatewayException exception) {
        HttpStatus status = exception.isConfiguracao() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, "integracao Efi indisponivel");
    }

    private record IntencaoPagamento(
            PagamentoEntity pagamento,
            String planoNome,
            boolean idempotente) {
    }
}
