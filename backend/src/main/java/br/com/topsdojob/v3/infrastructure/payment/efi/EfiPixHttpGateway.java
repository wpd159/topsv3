package br.com.topsdojob.v3.infrastructure.payment.efi;

import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.Map;
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
    private final AmbientePagamento ambiente;
    private final HttpClient httpClient;
    private volatile Token oauthState;

    public EfiPixHttpGateway(
            EfiPixProperties properties,
            ObjectMapper objectMapper,
            @Value("${app.env:nao_configurado}") String appEnv) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.ambiente = validarConfiguracao(appEnv);
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
    public AmbientePagamento ambiente() {
        return ambiente;
    }

    @Override
    public CobrancaPix consultarCobranca(String txid) {
        validarTxid(txid);
        JsonNode cobranca = enviarAutorizado("GET", "/v2/cob/" + txid, null);
        return mapearCobranca(cobranca, true);
    }

    @Override
    public void garantirWebhookConfigurado() {
        try {
            String path = "/v2/webhook/" + segmentoUrl(properties.getPixKey());
            String callbackUrl = webhookCallbackUrl();
            String payload = objectMapper.createObjectNode()
                    .put("webhookUrl", callbackUrl)
                    .toString();
            enviarAutorizado(
                    "PUT",
                    path,
                    payload,
                    properties.isWebhookSkipMtlsChecking()
                            ? Map.of("x-skip-mtls-checking", "true")
                            : Map.of());
            JsonNode configuracao = enviarAutorizado("GET", path, null);
            if (!callbackUrl.equals(configuracao.path("webhookUrl").asText(null))) {
                throw new EfiPixGatewayException("webhook Efi nao confirmado", true);
            }
        } catch (EfiPixGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EfiPixGatewayException("falha ao configurar webhook Efi", true, exception);
        }
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
                ambiente,
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
        String copiaECola = qr.path("qrcode").asText(null);
        String imagem = qr.path("imagemQrcode").asText(null);
        if (copiaECola == null
                || copiaECola.isBlank()
                || copiaECola.length() > 2_048
                || imagem == null
                || imagem.length() > 1_500_000
                || !imagem.startsWith("data:image/png;base64,")) {
            throw new EfiPixGatewayException("QR Code Efi invalido", false);
        }
        return new QrCode(copiaECola, imagem);
    }

    private JsonNode enviarAutorizado(String method, String path, String body) {
        return enviarAutorizado(method, path, body, Map.of());
    }

    private JsonNode enviarAutorizado(
            String method,
            String path,
            String body,
            Map<String, String> headers) {
        String accessToken = tokenValido();
        HttpResponse<String> response = enviar(method, path, body, "Bearer " + accessToken, headers);
        if (response.statusCode() == 401) {
            oauthState = null;
            response = enviar(method, path, body, "Bearer " + tokenValido(), headers);
        }
        return respostaJson(response, operacaoSegura(path));
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
                    "Basic " + basic,
                    Map.of());
            JsonNode body = respostaJson(response, "/oauth/token");
            String valor = textoObrigatorio(body, "access_token");
            long expiresIn = Math.max(60, body.path("expires_in").asLong(300));
            oauthState = new Token(valor, Instant.now().plusSeconds(Math.max(30, expiresIn - 30)));
            return valor;
        }
    }

    private HttpResponse<String> enviar(
            String method,
            String path,
            String body,
            String authorization,
            Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Authorization", authorization);
            headers.forEach(builder::header);
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
        String protection = properties.getCertificateProtection();
        char[] keyMaterial = protection == null ? new char[0] : protection.toCharArray();
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

    private AmbientePagamento validarConfiguracao(String appEnv) {
        String ambienteConfigurado = texto(properties.getEnvironment()).toLowerCase();
        String aplicacao = texto(appEnv).toLowerCase();
        String baseUrl = texto(properties.getBaseUrl());
        AmbientePagamento ambientePagamento;
        if ("producao".equals(aplicacao)) {
            if (!"producao".equals(ambienteConfigurado) || !PRODUCAO_URL.equals(baseUrl)) {
                throw configuracaoInvalida();
            }
            ambientePagamento = AmbientePagamento.PRODUCAO;
        } else if (!"homologacao".equals(ambienteConfigurado) || !HOMOLOGACAO_URL.equals(baseUrl)) {
            throw configuracaoInvalida();
        } else {
            ambientePagamento = AmbientePagamento.SANDBOX;
        }
        if (vazio(properties.getClientId())
                || vazio(properties.getClientSecret())
                || vazio(properties.getCertificatePath())
                || vazio(properties.getPixKey())
                || vazio(properties.getWebhookBaseUrl())
                || vazio(properties.getWebhookVerifier())
                || !webhookBaseUrlValida(properties.getWebhookBaseUrl())
                || properties.getWebhookVerifier().length() < 32
                || properties.getChargeExpirationSeconds() < 60
                || properties.getChargeExpirationSeconds() > 86400) {
            throw configuracaoInvalida();
        }
        Path mtlsFile = Path.of(properties.getCertificatePath());
        if (!Files.isRegularFile(mtlsFile)) {
            throw configuracaoInvalida();
        }
        return ambientePagamento;
    }

    private EfiPixGatewayException configuracaoInvalida() {
        return new EfiPixGatewayException("configuracao Efi incompleta ou insegura", true);
    }

    private void validarTxid(String txid) {
        if (txid == null || !txid.matches("[A-Za-z0-9]{26,35}")) {
            throw new EfiPixGatewayException("txid Efi invalido", false);
        }
    }

    String webhookCallbackUrl() {
        String base = texto(properties.getWebhookBaseUrl()).replaceAll("/+$", "");
        String verifier = URLEncoder.encode(properties.getWebhookVerifier(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return base + "/pix?hmac=" + verifier + "&ignorar=";
    }

    String operacaoSegura(String path) {
        String value = texto(path);
        if (value.startsWith("/v2/webhook/")) {
            return "/v2/webhook/{chave}";
        }
        if (value.startsWith("/v2/cob/")) {
            return "/v2/cob/{txid}";
        }
        if (value.startsWith("/v2/loc/")) {
            return "/v2/loc/{id}/qrcode";
        }
        return value;
    }

    private boolean webhookBaseUrlValida(String value) {
        try {
            URI uri = URI.create(texto(value).replaceAll("/+$", ""));
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && uri.getPath().endsWith("/api/public/webhooks/efi");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String segmentoUrl(String value) {
        return URLEncoder.encode(texto(value), StandardCharsets.UTF_8).replace("+", "%20");
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
