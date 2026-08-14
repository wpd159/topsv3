package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FingerprintMigracaoIntegralTest {

  private final FingerprintMigracaoIntegral fingerprint =
      new FingerprintMigracaoIntegral(new ObjectMapper().findAndRegisterModules());

  @Test
  void ignoraOrdemFisicaEIdentidadeTecnicaDaExecucao() {
    Map<String, List<Map<String, Object>>> primeira = fotografia(
        "execucao-a", "mapeamento-a", "2026-08-01T10:00:00Z", "APROVADO");
    Map<String, List<Map<String, Object>>> segunda = fotografia(
        "execucao-b", "mapeamento-b", "2026-08-02T10:00:00Z", "APROVADO");
    List<Map<String, Object>> pagamentos = segunda.get("pagamento");
    segunda.put("pagamento", List.of(pagamentos.get(1), pagamentos.get(0)));

    assertThat(fingerprint.calcular(primeira)).isEqualTo(fingerprint.calcular(segunda));
  }

  @Test
  void mudancaDeNegocioMudaFingerprint() {
    Map<String, List<Map<String, Object>>> primeira = fotografia(
        "execucao-a", "mapeamento-a", "2026-08-01T10:00:00Z", "APROVADO");
    Map<String, List<Map<String, Object>>> alterada = fotografia(
        "execucao-b", "mapeamento-b", "2026-08-02T10:00:00Z", "CANCELADO");

    assertThat(fingerprint.calcular(primeira)).isNotEqualTo(fingerprint.calcular(alterada));
  }

  @Test
  void ignoraTimestampTecnicoDeSeedMasPreservaValorComercial() {
    Map<String, List<Map<String, Object>>> primeira = Map.of(
        "plano_credito",
        List.of(Map.of(
            "id", "plano-estavel",
            "codigo", "PACOTE_QA",
            "valor", 10,
            "criado_em", OffsetDateTime.parse("2026-08-01T10:00:00Z"),
            "atualizado_em", OffsetDateTime.parse("2026-08-01T10:00:00Z"))));
    Map<String, List<Map<String, Object>>> somenteTimestamp = Map.of(
        "plano_credito",
        List.of(Map.of(
            "id", "plano-estavel",
            "codigo", "PACOTE_QA",
            "valor", 10,
            "criado_em", OffsetDateTime.parse("2026-08-02T10:00:00Z"),
            "atualizado_em", OffsetDateTime.parse("2026-08-02T10:00:00Z"))));
    Map<String, List<Map<String, Object>>> valorAlterado = Map.of(
        "plano_credito",
        List.of(Map.of(
            "id", "plano-estavel",
            "codigo", "PACOTE_QA",
            "valor", 20,
            "criado_em", OffsetDateTime.parse("2026-08-02T10:00:00Z"),
            "atualizado_em", OffsetDateTime.parse("2026-08-02T10:00:00Z"))));

    assertThat(fingerprint.calcular(primeira)).isEqualTo(fingerprint.calcular(somenteTimestamp));
    assertThat(fingerprint.calcular(primeira)).isNotEqualTo(fingerprint.calcular(valorAlterado));
  }

  private static Map<String, List<Map<String, Object>>> fotografia(
      String execucao,
      String mapeamento,
      String criadoEm,
      String statusPagamento) {
    Map<String, List<Map<String, Object>>> fotografia = new LinkedHashMap<>();
    fotografia.put("importacao_mapeamento", List.of(Map.of(
        "id", UUID.nameUUIDFromBytes(mapeamento.getBytes()),
        "execucao_id", UUID.nameUUIDFromBytes(execucao.getBytes()),
        "criado_em", OffsetDateTime.parse(criadoEm),
        "tabela_origem", "pagamentos",
        "id_origem", "pagamento-1",
        "status", "MAPEADO")));
    fotografia.put("pagamento", List.of(
        Map.of("id", "pagamento-2", "estado", "EXPIRADO", "valor", 20),
        Map.of("id", "pagamento-1", "estado", statusPagamento, "valor", 10)));
    return fotografia;
  }
}
