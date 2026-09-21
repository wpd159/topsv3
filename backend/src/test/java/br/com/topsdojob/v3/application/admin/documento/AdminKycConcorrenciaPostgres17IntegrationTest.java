package br.com.topsdojob.v3.application.admin.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceDocumentoAuditService;
import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceDocumentoAuditService.Etapa;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresInitializer;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresSupport;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.net.URI;
import java.sql.Connection;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TopsDoJobBackendApplication.class,
    initializers = AdminKycConcorrenciaPostgres17IntegrationTest.KycInitializer.class)
@Import({AdminKycService.class, AdminComplianceDocumentoAuditService.class,
    AdminKycConcorrenciaPostgres17IntegrationTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "COMPLIANCE_POSTGRES17_ENABLED", matches = "true")
class AdminKycConcorrenciaPostgres17IntegrationTest {
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-20T12:00:00Z");
  @Autowired AdminKycService service;
  @Autowired AdminComplianceDocumentoAuditService auditService;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ObjectStorage storage;

  @BeforeEach
  void prepararStorage() {
    reset(storage);
    when(storage.temporaryGetUrl(any(), any(), any()))
        .thenReturn(URI.create("https://documento.example.invalid/temporario"));
  }

  @AfterAll
  static void cleanup() throws Exception { PostgresSupport.stop(); }

  @Test
  void aprovarVersusRejeitarProduzUmaDecisaoEUmConflitoSemDuplaAuditoria() throws Exception {
    Fixture fixture = fixture();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch iniciadas = new CountDownLatch(2);
    Map<String, Integer> pids = new ConcurrentHashMap<>();
    List<String> resultados;
    try (Connection bloqueador = dataSource.getConnection()) {
      bloqueador.setAutoCommit(false);
      try (var statement = bloqueador.prepareStatement("select id from documento_usuario where envio_id = ? for update")) {
        statement.setObject(1, fixture.envioId());
        try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
      }
      Future<String> aprovar = iniciar(pool, "aprovar", iniciadas, pids,
          () -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, "concorrencia-aprovar"));
      Future<String> rejeitar = iniciar(pool, "rejeitar", iniciadas, pids,
          () -> decidir(fixture, AdminDecisaoModeracaoAcao.REPROVAR, "concorrencia-rejeitar"));
      assertThat(iniciadas.await(10, TimeUnit.SECONDS)).isTrue();
      // A barreira real de PostgreSQL garante sobreposicao: no codigo anterior ambos
      // liam PENDENTE e esperavam no UPDATE; com a protecao esperam antes da leitura.
      aguardar(() -> pids.values().stream().allMatch(this::aguardaLock), "duas transacoes aguardando lock");
      bloqueador.commit();
      resultados = List.of(aprovar.get(10, TimeUnit.SECONDS), rejeitar.get(10, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
    System.out.println("KYC_APPROVE_REJECT resultados=" + resultados + " estado=" + estado(fixture));
    assertThat(resultados).containsExactlyInAnyOrder("OK", "CONFLICT");
    assertThat(decisoes(fixture)).isEqualTo(1);
    String esperado = resultados.get(0).equals("OK") ? "VALIDADO" : "REJEITADO";
    assertThat(estado(fixture)).isEqualTo(esperado);
    assertThat(jdbc.queryForObject("select status_arquivo from arquivo_midia where id = ?", String.class,
        fixture.arquivoId())).isEqualTo(esperado);
    assertThatThrownBy(() -> service.decidir(fixture.envioId(),
        new AdminKycDecisaoRequestDto(AdminDecisaoModeracaoAcao.APROVAR, null), fixture.ator(), "retry-decisao"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(decisoes(fixture)).isEqualTo(1);
  }

  @Test
  void decisaoVersusAberturaNaoRegrideDocumentoDecididoParaEmAnalise() throws Exception {
    Fixture fixture = fixture();
    CountDownLatch aberturaPreparada = new CountDownLatch(1), liberarAbertura = new CountDownLatch(1);
    when(storage.temporaryGetUrl(any(), any(), any())).thenAnswer(invocation -> {
      aberturaPreparada.countDown();
      if (!liberarAbertura.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("barreira documental venceu");
      return URI.create("https://documento.example.invalid/temporario");
    });
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Map<String, Integer> pids = new ConcurrentHashMap<>();
    CountDownLatch decisaoIniciada = new CountDownLatch(1);
    AtomicBoolean decisaoConcluida = new AtomicBoolean();
    try {
      Future<?> abertura = pool.submit(() -> service.urlTemporaria(fixture.documentoId(), fixture.ator(), "abrir"));
      assertThat(aberturaPreparada.await(10, TimeUnit.SECONDS)).isTrue();
      Future<String> decisao = iniciar(pool, "decidir", decisaoIniciada, pids, () -> {
        String resultado = decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, "decidir-abertura");
        return resultado;
      }, decisaoConcluida);
      assertThat(decisaoIniciada.await(10, TimeUnit.SECONDS)).isTrue();
      aguardar(() -> decisaoConcluida.get() || aguardaLock(pids.get("decidir")), "decisao concluida ou serializada");
      liberarAbertura.countDown();
      abertura.get(10, TimeUnit.SECONDS);
      assertThat(decisao.get(10, TimeUnit.SECONDS)).isEqualTo("OK");
    } finally {
      liberarAbertura.countDown();
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
    System.out.println("KYC_DECISION_OPEN estado=" + estado(fixture) + " auditorias=" + decisoes(fixture));
    assertThat(estado(fixture)).isEqualTo("VALIDADO");
    assertThat(decisoes(fixture)).isEqualTo(1);
    service.urlTemporaria(fixture.documentoId(), fixture.ator(), "reabrir-legitimo");
    assertThat(estado(fixture)).isEqualTo("VALIDADO");
    assertThat(jdbc.queryForObject("select count(*) from documento_usuario_acesso where documento_usuario_id = ?",
        Integer.class, fixture.documentoId())).isEqualTo(2);
  }

  @Test
  void auditoriaDeNegativaEFalhaSobreviveRollbackDaOperacaoExterna() {
    Fixture fixture = fixture();
    UUID referencia = UUID.randomUUID();
    assertThatThrownBy(() -> transacao().executeWithoutResult(status -> {
      auditService.registrar(referencia, fixture.ator().usuarioId(), "rollback-negado", Etapa.NEGADO);
      auditService.registrar(referencia, fixture.ator().usuarioId(), "rollback-falha", Etapa.FALHA);
      throw new IllegalStateException("falha sintetica externa");
    })).isInstanceOf(IllegalStateException.class);
    var eventos = jdbc.queryForList("select resultado, depois_json::text as depois from auditoria_evento where recurso_id = ?",
        referencia);
    assertThat(eventos).hasSize(2).allSatisfy(evento -> {
      assertThat(evento.get("resultado")).isEqualTo("ERRO");
      assertThat(evento.get("depois").toString()).doesNotContain("http", "bucket", "session", "token");
    });
  }

  private Future<String> iniciar(ExecutorService pool, String nome, CountDownLatch iniciadas,
      Map<String, Integer> pids, Callable<String> action) {
    return iniciar(pool, nome, iniciadas, pids, action, new AtomicBoolean());
  }

  private Future<String> iniciar(ExecutorService pool, String nome, CountDownLatch iniciadas,
      Map<String, Integer> pids, Callable<String> action, AtomicBoolean concluida) {
    return pool.submit(() -> {
      try {
        return transacao().execute(status -> {
          pids.put(nome, jdbc.queryForObject("select pg_backend_pid()", Integer.class));
          iniciadas.countDown();
          try { return action.call(); }
          catch (RuntimeException exception) { throw exception; }
          catch (Exception exception) { throw new IllegalStateException(exception); }
        });
      } catch (ResponseStatusException exception) {
        if (exception.getStatusCode() == HttpStatus.CONFLICT) return "CONFLICT";
        throw exception;
      } finally { concluida.set(true); }
    });
  }

  private String decidir(Fixture fixture, AdminDecisaoModeracaoAcao decisao, String requestId) {
    service.decidir(fixture.envioId(), new AdminKycDecisaoRequestDto(decisao,
        decisao == AdminDecisaoModeracaoAcao.REPROVAR ? "Documento ficticio ilegivel" : null),
        fixture.ator(), requestId);
    return "OK";
  }

  private boolean aguardaLock(Integer pid) {
    return pid != null && Boolean.TRUE.equals(jdbc.queryForObject(
        "select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')", Boolean.class, pid));
  }

  private void aguardar(BooleanSupplier condition, String descricao) {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline) throw new AssertionError("nao observado: " + descricao);
      LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
    }
  }

  private TransactionTemplate transacao() {
    TransactionTemplate tx = new TransactionTemplate(transactions);
    tx.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    tx.setTimeout(20);
    return tx;
  }

  private int decisoes(Fixture fixture) {
    return jdbc.queryForObject("select count(*) from auditoria_evento where acao = 'KYC_DOCUMENTOS_DECIDIR' and recurso_id = ?",
        Integer.class, fixture.envioId());
  }

  private String estado(Fixture fixture) {
    return jdbc.queryForObject("select status from documento_usuario where id = ?", String.class, fixture.documentoId());
  }

  private Fixture fixture() {
    UUID usuarioId = UUID.randomUUID(), atorId = UUID.randomUUID(), arquivoId = UUID.randomUUID();
    UUID envioId = UUID.randomUUID(), documentoId = UUID.randomUUID();
    for (UUID id : List.of(usuarioId, atorId)) jdbc.update("""
        insert into usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
        values (?, 'KYC sintetico', 'ATIVO', 'ANUNCIANTE', ?, ?, 0)
        """, id, AGORA, AGORA);
    jdbc.update("""
        insert into arquivo_midia (id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes, status_arquivo, criado_em)
        values (?, 'R2', 'documentos-teste', ?, 'application/pdf', 12, 'PENDENTE', ?)
        """, arquivoId, "hml/teste/documentos/" + arquivoId, AGORA);
    jdbc.update("""
        insert into documento_usuario (id, usuario_id, arquivo_midia_id, envio_id, parte, tipo, status, criado_em, atualizado_em)
        values (?, ?, ?, ?, 'UNICO', 'IDENTIDADE', 'PENDENTE', ?, ?)
        """, documentoId, usuarioId, arquivoId, envioId, AGORA, AGORA);
    AdminUserPrincipal ator = new AdminUserPrincipal(atorId, "Admin sintetico", "admin@example.invalid", "n/a",
        List.of(PapelUsuario.ADMIN), List.of(), List.of(), true);
    return new Fixture(envioId, documentoId, arquivoId, ator);
  }

  private record Fixture(UUID envioId, UUID documentoId, UUID arquivoId, AdminUserPrincipal ator) {}

  static class KycInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
      new PostgresInitializer().initialize(context);
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("kyc-sintetico",
          Map.of("app.storage.r2.enabled", "true",
              "app.storage.r2.document-bucket", "documentos-teste",
              "app.storage.r2.document-prefix", "hml/teste/documentos/")));
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestBeans {
    @Bean ObjectStorage storage() { return mock(ObjectStorage.class); }
    @Bean AdminKycThumbnailProcessor thumbnailProcessor() { return mock(AdminKycThumbnailProcessor.class); }
    @Bean DocumentoUploadValidator uploadValidator() { return mock(DocumentoUploadValidator.class); }
    @Bean R2StorageProperties storageProperties() {
      R2StorageProperties properties = new R2StorageProperties();
      properties.setEnabled(true);
      properties.setDocumentBucket("documentos-teste");
      properties.setDocumentPrefix("hml/teste/documentos/");
      return properties;
    }
  }
}
