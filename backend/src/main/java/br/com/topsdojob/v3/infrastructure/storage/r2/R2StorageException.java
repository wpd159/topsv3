package br.com.topsdojob.v3.infrastructure.storage.r2;

public class R2StorageException extends RuntimeException {

  public R2StorageException(String message) {
    super(message);
  }

  public R2StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
