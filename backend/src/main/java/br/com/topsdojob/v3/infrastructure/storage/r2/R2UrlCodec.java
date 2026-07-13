package br.com.topsdojob.v3.infrastructure.storage.r2;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

final class R2UrlCodec {

  private R2UrlCodec() {
  }

  static String encodePath(String value) {
    return Arrays.stream(value.split("/", -1))
        .map(R2UrlCodec::encodeQueryValue)
        .collect(Collectors.joining("/"));
  }

  static String encodeQueryValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("%7E", "~");
  }
}
