package br.com.topsdojob.v3.importacao.financeiro;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SnapshotFinanceiroFaseQuatro {
  private SnapshotFinanceiroFaseQuatro() {
  }

  public record Snapshot(
      String snapshotId,
      OffsetDateTime capturadoEm,
      UUID atorSistemaV3Id,
      Map<String, UUID> usuariosV3,
      Map<String, UUID> anunciosV3,
      Map<String, UUID> planosCreditoV3,
      List<PagamentoLegado> pagamentos,
      List<GrupoAtivacaoLegado> gruposAtivacao,
      List<CarteiraLegada> carteiras) {

    public Snapshot {
      snapshotId = obrigatorio(snapshotId, "snapshotId");
      if (capturadoEm == null) {
        throw new IllegalArgumentException("capturadoEm deve ser informado");
      }
      if (atorSistemaV3Id == null) {
        throw new IllegalArgumentException("atorSistemaV3Id deve ser informado");
      }
      usuariosV3 = Map.copyOf(usuariosV3 == null ? Map.of() : usuariosV3);
      anunciosV3 = Map.copyOf(anunciosV3 == null ? Map.of() : anunciosV3);
      planosCreditoV3 = Map.copyOf(
          planosCreditoV3 == null ? Map.of() : planosCreditoV3);
      pagamentos = List.copyOf(pagamentos == null ? List.of() : pagamentos);
      gruposAtivacao = List.copyOf(
          gruposAtivacao == null ? List.of() : gruposAtivacao);
      carteiras = List.copyOf(carteiras == null ? List.of() : carteiras);
    }
  }

  public record PagamentoLegado(
      String idOrigem,
      String usuarioOrigemId,
      String planoOrigemId,
      String provedor,
      String estado,
      String identificadorProvedor,
      BigDecimal valor,
      Integer creditos,
      String moeda,
      boolean creditado,
      OffsetDateTime expiracaoEm,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      BigDecimal valorEvidencia,
      Integer creditosEvidencia) {

    public PagamentoLegado {
      idOrigem = obrigatorio(idOrigem, "pagamento.idOrigem");
      usuarioOrigemId = obrigatorio(
          usuarioOrigemId, "pagamento.usuarioOrigemId");
      planoOrigemId = opcional(planoOrigemId);
      provedor = opcional(provedor);
      estado = obrigatorio(estado, "pagamento.estado");
      identificadorProvedor = opcional(identificadorProvedor);
      moeda = opcional(moeda);
    }
  }

  public record GrupoAtivacaoLegado(
      String idOrigem,
      String usuarioOrigemId,
      String anuncioOrigemId,
      String origem,
      String pagamentoOrigemId,
      String status,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      List<AtivacaoLegada> ativacoes) {

    public GrupoAtivacaoLegado {
      idOrigem = obrigatorio(idOrigem, "grupo.idOrigem");
      usuarioOrigemId = obrigatorio(
          usuarioOrigemId, "grupo.usuarioOrigemId");
      anuncioOrigemId = opcional(anuncioOrigemId);
      origem = opcional(origem);
      pagamentoOrigemId = opcional(pagamentoOrigemId);
      status = obrigatorio(status, "grupo.status");
      ativacoes = List.copyOf(ativacoes == null ? List.of() : ativacoes);
    }
  }

  public record AtivacaoLegada(
      String idOrigem,
      String beneficioCodigoV3,
      Integer creditosCobrados,
      BigDecimal precoSnapshot,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm) {

    public AtivacaoLegada {
      idOrigem = obrigatorio(idOrigem, "ativacao.idOrigem");
      beneficioCodigoV3 = obrigatorio(
          beneficioCodigoV3, "ativacao.beneficioCodigoV3");
    }
  }

  public record CarteiraLegada(
      String idOrigem,
      String usuarioOrigemId,
      int saldoFinal,
      boolean divergenciaHistorica,
      String statusUsuarioLegado) {

    public CarteiraLegada {
      idOrigem = obrigatorio(idOrigem, "carteira.idOrigem");
      usuarioOrigemId = obrigatorio(
          usuarioOrigemId, "carteira.usuarioOrigemId");
      statusUsuarioLegado = opcional(statusUsuarioLegado);
    }
  }

  private static String obrigatorio(String valor, String campo) {
    String normalizado = opcional(valor);
    if (normalizado == null) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return normalizado;
  }

  private static String opcional(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
