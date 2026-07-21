package br.com.topsdojob.v3.application.metrica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto.Situacao;
import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoInicialEntity;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoInicialRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisualizacaoTotalCanonicaServiceTest {

  private final AgregadoVisualizacaoInicialRepository agregadoRepository =
      mock(AgregadoVisualizacaoInicialRepository.class);
  private final EventoVisualizacaoRepository eventoRepository =
      mock(EventoVisualizacaoRepository.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final VisualizacaoTotalCanonicaService service =
      new VisualizacaoTotalCanonicaService(agregadoRepository, eventoRepository, anuncioRepository);

  @Test
  void somaHistoricoInicialComEventosPosterioresAoCorte() {
    UUID anuncioId = UUID.randomUUID();
    AgregadoVisualizacaoInicialEntity inicial = historico(anuncioId, 42L);
    EventoVisualizacaoRepository.ContagemCanonicaProjection eventos = contagem(anuncioId, 3L);
    when(agregadoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of(inicial));
    when(eventoRepository.countCanonicosByAnuncioIdIn(List.of(anuncioId)))
        .thenReturn(List.of(eventos));
    when(anuncioRepository.findIdsComMapeamentoLegado(List.of(anuncioId))).thenReturn(List.of(anuncioId));

    VisualizacoesCanonicasDto total = service.calcular(anuncioId);

    assertThat(total.total()).isEqualTo(45L);
    assertThat(total.situacao()).isEqualTo(Situacao.DISPONIVEL);
  }

  @Test
  void anuncioLegadoSemAgregadoNaoExpoeEventosComoTotalParcial() {
    UUID anuncioId = UUID.randomUUID();
    EventoVisualizacaoRepository.ContagemCanonicaProjection eventos = contagem(anuncioId, 7L);
    when(agregadoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of());
    when(eventoRepository.countCanonicosByAnuncioIdIn(List.of(anuncioId)))
        .thenReturn(List.of(eventos));
    when(anuncioRepository.findIdsComMapeamentoLegado(List.of(anuncioId))).thenReturn(List.of(anuncioId));

    VisualizacoesCanonicasDto total = service.calcular(anuncioId);

    assertThat(total.total()).isNull();
    assertThat(total.situacao()).isEqualTo(Situacao.HISTORICO_PENDENTE);
  }

  @Test
  void anuncioNativoSemEventosTemZeroLegitimo() {
    UUID anuncioId = UUID.randomUUID();
    when(agregadoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of());
    when(eventoRepository.countCanonicosByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of());
    when(anuncioRepository.findIdsComMapeamentoLegado(List.of(anuncioId))).thenReturn(List.of());

    VisualizacoesCanonicasDto total = service.calcular(anuncioId);

    assertThat(total.total()).isZero();
    assertThat(total.situacao()).isEqualTo(Situacao.ZERO_LEGITIMO);
  }

  @Test
  void anuncioNativoComEventosUsaSomenteEventosV3() {
    UUID anuncioId = UUID.randomUUID();
    EventoVisualizacaoRepository.ContagemCanonicaProjection eventos = contagem(anuncioId, 4L);
    when(agregadoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of());
    when(eventoRepository.countCanonicosByAnuncioIdIn(List.of(anuncioId)))
        .thenReturn(List.of(eventos));
    when(anuncioRepository.findIdsComMapeamentoLegado(List.of(anuncioId))).thenReturn(List.of());

    VisualizacoesCanonicasDto total = service.calcular(anuncioId);

    assertThat(total.total()).isEqualTo(4L);
    assertThat(total.situacao()).isEqualTo(Situacao.DISPONIVEL);
  }

  @Test
  void loteUsaConsultasFixasSemNMaisUm() {
    UUID legado = UUID.randomUUID();
    UUID nativo = UUID.randomUUID();
    UUID completo = UUID.randomUUID();
    List<UUID> ids = List.of(legado, nativo, completo);
    AgregadoVisualizacaoInicialEntity historicoCompleto = historico(completo, 10L);
    EventoVisualizacaoRepository.ContagemCanonicaProjection eventosNativo = contagem(nativo, 2L);
    EventoVisualizacaoRepository.ContagemCanonicaProjection eventosCompleto = contagem(completo, 5L);
    when(agregadoRepository.findByAnuncioIdIn(ids)).thenReturn(List.of(historicoCompleto));
    when(eventoRepository.countCanonicosByAnuncioIdIn(ids))
        .thenReturn(List.of(eventosNativo, eventosCompleto));
    when(anuncioRepository.findIdsComMapeamentoLegado(ids)).thenReturn(List.of(legado, completo));

    Map<UUID, VisualizacoesCanonicasDto> totais = service.calcularEmLote(ids);

    assertThat(totais).hasSize(3);
    assertThat(totais.get(legado).situacao()).isEqualTo(Situacao.HISTORICO_PENDENTE);
    assertThat(totais.get(nativo).total()).isEqualTo(2L);
    assertThat(totais.get(completo).total()).isEqualTo(15L);
    verify(agregadoRepository).findByAnuncioIdIn(ids);
    verify(eventoRepository).countCanonicosByAnuncioIdIn(ids);
    verify(anuncioRepository).findIdsComMapeamentoLegado(ids);
    verify(agregadoRepository, never()).findByAnuncioId(org.mockito.ArgumentMatchers.any());
    verify(eventoRepository, never()).countByAnuncioId(org.mockito.ArgumentMatchers.any());
    verify(eventoRepository, never()).countByAnuncioIdAndCriadoEmAfter(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void ordemDaPaginaNaoAlteraATotalizacao() {
    UUID primeiro = UUID.randomUUID();
    UUID segundo = UUID.randomUUID();
    AgregadoVisualizacaoInicialEntity historicoPrimeiro = historico(primeiro, 8L);
    EventoVisualizacaoRepository.ContagemCanonicaProjection eventosPrimeiro = contagem(primeiro, 2L);
    EventoVisualizacaoRepository.ContagemCanonicaProjection eventosSegundo = contagem(segundo, 3L);
    when(agregadoRepository.findByAnuncioIdIn(anyList()))
        .thenReturn(List.of(historicoPrimeiro));
    when(eventoRepository.countCanonicosByAnuncioIdIn(anyList()))
        .thenReturn(List.of(eventosPrimeiro, eventosSegundo));
    when(anuncioRepository.findIdsComMapeamentoLegado(org.mockito.ArgumentMatchers.anyCollection()))
        .thenReturn(List.of(primeiro));

    Map<UUID, VisualizacoesCanonicasDto> seedA = service.calcularEmLote(List.of(primeiro, segundo));
    Map<UUID, VisualizacoesCanonicasDto> seedB = service.calcularEmLote(List.of(segundo, primeiro));

    assertThat(seedA).containsExactlyInAnyOrderEntriesOf(seedB);
    assertThat(seedA.get(primeiro).total()).isEqualTo(10L);
    assertThat(seedA.get(segundo).total()).isEqualTo(3L);
  }

  @Test
  void erroDeConsultaNaoViraZero() {
    UUID anuncioId = UUID.randomUUID();
    when(agregadoRepository.findByAnuncioIdIn(List.of(anuncioId)))
        .thenThrow(new IllegalStateException("falha de banco"));

    assertThatThrownBy(() -> service.calcular(anuncioId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("falha de banco");
  }

  @Test
  void fonteCanonicaNaoDependeDeAgregadoDiarioCliquesOuImpressoes() {
    var dependencias = Arrays.stream(VisualizacaoTotalCanonicaService.class.getDeclaredFields())
        .map(campo -> campo.getType().getSimpleName())
        .toList();

    assertThat(dependencias)
        .containsExactlyInAnyOrder(
            "AgregadoVisualizacaoInicialRepository",
            "EventoVisualizacaoRepository",
            "AnuncioRepository")
        .doesNotContain(
            "AgregadoVisualizacaoDiariaRepository",
            "CliqueWhatsappRepository",
            "AgregadoCliqueWhatsappDiarioRepository");
  }

  private AgregadoVisualizacaoInicialEntity historico(UUID anuncioId, long total) {
    AgregadoVisualizacaoInicialEntity historico = mock(AgregadoVisualizacaoInicialEntity.class);
    when(historico.getAnuncioId()).thenReturn(anuncioId);
    when(historico.getTotalVisualizacoes()).thenReturn(total);
    return historico;
  }

  private EventoVisualizacaoRepository.ContagemCanonicaProjection contagem(UUID anuncioId, long total) {
    EventoVisualizacaoRepository.ContagemCanonicaProjection contagem =
        mock(EventoVisualizacaoRepository.ContagemCanonicaProjection.class);
    when(contagem.getAnuncioId()).thenReturn(anuncioId);
    when(contagem.getTotalEventos()).thenReturn(total);
    return contagem;
  }
}
