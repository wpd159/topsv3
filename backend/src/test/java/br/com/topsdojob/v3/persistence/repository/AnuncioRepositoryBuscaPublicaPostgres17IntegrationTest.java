package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(
        classes = TopsDoJobBackendApplication.class,
        initializers = AnuncioRepositoryBuscaPublicaPostgres17IntegrationTest.PostgresInitializer.class)
@EnabledIfEnvironmentVariable(named = "PUBLIC_SEARCH_POSTGRES17_ENABLED", matches = "true")
class AnuncioRepositoryBuscaPublicaPostgres17IntegrationTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-21T12:00:00Z");
    private static final UUID ESTADO = UUID.fromString("11000000-0000-4000-8000-000000000001");
    private static final UUID CIDADE = UUID.fromString("22000000-0000-4000-8000-000000000001");
    private static final UUID BAIRRO = UUID.fromString("33000000-0000-4000-8000-000000000001");
    private static final UUID BENEFICIO_TOPO = UUID.fromString("f3000000-0000-4000-8000-000000000003");
    private static final String CATEGORIA = "ACOMPANHANTE_FEMININA";

    @Autowired
    private AnuncioRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedReferencias() {
        jdbc.update("""
                insert into estado (id, uf, nome, nome_normalizado, criado_em)
                values (?, 'GO', 'Goias', 'goias', now())
                """, ESTADO);
        jdbc.update("""
                insert into cidade (id, estado_id, nome, nome_normalizado, slug, criado_em)
                values (?, ?, 'Goiania', 'goiania', 'goiania', now())
                """, CIDADE, ESTADO);
        jdbc.update("""
                insert into bairro (id, cidade_id, nome, nome_normalizado, slug, criado_em)
                values (?, ?, 'Setor Central', 'setor central', 'setor-central', now())
                """, BAIRRO, CIDADE);
        jdbc.update("""
                insert into beneficio_premium (
                  id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
                  ordem_exibicao, criado_em, atualizado_em
                ) values (?, 'ANUNCIO_TOPO', 'Topo', 'Prioridade comercial', 'ANUNCIO',
                  true, true, 1, now(), now())
                on conflict (codigo) do update set atualizado_em = excluded.atualizado_em
                """, BENEFICIO_TOPO);
    }

    @AfterAll
    static void cleanup() throws Exception {
        PostgresSupport.stop();
    }

    @Test
    void preservaSemanticaTextualNormalizadaELiteral() {
        UUID anuncioId = anuncio(
                null,
                "perfil-cafe",
                "Café Central",
                "Atendimento noturno",
                "Café Central Goiânia Setor Central Espaço 100%_vip\\foto D'Ávila \"Noite\" 💖",
                "ATIVO",
                "PUBLICADO",
                "APROVADO",
                CATEGORIA);

        for (String termo : List.of(
                "cafe central",
                "caf",
                "goiania",
                "setor central",
                "espaco",
                "100\\%\\_vip\\\\foto",
                "d'avila",
                "\"noite\"",
                "💖")) {
            assertThat(buscar(termo).getContent())
                    .extracting(item -> item.getId())
                    .containsExactly(anuncioId);
        }

        assertThat(buscar("inexistente")).isEmpty();
        assertThat(buscar("' or 1=1 --")).isEmpty();
        assertThat(buscar(null)).extracting(item -> item.getId()).containsExactly(anuncioId);
    }

    @Test
    void excluiEstadosInelegiveisSemDeduplicarAnunciante() {
        UUID usuarioCompartilhado = usuario("ATIVO");
        UUID primeiro = anuncio(
                usuarioCompartilhado, "oferta-um", "Oferta um", "Descricao", "termo comum",
                "ATIVO", "PUBLICADO", "APROVADO", CATEGORIA);
        UUID segundo = anuncio(
                usuarioCompartilhado, "oferta-dois", "Oferta dois", "Descricao", "termo comum",
                "ATIVO", "PUBLICADO", "APROVADO", CATEGORIA);
        anuncio(null, "pausado", "Pausado", "Descricao", "termo comum",
                "ATIVO", "PAUSADO", "APROVADO", CATEGORIA);
        anuncio(null, "reprovado", "Reprovado", "Descricao", "termo comum",
                "ATIVO", "PUBLICADO", "REJEITADO", CATEGORIA);
        anuncio(null, "suspenso", "Suspenso", "Descricao", "termo comum",
                "DESATIVADO", "PUBLICADO", "APROVADO", CATEGORIA);

        Page<?> resultado = buscar("termo comum");
        assertThat(resultado.getTotalElements()).isEqualTo(2);
        assertThat(resultado.getContent())
                .extracting(item -> ((br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity) item).getId())
                .containsExactlyInAnyOrder(primeiro, segundo);
        assertThat(new LinkedHashSet<>(resultado.getContent())).hasSize(2);
    }

    @Test
    void preservaPremiumSeedPaginacaoCategoriaETotal() {
        UUID topoUm = anuncio(null, "topo-um", "Topo um", "Descricao", "busca comercial",
                "ATIVO", "PUBLICADO", "APROVADO", CATEGORIA);
        UUID topoDois = anuncio(null, "topo-dois", "Topo dois", "Descricao", "busca comercial",
                "ATIVO", "PUBLICADO", "APROVADO", CATEGORIA);
        UUID gratisUm = anuncio(null, "gratis-um", "Gratis um", "Descricao", "busca comercial",
                "ATIVO", "PUBLICADO", "APROVADO", CATEGORIA);
        UUID gratisDois = anuncio(null, "gratis-dois", "Gratis dois", "Descricao", "busca comercial",
                "ATIVO", "PUBLICADO", "APROVADO", CATEGORIA);
        anuncio(null, "outra-categoria", "Outra", "Descricao", "busca comercial",
                "ATIVO", "PUBLICADO", "APROVADO", "ACOMPANHANTE_MASCULINO");
        ativarTopo(topoUm);
        ativarTopo(topoDois);

        long seed = 987654321L;
        Page<?> primeira = repository.findPublicosOrdenados(
                CATEGORIA, "busca comercial", null, AGORA, seed, PageRequest.of(0, 2));
        Page<?> repetida = repository.findPublicosOrdenados(
                CATEGORIA, "busca comercial", null, AGORA, seed, PageRequest.of(0, 2));
        Page<?> segunda = repository.findPublicosOrdenados(
                CATEGORIA, "busca comercial", null, AGORA, seed, PageRequest.of(1, 2));

        List<UUID> primeiraIds = ids(primeira);
        assertThat(primeiraIds).containsExactlyInAnyOrder(topoUm, topoDois);
        assertThat(ids(repetida)).containsExactlyElementsOf(primeiraIds);
        assertThat(ids(segunda)).containsExactlyInAnyOrder(gratisUm, gratisDois);
        assertThat(primeira.getTotalElements()).isEqualTo(4);
        assertThat(Set.copyOf(List.of(
                primeiraIds.get(0), primeiraIds.get(1), ids(segunda).get(0), ids(segunda).get(1))))
                .hasSize(4);
    }

    @Test
    void refleteEdicaoDaProjecaoEMudancaCanonicaDeStatus() {
        UUID anuncioId = anuncio(null, "editavel", "Titulo antigo", "Descricao", "texto antigo",
                "ATIVO", "PUBLICADO", "APROVADO", CATEGORIA);

        assertThat(buscar("texto antigo")).hasSize(1);
        jdbc.update("""
                update documento_busca_anuncio
                set texto_busca = 'texto novo goiania setor central', atualizado_em = now()
                where anuncio_id = ?
                """, anuncioId);
        assertThat(buscar("texto antigo")).isEmpty();
        assertThat(buscar("texto novo")).hasSize(1);

        jdbc.update("update anuncio set status = 'PAUSADO', atualizado_em = now() where id = ?", anuncioId);
        assertThat(buscar("texto novo")).isEmpty();
    }

    @Test
    void planoComVolumeUsaIndiceNormalizadoDaProjecao() {
        jdbc.update("""
                insert into usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
                select md5('search-user-' || serie)::uuid, 'Usuario sintetico ' || serie,
                       'ATIVO', 'ANUNCIANTE', now(), now(), 0
                from generate_series(1, 50000) serie
                """);
        jdbc.update("""
                insert into anuncio (
                  id, usuario_id, slug, titulo, descricao, status, status_moderacao,
                  categoria, atendimento_exclusivamente_virtual, publicado_em,
                  ultima_publicacao_em, criado_em, atualizado_em, versao
                )
                select md5('search-ad-' || serie)::uuid,
                       md5('search-user-' || serie)::uuid,
                       'sintetico-' || serie,
                       'Perfil sintetico ' || serie,
                       'Descricao publica sintetica',
                       'PUBLICADO', 'APROVADO', ?, false,
                       now(), now(), now(), now(), 0
                from generate_series(1, 50000) serie
                """, CATEGORIA);
        jdbc.update("""
                insert into documento_busca_anuncio (
                  anuncio_id, texto_busca, estado_id, cidade_id, bairro_id, categoria,
                  status_publicacao, tem_midia_valida, atualizado_em
                )
                select md5('search-ad-' || serie)::uuid,
                       case when serie % 997 = 0
                         then 'perfil marcador ultrarraro goiania setor central'
                         else 'perfil sintetico comum goiania setor central ' || serie end,
                       ?, ?, ?, ?, 'PUBLICAVEL', true, now()
                from generate_series(1, 50000) serie
                """, ESTADO, CIDADE, BAIRRO, CATEGORIA);
        jdbc.execute("analyze usuario");
        jdbc.execute("analyze anuncio");
        jdbc.execute("analyze documento_busca_anuncio");

        String plano = jdbc.queryForObject("""
                explain (analyze, buffers, verbose, format json)
                select a.id
                from anuncio a
                join usuario u on u.id = a.usuario_id
                where a.status = 'PUBLICADO'
                  and a.status_moderacao = 'APROVADO'
                  and a.removido_em is null
                  and u.status = 'ATIVO'
                  and u.tipo_conta = 'ANUNCIANTE'
                  and u.desativado_em is null
                  and u.excluido_em is null
                  and exists (
                    select 1
                    from documento_busca_anuncio d
                    where d.anuncio_id = a.id
                      and lower(translate(coalesce(d.texto_busca, ''),
                            'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
                            'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'))
                          like '%marcador ultrarraro%' escape '\\'
                  )
                order by hashtextextended(a.id::text, 42), a.id
                limit 20
                """, String.class);

        assertThat(plano)
                .contains("documento_busca_anuncio_texto_normalizado_trgm_idx")
                .contains("Bitmap Index Scan");
    }

    private Page<br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity> buscar(String termo) {
        return repository.findPublicosOrdenados(
                null, termo, null, AGORA, 42L, PageRequest.of(0, 100));
    }

    private List<UUID> ids(Page<?> page) {
        return page.getContent().stream()
                .map(item -> ((br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity) item).getId())
                .toList();
    }

    private UUID usuario(String status) {
        UUID usuarioId = UUID.randomUUID();
        jdbc.update("""
                insert into usuario (
                  id, nome, status, tipo_conta, criado_em, atualizado_em, desativado_em, versao
                ) values (?, ?, ?, 'ANUNCIANTE', now(), now(), ?, 0)
                """, usuarioId, "Usuario sintetico", status,
                "ATIVO".equals(status) ? null : AGORA.minusDays(1));
        return usuarioId;
    }

    private UUID anuncio(
            UUID usuarioId,
            String slug,
            String titulo,
            String descricao,
            String textoBusca,
            String statusUsuario,
            String statusAnuncio,
            String statusModeracao,
            String categoria) {
        UUID proprietarioId = usuarioId == null ? usuario(statusUsuario) : usuarioId;
        UUID anuncioId = UUID.randomUUID();
        jdbc.update("""
                insert into anuncio (
                  id, usuario_id, slug, titulo, descricao, status, status_moderacao,
                  categoria, atendimento_exclusivamente_virtual, publicado_em,
                  ultima_publicacao_em, criado_em, atualizado_em, versao
                ) values (?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?, now(), now(), 0)
                """, anuncioId, proprietarioId, slug, titulo, descricao, statusAnuncio,
                statusModeracao, categoria,
                "PUBLICADO".equals(statusAnuncio) ? AGORA.minusDays(1) : null,
                "PUBLICADO".equals(statusAnuncio) ? AGORA.minusDays(1) : null);
        jdbc.update("""
                insert into documento_busca_anuncio (
                  anuncio_id, texto_busca, estado_id, cidade_id, bairro_id, categoria,
                  status_publicacao, tem_midia_valida, atualizado_em
                ) values (?, ?, ?, ?, ?, ?, 'PUBLICAVEL', true, now())
                """, anuncioId, textoBusca.toLowerCase(), ESTADO, CIDADE, BAIRRO, categoria);
        return anuncioId;
    }

    private void ativarTopo(UUID anuncioId) {
        UUID usuarioId = jdbc.queryForObject(
                "select usuario_id from anuncio where id = ?", UUID.class, anuncioId);
        UUID grupoId = UUID.randomUUID();
        jdbc.update("""
                insert into grupo_ativacao_beneficio (
                  id, tipo, origem, usuario_id, anuncio_id, validade_inicio_em,
                  validade_fim_em, status, criado_em, atualizado_em
                ) values (?, 'PACOTE', 'ADMIN', ?, ?, ?, ?, 'ATIVO', now(), now())
                """, grupoId, usuarioId, anuncioId, AGORA.minusHours(1), AGORA.plusHours(1));
        jdbc.update("""
                insert into ativacao_beneficio (
                  id, beneficio_id, usuario_id, anuncio_id, grupo_ativacao_id, origem,
                  inicio_em, fim_em, status, custo_creditos_snapshot, criado_em
                ) values (?, ?, ?, ?, ?, 'ADMIN', ?, ?, 'ATIVA', 0, now())
                """, UUID.randomUUID(), BENEFICIO_TOPO, usuarioId, anuncioId, grupoId,
                AGORA.minusHours(1), AGORA.plusHours(1));
    }

    static final class PostgresInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                PostgresSupport.start();
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "busca-publica-postgres17",
                        Map.of(
                                "spring.datasource.url", PostgresSupport.jdbcUrl(),
                                "spring.datasource.username", "topsv3test",
                                "spring.datasource.password", PostgresSupport.credential(),
                                "spring.flyway.enabled", "false",
                                "spring.jpa.hibernate.ddl-auto", "validate")));
            } catch (Exception exception) {
                throw new IllegalStateException("falha ao preparar PostgreSQL 17 para busca publica", exception);
            }
        }
    }

    static final class PostgresSupport {

        private static final Path MIGRATIONS = Path.of(
                "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
        private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        private static final String NETWORK = "topsv3-search-" + SUFFIX + "-net";
        private static final String CONTAINER = "topsv3-search-" + SUFFIX + "-pg17";
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
                        "-e", "POSTGRES_DB=topsv3_search",
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
                        "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_search",
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
            return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_search";
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
                        "--dbname", "topsv3_search") == 0) {
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
