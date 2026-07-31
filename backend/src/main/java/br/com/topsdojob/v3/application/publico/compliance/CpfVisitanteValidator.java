package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.domain.usuario.CpfValidator;
import org.springframework.stereotype.Component;

@Component
public class CpfVisitanteValidator {

  public boolean valido(String value) {
    return CpfValidator.isValid(value);
  }
}
