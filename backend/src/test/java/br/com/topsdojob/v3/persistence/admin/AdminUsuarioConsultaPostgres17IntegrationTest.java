package br.com.topsdojob.v3.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioConsultaJdbcRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
        named = "ADMIN_USUARIOS_POSTGRES17_ENABLED",
        matches = "true")
class AdminUsuarioConsultaPostgres17IntegrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

    @Test
    void buscaFiltraOrdenaEPaginaComKycEAnunciosSemDuplicarUsuario() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String network = "topsv3-admin-usuarios-" + suffix + "-net";
        String container = "topsv3-admin-usuarios-" + suffix + "-pg17";
        String credential = UUID.randomUUID().toString() + UUID.randomUUID();

        command("docker", "network", "create", network);
        command(
                Map.of("POSTGRES_PASSWORD", credential),
                "docker", "run", "--pull=never", "-d", "--name", container,
                "--network", network,
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=topsv3_admin_usuarios",
                "-e", "POSTGRES_USER=topsv3test",
                "-e", "POSTGRES_PASSWORD",
                "postgres:17-alpine");
        try {
            awaitPostgres(container, credential);
            migrate(container, network, credential);
            int port = mappedPort(container);
            var dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_admin_usuarios",
                    "topsv3test",
                    credential);
            var jdbc = new JdbcTemplate(dataSource);
            seed(jdbc);
            var repository = new AdminUsuarioConsultaJdbcRepository(
                    new NamedParameterJdbcTemplate(dataSource));

            var byEmail = repository.listar(
                    "qa.aprovado@example.invalid",
                    null,
                    null,
                    "ATIVO",
                    "APROVADO",
                    "RECENTES",
                    PageRequest.of(0, 20));
            assertThat(byEmail).singleElement().satisfies(user -> {
                assertThat(user.nomeCivil()).isEqualTo("QA Aprovado");
                assertThat(user.totalAnuncios()).isEqualTo(2);
                assertThat(user.kycStatus()).isEqualTo("APROVADO");
                assertThat(user.bloqueado()).isFalse();
            });

            var byCpf = repository.listar(
                    "78909",
                    "78909",
                    null,
                    null,
                    null,
                    "RECENTES",
                    PageRequest.of(0, 20));
            assertThat(byCpf).singleElement().satisfies(user ->
                    assertThat(user.cpf()).isEqualTo("12345678909"));

            var byMaskedCpf = repository.listar(
                    "***.***.***-09",
                    null,
                    "09",
                    null,
                    null,
                    "RECENTES",
                    PageRequest.of(0, 20));
            assertThat(byMaskedCpf).singleElement().satisfies(user ->
                    assertThat(user.email()).isEqualTo("qa.aprovado@example.invalid"));

            var byPhone = repository.listar(
                    "999999",
                    "999999",
                    null,
                    null,
                    null,
                    "RECENTES",
                    PageRequest.of(0, 20));
            assertThat(byPhone).singleElement().satisfies(user ->
                    assertThat(user.telefone()).isEqualTo("+5562999999999"));

            var byName = repository.listar(
                    "QA Aprovado",
                    null,
                    null,
                    null,
                    null,
                    "RECENTES",
                    PageRequest.of(0, 20));
            assertThat(byName).singleElement().satisfies(user ->
                    assertThat(user.nomeCivil()).isEqualTo("QA Aprovado"));

            var suspended = repository.listar(
                    null,
                    null,
                    null,
                    "SUSPENSO",
                    "SEM_ENVIO",
                    "ANTIGOS",
                    PageRequest.of(0, 20));
            assertThat(suspended).singleElement().satisfies(user -> {
                assertThat(user.nome()).isEqualTo("QA Suspenso");
                assertThat(user.totalAnuncios()).isZero();
            });

            var firstPage = repository.listar(
                    null,
                    null,
                    null,
                    null,
                    null,
                    "ANTIGOS",
                    PageRequest.of(0, 1));
            assertThat(firstPage.getTotalElements()).isEqualTo(2);
            assertThat(firstPage.getContent()).singleElement().satisfies(user ->
                    assertThat(user.nome()).isEqualTo("QA Suspenso"));
        } finally {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }
    }

    private static void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO usuario (
                  id, nome, nome_civil, email_normalizado, telefone_normalizado,
                  cpf_normalizado, data_nascimento, status, tipo_conta,
                  criado_em, atualizado_em, versao
                ) VALUES
                  (
                    '10000000-0000-4000-8000-000000000001', 'QA Publico', 'QA Aprovado',
                    'qa.aprovado@example.invalid', '+5562999999999', '12345678909',
                    '1990-01-02', 'ATIVO', 'ANUNCIANTE',
                    '2026-07-20T10:00:00Z', '2026-07-27T10:00:00Z', 0
                  ),
                  (
                    '10000000-0000-4000-8000-000000000002', 'QA Suspenso', NULL,
                    'qa.suspenso@example.invalid', '+5562888888888', NULL,
                    '1991-02-03', 'SUSPENSO', 'ANUNCIANTE',
                    '2026-07-10T10:00:00Z', '2026-07-27T10:00:00Z', 0
                  )
                """);
        jdbc.update("""
                INSERT INTO anuncio (
                  id, usuario_id, slug, titulo, status, status_moderacao, categoria,
                  criado_em, atualizado_em, versao
                ) VALUES
                  (
                    '20000000-0000-4000-8000-000000000001',
                    '10000000-0000-4000-8000-000000000001',
                    'qa-usuario-1', 'QA usuario 1', 'PUBLICADO', 'APROVADO', 'MASSAGENS',
                    '2026-07-20T10:00:00Z', '2026-07-20T10:00:00Z', 0
                  ),
                  (
                    '20000000-0000-4000-8000-000000000002',
                    '10000000-0000-4000-8000-000000000001',
                    'qa-usuario-2', 'QA usuario 2', 'PAUSADO', 'APROVADO', 'MASSAGENS',
                    '2026-07-21T10:00:00Z', '2026-07-21T10:00:00Z', 0
                  )
                """);
        jdbc.update("""
                INSERT INTO arquivo_midia (
                  id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                  status_arquivo, criado_em
                ) VALUES (
                  '30000000-0000-4000-8000-000000000001',
                  'R2', 'private-qa', 'qa/documento-usuario', 'image/jpeg', 100,
                  'VALIDADO', '2026-07-22T10:00:00Z'
                )
                """);
        jdbc.update("""
                INSERT INTO documento_usuario (
                  id, usuario_id, arquivo_midia_id, tipo, status, politica_retencao,
                  criado_em, atualizado_em, validado_por, validado_em,
                  envio_id, parte, revisado_por, revisado_em
                ) VALUES (
                  '40000000-0000-4000-8000-000000000001',
                  '10000000-0000-4000-8000-000000000001',
                  '30000000-0000-4000-8000-000000000001',
                  'IDENTIDADE', 'VALIDADO', 'ENQUANTO_HOUVER_ANUNCIO',
                  '2026-07-22T10:00:00Z', '2026-07-22T10:00:00Z',
                  '10000000-0000-4000-8000-000000000001', '2026-07-22T10:00:00Z',
                  '50000000-0000-4000-8000-000000000001', 'UNICO',
                  '10000000-0000-4000-8000-000000000001', '2026-07-22T10:00:00Z'
                )
                """);
    }

    private static void migrate(String container, String network, String credential) throws Exception {
        command(
                Map.of("FLYWAY_PASSWORD", credential),
                "docker", "run", "--pull=never", "--rm", "--network", network,
                "-e", "FLYWAY_PASSWORD",
                "-v", MIGRATIONS + ":/flyway/sql:ro",
                "flyway/flyway:12.10.0",
                "-url=jdbc:postgresql://" + container + ":5432/topsv3_admin_usuarios",
                "-user=topsv3test",
                "-locations=filesystem:/flyway/sql",
                "migrate");
    }

    private static void awaitPostgres(String container, String credential) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (commandIgnoringFailure(
                    Map.of("PGPASSWORD", credential),
                    "docker", "exec", "-e", "PGPASSWORD", container,
                    "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
                    "--dbname", "topsv3_admin_usuarios") == 0) {
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

    private static String command(Map<String, String> environment, String... args) throws Exception {
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
