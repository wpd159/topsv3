package br.com.topsdojob.v3.application.admin.outbox.template;

import br.com.topsdojob.v3.application.admin.readonly.AdminTextoSanitizer;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class AdminOutboxTemplateSanitizer {

    private static final int VALUE_MAX_LENGTH = 2_000;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+" + "@" + "[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern CONTATO = Pattern.compile("\\+?[0-9][0-9 .()\\-]{7,}[0-9]");
    private static final Pattern DOCUMENTO = Pattern.compile("\\b[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}\\b|\\b[0-9]{11}\\b");

    private AdminOutboxTemplateSanitizer() {
    }

    static AdminOutboxTemplateValores valores(OutboxEventoEntity entity, Map<String, Object> dados) {
        String bruto = entity.getPayloadJson() == null ? "" : entity.getPayloadJson();
        Set<String> campos = detectarCamposMascarados(bruto);
        List<String> pendencias = new ArrayList<>();
        pendencias.add("entrega externa depende do processador de outbox configurado no ambiente");

        String motivo = safeFromDados(dados, "motivoSanitizado", "[motivo]");
        if ("[motivo]".equals(motivo)) {
            pendencias.add("motivo sanitizado indisponivel no payload allowlist");
        }
        String acao = acaoNecessaria(entity.getTipoEvento());
        return new AdminOutboxTemplateValores(
                "[anunciante]",
                safeFromDados(dados, "anuncioTitulo", "[anuncio]"),
                motivo,
                acao,
                "[suporte]",
                "[link_painel_futuro]",
                linkSeguro(dados),
                List.copyOf(campos),
                List.copyOf(pendencias));
    }

    static String textoSeguro(String value, int maxLength) {
        String withoutHtml = HTML_TAG.matcher(value == null ? "" : value).replaceAll(" ");
        String withoutUrl = URL.matcher(withoutHtml).replaceAll("[link-removido]");
        String sanitized = AdminTextoSanitizer.resumo(withoutUrl, maxLength);
        if (sanitized == null || sanitized.isBlank()) {
            return null;
        }
        return sanitized;
    }

    private static String safeFromDados(Map<String, Object> dados, String key, String fallback) {
        if (dados == null || !dados.containsKey(key) || dados.get(key) == null) {
            return fallback;
        }
        String value = textoSeguro(String.valueOf(dados.get(key)), VALUE_MAX_LENGTH);
        return value == null ? fallback : value;
    }

    private static String acaoNecessaria(String tipoEvento) {
        return switch (tipoEvento == null ? "" : tipoEvento) {
            case "MODERACAO_SOLICITAR_AJUSTE" -> "revisar os pontos solicitados antes de nova analise";
            case "MODERACAO_REPROVADA" -> "Realize as correcoes e reenvie o anuncio para analise";
            case "ANUNCIO_REMETIDO_REVISAO" -> "acompanhar a revisao no painel futuro";
            case "MODERACAO_MIDIA_REPROVADA" -> "substituir a midia em fase futura aprovada";
            default -> "aguardar tratamento operacional futuro";
        };
    }

    private static String linkSeguro(Map<String, Object> dados) {
        if (dados == null || dados.get("linkEdicao") == null) {
            return "[link-indisponivel]";
        }
        try {
            URI uri = URI.create(String.valueOf(dados.get("linkEdicao")));
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !uri.getPath().matches("/meus-anuncios/[a-z0-9][a-z0-9-]{1,120}/editar")) {
                return "[link-indisponivel]";
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return "[link-indisponivel]";
        }
    }

    private static Set<String> detectarCamposMascarados(String raw) {
        String value = raw == null ? "" : raw;
        String lower = value.toLowerCase(Locale.ROOT);
        Set<String> campos = new LinkedHashSet<>();
        if (EMAIL.matcher(value).find() || lower.contains("email")) {
            campos.add("email");
        }
        if (CONTATO.matcher(value).find() || lower.contains("telefone") || lower.contains("whatsapp")) {
            campos.add("telefone_ou_whatsapp");
        }
        if (DOCUMENTO.matcher(value).find() || lower.contains("cpf") || lower.contains("documento")) {
            campos.add("cpf_ou_documento");
        }
        if (lower.contains("storage") || lower.contains("bucket") || lower.contains("chave") || lower.contains("hash")
                || lower.contains("etag")) {
            campos.add("storage_bucket_hash");
        }
        if (lower.contains("token") || lower.contains("senha") || lower.contains("password")
                || lower.contains("secret") || lower.contains("authorization") || lower.contains("credencial")) {
            campos.add("segredo");
        }
        if (lower.contains("pix") || lower.contains("pagamento") || lower.contains("valor")
                || lower.contains("credito") || lower.contains("qrcode") || lower.contains("txid")) {
            campos.add("pix_financeiro");
        }
        return campos;
    }
}

record AdminOutboxTemplateValores(
        String anunciante,
        String anuncio,
        String motivo,
        String acaoNecessaria,
        String suporte,
        String linkPainelFuturo,
        String linkEdicao,
        List<String> camposMascarados,
        List<String> pendencias) {
}
