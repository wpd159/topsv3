package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

final class FonteR2LegadaSomenteLeitura implements FonteMidiaMigracao {

  private final R2Operations operations;
  private final Map<StorageArea, String> buckets;

  FonteR2LegadaSomenteLeitura(R2Operations operations, Map<StorageArea, String> buckets) {
    this.operations = Objects.requireNonNull(operations, "operacoes R2 obrigatorias");
    EnumMap<StorageArea, String> configurados = new EnumMap<>(StorageArea.class);
    configurados.putAll(Objects.requireNonNull(buckets, "buckets obrigatorios"));
    if (configurados.size() != StorageArea.values().length
        || configurados.values().stream().anyMatch(
            bucket -> bucket == null || bucket.isBlank())) {
      throw new IllegalArgumentException("buckets da origem legada estao incompletos");
    }
    this.buckets = Map.copyOf(configurados);
  }

  @Override
  public StoredObject carregar(Origem origem) {
    if (origem == null
        || origem.tipo() != TipoOrigem.OBJECT_STORAGE
        || origem.area() == null) {
      throw new FonteMidiaMigracao.OrigemMidiaInvalidaException(
          "origem legada deve pertencer ao object storage");
    }
    String chave = origem.localizador();
    ValidadorPacoteMigracaoIntegralBridge.validar(chave);
    String bucket = buckets.get(origem.area());
    if (chave.equals(bucket) || chave.startsWith(bucket + "/")) {
      throw new FonteMidiaMigracao.OrigemMidiaInvalidaException(
          "bucket nao pode fazer parte da chave legada");
    }
    if (!operations.exists(bucket, chave)) {
      throw new FonteMidiaMigracao.ObjetoOrigemAusenteException();
    }
    return operations.get(bucket, chave);
  }

  /**
   * Mantem a politica sintatica junto ao adaptador R2 sem expor as operacoes mutaveis.
   */
  private static final class ValidadorPacoteMigracaoIntegralBridge {

    private ValidadorPacoteMigracaoIntegralBridge() {
    }

    private static void validar(String chave) {
      if (chave == null || chave.isBlank()
          || chave.startsWith("/")
          || chave.contains("://")
          || chave.contains("?")
          || chave.contains("#")
          || chave.contains("\\")
          || chave.indexOf('\0') >= 0
          || chave.chars().anyMatch(Character::isISOControl)) {
        throw new FonteMidiaMigracao.OrigemMidiaInvalidaException(
            "chave legada invalida");
      }
      for (String segmento : chave.split("/", -1)) {
        if (segmento.isBlank() || ".".equals(segmento) || "..".equals(segmento)) {
          throw new FonteMidiaMigracao.OrigemMidiaInvalidaException(
              "chave legada invalida");
        }
      }
    }
  }
}
