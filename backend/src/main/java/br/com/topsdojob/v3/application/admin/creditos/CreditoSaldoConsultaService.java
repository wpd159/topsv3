package br.com.topsdojob.v3.application.admin.creditos;

import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoSaldoDto;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.credito.SaldoCreditoUsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.SaldoCreditoUsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditoSaldoConsultaService {

    private final SaldoCreditoUsuarioRepository saldoRepository;
    private final MovimentoCreditoRepository movimentoRepository;

    public CreditoSaldoConsultaService(
            SaldoCreditoUsuarioRepository saldoRepository,
            MovimentoCreditoRepository movimentoRepository) {
        this.saldoRepository = saldoRepository;
        this.movimentoRepository = movimentoRepository;
    }

    @Transactional(readOnly = true)
    public AdminCreditoSaldoDto consultar(UUID usuarioId) {
        Optional<SaldoCreditoUsuarioEntity> saldo = saldoRepository.findById(usuarioId);
        List<MovimentoCreditoEntity> movimentos = movimentoRepository.findByUsuarioIdOrderByCriadoEmAsc(usuarioId);
        Integer saldoUltimoMovimento = movimentos.isEmpty()
                ? 0
                : movimentos.get(movimentos.size() - 1).getSaldoDepois();
        Integer saldoCalculadoMovimentos = calcularSaldoMovimentos(movimentos);
        Integer saldoMaterializado = saldo.map(SaldoCreditoUsuarioEntity::getSaldoAtual).orElse(null);
        Integer saldoFonteLedger = saldoCalculadoMovimentos;
        int entradas = contarPorDirecao(movimentos, DirecaoMovimentoCredito.CREDITO);
        int saidas = contarPorDirecao(movimentos, DirecaoMovimentoCredito.DEBITO);
        List<String> codigos = new ArrayList<>();
        if (saldoMaterializado != null
                && (saldoUltimoMovimento == null || !saldoMaterializado.equals(saldoUltimoMovimento))) {
            codigos.add(CreditoConsistenciaCodigo.SALDO_INCONSISTENTE.name());
        }
        if (saldoCalculadoMovimentos == null || saldoUltimoMovimento == null
                || !saldoCalculadoMovimentos.equals(saldoUltimoMovimento)) {
            codigos.add(CreditoConsistenciaCodigo.SALDO_INCONSISTENTE.name());
        }
        if (codigos.isEmpty()) {
            codigos.add(CreditoConsistenciaCodigo.CREDITO_OK.name());
        }
        return new AdminCreditoSaldoDto(
                usuarioId,
                saldoFonteLedger,
                saldoCalculadoMovimentos,
                saldoUltimoMovimento,
                movimentos.size(),
                entradas,
                saidas,
                codigos.size() == 1 && CreditoConsistenciaCodigo.CREDITO_OK.name().equals(codigos.get(0)),
                codigos.stream().distinct().toList(),
                saldo.map(SaldoCreditoUsuarioEntity::getAtualizadoEm).orElse(null),
                false);
    }

    private int contarPorDirecao(List<MovimentoCreditoEntity> movimentos, DirecaoMovimentoCredito direcao) {
        return (int) movimentos.stream()
                .filter(item -> item.getDirecao() == direcao)
                .count();
    }

    private Integer calcularSaldoMovimentos(List<MovimentoCreditoEntity> movimentos) {
        if (movimentos.isEmpty()) {
            return 0;
        }
        Integer calculado = movimentos.get(0).getSaldoAntes();
        if (calculado == null) {
            return null;
        }
        for (MovimentoCreditoEntity movimento : movimentos) {
            if (movimento.getQuantidade() == null || movimento.getDirecao() == null) {
                return null;
            }
            if (movimento.getDirecao() == DirecaoMovimentoCredito.CREDITO) {
                calculado += movimento.getQuantidade();
            } else if (movimento.getDirecao() == DirecaoMovimentoCredito.DEBITO) {
                calculado -= movimento.getQuantidade();
            } else {
                return null;
            }
        }
        return calculado;
    }
}
