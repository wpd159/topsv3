package br.com.topsdojob.v3.infrastructure.payment.efi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "efi.pix", name = "enabled", havingValue = "true")
public class EfiPixHttpGateway implements EfiPixGateway {

    private static final String HOMOLOGACAO_URL = "https://pix-h.api.efipay.com.br";
    private static final String PRODUCAO_URL = "https://pix.api.efipay.com.br";
    private static final int MAX_RESPONSE_CHARS = 2_000_000;

    private final EfiPixProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile Token oauthState;

    public EfiPixHttpGateway(
            EfiPixProperties properties,
            ObjectMapper objectMapper,
            @Value("${app.env:nao_configurado}") String appEnv) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        validarConfiguracao(appEnv);
        this.httpClient = HttpClient.newBuilder()
                .sslContext(criarSslContext())
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public CobrancaPix criarCobranca(String txid, BigDecimal valor, String descricao) {
        validarTxid(txid);
        if (valor == null || valor.signum() <= 0) {
            throw new EfiPixGatewayException("valor da cobranca Efi invalido", false);
        }
        try {
            JsonNode payload = objectMapper.createObjectNode()
                    .set("calendario", objectMapper.createObjectNode()
                            .put("expiracao", properties.getChargeExpirationSeconds()));
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
                    .set("valor", objectMapper.createObjectNode().put("original", valor.setScale(2).toPlainString()));
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
                    .put("chave", properties.getPixKey());
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
                    .put("solicitacaoPagador", textoSeguro(descricao, 140));

            JsonNode cobranca = enviarAutorizado("PUT", "/v2/cob/" + txid, objectMapper.writeValueAsString(payload));
            return mapearCobranca(cobranca, true);
        } catch (EfiPixGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EfiPixGatewayException("falha ao criar cobranca Efi", false, exception);
        }
    }

    @Override
    public CobrancaPix consultarCobranca(String txid) {
        validarTxid(txid);
        JsonNode cobranca = enviarAutorizado("GET", "/v2/cob/" + txid, null);
        return mapearCobranca(cobranca, true);
    }

    private CobrancaPix mapearCobranca(JsonNode cobranca, boolean carregarQrCode) {
        String txid = textoObrigatorio(cobranca, "txid");
        String status = textoObrigatorio(cobranca, "status");
        String localizacaoId = cobranca.path("loc").path("id").asText(null);
        BigDecimal valorOriginal = decimal(cobranca.path("valor").path("original"));
        BigDecimal valorRecebido = BigDecimal.ZERO;
        if (cobranca.path("pix").isArray()) {
            for (JsonNode pix : cobranca.path("pix")) {
                valorRecebido = valorRecebido.add(decimal(pix.path("valor")));
            }
        }
        OffsetDateTime expiracao = expiracao(cobranca.path("calendario"));
        QrCode qrCode = carregarQrCode && localizacaoId != null
                ? carregarQrCode(localizacaoId)
                : new QrCode(null, null);
        return new CobrancaPix(
                txid,
                status,
                localizacaoId,
                valorOriginal,
                valorRecebido,
                expiracao,
                qrCode.copiaECola(),
                qrCode.imagem());
    }

    private QrCode carregarQrCode(String localizacaoId) {
        if (!localizacaoId.matches("[0-9]{1,20}")) {
            throw new EfiPixGatewayException("identificador de QR Code Efi invalido", false);
        }
        JsonNode qr = enviarAutorizado("GET", "/v2/loc/" + localizacaoId + "/qrcode", null);
        return new QrCode(qr.path("qrcode").asText(null), qr.path("imagemQrcode").asText(null));
    }

    private JsonNode enviarAutorizado(String method, String path, String body) {
        String accessToken = tokenValido();
        HttpResponse<String> response = enviar(method, path, body, "Bearer " + accessToken);
        if (response.statusCode() == 401) {
            oauthState = null;
            response = enviar(method, path, body, "Bearer " + tokenValido());
        }
        return respostaJson(response, path);
    }

    private String tokenValido() {
        Token atual = oauthState;
        if (atual != null && atual.valido()) {
            return atual.valor();
        }
        synchronized (this) {
            atual = oauthState;
            if (atual != null && atual.valido()) {
                return atual.valor();
            }
            String credencial = properties.getClientId() + ":" + properties.getClientSecret();
            String basic = Base64.getEncoder().encodeToString(credencial.getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> response = enviar(
                    "POST",
                    "/oauth/token",
                    "{\"grant_type\":\"client_credentials\"}",
                    "Basic " + basic);
            JsonNode body = respostaJson(response, "/oauth/token");
            String valor = textoObrigatorio(body, "access_token");
            long expiresIn = Math.max(60, body.path("expires_in").asLong(300));
            oauthState = new Token(valor, Instant.now().plusSeconds(Math.max(30, expiresIn - 30)));
            return valor;
        }
    }

    private HttpResponse<String> enviar(String method, String path, String body, String authorization) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Authorization", authorization);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EfiPixGatewayException("chamada Efi interrompida", false, exception);
        } catch (IOException exception) {
            throw new EfiPixGatewayException("falha de comunicacao com a Efi", false, exception);
        }
    }

    private JsonNode respostaJson(HttpResponse<String> response, String operation) {
        String body = response.body() == null ? "" : response.body();
        if (body.length() > MAX_RESPONSE_CHARS) {
            throw new EfiPixGatewayException("resposta Efi acima do limite", false);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new EfiPixGatewayException(
                    "operacao Efi rejeitada: " + operation + " (HTTP " + response.statusCode() + ")",
                    false);
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new EfiPixGatewayException("resposta Efi invalida", false, exception);
        }
    }

    private SSLContext criarSslContext() {
        char[] keyMaterial = properties.getCertificateProtection().toCharArray();
        try (InputStream input = Files.newInputStream(Path.of(properties.getCertificatePath()))) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, keyMaterial);
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, keyMaterial);
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init((KeyStore) null);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
            return sslContext;
        } catch (Exception exception) {
            throw new EfiPixGatewayException("certificado Efi indisponivel ou invalido", true, exception);
        } finally {
            java.util.Arrays.fill(keyMaterial, '\0');
        }
    }

    private void validarConfiguracao(String appEnv) {
        String ambiente = texto(properties.getEnvironment()).toLowerCase();
        String aplicacao = texto(appEnv).toLowerCase();
        String baseUrl = texto(properties.getBaseUrl());
        if ("producao".equals(aplicacao)) {
            if (!"producao".equals(ambiente) || !PRODUCAO_URL.equals(baseUrl)) {
                throw configuracaoInvalida();
            }
        } else if (!"homologacao".equals(ambiente) || !HOMOLOGACAO_URL.equals(baseUrl)) {
            throw configuracaoInvalida();
        }
        if (vazio(properties.getClientId())
                || vazio(properties.getClientSecret())
                || vazio(properties.getCertificatePath())
                || vazio(properties.getCertificateProtection())
                || vazio(properties.getPixKey())
                || vazio(properties.getWebhookBaseUrl())
                || vazio(properties.getWebhookVerifier())
                || !properties.getWebhookBaseUrl().startsWith("https://")
                || properties.getChargeExpirationSeconds() < 60
                || properties.getChargeExpirationSeconds() > 86400) {
            throw configuracaoInvalida();
        }
        Path mtlsFile = Path.of(properties.getCertificatePath());
        if (!Files.isRegularFile(mtlsFile)) {
            throw configuracaoInvalida();
        }
    }

    private EfiPixGatewayException configuracaoInvalida() {
        return new EfiPixGatewayException("configuracao Efi incompleta ou insegura", true);
    }

    private void validarTxid(String txid) {
        if (txid == null || !txid.matches("[A-Za-z0-9]{26,35}")) {
            throw new EfiPixGatewayException("txid Efi invalido", false);
        }
    }

    private String textoObrigatorio(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new EfiPixGatewayException("resposta Efi sem campo obrigatorio", false);
        }
        return value;
    }

    private BigDecimal decimal(JsonNode node) {
        String value = node.asText("0");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new EfiPixGatewayException("valor monetario invalido na resposta Efi", false, exception);
        }
    }

    private OffsetDateTime expiracao(JsonNode calendario) {
        String criacao = calendario.path("criacao").asText(null);
        long segundos = calendario.path("expiracao").asLong(properties.getChargeExpirationSeconds());
        if (criacao == null) {
            return OffsetDateTime.now().plusSeconds(segundos);
        }
        try {
            return OffsetDateTime.parse(criacao).plusSeconds(segundos);
        } catch (RuntimeException exception) {
            throw new EfiPixGatewayException("calendario invalido na resposta Efi", false, exception);
        }
    }

    private String textoSeguro(String value, int maxLength) {
        String normalized = texto(value).replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }

    private String texto(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean vazio(String value) {
        return texto(value).isEmpty();
    }

    private record Token(String valor, Instant expiraEm) {
        boolean valido() {
            return valor != null && !valor.isBlank() && expiraEm != null && expiraEm.isAfter(Instant.now());
        }
    }

    private record QrCode(String copiaECola, String imagem) {
    }
}
