package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresInitializer;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresSupport;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TopsDoJobBackendApplication.class, initializers = PostgresInitializer.class)
@Import(AdminAprovacaoReconciliacaoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "APROVACAO_RECONCILIACAO_POSTGRES17_ENABLED", matches = "true")
class AdminAprovacaoReconciliacaoPostgres17IntegrationTest {
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-20T12:00:00Z");

  @Autowired private AdminAprovacaoReconciliacaoService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  @AfterAll
  static void cleanup() throws Exception { PostgresSupport.stop(); }

  @Test
  void confirmacaoExigeOperacaoAnuncioRevisaoAtorVersaoELeituraNaoMuta() throws Exception {
    Fixture fixture = fixture();
    UUID operacao = UUID.randomUUID();
    naMesmaTransacao(writer -> {
      inserirDecisao(writer, fixture.revisaoId(), fixture.ator().usuarioId());
      inserirAuditoria(writer, "MODERACAO_REVISAO_DECIDIR", "REVISAO_ANUNCIO",
          fixture.revisaoId(), fixture, operacao, 7, "PUBLICADO");
    });
    int auditorias = contar("auditoria_evento");
    int decisoes = contar("decisao_moderacao");

    for (int tentativa = 0; tentativa < 2; tentativa++) {
      assertThat(consultar(fixture, operacao, 7, fixture.revisaoId(), fixture.ator()).estado())
          .isEqualTo("CONFIRMADA");
      assertThat(consultar(fixture, operacao, 7, null, fixture.ator()).revisaoIdConfirmada())
          .isEqualTo(fixture.revisaoId());
    }
    assertThat(contar("auditoria_evento")).isEqualTo(auditorias);
    assertThat(contar("decisao_moderacao")).isEqualTo(decisoes);

    assertInconclusiva(fixture, UUID.randomUUID(), 7, fixture.revisaoId(), fixture.ator());
    assertInconclusiva(fixture, operacao, 8, fixture.revisaoId(), fixture.ator());
    assertInconclusiva(fixture, operacao, 7, UUID.randomUUID(), fixture.ator());
    assertInconclusiva(fixture, operacao, 7, fixture.revisaoId(), fixture.outroAtor());
    assertThat(service.consultar(UUID.randomUUID(), operacao, 7, fixture.revisaoId(), fixture.ator()).estado())
        .isEqualTo("INCONCLUSIVA");
  }

  @Test
  void estadoPublicadoSemAuditoriaOuDecisaoNaoProvaTentativa() {
    Fixture fixture = fixture(); // O anuncio e a revisao ja estao aprovados na fixture.
    assertInconclusiva(fixture, UUID.randomUUID(), 7, fixture.revisaoId(), fixture.ator());
  }

  @Test
  void somenteCommitTornaEvidenciaVisivelERollbackPermaneceInconclusivo() throws Exception {
    Fixture fixture = fixture();
    UUID confirmada = UUID.randomUUID();
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      JdbcTemplate writer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      inserirDecisao(writer, fixture.revisaoId(), fixture.ator().usuarioId());
      inserirAuditoria(writer, "MODERACAO_REVISAO_DECIDIR", "REVISAO_ANUNCIO",
          fixture.revisaoId(), fixture, confirmada, 7, "PUBLICADO");
      assertInconclusiva(fixture, confirmada, 7, fixture.revisaoId(), fixture.ator());
      connection.commit();
    }
    assertThat(consultar(fixture, confirmada, 7, fixture.revisaoId(), fixture.ator()).estado())
        .isEqualTo("CONFIRMADA");

    UUID revertida = UUID.randomUUID();
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      JdbcTemplate writer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      inserirAuditoria(writer, "MODERACAO_REVISAO_DECIDIR", "REVISAO_ANUNCIO",
          fixture.revisaoId(), fixture, revertida, 7, "PUBLICADO");
      assertInconclusiva(fixture, revertida, 7, fixture.revisaoId(), fixture.ator());
      connection.rollback();
    }
    assertInconclusiva(fixture, revertida, 7, fixture.revisaoId(), fixture.ator());
  }

  @Test
  void mesmaDataEAtorEmTransacoesDistintasNaoProvamAOperacao() {
    Fixture fixture = fixture();
    UUID operacao = UUID.randomUUID();
    // Ambas usam AGORA, mas cada insert autocommit recebe uma transacao PostgreSQL distinta.
    inserirDecisao(jdbc, fixture.revisaoId(), fixture.ator().usuarioId());
    inserirAuditoria(jdbc, "MODERACAO_REVISAO_DECIDIR", "REVISAO_ANUNCIO",
        fixture.revisaoId(), fixture, operacao, 7, "PUBLICADO");
    assertInconclusiva(fixture, operacao, 7, fixture.revisaoId(), fixture.ator());
  }

  @Test
  void regularizacaoLegadaEConfirmadaComoPublicacaoSemInventarNovaDecisao() {
    Fixture fixture = fixture();
    UUID operacao = UUID.randomUUID();
    inserirAuditoria(jdbc, "MODERACAO_ANUNCIO_PUBLICACAO_REGULARIZAR", "ANUNCIO",
        fixture.anuncioId(), fixture, operacao, 7, "PUBLICADO");

    var resultado = consultar(fixture, operacao, 7, null, fixture.ator());
    assertThat(resultado.estado()).isEqualTo("PUBLICACAO_REGULARIZADA");
    assertThat(resultado.revisaoIdConfirmada()).isNull();
    assertInconclusiva(fixture, operacao, 7, fixture.revisaoId(), fixture.ator());
    assertInconclusiva(fixture, operacao, 7, null, fixture.outroAtor());
  }

  @Test
  void regularizacaoDeRevisaoExigeMesmoIdDeRevisao() {
    Fixture fixture = fixture();
    UUID operacao = UUID.randomUUID();
    inserirAuditoria(jdbc, "MODERACAO_REVISAO_PUBLICACAO_REGULARIZAR", "REVISAO_ANUNCIO",
        fixture.revisaoId(), fixture, operacao, 7, "PUBLICADO");

    assertThat(consultar(fixture, operacao, 7, fixture.revisaoId(), fixture.ator()).estado())
        .isEqualTo("PUBLICACAO_REGULARIZADA");
    assertInconclusiva(fixture, operacao, 7, UUID.randomUUID(), fixture.ator());
  }

  @Test
  void evidenciasDuplicadasPermanecemInconclusivas() throws Exception {
    Fixture fixture = fixture();
    UUID operacao = UUID.randomUUID();
    naMesmaTransacao(writer -> {
      inserirDecisao(writer, fixture.revisaoId(), fixture.ator().usuarioId());
      for (int i = 0; i < 2; i++) {
        inserirAuditoria(writer, "MODERACAO_REVISAO_DECIDIR", "REVISAO_ANUNCIO",
            fixture.revisaoId(), fixture, operacao, 7, "PUBLICADO");
      }
    });
    assertInconclusiva(fixture, operacao, 7, fixture.revisaoId(), fixture.ator());
  }

  @Test
  void auditoriaDeAprovacaoSemEstadoPublicadoNaoProvaConclusao() throws Exception {
    Fixture fixture = fixture();
    UUID operacao = UUID.randomUUID();
    naMesmaTransacao(writer -> {
      inserirDecisao(writer, fixture.revisaoId(), fixture.ator().usuarioId());
      inserirAuditoria(writer, "MODERACAO_REVISAO_DECIDIR", "REVISAO_ANUNCIO",
          fixture.revisaoId(), fixture, operacao, 7, "PENDENTE_REVISAO");
    });
    assertInconclusiva(fixture, operacao, 7, fixture.revisaoId(), fixture.ator());
  }

  @Test
  void entradaInvalidaOuSemAtorNaoProduzConsulta() {
    Fixture fixture = fixture();
    assertThatThrownBy(() -> service.consultar(fixture.anuncioId(), UUID.randomUUID(), 7, null, null))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.consultar(fixture.anuncioId(), UUID.randomUUID(), -1, null, fixture.ator()))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private AdminAprovacaoReconciliacaoService.Resultado consultar(
      Fixture fixture, UUID operacao, int versao, UUID revisao, AdminUserPrincipal ator) {
    return service.consultar(fixture.anuncioId(), operacao, versao, revisao, ator);
  }

  private void assertInconclusiva(
      Fixture fixture, UUID operacao, int versao, UUID revisao, AdminUserPrincipal ator) {
    assertThat(consultar(fixture, operacao, versao, revisao, ator).estado()).isEqualTo("INCONCLUSIVA");
  }

  private int contar(String tabela) {
    return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
  }

  private void naMesmaTransacao(Consumer<JdbcTemplate> gravar) throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      JdbcTemplate writer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      try {
        gravar.accept(writer);
        connection.commit();
      } catch (RuntimeException exception) {
        connection.rollback();
        throw exception;
      }
    }
  }

  private Fixture fixture() {
    UUID proprietarioId = UUID.randomUUID();
    UUID atorId = UUID.randomUUID();
    UUID outroAtorId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    UUID revisaoId = UUID.randomUUID();
    for (UUID id : List.of(proprietarioId, atorId, outroAtorId)) {
      jdbc.update("""
          insert into usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
          values (?, 'Pessoa sintetica', 'ATIVO', ?, ?, ?, 0)
          """, id, id.equals(proprietarioId) ? "ANUNCIANTE" : "STAFF", AGORA, AGORA);
    }
    jdbc.update("""
        insert into anuncio (id, usuario_id, slug, titulo, status, status_moderacao,
          categoria, criado_em, atualizado_em, versao)
        values (?, ?, ?, 'Anuncio sintetico', 'PUBLICADO', 'APROVADO', 'TESTE', ?, ?, 7)
        """, anuncioId, proprietarioId,
        "reconciliacao-" + anuncioId.toString().replace("-", ""), AGORA, AGORA);
    jdbc.update("""
        insert into revisao_anuncio (id, anuncio_id, tipo, status, criado_em, finalizado_em)
        values (?, ?, 'EDICAO', 'APROVADA', ?, ?)
        """, revisaoId, anuncioId, AGORA, AGORA);
    return new Fixture(anuncioId, revisaoId, ator(atorId), ator(outroAtorId));
  }

  private AdminUserPrincipal ator(UUID id) {
    return new AdminUserPrincipal(id, "Admin sintetico", "admin@example.invalid", "n/a",
        List.of(PapelUsuario.ADMIN), List.of(), List.of(), true);
  }

  private void inserirDecisao(JdbcTemplate writer, UUID revisaoId, UUID atorId) {
    writer.update("""
        insert into decisao_moderacao (id, revisao_anuncio_id, decisao, ator_usuario_id, criado_em)
        values (?, ?, 'APROVAR', ?, ?)
        """, UUID.randomUUID(), revisaoId, atorId, AGORA);
  }

  private void inserirAuditoria(
      JdbcTemplate writer, String acao, String recursoTipo, UUID recursoId,
      Fixture fixture, UUID operacao, int versao, String statusAnuncio) {
    String revisaoJson = "REVISAO_ANUNCIO".equals(recursoTipo)
        ? "\"" + fixture.revisaoId() + "\"" : "null";
    String snapshot = """
        {"anuncioId":"%s","revisaoId":%s,"operacaoIdCliente":"%s",
         "versaoAnuncioAntes":%d,"decisao":"APROVAR","statusAnuncio":"%s",
         "statusRevisao":"APROVADA","statusModeracao":"APROVADO"}
        """.formatted(fixture.anuncioId(), revisaoJson, operacao, versao, statusAnuncio);
    writer.update("""
        insert into auditoria_evento (id, ator_usuario_id, acao, recurso_tipo, recurso_id,
          depois_json, request_id, origem, resultado, criado_em)
        values (?, ?, ?, ?, ?, cast(? as jsonb), 'request-sintetico', 'ADMIN', 'SUCESSO', ?)
        """, UUID.randomUUID(), fixture.ator().usuarioId(), acao, recursoTipo, recursoId,
        snapshot, AGORA);
  }

  private record Fixture(
      UUID anuncioId, UUID revisaoId, AdminUserPrincipal ator, AdminUserPrincipal outroAtor) { }
}
