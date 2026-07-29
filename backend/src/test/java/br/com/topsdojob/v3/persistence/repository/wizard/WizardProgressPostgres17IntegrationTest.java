package br.com.topsdojob.v3.persistence.repository.wizard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
    named = "WIZARD_PROGRESS_POSTGRES17_ENABLED",
    matches = "true")
class WizardProgressPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

  @Test
  void aplicaV043EReconciliaRetryFunilFiltrosEEstadoDoAnuncio() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-wizard-" + suffix + "-net";
    String container = "topsv3-wizard-" + suffix + "-pg17";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();

    command("docker", "network", "create", network);
    command(
        Map.of("POSTGRES_PASSWORD", credential),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", network,
        "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=topsv3_wizard",
        "-e", "POSTGRES_USER=topsv3test",
        "-e", "POSTGRES_PASSWORD",
        "postgres:17-alpine");
    try {
      awaitPostgres(container, credential);
      command(
          Map.of("FLYWAY_PASSWORD", credential),
          "docker", "run", "--pull=never", "--rm", "--network", network,
          "-e", "FLYWAY_PASSWORD",
          "-v", MIGRATIONS + ":/flyway/sql:ro",
          "flyway/flyway:12.10.0",
          "-url=jdbc:postgresql://" + container + ":5432/topsv3_wizard",
          "-user=topsv3test",
          "-locations=filesystem:/flyway/sql",
          "migrate");

      int port = mappedPort(container);
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_wizard",
          "topsv3test",
          credential);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      seed(jdbc);
      WizardProgressJdbcRepository repository =
          new WizardProgressJdbcRepository(new NamedParameterJdbcTemplate(dataSource));

      UUID usuarioId = UUID.fromString("10000000-0000-0000-0000-000000000001");
      UUID anuncioId = UUID.fromString("40000000-0000-0000-0000-000000000001");
      String sessionId = "wizard-pg17-session";
      OffsetDateTime t0 = OffsetDateTime.parse("2026-07-29T10:00:00Z");
      repository.sincronizar(
          UUID.randomUUID(), sessionId, usuarioId, null,
          "CREATE", "PERFIL", 0, "EM_PREENCHIMENTO", t0);
      var fotos = repository.sincronizar(
          UUID.randomUUID(), sessionId, usuarioId, null,
          "CREATE", "FOTOS", 3, "EM_PREENCHIMENTO", t0.plusMinutes(10));
      var retry = repository.sincronizar(
          UUID.randomUUID(), sessionId, usuarioId, null,
          "CREATE", "FOTOS", 3, "EM_PREENCHIMENTO", t0.plusMinutes(20));

      assertThat(retry.id()).isEqualTo(fotos.id());
      assertThat(retry.atualizadoEm()).isEqualTo(fotos.atualizadoEm());
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM wizard_progresso",
          Long.class)).isEqualTo(1L);

      repository.sincronizar(
          UUID.randomUUID(), sessionId, usuarioId, anuncioId,
          "CREATE", "CONCLUIDO", 7, "AGUARDANDO_MODERACAO", t0.plusMinutes(30));
      var filters = WizardProgressJdbcRepository.filtros(
          OffsetDateTime.parse("2026-07-29T00:00:00Z"),
          OffsetDateTime.parse("2026-07-30T00:00:00Z"),
          "qa",
          "CREATE",
          "TODOS",
          "GO",
          "goiania",
          "NAO_INICIADO",
          "PENDENTE_REVISAO");
      var summary = repository.resumo(filters);
      var funnel = repository.funil(filters);
      var page = repository.listar(filters, 0, 20);

      assertThat(summary.sessoesObservadas()).isEqualTo(1);
      assertThat(summary.usuariosObservados()).isEqualTo(1);
      assertThat(summary.kycNaoIniciado()).isEqualTo(1);
      assertThat(summary.aguardandoModeracao()).isEqualTo(1);
      assertThat(summary.tempoMedioConclusaoMinutos()).isEqualTo(30L);
      assertThat(funnel).containsOnly(1L);
      assertThat(page.totalElementos()).isEqualTo(1);
      assertThat(page.itens()).singleElement().satisfies(item -> {
        assertThat(item.ultimoStep()).isEqualTo("CONCLUIDO");
        assertThat(item.status()).isEqualTo("AGUARDANDO_MODERACAO");
      });

      jdbc.update("""
          UPDATE anuncio
          SET status = 'PUBLICADO',
              status_moderacao = 'APROVADO',
              publicado_em = '2026-07-29T11:00:00Z',
              atualizado_em = '2026-07-29T11:00:00Z'
          WHERE id = '40000000-0000-0000-0000-000000000001'
          """);
      var publishedFilters = WizardProgressJdbcRepository.filtros(
          OffsetDateTime.parse("2026-07-29T00:00:00Z"),
          OffsetDateTime.parse("2026-07-30T00:00:00Z"),
          null,
          "TODOS",
          "PUBLICADO",
          null,
          null,
          "TODOS",
          "PUBLICADO");
      assertThat(repository.resumo(publishedFilters).anunciosPublicados()).isEqualTo(1);
      assertThat(repository.listar(publishedFilters, 0, 20).itens())
          .singleElement()
          .satisfies(item -> assertThat(item.status()).isEqualTo("PUBLICADO"));
    } finally {
      commandIgnoringFailure("docker", "rm", "-f", container);
      commandIgnoringFailure("docker", "network", "rm", network);
    }
  }

  private static void seed(JdbcTemplate jdbc) {
    jdbc.update("""
        INSERT INTO usuario (
          id, nome, email_normalizado, telefone_normalizado,
          status, tipo_conta, criado_em, atualizado_em, versao
        ) VALUES (
          '10000000-0000-0000-0000-000000000001',
          'Usuario QA', 'qa.progress@example.invalid', '+5511999999999',
          'ATIVO', 'ANUNCIANTE',
          '2026-07-29T09:00:00Z', '2026-07-29T09:00:00Z', 0
        )
        """);
    jdbc.update("""
        INSERT INTO estado (id, uf, nome, nome_normalizado, criado_em)
        VALUES (
          '20000000-0000-0000-0000-000000000001',
          'GO', 'Goias', 'goias', '2026-07-29T09:00:00Z'
        )
        """);
    jdbc.update("""
        INSERT INTO cidade (
          id, estado_id, nome, nome_normalizado, slug, criado_em
        ) VALUES (
          '30000000-0000-0000-0000-000000000001',
          '20000000-0000-0000-0000-000000000001',
          'Goiania', 'goiania', 'goiania', '2026-07-29T09:00:00Z'
        )
        """);
    jdbc.update("""
        INSERT INTO anuncio (
          id, usuario_id, slug, titulo, status, status_moderacao,
          categoria, criado_em, atualizado_em, versao
        ) VALUES (
          '40000000-0000-0000-0000-000000000001',
          '10000000-0000-0000-0000-000000000001',
          'anuncio-wizard-qa', 'Anuncio Wizard QA',
          'PENDENTE_REVISAO', 'PENDENTE', 'ACOMPANHANTE_FEMININA',
          '2026-07-29T10:00:00Z', '2026-07-29T10:00:00Z', 0
        )
        """);
    jdbc.update("""
        INSERT INTO anuncio_localizacao (
          anuncio_id, estado_id, cidade_id, criado_em, atualizado_em
        ) VALUES (
          '40000000-0000-0000-0000-000000000001',
          '20000000-0000-0000-0000-000000000001',
          '30000000-0000-0000-0000-000000000001',
          '2026-07-29T10:00:00Z', '2026-07-29T10:00:00Z'
        )
        """);
  }

  private static void awaitPostgres(String container, String credential) throws Exception {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (commandIgnoringFailure(
          Map.of("PGPASSWORD", credential),
          "docker", "exec", "-e", "PGPASSWORD", container,
          "pg_isready", "--host", "127.0.0.1",
          "--username", "topsv3test", "--dbname", "topsv3_wizard") == 0) {
        return;
      }
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

  private static String command(
      Map<String, String> environment,
      String... args) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    builder.environment().putAll(environment);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException(String.join(" ", args) + " falhou: " + output);
    }
    return output;
  }

  private static int commandIgnoringFailure(String... args) throws Exception {
    return commandIgnoringFailure(Map.of(), args);
  }

  private static int commandIgnoringFailure(
      Map<String, String> environment,
      String... args) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    builder.environment().putAll(environment);
    Process process = builder.start();
    process.getInputStream().readAllBytes();
    return process.waitFor();
  }
}
