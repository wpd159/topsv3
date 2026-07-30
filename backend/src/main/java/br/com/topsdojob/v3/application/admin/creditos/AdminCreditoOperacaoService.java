package br.com.topsdojob.v3.application.admin.creditos;

import br.com.topsdojob.v3.application.admin.creditos.dto.AdminAuditoriaFinanceiraDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoAjusteRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoOperacaoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoUsuarioDto;
import br.com.topsdojob.v3.application.credito.CreditoLancamentoResultado;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCreditoOperacaoService {

    private static final List<String> ACOES_AUDITADAS = List.of(
            "CREDITO_ADMIN_AJUSTAR",
            "CREDITO_ADMIN_ESTORNAR",
            "PREMIUM_CATALOGO_ATUALIZAR",
            "CREDITO_PACOTE_CRIAR",
            "CREDITO_PACOTE_ATUALIZAR",
            "CREDITO_PACOTE_ATIVAR",
            "CREDITO_PACOTE_DESATIVAR",
            "PREMIUM_ATIVACAO_CANCELAR");

    private final CreditoLedgerOperacaoService ledgerService;
    private final MovimentoCreditoRepository movimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public AdminCreditoOperacaoService(
            CreditoLedgerOperacaoService ledgerService,
            MovimentoCreditoRepository movimentoRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.movimentoRepository = movimentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdminCreditoOperacaoDto ajustar(
            UUID usuarioId,
            AdminCreditoAjusteRequest request,
            String idempotencyKey,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        DirecaoMovimentoCredito direcao = direcao(request == null ? null : request.direcao());
        int quantidade = request == null || request.quantidade() == null ? 0 : request.quantidade();
        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
        if (usuario.getStatus() == StatusUsuario.EXCLUIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "conta excluida");
        }
        String chave = "admin-ajuste:" + usuarioId + ":" + CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
        var repetido = movimentoRepository.findByIdempotencyKey(chave);
        if (repetido.isPresent()) {
            return toDto(repetido.get(), true);
        }
        int saldoAntes = ledgerService.bloquearEConsultarSaldo(usuarioId);
        CreditoLancamentoResultado resultado = ledgerService.registrar(
                usuarioId,
                TipoMovimentoCredito.AJUSTE,
                direcao,
                quantidade,
                saldoAntes,
                OrigemMovimentoCredito.AJUSTE_ADMIN,
                "USUARIO",
                usuarioId,
                chave,
                administrador.usuarioId(),
                request == null ? null : request.motivo(),
                requestId);
        auditar(
                administrador.usuarioId(),
                "CREDITO_ADMIN_AJUSTAR",
                "MOVIMENTO_CREDITO",
                resultado.movimento().getId(),
                Map.of("usuarioId", usuarioId, "saldo", saldoAntes),
                Map.of(
                        "usuarioId", usuarioId,
                        "saldo", resultado.movimento().getSaldoDepois(),
                        "quantidade", quantidade,
                        "direcao", direcao.name()),
                requestId);
        return toDto(resultado.movimento(), resultado.idempotente());
    }

    @Transactional
    public AdminCreditoOperacaoDto estornar(
            UUID movimentoId,
            String motivo,
            String idempotencyKey,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        MovimentoCreditoEntity original = movimentoRepository.findById(movimentoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "movimento nao encontrado"));
        if (original.getDirecao() != DirecaoMovimentoCredito.DEBITO
                || original.getTipo() == TipoMovimentoCredito.ESTORNO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "movimento nao permite estorno");
        }
        String chave = "admin-estorno:" + movimentoId + ":"
                + CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
        var repetido = movimentoRepository.findByIdempotencyKey(chave);
        if (repetido.isPresent()) {
            return toDto(repetido.get(), true);
        }
        var estornoAnterior = movimentoRepository
                .findFirstByReferenciaTipoAndReferenciaIdAndDirecaoOrderByCriadoEmAsc(
                        "MOVIMENTO_CREDITO",
                        movimentoId,
                        DirecaoMovimentoCredito.CREDITO);
        if (estornoAnterior.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "movimento ja estornado");
        }
        int saldoAntes = ledgerService.bloquearEConsultarSaldo(original.getUsuarioId());
        CreditoLancamentoResultado resultado = ledgerService.registrar(
                original.getUsuarioId(),
                TipoMovimentoCredito.ESTORNO,
                DirecaoMovimentoCredito.CREDITO,
                original.getQuantidade(),
                saldoAntes,
                OrigemMovimentoCredito.ESTORNO,
                "MOVIMENTO_CREDITO",
                movimentoId,
                chave,
                administrador.usuarioId(),
                motivo,
                requestId);
        auditar(
                administrador.usuarioId(),
                "CREDITO_ADMIN_ESTORNAR",
                "MOVIMENTO_CREDITO",
                movimentoId,
                Map.of("saldo", saldoAntes, "movimentoId", movimentoId),
                Map.of("saldo", resultado.movimento().getSaldoDepois(), "estornoId", resultado.movimento().getId()),
                requestId);
        return toDto(resultado.movimento(), resultado.idempotente());
    }

    @Transactional(readOnly = true)
    public List<AdminCreditoUsuarioDto> buscarUsuarios(String query) {
        String termo = query == null ? "" : query.trim();
        if (termo.length() < 2 || termo.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "busca de usuario invalida");
        }
        return usuarioRepository.buscarAnunciantes(termo, TipoContaUsuario.ANUNCIANTE, PageRequest.of(0, 20)).stream()
                .map(usuario -> new AdminCreditoUsuarioDto(
                        usuario.getId(),
                        usuario.getNome(),
                        usuario.getEmailNormalizado(),
                        ledgerService.consultarSaldo(usuario.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAuditoriaFinanceiraDto> auditoria(int limite) {
        int tamanho = Math.min(100, Math.max(1, limite));
        return auditoriaRepository.findByAcaoInOrderByCriadoEmDesc(ACOES_AUDITADAS, PageRequest.of(0, tamanho))
                .stream()
                .map(item -> new AdminAuditoriaFinanceiraDto(
                        item.getId(),
                        item.getAtorUsuarioId(),
                        item.getAcao(),
                        item.getRecursoTipo(),
                        item.getRecursoId(),
                        item.getRequestId(),
                        item.getCriadoEm()))
                .toList();
    }

    public void auditar(
            UUID administradorId,
            String acao,
            String recursoTipo,
            UUID recursoId,
            Object antes,
            Object depois,
            String requestId) {
        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                administradorId,
                acao,
                recursoTipo,
                recursoId,
                json(antes),
                json(depois),
                requestId,
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private AdminCreditoOperacaoDto toDto(MovimentoCreditoEntity movimento, boolean idempotente) {
        return new AdminCreditoOperacaoDto(
                movimento.getId(),
                movimento.getUsuarioId(),
                natureza(movimento),
                movimento.getQuantidade(),
                movimento.getSaldoAntes(),
                movimento.getSaldoDepois(),
                movimento.getObservacao(),
                movimento.getRequestId(),
                movimento.getCriadoEm(),
                idempotente);
    }

    private String natureza(MovimentoCreditoEntity movimento) {
        if (movimento.getTipo() == TipoMovimentoCredito.MIGRACAO_SALDO_INICIAL) {
            return "MIGRACAO_SALDO_INICIAL";
        }
        if (movimento.getTipo() == TipoMovimentoCredito.ESTORNO) {
            return "ESTORNO";
        }
        if (movimento.getTipo() == TipoMovimentoCredito.AJUSTE) {
            return movimento.getDirecao() == DirecaoMovimentoCredito.CREDITO
                    ? "AJUSTE_ADMIN_POSITIVO"
                    : "AJUSTE_ADMIN_NEGATIVO";
        }
        return movimento.getDirecao() == DirecaoMovimentoCredito.CREDITO ? "CREDITO" : "DEBITO";
    }

    private DirecaoMovimentoCredito direcao(String value) {
        try {
            return DirecaoMovimentoCredito.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direcao do ajuste invalida");
        }
    }

    private void validarAdministrador(AdminUserPrincipal administrador) {
        if (administrador == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria", exception);
        }
    }
}
