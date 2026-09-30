package br.com.topsdojob.v3.application.admin.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeAccessAuditService;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.RelatorioRequest;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeService;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoStoryAccessAuditService;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoStoryService;
import br.com.topsdojob.v3.application.admin.arquivo.FinalidadeAcessoArquivoPublicidade;
import br.com.topsdojob.v3.application.admin.auth.AdminRbacService;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.ExportadorRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresInitializer;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresSupport;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(properties = {
    "app.admin.arquivo-exportador.approved-target-id=10000000-0000-4000-8000-000000000101"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TopsDoJobBackendApplication.class, initializers = PostgresInitializer.class)
@Import({AdminArquivoExportadorService.class, AdminRbacService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "ARQUIVO_EXPORTADOR_POSTGRES17_ENABLED", matches = "true")
class AdminArquivoExportadorPostgres17IntegrationTest {
  private static final UUID ALVO = UUID.fromString("10000000-0000-4000-8000-000000000101");
  private static final UUID OUTRO_ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000102");
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-30T12:00:00Z");

  @Autowired private AdminArquivoExportadorService service;
  @Autowired private AdminRbacService rbac;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private AdminStaffService staff;
  @MockBean private PublicSessionRegistry sessions;

  @AfterAll
  static void pararPostgresDescartavel() throws Exception {
    PostgresSupport.stop();
  }

  @Test
  void grantRevokeVersaoAuditoriaRbacERollbackUsamTransacoesReais() {
    inserirAdmin(ALVO);
    inserirAdmin(OUTRO_ADMIN);
    when(staff.detalhar(ALVO)).thenReturn(detalhe());

    assertThat(vinculos()).isZero();
    assertThat(versao()).isZero();
    jdbc.update("DELETE FROM papel_usuario WHERE usuario_id = ? AND papel = 'ADMIN'", OUTRO_ADMIN);
    jdbc.update("""
        INSERT INTO papel_usuario (usuario_id, papel, criado_em)
        VALUES (?, 'MODERADOR', ?)
        """, OUTRO_ADMIN, AGORA);
    assertThatThrownBy(() -> service.alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-stale-role"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThat(vinculos()).isZero();
    assertThat(versao()).isZero();
    assertThat(auditoria("req-export-stale-role", "STAFF_ARQUIVO_EXPORTADOR_CONCEDER")).isZero();
    verify(sessions, never()).invalidateAll(ALVO);

    jdbc.update("DELETE FROM papel_usuario WHERE usuario_id = ? AND papel = 'MODERADOR'", OUTRO_ADMIN);
    jdbc.update("""
        INSERT INTO papel_usuario (usuario_id, papel, criado_em)
        VALUES (?, 'ADMIN', ?)
        """, OUTRO_ADMIN, AGORA);
    jdbc.update("""
        UPDATE usuario SET status = 'DESATIVADO', desativado_em = ?
        WHERE id = ?
        """, AGORA, OUTRO_ADMIN);
    assertThatThrownBy(() -> service.alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-stale-inactive"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThat(vinculos()).isZero();
    assertThat(versao()).isZero();
    assertThat(auditoria("req-export-stale-inactive", "STAFF_ARQUIVO_EXPORTADOR_CONCEDER")).isZero();
    verify(sessions, never()).invalidateAll(ALVO);

    jdbc.update("""
        UPDATE usuario SET status = 'ATIVO', desativado_em = NULL
        WHERE id = ?
        """, OUTRO_ADMIN);
    service.alterar(ALVO, new ExportadorRequest(true, 0), ator(), "req-export-pg-grant");
    assertThat(vinculos()).isEqualTo(1);
    assertThat(versao()).isEqualTo(1);
    assertThat(auditoria("req-export-pg-grant", "STAFF_ARQUIVO_EXPORTADOR_CONCEDER")).isEqualTo(1);
    assertThat(rbac.permissoes(rbac.papeis(ALVO)))
        .extracting(AdminPermissionDto::codigo)
        .contains("ARQUIVO_PUBLICIDADE_LER", "ARQUIVO_PUBLICIDADE_EXPORTAR");
    assertThat(rbac.permissoes(rbac.papeis(OUTRO_ADMIN)))
        .extracting(AdminPermissionDto::codigo)
        .doesNotContain("ARQUIVO_PUBLICIDADE_EXPORTAR");
    verify(sessions).invalidateAll(ALVO);
    AdminUserPrincipal sessaoAntiga = sessaoExportadorAtual();

    assertThatThrownBy(() -> service.alterar(
        ALVO, new ExportadorRequest(false, 0), ator(), "req-export-pg-stale"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(vinculos()).isEqualTo(1);
    assertThat(auditoria("req-export-pg-stale", "STAFF_ARQUIVO_EXPORTADOR_REVOGAR")).isZero();

    service.alterar(ALVO, new ExportadorRequest(false, 1), ator(), "req-export-pg-revoke");
    assertThat(vinculos()).isZero();
    assertThat(versao()).isEqualTo(2);
    assertThat(auditoria("req-export-pg-revoke", "STAFF_ARQUIVO_EXPORTADOR_REVOGAR")).isEqualTo(1);
    assertThat(rbac.permissoes(rbac.papeis(ALVO)))
        .extracting(AdminPermissionDto::codigo)
        .doesNotContain("ARQUIVO_PUBLICIDADE_EXPORTAR");
    verify(sessions, times(2)).invalidateAll(ALVO);
    assertArquivoNegadoParaSessaoAntiga(sessaoAntiga);

    service.alterar(ALVO, new ExportadorRequest(true, 2), ator(), "req-export-pg-grant-g");
    assertThat(vinculos()).isEqualTo(1);
    assertThat(versao()).isEqualTo(3);
    verify(sessions, times(3)).invalidateAll(ALVO);
    assertThatThrownBy(() -> service.alterar(
        ALVO, new ExportadorRequest(false, 1), ator(), "req-export-pg-revoke"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    assertThat(vinculos()).isEqualTo(1);
    assertThat(versao()).isEqualTo(3);
    assertThat(auditoria("req-export-pg-revoke", "STAFF_ARQUIVO_EXPORTADOR_REVOGAR")).isEqualTo(1);
    verify(sessions, times(3)).invalidateAll(ALVO);

    when(staff.detalhar(ALVO)).thenThrow(new IllegalStateException("rollback sintetico"));
    assertThatThrownBy(() -> service.alterar(
        ALVO, new ExportadorRequest(false, 3), ator(), "req-export-pg-rollback"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("rollback sintetico");
    assertThat(vinculos()).isEqualTo(1);
    assertThat(versao()).isEqualTo(3);
    assertThat(auditoria("req-export-pg-rollback", "STAFF_ARQUIVO_EXPORTADOR_REVOGAR")).isZero();
    verify(sessions, times(3)).invalidateAll(ALVO);
  }

  private void inserirAdmin(UUID id) {
    jdbc.update("""
        INSERT INTO usuario (
          id, nome, email_normalizado, status, tipo_conta,
          email_verificado_em, criado_em, atualizado_em, versao
        ) VALUES (?, 'Admin QA', ?, 'ATIVO', 'STAFF', ?, ?, ?, 0)
        """, id, "admin-" + id + "@example.invalid", AGORA, AGORA, AGORA);
    jdbc.update("""
        INSERT INTO papel_usuario (usuario_id, papel, criado_em)
        VALUES (?, 'ADMIN', ?)
        """, id, AGORA);
  }

  private int vinculos() {
    return jdbc.queryForObject("""
        SELECT count(*) FROM papel_usuario
        WHERE usuario_id = ? AND papel = 'ARQUIVO_EXPORTADOR'
        """, Integer.class, ALVO);
  }

  private int versao() {
    return jdbc.queryForObject(
        "SELECT versao FROM usuario WHERE id = ?", Integer.class, ALVO);
  }

  private int auditoria(String requestId, String acao) {
    return jdbc.queryForObject("""
        SELECT count(*) FROM auditoria_evento
        WHERE recurso_id = ? AND request_id = ? AND acao = ?
        """, Integer.class, ALVO, requestId, acao);
  }

  private AdminUserPrincipal ator() {
    return new AdminUserPrincipal(
        OUTRO_ADMIN, "Operador QA", "operador.qa@example.invalid", "hash",
        List.of(PapelUsuario.ADMIN),
        List.of(new AdminPermissionDto("ADMIN_CONFIGURAR", "Configurar administracao")),
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ADMIN_CONFIGURAR")), true);
  }

  private AdminUserPrincipal sessaoExportadorAtual() {
    List<PapelUsuario> roles = rbac.papeis(ALVO);
    List<AdminPermissionDto> permissoes = rbac.permissoes(roles);
    List<GrantedAuthority> authorities = new ArrayList<>();
    roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
    permissoes.forEach(permissao ->
        authorities.add(new SimpleGrantedAuthority(permissao.codigo())));
    return new AdminUserPrincipal(
        ALVO, "Admin QA", "admin.qa@example.invalid", "hash",
        roles, permissoes, authorities, true);
  }

  @SuppressWarnings("unchecked")
  private void assertArquivoNegadoParaSessaoAntiga(AdminUserPrincipal sessaoAntiga) {
    assertThat(sessaoAntiga.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("ROLE_ADMIN", "ARQUIVO_PUBLICIDADE_LER", "ARQUIVO_PUBLICIDADE_EXPORTAR");
    ObjectProvider<ObjectStorage> provider = org.mockito.Mockito.mock(ObjectProvider.class);
    var publicidadeAudit = org.mockito.Mockito.mock(AdminArquivoPublicidadeAccessAuditService.class);
    var storyAudit = org.mockito.Mockito.mock(AdminArquivoStoryAccessAuditService.class);
    var properties = new R2StorageProperties();
    var publicidade = new AdminArquivoPublicidadeService(
        jdbc, new ObjectMapper(), provider, properties, publicidadeAudit);
    var stories = new AdminArquivoStoryService(
        jdbc, new ObjectMapper(), provider, properties, storyAudit);
    UUID registro = UUID.randomUUID();
    UUID midia = UUID.randomUUID();
    UUID atorIdCacheado = sessaoAntiga.usuarioId();
    var finalidade = FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA;
    var relatorio = new RelatorioRequest(null, null, null);
    List<ThrowingCallable> tentativas = List.of(
        () -> publicidade.relatorio(relatorio, atorIdCacheado, "req-stale-publicidade-relatorio", finalidade),
        () -> publicidade.detalhar(registro, atorIdCacheado, "req-stale-publicidade-detalhe", finalidade, true),
        () -> publicidade.midia(registro, midia, atorIdCacheado, "req-stale-publicidade-midia", finalidade),
        () -> stories.relatorio(relatorio, atorIdCacheado, "req-stale-stories-relatorio", finalidade),
        () -> stories.detalhar(registro, atorIdCacheado, "req-stale-stories-detalhe", finalidade, true),
        () -> stories.midia(registro, midia, atorIdCacheado, "req-stale-stories-midia", finalidade));
    for (ThrowingCallable tentativa : tentativas) {
      assertThatThrownBy(tentativa)
          .isInstanceOfSatisfying(ResponseStatusException.class,
              erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
    verifyNoInteractions(provider, publicidadeAudit, storyAudit);
  }

  private Detalhe detalhe() {
    return new Detalhe(new Resumo(
        ALVO, "Admin QA", "admin.qa@example.invalid", "ADMIN", "Administrador",
        "ATIVO", "Ativo", true, false, AGORA, AGORA, 0), List.of(), List.of());
  }
}
