package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.util.Map;
import java.util.Objects;

public record ArmazenamentosMigracaoIntegral(
    FonteMidiaMigracao fonte,
    ObjectStorage destino,
    Map<StorageArea, String> bucketsDestino) {

  public ArmazenamentosMigracaoIntegral {
    fonte = Objects.requireNonNull(fonte, "fonte de midia obrigatoria");
    destino = Objects.requireNonNull(destino, "destino de midia obrigatorio");
    bucketsDestino = Map.copyOf(bucketsDestino == null ? Map.of() : bucketsDestino);
  }

  public String bucketDestino(StorageArea area) {
    String bucket = bucketsDestino.get(area);
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("bucket de destino nao configurado para " + area);
    }
    return bucket;
  }
}
