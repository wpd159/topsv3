package br.com.topsdojob.v3.application.publico.compliance;

import org.springframework.stereotype.Component;

@Component
public class CpfVisitanteValidator {

  public boolean valido(String value) {
    String cpf = value == null ? "" : value.replaceAll("\\D", "");
    if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
      return false;
    }
    return digito(cpf, 9) == cpf.charAt(9) - '0'
        && digito(cpf, 10) == cpf.charAt(10) - '0';
  }

  private int digito(String cpf, int length) {
    int soma = 0;
    int peso = length + 1;
    for (int index = 0; index < length; index++) {
      soma += (cpf.charAt(index) - '0') * peso--;
    }
    int resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  }
}
