package br.com.topsdojob.v3.importacao.financeiro;

import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.PagamentoLegado;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MetodoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MatrizPagamentoHistoricoImportacao {
  private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999.99");

  public Linha classificar(PagamentoLegado pagamento, OffsetDateTime capturadoEm) {
    if (pagamento == null || capturadoEm == null) {
      throw new IllegalArgumentException("pagamento e captura devem ser informados");
    }
    ProvedorPagamento provedor = provedor(pagamento.provedor());
    if (pagamento.provedor() == null) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_PROVEDOR_AUSENTE);
    }
    if (!valorValido(pagamento.valor())) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_VALOR_INCONSISTENTE);
    }
    if (!creditosValidos(pagamento.creditos())) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_CREDITOS_INCONSISTENTES);
    }
    if (pagamento.valorEvidencia() != null
        && pagamento.valor().compareTo(pagamento.valorEvidencia()) != 0) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_VALOR_INCONSISTENTE);
    }
    if (pagamento.creditosEvidencia() != null
        && !pagamento.creditos().equals(pagamento.creditosEvidencia())) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_CREDITOS_INCONSISTENTES);
    }
    String estado = normalizar(pagamento.estado());
    return switch (estado) {
      case "APPROVED", "APROVADO", "PAID", "CONCLUIDA" ->
          aprovado(pagamento, provedor);
      case "PENDING", "PENDENTE", "IN_PROCESS", "AGUARDANDO_PAGAMENTO" ->
          pendente(pagamento, provedor, capturadoEm);
      case "CANCELLED", "CANCELED", "CANCELADO", "REMOVIDA" ->
          terminal(pagamento, provedor, StatusInternoPagamento.CANCELADO, false);
      case "EXPIRED", "EXPIRADO" ->
          terminal(pagamento, provedor, StatusInternoPagamento.EXPIRADO, false);
      case "REFUNDED", "CHARGED_BACK", "ESTORNADO", "REVERSED" ->
          terminal(pagamento, provedor, StatusInternoPagamento.ESTORNADO, false);
      case "CREATED", "CRIADO" -> Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_ESTADO_INCOMPLETO);
      default -> Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_ESTADO_INCOMPLETO);
    };
  }

  private Linha aprovado(PagamentoLegado pagamento, ProvedorPagamento provedor) {
    if (!pagamento.creditado()) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_ESTADO_INCOMPLETO);
    }
    if (dataReferencia(pagamento) == null) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_DATA_AUSENTE);
    }
    return Linha.importar(provedor, StatusInternoPagamento.APROVADO, true);
  }

  private Linha pendente(
      PagamentoLegado pagamento,
      ProvedorPagamento provedor,
      OffsetDateTime capturadoEm) {
    if (pagamento.creditado()) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_ESTADO_INCOMPLETO);
    }
    if (pagamento.expiracaoEm() == null
        || pagamento.expiracaoEm().isAfter(capturadoEm)) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_ESTADO_INCOMPLETO);
    }
    return Linha.importar(provedor, StatusInternoPagamento.EXPIRADO, false);
  }

  private Linha terminal(
      PagamentoLegado pagamento,
      ProvedorPagamento provedor,
      StatusInternoPagamento status,
      boolean receita) {
    if (dataReferencia(pagamento) == null) {
      return Linha.quarentena(
          provedor, CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_DATA_AUSENTE);
    }
    return Linha.importar(provedor, status, receita);
  }

  public OffsetDateTime dataReferencia(PagamentoLegado pagamento) {
    return pagamento.atualizadoEm() != null
        ? pagamento.atualizadoEm()
        : pagamento.criadoEm();
  }

  private boolean valorValido(BigDecimal valor) {
    if (valor == null || valor.signum() <= 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
      return false;
    }
    try {
      valor.setScale(2, RoundingMode.UNNECESSARY);
      return true;
    } catch (ArithmeticException exception) {
      return false;
    }
  }

  private boolean creditosValidos(Integer creditos) {
    return creditos != null && creditos > 0 && creditos <= 1_000_000;
  }

  private ProvedorPagamento provedor(String valor) {
    String normalizado = normalizar(valor);
    if (normalizado.contains("EFI") || normalizado.contains("GERENCIANET")) {
      return ProvedorPagamento.EFI;
    }
    if (normalizado.contains("MERCADO") || normalizado.equals("MP")) {
      return ProvedorPagamento.MERCADO_PAGO_LEGADO;
    }
    return normalizado.isBlank()
        ? ProvedorPagamento.DESCONHECIDO
        : ProvedorPagamento.OUTRO_LEGADO;
  }

  private String normalizar(String valor) {
    return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
  }

  public enum Decisao {
    IMPORTAR_HISTORICO,
    QUARENTENA
  }

  public record Linha(
      Decisao decisao,
      StatusInternoPagamento statusV3,
      ProvedorPagamento provedorV3,
      MetodoPagamento metodoV3,
      boolean entraEmReceita,
      boolean conciliavel,
      boolean geraLedger,
      CodigoPendenciaImportacao pendencia) {

    private static Linha importar(
        ProvedorPagamento provedor,
        StatusInternoPagamento status,
        boolean receita) {
      MetodoPagamento metodo = provedor == ProvedorPagamento.EFI
          ? MetodoPagamento.PIX
          : MetodoPagamento.LEGADO;
      return new Linha(
          Decisao.IMPORTAR_HISTORICO,
          status,
          provedor,
          metodo,
          receita,
          false,
          false,
          null);
    }

    private static Linha quarentena(
        ProvedorPagamento provedor,
        CodigoPendenciaImportacao pendencia) {
      MetodoPagamento metodo = provedor == ProvedorPagamento.EFI
          ? MetodoPagamento.PIX
          : MetodoPagamento.LEGADO;
      return new Linha(
          Decisao.QUARENTENA,
          null,
          provedor,
          metodo,
          false,
          false,
          false,
          pendencia);
    }
  }
}
