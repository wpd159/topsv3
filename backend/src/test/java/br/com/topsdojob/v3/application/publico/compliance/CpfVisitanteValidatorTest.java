package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfVisitanteValidatorTest {

  private final CpfVisitanteValidator validator = new CpfVisitanteValidator();

  @Test
  void validaDigitosVerificadoresComOuSemMascara() {
    String cpf = cpfSintetico();
    String masked = cpf.substring(0, 3)
        + "."
        + cpf.substring(3, 6)
        + "."
        + cpf.substring(6, 9)
        + "-"
        + cpf.substring(9);

    assertThat(validator.valido(cpf)).isTrue();
    assertThat(validator.valido(masked)).isTrue();
  }

  @Test
  void recusaAusenteRepetidoEComDigitoInvalido() {
    String cpf = cpfSintetico();
    String invalid = cpf.substring(0, 10) + (cpf.endsWith("0") ? "1" : "0");

    assertThat(validator.valido(null)).isFalse();
    assertThat(validator.valido("11111111111")).isFalse();
    assertThat(validator.valido(invalid)).isFalse();
  }

  static String cpfSintetico() {
    int[] digits = {7, 3, 1, 9, 4, 6, 5, 2, 0, 0, 0};
    digits[9] = digit(digits, 9);
    digits[10] = digit(digits, 10);
    StringBuilder value = new StringBuilder(11);
    for (int digit : digits) {
      value.append(digit);
    }
    return value.toString();
  }

  private static int digit(int[] digits, int length) {
    int sum = 0;
    int weight = length + 1;
    for (int index = 0; index < length; index++) {
      sum += digits[index] * weight--;
    }
    int remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }
}
