package br.com.topsdojob.v3.application.publico.kyc;

import org.springframework.http.HttpStatus;

public class KycPublicoException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public KycPublicoException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
