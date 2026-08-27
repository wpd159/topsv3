package br.com.topsdojob.v3.infrastructure.storage;

import java.time.Instant;

public record StoredObjectMetadata(
    String key,
    long size,
    String etag,
    Instant lastModified) {
}
