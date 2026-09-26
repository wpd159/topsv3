package br.com.topsdojob.v3.application.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Synthetic PG17 proof, with no R2 access or reconstruction from mutable live media. */
@EnabledIfEnvironmentVariable(named = "ARQUIVO_PUBLICIDADE_TRANSICOES_POSTGRES17_ENABLED", matches = "true")
class ArquivoPublicidadeTransicaoTemporalPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-09-25T12:00:00Z");
  private static final OffsetDateTime FRONTEIRA_EXTRA = BASE.plusHours(1);
  private static final OffsetDateTime FRONTEIRA_VIDEO = BASE.plusHours(2);
  private static final OffsetDateTime PROCESSAMENTO = BASE.plusHours(3);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @AfterAll
  static void cleanup() throws Exception {
    PostgresSupport.stop();
  }

  @Test
  void planoPersisteProjecaoEOrigemMaterializaAtrasadoECancelamentoNaoReescreve() throws Exception {
    PostgresSupport.start();
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        PostgresSupport.jdbcUrl(), "topsv3test", PostgresSupport.credential());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
    DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
    TransactionTemplate transactions = new TransactionTemplate(manager);

    Fixture principal = seed(jdbc);
    StoryFixture story = seedStory(jdbc, principal);
    BeneficioAnuncioConsultaService beneficios = beneficios(principal.anuncio());
    var planejamento = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, beneficios, manager, Clock.fixed(BASE.toInstant(), ZoneOffset.UTC));
    transactions.executeWithoutResult(status ->
        planejamento.reconciliarAnuncioAposCaptura(principal.anuncio(), BASE));
    transactions.executeWithoutResult(status ->
        planejamento.reconciliarStoryAposCaptura(story.story(), BASE));
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_story_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, Long.class, story.janela())).isEqualTo(2L);
    List<UUID> planos = jdbc.queryForList("""
        SELECT id FROM arquivo_publicidade_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        ORDER BY fronteira_em
        """, UUID.class, principal.janela());
    assertThat(planos).hasSize(2);
    UUID planoExtra = planos.get(0);
    UUID planoVideo = planos.get(1);
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_transicao_plano_midia WHERE plano_id = ?
        """, Long.class, planoExtra)).isEqualTo(4L);
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_transicao_plano_midia WHERE plano_id = ?
        """, Long.class, planoVideo)).isEqualTo(3L);
    assertThat(jdbc.queryForList("""
        SELECT origem_midia_id FROM arquivo_publicidade_transicao_plano_midia WHERE plano_id = ?
        """, UUID.class, planoVideo)).contains(principal.fotoPrivada());
    var planoConteudo = MAPPER.readTree(jdbc.queryForObject("""
        SELECT conteudo_projetado_json::text FROM arquivo_publicidade_transicao_plano
        WHERE id = ?
        """, String.class, planoExtra));
    assertThat(planoConteudo.path("midias").toString())
        .doesNotContain(principal.fotosVinculos().get(1).toString())
        .doesNotContain(principal.fotosVinculos().get(4).toString())
        .doesNotContain(principal.fotosVinculos().get(5).toString());

    // The live link changes after planning; late processing may use only the frozen evidence.
    jdbc.update("UPDATE anuncio_midia SET status = 'REMOVIDA' WHERE id = ?",
        principal.fotosVinculos().get(0));
    // The DB has passed both boundaries while this JVM is deliberately behind.
    // The previous min(request, JVM clock) cap left both plans pending here.
    Clock jvmAtrasada = Clock.fixed(BASE.minusDays(1).toInstant(), ZoneOffset.UTC);
    OffsetDateTime bancoAntes = agoraBanco(jdbc);
    assertThat(OffsetDateTime.now(jvmAtrasada)).isBefore(FRONTEIRA_EXTRA);
    assertThat(bancoAntes).isAfter(FRONTEIRA_VIDEO);
    var tardio = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, beneficios, manager, jvmAtrasada);
    transactions.executeWithoutResult(status ->
        tardio.processarAnuncioAte(principal.anuncio(), PROCESSAMENTO));
    transactions.executeWithoutResult(status ->
        tardio.processarStoryAte(story.story(), PROCESSAMENTO));
    transactions.executeWithoutResult(status ->
        tardio.processarStoryAte(story.story(), PROCESSAMENTO));
    transactions.executeWithoutResult(status ->
        tardio.processarAnuncioAte(principal.anuncio(), PROCESSAMENTO));
    OffsetDateTime bancoDepois = agoraBanco(jdbc);

    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_versao WHERE veiculacao_id = ?
        """, Long.class, principal.janela())).isEqualTo(3L);
    Map<String, Object> derivada = jdbc.queryForMap("""
        SELECT id, vigente_desde, capturado_em, evidencia_origem_versao_id,
               conteudo_json::text AS conteudo_json
        FROM arquivo_publicidade_versao WHERE veiculacao_id = ? AND numero = 2
        """, principal.janela());
    assertThat(instante(derivada.get("vigente_desde"))).isEqualTo(FRONTEIRA_EXTRA);
    assertThat(instante(derivada.get("capturado_em"))).isBetween(bancoAntes, bancoDepois);
    assertThat(derivada.get("evidencia_origem_versao_id")).isEqualTo(principal.fonte());
    var conteudo = MAPPER.readTree((String) derivada.get("conteudo_json"));
    assertThat(conteudo.path("estado").asText()).isEqualTo("DERIVADO_DE_EXPIRACAO_TEMPORAL");
    assertThat(conteudo.path("midias")).hasSize(4);
    assertThat(conteudo.path("midias").toString())
        .doesNotContain(principal.fotosVinculos().get(1).toString())
        .doesNotContain(principal.fotosVinculos().get(4).toString())
        .doesNotContain(principal.fotosVinculos().get(5).toString());
    assertThat(conteudo.path("derivacaoTemporal").path("evidenciaOrigemVersaoId").asText())
        .isEqualTo(principal.fonte().toString());
    assertThat(conteudo.path("derivacaoTemporal").path("fronteiraEm").asText())
        .isEqualTo(FRONTEIRA_EXTRA.toString());
    assertThat(conteudo.path("derivacaoTemporal").path("planejadoEm").asText())
        .isEqualTo(BASE.toString());
    assertThat(OffsetDateTime.parse(conteudo.path("derivacaoTemporal")
        .path("processadoEm").asText())).isBetween(bancoAntes, bancoDepois);
    assertThat(jdbc.queryForList("""
        SELECT origem_midia_id FROM arquivo_publicidade_midia_referencia WHERE versao_id = ?
        """, UUID.class, derivada.get("id"))).hasSize(4).contains(principal.fotoPrivada());
    Map<String, Object> aposVideo = jdbc.queryForMap("""
        SELECT id, vigente_desde, capturado_em, evidencia_origem_versao_id,
               conteudo_json::text AS conteudo_json
        FROM arquivo_publicidade_versao WHERE veiculacao_id = ? AND numero = 3
        """, principal.janela());
    assertThat(instante(aposVideo.get("vigente_desde"))).isEqualTo(FRONTEIRA_VIDEO);
    assertThat(instante(aposVideo.get("capturado_em"))).isBetween(bancoAntes, bancoDepois);
    assertThat(aposVideo.get("evidencia_origem_versao_id")).isEqualTo(principal.fonte());
    assertThat(MAPPER.readTree((String) aposVideo.get("conteudo_json")).path("midias"))
        .hasSize(3);
    assertThat(jdbc.queryForList("""
        SELECT origem_midia_id FROM arquivo_publicidade_midia_referencia WHERE versao_id = ?
        """, UUID.class, aposVideo.get("id"))).hasSize(3).contains(principal.fotoPrivada());
    assertThat(jdbc.queryForList("""
        SELECT estado FROM arquivo_publicidade_transicao_plano WHERE veiculacao_id = ?
        """, String.class, principal.janela())).containsExactlyInAnyOrder(
            "PROCESSADA", "PROCESSADA");
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_story_versao WHERE veiculacao_id = ?
        """, Long.class, story.janela())).isEqualTo(3L);
    Map<String, Object> storyExtra = jdbc.queryForMap("""
        SELECT id, vigente_desde, capturado_em, evidencia_origem_versao_id,
               conteudo_json::text AS conteudo_json
        FROM arquivo_publicidade_story_versao WHERE veiculacao_id = ? AND numero = 2
        """, story.janela());
    assertThat(instante(storyExtra.get("vigente_desde"))).isEqualTo(FRONTEIRA_EXTRA);
    assertThat(instante(storyExtra.get("capturado_em"))).isBetween(bancoAntes, bancoDepois);
    assertThat(storyExtra.get("evidencia_origem_versao_id")).isEqualTo(story.fonte());
    var storyConteudo = MAPPER.readTree((String) storyExtra.get("conteudo_json"));
    assertThat(storyConteudo.path("proveniencia").asText())
        .isEqualTo("DERIVADO_DE_EXPIRACAO_TEMPORAL");
    assertThat(storyConteudo.path("midias")).hasSize(4);
    assertThat(OffsetDateTime.parse(storyConteudo.path("derivacaoTemporal")
        .path("processadoEm").asText())).isBetween(bancoAntes, bancoDepois);
    assertThat(jdbc.queryForList("""
        SELECT origem_midia_id FROM arquivo_publicidade_story_midia_referencia WHERE versao_id = ?
        """, UUID.class, storyExtra.get("id"))).hasSize(4).contains(story.fotoPrivada());
    Map<String, Object> storyVideo = jdbc.queryForMap("""
        SELECT id, vigente_desde, capturado_em, evidencia_origem_versao_id,
               conteudo_json::text AS conteudo_json
        FROM arquivo_publicidade_story_versao WHERE veiculacao_id = ? AND numero = 3
        """, story.janela());
    assertThat(instante(storyVideo.get("vigente_desde"))).isEqualTo(FRONTEIRA_VIDEO);
    assertThat(instante(storyVideo.get("capturado_em"))).isBetween(bancoAntes, bancoDepois);
    assertThat(storyVideo.get("evidencia_origem_versao_id")).isEqualTo(story.fonte());
    assertThat(MAPPER.readTree((String) storyVideo.get("conteudo_json")).path("midias"))
        .hasSize(3);
    assertThat(jdbc.queryForList("""
        SELECT origem_midia_id FROM arquivo_publicidade_story_midia_referencia WHERE versao_id = ?
        """, UUID.class, storyVideo.get("id"))).hasSize(3).contains(story.fotoPrivada());

    Fixture cancelada = seed(jdbc);
    BeneficioAnuncioConsultaService direitosCancelados = beneficios(cancelada.anuncio());
    var antesDoFim = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, direitosCancelados, manager, Clock.fixed(BASE.toInstant(), ZoneOffset.UTC));
    transactions.executeWithoutResult(status ->
        antesDoFim.reconciliarAnuncioAposCaptura(cancelada.anuncio(), BASE));
    OffsetDateTime cancelamento = BASE.plusMinutes(30);
    jdbc.update("""
        UPDATE arquivo_publicidade_veiculacao
        SET fim_em = ?, retencao_ate = ?, encerramento_motivo = 'CANCELAMENTO_SINTETICO'
        WHERE id = ?
        """, cancelamento, cancelamento.plusYears(1), cancelada.janela());
    var aposCancelamento = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, direitosCancelados, manager,
        Clock.fixed(cancelamento.toInstant(), ZoneOffset.UTC));
    transactions.executeWithoutResult(status ->
        aposCancelamento.reconciliarAnuncioAposCaptura(cancelada.anuncio(), cancelamento));
    assertThat(jdbc.queryForList("""
        SELECT estado FROM arquivo_publicidade_transicao_plano WHERE veiculacao_id = ?
        """, String.class, cancelada.janela())).containsExactlyInAnyOrder(
            "CANCELADA", "CANCELADA");
    transactions.executeWithoutResult(status ->
        tardio.processarAnuncioAte(cancelada.anuncio(), PROCESSAMENTO));
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_versao WHERE veiculacao_id = ?
        """, Long.class, cancelada.janela())).isEqualTo(1L);

    provarEdicaoAntesDaFronteira(jdbc, named, manager, transactions);
  }

  @Test
  void bootstrapPlanejaSomenteFronteirasFuturasDeFontesJaArquivadas() throws Exception {
    PostgresSupport.start();
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        PostgresSupport.jdbcUrl(), "topsv3test", PostgresSupport.credential());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource);
    DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
    OffsetDateTime inicioBootstrap = agoraBanco(jdbc);
    Fixture legadoAtivo = seed(jdbc);
    StoryFixture storyAtivo = seedStory(jdbc, legadoAtivo, inicioBootstrap.plusHours(4));
    Fixture fronteiraPassada = seed(jdbc);
    PremiumBeneficioCalculado extra = calculado(PremiumBeneficioCodigo.FOTOS_EXTRA_5,
        inicioBootstrap.plusHours(1));
    PremiumBeneficioCalculado video = calculado(PremiumBeneficioCodigo.VIDEO_1,
        inicioBootstrap.plusHours(2));
    BeneficioAnuncioConsultaService direitos = mock(BeneficioAnuncioConsultaService.class);
    when(direitos.consultarCalculadosPorAnuncio(anyCollection(), any(OffsetDateTime.class)))
        .thenReturn(Map.of(legadoAtivo.anuncio(), List.of(extra, video)));
    var runner = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, direitos, manager,
        Clock.fixed(BASE.minusDays(1).toInstant(), ZoneOffset.UTC));

    assertThat(jdbc.queryForObject("""
        SELECT transicoes_reconciliadas_em FROM arquivo_publicidade_versao WHERE id = ?
        """, Object.class, legadoAtivo.fonte())).isNull();
    runner.run(new DefaultApplicationArguments(new String[0]));
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, Long.class, legadoAtivo.janela())).isEqualTo(2L);
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_story_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, Long.class, storyAtivo.janela())).isEqualTo(2L);
    assertThat(jdbc.queryForList("""
        SELECT fronteira_em FROM arquivo_publicidade_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, Object.class, legadoAtivo.janela()).stream().map(
            ArquivoPublicidadeTransicaoTemporalPostgres17IntegrationTest::instante))
        .allSatisfy(fronteira -> assertThat(fronteira).isAfter(inicioBootstrap));
    assertThat(jdbc.queryForObject("""
        SELECT transicoes_reconciliadas_em FROM arquivo_publicidade_versao WHERE id = ?
        """, Object.class, legadoAtivo.fonte())).isNotNull();
    assertThat(jdbc.queryForObject("""
        SELECT transicoes_reconciliadas_em FROM arquivo_publicidade_story_versao WHERE id = ?
        """, Object.class, storyAtivo.fonte())).isNotNull();
    // No current entitlement for this old source: warn about a potential gap,
    // but neither invent a prior boundary nor a new archive version.
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_transicao_plano WHERE veiculacao_id = ?
        """, Long.class, fronteiraPassada.janela())).isZero();
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_versao WHERE veiculacao_id = ?
        """, Long.class, fronteiraPassada.janela())).isEqualTo(1L);
    assertThat(jdbc.queryForObject("""
        SELECT transicoes_reconciliadas_em FROM arquivo_publicidade_versao WHERE id = ?
        """, Object.class, fronteiraPassada.fonte())).isNotNull();
    runner.run(new DefaultApplicationArguments(new String[0]));
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_transicao_plano WHERE veiculacao_id = ?
        """, Long.class, legadoAtivo.janela())).isEqualTo(2L);
  }

  @Test
  void upgradeV055ParaV057InicializaSomenteFuturoSemFabricarPassado() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-temporal-" + suffix + "-net";
    String container = "topsv3-temporal-" + suffix + "-pg17";
    String database = "topsv3_temporal_upgrade";
    String user = "topsv3test";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    command("docker", "network", "create", network);
    try {
      command(Map.of("POSTGRES_PASSWORD", credential),
          "docker", "run", "--pull=never", "-d", "--name", container,
          "--network", network, "-p", "127.0.0.1::5432",
          "-e", "POSTGRES_DB=" + database, "-e", "POSTGRES_USER=" + user,
          "-e", "POSTGRES_PASSWORD", "postgres:17-alpine");
      awaitPostgres(container, credential, user, database);
      flyway(container, network, credential, user, database, "-target=55", "migrate");
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + mappedPort(container) + "/" + database,
          user, credential);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      OffsetDateTime antesDoUpgrade = agoraBanco(jdbc);
      Fixture ativo = seed(jdbc);
      StoryFixture story = seedStory(jdbc, ativo, antesDoUpgrade.plusHours(4));
      Fixture expirado = seed(jdbc);
      String checksumV055 = jdbc.queryForObject("""
          SELECT checksum::text FROM flyway_schema_history WHERE version::integer = 55
          """, String.class);

      flyway(container, network, credential, user, database, "migrate");
      flyway(container, network, credential, user, database, "validate");
      assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class))
          .isBetween(170000, 179999);
      assertThat(jdbc.queryForObject("""
          SELECT checksum::text FROM flyway_schema_history WHERE version::integer = 55
          """, String.class)).isEqualTo(checksumV055);
      assertThat(jdbc.queryForObject("""
          SELECT transicoes_reconciliadas_em FROM arquivo_publicidade_versao WHERE id = ?
          """, Object.class, ativo.fonte())).isNull();

      PremiumBeneficioCalculado extra = calculado(PremiumBeneficioCodigo.FOTOS_EXTRA_5,
          antesDoUpgrade.plusHours(1));
      PremiumBeneficioCalculado video = calculado(PremiumBeneficioCodigo.VIDEO_1,
          antesDoUpgrade.plusHours(2));
      BeneficioAnuncioConsultaService direitos = mock(BeneficioAnuncioConsultaService.class);
      when(direitos.consultarCalculadosPorAnuncio(anyCollection(), any(OffsetDateTime.class)))
          .thenReturn(Map.of(ativo.anuncio(), List.of(extra, video)));
      var runner = new ArquivoPublicidadeTransicaoTemporalService(
          new NamedParameterJdbcTemplate(dataSource), MAPPER, direitos,
          new DataSourceTransactionManager(dataSource),
          Clock.fixed(BASE.minusDays(1).toInstant(), ZoneOffset.UTC));
      runner.run(new DefaultApplicationArguments(new String[0]));
      assertThat(jdbc.queryForObject("""
          SELECT count(*) FROM arquivo_publicidade_transicao_plano
          WHERE veiculacao_id = ? AND estado = 'PENDENTE'
          """, Long.class, ativo.janela())).isEqualTo(2L);
      assertThat(jdbc.queryForObject("""
          SELECT count(*) FROM arquivo_publicidade_story_transicao_plano
          WHERE veiculacao_id = ? AND estado = 'PENDENTE'
          """, Long.class, story.janela())).isEqualTo(2L);
      assertThat(jdbc.queryForObject("""
          SELECT transicoes_reconciliadas_em FROM arquivo_publicidade_versao WHERE id = ?
          """, Object.class, ativo.fonte())).isNotNull();
      assertThat(jdbc.queryForObject("""
          SELECT transicoes_reconciliadas_em FROM arquivo_publicidade_story_versao WHERE id = ?
          """, Object.class, story.fonte())).isNotNull();
      assertThat(jdbc.queryForObject("""
          SELECT count(*) FROM arquivo_publicidade_transicao_plano WHERE veiculacao_id = ?
          """, Long.class, expirado.janela())).isZero();
      assertThat(jdbc.queryForObject("""
          SELECT count(*) FROM arquivo_publicidade_versao WHERE veiculacao_id = ?
          """, Long.class, expirado.janela())).isEqualTo(1L);
    } finally {
      commandIgnoringFailure("docker", "rm", "-f", "-v", container);
      commandIgnoringFailure("docker", "network", "rm", network);
    }
  }

  private static void provarEdicaoAntesDaFronteira(JdbcTemplate jdbc,
      NamedParameterJdbcTemplate named, DataSourceTransactionManager manager,
      TransactionTemplate transactions) throws Exception {
    Fixture anuncio = seed(jdbc);
    StoryFixture story = seedStory(jdbc, anuncio);
    BeneficioAnuncioConsultaService direitos = beneficios(anuncio.anuncio());
    var inicial = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, direitos, manager, Clock.fixed(BASE.toInstant(), ZoneOffset.UTC));
    transactions.executeWithoutResult(status -> {
      jdbc.queryForObject("SELECT id FROM anuncio WHERE id = ? FOR UPDATE", UUID.class,
          anuncio.anuncio());
      inicial.reconciliarAnuncioAposCaptura(anuncio.anuncio(), BASE);
      jdbc.queryForObject("SELECT id FROM story_anuncio WHERE id = ? FOR UPDATE", UUID.class,
          story.story());
      inicial.reconciliarStoryAposCaptura(story.story(), BASE);
    });
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, Long.class, anuncio.janela())).isEqualTo(2L);
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_story_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, Long.class, story.janela())).isEqualTo(2L);

    OffsetDateTime editadoEm = BASE.plusMinutes(30);
    var aposEdicao = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, direitos, manager, Clock.fixed(editadoEm.toInstant(), ZoneOffset.UTC));
    UUID novaFonteAnuncio = transactions.execute(status -> {
      jdbc.queryForObject("SELECT id FROM anuncio WHERE id = ? FOR UPDATE", UUID.class,
          anuncio.anuncio());
      UUID nova = editarAnuncio(jdbc, anuncio, editadoEm);
      aposEdicao.reconciliarAnuncioAposCaptura(anuncio.anuncio(), editadoEm);
      return nova;
    });
    UUID novaFonteStory = transactions.execute(status -> {
      jdbc.queryForObject("SELECT id FROM story_anuncio WHERE id = ? FOR UPDATE", UUID.class,
          story.story());
      UUID nova = editarStory(jdbc, story, editadoEm);
      aposEdicao.reconciliarStoryAposCaptura(story.story(), editadoEm);
      return nova;
    });
    assertThat(jdbc.queryForList("""
        SELECT estado FROM arquivo_publicidade_transicao_plano WHERE veiculacao_id = ?
        """, String.class, anuncio.janela())).containsExactlyInAnyOrder(
            "CANCELADA", "CANCELADA", "PENDENTE", "PENDENTE");
    assertThat(jdbc.queryForList("""
        SELECT estado FROM arquivo_publicidade_story_transicao_plano WHERE veiculacao_id = ?
        """, String.class, story.janela())).containsExactlyInAnyOrder(
            "CANCELADA", "CANCELADA", "PENDENTE", "PENDENTE");
    assertThat(jdbc.queryForList("""
        SELECT DISTINCT evidencia_versao_id FROM arquivo_publicidade_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, UUID.class, anuncio.janela())).containsExactly(novaFonteAnuncio);
    assertThat(jdbc.queryForList("""
        SELECT DISTINCT evidencia_versao_id FROM arquivo_publicidade_story_transicao_plano
        WHERE veiculacao_id = ? AND estado = 'PENDENTE'
        """, UUID.class, story.janela())).containsExactly(novaFonteStory);

    OffsetDateTime bancoAntes = agoraBanco(jdbc);
    var atrasado = new ArquivoPublicidadeTransicaoTemporalService(
        named, MAPPER, direitos, manager,
        Clock.fixed(BASE.minusDays(1).toInstant(), ZoneOffset.UTC));
    transactions.executeWithoutResult(status -> {
      jdbc.queryForObject("SELECT id FROM anuncio WHERE id = ? FOR UPDATE", UUID.class,
          anuncio.anuncio());
      atrasado.processarAnuncioAte(anuncio.anuncio(), PROCESSAMENTO);
      jdbc.queryForObject("SELECT id FROM story_anuncio WHERE id = ? FOR UPDATE", UUID.class,
          story.story());
      atrasado.processarStoryAte(story.story(), PROCESSAMENTO);
    });
    OffsetDateTime bancoDepois = agoraBanco(jdbc);
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_versao WHERE veiculacao_id = ?
        """, Long.class, anuncio.janela())).isEqualTo(4L);
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM arquivo_publicidade_story_versao WHERE veiculacao_id = ?
        """, Long.class, story.janela())).isEqualTo(4L);
    for (int numero : List.of(3, 4)) {
      Map<String, Object> versaoAnuncio = jdbc.queryForMap("""
          SELECT vigente_desde, capturado_em, evidencia_origem_versao_id,
                 conteudo_json::text AS conteudo_json
          FROM arquivo_publicidade_versao WHERE veiculacao_id = ? AND numero = ?
          """, anuncio.janela(), numero);
      assertThat(versaoAnuncio.get("evidencia_origem_versao_id"))
          .isEqualTo(novaFonteAnuncio).isNotEqualTo(anuncio.fonte());
      assertThat(instante(versaoAnuncio.get("vigente_desde")))
          .isEqualTo(numero == 3 ? FRONTEIRA_EXTRA : FRONTEIRA_VIDEO);
      assertThat(instante(versaoAnuncio.get("capturado_em"))).isBetween(bancoAntes, bancoDepois);
      assertThat(MAPPER.readTree((String) versaoAnuncio.get("conteudo_json"))
          .path("titulo").asText()).isEqualTo("EDITADO_ANTES_DA_FRONTEIRA");
      Map<String, Object> versaoStory = jdbc.queryForMap("""
          SELECT vigente_desde, capturado_em, evidencia_origem_versao_id,
                 conteudo_json::text AS conteudo_json
          FROM arquivo_publicidade_story_versao WHERE veiculacao_id = ? AND numero = ?
          """, story.janela(), numero);
      assertThat(versaoStory.get("evidencia_origem_versao_id"))
          .isEqualTo(novaFonteStory).isNotEqualTo(story.fonte());
      assertThat(instante(versaoStory.get("vigente_desde")))
          .isEqualTo(numero == 3 ? FRONTEIRA_EXTRA : FRONTEIRA_VIDEO);
      assertThat(instante(versaoStory.get("capturado_em"))).isBetween(bancoAntes, bancoDepois);
      assertThat(MAPPER.readTree((String) versaoStory.get("conteudo_json"))
          .path("titulo").asText()).isEqualTo("EDITADO_ANTES_DA_FRONTEIRA");
    }
  }

  private static UUID editarAnuncio(JdbcTemplate jdbc, Fixture anuncio,
      OffsetDateTime editadoEm) {
    UUID nova = UUID.randomUUID();
    String conteudo = conteudoEditado(jdbc, "arquivo_publicidade_versao", anuncio.fonte());
    jdbc.update("UPDATE arquivo_publicidade_versao SET vigente_ate = ? WHERE id = ?",
        editadoEm, anuncio.fonte());
    jdbc.update("""
        INSERT INTO arquivo_publicidade_versao(id,veiculacao_id,numero,vigente_desde,
          capturado_em,motivo,conteudo_json,contratante_json,comercial_json,
          segmentacao_json,alcance_json,conteudo_sha256)
        SELECT ?, veiculacao_id, 2, ?, ?, 'EDICAO_SINTETICA', ?::jsonb,
          contratante_json, comercial_json, segmentacao_json, alcance_json, ?
        FROM arquivo_publicidade_versao WHERE id = ?
        """, nova, editadoEm, editadoEm, conteudo, hashFixture(conteudo), anuncio.fonte());
    for (Map<String, Object> midia : jdbc.queryForList("""
        SELECT id, anuncio_midia_id, variante, ordem
        FROM arquivo_publicidade_midia WHERE versao_id = ?
        """, anuncio.fonte())) {
      jdbc.update("""
          INSERT INTO arquivo_publicidade_midia_referencia(id,versao_id,origem_midia_id,
            anuncio_midia_id,variante,ordem) VALUES (?, ?, ?, ?, ?, ?)
          """, UUID.randomUUID(), nova, midia.get("id"), midia.get("anuncio_midia_id"),
          midia.get("variante"), midia.get("ordem"));
    }
    return nova;
  }

  private static UUID editarStory(JdbcTemplate jdbc, StoryFixture story,
      OffsetDateTime editadoEm) {
    UUID nova = UUID.randomUUID();
    String conteudo = conteudoEditado(jdbc, "arquivo_publicidade_story_versao", story.fonte());
    jdbc.update("UPDATE arquivo_publicidade_story_versao SET vigente_ate = ? WHERE id = ?",
        editadoEm, story.fonte());
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_versao(id,veiculacao_id,numero,vigente_desde,
          vigente_ate,capturado_em,motivo,conteudo_json,contratante_json,comercial_json,
          segmentacao_json,alcance_json,conteudo_sha256)
        SELECT ?, veiculacao_id, 2, ?, j.fim_em, ?, 'EDICAO_SINTETICA', ?::jsonb,
          v.contratante_json, v.comercial_json, v.segmentacao_json, v.alcance_json, ?
        FROM arquivo_publicidade_story_versao v
        JOIN arquivo_publicidade_story_veiculacao j ON j.id = v.veiculacao_id
        WHERE v.id = ?
        """, nova, editadoEm, editadoEm, conteudo, hashFixture(conteudo), story.fonte());
    for (Map<String, Object> midia : jdbc.queryForList("""
        SELECT id, arquivo_midia_id, variante, ordem
        FROM arquivo_publicidade_story_midia WHERE versao_id = ?
        """, story.fonte())) {
      jdbc.update("""
          INSERT INTO arquivo_publicidade_story_midia_referencia(id,versao_id,origem_midia_id,
            arquivo_midia_id,variante,ordem) VALUES (?, ?, ?, ?, ?, ?)
          """, UUID.randomUUID(), nova, midia.get("id"), midia.get("arquivo_midia_id"),
          midia.get("variante"), midia.get("ordem"));
    }
    return nova;
  }

  private static String conteudoEditado(JdbcTemplate jdbc, String tabela, UUID origem) {
    try {
      String fonte = jdbc.queryForObject(
          "SELECT conteudo_json::text FROM " + tabela + " WHERE id = ?", String.class, origem);
      Map<String, Object> conteudo = MAPPER.readValue(fonte, new TypeReference<>() { });
      conteudo.put("titulo", "EDITADO_ANTES_DA_FRONTEIRA");
      return MAPPER.writeValueAsString(conteudo);
    } catch (Exception exception) {
      throw new IllegalStateException("fixture temporal editada invalida", exception);
    }
  }

  private static String hashFixture(String conteudo) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
          (conteudo + "{}{}{}{}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private static BeneficioAnuncioConsultaService beneficios(UUID anuncio) {
    PremiumBeneficioCalculado extra = calculado(PremiumBeneficioCodigo.FOTOS_EXTRA_5,
        FRONTEIRA_EXTRA);
    PremiumBeneficioCalculado video = calculado(PremiumBeneficioCodigo.VIDEO_1,
        FRONTEIRA_VIDEO);
    BeneficioAnuncioConsultaService consulta = mock(BeneficioAnuncioConsultaService.class);
    when(consulta.consultarCalculadosPorAnuncio(anyCollection(), any(OffsetDateTime.class)))
        .thenReturn(Map.of(anuncio, List.of(extra, video)));
    return consulta;
  }

  private static PremiumBeneficioCalculado calculado(String codigo, OffsetDateTime fim) {
    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    when(ativacao.getId()).thenReturn(UUID.randomUUID());
    when(ativacao.getFimEm()).thenReturn(fim);
    BeneficioPremiumEntity beneficio = mock(BeneficioPremiumEntity.class);
    when(beneficio.getCodigo()).thenReturn(codigo);
    return new PremiumBeneficioCalculado(
        ativacao, beneficio, null, PremiumBeneficioStatusCalculado.ATIVO,
        List.of(), false, false);
  }

  private static Fixture seed(JdbcTemplate jdbc) throws Exception {
    UUID usuario = UUID.randomUUID();
    UUID anuncio = UUID.randomUUID();
    UUID janela = UUID.randomUUID();
    UUID fonte = UUID.randomUUID();
    OffsetDateTime inicio = BASE.minusHours(1);
    jdbc.update("""
        INSERT INTO usuario(id,nome,email_normalizado,status,tipo_conta,criado_em,atualizado_em)
        VALUES (?, 'Pessoa temporal sintética', ?, 'ATIVO', 'ANUNCIANTE', ?, ?)
        """, usuario, "temporal-" + usuario + "@example.invalid", inicio, inicio);
    jdbc.update("""
        INSERT INTO anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,
          criado_em,atualizado_em)
        VALUES (?, ?, ?, 'Anúncio sintético', 'Sem dados reais', 'PUBLICADO', 'APROVADO',
          'OUTROS', ?, ?)
        """, anuncio, usuario, "temporal-" + anuncio, inicio, inicio);
    List<UUID> fotosVinculos = new ArrayList<>();
    List<MidiaFonte> fontes = new ArrayList<>();
    UUID fotoPrivada = null;
    for (int posicao = 1; posicao <= 6; posicao++) {
      UUID arquivo = UUID.randomUUID();
      UUID vinculo = UUID.randomUUID();
      fotosVinculos.add(vinculo);
      seedMidia(jdbc, arquivo, vinculo, anuncio, "FOTO", posicao, inicio,
          posicao == 2 ? "PENDENTE" : "VALIDADO");
      if (posicao != 2) {
        UUID privada = UUID.randomUUID();
        if (posicao == 1) fotoPrivada = privada;
        fontes.add(new MidiaFonte(arquivo, vinculo, privada, "FOTO", posicao));
      }
    }
    UUID videoArquivo = UUID.randomUUID();
    UUID videoVinculo = UUID.randomUUID();
    seedMidia(jdbc, videoArquivo, videoVinculo, anuncio, "VIDEO", 7, inicio,
        "VALIDADO");
    fontes.add(0, new MidiaFonte(videoArquivo, videoVinculo, UUID.randomUUID(), "VIDEO", 7));
    jdbc.update("""
        INSERT INTO arquivo_publicidade_veiculacao(id,anuncio_id,contratante_usuario_id,
          classificacao,relacao_material,cobertura,inicio_em,criado_em,atualizado_em)
        VALUES (?, ?, ?, 'ORIGEM_INDETERMINADA', 'DESCONHECIDA', 'PREVENTIVA', ?, ?, ?)
        """, janela, anuncio, usuario, inicio, inicio, inicio);
    String conteudo = MAPPER.writeValueAsString(new LinkedHashMap<>(Map.of(
        "estado", "CAPTURADO_NA_EXIBICAO", "anuncioId", anuncio.toString(),
        "midias", fontes.stream().map(midia -> Map.of(
            "anuncioMidiaId", midia.vinculo().toString(),
            "arquivoMidiaId", midia.arquivo().toString(),
            "tipo", midia.tipo(), "ordem", midia.ordem())).toList())));
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
        (conteudo + "{}{}{}{}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    jdbc.update("""
        INSERT INTO arquivo_publicidade_versao(id,veiculacao_id,numero,vigente_desde,capturado_em,
          motivo,conteudo_json,contratante_json,comercial_json,segmentacao_json,alcance_json,
          conteudo_sha256)
        VALUES (?, ?, 1, ?, ?, 'CAPTURA_SINTETICA', ?::jsonb, '{}'::jsonb, '{}'::jsonb,
          '{}'::jsonb, '{}'::jsonb, ?)
        """, fonte, janela, inicio, inicio, conteudo, hash);
    for (MidiaFonte midia : fontes) {
      seedPrivada(jdbc, midia.privada(), fonte, midia.vinculo(), midia.arquivo(),
          midia.tipo(), midia.ordem());
    }
    return new Fixture(usuario, anuncio, janela, fonte, List.copyOf(fotosVinculos), fotoPrivada);
  }

  private static StoryFixture seedStory(JdbcTemplate jdbc, Fixture anuncio) throws Exception {
    return seedStory(jdbc, anuncio, BASE.plusHours(4));
  }

  private static StoryFixture seedStory(JdbcTemplate jdbc, Fixture anuncio,
      OffsetDateTime fim) throws Exception {
    UUID novoBeneficio = UUID.randomUUID();
    UUID ativacao = UUID.randomUUID();
    UUID story = UUID.randomUUID();
    UUID janela = UUID.randomUUID();
    UUID fonte = UUID.randomUUID();
    UUID fotoPrivada = null;
    OffsetDateTime inicio = fim.minusHours(5);
    jdbc.update("""
        INSERT INTO beneficio_premium(id,codigo,nome,descricao,escopo,criado_em,atualizado_em)
        VALUES (?, 'STORIES', 'Stories', 'Fixture temporal', 'MIDIA', ?, ?)
        ON CONFLICT (codigo) DO NOTHING
        """, novoBeneficio, inicio, inicio);
    UUID beneficio = jdbc.queryForObject("""
        SELECT id FROM beneficio_premium WHERE codigo = 'STORIES'
        """, UUID.class);
    jdbc.update("""
        INSERT INTO ativacao_beneficio(id,beneficio_id,usuario_id,anuncio_id,origem,
          inicio_em,fim_em,status,custo_creditos_snapshot,criado_em)
        VALUES (?, ?, ?, ?, 'CREDITO', ?, ?, 'ATIVA', 1, ?)
        """, ativacao, beneficio, anuncio.usuario(), anuncio.anuncio(), inicio, fim, inicio);
    jdbc.update("""
        INSERT INTO story_anuncio(id,status,inicio_em,fim_em,criado_por,criado_em,atualizado_em,
          anuncio_id,modo_conteudo,ativacao_beneficio_id,idempotency_key,request_fingerprint)
        VALUES (?, 'PUBLICADO', ?, ?, ?, ?, ?, ?, 'ANUNCIO', ?, ?, ?)
        """, story, inicio, fim, anuncio.usuario(), inicio, inicio, anuncio.anuncio(),
        ativacao, "story-temporal-" + story, "c".repeat(64));
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_veiculacao(id,story_id,anuncio_id,
          contratante_usuario_id,ativacao_beneficio_id,modo_conteudo,classificacao,
          relacao_material,cobertura,inicio_em,fim_em,retencao_ate,encerramento_motivo,
          capturado_em,atualizado_em)
        VALUES (?, ?, ?, ?, ?, 'ANUNCIO', 'ORIGEM_INDETERMINADA', 'DESCONHECIDA',
          'PREVENTIVA', ?, ?, ?, 'FIM_PROGRAMADO', ?, ?)
        """, janela, story, anuncio.anuncio(), anuncio.usuario(), ativacao,
        inicio, fim, fim.plusYears(1), inicio, inicio);
    String fonteAdJson = jdbc.queryForObject("""
        SELECT conteudo_json::text FROM arquivo_publicidade_versao WHERE id = ?
        """, String.class, anuncio.fonte());
    Map<String, Object> conteudoAd = MAPPER.readValue(fonteAdJson, new TypeReference<>() { });
    String conteudo = MAPPER.writeValueAsString(Map.of(
        "proveniencia", "CAPTURADO_NA_EXIBICAO_PROSPECTIVA",
        "storyId", story.toString(), "modoConteudo", "ANUNCIO",
        "anuncioId", anuncio.anuncio().toString(), "midias", conteudoAd.get("midias")));
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
        (conteudo + "{}{}{}{}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_versao(id,veiculacao_id,numero,vigente_desde,
          vigente_ate,capturado_em,motivo,conteudo_json,contratante_json,comercial_json,
          segmentacao_json,alcance_json,conteudo_sha256)
        VALUES (?, ?, 1, ?, ?, ?, 'CAPTURA_STORY_SINTETICA', ?::jsonb,
          '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, ?)
        """, fonte, janela, inicio, fim, inicio, conteudo, hash);
    List<Map<String, Object>> midias = jdbc.queryForList("""
        SELECT anuncio_midia_id, arquivo_midia_id, variante, mime_type, ordem
        FROM arquivo_publicidade_midia WHERE versao_id = ?
        """, anuncio.fonte());
    for (Map<String, Object> midia : midias) {
      UUID privada = UUID.randomUUID();
      if (anuncio.fotosVinculos().get(0).equals(midia.get("anuncio_midia_id"))) {
        fotoPrivada = privada;
      }
      jdbc.update("""
          INSERT INTO arquivo_publicidade_story_midia(id,versao_id,anuncio_midia_id,
            arquivo_midia_id,variante,storage_provider,bucket,chave_privada,sha256,
            mime_type,tamanho_bytes,ordem)
          VALUES (?, ?, ?, ?, ?, 'R2', 'privado', ?, ?, ?, 10, ?)
          """, privada, fonte, midia.get("anuncio_midia_id"),
          midia.get("arquivo_midia_id"), midia.get("variante"),
          "temporal/story/private/" + privada, "b".repeat(64),
          midia.get("mime_type"), midia.get("ordem"));
    }
    return new StoryFixture(story, janela, fonte, fotoPrivada);
  }

  private static void seedMidia(JdbcTemplate jdbc, UUID arquivo, UUID vinculo, UUID anuncio,
      String tipo, int ordem, OffsetDateTime inicio, String statusArquivo) {
    jdbc.update("""
        INSERT INTO arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,
          tamanho_bytes,sha256,status_arquivo,criado_em)
        VALUES (?, 'R2', 'publico', ?, ?, 10, ?, ?, ?)
        """, arquivo, "temporal/" + arquivo,
        "VIDEO".equals(tipo) ? "video/mp4" : "image/jpeg", "b".repeat(64),
        statusArquivo, inicio);
    jdbc.update("""
        INSERT INTO anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,
          status,visibilidade_midia,criado_em,atualizado_em)
        VALUES (?, ?, ?, ?, 'GALERIA', ?, 'PUBLICAVEL', ?, ?, ?)
        """, vinculo, anuncio, arquivo, tipo, ordem,
        "VIDEO".equals(tipo) ? "RESTRITA_18" : "LIVRE", inicio, inicio);
  }

  private static void seedPrivada(JdbcTemplate jdbc, UUID privada, UUID versao,
      UUID vinculo, UUID arquivo, String tipo, int ordem) {
    jdbc.update("""
        INSERT INTO arquivo_publicidade_midia(id,versao_id,anuncio_midia_id,arquivo_midia_id,
          variante,storage_provider,bucket,chave_privada,sha256,mime_type,tamanho_bytes,ordem)
        VALUES (?, ?, ?, ?, 'ORIGINAL', 'R2', 'privado', ?, ?, ?, 10, ?)
        """, privada, versao, vinculo, arquivo, "temporal/private/" + privada,
        "b".repeat(64), "VIDEO".equals(tipo) ? "video/mp4" : "image/jpeg", ordem);
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor instanceof OffsetDateTime instante) return instante;
    return ((Timestamp) valor).toInstant().atOffset(ZoneOffset.UTC);
  }

  private static OffsetDateTime agoraBanco(JdbcTemplate jdbc) {
    return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class)
        .withOffsetSameInstant(ZoneOffset.UTC);
  }

  private static void flyway(String container, String network, String credential,
      String user, String database, String... operation) throws Exception {
    String[] base = {"docker", "run", "--pull=never", "--rm", "--network", network,
        "-e", "FLYWAY_PASSWORD", "-v", MIGRATIONS + ":/flyway/sql:ro",
        "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/" + database,
        "-user=" + user, "-locations=filesystem:/flyway/sql"};
    String[] args = new String[base.length + operation.length];
    System.arraycopy(base, 0, args, 0, base.length);
    System.arraycopy(operation, 0, args, base.length, operation.length);
    command(Map.of("FLYWAY_PASSWORD", credential), args);
  }

  private static void awaitPostgres(String container, String credential,
      String user, String database) throws Exception {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (commandIgnoringFailure(Map.of("PGPASSWORD", credential),
          "docker", "exec", "-e", "PGPASSWORD", container,
          "pg_isready", "--host", "127.0.0.1", "--username", user,
          "--dbname", database) == 0) return;
      Thread.sleep(500L);
    }
    throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
  }

  private static int mappedPort(String container) throws Exception {
    String output = command("docker", "port", container, "5432/tcp").trim();
    return Integer.parseInt(output.substring(output.lastIndexOf(':') + 1));
  }

  private static String command(String... args) throws Exception {
    return command(Map.of(), args);
  }

  private static String command(Map<String, String> environment, String... args)
      throws Exception {
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    builder.environment().putAll(environment);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.waitFor();
    if (exit != 0) throw new IllegalStateException(String.join(" ", args) + " falhou: " + output);
    return output;
  }

  private static int commandIgnoringFailure(String... args) throws Exception {
    return commandIgnoringFailure(Map.of(), args);
  }

  private static int commandIgnoringFailure(Map<String, String> environment,
      String... args) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    builder.environment().putAll(environment);
    Process process = builder.start();
    process.getInputStream().readAllBytes();
    return process.waitFor();
  }

  private record MidiaFonte(UUID arquivo, UUID vinculo, UUID privada, String tipo, int ordem) { }
  private record Fixture(UUID usuario, UUID anuncio, UUID janela, UUID fonte,
      List<UUID> fotosVinculos, UUID fotoPrivada) { }
  private record StoryFixture(UUID story, UUID janela, UUID fonte, UUID fotoPrivada) { }
}
