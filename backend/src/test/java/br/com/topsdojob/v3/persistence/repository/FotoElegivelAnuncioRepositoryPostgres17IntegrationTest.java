package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(
        classes = TopsDoJobBackendApplication.class,
        initializers = FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresInitializer.class)
@EnabledIfEnvironmentVariable(named = "FOTO_ELEGIVEL_POSTGRES17_ENABLED", matches = "true")
public class FotoElegivelAnuncioRepositoryPostgres17IntegrationTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-04T12:00:00Z");
    private static final UUID USUARIO_ID = uuid(1);
    private static final UUID ANUNCIO_ID = uuid(2);
    private static final UUID OUTRO_ANUNCIO_ID = uuid(3);

    @Autowired
    private AnuncioMidiaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedAgregados() {
        jdbc.update("""
                insert into usuario (
                  id, nome, status, tipo_conta, criado_em, atualizado_em, versao
                ) values (?, 'Usuario foto policy', 'ATIVO', 'ANUNCIANTE', ?, ?, 0)
                """, USUARIO_ID, AGORA, AGORA);
        inserirAnuncio(ANUNCIO_ID, "foto-policy-principal");
        inserirAnuncio(OUTRO_ANUNCIO_ID, "foto-policy-outro");
    }

    @AfterAll
    static void cleanup() throws Exception {
        PostgresSupport.stop();
    }

    @Test
    void aplicaPredicadoExatoEOrdenaIdsDeterministicamente() {
        UUID elegivelMaior = midia(
                ANUNCIO_ID, 21, 121, "FOTO", "CAPA", 0, "PUBLICAVEL", "LIVRE", "VALIDADO");
        UUID elegivelMenor = midia(
                ANUNCIO_ID, 11, 111, "FOTO", "GALERIA", 1, "PUBLICAVEL", "RESTRITA_18", "VALIDADO");
        UUID pendente = midia(
                ANUNCIO_ID, 31, 131, "FOTO", "GALERIA", 2, "PENDENTE", null, "PENDENTE");
        UUID ajuste = midia(
                ANUNCIO_ID, 32, 132, "FOTO", "GALERIA", 3, "AJUSTE_SOLICITADO", null, "PENDENTE");
        midia(ANUNCIO_ID, 33, 133, "FOTO", "GALERIA", 4, "REJEITADA", null, "REJEITADO");
        midia(ANUNCIO_ID, 34, 134, "FOTO", "GALERIA", 5, "REMOVIDA", null, "REMOVIDO");
        midia(ANUNCIO_ID, 35, 135, "VIDEO", "GALERIA", 6, "PUBLICAVEL", "RESTRITA_18", "VALIDADO");
        midia(ANUNCIO_ID, 36, 136, "STORY", "STORY", 0, "PUBLICAVEL", "RESTRITA_18", "VALIDADO");
        midia(ANUNCIO_ID, 37, 137, "FOTO", "GALERIA", 7, "PUBLICAVEL", "LIVRE", "PENDENTE");
        midia(OUTRO_ANUNCIO_ID, 39, 139, "FOTO", "CAPA", 0, "PUBLICAVEL", "LIVRE", "VALIDADO");

        assertThat(repository.findFotosAprovadasElegiveisIds(ANUNCIO_ID))
                .containsExactly(elegivelMenor, elegivelMaior);
        assertThat(repository.findFotosAguardandoDecisaoIds(ANUNCIO_ID))
                .containsExactly(pendente, ajuste);
    }

    @Test
    void qualquerHistoricoDocumentalExcluiFotoMesmoRemovidoOuExpurgado() {
        UUID removida = midia(
                ANUNCIO_ID, 41, 141, "FOTO", "CAPA", 0, "PUBLICAVEL", "LIVRE", "VALIDADO");
        UUID expurgada = midia(
                ANUNCIO_ID, 42, 142, "FOTO", "GALERIA", 1, "PUBLICAVEL", "LIVRE", "VALIDADO");
        UUID arquivoRemovido = arquivoId(141);
        UUID arquivoExpurgado = arquivoId(142);
        inserirDocumentoHistorico(arquivoRemovido, "REMOVIDO", AGORA, null, 51);
        inserirDocumentoHistorico(arquivoExpurgado, "EXPURGADO", null, AGORA, 52);

        assertThat(repository.findFotosAprovadasElegiveisIds(ANUNCIO_ID))
                .doesNotContain(removida, expurgada)
                .isEmpty();
        assertThat(repository.existsDocumentoUsuarioHistoricoPorArquivoId(arquivoRemovido)).isTrue();
        assertThat(repository.existsDocumentoUsuarioHistoricoPorArquivoId(arquivoExpurgado)).isTrue();
    }

    @Test
    void referenciaEscalarNaoAnexaEntidadeAntesDoLock() {
        UUID midiaId = midia(
                ANUNCIO_ID, 61, 161, "FOTO", "CAPA", 0, "PENDENTE", null, "PENDENTE");

        var referencia = repository.findReferenciaById(midiaId).orElseThrow();

        assertThat(referencia.getAnuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(referencia.getArquivoMidiaId()).isEqualTo(arquivoId(161));
    }

    @Test
    void planoUsaIndicesSeletivosExistentesSemExigirMigration() {
        midia(ANUNCIO_ID, 71, 171, "FOTO", "CAPA", 0, "PUBLICAVEL", "LIVRE", "VALIDADO");
        jdbc.update("""
                insert into arquivo_midia (
                  id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                  status_arquivo, criado_em
                )
                select md5('foto-policy-arquivo-' || serie)::uuid,
                       'R2', 'foto-policy-massa', 'foto-policy-massa/' || serie,
                       'image/jpeg', 1024,
                       case when serie % 20 = 0 then 'PENDENTE' else 'VALIDADO' end,
                       now()
                from generate_series(1, 4000) serie
                """);
        jdbc.update("""
                insert into anuncio_midia (
                  id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
                  visibilidade_midia, criado_em, atualizado_em
                )
                select md5('foto-policy-vinculo-' || serie)::uuid,
                       ?,
                       md5('foto-policy-arquivo-' || serie)::uuid,
                       'FOTO', 'GALERIA', serie, 'PUBLICAVEL', 'LIVRE', now(), now()
                from generate_series(1, 4000) serie
                """, OUTRO_ANUNCIO_ID);
        jdbc.update("""
                insert into documento_usuario (
                  id, usuario_id, arquivo_midia_id, envio_id, parte, tipo, status,
                  politica_retencao, criado_em, atualizado_em, removido_em
                )
                select md5('foto-policy-documento-' || serie)::uuid,
                       ?,
                       md5('foto-policy-arquivo-' || serie)::uuid,
                       md5('foto-policy-envio-' || serie)::uuid,
                       'UNICO', 'IDENTIDADE', 'REMOVIDO', 'MANUAL', now(), now(), now()
                from generate_series(1, 2000) serie
                """, USUARIO_ID);
        jdbc.execute("analyze anuncio_midia");
        jdbc.execute("analyze arquivo_midia");
        jdbc.execute("analyze documento_usuario");

        List<String> indices = jdbc.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = current_schema()
                  and indexname in (
                    'anuncio_midia_anuncio_idx',
                    'arquivo_midia_status_idx',
                    'documento_usuario_arquivo_idx'
                  )
                order by indexname
                """, String.class);
        List<String> linhasPlano = jdbc.query(
                """
                explain (costs off)
                select am.id
                from anuncio_midia am
                join arquivo_midia arquivo on arquivo.id = am.arquivo_midia_id
                where am.anuncio_id = ?
                  and am.tipo = 'FOTO'
                  and am.finalidade in ('CAPA', 'GALERIA')
                  and am.status = 'PUBLICAVEL'
                  and am.visibilidade_midia is not null
                  and arquivo.status_arquivo = 'VALIDADO'
                  and not exists (
                    select 1
                    from documento_usuario documento
                    where documento.arquivo_midia_id = am.arquivo_midia_id
                  )
                order by am.id
                """,
                (resultSet, rowNum) -> resultSet.getString(1),
                ANUNCIO_ID);
        String plano = String.join("\n", linhasPlano);
        String planoMidias = String.join("\n", jdbc.query(
                """
                explain (costs off)
                select am.id
                from anuncio_midia am
                where am.anuncio_id = ?
                  and am.tipo = 'FOTO'
                  and am.finalidade = 'GALERIA'
                order by am.ordem
                """,
                (resultSet, rowNum) -> resultSet.getString(1),
                ANUNCIO_ID));
        String planoArquivos = String.join("\n", jdbc.query(
                """
                explain (costs off)
                select arquivo.id
                from arquivo_midia arquivo
                where arquivo.status_arquivo = 'PENDENTE'
                """,
                (resultSet, rowNum) -> resultSet.getString(1)));

        assertThat(indices).containsExactly(
                "anuncio_midia_anuncio_idx",
                "arquivo_midia_status_idx",
                "documento_usuario_arquivo_idx");
        assertThat(plano)
                .containsAnyOf("anuncio_midia_anuncio_id_id_uk", "anuncio_midia_anuncio_idx")
                .contains("arquivo_midia_pkey")
                .contains("documento_usuario_arquivo_idx");
        assertThat(planoMidias).contains("anuncio_midia_anuncio_idx");
        assertThat(planoArquivos).contains("arquivo_midia_status_idx");
    }

    private void inserirAnuncio(UUID anuncioId, String slug) {
        jdbc.update("""
                insert into anuncio (
                  id, usuario_id, slug, titulo, status, status_moderacao, categoria,
                  criado_em, atualizado_em, versao
                ) values (?, ?, ?, ?, 'PENDENTE_REVISAO', 'PENDENTE',
                  'ACOMPANHANTE_FEMININA', ?, ?, 0)
                """, anuncioId, USUARIO_ID, slug, "Anuncio " + slug, AGORA, AGORA);
    }

    private UUID midia(
            UUID anuncioId,
            int midiaSuffix,
            int arquivoSuffix,
            String tipo,
            String finalidade,
            int ordem,
            String status,
            String visibilidade,
            String statusArquivo) {
        UUID arquivoId = arquivoId(arquivoSuffix);
        UUID midiaId = uuid(midiaSuffix);
        jdbc.update("""
                insert into arquivo_midia (
                  id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                  status_arquivo, criado_em
                ) values (?, 'R2', 'foto-policy', ?, ?, 1024, ?, ?)
                """, arquivoId, "foto-policy/" + arquivoId, "VIDEO".equals(tipo) ? "video/mp4" : "image/jpeg",
                statusArquivo, AGORA);
        jdbc.update("""
                insert into anuncio_midia (
                  id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
                  visibilidade_midia, criado_em, atualizado_em
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, midiaId, anuncioId, arquivoId, tipo, finalidade, ordem, status,
                visibilidade, AGORA, AGORA);
        return midiaId;
    }

    private void inserirDocumentoHistorico(
            UUID arquivoId,
            String status,
            OffsetDateTime removidoEm,
            OffsetDateTime expurgadoEm,
            int suffix) {
        UUID documentoId = uuid(suffix);
        jdbc.update("""
                insert into documento_usuario (
                  id, usuario_id, arquivo_midia_id, envio_id, parte, tipo, status,
                  politica_retencao, criado_em, atualizado_em, removido_em, expurgado_em
                ) values (?, ?, ?, ?, 'UNICO', 'IDENTIDADE', ?, 'MANUAL', ?, ?, ?, ?)
                """, documentoId, USUARIO_ID, arquivoId, documentoId, status,
                AGORA, AGORA, removidoEm, expurgadoEm);
    }

    private static UUID arquivoId(int suffix) {
        return UUID.fromString(String.format("20000000-0000-4000-8000-%012d", suffix));
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString(String.format("10000000-0000-4000-8000-%012d", suffix));
    }

    public static final class PostgresInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                PostgresSupport.start();
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "foto-elegivel-postgres17",
                        Map.of(
                                "spring.datasource.url", PostgresSupport.jdbcUrl(),
                                "spring.datasource.username", "topsv3test",
                                "spring.datasource.password", PostgresSupport.credential(),
                                "spring.flyway.enabled", "false",
                                "spring.jpa.hibernate.ddl-auto", "validate")));
            } catch (Exception exception) {
                throw new IllegalStateException("falha ao preparar PostgreSQL 17 para foto elegivel", exception);
            }
        }
    }

    public static final class PostgresSupport {

        private static final Path MIGRATIONS = Path.of(
                "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
        private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        private static final String NETWORK = "topsv3-foto-policy-" + SUFFIX + "-net";
        private static final String CONTAINER = "topsv3-foto-policy-" + SUFFIX + "-pg17";
        private static final String CREDENTIAL = UUID.randomUUID().toString() + UUID.randomUUID();
        private static int port;
        private static boolean started;

        private PostgresSupport() {
        }

        public static synchronized void start() throws Exception {
            if (started) return;
            command("docker", "network", "create", NETWORK);
            try {
                command(
                        Map.of("POSTGRES_PASSWORD", CREDENTIAL),
                        "docker", "run", "--pull=never", "-d", "--name", CONTAINER,
                        "--network", NETWORK,
                        "-p", "127.0.0.1::5432",
                        "-e", "POSTGRES_DB=topsv3_foto_policy",
                        "-e", "POSTGRES_USER=topsv3test",
                        "-e", "POSTGRES_PASSWORD",
                        "postgres:17-alpine");
                awaitPostgres();
                command(
                        Map.of("FLYWAY_PASSWORD", CREDENTIAL),
                        "docker", "run", "--pull=never", "--rm", "--network", NETWORK,
                        "-e", "FLYWAY_PASSWORD",
                        "-v", MIGRATIONS + ":/flyway/sql:ro",
                        "flyway/flyway:12.10.0",
                        "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_foto_policy",
                        "-user=topsv3test",
                        "-locations=filesystem:/flyway/sql",
                        "migrate");
                port = mappedPort();
                started = true;
            } catch (Exception exception) {
                stop();
                throw exception;
            }
        }

        public static synchronized void stop() throws Exception {
            commandIgnoringFailure("docker", "rm", "-f", CONTAINER);
            commandIgnoringFailure("docker", "network", "rm", NETWORK);
            started = false;
        }

        public static String jdbcUrl() {
            return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_foto_policy";
        }

        public static String credential() {
            return CREDENTIAL;
        }

        private static void awaitPostgres() throws Exception {
            for (int attempt = 0; attempt < 60; attempt++) {
                if (commandIgnoringFailure(
                        Map.of("PGPASSWORD", CREDENTIAL),
                        "docker", "exec", "-e", "PGPASSWORD", CONTAINER,
                        "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
                        "--dbname", "topsv3_foto_policy") == 0) {
                    return;
                }
                Thread.sleep(500L);
            }
            throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
        }

        private static int mappedPort() throws Exception {
            String output = command("docker", "port", CONTAINER, "5432/tcp").trim();
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
}
