package br.com.topsdojob.v3.infrastructure.storage;

/** Read-only object inventory exposed to operational reconciliation jobs. */
@FunctionalInterface
public interface ObjectStorageInventory {

  StoredObjectPage list(
      StorageArea area,
      String prefix,
      String continuationToken,
      int maxKeys);
}
