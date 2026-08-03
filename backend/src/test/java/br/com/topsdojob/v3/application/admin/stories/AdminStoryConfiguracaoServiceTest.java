package br.com.topsdojob.v3.application.admin.stories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryConfiguracaoRequest;
import br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminStoryConfiguracaoServiceTest {

  private StoryConfiguracaoComercialRepository configuracoes;
  private BeneficioPremiumRepository beneficios;
  private AdminCreditoOperacaoService auditoria;
  private AdminStoryConfiguracaoService service;
  private AdminUserPrincipal admin;

  @BeforeEach
  void setUp() {
    configuracoes = mock(StoryConfiguracaoComercialRepository.class);
    beneficios = mock(BeneficioPremiumRepository.class);
    auditoria = mock(AdminCreditoOperacaoService.class);
    admin = mock(AdminUserPrincipal.class);
    when(admin.isEnabled()).thenReturn(true);
    when(admin.usuarioId()).thenReturn(UUID.randomUUID());
    when(beneficios.findByCodigo("STORIES"))
        .thenReturn(Optional.empty(), Optional.of(mock(BeneficioPremiumEntity.class)));
    when(beneficios.inserirCatalogoSeAusente(
        any(), any(), any(), any(), any(), eq(false), eq(true), eq(0), any()))
        .thenReturn(0);
    when(configuracoes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    service = new AdminStoryConfiguracaoService(configuracoes, beneficios, auditoria);
  }

  @Test
  void ausenciaDeConfiguracaoEhIndisponivelESemPrecoInventado() {
    when(configuracoes.findById(StoryConfiguracaoComercialEntity.SINGLETON_ID))
        .thenReturn(Optional.empty());

    var resultado = service.consultar();

    assertThat(resultado.configurada()).isFalse();
    assertThat(resultado.ativo()).isFalse();
    assertThat(resultado.custoCreditos()).isNull();
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
  }

  @Test
  void adminCriaConfiguracaoMesmoQuandoIdentidadeFoiInseridaConcorrentemente() {
    when(configuracoes.findForUpdate()).thenReturn(Optional.empty());

    var resultado = service.salvar(
        new AdminStoryConfiguracaoRequest(true, 0, null), admin, "req-story-config");

    assertThat(resultado.configurada()).isTrue();
    assertThat(resultado.ativo()).isTrue();
    assertThat(resultado.custoCreditos()).isZero();
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    verify(beneficios).inserirCatalogoSeAusente(
        any(), eq("STORIES"), eq("Stories"), any(), eq("ANUNCIO"),
        eq(false), eq(true), eq(0), any());
    verify(configuracoes).saveAndFlush(any());
    verify(auditoria).auditar(
        eq(admin.usuarioId()), eq("STORY_CONFIGURACAO_ATUALIZAR"),
        eq("STORY_CONFIGURACAO_COMERCIAL"), any(), any(), any(), eq("req-story-config"));
  }

  @Test
  void adminAtualizaSomenteStatusECustoComVersaoEsperada() {
    StoryConfiguracaoComercialEntity existente = StoryConfiguracaoComercialEntity.criar(
        true, 4, admin.usuarioId(), OffsetDateTime.parse("2026-08-02T12:00:00Z"));
    when(configuracoes.findForUpdate()).thenReturn(Optional.of(existente));
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(mock(BeneficioPremiumEntity.class)));

    var resultado = service.salvar(
        new AdminStoryConfiguracaoRequest(false, 9, 0L), admin, "req-story-update");

    assertThat(resultado.ativo()).isFalse();
    assertThat(resultado.custoCreditos()).isEqualTo(9);
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    verify(beneficios, never()).inserirCatalogoSeAusente(
        any(), any(), any(), any(), any(), eq(false), eq(true), eq(0), any());
  }

  @Test
  void custoNegativoEhRejeitadoSemPersistencia() {
    assertStatus(
        () -> service.salvar(new AdminStoryConfiguracaoRequest(true, -1, null), admin, "req-negativo"),
        HttpStatus.BAD_REQUEST);

    verify(configuracoes, never()).saveAndFlush(any());
    verify(auditoria, never()).auditar(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void versaoDivergenteProtegeAtualizacaoConcorrente() {
    StoryConfiguracaoComercialEntity existente = StoryConfiguracaoComercialEntity.criar(
        true, 4, admin.usuarioId(), OffsetDateTime.parse("2026-08-02T12:00:00Z"));
    when(configuracoes.findForUpdate()).thenReturn(Optional.of(existente));

    assertStatus(
        () -> service.salvar(new AdminStoryConfiguracaoRequest(false, 8, 99L), admin, "req-conflito"),
        HttpStatus.CONFLICT);

    verify(configuracoes, never()).saveAndFlush(any());
  }

  @Test
  void sessaoAdministrativaAusenteEhRejeitada() {
    assertStatus(
        () -> service.salvar(new AdminStoryConfiguracaoRequest(true, 2, null), null, "req-sem-sessao"),
        HttpStatus.UNAUTHORIZED);
  }

  private void assertStatus(Runnable action, HttpStatus status) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
  }
}
