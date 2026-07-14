package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutDto;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutRequest;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
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

    public EfiPagamentoService(
            MeusAnunciosConsultaService usuarioService,
            UsuarioRepository usuarioRepository,
            PlanoCreditoRepository planoRepository,
            PagamentoRepository pagamentoRepository,
            EfiPixGateway gateway,
            EfiPagamentoConciliacaoService conciliacaoService) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.planoRepository = planoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.gateway = gateway;
        this.conciliacaoService = conciliacaoService;
    }

    @Transactional
    public EfiPixCheckoutDto criar(
            EfiPixCheckoutRequest request,
            String idempotencyKey,
            Authentication authentication) {
        UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
        if (request == null || request.planoCreditoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plano de credito obrigatorio");
        }
        String chave = chavePagamento(usuarioId, idempotencyKey);
        var existente = pagamentoRepository.findByIdempotencyKey(chave);
        if (existente.isPresent()) {
            return consultarGateway(existente.get(), true);
        }
        usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao publica invalida"));
        PlanoCreditoEntity plano = planoRepository.findById(request.planoCreditoId())
                .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                .filter(item -> item.getValor() != null && item.getValor().signum() > 0)
                .filter(item -> item.getQuantidadeCreditos() != null && item.getQuantidadeCreditos() > 0)
                .filter(item -> "BRL".equals(item.getMoeda()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pacote de credito indisponivel"));

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String txid = txid(usuarioId, chave);
        PagamentoEntity pagamento = pagamentoRepository.save(PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                plano.getId(),
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
        return consultarGateway(pagamento, false);
    }

    public EfiPixCheckoutDto conciliar(
            UUID pagamentoId,
            Authentication authentication,
            String requestId) {
        PagamentoEntity pagamento = pagamentoDoUsuario(pagamentoId, authentication);
        EfiPagamentoConciliacaoService.ConciliacaoResultado resultado = conciliacaoService.conciliar(
                pagamento.getTxid(),
                null,
                null,
                br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento.CONSULTA_PROVEDOR,
                requestId);
        return dto(resultado.pagamento(), resultado.cobranca(), resultado.idempotente());
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
                || pagamento.getValor().compareTo(cobranca.valorOriginal()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cobranca Efi divergente");
        }
    }

    private EfiPixCheckoutDto dto(
            PagamentoEntity pagamento,
            EfiPixGateway.CobrancaPix cobranca,
            boolean idempotente) {
        return new EfiPixCheckoutDto(
                pagamento.getId(),
                pagamento.getTxid(),
                pagamento.getStatusInterno().name(),
                pagamento.getValor(),
                pagamento.getQuantidadeCreditos(),
                cobranca == null ? pagamento.getExpiracaoEm() : cobranca.expiracaoEm(),
                cobranca == null ? null : cobranca.pixCopiaECola(),
                cobranca == null ? null : cobranca.imagemQrCode(),
                pagamento.getCreditadoEm() != null,
                idempotente);
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
