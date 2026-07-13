package br.com.topsdojob.v3.infrastructure.storage;

public record StoredObject(byte[] content, String contentType) {

  public StoredObject {
    content = content.clone();
    contentType = contentType == null || contentType.isBlank()
        ? "application/octet-stream"
        : contentType;
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
