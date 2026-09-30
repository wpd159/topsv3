package br.com.topsdojob.v3.application.admin.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.ExportadorRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class AdminArquivoExportadorServiceTest {
  private static final UUID ALVO = UUID.fromString("10000000-0000-4000-8000-000000000101");
  private static final UUID ATOR = UUID.fromString("10000000-0000-4000-8000-000000000102");
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-30T12:00:00Z");

  private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
  private final PapelUsuarioRepository papeis = mock(PapelUsuarioRepository.class);
  private final AuditoriaEventoRepository auditorias = mock(AuditoriaEventoRepository.class);
  private final PublicSessionRegistry sessions = mock(PublicSessionRegistry.class);
  private final AdminStaffService staff = mock(AdminStaffService.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  @BeforeEach
  void transacao() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    when(staff.detalhar(ALVO)).thenReturn(detalhe());
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(ATOR))).thenReturn(true);
  }

  @AfterEach
  void limparTransacao() {
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void concedeSomenteAoAdminStaffAtivoAprovadoComVersaoEAuditoria() {
    prepararAlvo(true, PapelUsuario.ADMIN, false);

    Detalhe resultado = service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-grant");

    assertThat(resultado).isEqualTo(detalhe());
    ArgumentCaptor<PapelUsuarioEntity> vinculo = ArgumentCaptor.forClass(PapelUsuarioEntity.class);
    verify(papeis).save(vinculo.capture());
    assertThat(vinculo.getValue().getUsuarioId()).isEqualTo(ALVO);
    assertThat(vinculo.getValue().getPapel()).isEqualTo(PapelUsuario.ARQUIVO_EXPORTADOR);
    assertThat(vinculo.getValue().getCriadoPor()).isEqualTo(ATOR);
    assertAuditoria("STAFF_ARQUIVO_EXPORTADOR_CONCEDER", "req-export-grant");
    assertInvalidacaoSoAposCommit();
  }

  @Test
  void revogaMesmoComConfiguracaoDeAprovacaoVazia() {
    prepararAlvo(true, PapelUsuario.ADMIN, true);

    service("").alterar(ALVO, new ExportadorRequest(false, 0), ator(), "req-export-revoke");

    assertAuditoria("STAFF_ARQUIVO_EXPORTADOR_REVOGAR", "req-export-revoke");
    assertInvalidacaoSoAposCommit();
  }

  @Test
  void falhaDeTransacaoNaoInvalidaSessao() {
    prepararAlvo(true, PapelUsuario.ADMIN, false);

    service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-rollback");
    assertThat(TransactionSynchronizationManager.getSynchronizations()).isNotEmpty();
    for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
      callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }
    verify(sessions, never()).invalidateAll(ALVO);
  }

  @Test
  void concessaoFalhaFechadaSemAlvoAprovadoOuComOutroId() {
    prepararAlvo(true, PapelUsuario.ADMIN, false);
    for (String configuracao : List.of("", UUID.randomUUID().toString(), "id-invalido")) {
      assertThatThrownBy(() -> service(configuracao).alterar(
          ALVO, new ExportadorRequest(true, 0), ator(), "req-export-unapproved"))
          .isInstanceOf(ResponseStatusException.class);
    }
    assertSemMutacao();
  }

  @Test
  void concessaoRejeitaPapelErradoContaInativaEContaNaoStaff() {
    UUID naoStaffId = UUID.fromString("10000000-0000-4000-8000-000000000103");
    UsuarioEntity naoStaff = UsuarioEntity.criarSolicitacaoLocal(
        naoStaffId, "Pessoa QA", "pessoa.qa@example.invalid", null, AGORA);
    naoStaff.confirmarEmail(AGORA);
    when(usuarios.findByIdForUpdate(naoStaffId)).thenReturn(Optional.of(naoStaff));
    when(papeis.findByUsuarioId(naoStaffId)).thenReturn(List.of(
        PapelUsuarioEntity.criarAdminHomologacao(naoStaffId, AGORA)));
    assertThatThrownBy(() -> service(naoStaffId.toString()).alterar(
        naoStaffId, new ExportadorRequest(true, 0), ator(), "req-export-nonstaff"))
        .isInstanceOf(ResponseStatusException.class);

    prepararAlvo(true, PapelUsuario.MODERADOR, false);
    assertThatThrownBy(() -> service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-wrong-role"))
        .isInstanceOf(ResponseStatusException.class);
    prepararAlvo(false, PapelUsuario.ADMIN, false);
    assertThatThrownBy(() -> service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-inactive"))
        .isInstanceOf(ResponseStatusException.class);
    assertSemMutacao();
  }

  @Test
  void versaoAusenteOuObsoletaNaoAlteraAcesso() {
    prepararAlvo(true, PapelUsuario.ADMIN, false);
    assertThatThrownBy(() -> service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, null), ator(), "req-export-no-version"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 42), ator(), "req-export-stale"))
        .isInstanceOf(ResponseStatusException.class);
    assertSemMutacao();
  }

  @Test
  void retryEEstadoJaDesejadoNaoDuplicamAuditoriaOuInvalidacao() {
    prepararAlvo(true, PapelUsuario.ADMIN, true);
    when(auditorias.existsByAcaoAndRecursoIdAndRequestId(
        "STAFF_ARQUIVO_EXPORTADOR_CONCEDER", ALVO, "req-export-retry")).thenReturn(true);
    service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-retry");
    service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-export-already");
    verify(auditorias, never()).save(any());
    verify(papeis, never()).save(any());
    verify(sessions, never()).invalidateAll(ALVO);
    assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
  }

  @Test
  void replayDeRevogacaoAposNovaConcessaoRetornaConflitoSemAlterarPapel() {
    prepararAlvo(true, PapelUsuario.ADMIN, true);
    when(auditorias.existsByAcaoAndRecursoIdAndRequestId(
        "STAFF_ARQUIVO_EXPORTADOR_REVOGAR", ALVO, "req-revoke-r")).thenReturn(true);

    assertThatThrownBy(() -> service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(false, 0), ator(), "req-revoke-r"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    assertSemMutacao();
  }

  @Test
  void retryDeRevogacaoComPapelAindaAusentePermaneceIdempotente() {
    prepararAlvo(true, PapelUsuario.ADMIN, false);
    when(auditorias.existsByAcaoAndRecursoIdAndRequestId(
        "STAFF_ARQUIVO_EXPORTADOR_REVOGAR", ALVO, "req-revoke-r")).thenReturn(true);

    assertThat(service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(false, 0), ator(), "req-revoke-r"))
        .isEqualTo(detalhe());
    assertSemMutacao();
  }

  @Test
  void principalComPermissaoCacheadaMasAtorSemAcessoAtualFalhaAntesDoLockDoAlvo() {
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(ATOR))).thenReturn(false);

    assertThatThrownBy(() -> service(ALVO.toString()).alterar(
        ALVO, new ExportadorRequest(true, 0), ator(), "req-stale-actor"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    verify(usuarios, never()).findByIdForUpdate(ALVO);
    assertSemMutacao();
  }

  private void prepararAlvo(boolean ativo, PapelUsuario papel, boolean exportador) {
    UsuarioEntity alvo = UsuarioEntity.criarStaff(
        ALVO, "Administrador QA", "admin.qa@example.invalid", ativo, AGORA);
    when(usuarios.findByIdForUpdate(ALVO)).thenReturn(Optional.of(alvo));
    List<PapelUsuarioEntity> vinculos = new java.util.ArrayList<>();
    vinculos.add(PapelUsuarioEntity.criarStaff(ALVO, papel, ATOR, AGORA));
    if (exportador) {
      PapelUsuarioEntity adicional = mock(PapelUsuarioEntity.class);
      when(adicional.getPapel()).thenReturn(PapelUsuario.ARQUIVO_EXPORTADOR);
      vinculos.add(adicional);
    }
    when(papeis.findByUsuarioId(ALVO)).thenReturn(vinculos);
    when(usuarios.saveAndFlush(alvo)).thenReturn(alvo);
  }

  private AdminArquivoExportadorService service(String aprovado) {
    return new AdminArquivoExportadorService(
        usuarios, papeis, auditorias, sessions, staff, jdbc, aprovado);
  }

  private AdminUserPrincipal ator() {
    return new AdminUserPrincipal(
        ATOR, "Operador QA", "operador.qa@example.invalid", "hash",
        List.of(PapelUsuario.ADMIN),
        List.of(new AdminPermissionDto("ADMIN_CONFIGURAR", "Configurar administracao")),
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ADMIN_CONFIGURAR")), true);
  }

  private Detalhe detalhe() {
    return new Detalhe(new Resumo(
        ALVO, "Administrador QA", "admin.qa@example.invalid", "ADMIN", "Administrador",
        "ATIVO", "Ativo", true, false, AGORA, AGORA, 0), List.of(), List.of());
  }

  private void assertAuditoria(String acao, String requestId) {
    ArgumentCaptor<AuditoriaEventoEntity> evento = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(auditorias).saveAndFlush(evento.capture());
    assertThat(evento.getValue().getAcao()).isEqualTo(acao);
    assertThat(evento.getValue().getAtorUsuarioId()).isEqualTo(ATOR);
    assertThat(evento.getValue().getRecursoId()).isEqualTo(ALVO);
    assertThat(evento.getValue().getRequestId()).isEqualTo(requestId);
    assertThat(evento.getValue().getAntesJson()).isNotBlank();
    assertThat(evento.getValue().getDepoisJson()).isNotBlank();
  }

  private void assertInvalidacaoSoAposCommit() {
    verify(sessions, never()).invalidateAll(ALVO);
    assertThat(TransactionSynchronizationManager.getSynchronizations()).isNotEmpty();
    for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
      callback.afterCommit();
    }
    verify(sessions).invalidateAll(ALVO);
  }

  private void assertSemMutacao() {
    verify(papeis, never()).save(any());
    verify(auditorias, never()).saveAndFlush(any());
    verify(sessions, never()).invalidateAll(any());
    assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
  }
}
