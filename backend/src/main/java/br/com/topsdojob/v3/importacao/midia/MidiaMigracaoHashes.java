package br.com.topsdojob.v3.importacao.midia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class MidiaMigracaoHashes {

  private MidiaMigracaoHashes() {
  }

  static String sha256(byte[] content) {
    return HexFormat.of().formatHex(digest().digest(content));
  }

  static String sha256(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  static String identificadorSeguro(String value) {
    return sha256(value).substring(0, 24);
  }

  static boolean sha256Valido(String value) {
    return value != null && value.matches("[a-f0-9]{64}");
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }
}
