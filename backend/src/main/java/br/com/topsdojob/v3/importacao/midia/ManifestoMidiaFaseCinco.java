package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record ManifestoMidiaFaseCinco(List<Item> itens) {

  public ManifestoMidiaFaseCinco {
    itens = (itens == null ? List.<Item>of() : itens).stream()
        .sorted(Comparator.comparing(Item::ordemEstavel))
        .toList();
  }

  public String sha256() {
    String canonical = itens.stream()
        .map(Item::representacaoCanonica)
        .reduce("", (left, right) -> left + right + "\n");
    return MidiaMigracaoHashes.sha256(canonical);
  }

  public record Item(
      String idOrigem,
      EntidadeTipo entidadeTipo,
      String entidadeOrigemId,
      String entidadeV3Id,
      String proprietarioOrigemId,
      String proprietarioV3Id,
      String referenciaOrigemId,
      Finalidade finalidade,
      TipoMidia tipoMidia,
      Visibilidade visibilidade,
      EstadoModeracao estadoModeracao,
      boolean originalExiste,
      boolean capaValida,
      int ordem,
      String mimeType,
      long tamanhoBytes,
      String sha256,
      Origem origem,
      Destino destino,
      Decisao decisao,
      String motivo) {

    public Item {
      idOrigem = obrigatorio(idOrigem, "idOrigem");
      entidadeTipo = Objects.requireNonNull(entidadeTipo, "entidadeTipo obrigatorio");
      entidadeOrigemId = opcional(entidadeOrigemId);
      entidadeV3Id = opcional(entidadeV3Id);
      proprietarioOrigemId = opcional(proprietarioOrigemId);
      proprietarioV3Id = opcional(proprietarioV3Id);
      referenciaOrigemId = opcional(referenciaOrigemId);
      finalidade = Objects.requireNonNull(finalidade, "finalidade obrigatoria");
      tipoMidia = Objects.requireNonNull(tipoMidia, "tipoMidia obrigatorio");
      visibilidade = Objects.requireNonNull(visibilidade, "visibilidade obrigatoria");
      estadoModeracao = Objects.requireNonNull(
          estadoModeracao, "estadoModeracao obrigatorio");
      mimeType = normalizarMime(mimeType);
      sha256 = sha256 == null ? null : sha256.toLowerCase(Locale.ROOT);
      origem = Objects.requireNonNull(origem, "origem obrigatoria");
      decisao = Objects.requireNonNull(decisao, "decisao obrigatoria");
      motivo = opcional(motivo);
      if (decisao == Decisao.IMPORTAR && destino == null) {
        throw new IllegalArgumentException("destino obrigatorio para item importavel");
      }
      if (decisao != Decisao.IMPORTAR && motivo == null) {
        throw new IllegalArgumentException("motivo obrigatorio para item nao importavel");
      }
    }

    String ordemEstavel() {
      return entidadeTipo + "|" + valor(entidadeOrigemId) + "|"
          + String.format(Locale.ROOT, "%08d", ordem) + "|" + idOrigem;
    }

    String representacaoCanonica() {
      return String.join("|",
          idOrigem,
          entidadeTipo.name(),
          valor(entidadeOrigemId),
          valor(entidadeV3Id),
          valor(proprietarioOrigemId),
          valor(proprietarioV3Id),
          valor(referenciaOrigemId),
          finalidade.name(),
          tipoMidia.name(),
          visibilidade.name(),
          estadoModeracao.name(),
          Boolean.toString(originalExiste),
          Boolean.toString(capaValida),
          Integer.toString(ordem),
          valor(mimeType),
          Long.toString(tamanhoBytes),
          valor(sha256),
          origem.representacaoCanonica(),
          destino == null ? "" : destino.representacaoCanonica(),
          decisao.name(),
          valor(motivo));
    }

    public String fingerprint() {
      return MidiaMigracaoHashes.sha256(representacaoCanonica());
    }
  }

  public record Origem(TipoOrigem tipo, StorageArea area, String localizador) {

    public Origem {
      tipo = Objects.requireNonNull(tipo, "tipo de origem obrigatorio");
      localizador = obrigatorio(localizador, "localizador");
      if (tipo == TipoOrigem.OBJECT_STORAGE && area == null) {
        throw new IllegalArgumentException("area obrigatoria para object storage");
      }
      if (tipo == TipoOrigem.ARQUIVO_LOCAL && area != null) {
        throw new IllegalArgumentException("arquivo local nao possui area de storage");
      }
    }

    String representacaoCanonica() {
      return tipo + "|" + (area == null ? "" : area.name()) + "|" + localizador;
    }

    @Override
    public String toString() {
      return "Origem[tipo=" + tipo + ", area=" + area + ", localizador=<sanitizado>]";
    }
  }

  public record Destino(StorageArea area, String bucket, String chave) {

    public Destino {
      area = Objects.requireNonNull(area, "area de destino obrigatoria");
      bucket = obrigatorio(bucket, "bucket");
      chave = obrigatorio(chave, "chave");
    }

    String representacaoCanonica() {
      return area + "|" + bucket + "|" + chave;
    }

    @Override
    public String toString() {
      return "Destino[area=" + area + ", bucket=<sanitizado>, chave=<sanitizada>]";
    }
  }

  public enum EntidadeTipo {
    ANUNCIO,
    REVISAO_ANUNCIO,
    KYC,
    EDITORIAL,
    STORY,
    SUPORTE,
    DENUNCIA,
    COMPLIANCE,
    VISITANTE_COMPLIANCE,
    WIZARD,
    QA,
    TEMPORARIO,
    DESCONHECIDO
  }

  public enum Finalidade {
    CAPA,
    GALERIA,
    VIDEO,
    REVISAO,
    KYC_IDENTIDADE,
    KYC_IDADE,
    BLOG_CAPA,
    BLOG_OG,
    EDITORIAL,
    STORY,
    SUPORTE,
    DENUNCIA,
    COMPLIANCE,
    PREVIEW,
    THUMBNAIL,
    TEMPORARIO
  }

  public enum TipoMidia {
    FOTO,
    VIDEO,
    DOCUMENTO
  }

  public enum Visibilidade {
    LIVRE,
    RESTRITA_18,
    PRIVADA
  }

  public enum EstadoModeracao {
    APROVADA,
    PENDENTE,
    REJEITADA,
    NAO_APLICAVEL
  }

  public enum TipoOrigem {
    OBJECT_STORAGE,
    ARQUIVO_LOCAL
  }

  public enum Decisao {
    IMPORTAR,
    DESCARTAR,
    QUARENTENA
  }

  private static String obrigatorio(String value, String field) {
    String normalized = opcional(value);
    if (normalized == null) {
      throw new IllegalArgumentException(field + " obrigatorio");
    }
    return normalized;
  }

  private static String opcional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String valor(String value) {
    return value == null ? "" : value;
  }

  private static String normalizarMime(String value) {
    String normalized = opcional(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }
}
