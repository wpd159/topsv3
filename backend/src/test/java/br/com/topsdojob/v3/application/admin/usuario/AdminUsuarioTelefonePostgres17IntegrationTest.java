package br.com.topsdojob.v3.application.admin.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresInitializer;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresSupport;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TopsDoJobBackendApplication.class, initializers = PostgresInitializer.class)
@Import(AdminUsuarioAtualizacaoService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "ADMIN_USUARIO_UPDATE_POSTGRES17_ENABLED", matches = "true")
class AdminUsuarioTelefonePostgres17IntegrationTest {

  private static final AtomicInteger SEQUENCIA_TELEFONE = new AtomicInteger(900000000);
  private static final OffsetDateTime ANTES = OffsetDateTime.parse("2026-01-02T03:04:05Z");

  @Autowired private AdminUsuarioAtualizacaoService service;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private AdminUsuarioConsultaService consultaService;

  private UUID usuarioId;
  private UUID outroUsuarioId;
  private UUID publicadoId;
  private UUID pendenteId;
  private UUID removidoId;
  private UUID outroAnuncioId;
  private String telefoneAntigo;
  private String telefoneNovo;
  private String telefoneOutroUsuario;

  @AfterAll
  static void pararPostgresDescartavel() throws Exception {
    PostgresSupport.stop();
  }

  @BeforeEach
  void criarDadosSinteticos() {
    int sequencia = SEQUENCIA_TELEFONE.getAndAdd(3);
    telefoneAntigo = "+5562" + sequencia;
    telefoneNovo = "+5562" + (sequencia + 1);
    telefoneOutroUsuario = "+5562" + (sequencia + 2);
    usuarioId = UUID.randomUUID();
    outroUsuarioId = UUID.randomUUID();
    publicadoId = UUID.randomUUID();
    pendenteId = UUID.randomUUID();
    removidoId = UUID.randomUUID();
    outroAnuncioId = UUID.randomUUID();
    inserirUsuario(usuarioId, telefoneAntigo);
    inserirUsuario(outroUsuarioId, telefoneOutroUsuario);
    inserirAnuncio(publicadoId, usuarioId, telefoneAntigo, "PUBLICADO", "APROVADO", null);
    inserirAnuncio(pendenteId, usuarioId, telefoneAntigo, "PENDENTE_REVISAO", "PENDENTE", null);
    inserirAnuncio(removidoId, usuarioId, telefoneAntigo, "REMOVIDO", "APROVADO", ANTES.plusDays(1));
    inserirAnuncio(outroAnuncioId, outroUsuarioId, telefoneOutroUsuario, "PUBLICADO", "APROVADO", null);
  }

  @Test
  void telefoneAdminSincronizaSomenteAnunciosDoUsuarioNaoRemovidos() {
    Map<String, Object> publicadoAntes = anuncio(publicadoId);
    Map<String, Object> pendenteAntes = anuncio(pendenteId);
    Map<String, Object> removidoAntes = anuncio(removidoId);
    Map<String, Object> outroAntes = anuncio(outroAnuncioId);
    AdminUserPrincipal ator = ator();

    service.atualizar(usuarioId, trocaTelefone(), ator, "req-telefone-admin-ok");

    assertThat(telefoneUsuario(usuarioId)).isEqualTo(telefoneNovo);
    assertThat(anuncio(publicadoId)).containsEntry("whatsapp_normalizado", telefoneNovo);
    assertThat(anuncio(pendenteId)).containsEntry("whatsapp_normalizado", telefoneNovo);
    assertCamposPreservados(publicadoAntes, anuncio(publicadoId));
    assertCamposPreservados(pendenteAntes, anuncio(pendenteId));
    assertThat(anuncio(removidoId)).isEqualTo(removidoAntes);
    assertThat(anuncio(outroAnuncioId)).isEqualTo(outroAntes);
    assertThat(telefoneUsuario(outroUsuarioId)).isEqualTo(telefoneOutroUsuario);
    assertThat(auditorias("req-telefone-admin-ok")).isEqualTo(1);
  }

  @Test
  void edicaoDeOutroCampoNaoModificaContatoNemAnuncios() {
    List<Map<String, Object>> anunciosAntes = anuncios();
    AdminUsuarioAtualizacaoRequestDto request = new AdminUsuarioAtualizacaoRequestDto();
    request.setVersao(0);
    request.setNome("Nome atualizado");

    service.atualizar(usuarioId, request, ator(), "req-outro-campo");

    assertThat(telefoneUsuario(usuarioId)).isEqualTo(telefoneAntigo);
    assertThat(anuncios()).isEqualTo(anunciosAntes);
    assertThat(auditorias("req-outro-campo")).isEqualTo(1);
  }

  @Test
  void falhaAposSincronizacaoReverteUsuarioAnunciosEAuditoria() {
    AdminUserPrincipal ator = ator();
    List<Map<String, Object>> anunciosAntes = anuncios();
    when(consultaService.detalhar(usuarioId, ator))
        .thenThrow(new IllegalStateException("falha sintetica apos gravacoes"));

    assertThatThrownBy(() -> service.atualizar(
        usuarioId, trocaTelefone(), ator, "req-telefone-admin-rollback"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("falha sintetica apos gravacoes");

    assertThat(telefoneUsuario(usuarioId)).isEqualTo(telefoneAntigo);
    assertThat(anuncios()).isEqualTo(anunciosAntes);
    assertThat(auditorias("req-telefone-admin-rollback")).isZero();
  }

  private AdminUsuarioAtualizacaoRequestDto trocaTelefone() {
    AdminUsuarioAtualizacaoRequestDto request = new AdminUsuarioAtualizacaoRequestDto();
    request.setVersao(0);
    request.setTelefone(telefoneNovo);
    return request;
  }

  private AdminUserPrincipal ator() {
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(ator.usuarioId()).thenReturn(outroUsuarioId);
    return ator;
  }

  private void inserirUsuario(UUID id, String telefone) {
    jdbc.update("""
        insert into usuario (
          id, nome, email_normalizado, telefone_normalizado, status, tipo_conta,
          criado_em, atualizado_em, versao
        ) values (?, 'Usuario sintetico', ?, ?, 'ATIVO', 'ANUNCIANTE', ?, ?, 0)
        """, id, "qa-" + id + "@example.invalid", telefone, ANTES, ANTES);
  }

  private void inserirAnuncio(
      UUID id, UUID proprietarioId, String whatsapp, String status,
      String moderacao, OffsetDateTime removidoEm) {
    jdbc.update("""
        insert into anuncio (
          id, usuario_id, slug, titulo, descricao, status, status_moderacao,
          categoria, whatsapp_normalizado, publicado_em, criado_em, atualizado_em,
          removido_em, versao
        ) values (?, ?, ?, 'Titulo sintetico', 'Descricao sintetica', ?, ?,
          'ACOMPANHANTE_FEMININA', ?, ?, ?, ?, ?, 0)
        """, id, proprietarioId, "qa-" + id, status, moderacao, whatsapp,
        "PUBLICADO".equals(status) ? ANTES : null, ANTES, ANTES, removidoEm);
  }

  private String telefoneUsuario(UUID id) {
    return jdbc.queryForObject(
        "select telefone_normalizado from usuario where id = ?", String.class, id);
  }

  private Map<String, Object> anuncio(UUID id) {
    return jdbc.queryForMap("""
        select titulo, descricao, status, status_moderacao, categoria,
          whatsapp_normalizado, publicado_em, removido_em, atualizado_em, versao
        from anuncio where id = ?
        """, id);
  }

  private List<Map<String, Object>> anuncios() {
    return jdbc.queryForList("""
        select id, titulo, descricao, status, status_moderacao, categoria,
          whatsapp_normalizado, publicado_em, removido_em, atualizado_em, versao
        from anuncio where usuario_id in (?, ?) order by id
        """, usuarioId, outroUsuarioId);
  }

  private int auditorias(String requestId) {
    return jdbc.queryForObject(
        "select count(*) from auditoria_evento where request_id = ?", Integer.class, requestId);
  }

  private void assertCamposPreservados(
      Map<String, Object> antes, Map<String, Object> depois) {
    for (String campo : List.of(
        "titulo", "descricao", "status", "status_moderacao", "categoria",
        "publicado_em", "removido_em", "versao")) {
      assertThat(depois.get(campo)).as(campo).isEqualTo(antes.get(campo));
    }
  }
}
