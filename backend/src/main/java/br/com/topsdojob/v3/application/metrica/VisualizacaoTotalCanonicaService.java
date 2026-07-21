package br.com.topsdojob.v3.application.metrica;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoInicialEntity;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoInicialRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisualizacaoTotalCanonicaService {
  private final AgregadoVisualizacaoInicialRepository agregadoInicialRepository;
  private final EventoVisualizacaoRepository eventoRepository;

  public VisualizacaoTotalCanonicaService(
      AgregadoVisualizacaoInicialRepository agregadoInicialRepository,
      EventoVisualizacaoRepository eventoRepository) {
    this.agregadoInicialRepository = agregadoInicialRepository;
    this.eventoRepository = eventoRepository;
  }

  @Transactional(readOnly = true)
  public TotalVisualizacoes calcular(UUID anuncioId) {
    return agregadoInicialRepository.findByAnuncioId(anuncioId)
        .map(inicial -> calcularComHistorico(anuncioId, inicial))
        .orElseGet(() -> calcularSemHistorico(anuncioId));
  }

  private TotalVisualizacoes calcularComHistorico(
      UUID anuncioId,
      AgregadoVisualizacaoInicialEntity inicial) {
    long totalInicial = inicial.getTotalVisualizacoes();
    OffsetDateTime corte = inicial.getSnapshotCorteEm();
    long eventosNovos = eventoRepository.countByAnuncioIdAndCriadoEmAfter(anuncioId, corte);
    return new TotalVisualizacoes(totalInicial, eventosNovos, Math.addExact(totalInicial, eventosNovos), corte);
  }

  private TotalVisualizacoes calcularSemHistorico(UUID anuncioId) {
    long eventosNovos = eventoRepository.countByAnuncioId(anuncioId);
    return new TotalVisualizacoes(0, eventosNovos, eventosNovos, null);
  }

  public record TotalVisualizacoes(
      long historicoInicial,
      long eventosV3,
      long total,
      OffsetDateTime snapshotCorteEm) {
  }
}
