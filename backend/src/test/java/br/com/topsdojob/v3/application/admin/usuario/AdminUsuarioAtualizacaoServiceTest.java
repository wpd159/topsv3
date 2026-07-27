package br.com.topsdojob.v3.application.admin.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminUsuarioAtualizacaoServiceTest {

  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final AdminUsuarioConsultaService consultaService = mock(AdminUsuarioConsultaService.class);
  private final AdminUsuarioAtualizacaoService service = new AdminUsuarioAtualizacaoService(
      usuarioRepository,
      auditoriaRepository,
      consultaService);

  @Test
  void persisteSomenteTelefoneNormalizadoSemExporValorNaAuditoria() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    AdminUsuarioDetalheDto detail = mock(AdminUsuarioDetalheDto.class);
    when(ator.usuarioId()).thenReturn(UUID.randomUUID());
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByTelefoneNormalizado("+5562999998888")).thenReturn(Optional.empty());
    when(consultaService.detalhar(usuarioId, ator)).thenReturn(detail);

    var result = service.atualizarTelefone(
        usuarioId,
        new AdminUsuarioAtualizacaoRequestDto("(62) 99999-8888"),
        ator,
        "req-phone");

    assertThat(result).isSameAs(detail);
    assertThat(usuario.getTelefoneNormalizado()).isEqualTo("+5562999998888");
    verify(usuarioRepository).save(usuario);
    ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(auditoriaRepository).save(audit.capture());
    assertThat(audit.getValue().getAcao()).isEqualTo("USUARIO_TELEFONE_ATUALIZAR");
    assertThat(audit.getValue().getAntesJson()).doesNotContain("33334444", "999998888");
    assertThat(audit.getValue().getDepoisJson()).doesNotContain("33334444", "999998888");
  }

  @Test
  void recusaCampoVazioOuParcialSemAlterarOUsuario() {
    UUID usuarioId = UUID.randomUUID();
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);

    for (String telefone : new String[] {"", "(62) 9999"}) {
      assertThatThrownBy(() -> service.atualizarTelefone(
          usuarioId,
          new AdminUsuarioAtualizacaoRequestDto(telefone),
          ator,
          "req-invalid"))
          .isInstanceOfSatisfying(ResponseStatusException.class,
              exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    verify(usuarioRepository, never()).save(any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void aceitaFixoENaoDuplicaTelefoneDeOutroUsuario() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+5562999998888");
    UsuarioEntity other = usuario(UUID.randomUUID(), "+556233334444");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByTelefoneNormalizado("+556233334444")).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> service.atualizarTelefone(
        usuarioId,
        new AdminUsuarioAtualizacaoRequestDto("(62) 3333-4444"),
        ator,
        "req-conflict"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    assertThat(usuario.getTelefoneNormalizado()).isEqualTo("+5562999998888");
    verify(usuarioRepository, never()).save(any());
    verify(consultaService, never()).detalhar(eq(usuarioId), any());
  }

  private UsuarioEntity usuario(UUID id, String telefone) {
    return UsuarioEntity.criarCadastroPublico(
        id,
        "QA",
        "qa-" + id + "@example.invalid",
        telefone,
        LocalDate.of(1990, 1, 1),
        OffsetDateTime.now(ZoneOffset.UTC));
  }
}
