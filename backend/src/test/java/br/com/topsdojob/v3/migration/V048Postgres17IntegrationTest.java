package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "V048_POSTGRES17_ENABLED", matches = "true")
class V048Postgres17IntegrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
    private static final UUID OWNER_A = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID OWNER_B = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID ANUNCIO_A = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID ANUNCIO_B = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID ANUNCIO_C = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID ANUNCIO_D = UUID.fromString("20000000-0000-4000-8000-000000000004");
    private static final UUID ANUNCIO_E = UUID.fromString("20000000-0000-4000-8000-000000000005");
    private static final UUID ARQUIVO_HISTORICO = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID ARQUIVO_STORY = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID ARQUIVO_STORY_C1 = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID ARQUIVO_STORY_C2 = UUID.fromString("30000000-0000-4000-8000-000000000004");
    private static final UUID ARQUIVO_STORY_D1 = UUID.fromString("30000000-0000-4000-8000-000000000005");
    private static final UUID ARQUIVO_STORY_B3 = UUID.fromString("30000000-0000-4000-8000-000000000006");
    private static final UUID ARQUIVO_STORY_E1 = UUID.fromString("30000000-0000-4000-8000-000000000007");
    private static final UUID ARQUIVO_STORY_E2 = UUID.fromString("30000000-0000-4000-8000-000000000008");
    private static final UUID ARQUIVO_DIRETO_1 = UUID.fromString("30000000-0000-4000-8000-000000000009");
    private static final UUID ARQUIVO_DIRETO_2 = UUID.fromString("30000000-0000-4000-8000-000000000010");
    private static final UUID ARQUIVO_DIRETO_3 = UUID.fromString("30000000-0000-4000-8000-000000000011");
    private static final UUID ARQUIVO_DIRETO_4 = UUID.fromString("30000000-0000-4000-8000-000000000012");
    private static final UUID ARQUIVO_DIRETO_5 = UUID.fromString("30000000-0000-4000-8000-000000000013");
    private static final UUID ARQUIVO_DIRETO_6 = UUID.fromString("30000000-0000-4000-8000-000000000014");
    private static final UUID ARQUIVO_DIRETO_7 = UUID.fromString("30000000-0000-4000-8000-000000000015");
    private static final UUID MIDIA_HISTORICA = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID MIDIA_STORY = UUID.fromString("40000000-0000-4000-8000-000000000002");
    private static final UUID MIDIA_STORY_C1 = UUID.fromString("40000000-0000-4000-8000-000000000003");
    private static final UUID MIDIA_STORY_C2 = UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final UUID MIDIA_STORY_D1 = UUID.fromString("40000000-0000-4000-8000-000000000005");
    private static final UUID MIDIA_STORY_B3 = UUID.fromString("40000000-0000-4000-8000-000000000006");
    private static final UUID MIDIA_STORY_E1 = UUID.fromString("40000000-0000-4000-8000-000000000007");
    private static final UUID MIDIA_STORY_E2 = UUID.fromString("40000000-0000-4000-8000-000000000008");
    private static final UUID STORY_HISTORICO = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID BENEFICIO = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID ATIVACAO_A1 = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID ATIVACAO_A2 = UUID.fromString("70000000-0000-4000-8000-000000000002");
    private static final UUID ATIVACAO_B = UUID.fromString("70000000-0000-4000-8000-000000000003");
    private static final UUID ATIVACAO_C1 = UUID.fromString("70000000-0000-4000-8000-000000000004");
    private static final UUID ATIVACAO_C2 = UUID.fromString("70000000-0000-4000-8000-000000000005");
    private static final UUID ATIVACAO_D = UUID.fromString("70000000-0000-4000-8000-000000000006");
    private static final UUID ATIVACAO_A3 = UUID.fromString("70000000-0000-4000-8000-000000000007");
    private static final UUID ATIVACAO_B2 = UUID.fromString("70000000-0000-4000-8000-000000000008");
    private static final UUID ATIVACAO_E = UUID.fromString("70000000-0000-4000-8000-000000000009");
    private static final UUID ATIVACAO_C3 = UUID.fromString("70000000-0000-4000-8000-000000000010");
    private static final UUID ATIVACAO_C4 = UUID.fromString("70000000-0000-4000-8000-000000000011");
    private static final UUID ATIVACAO_D2 = UUID.fromString("70000000-0000-4000-8000-000000000012");
    private static final UUID ATIVACAO_D3 = UUID.fromString("70000000-0000-4000-8000-000000000013");
    private static final UUID ATIVACAO_B3 = UUID.fromString("70000000-0000-4000-8000-000000000014");
    private static final UUID ATIVACAO_B4 = UUID.fromString("70000000-0000-4000-8000-000000000015");
    private static final UUID ATIVACAO_E2 = UUID.fromString("70000000-0000-4000-8000-000000000016");
    private static final UUID ATIVACAO_E3 = UUID.fromString("70000000-0000-4000-8000-000000000017");
    private static final UUID ATIVACAO_DIRETA_1 = UUID.fromString("70000000-0000-4000-8000-000000000018");
    private static final UUID ATIVACAO_DIRETA_2 = UUID.fromString("70000000-0000-4000-8000-000000000019");
    private static final UUID ATIVACAO_DIRETA_3 = UUID.fromString("70000000-0000-4000-8000-000000000020");
    private static final UUID ATIVACAO_DIRETA_4 = UUID.fromString("70000000-0000-4000-8000-000000000021");
    private static final UUID ATIVACAO_DIRETA_5 = UUID.fromString("70000000-0000-4000-8000-000000000022");
    private static final UUID ATIVACAO_DIRETA_6 = UUID.fromString("70000000-0000-4000-8000-000000000023");
    private static final UUID ATIVACAO_DIRETA_7 = UUID.fromString("70000000-0000-4000-8000-000000000024");
    private static final UUID STORY_E = UUID.fromString("50000000-0000-4000-8000-000000000002");

    @Test
    void upgradePreservaHistoricoEProtegeOwnershipAtivacaoEConcorrencia() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String network = "topsv3-v048-" + suffix + "-net";
        String container = "topsv3-v048-" + suffix + "-pg17";
        String credential = UUID.randomUUID().toString() + UUID.randomUUID();

        command("docker", "network", "create", network);
        command(
                Map.of("POSTGRES_PASSWORD", credential),
                "docker", "run", "--pull=never", "-d", "--name", container,
                "--network", network,
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=topsv3_v048",
                "-e", "POSTGRES_USER=topsv3test",
                "-e", "POSTGRES_PASSWORD",
                "postgres:17-alpine");
        try {
            awaitPostgres(container, credential);
            flyway(container, network, credential, "-target=47", "migrate");
            int port = mappedPort(container);
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_v048",
                    "topsv3test",
                    credential);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedV047(jdbc);

            flyway(container, network, credential, "migrate");
            flyway(container, network, credential, "validate");
            flyway(container, network, credential, "migrate");

            assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class))
                    .isBetween(170000, 179999);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE version::integer = 48 AND success",
                    Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE version::integer = 49 AND success",
                    Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE version::integer = 50 AND success",
                    Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success",
                    Long.class)).isEqualTo(50L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM story_configuracao_comercial",
                    Long.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'story_configuracao_comercial'
                      AND column_name ILIKE '%duracao%'
                    """, Long.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM story_anuncio
                    WHERE id = ?
                      AND anuncio_midia_id = ?
                      AND modo_conteudo IS NULL
                      AND anuncio_id IS NULL
                      AND ativacao_beneficio_id IS NULL
                      AND idempotency_key IS NULL
                      AND request_fingerprint IS NULL
                    """, Long.class, STORY_HISTORICO, MIDIA_HISTORICA)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM story_selecao_administrativa WHERE anuncio_id = ? AND ativa",
                    Long.class, ANUNCIO_A)).isEqualTo(1L);

            assertThat(insertStory(
                    dataSource, ANUNCIO_A, null, "ANUNCIO", ATIVACAO_A1, OWNER_A, "story-a-1"))
                    .isTrue();
            assertThat(insertStory(
                    dataSource, ANUNCIO_A, null, "ANUNCIO", ATIVACAO_A2, OWNER_A, "story-a-2-ativa"))
                    .isFalse();
            jdbc.update("UPDATE story_anuncio SET status = 'EXPIRADO' WHERE ativacao_beneficio_id = ?", ATIVACAO_A1);
            assertThat(insertStory(
                    dataSource, ANUNCIO_A, null, "ANUNCIO", ATIVACAO_A1, OWNER_A, "story-a-1-reuso"))
                    .isFalse();
            assertThat(insertStory(
                    dataSource, ANUNCIO_A, null, "ANUNCIO", ATIVACAO_A2, OWNER_A, "story-a-2"))
                    .isTrue();

            assertThat(concurrently(
                    () -> insertStory(dataSource, ANUNCIO_C, null, "ANUNCIO", ATIVACAO_C1, OWNER_B, "story-c-1"),
                    () -> insertStory(dataSource, ANUNCIO_C, null, "ANUNCIO", ATIVACAO_C2, OWNER_B, "story-c-2")))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(concurrently(
                    () -> insertStory(dataSource, ANUNCIO_B, MIDIA_STORY, "MIDIA_UPLOAD", ATIVACAO_B, OWNER_A, "story-b"),
                    () -> insertStory(dataSource, ANUNCIO_D, null, "ANUNCIO", ATIVACAO_D, OWNER_B, "story-d")))
                    .containsExactly(true, true);

            jdbc.update("UPDATE story_anuncio SET status = 'EXPIRADO' WHERE anuncio_id IN (?, ?)",
                    ANUNCIO_A, ANUNCIO_B);
            assertThat(insertStory(
                    dataSource, ANUNCIO_A, null, "ANUNCIO", ATIVACAO_A3, OWNER_A, "chave-compartilhada"))
                    .isTrue();
            assertThat(insertStory(
                    dataSource, ANUNCIO_B, null, "ANUNCIO", ATIVACAO_B2, OWNER_A,
                    "chave-compartilhada"))
                    .isTrue();

            assertCleanupEsperaRetryCommitado(dataSource);

            assertThat(concurrently(
                    () -> insertStoryDireto(
                            dataSource, ARQUIVO_DIRETO_1, ATIVACAO_DIRETA_1,
                            OWNER_B, "story-direto-1", "1".repeat(64)),
                    () -> insertStoryDireto(
                            dataSource, ARQUIVO_DIRETO_2, ATIVACAO_DIRETA_2,
                            OWNER_B, "story-direto-2", "2".repeat(64))))
                    .containsExactly(true, true);

            jdbc.update("UPDATE story_anuncio SET status = 'EXPIRADO' WHERE anuncio_id = ?", ANUNCIO_D);
            assertThat(concurrently(
                    () -> insertStory(
                            dataSource, ANUNCIO_D, null, "ANUNCIO",
                            ATIVACAO_D2, OWNER_B, "story-d-anuncio"),
                    () -> insertStoryDireto(
                            dataSource, ARQUIVO_DIRETO_3, ATIVACAO_DIRETA_3,
                            OWNER_B, "story-d-midia", "3".repeat(64))))
                    .containsExactly(true, true);

            assertThat(concurrently(
                    () -> insertStoryDireto(
                            dataSource, ARQUIVO_DIRETO_4, ATIVACAO_DIRETA_4,
                            OWNER_A, "retry-concorrente", "4".repeat(64)),
                    () -> insertStoryDireto(
                            dataSource, ARQUIVO_DIRETO_5, ATIVACAO_DIRETA_5,
                            OWNER_A, "retry-concorrente", "4".repeat(64))))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM story_anuncio WHERE anuncio_id IS NULL AND idempotency_key = ?",
                    Long.class, "retry-concorrente"))
                    .isEqualTo(1L);

            assertThat(concurrently(
                    () -> insertStoryDireto(
                            dataSource, ARQUIVO_DIRETO_6, ATIVACAO_DIRETA_6,
                            OWNER_B, "chave-arquivo-divergente", "e".repeat(64)),
                    () -> insertStoryDireto(
                            dataSource, ARQUIVO_DIRETO_7, ATIVACAO_DIRETA_7,
                            OWNER_B, "chave-arquivo-divergente", "f".repeat(64))))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM story_anuncio WHERE anuncio_id IS NULL AND idempotency_key = ?",
                    Long.class, "chave-arquivo-divergente"))
                    .isEqualTo(1L);

            assertThat(insertStory(
                    dataSource, ANUNCIO_B, null, "ANUNCIO", ATIVACAO_D, OWNER_A, "ownership-invalido"))
                    .isFalse();
            assertThat(insertStoryWithFingerprint(
                    dataSource, ANUNCIO_B, null, "MIDIA_UPLOAD", ATIVACAO_B, OWNER_A,
                    "forma-invalida", "A".repeat(64)))
                    .isFalse();
        } finally {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }
    }

    private static void seedV047(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
                VALUES
                  (?, 'Owner A', 'ATIVO', 'ANUNCIANTE', now(), now(), 0),
                  (?, 'Owner B', 'ATIVO', 'ANUNCIANTE', now(), now(), 0)
                """, OWNER_A, OWNER_B);
        for (UUID anuncioId : List.of(ANUNCIO_A, ANUNCIO_B)) {
            insertAnuncio(jdbc, anuncioId, OWNER_A);
        }
        for (UUID anuncioId : List.of(ANUNCIO_C, ANUNCIO_D, ANUNCIO_E)) {
            insertAnuncio(jdbc, anuncioId, OWNER_B);
        }
        jdbc.update("""
                INSERT INTO arquivo_midia (
                  id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                  largura, altura, sha256, status_arquivo, criado_em
                ) VALUES
                  (?, 'R2', 'privado', 'historico.jpg', 'image/jpeg', 10, 800, 1200, ?, 'VALIDADO', now()),
                  (?, 'R2', 'privado', 'story.mp4', 'video/mp4', 20, 720, 1280, ?, 'VALIDADO', now())
                """, ARQUIVO_HISTORICO, "a".repeat(64), ARQUIVO_STORY, "b".repeat(64));
        jdbc.update("""
                INSERT INTO anuncio_midia (
                  id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
                  visibilidade_midia, criado_em, atualizado_em
                ) VALUES
                  (?, ?, ?, 'FOTO', 'GALERIA', 0, 'PUBLICAVEL', 'LIVRE', now(), now()),
                  (?, ?, ?, 'STORY', 'STORY', 0, 'PUBLICAVEL', 'RESTRITA_18', now(), now())
                """, MIDIA_HISTORICA, ANUNCIO_A, ARQUIVO_HISTORICO,
                MIDIA_STORY, ANUNCIO_B, ARQUIVO_STORY);
        insertStoryMidia(jdbc, ARQUIVO_STORY_C1, MIDIA_STORY_C1, ANUNCIO_C, "story-c-1.jpg", "c", 0);
        insertStoryMidia(jdbc, ARQUIVO_STORY_C2, MIDIA_STORY_C2, ANUNCIO_C, "story-c-2.jpg", "d", 1);
        insertStoryMidia(jdbc, ARQUIVO_STORY_D1, MIDIA_STORY_D1, ANUNCIO_D, "story-d-1.jpg", "e", 0);
        insertStoryMidia(jdbc, ARQUIVO_STORY_B3, MIDIA_STORY_B3, ANUNCIO_B, "story-b-3.jpg", "f", 1);
        insertStoryMidia(jdbc, ARQUIVO_STORY_E1, MIDIA_STORY_E1, ANUNCIO_E, "story-e-1.jpg", "1", 0);
        insertStoryMidia(jdbc, ARQUIVO_STORY_E2, MIDIA_STORY_E2, ANUNCIO_E, "story-e-2.jpg", "2", 1);
        List<UUID> arquivosDiretos = List.of(
                ARQUIVO_DIRETO_1, ARQUIVO_DIRETO_2, ARQUIVO_DIRETO_3,
                ARQUIVO_DIRETO_4, ARQUIVO_DIRETO_5, ARQUIVO_DIRETO_6,
                ARQUIVO_DIRETO_7);
        for (int index = 0; index < arquivosDiretos.size(); index++) {
            insertArquivoDireto(
                    jdbc,
                    arquivosDiretos.get(index),
                    "story-direto-" + (index + 1) + ".jpg",
                    Integer.toHexString(index + 3));
        }
        jdbc.update("""
                INSERT INTO story_anuncio (
                  id, anuncio_midia_id, status, inicio_em, fim_em, ordem,
                  criado_por, criado_em, atualizado_em
                ) VALUES (?, ?, 'PUBLICADO', now() - interval '1 hour',
                          now() + interval '1 hour', 0, ?, now(), now())
                """, STORY_HISTORICO, MIDIA_HISTORICA, OWNER_A);
        jdbc.update("""
                INSERT INTO story_selecao_administrativa (
                  id, anuncio_id, ativa, ativado_por, ativado_em, expira_em,
                  idempotency_key, criado_em, atualizado_em, versao
                ) VALUES (1, ?, true, ?, now() - interval '1 hour', now() + interval '23 hours',
                          'admin-historico', now(), now(), 0)
                """, ANUNCIO_A, OWNER_A);
        jdbc.update("""
                INSERT INTO beneficio_premium (
                  id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
                  criado_em, atualizado_em
                ) VALUES (?, 'STORIES', 'Stories', 'Story por 24 horas',
                          'ANUNCIO', false, true, now(), now())
                """, BENEFICIO);
        insertAtivacao(jdbc, ATIVACAO_A1, ANUNCIO_A, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_A2, ANUNCIO_A, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_B, ANUNCIO_B, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_C1, ANUNCIO_C, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_C2, ANUNCIO_C, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_D, ANUNCIO_D, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_A3, ANUNCIO_A, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_B2, ANUNCIO_B, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_E, ANUNCIO_E, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_C3, ANUNCIO_C, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_C4, ANUNCIO_C, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_D2, ANUNCIO_D, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_D3, ANUNCIO_D, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_B3, ANUNCIO_B, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_B4, ANUNCIO_B, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_E2, ANUNCIO_E, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_E3, ANUNCIO_E, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_DIRETA_1, null, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_DIRETA_2, null, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_DIRETA_3, null, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_DIRETA_4, null, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_DIRETA_5, null, OWNER_A);
        insertAtivacao(jdbc, ATIVACAO_DIRETA_6, null, OWNER_B);
        insertAtivacao(jdbc, ATIVACAO_DIRETA_7, null, OWNER_B);
    }

    private static void assertCleanupEsperaRetryCommitado(
            DriverManagerDataSource dataSource) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch storyInserido = new CountDownLatch(1);
        CountDownLatch liberarCommit = new CountDownLatch(1);
        CountDownLatch cleanupTentandoLock = new CountDownLatch(1);
        try {
            var retry = executor.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    lockAnuncio(connection, ANUNCIO_E);
                    insertStoryNaTransacao(
                            connection, STORY_E, ANUNCIO_E, ATIVACAO_E, OWNER_B, "retry-cleanup");
                    storyInserido.countDown();
                    liberarCommit.await();
                    connection.commit();
                    return true;
                }
            });
            storyInserido.await();
            var cleanup = executor.submit(() -> {
                cleanupTentandoLock.countDown();
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    lockAnuncio(connection, ANUNCIO_E);
                    boolean orfao;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT count(*) = 0 FROM story_anuncio WHERE id = ?")) {
                        statement.setObject(1, STORY_E);
                        try (var result = statement.executeQuery()) {
                            result.next();
                            orfao = result.getBoolean(1);
                        }
                    }
                    connection.commit();
                    return orfao;
                }
            });
            cleanupTentandoLock.await();
            Thread.sleep(200L);
            assertThat(cleanup.isDone()).isFalse();
            liberarCommit.countDown();
            assertThat(retry.get()).isTrue();
            assertThat(cleanup.get()).isFalse();
        } finally {
            liberarCommit.countDown();
            executor.shutdownNow();
        }
    }

    private static void lockAnuncio(Connection connection, UUID anuncioId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM anuncio WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, anuncioId);
            statement.executeQuery().close();
        }
    }

    private static void insertStoryNaTransacao(
            Connection connection,
            UUID storyId,
            UUID anuncioId,
            UUID ativacaoId,
            UUID ownerId,
            String key) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO story_anuncio (
                  id, anuncio_midia_id, status, inicio_em, fim_em, ordem,
                  criado_por, criado_em, atualizado_em, anuncio_id, modo_conteudo,
                  ativacao_beneficio_id, idempotency_key, request_fingerprint
                ) VALUES (?, NULL, 'PUBLICADO', now(), now() + interval '24 hours', 0,
                          ?, now(), now(), ?, 'ANUNCIO', ?, ?, ?)
                """)) {
            Object[] values = {storyId, ownerId, anuncioId, ativacaoId, key, "d".repeat(64)};
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static void insertAnuncio(JdbcTemplate jdbc, UUID anuncioId, UUID ownerId) {
        jdbc.update("""
                INSERT INTO anuncio (
                  id, usuario_id, slug, titulo, status, status_moderacao,
                  categoria, criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 'Anuncio QA', 'PUBLICADO', 'APROVADO',
                          'ACOMPANHANTE_FEMININA', now(), now(), 0)
                """, anuncioId, ownerId, "qa-" + anuncioId);
    }

    private static void insertStoryMidia(
            JdbcTemplate jdbc,
            UUID arquivoId,
            UUID midiaId,
            UUID anuncioId,
            String objectKey,
            String hashDigit,
            int ordem) {
        jdbc.update("""
                INSERT INTO arquivo_midia (
                  id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                  largura, altura, sha256, status_arquivo, criado_em
                ) VALUES (?, 'R2', 'privado', ?, 'image/jpeg', 10, 720, 1280, ?, 'VALIDADO', now())
                """, arquivoId, objectKey, hashDigit.repeat(64));
        jdbc.update("""
                INSERT INTO anuncio_midia (
                  id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
                  visibilidade_midia, criado_em, atualizado_em
                ) VALUES (?, ?, ?, 'STORY', 'STORY', ?, 'PUBLICAVEL', 'RESTRITA_18', now(), now())
                """, midiaId, anuncioId, arquivoId, ordem);
    }

    private static void insertArquivoDireto(
            JdbcTemplate jdbc,
            UUID arquivoId,
            String objectKey,
            String hashDigit) {
        jdbc.update("""
                INSERT INTO arquivo_midia (
                  id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                  largura, altura, sha256, status_arquivo, criado_em
                ) VALUES (?, 'R2', 'privado', ?, 'image/jpeg', 10, 720, 1280, ?, 'VALIDADO', now())
                """, arquivoId, objectKey, hashDigit.repeat(64));
    }

    private static void insertAtivacao(
            JdbcTemplate jdbc,
            UUID id,
            UUID anuncioId,
            UUID ownerId) {
        jdbc.update("""
                INSERT INTO ativacao_beneficio (
                  id, beneficio_id, usuario_id, anuncio_id, origem,
                  inicio_em, fim_em, status, custo_creditos_snapshot,
                  idempotency_key, criado_em
                ) VALUES (?, ?, ?, ?, 'CREDITO', now() - interval '1 hour',
                          now() + interval '48 hours', 'ATIVA', 5, ?, now())
                """, id, BENEFICIO, ownerId, anuncioId, "ativacao-" + id);
    }

    private static boolean insertStory(
            DriverManagerDataSource dataSource,
            UUID anuncioId,
            UUID midiaId,
            String modo,
            UUID ativacaoId,
            UUID ownerId,
            String key) throws Exception {
        return insertStoryWithFingerprint(
                dataSource, anuncioId, midiaId, modo, ativacaoId, ownerId, key, "c".repeat(64));
    }

    private static boolean insertStoryWithFingerprint(
            DriverManagerDataSource dataSource,
            UUID anuncioId,
            UUID midiaId,
            String modo,
            UUID ativacaoId,
            UUID ownerId,
            String key,
            String fingerprint) throws Exception {
        return insertIgnoringConstraint(dataSource, """
                INSERT INTO story_anuncio (
                  id, anuncio_midia_id, status, inicio_em, fim_em, ordem,
                  criado_por, criado_em, atualizado_em, anuncio_id, modo_conteudo,
                  ativacao_beneficio_id, idempotency_key, request_fingerprint
                ) VALUES (?, ?, 'PUBLICADO', now(), now() + interval '24 hours', 0,
                          ?, now(), now(), ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), midiaId, ownerId, anuncioId, modo, ativacaoId, key, fingerprint);
    }

    private static boolean insertStoryDireto(
            DriverManagerDataSource dataSource,
            UUID arquivoId,
            UUID ativacaoId,
            UUID ownerId,
            String key,
            String fingerprint) throws Exception {
        return insertIgnoringConstraint(dataSource, """
                INSERT INTO story_anuncio (
                  id, anuncio_midia_id, arquivo_midia_id, status, inicio_em, fim_em, ordem,
                  criado_por, criado_em, atualizado_em, anuncio_id, modo_conteudo,
                  ativacao_beneficio_id, idempotency_key, request_fingerprint
                ) VALUES (?, NULL, ?, 'PUBLICADO', now(), now() + interval '24 hours', 0,
                          ?, now(), now(), NULL, 'MIDIA_UPLOAD', ?, ?, ?)
                """, UUID.randomUUID(), arquivoId, ownerId, ativacaoId, key, fingerprint);
    }

    private static boolean insertIgnoringConstraint(
            DriverManagerDataSource dataSource,
            String sql,
            Object... values) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) {
                    statement.setObject(index + 1, values[index]);
                }
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                if (List.of("23503", "23505", "23514").contains(exception.getSQLState())) {
                    return false;
                }
                throw exception;
            }
        }
    }

    private static List<Boolean> concurrently(
            Callable<Boolean> first,
            Callable<Boolean> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> wrapFirst = () -> {
                ready.countDown();
                start.await();
                return first.call();
            };
            Callable<Boolean> wrapSecond = () -> {
                ready.countDown();
                start.await();
                return second.call();
            };
            var firstResult = executor.submit(wrapFirst);
            var secondResult = executor.submit(wrapSecond);
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void flyway(
            String container,
            String network,
            String credential,
            String... operation) throws Exception {
        String[] base = {
                "docker", "run", "--pull=never", "--rm", "--network", network,
                "-e", "FLYWAY_PASSWORD",
                "-v", MIGRATIONS + ":/flyway/sql:ro",
                "flyway/flyway:12.10.0",
                "-url=jdbc:postgresql://" + container + ":5432/topsv3_v048",
                "-user=topsv3test",
                "-locations=filesystem:/flyway/sql"
        };
        String[] args = new String[base.length + operation.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(operation, 0, args, base.length, operation.length);
        command(Map.of("FLYWAY_PASSWORD", credential), args);
    }

    private static void awaitPostgres(String container, String credential) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (commandIgnoringFailure(
                    Map.of("PGPASSWORD", credential),
                    "docker", "exec", "-e", "PGPASSWORD", container,
                    "pg_isready", "--host", "127.0.0.1",
                    "--username", "topsv3test", "--dbname", "topsv3_v048") == 0) {
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
