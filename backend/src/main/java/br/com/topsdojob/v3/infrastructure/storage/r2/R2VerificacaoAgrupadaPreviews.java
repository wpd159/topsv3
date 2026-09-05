package br.com.topsdojob.v3.infrastructure.storage.r2;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Read-only, operation-local proofs for the canonical restricted-preview namespace. */
public class R2VerificacaoAgrupadaPreviews {

  private static final String DIRECTORY = "restritas-borradas/v1/";
  private final R2StorageProperties properties;
  private final HttpClient suppliedHttpClient;
  private volatile R2SigV4Client client;

  public R2VerificacaoAgrupadaPreviews(R2StorageProperties properties) {
    this(properties, null);
  }

  /** Explicit transport seam; configuration validation is identical for both constructors. */
  public R2VerificacaoAgrupadaPreviews(R2StorageProperties properties, HttpClient httpClient) {
    this.properties = properties;
    this.suppliedHttpClient = httpClient;
  }

  public Set<String> verificar(Set<String> chaves) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new R2StorageException("Listagem de previews exige transacao encerrada");
    }
    if (chaves == null || chaves.size() > 4096) {
      throw new R2StorageException("Conjunto de previews fora do limite");
    }
    if (chaves.isEmpty()) return Set.of();
    if (properties == null || !properties.isEnabled()) {
      throw new R2StorageException("Configuracao publica R2 indisponivel");
    }
    try {
      properties.validateConfigured();
    } catch (RuntimeException exception) {
      throw new R2StorageException("Configuracao publica R2 indisponivel", exception);
    }
    String prefix = properties.getPublicMediaPrefix();
    if (!properties.isEnabled() || prefix == null || !prefix.startsWith("hml/")
        || !prefix.endsWith("/") || prefix.contains("..")) {
      throw new R2StorageException("Configuracao publica R2 indisponivel");
    }
    String namespace = prefix + DIRECTORY;
    Set<String> requested = new LinkedHashSet<>();
    for (String key : chaves) {
      if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")
          || key.contains("\\") || key.contains("?") || key.contains("#")
          || !key.startsWith(namespace) || key.length() == namespace.length()) {
        throw new R2StorageException("Chave fora do namespace de previews autorizado");
      }
      requested.add(key);
    }
    return client().listarExistentes(properties.getPublicMediaBucket(), namespace, requested);
  }

  private R2SigV4Client client() {
    R2SigV4Client current = client;
    if (current != null) return current;
    synchronized (this) {
      if (client == null) {
        require(properties.getPublicMediaBucket());
        require(properties.getEndpoint());
        require(properties.getAccessKey());
        require(properties.getSigningValue());
        URI endpoint;
        try {
          endpoint = URI.create(properties.getEndpoint());
        } catch (IllegalArgumentException exception) {
          throw new R2StorageException("Endpoint R2 invalido");
        }
        if (endpoint.getHost() == null
            || endpoint.getUserInfo() != null || endpoint.getRawQuery() != null
            || endpoint.getRawFragment() != null
            || !(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))) {
          throw new R2StorageException("Endpoint R2 invalido");
        }
        HttpClient transport = suppliedHttpClient == null
            ? HttpClient.newBuilder().connectTimeout(Duration.ofMillis(3500))
                .followRedirects(HttpClient.Redirect.NEVER).build()
            : suppliedHttpClient;
        client = new R2SigV4Client(transport, endpoint, properties.getRegion(),
            properties.getAccessKey(), properties.getSigningValue());
      }
      return client;
    }
  }

  private static void require(String value) {
    if (value == null || value.isBlank()) {
      throw new R2StorageException("Configuracao publica R2 indisponivel");
    }
  }
}
