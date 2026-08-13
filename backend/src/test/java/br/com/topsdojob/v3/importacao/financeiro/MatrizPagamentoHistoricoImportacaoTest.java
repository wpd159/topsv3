package br.com.topsdojob.v3.importacao.financeiro;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.financeiro.MatrizPagamentoHistoricoImportacao.Decisao;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.PagamentoLegado;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MatrizPagamentoHistoricoImportacaoTest {
  private static final OffsetDateTime AGORA =
      OffsetDateTime.parse("2026-08-01T12:00:00Z");
  private final MatrizPagamentoHistoricoImportacao matriz =
      new MatrizPagamentoHistoricoImportacao();

  @Test
  void aprovadoCreditadoEntraEmReceitaSemConciliacaoOuLedger() {
    var linha = matriz.classificar(
        pagamento("EFI", "APPROVED", true, "10.00", 100, AGORA.minusDays(1)),
        AGORA);

    assertThat(linha.decisao()).isEqualTo(Decisao.IMPORTAR_HISTORICO);
    assertThat(linha.statusV3()).isEqualTo(StatusInternoPagamento.APROVADO);
    assertThat(linha.provedorV3()).isEqualTo(ProvedorPagamento.EFI);
    assertThat(linha.entraEmReceita()).isTrue();
    assertThat(linha.conciliavel()).isFalse();
    assertThat(linha.geraLedger()).isFalse();
  }

  @Test
  void mercadoPagoPermaneceHistoricoSemProviderExecutavel() {
    var linha = matriz.classificar(
        pagamento("MERCADO_PAGO", "PAID", true, "20.00", 200, AGORA.minusDays(1)),
        AGORA);

    assertThat(linha.decisao()).isEqualTo(Decisao.IMPORTAR_HISTORICO);
    assertThat(linha.provedorV3()).isEqualTo(ProvedorPagamento.MERCADO_PAGO_LEGADO);
    assertThat(linha.conciliavel()).isFalse();
    assertThat(linha.geraLedger()).isFalse();
  }

  @Test
  void pendenteVencidoViraExpiradoSemCredito() {
    PagamentoLegado pagamento = pagamento(
        "EFI", "PENDING", false, "30.00", 300, AGORA.minusDays(1));
    pagamento = new PagamentoLegado(
        pagamento.idOrigem(), pagamento.usuarioOrigemId(), pagamento.planoOrigemId(),
        pagamento.provedor(), pagamento.estado(), pagamento.identificadorProvedor(),
        pagamento.valor(), pagamento.creditos(), pagamento.moeda(), pagamento.creditado(),
        AGORA, pagamento.criadoEm(), pagamento.atualizadoEm(),
        pagamento.valorEvidencia(), pagamento.creditosEvidencia());

    var linha = matriz.classificar(pagamento, AGORA);

    assertThat(linha.decisao()).isEqualTo(Decisao.IMPORTAR_HISTORICO);
    assertThat(linha.statusV3()).isEqualTo(StatusInternoPagamento.EXPIRADO);
    assertThat(linha.entraEmReceita()).isFalse();
    assertThat(linha.geraLedger()).isFalse();
  }

  @Test
  void terminaisNaoGeramReceitaNemEfeitoFinanceiro() {
    for (String estado : new String[] {"CANCELLED", "EXPIRED", "REFUNDED"}) {
      var linha = matriz.classificar(
          pagamento("EFI", estado, false, "40.00", 400, AGORA.minusDays(1)),
          AGORA);
      assertThat(linha.decisao()).as(estado).isEqualTo(Decisao.IMPORTAR_HISTORICO);
      assertThat(linha.entraEmReceita()).as(estado).isFalse();
      assertThat(linha.conciliavel()).as(estado).isFalse();
      assertThat(linha.geraLedger()).as(estado).isFalse();
    }
  }

  @Test
  void createdEInconsistenciasFicamEmQuarentena() {
    assertThat(matriz.classificar(
        pagamento("EFI", "CREATED", false, "10.00", 100, AGORA), AGORA).pendencia())
        .isEqualTo(CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_ESTADO_INCOMPLETO);

    PagamentoLegado valor = pagamento("EFI", "APPROVED", true, "10.00", 100, AGORA);
    valor = new PagamentoLegado(
        valor.idOrigem(), valor.usuarioOrigemId(), valor.planoOrigemId(), valor.provedor(),
        valor.estado(), valor.identificadorProvedor(), valor.valor(), valor.creditos(),
        valor.moeda(), valor.creditado(), valor.expiracaoEm(), valor.criadoEm(),
        valor.atualizadoEm(), new BigDecimal("11.00"), valor.creditosEvidencia());
    assertThat(matriz.classificar(valor, AGORA).pendencia())
        .isEqualTo(CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_VALOR_INCONSISTENTE);

    PagamentoLegado creditos = pagamento("EFI", "APPROVED", true, "10.00", 100, AGORA);
    creditos = new PagamentoLegado(
        creditos.idOrigem(), creditos.usuarioOrigemId(), creditos.planoOrigemId(),
        creditos.provedor(), creditos.estado(), creditos.identificadorProvedor(),
        creditos.valor(), creditos.creditos(), creditos.moeda(), creditos.creditado(),
        creditos.expiracaoEm(), creditos.criadoEm(), creditos.atualizadoEm(),
        creditos.valorEvidencia(), 101);
    assertThat(matriz.classificar(creditos, AGORA).pendencia())
        .isEqualTo(CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_CREDITOS_INCONSISTENTES);
  }

  private PagamentoLegado pagamento(
      String provedor,
      String estado,
      boolean creditado,
      String valor,
      int creditos,
      OffsetDateTime atualizadoEm) {
    return new PagamentoLegado(
        "payment-synthetic",
        "user-synthetic",
        "plan-synthetic",
        provedor,
        estado,
        "provider-synthetic",
        new BigDecimal(valor),
        creditos,
        "BRL",
        creditado,
        AGORA.minusHours(1),
        AGORA.minusDays(2),
        atualizadoEm,
        new BigDecimal(valor),
        creditos);
  }
}
