package br.com.topsdojob.v3.importacao.comercial;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SnapshotConfiguracaoComercialFaseTres {
  private SnapshotConfiguracaoComercialFaseTres() {
  }

  public record Snapshot(
      String snapshotId,
      OffsetDateTime capturadoEm,
      UUID atorSistemaV3Id,
      List<BeneficioLegado> beneficios,
      List<OpcaoBeneficioLegada> opcoes,
      List<PacoteCreditoLegado> pacotes,
      List<ConfiguracaoStoryLegada> configuracoesStory) {

    public Snapshot {
      snapshotId = obrigatorio(snapshotId, "snapshotId");
      if (capturadoEm == null) {
        throw new IllegalArgumentException("capturadoEm deve ser informado");
      }
      if (atorSistemaV3Id == null) {
        throw new IllegalArgumentException("atorSistemaV3Id deve ser informado");
      }
      beneficios = List.copyOf(beneficios == null ? List.of() : beneficios);
      opcoes = List.copyOf(opcoes == null ? List.of() : opcoes);
      pacotes = List.copyOf(pacotes == null ? List.of() : pacotes);
      configuracoesStory = List.copyOf(
          configuracoesStory == null ? List.of() : configuracoesStory);
    }
  }

  public record BeneficioLegado(
      String idOrigem,
      String codigo,
      String nome,
      String descricao,
      String escopo,
      boolean ativo,
      Integer ordemExibicao) {

    public BeneficioLegado {
      idOrigem = obrigatorio(idOrigem, "beneficio.idOrigem");
      codigo = obrigatorio(codigo, "beneficio.codigo");
    }
  }

  public record OpcaoBeneficioLegada(
      String idOrigem,
      String beneficioOrigemId,
      Integer duracaoDias,
      Integer custoCreditos,
      BigDecimal precoReferencia,
      boolean ativo,
      Integer ordemExibicao) {

    public OpcaoBeneficioLegada {
      idOrigem = obrigatorio(idOrigem, "opcao.idOrigem");
      beneficioOrigemId = obrigatorio(
          beneficioOrigemId, "opcao.beneficioOrigemId");
    }
  }

  public record PacoteCreditoLegado(
      String idOrigem,
      String codigoLegado,
      String nome,
      String descricao,
      Integer quantidadeCreditosBase,
      Integer bonusCreditos,
      BigDecimal valor,
      String moeda,
      boolean ativo,
      Integer ordemExibicao,
      Integer validadeDias) {

    public PacoteCreditoLegado {
      idOrigem = obrigatorio(idOrigem, "pacote.idOrigem");
      codigoLegado = obrigatorio(codigoLegado, "pacote.codigoLegado");
    }
  }

  public record ConfiguracaoStoryLegada(
      String idOrigem,
      String nome,
      String descricao,
      Integer custoCreditos,
      boolean ativo,
      Integer ordemExibicao,
      Integer duracaoHoras,
      List<String> modos) {

    public ConfiguracaoStoryLegada {
      idOrigem = obrigatorio(idOrigem, "story.idOrigem");
      modos = List.copyOf(modos == null ? List.of() : modos);
    }
  }

  private static String obrigatorio(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return valor.trim();
  }
}
