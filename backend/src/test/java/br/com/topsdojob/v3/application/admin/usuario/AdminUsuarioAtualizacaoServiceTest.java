package br.com.topsdojob.v3.application.admin.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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

class AdminUsuarioAtualizacaoServiceTest {

  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final AdminUsuarioConsultaService consultaService = mock(AdminUsuarioConsultaService.class);
  private final AdminUsuarioAtualizacaoService service = new AdminUsuarioAtualizacaoService(
      usuarioRepository,
      auditoriaRepository,
      consultaService);

  @Test
  void atualizaTodosOsCamposEmUmaOperacaoSemExporValoresNaAuditoria() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    AdminUsuarioDetalheDto detail = mock(AdminUsuarioDetalheDto.class);
    when(ator.usuarioId()).thenReturn(UUID.randomUUID());
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByEmailNormalizado("novo@example.invalid")).thenReturn(Optional.empty());
    when(usuarioRepository.findByCpfNormalizado("52998224725")).thenReturn(Optional.empty());
    when(usuarioRepository.findByTelefoneNormalizado("+5562999998888")).thenReturn(Optional.empty());
    when(consultaService.detalhar(usuarioId, ator)).thenReturn(detail);

    AdminUsuarioAtualizacaoRequestDto request = request(usuario);
    request.setNome("Nome atualizado");
    request.setNomeCivil("Nome Civil Atualizado");
    request.setEmail("NOVO@example.invalid");
    request.setCpf("529.982.247-25");
    request.setTelefone("(62) 99999-8888");
    request.setDataNascimento("1991-02-03");

    var result = service.atualizar(usuarioId, request, ator, "req-user-update");

    assertThat(result).isSameAs(detail);
    assertThat(usuario.getNome()).isEqualTo("Nome atualizado");
    assertThat(usuario.getNomeCivil()).isEqualTo("Nome Civil Atualizado");
    assertThat(usuario.getEmailNormalizado()).isEqualTo("novo@example.invalid");
    assertThat(usuario.getCpfNormalizado()).isEqualTo("52998224725");
    assertThat(usuario.getTelefoneNormalizado()).isEqualTo("+5562999998888");
    assertThat(usuario.getDataNascimento()).isEqualTo(LocalDate.of(1991, 2, 3));
    verify(usuarioRepository).saveAndFlush(usuario);
    ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(auditoriaRepository).save(audit.capture());
    assertThat(audit.getValue().getAcao()).isEqualTo("USUARIO_DADOS_CADASTRAIS_ATUALIZAR");
    assertThat(audit.getValue().getDepoisJson())
        .contains("nome", "nomeCivil", "email", "cpf", "telefone", "dataNascimento")
        .doesNotContain("Nome atualizado", "52998224725", "999998888");
  }

  @Test
  void campoOmitidoPermaneceInalterado() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByTelefoneNormalizado("+5562999998888")).thenReturn(Optional.empty());
    when(consultaService.detalhar(usuarioId, ator)).thenReturn(mock(AdminUsuarioDetalheDto.class));
    AdminUsuarioAtualizacaoRequestDto request = request(usuario);
    request.setTelefone("(62) 99999-8888");

    service.atualizar(usuarioId, request, ator, "req-partial");

    assertThat(usuario.getNome()).isEqualTo("QA");
    assertThat(usuario.getEmailNormalizado()).isEqualTo("qa-" + usuarioId + "@example.invalid");
    assertThat(usuario.getTelefoneNormalizado()).isEqualTo("+5562999998888");
  }

  @Test
  void corrigeNomeCivilECpfSemAlterarDemaisDadosDaConta() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(ator.usuarioId()).thenReturn(UUID.randomUUID());
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByCpfNormalizado("52998224725")).thenReturn(Optional.empty());
    when(consultaService.detalhar(usuarioId, ator)).thenReturn(mock(AdminUsuarioDetalheDto.class));
    AdminUsuarioAtualizacaoRequestDto request = request(usuario);
    request.setNomeCivil("Nome Civil Corrigido");
    request.setCpf("529.982.247-25");

    service.atualizar(usuarioId, request, ator, "req-owner-data");

    assertThat(usuario.getNomeCivil()).isEqualTo("Nome Civil Corrigido");
    assertThat(usuario.getCpfNormalizado()).isEqualTo("52998224725");
    assertThat(usuario.getNome()).isEqualTo("QA");
    assertThat(usuario.getEmailNormalizado()).isEqualTo("qa-" + usuarioId + "@example.invalid");
    assertThat(usuario.getTelefoneNormalizado()).isEqualTo("+556233334444");
    assertThat(usuario.getDataNascimento()).isEqualTo(LocalDate.of(1990, 1, 1));
    ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(auditoriaRepository).save(audit.capture());
    assertThat(audit.getValue().getDepoisJson())
        .contains("nomeCivil", "cpf")
        .doesNotContain("Nome Civil Corrigido", "52998224725");
  }

  @Test
  void permiteEditarOutroCampoSemRevalidarCpfLegadoInalterado() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    usuario.aplicarDadosKyc(
        "QA Civil",
        "11111111111",
        LocalDate.of(1990, 1, 1),
        OffsetDateTime.now(ZoneOffset.UTC));
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByEmailNormalizado(usuario.getEmailNormalizado()))
        .thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByCpfNormalizado("11111111111"))
        .thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByTelefoneNormalizado("+5562999998888"))
        .thenReturn(Optional.empty());
    when(consultaService.detalhar(usuarioId, ator)).thenReturn(mock(AdminUsuarioDetalheDto.class));
    AdminUsuarioAtualizacaoRequestDto request = request(usuario);
    request.setTelefone("(62) 99999-8888");

    service.atualizar(usuarioId, request, ator, "req-legacy-cpf");

    assertThat(usuario.getCpfNormalizado()).isEqualTo("11111111111");
    assertThat(usuario.getTelefoneNormalizado()).isEqualTo("+5562999998888");
    verify(usuarioRepository).saveAndFlush(usuario);
  }

  @Test
  void recusaCpfEmailTelefoneDataEVersaoInvalidosSemPersistir() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));

    assertInvalid(usuarioId, ator, usuario, request -> request.setEmail("email-invalido"));
    AdminUsuarioAtualizacaoRequestDto cpfInvalido = request(usuario);
    cpfInvalido.setCpf("111.111.111-11");
    assertThatThrownBy(() -> service.atualizar(
        usuarioId, cpfInvalido, ator, "req-invalid-cpf"))
        .isInstanceOfSatisfying(AdminUsuarioAtualizacaoException.class, exception ->
            assertThat(exception.erros()).singleElement().satisfies(error ->
                assertThat(error.codigo()).isEqualTo("CPF_INVALIDO")));
    assertInvalid(usuarioId, ator, usuario, request -> request.setTelefone("(62) 9999"));
    assertInvalid(usuarioId, ator, usuario, request -> request.setDataNascimento("2020-01-01"));
    AdminUsuarioAtualizacaoRequestDto stale = request(usuario);
    stale.setVersao(usuario.getVersao() + 1);
    stale.setNome("Outro nome");
    assertThatThrownBy(() -> service.atualizar(usuarioId, stale, ator, "req-stale"))
        .isInstanceOfSatisfying(AdminUsuarioAtualizacaoException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));

    verify(usuarioRepository, never()).saveAndFlush(any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void conflitosDeEmailECpfRetornam409SemRevelarOutraConta() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    UsuarioEntity outraConta = usuario(UUID.randomUUID(), "+5562888888888");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByEmailNormalizado("duplicado@example.invalid"))
        .thenReturn(Optional.of(outraConta));
    when(usuarioRepository.findByCpfNormalizado("52998224725"))
        .thenReturn(Optional.of(outraConta));

    AdminUsuarioAtualizacaoRequestDto email = request(usuario);
    email.setEmail("duplicado@example.invalid");
    assertThatThrownBy(() -> service.atualizar(usuarioId, email, ator, "req-email-conflict"))
        .isInstanceOfSatisfying(AdminUsuarioAtualizacaoException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.erros()).singleElement().satisfies(error -> {
            assertThat(error.campo()).isEqualTo("email");
            assertThat(error.mensagem()).doesNotContain(outraConta.getId().toString());
          });
        });

    AdminUsuarioAtualizacaoRequestDto cpf = request(usuario);
    cpf.setCpf("529.982.247-25");
    assertThatThrownBy(() -> service.atualizar(usuarioId, cpf, ator, "req-cpf-conflict"))
        .isInstanceOfSatisfying(AdminUsuarioAtualizacaoException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.erros()).singleElement().satisfies(error ->
              assertThat(error.campo()).isEqualTo("cpf"));
          assertThat(exception.getMessage()).isEqualTo("CPF já vinculado a outro usuário.");
        });

    verify(usuarioRepository, never()).saveAndFlush(any());
  }

  @Test
  void corridaDeUnicidadeDoCpfMantemConflitoEspecifico() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioEntity usuario = usuario(usuarioId, "+556233334444");
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByCpfNormalizado("52998224725")).thenReturn(Optional.empty());
    doThrow(new org.springframework.dao.DataIntegrityViolationException("conflito sintetico"))
        .when(usuarioRepository).saveAndFlush(usuario);
    AdminUsuarioAtualizacaoRequestDto request = request(usuario);
    request.setCpf("529.982.247-25");

    assertThatThrownBy(() -> service.atualizar(usuarioId, request, ator, "req-cpf-race"))
        .isInstanceOfSatisfying(AdminUsuarioAtualizacaoException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.getMessage()).isEqualTo("CPF já vinculado a outro usuário.");
          assertThat(exception.erros()).singleElement().satisfies(error ->
              assertThat(error.campo()).isEqualTo("cpf"));
        });
    verify(auditoriaRepository, never()).save(any());
  }

  private void assertInvalid(
      UUID usuarioId,
      AdminUserPrincipal ator,
      UsuarioEntity usuario,
      java.util.function.Consumer<AdminUsuarioAtualizacaoRequestDto> change) {
    AdminUsuarioAtualizacaoRequestDto request = request(usuario);
    change.accept(request);
    assertThatThrownBy(() -> service.atualizar(usuarioId, request, ator, "req-invalid"))
        .isInstanceOf(AdminUsuarioAtualizacaoException.class);
  }

  private AdminUsuarioAtualizacaoRequestDto request(UsuarioEntity usuario) {
    AdminUsuarioAtualizacaoRequestDto request = new AdminUsuarioAtualizacaoRequestDto();
    request.setVersao(usuario.getVersao());
    return request;
  }

  private UsuarioEntity usuario(UUID id, String telefone) {
    UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
        id,
        "QA",
        "qa-" + id + "@example.invalid",
        telefone,
        LocalDate.of(1990, 1, 1),
        OffsetDateTime.now(ZoneOffset.UTC));
    usuario.aplicarDadosKyc(
        "QA Civil",
        "12345678909",
        LocalDate.of(1990, 1, 1),
        OffsetDateTime.now(ZoneOffset.UTC));
    return usuario;
  }
}
