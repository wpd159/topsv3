package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiWebhookResultadoDto;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EfiWebhookService {

    private static final int MAX_PAYLOAD_CHARS = 65_536;
    private static final int MAX_EVENTOS = 100;

    private final EfiPixProperties properties;
    private final EfiPagamentoHashService hashService;
    private final EfiWebhookItemProcessor processor;
    private final ObjectMapper objectMapper;

    public EfiWebhookService(
            EfiPixProperties properties,
            EfiPagamentoHashService hashService,
            EfiWebhookItemProcessor processor,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.hashService = hashService;
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    public EfiWebhookResultadoDto receber(
            String hmac,
            String payload,
            String origemIp,
            String requestId) {
        String raw = payload == null ? "" : payload;
        if (raw.length() > MAX_PAYLOAD_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "webhook acima do limite");
        }
        String payloadHash = hashService.hash(raw);
        String ipHash = hashService.hash(origemIp);
        if (!properties.isEnabled()
                || !hashService.segredoConfere(properties.getWebhookVerifier(), hmac)) {
            processor.registrarInvalido("invalido-" + payloadHash.substring(0, 32), payloadHash, ipHash);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "origem do webhook nao validada");
        }

        List<Notificacao> notificacoes = notificacoes(raw, payloadHash);
        int processados = 0;
        int repetidos = 0;
        int ignorados = 0;
        boolean falhou = false;
        for (Notificacao notificacao : notificacoes) {
            EfiWebhookItemProcessor.Resultado resultado = processor.processar(
                    notificacao.eventoId(),
                    notificacao.txid(),
                    payloadHash,
                    ipHash,
                    requestId);
            switch (resultado) {
                case PROCESSADO -> processados++;
                case REPETIDO -> repetidos++;
                case IGNORADO -> ignorados++;
                case FALHA -> falhou = true;
            }
        }
        if (falhou) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "falha ao conciliar webhook Efi");
        }
        return new EfiWebhookResultadoDto(notificacoes.size(), processados, repetidos, ignorados);
    }

    private List<Notificacao> notificacoes(String raw, String payloadHash) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode pix = root.path("pix");
            if (pix.isMissingNode() || pix.isNull()) {
                return List.of();
            }
            if (!pix.isArray() || pix.size() > MAX_EVENTOS) {
                throw payloadInvalido();
            }
            List<Notificacao> result = new ArrayList<>();
            for (JsonNode item : pix) {
                String txid = item.path("txid").asText("").trim();
                if (!txid.matches("[A-Za-z0-9]{26,35}")) {
                    throw payloadInvalido();
                }
                String providerId = item.path("endToEndId").asText("").trim();
                String eventoId = providerId.matches("[A-Za-z0-9]{20,64}")
                        ? providerId
                        : txid + "-" + payloadHash.substring(0, 16);
                result.add(new Notificacao(eventoId, txid));
            }
            return result;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw payloadInvalido();
        }
    }

    private ResponseStatusException payloadInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload de webhook invalido");
    }

    private record Notificacao(String eventoId, String txid) {
    }
}
