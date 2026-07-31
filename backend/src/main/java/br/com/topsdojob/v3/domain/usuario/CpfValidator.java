package br.com.topsdojob.v3.domain.usuario;

public final class CpfValidator {

  private CpfValidator() {
  }

  public static boolean isValid(String value) {
    String cpf = value == null ? "" : value.replaceAll("\\D", "");
    if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
      return false;
    }
    return digit(cpf, 9) == cpf.charAt(9) - '0'
        && digit(cpf, 10) == cpf.charAt(10) - '0';
  }

  private static int digit(String cpf, int length) {
    int sum = 0;
    int weight = length + 1;
    for (int index = 0; index < length; index++) {
      sum += (cpf.charAt(index) - '0') * weight--;
    }
    int remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }
}
