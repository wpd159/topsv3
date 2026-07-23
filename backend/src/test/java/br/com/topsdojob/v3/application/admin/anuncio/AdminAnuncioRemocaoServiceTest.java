package br.com.topsdojob.v3.application.admin.anuncio;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioRemocaoRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioStatusHistoricoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.springframework.web.server.ResponseStatusException;

class AdminAnuncioRemocaoServiceTest {

  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final AnuncioStatusHistoricoRepository statusHistoricoRepository =
      mock(AnuncioStatusHistoricoRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private AdminAnuncioRemocaoService service;

  @BeforeEach
  void preparar() {
    service = new AdminAnuncioRemocaoService(
        anuncioRepository,
        statusHistoricoRepository,
        auditoriaRepository,
        new ObjectMapper());
    when(anuncioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(statusHistoricoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void adminRemoveLogicamenteComLockHistoricoEAuditoria() {
    AnuncioEntity anuncio = anuncio(StatusAnuncio.PUBLICADO);
    when(anuncioRepository.findByIdForModeration(anuncio.getId())).thenReturn(Optional.of(anuncio));

    var resultado = service.remover(
        anuncio.getId(),
        new AdminAnuncioRemocaoRequest("solicitacao administrativa confirmada"),
        admin(),
        "req-remocao-admin");

    assertThat(resultado.statusAnuncio()).isEqualTo("REMOVIDO");
    assertThat(resultado.statusModeracao()).isEqualTo("APROVADO");
    assertThat(resultado.acao()).isEqualTo("REMOVER");
    assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.REMOVIDO);
    assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
    assertThat(anuncio.getRemovidoEm()).isNotNull();
    assertThat(anuncio.getRemovidoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
    verify(anuncioRepository).findByIdForModeration(anuncio.getId());
    verify(anuncioRepository).save(anuncio);
    verify(anuncioRepository, never()).delete(any(AnuncioEntity.class));

    ArgumentCaptor<AnuncioStatusHistoricoEntity> historico =
        ArgumentCaptor.forClass(AnuncioStatusHistoricoEntity.class);
    verify(statusHistoricoRepository).save(historico.capture());
    assertThat(historico.getValue().getStatusAnterior()).isEqualTo(StatusAnuncio.PUBLICADO);
    assertThat(historico.getValue().getStatusNovo()).isEqualTo(StatusAnuncio.REMOVIDO);
    assertThat(historico.getValue().getMotivo())
        .contains("REMOCAO_ADMINISTRATIVA")
        .contains("solicitacao administrativa confirmada");

    ArgumentCaptor<AuditoriaEventoEntity> auditoria =
        ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
    verify(auditoriaRepository).save(auditoria.capture());
    assertThat(auditoria.getValue().getAtorUsuarioId()).isNotNull();
    assertThat(auditoria.getValue().getAcao()).isEqualTo("ANUNCIO_REMOVIDO_ADMINISTRATIVAMENTE");
    assertThat(auditoria.getValue().getRecursoId()).isEqualTo(anuncio.getId());
    assertThat(auditoria.getValue().getRequestId()).isEqualTo("req-remocao-admin");
    assertThat(auditoria.getValue().getCriadoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(auditoria.getValue().getDepoisJson())
        .contains("\"statusAnuncio\":\"REMOVIDO\"")
        .contains("\"decisao\":\"REMOVER\"")
        .contains("\"motivoSanitizado\":\"solicitacao administrativa confirmada\"");
  }

  @Test
  void repeticaoEBloqueioJuridicoRetornam409SemNovaAuditoria() {
    AnuncioEntity removido = anuncio(StatusAnuncio.PUBLICADO);
    removido.removerLogicamente(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    when(anuncioRepository.findByIdForModeration(removido.getId())).thenReturn(Optional.of(removido));

    assertStatus(
        HttpStatus.CONFLICT,
        () -> service.remover(
            removido.getId(),
            new AdminAnuncioRemocaoRequest("retry da mesma remocao"),
            admin(),
            "req-retry"));

    AnuncioEntity bloqueado = anuncio(StatusAnuncio.BLOQUEADO);
    when(anuncioRepository.findByIdForModeration(bloqueado.getId())).thenReturn(Optional.of(bloqueado));
    assertStatus(
        HttpStatus.CONFLICT,
        () -> service.remover(
            bloqueado.getId(),
            new AdminAnuncioRemocaoRequest("estado juridico incompativel"),
            admin(),
            "req-bloqueado"));

    verify(statusHistoricoRepository, never()).save(any());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  void alvoInexistenteRetorna404EMotivoInvalidoRetorna400() {
    UUID inexistente = UUID.randomUUID();
    when(anuncioRepository.findByIdForModeration(inexistente)).thenReturn(Optional.empty());

    assertStatus(
        HttpStatus.NOT_FOUND,
        () -> service.remover(
            inexistente,
            new AdminAnuncioRemocaoRequest("motivo administrativo valido"),
            admin(),
            "req-404"));
    assertStatus(
        HttpStatus.BAD_REQUEST,
        () -> service.remover(
            inexistente,
            new AdminAnuncioRemocaoRequest(" "),
            admin(),
            "req-400"));
  }

  @Test
  void moderadorRecebe403MesmoComAutoridadeDeModeracao() {
    AnuncioEntity anuncio = anuncio(StatusAnuncio.PUBLICADO);

    assertStatus(
        HttpStatus.FORBIDDEN,
        () -> service.remover(
            anuncio.getId(),
            new AdminAnuncioRemocaoRequest("motivo administrativo valido"),
            moderador(),
            "req-403"));

    verify(anuncioRepository, never()).findByIdForModeration(any());
  }

  private AnuncioEntity anuncio(StatusAnuncio status) {
    UUID id = UUID.randomUUID();
    AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
        id,
        UUID.randomUUID(),
        "remocao-" + id,
        "Anuncio sintetico para remocao",
        "Descricao sintetica suficientemente longa para validar remocao administrativa",
        "MASSAGENS",
        BigDecimal.TEN,
        "+5562999999999",
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    set(anuncio, "status", status);
    set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
    return anuncio;
  }

  private AdminUserPrincipal admin() {
    return principal(PapelUsuario.ADMIN, "ROLE_ADMIN");
  }

  private AdminUserPrincipal moderador() {
    return principal(PapelUsuario.MODERADOR, "ROLE_MODERADOR");
  }

  private AdminUserPrincipal principal(PapelUsuario papel, String role) {
    return new AdminUserPrincipal(
        UUID.randomUUID(),
        papel.name(),
        papel.name().toLowerCase() + "@example.invalid",
        "hash",
        List.of(papel),
        List.of(),
        List.of(
            new SimpleGrantedAuthority(role),
            new SimpleGrantedAuthority("ANUNCIO_MODERAR")),
        true);
  }

  private void assertStatus(HttpStatus status, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(ResponseStatusException.class, error ->
            assertThat(error.getStatusCode()).isEqualTo(status));
  }
}
