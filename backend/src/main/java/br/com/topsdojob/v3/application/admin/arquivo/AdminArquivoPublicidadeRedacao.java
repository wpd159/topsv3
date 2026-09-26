package br.com.topsdojob.v3.application.admin.arquivo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Remove apenas contatos, endereco e rastros financeiros estruturados da vista operacional. */
final class AdminArquivoPublicidadeRedacao {
  private AdminArquivoPublicidadeRedacao() {
  }

  static JsonNode conteudo(JsonNode source) {
    if (!(source instanceof ObjectNode node)) return source;
    ObjectNode copia = node.deepCopy();
    copia.remove(List.of("whatsapp_normalizado", "link_conteudo"));
    if (copia.get("localizacao") instanceof ObjectNode localizacao) {
      localizacao.remove("endereco_resumido");
    }
    return copia;
  }

  static JsonNode comercial(JsonNode source) {
    if (!(source instanceof ObjectNode node)) return source;
    ObjectNode copia = node.deepCopy();
    copia.remove(List.of("movimentoCreditoId", "pagamentoId"));
    return copia;
  }
}
