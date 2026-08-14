package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ArquivoManifestoMidiaFaseCinco(
    String schemaVersion,
    String origemId,
    String snapshotSha256,
    String execucaoId,
    OffsetDateTime geradoEm,
    long quantidadeEntradas,
    Map<String, Long> contagens,
    String fingerprintDadosOrigem,
    String manifestoSha256,
    String fingerprint,
    ManifestoMidiaFaseCinco manifesto) {

  public static final String SCHEMA_VERSION = "topsdojob.migracao.midia/v1";

  public ArquivoManifestoMidiaFaseCinco {
    schemaVersion = obrigatorio(schemaVersion, "schemaVersion");
    origemId = obrigatorio(origemId, "origemId");
    snapshotSha256 = hash(snapshotSha256, "snapshotSha256");
    execucaoId = obrigatorio(execucaoId, "execucaoId");
    if (geradoEm == null) {
      throw new IllegalArgumentException("geradoEm deve ser informado");
    }
    if (quantidadeEntradas < 0) {
      throw new IllegalArgumentException("quantidadeEntradas nao pode ser negativa");
    }
    contagens = Map.copyOf(contagens == null ? Map.of() : contagens);
    fingerprintDadosOrigem = hash(fingerprintDadosOrigem, "fingerprintDadosOrigem");
    manifestoSha256 = hash(manifestoSha256, "manifestoSha256");
    fingerprint = hash(fingerprint, "fingerprint");
    if (manifesto == null) {
      throw new IllegalArgumentException("manifesto deve ser informado");
    }
  }

  public static ArquivoManifestoMidiaFaseCinco criar(
      String origemId,
      String snapshotSha256,
      String execucaoId,
      OffsetDateTime geradoEm,
      String fingerprintDadosOrigem,
      ManifestoMidiaFaseCinco manifesto,
      ObjectMapper mapper) {
    Map<String, Long> contagens = contagens(manifesto);
    String manifestoSha256 = manifesto.sha256();
    String fingerprint = fingerprint(
        mapper,
        SCHEMA_VERSION,
        origemId,
        snapshotSha256,
        manifesto.itens().size(),
        contagens,
        fingerprintDadosOrigem,
        manifestoSha256,
        manifesto);
    return new ArquivoManifestoMidiaFaseCinco(
        SCHEMA_VERSION,
        origemId,
        snapshotSha256,
        execucaoId,
        geradoEm,
        manifesto.itens().size(),
        contagens,
        fingerprintDadosOrigem,
        manifestoSha256,
        fingerprint,
        manifesto);
  }

  static String fingerprint(ObjectMapper mapper, ArquivoManifestoMidiaFaseCinco arquivo) {
    return fingerprint(
        mapper,
        arquivo.schemaVersion(),
        arquivo.origemId(),
        arquivo.snapshotSha256(),
        arquivo.quantidadeEntradas(),
        arquivo.contagens(),
        arquivo.fingerprintDadosOrigem(),
        arquivo.manifestoSha256(),
        arquivo.manifesto());
  }

  static Map<String, Long> contagens(ManifestoMidiaFaseCinco manifesto) {
    Map<String, Long> valores = new LinkedHashMap<>();
    manifesto.itens().stream()
        .collect(java.util.stream.Collectors.groupingBy(
            item -> "entidade." + item.entidadeTipo().name(),
            java.util.TreeMap::new,
            java.util.stream.Collectors.counting()))
        .forEach(valores::put);
    manifesto.itens().stream()
        .collect(java.util.stream.Collectors.groupingBy(
            item -> "decisao." + item.decisao().name(),
            java.util.TreeMap::new,
            java.util.stream.Collectors.counting()))
        .forEach(valores::put);
    return Map.copyOf(valores);
  }

  private static String fingerprint(
      ObjectMapper mapper,
      String schemaVersion,
      String origemId,
      String snapshotSha256,
      long quantidadeEntradas,
      Map<String, Long> contagens,
      String fingerprintDadosOrigem,
      String manifestoSha256,
      ManifestoMidiaFaseCinco manifesto) {
    Map<String, Object> canonico = new LinkedHashMap<>();
    canonico.put("schemaVersion", schemaVersion);
    canonico.put("origemId", origemId);
    canonico.put("snapshotSha256", snapshotSha256);
    canonico.put("quantidadeEntradas", quantidadeEntradas);
    canonico.put("contagens", contagens);
    canonico.put("fingerprintDadosOrigem", fingerprintDadosOrigem);
    canonico.put("manifestoSha256", manifestoSha256);
    canonico.put("manifesto", manifesto);
    try {
      ObjectMapper ordenado = mapper.copy()
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
      return FingerprintMigracaoIntegral.sha256(ordenado.writeValueAsBytes(canonico));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao calcular fingerprint do manifesto", exception);
    }
  }

  private static String hash(String valor, String campo) {
    String normalizado = obrigatorio(valor, campo).toLowerCase(Locale.ROOT);
    if (!normalizado.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(campo + " deve ser SHA-256 hexadecimal");
    }
    return normalizado;
  }

  private static String obrigatorio(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return valor.trim();
  }
}
