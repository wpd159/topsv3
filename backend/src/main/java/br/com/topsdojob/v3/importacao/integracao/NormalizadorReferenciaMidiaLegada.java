package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class NormalizadorReferenciaMidiaLegada {

  private final Map<String, Set<StorageArea>> areasPorBucket;
  private final List<URI> origensPublicas;

  NormalizadorReferenciaMidiaLegada(R2StorageProperties properties) {
    Map<String, Set<StorageArea>> buckets = new LinkedHashMap<>();
    adicionarBucket(buckets, properties.getPublicMediaBucket(), StorageArea.PUBLIC_MEDIA);
    adicionarBucket(buckets, properties.getPrivateMediaBucket(), StorageArea.PRIVATE_MEDIA);
    adicionarBucket(buckets, properties.getDocumentBucket(), StorageArea.PRIVATE_DOCUMENT);
    this.areasPorBucket = Map.copyOf(buckets);
    List<URI> bases = new ArrayList<>();
    adicionarBase(bases, properties.getPublicBaseUrl());
    adicionarBase(bases, properties.getPreservedPublicBaseUrl());
    this.origensPublicas = List.copyOf(bases);
  }

  Resultado normalizar(String referencia, StorageArea areaPadrao) {
    String bruto = referencia == null ? null : referencia.trim();
    String referenciaFingerprint = FingerprintMigracaoIntegral.sha256(
        (bruto == null ? "<ausente>" : bruto).getBytes(StandardCharsets.UTF_8));
    if (bruto == null || bruto.isBlank()) {
      return invalido(areaPadrao, referenciaFingerprint, false);
    }
    try {
      if (bruto.regionMatches(true, 0, "r2://", 0, 5)) {
        return normalizarR2(bruto, referenciaFingerprint, areaPadrao);
      }
      if (bruto.matches("(?i)^https?://.*")) {
        return normalizarUrl(bruto, referenciaFingerprint);
      }
      if (bruto.contains("://")) {
        return invalido(areaPadrao, referenciaFingerprint, true);
      }
      String chave = chave(bruto);
      rejeitarBucketNaChave(chave);
      if (areaPadrao == null) {
        return invalido(null, referenciaFingerprint, true);
      }
      return valido(areaPadrao, chave, referenciaFingerprint);
    } catch (IllegalArgumentException exception) {
      return invalido(areaPadrao, referenciaFingerprint, true);
    }
  }

  private Resultado normalizarR2(
      String referencia,
      String fingerprint,
      StorageArea areaPadrao) {
    URI uri = URI.create(referencia);
    if (!"r2".equalsIgnoreCase(uri.getScheme())
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalArgumentException("referencia R2 invalida");
    }
    String bucket = uri.getHost();
    if (bucket == null || bucket.isBlank()) {
      String parte = referencia.substring("r2://".length());
      int separador = parte.indexOf('/');
      if (separador < 1) {
        throw new IllegalArgumentException("bucket R2 ausente");
      }
      bucket = parte.substring(0, separador);
    }
    Set<StorageArea> areas = areasPorBucket.get(bucket);
    if (areas == null) {
      throw new IllegalArgumentException("bucket R2 fora da configuracao externa");
    }
    StorageArea area;
    if (areaPadrao != null && areas.contains(areaPadrao)) {
      area = areaPadrao;
    } else if (areas.size() == 1) {
      area = areas.iterator().next();
    } else {
      throw new IllegalArgumentException("bucket compartilhado exige area persistida compativel");
    }
    String caminho = uri.getRawPath();
    if (caminho == null || caminho.length() < 2) {
      throw new IllegalArgumentException("chave R2 ausente");
    }
    return valido(area, chave(decodificar(caminho.substring(1))), fingerprint);
  }

  private Resultado normalizarUrl(String referencia, String fingerprint) {
    URI uri = URI.create(referencia);
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalArgumentException("URL legada invalida");
    }
    for (URI base : origensPublicas) {
      if (!mesmaOrigem(uri, base)) {
        continue;
      }
      String basePath = normalizarBasePath(base.getRawPath());
      String path = uri.getRawPath() == null ? "" : uri.getRawPath();
      if (!path.startsWith(basePath)) {
        continue;
      }
      String relativo = path.substring(basePath.length());
      return valido(
          StorageArea.PUBLIC_MEDIA,
          chave(decodificar(relativo)),
          fingerprint);
    }
    throw new IllegalArgumentException("URL fora das origens publicas configuradas");
  }

  private Resultado valido(StorageArea area, String chave, String fingerprint) {
    rejeitarBucketNaChave(chave);
    return new Resultado(
        new Origem(TipoOrigem.OBJECT_STORAGE, area, chave),
        true,
        true,
        extensao(chave),
        fingerprint);
  }

  private Resultado invalido(
      StorageArea area,
      String fingerprint,
      boolean referenciaPersistida) {
    StorageArea areaSegura = area == null ? StorageArea.PRIVATE_MEDIA : area;
    Origem origem = new Origem(
        TipoOrigem.OBJECT_STORAGE,
        areaSegura,
        "quarentena/referencia-invalida/" + fingerprint);
    return new Resultado(origem, false, referenciaPersistida, null, fingerprint);
  }

  private void rejeitarBucketNaChave(String chave) {
    for (String bucket : areasPorBucket.keySet()) {
      if (chave.equals(bucket) || chave.startsWith(bucket + "/")) {
        throw new IllegalArgumentException("bucket nao pode fazer parte da chave");
      }
    }
  }

  private static String chave(String valor) {
    ValidadorManifestoMidiaFaseCinco.validarChave(valor, "origem");
    return valor;
  }

  private static String decodificar(String valor) {
    try {
      return URLDecoder.decode(valor.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("encoding invalido na referencia legada", exception);
    }
  }

  private static boolean mesmaOrigem(URI esquerda, URI direita) {
    return esquerda.getScheme().equalsIgnoreCase(direita.getScheme())
        && esquerda.getHost().equalsIgnoreCase(direita.getHost())
        && porta(esquerda) == porta(direita);
  }

  private static int porta(URI uri) {
    if (uri.getPort() >= 0) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private static String normalizarBasePath(String path) {
    if (path == null || path.isBlank() || "/".equals(path)) {
      return "/";
    }
    return path.endsWith("/") ? path : path + "/";
  }

  private static String extensao(String chave) {
    int barra = chave.lastIndexOf('/');
    int ponto = chave.lastIndexOf('.');
    if (ponto <= barra || ponto == chave.length() - 1) {
      return null;
    }
    String extensao = chave.substring(ponto + 1).toLowerCase(Locale.ROOT);
    return extensao.matches("[a-z0-9]{2,5}") ? extensao : null;
  }

  private static void adicionarBucket(
      Map<String, Set<StorageArea>> buckets,
      String bucket,
      StorageArea area) {
    if (bucket == null || bucket.isBlank()) {
      return;
    }
    buckets.computeIfAbsent(bucket.trim(), ignored -> new LinkedHashSet<>()).add(area);
  }

  private static void adicionarBase(List<URI> bases, String valor) {
    if (valor == null || valor.isBlank()) {
      return;
    }
    URI base = URI.create(valor.trim());
    if (!"https".equalsIgnoreCase(base.getScheme())
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null) {
      throw new IllegalArgumentException("origem publica legada deve ser HTTPS");
    }
    bases.add(base);
  }

  record Resultado(
      Origem origem,
      boolean valida,
      boolean referenciaPersistida,
      String extensao,
      String referenciaFingerprint) {

    @Override
    public String toString() {
      return "Resultado[origem=<sanitizada>, valida=" + valida
          + ", referenciaPersistida=" + referenciaPersistida + "]";
    }
  }
}
