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
    private static final UUID BENEFICIO_TOPO = UUID.fromString("f3000000-0000-4000-8000-000000000003");
    private static final UUID BENEFICIO_FUTURO = UUID.fromString("f3ff0000-0000-4000-8000-000000000001");

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
                ) values
                  (?, 'ANUNCIO_TOPO', 'Topo QA', 'Beneficio QA', 'ANUNCIO', true, true, 1, now(), now()),
                  (?, 'BENEFICIO_FUTURO_QA', 'Futuro QA', 'Beneficio futuro QA', 'ANUNCIO', false, true, 2, now(), now())
                on conflict (codigo) do update set atualizado_em = excluded.atualizado_em
                """, BENEFICIO_TOPO, BENEFICIO_FUTURO);
    }

    @AfterAll
    static void cleanup() throws Exception {
        PostgresSupport.stop();
    }

    @Test
    void selecionaTodoBeneficioVigenteIndependentementeDaOrigemOuPagamento() {
        UUID atualId = candidato("anuncio-atual", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID compraLocal = candidato("compra-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID creditoLocal = candidato("credito-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID creditoSemDebito = candidato("credito-sem-debito", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID cortesia = candidato("cortesia-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID administracao = candidato("admin-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID importacao = candidato("importacao-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID beneficioFuturo = candidato("beneficio-futuro", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID expirado = candidato("expirado-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID revogado = candidato("revogado-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID cancelado = candidato("cancelado-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID outraCategoria = candidato("outra-categoria", "ACOMPANHANTE_MASCULINO", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID pausado = candidato("pausado-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PAUSADO", true);
        UUID reprovado = candidato("reprovado-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID removido = candidato("removido-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID bloqueado = candidato("bloqueado-local", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", true);
        UUID usuarioInativo = candidato("usuario-inativo", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "DESATIVADO", "PUBLICADO", true);
        UUID semMidia = candidato("sem-midia", "ACOMPANHANTE_FEMININA", CIDADE_LOCAL, "ATIVO", "PUBLICADO", false);
        UUID outraCidade = candidato("outra-cidade", "ACOMPANHANTE_FEMININA", CIDADE_HOMONIMA, "ATIVO", "PUBLICADO", true);

        ativar(atualId, "IMPORTACAO", false);
        ativar(compraLocal, "COMPRA", true);
        ativar(creditoLocal, "CREDITO", true);
        ativar(creditoSemDebito, "CREDITO", false);
        ativar(cortesia, "CORTESIA", false);
        ativar(administracao, "ADMIN", false);
        ativar(importacao, "IMPORTACAO", false);
        ativar(beneficioFuturo, BENEFICIO_FUTURO, "CAMPANHA", false);
        UUID ativacaoExpirada = ativar(expirado, "IMPORTACAO", false);
        UUID ativacaoRevogada = ativar(revogado, "ADMIN", false);
        UUID ativacaoCancelada = ativar(cancelado, "CORTESIA", false);
        ativar(outraCategoria, "COMPRA", true);
        ativar(pausado, "COMPRA", true);
        ativar(reprovado, "CAMPANHA", false);
        ativar(removido, "IMPORTACAO", false);
        ativar(bloqueado, "ADMIN", false);
        ativar(usuarioInativo, "COMPRA", true);
        ativar(semMidia, "COMPRA", true);
        ativar(outraCidade, "COMPRA", true);

        jdbc.update("update ativacao_beneficio set status = 'EXPIRADA' where id = ?", ativacaoExpirada);
        jdbc.update(
                "update ativacao_beneficio set status = 'REVOGADA', revogada_em = ? where id = ?",
                AGORA,
                ativacaoRevogada);
        jdbc.update("update ativacao_beneficio set status = 'CANCELADA' where id = ?", ativacaoCancelada);
        jdbc.update("update anuncio set status_moderacao = 'REJEITADO' where id = ?", reprovado);
        jdbc.update("update anuncio set status = 'REMOVIDO', removido_em = ? where id = ?", AGORA, removido);
        bloquearJuridicamente(bloqueado);

        var locais = repository.findRelacionadosComBeneficioVigente(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA,
                PageRequest.of(0, 100));
        var fallback = repository.findRelacionadosComBeneficioVigente(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                false,
                AGORA,
                PageRequest.of(0, 100));

        assertThat(locais).extracting(item -> item.getId())
                .containsExactlyInAnyOrder(
                        compraLocal,
                        creditoLocal,
                        creditoSemDebito,
                        cortesia,
                        administracao,
                        importacao,
                        beneficioFuturo)
                .doesNotContain(
                        atualId,
                        expirado,
                        revogado,
                        cancelado,
                        outraCategoria,
                        pausado,
                        reprovado,
                        removido,
                        bloqueado,
                        usuarioInativo,
                        semMidia);
        assertThat(fallback).extracting(item -> item.getId()).containsExactly(outraCidade);
    }

    @Test
    void refleteAtivacaoRecenteSemCacheEExcluiBeneficioExpirado() {
        UUID atualId = candidato(
                "atual-sem-cache",
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                "ATIVO",
                "PUBLICADO",
                true);
        UUID administracao = candidato(
                "admin-recente",
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                "ATIVO",
                "PUBLICADO",
                true);

        var antes = repository.findRelacionadosComBeneficioVigente(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA,
                PageRequest.of(0, 6));
        assertThat(antes).isEmpty();

        ativar(administracao, "ADMIN", false);

        var imediatamente = repository.findRelacionadosComBeneficioVigente(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA,
                PageRequest.of(0, 6));
        var depoisExpiracao = repository.findRelacionadosComBeneficioVigente(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA.plusDays(2),
                PageRequest.of(0, 6));

        assertThat(imediatamente).extracting(item -> item.getId()).containsExactly(administracao);
        assertThat(depoisExpiracao).isEmpty();
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

        var primeira = repository.findRelacionadosComBeneficioVigente(
                atualId,
                "ACOMPANHANTE_FEMININA",
                CIDADE_LOCAL,
                true,
                AGORA,
                PageRequest.of(0, 6));
        var segunda = repository.findRelacionadosComBeneficioVigente(
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
                ) values (?, ?, ?, ?, ?, 100, 'NAO_PUBLICAVEL', false, now())
                """, anuncioId, slug, ESTADO_GO, cidadeId, categoria);
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

    private UUID ativar(UUID anuncioId, String origem, boolean registrarDebito) {
        return ativar(anuncioId, BENEFICIO_TOPO, origem, registrarDebito);
    }

    private UUID ativar(UUID anuncioId, UUID beneficioId, String origem, boolean registrarDebito) {
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
                """, ativacaoId, beneficioId, usuarioId, anuncioId, grupoId, origem,
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
        return ativacaoId;
    }

    private void bloquearJuridicamente(UUID anuncioId) {
        UUID usuarioId = jdbc.queryForObject(
                "select usuario_id from anuncio where id = ?",
                UUID.class,
                anuncioId);
        jdbc.update("""
                insert into anuncio_bloqueio_juridico (
                  id, anuncio_id, usuario_id, escopo, categoria, motivo, bloqueado_por_id,
                  bloqueado_em, bloqueio_request_id, versao
                ) values (?, ?, ?, 'ANUNCIO', 'OUTRA_INTERVENCAO',
                  'Bloqueio juridico QA', ?, ?, 'req-relacionados-qa', 0)
                """, UUID.randomUUID(), anuncioId, usuarioId, usuarioId, AGORA);
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
            commandIgnoringFailure("docker", "rm", "-f", "-v", CONTAINER);
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
