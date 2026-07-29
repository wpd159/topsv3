package br.com.topsdojob.v3.application.admin.wizard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.repository.wizard.WizardProgressJdbcRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminWizardProgressServiceTest {

  private final WizardProgressJdbcRepository repository =
      mock(WizardProgressJdbcRepository.class);
  private final Clock clock = Clock.fixed(
      Instant.parse("2026-07-29T03:30:00Z"),
      ZoneOffset.UTC);
  private AdminWizardProgressService service;

  @BeforeEach
  void setup() {
    service = new AdminWizardProgressService(repository, clock);
  }

  @Test
  void calculaPeriodoNoFusoCanonicoEConversaoSemDivisaoPorZero() {
    stubEmpty();

    var dashboard = service.consultar(
        "7_DIAS",
        null,
        null,
        null,
        "TODOS",
        "TODOS",
        null,
        null,
        "TODOS",
        "TODOS",
        0,
        20);

    assertThat(dashboard.periodo().inicio()).hasToString("2026-07-23");
    assertThat(dashboard.periodo().fim()).hasToString("2026-07-29");
    assertThat(dashboard.periodo().fusoHorario()).isEqualTo("America/Sao_Paulo");
    assertThat(dashboard.indicadores().sessoesObservadas()).isZero();
    assertThat(dashboard.funil())
        .hasSize(8)
        .allSatisfy(step -> {
          assertThat(step.quantidade()).isZero();
          assertThat(step.conversaoPercentual()).isZero();
        });
  }

  @Test
  void retornaIndicadoresCoesosEEmailMascarado() {
    when(repository.resumo(any())).thenReturn(new WizardProgressJdbcRepository.ResumoRow(
        5, 4, 1, 2, 1, 2, 1, 1, 1, 1, 45L));
    when(repository.funil(any())).thenReturn(new long[] {5, 4, 4, 3, 2, 2, 1, 1});
    when(repository.etapasAtuais(any())).thenReturn(Map.of("FOTOS", 2L, "CONCLUIDO", 1L));
    UUID progressoId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    UUID anuncioId = UUID.randomUUID();
    OffsetDateTime criadoEm = OffsetDateTime.parse("2026-07-29T10:00:00Z");
    when(repository.listar(any(), anyInt(), anyInt())).thenReturn(
        new WizardProgressJdbcRepository.PaginaRow(List.of(
            new WizardProgressJdbcRepository.ItemRow(
                progressoId,
                usuarioId,
                "Usuario QA",
                "qa.progress@example.invalid",
                "CREATE",
                "PUBLICADO",
                "CONCLUIDO",
                "APROVADO",
                anuncioId,
                "anuncio-qa",
                "Anuncio QA",
                "PUBLICADO",
                criadoEm,
                criadoEm.plusMinutes(45),
                1L)), 1L));

    var dashboard = service.consultar(
        "HOJE",
        null,
        null,
        "QA",
        "CREATE",
        "PUBLICADO",
        "GO",
        "goiania",
        "APROVADO",
        "PUBLICADO",
        0,
        20);

    assertThat(dashboard.indicadores().sessoesObservadas()).isEqualTo(5);
    assertThat(dashboard.indicadores().tempoMedioConclusaoMinutos()).isEqualTo(45L);
    assertThat(dashboard.funil().get(1).perda()).isEqualTo(1);
    assertThat(dashboard.funil().get(1).conversaoPercentual()).isEqualByComparingTo("80.0");
    assertThat(dashboard.progresso().itens()).singleElement().satisfies(item -> {
      assertThat(item.emailMascarado()).isEqualTo("q***@example.invalid");
      assertThat(item.usuario()).isEqualTo("Usuario QA");
    });
  }

  @Test
  void periodoPersonalizadoInvalidoEhRecusado() {
    assertThatThrownBy(() -> service.consultar(
        "PERSONALIZADO",
        null,
        null,
        null,
        "TODOS",
        "TODOS",
        null,
        null,
        "TODOS",
        "TODOS",
        0,
        20))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400 BAD_REQUEST");
  }

  private void stubEmpty() {
    when(repository.resumo(any())).thenReturn(new WizardProgressJdbcRepository.ResumoRow(
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null));
    when(repository.funil(any())).thenReturn(new long[8]);
    when(repository.etapasAtuais(any())).thenReturn(Map.of());
    when(repository.listar(any(), anyInt(), anyInt())).thenReturn(
        new WizardProgressJdbcRepository.PaginaRow(List.of(), 0));
  }
}
