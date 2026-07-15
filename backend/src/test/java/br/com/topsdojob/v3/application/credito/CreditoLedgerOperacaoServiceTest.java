package br.com.topsdojob.v3.application.credito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CreditoLedgerOperacaoServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final MovimentoCreditoRepository movimentoRepository = mock(MovimentoCreditoRepository.class);
    private final CreditoLedgerOperacaoService service = new CreditoLedgerOperacaoService(
            usuarioRepository,
            movimentoRepository);

    @Test
    void calculaSaldoExclusivamentePelaSequenciaImutavel() {
        UUID usuarioId = UUID.randomUUID();
        MovimentoCreditoEntity entrada = movimento(
                usuarioId, DirecaoMovimentoCredito.CREDITO, 20, 0, 20, "entrada");
        MovimentoCreditoEntity saida = movimento(
                usuarioId, DirecaoMovimentoCredito.DEBITO, 7, 20, 13, "saida");

        assertThat(service.calcularSaldo(List.of(entrada, saida))).isEqualTo(13);
    }

    @Test
    void saldoMigradoAceitaDebitoEEstornoPosteriores() {
        UUID usuarioId = UUID.randomUUID();
        MovimentoCreditoEntity inicial = movimento(
                usuarioId,
                TipoMovimentoCredito.MIGRACAO_SALDO_INICIAL,
                DirecaoMovimentoCredito.CREDITO,
                30,
                0,
                30,
                "saldo-inicial");
        MovimentoCreditoEntity compra = movimento(
                usuarioId, TipoMovimentoCredito.SAIDA, DirecaoMovimentoCredito.DEBITO,
                8, 30, 22, "compra-premium");
        MovimentoCreditoEntity estorno = movimento(
                usuarioId, TipoMovimentoCredito.ESTORNO, DirecaoMovimentoCredito.CREDITO,
                8, 22, 30, "estorno-premium");

        assertThat(service.calcularSaldo(List.of(inicial, compra, estorno))).isEqualTo(30);
    }

    @Test
    void impedeSaldoNegativoSemPersistirLancamento() {
        UUID usuarioId = UUID.randomUUID();
        when(movimentoRepository.findByIdempotencyKey("operacao-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(
                usuarioId,
                TipoMovimentoCredito.SAIDA,
                DirecaoMovimentoCredito.DEBITO,
                6,
                5,
                OrigemMovimentoCredito.BENEFICIO,
                "TESTE",
                UUID.randomUUID(),
                "operacao-1",
                usuarioId,
                "motivo de teste",
                "req-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("saldo de creditos insuficiente");
        verify(movimentoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeticaoDaChaveNaoGeraSegundoLancamento() {
        UUID usuarioId = UUID.randomUUID();
        MovimentoCreditoEntity existente = movimento(
                usuarioId, DirecaoMovimentoCredito.CREDITO, 10, 0, 10, "operacao-2");
        when(movimentoRepository.findByIdempotencyKey("operacao-2")).thenReturn(Optional.of(existente));

        CreditoLancamentoResultado resultado = service.registrar(
                usuarioId,
                TipoMovimentoCredito.AJUSTE,
                DirecaoMovimentoCredito.CREDITO,
                10,
                0,
                OrigemMovimentoCredito.AJUSTE_ADMIN,
                "AJUSTE_ADMIN",
                UUID.randomUUID(),
                "operacao-2",
                usuarioId,
                "ajuste administrativo",
                "req-2");

        assertThat(resultado.idempotente()).isTrue();
        assertThat(resultado.movimento()).isSameAs(existente);
        verify(movimentoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private MovimentoCreditoEntity movimento(
            UUID usuarioId,
            DirecaoMovimentoCredito direcao,
            int quantidade,
            int saldoAntes,
            int saldoDepois,
            String chave) {
        return movimento(
                usuarioId,
                direcao == DirecaoMovimentoCredito.CREDITO
                        ? TipoMovimentoCredito.ENTRADA
                        : TipoMovimentoCredito.SAIDA,
                direcao,
                quantidade,
                saldoAntes,
                saldoDepois,
                chave);
    }

    private MovimentoCreditoEntity movimento(
            UUID usuarioId,
            TipoMovimentoCredito tipo,
            DirecaoMovimentoCredito direcao,
            int quantidade,
            int saldoAntes,
            int saldoDepois,
            String chave) {
        return MovimentoCreditoEntity.registrar(
                UUID.randomUUID(),
                usuarioId,
                tipo,
                direcao,
                quantidade,
                saldoAntes,
                saldoDepois,
                OrigemMovimentoCredito.AJUSTE_ADMIN,
                "TESTE",
                UUID.randomUUID(),
                chave,
                usuarioId,
                "motivo de teste",
                "req-teste",
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
