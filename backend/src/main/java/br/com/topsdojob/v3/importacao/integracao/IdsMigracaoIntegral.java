package br.com.topsdojob.v3.importacao.integracao;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.UUID;

final class IdsMigracaoIntegral {

  private IdsMigracaoIntegral() {
  }

  static UUID uuid(String... partes) {
    StringJoiner valor = new StringJoiner(":");
    for (String parte : partes) {
      valor.add(parte == null ? "" : parte);
    }
    return UUID.nameUUIDFromBytes(valor.toString().getBytes(StandardCharsets.UTF_8));
  }
}
