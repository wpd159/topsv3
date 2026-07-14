package br.com.topsdojob.v3.application.credito;

import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditoLedgerOperacaoService {

    private final UsuarioRepository usuarioRepository;
    private final MovimentoCreditoRepository movimentoRepository;

    public CreditoLedgerOperacaoService(
            UsuarioRepository usuarioRepository,
            MovimentoCreditoRepository movimentoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.movimentoRepository = movimentoRepository;
    }

    @Transactional(readOnly = true)
    public int consultarSaldo(UUID usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado");
        }
        return calcularSaldo(movimentoRepository.findByUsuarioIdOrderByCriadoEmAsc(usuarioId));
    }

    public int bloquearEConsultarSaldo(UUID usuarioId) {
        usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
        return calcularSaldo(movimentoRepository.findByUsuarioIdOrderByCriadoEmAsc(usuarioId));
    }

    public CreditoLancamentoResultado registrar(
            UUID usuarioId,
            TipoMovimentoCredito tipo,
            DirecaoMovimentoCredito direcao,
            int quantidade,
            int saldoAntes,
            OrigemMovimentoCredito origem,
            String referenciaTipo,
            UUID referenciaId,
            String chaveOperacional,
            UUID atorUsuarioId,
            String motivo,
            String requestId) {
        validarQuantidade(quantidade);
        String chave = chaveObrigatoria(chaveOperacional);
        var existente = movimentoRepository.findByIdempotencyKey(chave);
        if (existente.isPresent()) {
            return new CreditoLancamentoResultado(existente.get(), true);
        }
        int saldoDepois = direcao == DirecaoMovimentoCredito.CREDITO
                ? Math.addExact(saldoAntes, quantidade)
                : Math.subtractExact(saldoAntes, quantidade);
        if (saldoDepois < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "saldo de creditos insuficiente");
        }
        MovimentoCreditoEntity movimento = MovimentoCreditoEntity.registrar(
                UUID.randomUUID(),
                usuarioId,
                tipo,
                direcao,
                quantidade,
                saldoAntes,
                saldoDepois,
                origem,
                referenciaTipo,
                referenciaId,
                chave,
                atorUsuarioId,
                motivoSeguro(motivo),
                requestIdSeguro(requestId),
                OffsetDateTime.now(ZoneOffset.UTC));
        return new CreditoLancamentoResultado(movimentoRepository.save(movimento), false);
    }

    public int calcularSaldo(List<MovimentoCreditoEntity> movimentos) {
        if (movimentos == null || movimentos.isEmpty()) {
            return 0;
        }
        Integer saldo = movimentos.get(0).getSaldoAntes();
        if (saldo == null || saldo < 0) {
            throw inconsistente();
        }
        for (MovimentoCreditoEntity movimento : movimentos) {
            if (!saldo.equals(movimento.getSaldoAntes())
                    || movimento.getQuantidade() == null
                    || movimento.getQuantidade() <= 0
                    || movimento.getDirecao() == null) {
                throw inconsistente();
            }
            saldo = movimento.getDirecao() == DirecaoMovimentoCredito.CREDITO
                    ? Math.addExact(saldo, movimento.getQuantidade())
                    : Math.subtractExact(saldo, movimento.getQuantidade());
            if (saldo < 0 || !saldo.equals(movimento.getSaldoDepois())) {
                throw inconsistente();
            }
        }
        return saldo;
    }

    public static String chaveObrigatoria(String value) {
        String normalizado = value == null ? "" : value.trim();
        if (normalizado.isEmpty() || normalizado.length() > 160 || !normalizado.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chave de idempotencia invalida");
        }
        return normalizado;
    }

    private void validarQuantidade(int quantidade) {
        if (quantidade <= 0 || quantidade > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantidade de creditos invalida");
        }
    }

    private String motivoSeguro(String motivo) {
        String valor = motivo == null ? "" : motivo.trim();
        if (valor.length() < 5 || valor.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo obrigatorio");
        }
        return valor;
    }

    private String requestIdSeguro(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.trim().substring(0, Math.min(requestId.trim().length(), 120));
    }

    private ResponseStatusException inconsistente() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "ledger de creditos inconsistente");
    }
}
