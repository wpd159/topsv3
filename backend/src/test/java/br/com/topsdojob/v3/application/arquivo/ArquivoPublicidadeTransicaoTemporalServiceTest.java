package br.com.topsdojob.v3.application.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class ArquivoPublicidadeTransicaoTemporalServiceTest {
  private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-09-25T12:00:00Z");
  private static final UUID FONTE = UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final UUID FOTO_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID FOTO_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID FOTO_4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID FOTO_5 = UUID.fromString("00000000-0000-0000-0000-000000000005");
  private static final UUID VIDEO = UUID.fromString("00000000-0000-0000-0000-000000000006");

  private final ObjectMapper mapper = new ObjectMapper();
  private final ArquivoPublicidadeTransicaoTemporalService service =
      new ArquivoPublicidadeTransicaoTemporalService(
          mock(NamedParameterJdbcTemplate.class), mapper,
          mock(BeneficioAnuncioConsultaService.class), mock(PlatformTransactionManager.class),
          Clock.fixed(BASE.toInstant(), ZoneOffset.UTC));

  @Test
  void posicaoPublicavelInvalidaConsomeVagaEJamaisPromoveQuintaFoto() throws Exception {
    // Position 2 was PUBLICAVEL but had an invalid file, so it is absent from
    // the captured archive while still consuming one of the four public slots.
    String capturado = mapper.writeValueAsString(Map.of(
        "estado", "CAPTURADO_NA_EXIBICAO", "midias", List.of(
            foto(FOTO_1), foto(FOTO_3), foto(FOTO_4), foto(FOTO_5), video())));
    Map<UUID, Integer> posicoes = Map.of(FOTO_1, 1, FOTO_3, 3, FOTO_4, 4, FOTO_5, 5);
    OffsetDateTime extraFim = BASE.plusHours(1);
    OffsetDateTime videoFim = BASE.plusHours(2);
    List<ArquivoPublicidadeTransicaoTemporalService.Direito> direitos = List.of(
        direito(PremiumBeneficioCodigo.FOTOS_EXTRA_5, extraFim),
        direito(PremiumBeneficioCodigo.VIDEO_1, videoFim));

    var aposExtra = service.projetar(capturado, direitos, extraFim, posicoes, FONTE);
    assertThat(aposExtra.midias()).extracting(m -> m.get("anuncioMidiaId"))
        .containsExactly(FOTO_1.toString(), FOTO_3.toString(), FOTO_4.toString(), VIDEO.toString());
    var conteudo = mapper.readTree(aposExtra.conteudoJson());
    assertThat(conteudo.path("estado").asText()).isEqualTo("DERIVADO_DE_EXPIRACAO_TEMPORAL");
    assertThat(conteudo.path("derivacaoTemporal").path("evidenciaOrigemVersaoId").asText())
        .isEqualTo(FONTE.toString());
    assertThat(conteudo.path("derivacaoTemporal").path("fronteiraEm").asText())
        .isEqualTo(extraFim.toString());

    var aposVideo = service.projetar(capturado, direitos, videoFim, posicoes, FONTE);
    assertThat(aposVideo.midias()).extracting(m -> m.get("anuncioMidiaId"))
        .containsExactly(FOTO_1.toString(), FOTO_3.toString(), FOTO_4.toString());
  }

  @Test
  void beneficiosSobrepostosNaoRemovemMidiaAntesDoUltimoFim() throws Exception {
    String capturado = mapper.writeValueAsString(Map.of(
        "proveniencia", "CAPTURADO_NA_EXIBICAO_PROSPECTIVA",
        "midias", List.of(foto(FOTO_1), foto(FOTO_5))));
    OffsetDateTime primeiroFim = BASE.plusHours(1);
    OffsetDateTime ultimoFim = BASE.plusHours(2);
    List<ArquivoPublicidadeTransicaoTemporalService.Direito> direitos = List.of(
        direito(PremiumBeneficioCodigo.FOTOS_EXTRA_5, primeiroFim),
        direito(PremiumBeneficioCodigo.FOTOS_EXTRA_5, ultimoFim));
    Map<UUID, Integer> posicoes = Map.of(FOTO_1, 1, FOTO_5, 5);

    var primeiro = service.projetar(capturado, direitos, primeiroFim, posicoes, FONTE);
    assertThat(primeiro.midias()).hasSize(2);
    assertThat(mapper.readTree(primeiro.conteudoJson()).path("proveniencia").asText())
        .isEqualTo("DERIVADO_DE_EXPIRACAO_TEMPORAL");
    var ultimo = service.projetar(capturado, direitos, ultimoFim, posicoes, FONTE);
    assertThat(ultimo.midias()).extracting(m -> m.get("anuncioMidiaId"))
        .containsExactly(FOTO_1.toString());
  }

  @Test
  void snapshotSemPosicaoComprovadaFalhaFechado() throws Exception {
    String capturado = mapper.writeValueAsString(Map.of(
        "estado", "CAPTURADO_NA_EXIBICAO", "midias", List.of(foto(FOTO_5))));
    assertThatThrownBy(() -> service.projetar(capturado,
        List.of(direito(PremiumBeneficioCodigo.FOTOS_EXTRA_5, BASE.plusHours(1))),
        BASE.plusHours(1), Map.of(), FONTE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sem posicao publica comprovada");
  }

  private static ArquivoPublicidadeTransicaoTemporalService.Direito direito(
      String codigo, OffsetDateTime fim) {
    return new ArquivoPublicidadeTransicaoTemporalService.Direito(UUID.randomUUID(), codigo, fim);
  }

  private static Map<String, Object> foto(UUID id) {
    return Map.of("anuncioMidiaId", id.toString(), "tipo", "FOTO", "ordem", 0);
  }

  private static Map<String, Object> video() {
    return Map.of("anuncioMidiaId", VIDEO.toString(), "tipo", "VIDEO", "ordem", 6);
  }
}
