package br.com.topsdojob.v3.application.admin.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.AtualizarRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.CriarRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminStaffJdbcRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AdminStaffServiceTest {
  private final AdminStaffJdbcRepository consulta = mock(AdminStaffJdbcRepository.class);
  private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
  private final PapelUsuarioRepository papeis = mock(PapelUsuarioRepository.class);
  private final CredencialUsuarioRepository credenciais = mock(CredencialUsuarioRepository.class);
  private final AuditoriaEventoRepository auditorias = mock(AuditoriaEventoRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final PublicSessionRegistry sessions = mock(PublicSessionRegistry.class);
  private AdminStaffService service;

  @BeforeEach
  void setUp() {
    service = new AdminStaffService(
        consulta,
        usuarios,
        papeis,
        credenciais,
        auditorias,
        encoder,
        sessions);
  }

  @Test
  void criaStaffSemSenhaEmClaroEComRedefinicaoPendente() {
    when(usuarios.findByEmailNormalizado("qa.staff@example.invalid")).thenReturn(Optional.empty());
    when(encoder.encode(any())).thenReturn("$2a$10$hash-sintetico");
    when(usuarios.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(consulta.detalhar(any())).thenAnswer(invocation -> Optional.of(resumo(invocation.getArgument(0), "MODERADOR", true)));
    when(consulta.permissoes(any())).thenReturn(List.of());
    when(consulta.historico(any())).thenReturn(List.of());

    Detalhe detalhe = service.criar(
        new CriarRequest("QA Staff", "QA.STAFF@example.invalid", "MODERADOR", true),
        admin(),
        "req-staff-create");

    assertThat(detalhe.staff().papel()).isEqualTo("MODERADOR");
    ArgumentCaptor<CredencialUsuarioEntity> credencial = ArgumentCaptor.forClass(CredencialUsuarioEntity.class);
    verify(credenciais).save(credencial.capture());
    assertThat(credencial.getValue().getPrecisaRedefinir()).isTrue();
    assertThat(credencial.getValue().getSenhaHash()).isEqualTo("$2a$10$hash-sintetico");
  }

  @Test
  void rejeitaPapelComercialSemPersistir() {
    assertThatThrownBy(() -> service.criar(
        new CriarRequest("QA Staff", "qa.staff@example.invalid", "COMERCIAL", true),
        admin(),
        "req-staff-commercial"))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

    verify(usuarios, never()).saveAndFlush(any());
  }

  @Test
  void protegeUltimoAdministradorAtivo() {
    UUID id = UUID.randomUUID();
    UsuarioEntity staff = UsuarioEntity.criarStaff(id, "Admin unico", "admin@example.invalid", true, agora());
    when(usuarios.findByIdForUpdate(id)).thenReturn(Optional.of(staff));
    when(papeis.findByUsuarioId(id)).thenReturn(List.of(
        PapelUsuarioEntity.criarStaff(id, PapelUsuario.ADMIN, admin().usuarioId(), agora())));
    when(consulta.bloquearAdministradoresAtivos()).thenReturn(List.of(id));

    assertThatThrownBy(() -> service.atualizar(
        id,
        new AtualizarRequest("Admin unico", "MODERADOR", true, 0),
        admin(),
        "req-last-admin"))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    verify(papeis, never()).deleteByUsuarioId(id);
  }

  @Test
  void desativacaoInvalidaSessoesSemExcluirConta() {
    UUID id = UUID.randomUUID();
    UsuarioEntity staff = UsuarioEntity.criarStaff(id, "Moderador QA", "moderador@example.invalid", true, agora());
    when(usuarios.findByIdForUpdate(id)).thenReturn(Optional.of(staff));
    when(papeis.findByUsuarioId(id)).thenReturn(List.of(
        PapelUsuarioEntity.criarStaff(id, PapelUsuario.MODERADOR, admin().usuarioId(), agora())));
    when(usuarios.saveAndFlush(staff)).thenReturn(staff);
    when(consulta.detalhar(id)).thenReturn(Optional.of(resumo(id, "MODERADOR", false)));
    when(consulta.permissoes(id)).thenReturn(List.of());
    when(consulta.historico(id)).thenReturn(List.of());

    service.atualizar(
        id,
        new AtualizarRequest("Moderador QA", "MODERADOR", false, 0),
        admin(),
        "req-deactivate");

    verify(sessions).invalidateAll(id);
    verify(usuarios, never()).deleteById(any());
  }

  private AdminUserPrincipal admin() {
    return new AdminUserPrincipal(
        UUID.fromString("00000000-0000-4000-8000-000000000901"),
        "Admin QA",
        "admin.qa@example.invalid",
        "$2a$10$hash",
        List.of(PapelUsuario.ADMIN),
        List.of(new AdminPermissionDto("ADMIN_CONFIGURAR", "Configurar administracao")),
        List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ADMIN_CONFIGURAR")),
        true);
  }

  private Resumo resumo(UUID id, String papel, boolean ativo) {
    return new Resumo(
        id,
        "Staff QA",
        "staff.qa@example.invalid",
        papel,
        "ADMIN".equals(papel) ? "Administrador" : "Moderador",
        ativo ? "ATIVO" : "DESATIVADO",
        ativo ? "Ativo" : "Inativo",
        ativo,
        true,
        agora(),
        agora(),
        0);
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }
}
