package br.com.topsdojob.v3.application.metrica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoInicialEntity;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoInicialRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisualizacaoTotalCanonicaServiceTest {

  private final AgregadoVisualizacaoInicialRepository agregadoRepository =
      mock(AgregadoVisualizacaoInicialRepository.class);
  private final EventoVisualizacaoRepository eventoRepository =
      mock(EventoVisualizacaoRepository.class);
  private final VisualizacaoTotalCanonicaService service =
      new VisualizacaoTotalCanonicaService(agregadoRepository, eventoRepository);

  @Test
  void somaHistoricoInicialComEventosPosterioresAoCorte() {
    UUID anuncioId = UUID.randomUUID();
    OffsetDateTime corte = OffsetDateTime.parse("2026-07-21T12:00:00Z");
    AgregadoVisualizacaoInicialEntity inicial = mock(AgregadoVisualizacaoInicialEntity.class);
    when(inicial.getTotalVisualizacoes()).thenReturn(42L);
    when(inicial.getSnapshotCorteEm()).thenReturn(corte);
    when(agregadoRepository.findByAnuncioId(anuncioId)).thenReturn(Optional.of(inicial));
    when(eventoRepository.countByAnuncioIdAndCriadoEmAfter(anuncioId, corte)).thenReturn(3L);

    var total = service.calcular(anuncioId);

    assertThat(total.historicoInicial()).isEqualTo(42);
    assertThat(total.eventosV3()).isEqualTo(3);
    assertThat(total.total()).isEqualTo(45);
    assertThat(total.snapshotCorteEm()).isEqualTo(corte);
    verify(eventoRepository).countByAnuncioIdAndCriadoEmAfter(anuncioId, corte);
    verifyNoMoreInteractions(eventoRepository);
  }

  @Test
  void semHistoricoUsaSomenteEventosBrutosV3() {
    UUID anuncioId = UUID.randomUUID();
    when(agregadoRepository.findByAnuncioId(anuncioId)).thenReturn(Optional.empty());
    when(eventoRepository.countByAnuncioId(anuncioId)).thenReturn(7L);

    var total = service.calcular(anuncioId);

    assertThat(total.historicoInicial()).isZero();
    assertThat(total.eventosV3()).isEqualTo(7);
    assertThat(total.total()).isEqualTo(7);
    assertThat(total.snapshotCorteEm()).isNull();
    verify(eventoRepository).countByAnuncioId(anuncioId);
    verifyNoMoreInteractions(eventoRepository);
  }

  @Test
  void historicoZeroContinuaRespeitandoOCorteTemporal() {
    UUID anuncioId = UUID.randomUUID();
    OffsetDateTime corte = OffsetDateTime.parse("2026-07-21T12:00:00Z");
    AgregadoVisualizacaoInicialEntity inicial = mock(AgregadoVisualizacaoInicialEntity.class);
    when(inicial.getTotalVisualizacoes()).thenReturn(0L);
    when(inicial.getSnapshotCorteEm()).thenReturn(corte);
    when(agregadoRepository.findByAnuncioId(anuncioId)).thenReturn(Optional.of(inicial));
    when(eventoRepository.countByAnuncioIdAndCriadoEmAfter(anuncioId, corte)).thenReturn(1L);

    var total = service.calcular(anuncioId);

    assertThat(total.historicoInicial()).isZero();
    assertThat(total.eventosV3()).isOne();
    assertThat(total.total()).isOne();
    verify(eventoRepository).countByAnuncioIdAndCriadoEmAfter(anuncioId, corte);
    verifyNoMoreInteractions(eventoRepository);
  }

  @Test
  void fonteCanonicaNaoDependeDeAgregadoDiarioCliquesOuImpressoes() {
    var dependencias = Arrays.stream(VisualizacaoTotalCanonicaService.class.getDeclaredFields())
        .map(campo -> campo.getType().getSimpleName())
        .toList();

    assertThat(dependencias)
        .containsExactlyInAnyOrder(
            "AgregadoVisualizacaoInicialRepository",
            "EventoVisualizacaoRepository")
        .doesNotContain(
            "AgregadoVisualizacaoDiariaRepository",
            "CliqueWhatsappRepository",
            "AgregadoCliqueWhatsappDiarioRepository");
  }
}
