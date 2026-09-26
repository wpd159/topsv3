package br.com.topsdojob.v3.application.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeAccessAuditService;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeService;
import br.com.topsdojob.v3.application.admin.arquivo.FinalidadeAcessoArquivoPublicidade;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Disposable PostgreSQL 17 proof of a transactional withdrawal and one shared verified copy. */
@EnabledIfEnvironmentVariable(named = "ARQUIVO_PUBLICIDADE_POSTGRES17_ENABLED", matches = "true")
class ArquivoPublicidadeRetiradaPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String DATABASE = "topsv3_retirada";
  private static final String USER = "topsv3test";

  @Test
  void rollbackRestauraRetiradaECommitReusaCopiaSemAcessarR2() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-retirada-" + suffix + "-net";
    String container = "topsv3-retirada-" + suffix + "-pg17";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    command("docker", "network", "create", network);
    try {
      command(Map.of("POSTGRES_PASSWORD", credential),
          "docker", "run", "--pull=never", "-d", "--name", container,
          "--network", network, "-p", "127.0.0.1::5432",
          "-e", "POSTGRES_DB=" + DATABASE, "-e", "POSTGRES_USER=" + USER,
          "-e", "POSTGRES_PASSWORD", "postgres:17.10-alpine");
      awaitPostgres(container, credential);
      flyway(container, network, credential, "-target=55", "migrate");
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + mappedPort(container) + "/" + DATABASE,
          USER, credential);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class))
          .isBetween(170000, 179999);
      Fixture fixture = seed(jdbc);
      Map<String, StoredObject> privateObjects = new ConcurrentHashMap<>();
      ObjectStorage storage = mock(ObjectStorage.class);
      when(storage.get(any(), any())).thenAnswer(call -> {
        StorageArea area = call.getArgument(0);
        String key = call.getArgument(1);
        if (area == StorageArea.PUBLIC_MEDIA && key.startsWith("hml/public/")) {
          return new StoredObject(fixture.bytes(), "image/jpeg");
        }
        return area == StorageArea.PRIVATE_MEDIA ? privateObjects.get(key) : null;
      });
      when(storage.putIfAbsent(any(), any(), any(), any())).thenAnswer(call -> {
        String key = call.getArgument(1);
        StoredObject value = new StoredObject(call.getArgument(2), call.getArgument(3));
        return privateObjects.putIfAbsent(key, value) == null
            ? ObjectWriteResult.CREATED : ObjectWriteResult.ALREADY_EXISTS;
      });
      doAnswer(call -> {
        privateObjects.remove(call.getArgument(1));
        return null;
      }).when(storage).delete(any(), any());
      @SuppressWarnings("unchecked")
      ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
      doReturn(storage).when(provider).getIfAvailable();
      R2StorageProperties properties = new R2StorageProperties();
      properties.setEnabled(true);
      properties.setPublicMediaBucket("publico");
      properties.setPublicMediaPrefix("hml/public/");
      properties.setPrivateMediaBucket("privado");
      properties.setPrivateMediaPrefix("hml/private/");
      PremiumPublicoMapper premium = mock(PremiumPublicoMapper.class);
      when(premium.idsAtivacoesComEfeitoPublico(fixture.ad()))
          .thenReturn(java.util.Set.of(fixture.activation()));
      when(premium.flagsPorAnuncioIds(List.of(fixture.ad())))
          .thenReturn(Map.of(fixture.ad(), PremiumPublicoFlagsDto.vazio()));
      EntityManager entityManager = mock(EntityManager.class);
      var writer = new ArquivoPublicidadeRegistroService(
          new NamedParameterJdbcTemplate(jdbc), entityManager,
          new ObjectMapper().findAndRegisterModules(), provider, properties,
          mock(ArquivoPublicidadeStoryRegistroService.class), premium,
          mock(ArquivoPublicidadeTransicaoTemporalService.class));
      TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

      tx.executeWithoutResult(ignored -> writer.registrarEstado(
          fixture.ad(), "PUBLICACAO", "req-publicacao", OffsetDateTime.now(ZoneOffset.UTC)));
      UUID period = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_veiculacao WHERE anuncio_id=?", UUID.class,
          fixture.ad());
      UUID firstVersion = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_versao WHERE veiculacao_id=? AND numero=1",
          UUID.class, period);
      UUID source = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_midia WHERE versao_id=? AND anuncio_midia_id=?",
          UUID.class, firstVersion, fixture.survivor());
      assertThat(privateObjects).hasSize(2);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_midia WHERE versao_id=?", Long.class,
          firstVersion)).isEqualTo(2L);

      String originalChecksum = jdbc.queryForObject(
          "SELECT checksum::text FROM flyway_schema_history WHERE version::integer=55",
          String.class);
      flyway(container, network, credential, "-target=56", "migrate");
      flyway(container, network, credential, "-target=56", "validate");
      assertThat(jdbc.queryForObject(
          "SELECT checksum::text FROM flyway_schema_history WHERE version::integer=55",
          String.class)).isEqualTo(originalChecksum);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_midia WHERE id=? AND versao_id=?",
          Long.class, source, firstVersion)).isEqualTo(1L);

      clearInvocations(storage);
      when(provider.getIfAvailable()).thenThrow(new IllegalStateException("R2 indisponivel"));
      assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
        jdbc.update("UPDATE anuncio_midia SET status='REMOVIDA' WHERE id=?", fixture.removed());
        writer.registrarEstado(fixture.ad(), "MIDIA_REMOVIDA_PELO_PROPRIETARIO",
            "req-rollback", OffsetDateTime.now(ZoneOffset.UTC));
        throw new IllegalStateException("rollback sintetico apos referencia");
      })).isInstanceOf(IllegalStateException.class).hasMessageContaining("rollback sintetico");
      assertThat(jdbc.queryForObject(
          "SELECT status FROM anuncio_midia WHERE id=?", String.class,
          fixture.removed())).isEqualTo("PUBLICAVEL");
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_versao WHERE veiculacao_id=?",
          Long.class, period)).isEqualTo(1L);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_midia_referencia", Long.class)).isZero();
      verify(storage, never()).get(any(), any());
      verify(storage, never()).putIfAbsent(any(), any(), any(), any());
      verify(storage, never()).delete(any(), any());

      tx.executeWithoutResult(ignored -> {
        jdbc.update("UPDATE anuncio_midia SET status='REMOVIDA' WHERE id=?", fixture.removed());
        writer.registrarEstado(fixture.ad(), "MIDIA_REMOVIDA_PELO_PROPRIETARIO",
            "req-commit", OffsetDateTime.now(ZoneOffset.UTC));
      });
      UUID secondVersion = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_versao WHERE veiculacao_id=? AND numero=2",
          UUID.class, period);
      UUID reference = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_midia_referencia WHERE versao_id=?",
          UUID.class, secondVersion);
      assertThat(jdbc.queryForObject(
          "SELECT origem_midia_id FROM arquivo_publicidade_midia_referencia WHERE id=?",
          UUID.class, reference)).isEqualTo(source);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_midia WHERE versao_id=?", Long.class,
          secondVersion)).isZero();
      assertThat(privateObjects).hasSize(2);
      verify(storage, never()).get(any(), any());
      verify(storage, never()).putIfAbsent(any(), any(), any(), any());

      doReturn(storage).when(provider).getIfAvailable();
      AdminArquivoPublicidadeService admin = new AdminArquivoPublicidadeService(jdbc,
          new ObjectMapper().findAndRegisterModules(), provider, properties,
          mock(AdminArquivoPublicidadeAccessAuditService.class));
      var finalidade = FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA;
      var detail = admin.detalhar(period, fixture.user(), "req-auditoria", finalidade, true);
      assertThat(detail.versoes()).hasSize(2);
      assertThat(detail.versoes().get(1).midias()).extracting("id").contains(reference);
      assertThat(admin.midia(period, reference, fixture.user(), "req-bytes", finalidade).bytes())
          .isEqualTo(fixture.bytes());

      conferirReusoTextualERollback(jdbc, tx, writer, premium, storage, privateObjects, admin);

      PromotionFixture promotion = seedPromotion(jdbc);
      Fixture promotedAd = promotion.base();
      when(premium.idsAtivacoesComEfeitoPublico(promotedAd.ad()))
          .thenReturn(Set.of(promotedAd.activation()));
      when(premium.flagsPorAnuncioIds(List.of(promotedAd.ad())))
          .thenReturn(Map.of(promotedAd.ad(), PremiumPublicoFlagsDto.vazio()));
      tx.executeWithoutResult(ignored -> writer.registrarEstado(promotedAd.ad(),
          "PUBLICACAO", "req-promocao-inicial", OffsetDateTime.now(ZoneOffset.UTC)));
      UUID promotedPeriod = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_veiculacao WHERE anuncio_id=?",
          UUID.class, promotedAd.ad());
      Set<UUID> selectedBefore = tx.execute(ignored ->
          writer.midiasExibidasAntesDaRetirada(promotedAd.ad()));
      assertThat(selectedBefore).hasSize(4)
          .contains(promotedAd.removed(), promotedAd.survivor(),
              promotion.third(), promotion.fourth())
          .doesNotContain(promotion.promoted());
      AnuncioEntity promotedEntity = mock(AnuncioEntity.class);
      when(promotedEntity.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
      when(promotedEntity.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.APROVADO);
      when(entityManager.find(AnuncioEntity.class, promotedAd.ad()))
          .thenReturn(promotedEntity);
      AnuncioMidiaEntity promotedLink = mock(AnuncioMidiaEntity.class);
      when(promotedLink.getAnuncioId()).thenReturn(promotedAd.ad());
      when(promotedLink.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
      when(promotedLink.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
      when(promotedLink.getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
      when(entityManager.find(AnuncioMidiaEntity.class, promotion.promoted()))
          .thenReturn(promotedLink);
      doAnswer(call -> {
        jdbc.update("UPDATE anuncio_midia SET status='PENDENTE' WHERE id=?",
            promotion.promoted());
        return null;
      }).when(promotedLink).aplicarDecisao(eq(StatusAnuncioMidia.PENDENTE),
          eq(VisibilidadeMidia.LIVRE), any());
      clearInvocations(storage);
      when(provider.getIfAvailable()).thenThrow(new IllegalStateException("R2 indisponivel"));
      tx.executeWithoutResult(ignored -> {
        jdbc.update("UPDATE anuncio_midia SET status='REMOVIDA' WHERE id=?",
            promotedAd.removed());
        List<UUID> suppressed = writer.prepararRetiradaSemNovaCopia(promotedAd.ad(),
            promotedAd.user(), "req-promocao", OffsetDateTime.now(ZoneOffset.UTC),
            selectedBefore);
        assertThat(suppressed).containsExactly(promotion.promoted());
        assertThat(writer.possuiFotoPublicaSelecionada(promotedAd.ad())).isTrue();
        writer.registrarEstado(promotedAd.ad(), "MIDIA_REMOVIDA_PELO_PROPRIETARIO",
            "req-promocao", OffsetDateTime.now(ZoneOffset.UTC));
      });
      assertThat(jdbc.queryForObject(
          "SELECT status FROM anuncio_midia WHERE id=?", String.class,
          promotion.promoted())).isEqualTo("PENDENTE");
      for (UUID survivor : List.of(promotedAd.survivor(), promotion.third(), promotion.fourth())) {
        assertThat(jdbc.queryForObject(
            "SELECT status FROM anuncio_midia WHERE id=?", String.class,
            survivor)).isEqualTo("PUBLICAVEL");
      }
      UUID promotedSecondVersion = jdbc.queryForObject(
          "SELECT id FROM arquivo_publicidade_versao WHERE veiculacao_id=? AND numero=2",
          UUID.class, promotedPeriod);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_midia_referencia WHERE versao_id=?",
          Long.class, promotedSecondVersion)).isEqualTo(3L);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM arquivo_publicidade_midia WHERE versao_id=?",
          Long.class, promotedSecondVersion)).isZero();
      verify(storage, never()).get(any(), any());
      verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    } finally {
      commandIgnoringFailure("docker", "rm", "-f", container);
      commandIgnoringFailure("docker", "network", "rm", network);
    }
  }

  private static void conferirReusoTextualERollback(JdbcTemplate jdbc, TransactionTemplate tx,
      ArquivoPublicidadeRegistroService writer, PremiumPublicoMapper premium,
      ObjectStorage storage, Map<String, StoredObject> privateObjects,
      AdminArquivoPublicidadeService admin) throws Exception {
    Fixture fixture = seed(jdbc);
    when(premium.idsAtivacoesComEfeitoPublico(fixture.ad()))
        .thenReturn(Set.of(fixture.activation()));
    when(premium.flagsPorAnuncioIds(List.of(fixture.ad())))
        .thenReturn(Map.of(fixture.ad(), PremiumPublicoFlagsDto.vazio()));
    clearInvocations(storage);
    tx.executeWithoutResult(ignored -> writer.registrarEstado(fixture.ad(),
        "PUBLICACAO", "req-reuso-inicial", OffsetDateTime.now(ZoneOffset.UTC)));
    verify(storage, times(2)).putIfAbsent(any(), any(), any(), any());
    UUID period = jdbc.queryForObject(
        "SELECT id FROM arquivo_publicidade_veiculacao WHERE anuncio_id=?",
        UUID.class, fixture.ad());
    UUID hold = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.update("""
        INSERT INTO arquivo_publicidade_hold
          (id,veiculacao_id,fundamento,responsavel_usuario_id,inicio_em,revisar_em)
        VALUES (?,?,'Hold sintetico do reuso',?,?,?)
        """, hold, period, fixture.user(), now, now.plusDays(1));
    Map<String, StoredObject> before = Map.copyOf(privateObjects);

    clearInvocations(storage);
    tx.executeWithoutResult(ignored -> {
      jdbc.update("UPDATE anuncio SET titulo='Texto alterado sem trocar fotos' WHERE id=?",
          fixture.ad());
      writer.registrarEstado(fixture.ad(), "EDICAO_ADMINISTRATIVA", "req-reuso-texto",
          OffsetDateTime.now(ZoneOffset.UTC));
    });
    verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    verify(storage, never()).delete(any(), any());
    assertThat(privateObjects).containsExactlyInAnyOrderEntriesOf(before);
    UUID textVersion = jdbc.queryForObject("""
        SELECT id FROM arquivo_publicidade_versao WHERE veiculacao_id=? AND numero=2
        """, UUID.class, period);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM arquivo_publicidade_midia WHERE versao_id=?",
        Long.class, textVersion)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM arquivo_publicidade_midia_referencia WHERE versao_id=?",
        Long.class, textVersion)).isEqualTo(2L);
    var finalidade = FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA;
    var detail = admin.detalhar(period, fixture.user(), "req-reuso-exportacao", finalidade, true);
    assertThat(detail.versoes()).hasSize(2);
    assertThat(detail.versoes().get(1).midias()).hasSize(2);
    for (var media : detail.versoes().get(1).midias()) {
      assertThat(admin.midia(period, media.id(), fixture.user(), "req-reuso-bytes", finalidade)
          .bytes()).isEqualTo(fixture.bytes());
    }

    byte[] changed = "nova-foto-sintetica-do-reuso".getBytes(StandardCharsets.UTF_8);
    String changedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(changed));
    String changedKey = "hml/public/reuso-" + fixture.ad() + ".jpg";
    when(storage.get(StorageArea.PUBLIC_MEDIA, changedKey))
        .thenReturn(new StoredObject(changed, "image/jpeg"));
    Runnable editOnePhoto = () -> {
      jdbc.update("UPDATE anuncio SET titulo='Texto com uma foto alterada' WHERE id=?", fixture.ad());
      jdbc.update("""
          UPDATE arquivo_midia SET chave_objeto=?,sha256=?,tamanho_bytes=?
          WHERE id=(SELECT arquivo_midia_id FROM anuncio_midia WHERE id=?)
          """, changedKey, changedHash, changed.length, fixture.removed());
      writer.registrarEstado(fixture.ad(), "EDICAO_ADMINISTRATIVA", "req-reuso-misto",
          OffsetDateTime.now(ZoneOffset.UTC));
    };
    clearInvocations(storage);
    assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
      editOnePhoto.run();
      throw new IllegalStateException("rollback sintetico depois de referencia e copia");
    })).isInstanceOf(IllegalStateException.class).hasMessageContaining("rollback sintetico");
    verify(storage, times(1)).putIfAbsent(any(), any(), any(), any());
    verify(storage, times(1)).delete(any(), any());
    assertThat(privateObjects).containsExactlyInAnyOrderEntriesOf(before);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM arquivo_publicidade_versao WHERE veiculacao_id=?",
        Long.class, period)).isEqualTo(2L);

    clearInvocations(storage);
    tx.executeWithoutResult(ignored -> editOnePhoto.run());
    verify(storage, times(1)).putIfAbsent(any(), any(), any(), any());
    verify(storage, never()).delete(any(), any());
    assertThat(privateObjects).hasSize(before.size() + 1).containsAllEntriesOf(before);
    UUID mixedVersion = jdbc.queryForObject("""
        SELECT id FROM arquivo_publicidade_versao WHERE veiculacao_id=? AND numero=3
        """, UUID.class, period);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM arquivo_publicidade_midia WHERE versao_id=?",
        Long.class, mixedVersion)).isEqualTo(1L);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM arquivo_publicidade_midia_referencia WHERE versao_id=?",
        Long.class, mixedVersion)).isEqualTo(1L);
    UUID reusedReference = jdbc.queryForObject(
        "SELECT id FROM arquivo_publicidade_midia_referencia WHERE versao_id=?",
        UUID.class, mixedVersion);
    assertThat(admin.midia(period, reusedReference, fixture.user(), "req-reuso-cadeia", finalidade)
        .bytes()).isEqualTo(fixture.bytes());
    tx.executeWithoutResult(ignored -> {
      jdbc.update("UPDATE anuncio SET status='PAUSADO' WHERE id=?", fixture.ad());
      writer.registrarEstado(fixture.ad(), "ENCERRAMENTO", "req-reuso-fim",
          OffsetDateTime.now(ZoneOffset.UTC));
    });
    assertThat(jdbc.queryForObject(
        "SELECT fim_em IS NOT NULL FROM arquivo_publicidade_veiculacao WHERE id=?",
        Boolean.class, period)).isTrue();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM arquivo_publicidade_hold WHERE id=? AND encerrado_em IS NULL",
        Long.class, hold)).isEqualTo(1L);
    assertThat(privateObjects).hasSize(before.size() + 1).containsAllEntriesOf(before);
    System.out.println("ARCHIVE_REUSE_PG initialPut=2 textPut=0 changedVariantPut=1 rollbackDeletedOwn=1 reusedDeleted=0 holdPreserved=true");
  }

  private static Fixture seed(JdbcTemplate jdbc) throws Exception {
    UUID user = UUID.randomUUID();
    UUID ad = UUID.randomUUID();
    UUID benefit = jdbc.queryForObject(
        "SELECT id FROM beneficio_premium WHERE codigo='ANUNCIO_TOPO'", UUID.class);
    UUID activation = UUID.randomUUID();
    UUID removedFile = UUID.randomUUID();
    UUID survivorFile = UUID.randomUUID();
    UUID removed = UUID.randomUUID();
    UUID survivor = UUID.randomUUID();
    byte[] bytes = "retirada-pg17-sintetica".getBytes(StandardCharsets.UTF_8);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    OffsetDateTime start = now.minusHours(1);
    OffsetDateTime end = now.plusDays(1);
    jdbc.update("""
        INSERT INTO usuario(id,nome,email_normalizado,status,tipo_conta,criado_em,atualizado_em)
        VALUES (?,'Pessoa sintetica',?,'ATIVO','ANUNCIANTE',?,?)
        """, user, "retirada-" + user + "@example.invalid", start, start);
    jdbc.update("""
        INSERT INTO anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,
          categoria,criado_em,atualizado_em)
        VALUES (?,? ,?,'Anuncio sintetico','Conteudo sintetico','PUBLICADO','APROVADO',
          'OUTROS',?,?)
        """, ad, user, "retirada-" + ad, start, start);
    jdbc.update("""
        INSERT INTO ativacao_beneficio(id,beneficio_id,usuario_id,anuncio_id,origem,
          inicio_em,fim_em,status,custo_creditos_snapshot,criado_em)
        VALUES (?,?,?,?,'CREDITO',?,?,'ATIVA',1,?)
        """, activation, benefit, user, ad, start, end, start);
    insertFile(jdbc, removedFile, "hml/public/" + ad + "/removida.jpg", hash, bytes.length, start);
    insertFile(jdbc, survivorFile, "hml/public/" + ad + "/sobrevivente.jpg", hash, bytes.length, start);
    insertLink(jdbc, removed, ad, removedFile, "CAPA", 0, start);
    insertLink(jdbc, survivor, ad, survivorFile, "GALERIA", 1, start);
    return new Fixture(user, ad, activation, removed, survivor, bytes);
  }

  private static PromotionFixture seedPromotion(JdbcTemplate jdbc) throws Exception {
    Fixture base = seed(jdbc);
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(base.bytes()));
    OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
    UUID third = UUID.randomUUID();
    UUID fourth = UUID.randomUUID();
    UUID promoted = UUID.randomUUID();
    for (int order = 2; order <= 4; order++) {
      UUID file = UUID.randomUUID();
      UUID link = order == 2 ? third : order == 3 ? fourth : promoted;
      insertFile(jdbc, file, "hml/public/extra-" + link + ".jpg", hash,
          base.bytes().length, start);
      insertLink(jdbc, link, base.ad(), file, "GALERIA", order, start);
    }
    return new PromotionFixture(base, third, fourth, promoted);
  }

  private static void insertFile(JdbcTemplate jdbc, UUID id, String key,
      String hash, int size, OffsetDateTime at) {
    jdbc.update("""
        INSERT INTO arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,
          tamanho_bytes,sha256,status_arquivo,criado_em)
        VALUES (?,'R2','publico',?,'image/jpeg',?,?,'VALIDADO',?)
        """, id, key, size, hash, at);
  }

  private static void insertLink(JdbcTemplate jdbc, UUID id, UUID ad, UUID file,
      String purpose, int order, OffsetDateTime at) {
    jdbc.update("""
        INSERT INTO anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
          visibilidade_midia,criado_em,atualizado_em)
        VALUES (?,?,?,'FOTO',?,?,'PUBLICAVEL','LIVRE',?,?)
        """, id, ad, file, purpose, order, at, at);
  }

  private static void flyway(String container, String network, String credential,
      String... operation)
      throws Exception {
    String[] base = {"docker", "run", "--pull=never", "--rm",
        "--network", network, "-e", "FLYWAY_PASSWORD",
        "-v", MIGRATIONS + ":/flyway/sql:ro", "flyway/flyway:12.10.0",
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

  private record Fixture(UUID user, UUID ad, UUID activation, UUID removed,
      UUID survivor, byte[] bytes) {
  }

  private record PromotionFixture(Fixture base, UUID third, UUID fourth,
      UUID promoted) {
  }
}
