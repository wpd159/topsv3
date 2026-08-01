package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(
        classes = TopsDoJobBackendApplication.class,
        initializers = AnuncioRepositoryRelacionadosPostgres17IntegrationTest.PostgresInitializer.class)
@EnabledIfEnvironmentVariable(
        named = "ANUNCIOS_RELACIONADOS_POSTGRES17_ENABLED",
        matches = "true")
class AnuncioRepositoryRelacionadosPostgres17IntegrationTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-07-31T15:00:00Z");
    private static final UUID ESTADO_GO = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID CIDADE_LOCAL = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID CIDADE_HOMONIMA = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID BENEFICIO = UUID.fromString("f3000000-0000-4000-8000-000000000003");

    @Autowired
    private AnuncioRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedCatalogo() {
        jdbc.update("""
                insert into estado (id, uf, nome, nome_normalizado, criado_em)
                values (?, 'GO', 'Goias', 'goias', now())
                """, ESTADO_GO);
        jdbc.update("""
                insert into cidade (id, estado_id, nome, nome_normalizado, slug, criado_em)
                values
                  (?, ?, 'Cidade QA', 'cidade qa', 'cidade-qa', now()),
                  (?, ?, 'Cidade QA', 'cidade qa dois', 'cidade-qa-dois', now())
                """, CIDADE_LOCAL, ESTADO_GO, CIDADE_HOMONIMA, ESTADO_GO);
        jdbc.update("""
                insert into beneficio_premium (
                  id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
                  ordem_exibicao, criado_em, atualizado_em
                ) values (?, 'ANUNCIO_TOPO', 'Topo QA', 'Beneficio QA', 'ANUNCIO', true, true, 1, now(), now())
                on conflict (codigo) do update set atualizado_em = excluded.atualizado_em
                """, BENEFICIO);
    }

    @AfterAll
    static void cleanup() throws Exception {
        PostgresSupport.stop();
    }

    @Test
    void selecionaSomenteCompraOuCreditoComDebitoNaCidadeExata() {
        UUID atualId = UUID.randomUUID();
        UUID compraLocal = candidato("compra-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID creditoLocal = candidato("credito-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID creditoSemDebito = candidato("credito-sem-debito", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID cortesia = candidato("cortesia-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID outraCategoria = candidato("outra-categoria", "ACOMPANHANTE_MASCULINO", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID pausado = candidato("pausado-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PAUSADO", true);
        UUID usuarioInativo = candidato("usuario-inativo", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "DESATIVADO", "PUBLICADO", true);
        UUID semMidia = candidato("sem-midia", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", false);
        UUID outraCidade = candidato("outra-cidade", "ACOMPANHANTE_FEMININA", CIDADE_HOMONIMA, "ATIVO", "PUBLICADO", true);
        ativar(compraLocal, "COMPRA", true);
        ativar(creditoLocal, "CREDITO", true);
        ativar(creditoSemDebito, "CREDITO", false);
        ativar(cortesia, "CORTESIA", false);
        ativar(outraCategoria, "COMPRA", true);
        ativar(pausado, "COMPRA", true);
        ativar(usuarioInativo, "COMPRA", true);
        ativar(semMidia, "COMPRA", true);
        ativar(outraCidade, "COMPRA", true);

        var locais = repository.findRelacionadosPagos(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA,
                PageRequest.of(0, 6));
        var fallback = repository.findRelacionadosPagos(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                false,
                AGORA,
                PageRequest.of(0, 6));

        assertThat(locais).extracting(item -> item.getId())
                .containsExactlyInAnyOrder(compraLocal, creditoLocal);
        assertThat(fallback).extracting(item -> item.getId()).containsExactly(outraCidade);
    }

    @Test
    void limitaNoBancoEOrdenaDeFormaDeterministica() {
        UUID atualId = UUID.randomUUID();
        for (int indice = 0; indice < 8; indice++) {
            UUID anuncioId = candidato(
                    "limite-" + indice,
                    "ACOMPANHANTE_FEMININA",
                    CIDADE_LOCAL,
                    "ATIVO",
                    "PUBLICADO",
                    true);
            ativar(anuncioId, "COMPRA", true);
        }

        var primeira = repository.findRelacionadosPagos(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA,
                PageRequest.of(0, 6));
        var segunda = repository.findRelacionadosPagos(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA,
                PageRequest.of(0, 6));

        assertThat(primeira).hasSize(6);
        assertThat(segunda).extracting(item -> item.getId())
                .containsExactlyElementsOf(primeira.stream().map(item -> item.getId()).toList());
    }

    private UUID candidato(
            String slug,
            String categoria,
            UUID cidadeId,
            String statusUsuario,
            String statusAnuncio,
            boolean comMidia) {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        jdbc.update("""
                insert into usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, desativado_em, versao)
                values (?, ?, ?, 'ANUNCIANTE', now(), now(), ?, 0)
                """, usuarioId, "QA " + slug, statusUsuario,
                "DESATIVADO".equals(statusUsuario) ? AGORA.minusDays(1) : null);
        jdbc.update("""
                insert into anuncio (
                  id, usuario_id, slug, titulo, status, status_moderacao, categoria, preco,
                  publicado_em, ultima_publicacao_em, criado_em, atualizado_em, versao
                ) values (?, ?, ?, ?, ?, 'APROVADO', ?, 100, ?, ?, ?, ?, 0)
                """, anuncioId, usuarioId, slug, "QA " + slug, statusAnuncio, categoria,
                AGORA.minusDays(1), AGORA.minusDays(1), AGORA.minusDays(2), AGORA.minusDays(1));
        jdbc.update("""
                insert into anuncio_localizacao (
                  anuncio_id, estado_id, cidade_id, criado_em, atualizado_em
                ) values (?, ?, ?, now(), now())
                """, anuncioId, ESTADO_GO, cidadeId);
        jdbc.update("""
                insert into documento_busca_anuncio (
                  anuncio_id, texto_busca, estado_id, cidade_id, categoria, preco,
                  status_publicacao, tem_midia_valida, atualizado_em
                ) values (?, ?, ?, ?, ?, 100, 'PUBLICAVEL', ?, now())
                """, anuncioId, slug, ESTADO_GO, cidadeId, categoria, comMidia);
        if (comMidia) {
            UUID arquivoId = UUID.randomUUID();
            jdbc.update("""
                    insert into arquivo_midia (
                      id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                      status_arquivo, criado_em
                    ) values (?, 'R2', 'qa', ?, 'image/jpeg', 1000, 'VALIDADO', now())
                    """, arquivoId, "qa/" + arquivoId + ".jpg");
            jdbc.update("""
                    insert into anuncio_midia (
                      id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
                      visibilidade_midia, criado_em, atualizado_em
                    ) values (?, ?, ?, 'FOTO', 'CAPA', 0, 'PUBLICAVEL', 'LIVRE', now(), now())
                    """, UUID.randomUUID(), anuncioId, arquivoId);
        }
        return anuncioId;
    }

    private void ativar(UUID anuncioId, String origem, boolean registrarDebito) {
        UUID usuarioId = jdbc.queryForObject(
                "select usuario_id from anuncio where id = ?",
                UUID.class,
                anuncioId);
        UUID grupoId = UUID.randomUUID();
        UUID ativacaoId = UUID.randomUUID();
        jdbc.update("""
                insert into grupo_ativacao_beneficio (
                  id, tipo, origem, usuario_id, anuncio_id, validade_inicio_em,
                  validade_fim_em, status, criado_em, atualizado_em
                ) values (?, 'PACOTE', ?, ?, ?, ?, ?, 'ATIVO', now(), now())
                """, grupoId, origem, usuarioId, anuncioId, AGORA.minusHours(1), AGORA.plusDays(1));
        int custo = "CREDITO".equals(origem) ? 10 : 0;
        jdbc.update("""
                insert into ativacao_beneficio (
                  id, beneficio_id, usuario_id, anuncio_id, grupo_ativacao_id, origem,
                  inicio_em, fim_em, status, custo_creditos_snapshot, preco_snapshot, criado_em
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 'ATIVA', ?, ?, now())
                """, ativacaoId, BENEFICIO, usuarioId, anuncioId, grupoId, origem,
                AGORA.minusHours(1), AGORA.plusDays(1), custo,
                "COMPRA".equals(origem) ? 10 : null);
        if (registrarDebito && "CREDITO".equals(origem)) {
            jdbc.update("""
                    insert into movimento_credito (
                      id, usuario_id, tipo, direcao, quantidade, saldo_antes, saldo_depois,
                      origem, referencia_tipo, referencia_id, criado_em
                    ) values (?, ?, 'SAIDA', 'DEBITO', ?, 20, 10, 'BENEFICIO',
                      'ATIVACAO_BENEFICIO', ?, now())
                    """, UUID.randomUUID(), usuarioId, custo, ativacaoId);
        }
    }

    static final class PostgresInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                PostgresSupport.start();
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "relacionados-postgres17",
                        Map.of(
                                "spring.datasource.url", PostgresSupport.jdbcUrl(),
                                "spring.datasource.username", "topsv3test",
                                "spring.datasource.password", PostgresSupport.credential(),
                                "spring.flyway.enabled", "false",
                                "spring.jpa.hibernate.ddl-auto", "validate")));
            } catch (Exception exception) {
                throw new IllegalStateException("falha ao preparar PostgreSQL 17 para relacionados", exception);
            }
        }
    }

    static final class PostgresSupport {

        private static final Path MIGRATIONS = Path.of(
                "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
        private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        private static final String NETWORK = "topsv3-relacionados-" + SUFFIX + "-net";
        private static final String CONTAINER = "topsv3-relacionados-" + SUFFIX + "-pg17";
        private static final String CREDENTIAL = UUID.randomUUID().toString() + UUID.randomUUID();
        private static int port;
        private static boolean started;

        private PostgresSupport() {
        }

        static synchronized void start() throws Exception {
            if (started) return;
            command("docker", "network", "create", NETWORK);
            try {
                command(
                        Map.of("POSTGRES_PASSWORD", CREDENTIAL),
                        "docker", "run", "--pull=never", "-d", "--name", CONTAINER,
                        "--network", NETWORK,
                        "-p", "127.0.0.1::5432",
                        "-e", "POSTGRES_DB=topsv3_relacionados",
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
                        "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_relacionados",
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

        static synchronized void stop() throws Exception {
            commandIgnoringFailure("docker", "rm", "-f", CONTAINER);
            commandIgnoringFailure("docker", "network", "rm", NETWORK);
            started = false;
        }

        static String jdbcUrl() {
            return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_relacionados";
        }

        static String credential() {
            return CREDENTIAL;
        }

        private static void awaitPostgres() throws Exception {
            for (int attempt = 0; attempt < 60; attempt++) {
                if (commandIgnoringFailure(
                        Map.of("PGPASSWORD", CREDENTIAL),
                        "docker", "exec", "-e", "PGPASSWORD", CONTAINER,
                        "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
                        "--dbname", "topsv3_relacionados") == 0) {
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
