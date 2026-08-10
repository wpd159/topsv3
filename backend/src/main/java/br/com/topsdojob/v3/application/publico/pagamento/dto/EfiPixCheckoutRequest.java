package br.com.topsdojob.v3.application.publico.pagamento.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.UUID;

@JsonDeserialize(using = EfiPixCheckoutRequest.Deserializer.class)
public record EfiPixCheckoutRequest(UUID planoCreditoId) {

  public static final class Deserializer extends StdDeserializer<EfiPixCheckoutRequest> {

    public Deserializer() {
      super(EfiPixCheckoutRequest.class);
    }

    @Override
    public EfiPixCheckoutRequest deserialize(
        JsonParser parser,
        DeserializationContext context) throws IOException {
      JsonNode payload = parser.getCodec().readTree(parser);
      if (!payload.isObject()
          || payload.size() != 1
          || !payload.has("planoCreditoId")
          || !payload.get("planoCreditoId").isTextual()) {
        return context.reportInputMismatch(
            EfiPixCheckoutRequest.class,
            "checkout Pix aceita somente planoCreditoId");
      }
      try {
        return new EfiPixCheckoutRequest(UUID.fromString(payload.get("planoCreditoId").textValue()));
      } catch (IllegalArgumentException exception) {
        return context.reportInputMismatch(
            EfiPixCheckoutRequest.class,
            "planoCreditoId invalido");
      }
    }
  }
}
