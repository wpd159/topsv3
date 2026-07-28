package br.com.topsdojob.v3.application.admin.usuario;

import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioErroCampoDto;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class AdminUsuarioAtualizacaoException extends RuntimeException {

  private final HttpStatus status;
  private final List<AdminUsuarioErroCampoDto> erros;

  public AdminUsuarioAtualizacaoException(
      HttpStatus status,
      String mensagem,
      AdminUsuarioErroCampoDto erro) {
    super(mensagem);
    this.status = status;
    this.erros = List.of(erro);
  }

  public HttpStatus status() {
    return status;
  }

  public List<AdminUsuarioErroCampoDto> erros() {
    return erros;
  }
}
