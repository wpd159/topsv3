package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminOutboxPreviewDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdminOutboxSanitizer {

    private static final int VALUE_MAX_LENGTH = 180;
    private static final int BODY_MAX_LENGTH = 360;
    private static final Set<String> SAFE_KEYS = Set.of(
            "anuncioId",
            "revisaoId",
            "decisao",
            "statusAnuncio",
            "statusModeracao",
            "motivoSanitizado",
            "anuncioTitulo",
            "linkEdicao",
            "destinatarioUsuarioId",
            "destinatarioLogico",
            "hardDeleteExecutado",
            "envioExternoPendente");
    private static final Set<String> BLOCKED_KEY_FRAGMENTS = Set.of(
            "email",
            "telefone",
            "whatsapp",
            "cpf",
            "documento",
            "storage",
            "bucket",
            "chave",
            "hash",
            "etag",
            "token",
            "senha",
            "password",
            "secret",
            "pagamento",
            "pix",
            "valor",
            "credito");

    private AdminOutboxSanitizer() {
    }

    public static Map<String, Object> dadosSanitizados(String dadosJson, ObjectMapper objectMapper) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (dadosJson == null || dadosJson.isBlank()) {
            result.put("conteudoDisponivel", false);
            return result;
        }
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(dadosJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            result.put("conteudoDisponivel", false);
            result.put("motivo", "conteudo local nao parseavel");
            return result;
        }
        for (String key : SAFE_KEYS) {
            if (parsed.containsKey(key) && !blockedKey(key)) {
                result.put(key, safeValue(key, parsed.get(key)));
            }
        }
        result.put("conteudoDisponivel", !result.isEmpty());
        result.put("jsonBrutoExposto", false);
        return result;
    }

    public static String resumo(OutboxEventoEntity entity) {
        String base = String.join(
                " ",
                safePart(entity.getTipoEvento()),
                safePart(entity.getStatus() == null ? null : entity.getStatus().name()),
                "para",
                safePart(entity.getAggregateTipo()));
        return AdminTextoSanitizer.resumo(base, 140);
    }

    public static AdminOutboxPreviewDto previa(OutboxEventoEntity entity, Map<String, Object> dados) {
        String tipoEvento = entity.getTipoEvento();
        String assunto = switch (tipoEvento == null ? "" : tipoEvento) {
            case "MODERACAO_SOLICITAR_AJUSTE" -> "Solicitacao local de ajuste pendente";
            case "MODERACAO_REPROVADA" -> "Comunicacao local de reprovacao pendente";
            case "ANUNCIO_REMETIDO_REVISAO" -> "Comunicacao local de remeter revisao pendente";
            default -> "Evento local de outbox pendente";
        };
        String destino = switch (tipoEvento == null ? "" : tipoEvento) {
            case "ANUNCIO_REMETIDO_REVISAO" -> "EQUIPE_MODERACAO_LOCAL";
            case "MODERACAO_SOLICITAR_AJUSTE", "MODERACAO_REPROVADA" -> "ANUNCIANTE_VINCULADA_AO_ANUNCIO";
            default -> "DESTINATARIO_LOGICO_LOCAL";
        };
        String motivo = dados.get("motivoSanitizado") == null ? "" : " Motivo: " + dados.get("motivoSanitizado");
        String corpo = "Previa local sanitizada do evento "
                + safePart(tipoEvento)
                + " para "
                + safePart(entity.getAggregateTipo())
                + ". Nenhuma comunicacao real foi enviada."
                + motivo;
        return new AdminOutboxPreviewDto(
                "COMUNICACAO_ADMIN_LOCAL",
                destino,
                AdminTextoSanitizer.resumo(assunto, VALUE_MAX_LENGTH),
                AdminTextoSanitizer.resumo(corpo, BODY_MAX_LENGTH),
                false);
    }

    public static boolean eventoModeracao(String tipoEvento) {
        return tipoEvento != null && (
                tipoEvento.equals("MODERACAO_SOLICITAR_AJUSTE")
                        || tipoEvento.equals("MODERACAO_REPROVADA")
                        || tipoEvento.equals("ANUNCIO_REMETIDO_REVISAO")
                        || tipoEvento.equals("MODERACAO_MIDIA_REPROVADA"));
    }

    private static Object safeValue(String key, Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return value;
        }
        int maxLength = "motivoSanitizado".equals(key) ? 2_000 : VALUE_MAX_LENGTH;
        return AdminTextoSanitizer.resumo(String.valueOf(value), maxLength);
    }

    private static boolean blockedKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return BLOCKED_KEY_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    private static String safePart(String value) {
        String sanitized = AdminTextoSanitizer.resumo(value, VALUE_MAX_LENGTH);
        return sanitized == null ? "NAO_INFORMADO" : sanitized;
    }
}
