package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import java.util.Locale;
import java.util.Objects;

public final class GeradorChaveDestinoMidiaMigracao {

  private final R2StorageProperties properties;

  public GeradorChaveDestinoMidiaMigracao(R2StorageProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties obrigatorias");
  }

  public Destino gerar(
      EntidadeTipo entidadeTipo,
      String entidadeId,
      String proprietarioId,
      String referenciaId,
      String origemFingerprint,
      Finalidade finalidade,
      Visibilidade visibilidade,
      String checksum,
      String extensao) {
    if (!MidiaMigracaoHashes.sha256Valido(checksum)) {
      throw new IllegalArgumentException("checksum SHA-256 invalido");
    }
    String extension = normalizarExtensao(extensao);
    String entity = MidiaMigracaoHashes.identificadorSeguro(exigir(entidadeId));
    String owner = MidiaMigracaoHashes.identificadorSeguro(exigir(proprietarioId));
    exigir(referenciaId);
    String source = exigirFingerprint(origemFingerprint);
    String suffix = checksum + "." + extension;

    return switch (entidadeTipo) {
      case ANUNCIO -> destinoMidia(
          visibilidade == Visibilidade.LIVRE ? StorageArea.PUBLIC_MEDIA : StorageArea.PRIVATE_MEDIA,
          caminhoAnuncio("", entity, source, "original", checksum, extension));
      case REVISAO_ANUNCIO -> destinoMidia(
          StorageArea.PRIVATE_MEDIA,
          "importacao/anuncios/" + entity + "/revisoes/" + source + "/" + suffix);
      case KYC -> new Destino(
          StorageArea.PRIVATE_DOCUMENT,
          properties.getDocumentBucket(),
          properties.getDocumentPrefix() + "importacao/usuarios/" + owner + "/kyc/"
              + source + "/" + suffix);
      case EDITORIAL -> destinoMidia(
          visibilidade == Visibilidade.LIVRE ? StorageArea.PUBLIC_MEDIA : StorageArea.PRIVATE_MEDIA,
          "importacao/editorial/" + entity + "/" + source + "/" + suffix);
      default -> throw new IllegalArgumentException("entidade sem destino de migracao");
    };
  }

  public static String caminhoAnuncio(
      String prefixo,
      String anuncioId,
      String midiaLogica,
      String variante,
      String checksum,
      String extensao) {
    String prefix = prefixo == null ? "" : prefixo;
    return prefix + "importacao/anuncios/" + exigirSegmento(anuncioId)
        + "/midias/" + exigirSegmento(midiaLogica)
        + "/" + exigirSegmento(variante).toLowerCase(Locale.ROOT)
        + "/" + exigirFingerprint(checksum)
        + "." + normalizarExtensao(extensao);
  }

  private Destino destinoMidia(StorageArea area, String relativeKey) {
    if (area == StorageArea.PUBLIC_MEDIA) {
      return new Destino(
          area,
          properties.getPublicMediaBucket(),
          properties.getPublicMediaPrefix() + relativeKey);
    }
    return new Destino(
        area,
        properties.getPrivateMediaBucket(),
        properties.getPrivateMediaPrefix() + relativeKey);
  }

  private static String exigir(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("identificador obrigatorio para chave de destino");
    }
    return value.trim();
  }

  private static String exigirSegmento(String value) {
    String normalized = exigir(value);
    if (!normalized.matches("[A-Za-z0-9._-]+")) {
      throw new IllegalArgumentException("segmento invalido para chave de destino");
    }
    return normalized;
  }

  private static String exigirFingerprint(String value) {
    String normalized = exigir(value).toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-f0-9]{24}|[a-f0-9]{64}")) {
      throw new IllegalArgumentException("fingerprint invalido para chave de destino");
    }
    return normalized;
  }

  private static String normalizarExtensao(String value) {
    String normalized = exigir(value).toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
    if (!normalized.matches("[a-z0-9]{2,5}")) {
      throw new IllegalArgumentException("extensao invalida");
    }
    return normalized;
  }
}
