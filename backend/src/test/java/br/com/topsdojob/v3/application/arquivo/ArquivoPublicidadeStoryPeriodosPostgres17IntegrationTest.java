package br.com.topsdojob.v3.application.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoStoryAccessAuditService;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoStoryService;
import br.com.topsdojob.v3.application.admin.arquivo.FinalidadeAcessoArquivoPublicidade;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.SelecaoMidiasPublicas;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Disposable PostgreSQL 17 proof: V055 rows survive V056/V057 and recovery remains private. */
@EnabledIfEnvironmentVariable(named = "STORY_PERIODOS_POSTGRES17_ENABLED", matches = "true")
class ArquivoPublicidadeStoryPeriodosPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String DATABASE = "topsv3_story_periodos";
  private static final String USER = "topsv3test";

  @Test
  void migracaoEWriterPreservamPeriodoAntigoECriamRetomadaComReferenciaSemR2() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-story-" + suffix + "-net";
    String container = "topsv3-story-" + suffix + "-pg17";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    command("docker", "network", "create", network);
    try {
      command(Map.of("POSTGRES_PASSWORD", credential),
          "docker", "run", "--pull=never", "-d", "--name", container,
          "--network", network, "-p", "127.0.0.1::5432",
          "-e", "POSTGRES_DB=" + DATABASE, "-e", "POSTGRES_USER=" + USER,
          "-e", "POSTGRES_PASSWORD", "postgres:17-alpine");
      awaitPostgres(container, credential);
      flyway(container, network, credential, "-target=55", "migrate");
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + mappedPort(container) + "/" + DATABASE,
          USER, credential);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      Fixture fixture = seedV055(jdbc);
      String checksum = jdbc.queryForObject(
          "SELECT checksum::text FROM flyway_schema_history WHERE version::integer=55", String.class);

      flyway(container, network, credential, "-target=57", "migrate");
      flyway(container, network, credential, "-target=57", "validate");
      String previousMigrations = System.getenv("STORY_PREVIOUS_RELEASE_MIGRATIONS");
      if (previousMigrations != null && !previousMigrations.isBlank()) {
        Path oldPath = Path.of(previousMigrations).toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(oldPath.resolve("V053__estado_preview_restrito.sql"))).isTrue();
        assertThat(Files.exists(oldPath.resolve("V054__arquivo_publicidade_privado.sql"))).isFalse();
        flywayAtPath(oldPath, container, network, credential, "validate");
      }
      assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class))
          .isBetween(170000, 179999);
      assertThat(jdbc.queryForObject(
          "SELECT checksum::text FROM flyway_schema_history WHERE version::integer=55", String.class))
          .isEqualTo(checksum);
      assertThat(jdbc.queryForObject("""
          SELECT COALESCE(MAX(version::integer) FILTER
            (WHERE success AND version ~ '^[0-9]+$'), 0)
          FROM flyway_schema_history
          """, Integer.class)).isEqualTo(57);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Long.class)).isZero();
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_hold WHERE veiculacao_id=?", Long.class,
          fixture.firstPeriod())).isEqualTo(1L);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_midia WHERE versao_id=?", Long.class,
          fixture.firstVersion())).isEqualTo(1L);
      assertThat(selecionarPublicasPersistidas(jdbc, fixture.ad()))
          .extracting(MidiaVinculoLeitura::id)
          .containsExactly(fixture.firstLink(), fixture.secondLink());

      ObjectStorage storage = mock(ObjectStorage.class);
      Map<String, StoredObject> privateObjects = new ConcurrentHashMap<>();
      String oldKey = key(fixture.firstVersion(), fixture.firstLink(), fixture.firstMedia());
      privateObjects.put(oldKey, new StoredObject(fixture.bytes(), "image/jpeg"));
      when(storage.get(any(), any())).thenAnswer(call -> {
        StorageArea area = call.getArgument(0);
        String objectKey = call.getArgument(1);
        if (area == StorageArea.PUBLIC_MEDIA && objectKey.startsWith("hml/public/")) {
          return new StoredObject(fixture.bytes(), "image/jpeg");
        }
        return area == StorageArea.PRIVATE_MEDIA ? privateObjects.get(objectKey) : null;
      });
      when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any()))
          .thenAnswer(call -> {
            String objectKey = call.getArgument(1);
            StoredObject value = new StoredObject(call.getArgument(2), call.getArgument(3));
            return privateObjects.putIfAbsent(objectKey, value) == null
                ? ObjectWriteResult.CREATED : ObjectWriteResult.ALREADY_EXISTS;
          });
      @SuppressWarnings("unchecked")
      ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
      when(provider.getIfAvailable()).thenReturn(storage);
      R2StorageProperties properties = new R2StorageProperties();
      properties.setEnabled(true);
      properties.setPublicMediaBucket("publico");
      properties.setPublicMediaPrefix("hml/public/");
      properties.setPrivateMediaBucket("privado");
      properties.setPrivateMediaPrefix("hml/private/");
      AtomicReference<List<MidiaPublicaDto>> selected = new AtomicReference<>(List.of(
          publicPhoto(fixture.firstLink()), publicPhoto(fixture.secondLink())));
      AnuncioMidiaRepository links = mock(AnuncioMidiaRepository.class);
      ArquivoMidiaRepository files = mock(ArquivoMidiaRepository.class);
      AnuncioMidiaEntity first = mock(AnuncioMidiaEntity.class);
      AnuncioMidiaEntity second = mock(AnuncioMidiaEntity.class);
      when(first.getId()).thenReturn(fixture.firstLink());
      when(first.getAnuncioId()).thenReturn(fixture.ad());
      when(first.getArquivoMidiaId()).thenReturn(fixture.firstMedia());
      when(first.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
      when(first.getFinalidade()).thenReturn(FinalidadeAnuncioMidia.CAPA);
      when(first.getOrdem()).thenReturn(0);
      when(first.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
      when(first.getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
      when(second.getId()).thenReturn(fixture.secondLink());
      when(second.getAnuncioId()).thenReturn(fixture.ad());
      when(second.getArquivoMidiaId()).thenReturn(fixture.secondMedia());
      when(second.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
      when(second.getFinalidade()).thenReturn(FinalidadeAnuncioMidia.GALERIA);
      when(second.getOrdem()).thenReturn(1);
      when(second.getStatus()).thenReturn(StatusAnuncioMidia.REMOVIDA);
      when(second.getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
      when(links.findByAnuncioId(fixture.ad())).thenReturn(List.of(first, second));
      ArquivoMidiaEntity firstFile = mock(ArquivoMidiaEntity.class);
      ArquivoMidiaEntity secondFile = mock(ArquivoMidiaEntity.class);
      when(firstFile.getId()).thenReturn(fixture.firstMedia());
      when(secondFile.getId()).thenReturn(fixture.secondMedia());
      when(files.findByIdIn(List.of(fixture.firstMedia(), fixture.secondMedia())))
          .thenReturn(List.of(firstFile, secondFile));
      PremiumPublicoMapper premium = mock(PremiumPublicoMapper.class);
      when(premium.flagsPorAnuncioIds(List.of(fixture.ad())))
          .thenReturn(Map.of(fixture.ad(), PremiumPublicoFlagsDto.vazio()));
      MidiaPublicaMapper publicMapper = mock(MidiaPublicaMapper.class);
      when(publicMapper.publicas(any(), anyMap(), eq(true), anyInt(), eq(false)))
          .thenAnswer(call -> selected.get());
      var writer = new ArquivoPublicidadeStoryRegistroService(
          new NamedParameterJdbcTemplate(jdbc), mock(EntityManager.class),
          new ObjectMapper().findAndRegisterModules(), provider, properties,
          links, files, publicMapper, premium,
          mock(ArquivoPublicidadeTransicaoTemporalService.class));
      TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

      CountDownLatch pronta = new CountDownLatch(2);
      CountDownLatch iniciar = new CountDownLatch(1);
      var workers = Executors.newFixedThreadPool(2);
      try {
        Runnable retomadaConcorrente = () -> {
          pronta.countDown();
          try {
            assertThat(iniciar.await(10, TimeUnit.SECONDS)).isTrue();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
          tx.executeWithoutResult(ignored -> writer.registrarEstado(
              fixture.story(), "STORY_RETOMADO", "req-retomada", OffsetDateTime.now(ZoneOffset.UTC)));
        };
        Future<?> primeira = workers.submit(retomadaConcorrente);
        Future<?> segunda = workers.submit(retomadaConcorrente);
        assertThat(pronta.await(10, TimeUnit.SECONDS)).isTrue();
        iniciar.countDown();
        primeira.get(30, TimeUnit.SECONDS);
        segunda.get(30, TimeUnit.SECONDS);
      } finally {
        workers.shutdownNow();
      }
      UUID secondPeriod = jdbc.queryForObject("""
          SELECT id FROM arquivo_publicidade_story_veiculacao
           WHERE story_id=? ORDER BY inicio_em DESC, id DESC LIMIT 1
          """, UUID.class, fixture.story());
      assertThat(secondPeriod).isNotEqualTo(fixture.firstPeriod());
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_veiculacao WHERE story_id=?",
          Long.class, fixture.story())).isEqualTo(2L);
      assertThat(jdbc.queryForObject("""
          SELECT count(*) FROM arquivo_publicidade_story_veiculacao a
          JOIN arquivo_publicidade_story_veiculacao b
            ON a.story_id=b.story_id AND a.id<>b.id
           AND a.inicio_em < b.fim_em AND b.inicio_em < a.fim_em
          WHERE a.story_id=?
          """, Long.class, fixture.story())).isZero();
      tx.executeWithoutResult(ignored -> writer.registrarEstado(
          fixture.story(), "STORY_REPETIDO", "req-repetido", OffsetDateTime.now(ZoneOffset.UTC)));
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_veiculacao WHERE story_id=?",
          Long.class, fixture.story())).isEqualTo(2L);

      selected.set(List.of(publicPhoto(fixture.firstLink())));
      clearInvocations(provider, storage, publicMapper);
      doThrow(new IllegalStateException("provider sinteticamente indisponivel"))
          .when(provider).getIfAvailable();
      doThrow(new IllegalStateException("URL nao deve ser resolvida na retirada"))
          .when(publicMapper).publicas(any(), anyMap(), eq(true), anyInt(), eq(false));
      tx.executeWithoutResult(ignored -> {
        jdbc.update("UPDATE anuncio_midia SET status='REMOVIDA', atualizado_em=now() WHERE id=?",
            fixture.secondLink());
        assertThat(writer.midiasSemCopiaParaRetiradaPorAnuncio(fixture.ad())).isEmpty();
        writer.registrarEstado(fixture.story(), "MIDIA_REMOVIDA_PELO_PROPRIETARIO", "req-retirada",
            OffsetDateTime.now(ZoneOffset.UTC));
      });
      assertThat(jdbc.queryForObject("SELECT status FROM anuncio_midia WHERE id=?",
          String.class, fixture.secondLink())).isEqualTo("REMOVIDA");
      assertThat(selecionarPublicasPersistidas(jdbc, fixture.ad()))
          .extracting(MidiaVinculoLeitura::id)
          .containsExactly(fixture.firstLink());
      verify(provider, never()).getIfAvailable();
      verify(publicMapper, never()).publicas(any(), anyMap(), eq(true), anyInt(), eq(false));
      verify(storage, never()).get(any(), any());
      verify(storage, never()).putIfAbsent(any(), any(), any(), any());
      doReturn(storage).when(provider).getIfAvailable();
      UUID secondFirstVersion = jdbc.queryForObject("""
          SELECT id FROM arquivo_publicidade_story_versao
           WHERE veiculacao_id=? AND numero=1
          """, UUID.class, secondPeriod);
      UUID referenceId = jdbc.queryForObject("""
          SELECT r.id FROM arquivo_publicidade_story_midia_referencia r
          JOIN arquivo_publicidade_story_versao v ON v.id=r.versao_id
          WHERE v.veiculacao_id=? AND v.numero=2
          """, UUID.class, secondPeriod);
      assertThat(referenceId).isNotNull();
      assertThat(jdbc.queryForObject("""
          SELECT origem.versao_id FROM arquivo_publicidade_story_midia_referencia r
          JOIN arquivo_publicidade_story_midia origem ON origem.id=r.origem_midia_id
          WHERE r.id=?
          """, UUID.class, referenceId)).isEqualTo(secondFirstVersion);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_midia WHERE versao_id IN "
              + "(SELECT id FROM arquivo_publicidade_story_versao WHERE veiculacao_id=? AND numero=2)",
          Long.class, secondPeriod)).isZero();

      AdminArquivoStoryService admin = new AdminArquivoStoryService(jdbc,
          new ObjectMapper().findAndRegisterModules(), provider, properties,
          mock(AdminArquivoStoryAccessAuditService.class));
      var finalidade = FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA;
      var detalhe = admin.detalhar(secondPeriod, fixture.user(), "req-auditoria", finalidade, true);
      assertThat(detalhe.versoes()).hasSize(2);
      assertThat(detalhe.versoes().get(1).midias()).extracting("id").contains(referenceId);
      assertThat(admin.midia(secondPeriod, referenceId, fixture.user(), "req-bytes", finalidade).bytes())
          .isEqualTo(fixture.bytes());
      assertThat(admin.detalhar(fixture.firstPeriod(), fixture.user(), "req-legado", finalidade, true)
          .versoes()).hasSize(1);
      UUID firstArchivedMedia = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_story_midia WHERE versao_id=?",
          UUID.class, fixture.firstVersion());
      assertThat(jdbc.queryForObject("""
          SELECT chave_privada FROM arquivo_publicidade_story_midia WHERE id=?
          """, String.class, firstArchivedMedia)).isEqualTo(oldKey);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_hold WHERE veiculacao_id=?", Long.class,
          fixture.firstPeriod())).isEqualTo(1L);
      assertThat(admin.midia(fixture.firstPeriod(), firstArchivedMedia, fixture.user(),
          "req-bytes-legado", finalidade).bytes()).isEqualTo(fixture.bytes());

      doReturn(selected.get()).when(publicMapper)
          .publicas(any(), anyMap(), eq(true), anyInt(), eq(false));
      clearInvocations(provider, storage, publicMapper);
      int objectsBeforeText = privateObjects.size();
      tx.executeWithoutResult(ignored -> {
        jdbc.update("UPDATE anuncio SET titulo='Story com texto atualizado', atualizado_em=now() WHERE id=?",
            fixture.ad());
        writer.registrarEstado(fixture.story(), "STORY_ATUALIZADO", "req-texto-apos-retirada",
            OffsetDateTime.now(ZoneOffset.UTC));
      });
      verify(storage, never()).putIfAbsent(any(), any(), any(), any());
      verify(storage, never()).delete(any(), any());
      assertThat(privateObjects).hasSize(objectsBeforeText);
      UUID thirdVersion = jdbc.queryForObject("""
          SELECT id FROM arquivo_publicidade_story_versao
          WHERE veiculacao_id=? AND numero=3
          """, UUID.class, secondPeriod);
      UUID textReference = jdbc.queryForObject("""
          SELECT id FROM arquivo_publicidade_story_midia_referencia WHERE versao_id=?
          """, UUID.class, thirdVersion);
      assertThat(jdbc.queryForObject("""
          SELECT origem.versao_id FROM arquivo_publicidade_story_midia_referencia r
          JOIN arquivo_publicidade_story_midia origem ON origem.id=r.origem_midia_id
          WHERE r.id=?
          """, UUID.class, textReference)).isEqualTo(secondFirstVersion);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_midia WHERE versao_id=?",
          Long.class, thirdVersion)).isZero();
      verify(storage).get(StorageArea.PRIVATE_MEDIA,
          key(secondFirstVersion, fixture.firstLink(), fixture.firstMedia()));
      var textExport = admin.detalhar(secondPeriod, fixture.user(), "req-export-texto", finalidade, true);
      assertThat(textExport.versoes()).hasSize(3);
      assertThat(textExport.versoes().get(0).conteudo().path("titulo").asText())
          .isEqualTo("Story de teste");
      assertThat(textExport.versoes().get(1).conteudo().path("titulo").asText())
          .isEqualTo("Story de teste");
      assertThat(textExport.versoes().get(2).conteudo().path("titulo").asText())
          .isEqualTo("Story com texto atualizado");
      assertThat(textExport.versoes().get(2).midias()).extracting("id").containsExactly(textReference);
      clearInvocations(storage);
      assertThat(admin.midia(secondPeriod, textReference, fixture.user(), "req-texto-bytes", finalidade)
          .bytes()).isEqualTo(fixture.bytes());
      verify(storage).get(StorageArea.PRIVATE_MEDIA,
          key(secondFirstVersion, fixture.firstLink(), fixture.firstMedia()));
      verify(storage, never()).get(eq(StorageArea.PUBLIC_MEDIA), any());
      verify(storage, never()).putIfAbsent(any(), any(), any(), any());
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_hold WHERE veiculacao_id=?", Long.class,
          fixture.firstPeriod())).isEqualTo(1L);
      assertThat(selecionarPublicasPersistidas(jdbc, fixture.ad()))
          .extracting(MidiaVinculoLeitura::id).containsExactly(fixture.firstLink());

      UUID directFile = UUID.randomUUID();
      UUID directStory = seedDirectStory(jdbc, fixture.user(), directFile,
          fixture.bytes());
      privateObjects.put("hml/private/direct.jpg",
          new StoredObject(fixture.bytes(), "image/jpeg"));
      tx.executeWithoutResult(ignored -> writer.registrarEstado(directStory,
          "STORY_PUBLICADO", "req-direto", OffsetDateTime.now(ZoneOffset.UTC)));
      UUID directPeriod = jdbc.queryForObject("""
          SELECT id FROM arquivo_publicidade_story_veiculacao
          WHERE story_id=? AND modo_conteudo='MIDIA_UPLOAD' AND anuncio_id IS NULL
          """, UUID.class, directStory);
      assertThat(directPeriod).isNotNull();
      UUID directArchivedMedia = jdbc.queryForObject("""
          SELECT m.id FROM arquivo_publicidade_story_midia m
          JOIN arquivo_publicidade_story_versao v ON v.id=m.versao_id
          WHERE v.veiculacao_id=? AND m.anuncio_midia_id IS NULL
          """, UUID.class, directPeriod);
      assertThat(admin.midia(directPeriod, directArchivedMedia, fixture.user(),
          "req-direto-bytes", finalidade).bytes()).isEqualTo(fixture.bytes());
      jdbc.update("UPDATE anuncio SET status='PAUSADO', atualizado_em=now() WHERE id=?", fixture.ad());
      tx.executeWithoutResult(ignored -> writer.registrarEstado(directStory,
          "ANUNCIO_PAUSADO", "req-direto-independente", OffsetDateTime.now(ZoneOffset.UTC)));
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_story_versao WHERE veiculacao_id=?",
          Long.class, directPeriod)).isEqualTo(1L);
      assertThat(jdbc.queryForObject(
          "SELECT fim_em > now() FROM arquivo_publicidade_story_veiculacao WHERE id=?",
          Boolean.class, directPeriod)).isTrue();
      UUID secondVersion = jdbc.queryForObject("""
          SELECT id FROM arquivo_publicidade_story_versao
          WHERE veiculacao_id=? AND numero=2
          """, UUID.class, secondPeriod);
      UUID foreignReference = UUID.randomUUID();
      jdbc.update("""
          INSERT INTO arquivo_publicidade_story_midia_referencia
            (id,versao_id,origem_midia_id,arquivo_midia_id,variante,ordem)
          VALUES (?,?,?,?,'ORIGINAL',99)
          """, foreignReference, secondVersion, directArchivedMedia, directFile);
      assertThat(admin.detalhar(secondPeriod, fixture.user(), "req-cruzada", finalidade, true)
          .versoes().get(1).midias()).extracting("id").doesNotContain(foreignReference);
      assertThatThrownBy(() -> admin.midia(secondPeriod, foreignReference, fixture.user(),
          "req-cruzada-bytes", finalidade))
          .isInstanceOfSatisfying(ResponseStatusException.class,
              error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    } finally {
      commandIgnoringFailure("docker", "rm", "-f", container);
      commandIgnoringFailure("docker", "network", "rm", network);
    }
  }

  private static Fixture seedV055(JdbcTemplate jdbc) throws Exception {
    UUID user = UUID.randomUUID();
    UUID ad = UUID.randomUUID();
    UUID benefit = UUID.randomUUID();
    UUID activation = UUID.randomUUID();
    UUID story = UUID.randomUUID();
    UUID firstMedia = UUID.randomUUID();
    UUID secondMedia = UUID.randomUUID();
    UUID firstLink = UUID.randomUUID();
    UUID secondLink = UUID.randomUUID();
    UUID firstPeriod = UUID.randomUUID();
    UUID firstVersion = UUID.randomUUID();
    byte[] bytes = "story-pg17-sintetica".getBytes(StandardCharsets.UTF_8);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    OffsetDateTime start = now.minusHours(2);
    OffsetDateTime closed = now.minusHours(1);
    OffsetDateTime deadline = now.plusDays(1);
    jdbc.update("""
        INSERT INTO usuario(id,nome,email_normalizado,status,tipo_conta,criado_em,atualizado_em)
        VALUES (?,'Pessoa sintética',?,'ATIVO','ANUNCIANTE',?,?)
        """, user, "story-" + user + "@example.invalid", start, start);
    jdbc.update("""
        INSERT INTO anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,criado_em,atualizado_em)
        VALUES (?,? ,?,'Story de teste','Conteúdo sintético','PUBLICADO','APROVADO','OUTROS',?,?)
        """, ad, user, "story-" + ad, start, start);
    jdbc.update("""
        INSERT INTO beneficio_premium(id,codigo,nome,descricao,escopo,criado_em,atualizado_em)
        VALUES (?,'STORIES','Stories','Fixture sintética','MIDIA',?,?)
        """, benefit, start, start);
    jdbc.update("""
        INSERT INTO ativacao_beneficio(id,beneficio_id,usuario_id,anuncio_id,origem,inicio_em,fim_em,
          status,custo_creditos_snapshot,criado_em)
        VALUES (?,?,?,?,'CREDITO',?,?,'ATIVA',1,?)
        """, activation, benefit, user, ad, start, deadline, start);
    insertFile(jdbc, firstMedia, "hml/public/primeira.jpg", hash, bytes.length, start);
    insertFile(jdbc, secondMedia, "hml/public/segunda.jpg", hash, bytes.length, start);
    insertLink(jdbc, firstLink, ad, firstMedia, 0, start);
    insertLink(jdbc, secondLink, ad, secondMedia, 1, start);
    jdbc.update("""
        INSERT INTO story_anuncio(id,status,inicio_em,fim_em,criado_por,criado_em,atualizado_em,
          anuncio_id,modo_conteudo,ativacao_beneficio_id,idempotency_key,request_fingerprint)
        VALUES (?,'PUBLICADO',?,?,?,?,?,?,'ANUNCIO',?,'story-pg17',?)
        """, story, start, deadline, user, start, start, ad, activation, "a".repeat(64));
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_veiculacao(id,story_id,anuncio_id,contratante_usuario_id,
          ativacao_beneficio_id,modo_conteudo,classificacao,relacao_material,cobertura,
          inicio_em,fim_em,retencao_ate,encerramento_motivo,capturado_em,atualizado_em)
        VALUES (?,?,?,? ,?,'ANUNCIO','ORIGEM_INDETERMINADA','DESCONHECIDA','PREVENTIVA',
          ?,?,?,'ANUNCIO_PAUSADO',?,?)
        """, firstPeriod, story, ad, user, activation, start, closed, closed.plusYears(1), start, closed);
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_versao(id,veiculacao_id,numero,vigente_desde,
          vigente_ate,capturado_em,motivo,conteudo_json,contratante_json,comercial_json,
          segmentacao_json,alcance_json,conteudo_sha256)
        VALUES (?,?,1,?, ?,?,'STORY_PUBLICADO','{}','{}','{}','{}','{}',?)
        """, firstVersion, firstPeriod, start, closed, start, "0".repeat(64));
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_midia(id,versao_id,anuncio_midia_id,arquivo_midia_id,
          variante,storage_provider,bucket,chave_privada,sha256,mime_type,tamanho_bytes,ordem)
        VALUES (?,?,?,?, 'ORIGINAL','R2','privado',? ,?,'image/jpeg',?,0)
        """, UUID.randomUUID(), firstVersion, firstLink, firstMedia,
        key(firstVersion, firstLink, firstMedia), hash, bytes.length);
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_hold(id,veiculacao_id,fundamento,responsavel_usuario_id,
          inicio_em,revisar_em)
        VALUES (?,?, 'fixture jurídica sintética',?, ?,?)
        """, UUID.randomUUID(), firstPeriod, user, start, deadline);
    return new Fixture(user, ad, story, firstMedia, secondMedia, firstLink, secondLink,
        firstPeriod, firstVersion, bytes);
  }

  /** Unchanged pre-V054 public position selector, fed from persisted post-withdrawal rows. */
  private static List<MidiaVinculoLeitura> selecionarPublicasPersistidas(
      JdbcTemplate jdbc, UUID anuncioId) {
    List<MidiaVinculoLeitura> vinculos = jdbc.query("""
        SELECT id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
               visibilidade_midia, atualizado_em
        FROM anuncio_midia WHERE anuncio_id=?
        """, (rs, index) -> new MidiaVinculoLeitura(
        rs.getObject("id", UUID.class), rs.getObject("anuncio_id", UUID.class),
        rs.getObject("arquivo_midia_id", UUID.class),
        TipoAnuncioMidia.valueOf(rs.getString("tipo")),
        FinalidadeAnuncioMidia.valueOf(rs.getString("finalidade")),
        rs.getObject("ordem", Integer.class),
        StatusAnuncioMidia.valueOf(rs.getString("status")),
        VisibilidadeMidia.valueOf(rs.getString("visibilidade_midia")),
        rs.getObject("atualizado_em", OffsetDateTime.class)), anuncioId);
    return SelecaoMidiasPublicas.selecionar(vinculos, 10, false);
  }

  private static void insertFile(JdbcTemplate jdbc, UUID id, String key, String hash,
      int size, OffsetDateTime at) {
    jdbc.update("""
        INSERT INTO arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,
          tamanho_bytes,sha256,status_arquivo,criado_em)
        VALUES (?,'R2','publico',?,'image/jpeg',?,?,'VALIDADO',?)
        """, id, key, size, hash, at);
  }

  private static UUID seedDirectStory(JdbcTemplate jdbc, UUID user, UUID file,
      byte[] bytes) throws Exception {
    UUID story = UUID.randomUUID();
    UUID activation = UUID.randomUUID();
    UUID benefit = jdbc.queryForObject(
        "SELECT id FROM beneficio_premium WHERE codigo='STORIES'", UUID.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    OffsetDateTime start = now.minusHours(1);
    OffsetDateTime deadline = now.plusHours(3);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    jdbc.update("""
        INSERT INTO arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,
          tamanho_bytes,sha256,status_arquivo,criado_em)
        VALUES (?,'R2','privado','hml/private/direct.jpg','image/jpeg',?,?,'VALIDADO',?)
        """, file, bytes.length, hash, start);
    jdbc.update("""
        INSERT INTO ativacao_beneficio(id,beneficio_id,usuario_id,origem,inicio_em,fim_em,
          status,custo_creditos_snapshot,criado_em)
        VALUES (?,?,?,'CREDITO',?,?,'ATIVA',1,?)
        """, activation, benefit, user, start, deadline, start);
    jdbc.update("""
        INSERT INTO story_anuncio(id,status,inicio_em,fim_em,criado_por,criado_em,atualizado_em,
          arquivo_midia_id,modo_conteudo,ativacao_beneficio_id,idempotency_key,request_fingerprint)
        VALUES (?,'PUBLICADO',?,?,?,?,?,?,'MIDIA_UPLOAD',?,'story-direto-pg17',?)
        """, story, start, deadline, user, start, start, file, activation, "b".repeat(64));
    return story;
  }

  private static void insertLink(JdbcTemplate jdbc, UUID id, UUID ad, UUID file,
      int order, OffsetDateTime at) {
    jdbc.update("""
        INSERT INTO anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
          visibilidade_midia,criado_em,atualizado_em)
        VALUES (?,?,?,'FOTO',?,?,'PUBLICAVEL','LIVRE',?,?)
        """, id, ad, file, order == 0 ? "CAPA" : "GALERIA", order, at, at);
  }

  private static MidiaPublicaDto publicPhoto(UUID id) {
    return new MidiaPublicaDto(id, "FOTO", "GALERIA", 0, "LIVRE", true,
        "https://example.invalid/foto.jpg", null, null, 100, 100, "image/jpeg");
  }

  private static String key(UUID version, UUID link, UUID file) {
    return "hml/private/arquivo-publicidade/stories/" + version + "/"
        + link + "/" + file + "/original";
  }

  private static void flyway(String container, String network, String credential,
      String... operation) throws Exception {
    flywayAtPath(MIGRATIONS, container, network, credential, operation);
  }

  private static void flywayAtPath(Path migrations, String container, String network,
      String credential, String... operation) throws Exception {
    String[] base = {"docker", "run", "--pull=never", "--rm", "--network", network,
        "-e", "FLYWAY_PASSWORD", "-v", migrations + ":/flyway/sql:ro",
        "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/" + DATABASE,
        "-user=" + USER, "-locations=filesystem:/flyway/sql"};
    String[] args = new String[base.length + operation.length];
    System.arraycopy(base, 0, args, 0, base.length);
    System.arraycopy(operation, 0, args, base.length, operation.length);
    command(Map.of("FLYWAY_PASSWORD", credential), args);
  }

  private static void awaitPostgres(String container, String credential) throws Exception {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (commandIgnoringFailure(Map.of("PGPASSWORD", credential),
          "docker", "exec", "-e", "PGPASSWORD", container, "pg_isready",
          "--host", "127.0.0.1", "--username", USER, "--dbname", DATABASE) == 0) {
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
    if (process.waitFor() != 0) {
      throw new IllegalStateException(String.join(" ", args) + " falhou: " + output);
    }
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

  private record Fixture(UUID user, UUID ad, UUID story, UUID firstMedia, UUID secondMedia,
      UUID firstLink, UUID secondLink, UUID firstPeriod, UUID firstVersion, byte[] bytes) {
  }
}
