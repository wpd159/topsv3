package br.com.topsdojob.v3.infrastructure.storage;

import java.util.List;

public record StoredObjectPage(
    List<StoredObjectMetadata> objects,
    String nextContinuationToken,
    boolean truncated) {

  public StoredObjectPage {
    objects = objects == null ? List.of() : List.copyOf(objects);
  }
}
