package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import java.net.URI;
import java.time.Duration;

interface R2Operations {

  void put(String bucket, String key, byte[] content, String contentType);

  ObjectWriteResult putIfAbsent(String bucket, String key, byte[] content, String contentType);

  boolean exists(String bucket, String key);

  StoredObject get(String bucket, String key);

  void delete(String bucket, String key);

  URI presignGet(String bucket, String key, Duration ttl);
}
