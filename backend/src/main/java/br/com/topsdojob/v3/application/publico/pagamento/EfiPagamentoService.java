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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiPagamentoService {

    private final MeusAnunciosConsultaService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final PlanoCreditoRepository planoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final EfiPixGateway gateway;
    private final EfiPagamentoConciliacaoService conciliacaoService;
    private final PublicAuthRateLimiter rateLimiter;

    public EfiPagamentoService(
            MeusAnunciosConsultaService usuarioService,
            UsuarioRepository usuarioRepository,
            PlanoCreditoRepository planoRepository,
            PagamentoRepository pagamentoRepository,
            EfiPixGateway gateway,
            EfiPagamentoConciliacaoService conciliacaoService,
            PublicAuthRateLimiter rateLimiter) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.planoRepository = planoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.gateway = gateway;
        this.conciliacaoService = conciliacaoService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public EfiPixCheckoutDto criar(
            EfiPixCheckoutRequest request,
            String idempotencyKey,
            Authentication authentication) {
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        rateLimiter.require("efi-pix-criar", usuarioId.toString(), 10, Duration.ofMinutes(5));
        if (request == null || request.planoCreditoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plano de credito obrigatorio");
        }
        String chave = chavePagamento(usuarioId, idempotencyKey);
        var existente = pagamentoRepository.findByIdempotencyKey(chave);
        if (existente.isPresent()) {
            validarMesmoPlano(existente.get(), request.planoCreditoId());
            return consultarExistente(existente.get(), true);
        }
        usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica invalida"));
        existente = pagamentoRepository.findByIdempotencyKey(chave);
        if (existente.isPresent()) {
            validarMesmoPlano(existente.get(), request.planoCreditoId());
            return consultarExistente(existente.get(), true);
        }
        PlanoCreditoEntity plano = planoRepository.findById(request.planoCreditoId())
                .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                .filter(item -> item.getValor() != null && item.getValor().signum() > 0)
                .filter(item -> item.getQuantidadeCreditos() != null && item.getQuantidadeCreditos() > 0)
                .filter(item -> "BRL".equals(item.getMoeda()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pacote de credito indisponivel"));

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String txid = txid(usuarioId, chave);
        AmbientePagamento ambiente = ambienteGatewayObrigatorio();
        PagamentoEntity pagamento = pagamentoRepository.saveAndFlush(PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                plano.getId(),
                ambiente,
                txid,
                plano.getValor().setScale(2),
                plano.getQuantidadeCreditos(),
                chave,
                agora));
        try {
            EfiPixGateway.CobrancaPix cobranca = gateway.criarCobranca(
                    txid,
                    pagamento.getValor(),
                    "Compra de " + plano.getNome());
            validarCobrancaDoPagamento(pagamento, cobranca);
            pagamento.aguardarPagamento(
                    cobranca.identificadorLocalizacao(),
                    cobranca.status(),
                    cobranca.expiracaoEm(),
                    OffsetDateTime.now(ZoneOffset.UTC));
            return dto(pagamento, cobranca, false);
        } catch (EfiPixGatewayException exception) {
            throw indisponivel(exception);
        }
    }

    @Transactional(readOnly = true)
    public EfiPixCheckoutDto consultar(UUID pagamentoId, Authentication authentication) {
        PagamentoEntity pagamento = pagamentoDoUsuario(pagamentoId, authentication);
        rateLimiter.require(
                "efi-pix-consultar",
                pagamento.getUsuarioId().toString(),
                30,
                Duration.ofMinutes(5));
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
        EfiPagamentoConciliacaoService.ConciliacaoResultado resultado = conciliacaoService.conciliar(
                pagamento.getTxid(),
                null,
                null,
                br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento.CONSULTA_PROVEDOR,
                requestId);
        if (resultado.erroResumido() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "pagamento requer conciliacao administrativa");
        }
        return dto(resultado.pagamento(), resultado.cobranca(), resultado.idempotente());
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

    private EfiPixCheckoutDto consultarExistente(PagamentoEntity pagamento, boolean idempotente) {
        if (pagamento.getCreditadoEm() != null
                || pagamento.getStatusInterno()
                == br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento.CANCELADO
                || pagamento.getStatusInterno()
                == br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento.EXPIRADO
                || pagamento.getStatusInterno()
                == br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento.ERRO) {
            return dto(pagamento, null, idempotente);
        }
        return consultarGateway(pagamento, idempotente);
    }

    private EfiPixCheckoutDto consultarGateway(PagamentoEntity pagamento, boolean idempotente) {
        try {
            EfiPixGateway.CobrancaPix cobranca = gateway.consultarCobranca(pagamento.getTxid());
            validarCobrancaDoPagamento(pagamento, cobranca);
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

    private void validarCobrancaDoPagamento(PagamentoEntity pagamento, EfiPixGateway.CobrancaPix cobranca) {
        if (cobranca == null
                || !pagamento.getTxid().equals(cobranca.txid())
                || cobranca.valorOriginal() == null
                || pagamento.getValor().compareTo(cobranca.valorOriginal()) != 0
                || pagamento.getAmbiente() == null
                || pagamento.getAmbiente() != ambienteGatewayObrigatorio()
                || pagamento.getAmbiente() != cobranca.ambiente()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cobranca Efi divergente");
        }
    }

    private AmbientePagamento ambienteGatewayObrigatorio() {
        try {
            AmbientePagamento ambiente = gateway.ambiente();
            if (ambiente == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "ambiente Efi indisponivel");
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
            case ERRO -> "FALHO";
            case LEGADO -> "FALHO";
        };
    }

    private String identificacaoSanitizada(String txid) {
        String valor = txid == null ? "" : txid.trim();
        String sufixo = valor.substring(Math.max(0, valor.length() - 8));
        return "PIX **** " + sufixo;
    }

    private void validarMesmoPlano(PagamentoEntity pagamento, UUID planoId) {
        if (!pagamento.getPlanoCreditoId().equals(planoId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency-Key reutilizada com outro pacote");
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

    private ResponseStatusException indisponivel(EfiPixGatewayException exception) {
        HttpStatus status = exception.isConfiguracao() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, "integracao Efi indisponivel");
    }
}
