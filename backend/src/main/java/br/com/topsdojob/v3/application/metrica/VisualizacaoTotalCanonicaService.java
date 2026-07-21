package br.com.topsdojob.v3.application.metrica;

import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoInicialEntity;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoInicialRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisualizacaoTotalCanonicaService {
  private final AgregadoVisualizacaoInicialRepository agregadoInicialRepository;
  private final EventoVisualizacaoRepository eventoRepository;
  private final AnuncioRepository anuncioRepository;

  public VisualizacaoTotalCanonicaService(
      AgregadoVisualizacaoInicialRepository agregadoInicialRepository,
      EventoVisualizacaoRepository eventoRepository,
      AnuncioRepository anuncioRepository) {
    this.agregadoInicialRepository = agregadoInicialRepository;
    this.eventoRepository = eventoRepository;
    this.anuncioRepository = anuncioRepository;
  }

  @Transactional(readOnly = true)
  public VisualizacoesCanonicasDto calcular(UUID anuncioId) {
    UUID id = Objects.requireNonNull(anuncioId, "anuncioId obrigatorio");
    return calcularEmLote(List.of(id)).get(id);
  }

  @Transactional(readOnly = true)
  public Map<UUID, VisualizacoesCanonicasDto> calcularEmLote(Collection<UUID> anuncioIds) {
    if (anuncioIds == null || anuncioIds.isEmpty()) {
      return Map.of();
    }

    List<UUID> ids = anuncioIds.stream()
        .map(id -> Objects.requireNonNull(id, "anuncioId obrigatorio"))
        .distinct()
        .toList();
    Map<UUID, AgregadoVisualizacaoInicialEntity> historicos = agregadoInicialRepository
        .findByAnuncioIdIn(ids)
        .stream()
        .collect(Collectors.toMap(AgregadoVisualizacaoInicialEntity::getAnuncioId, Function.identity()));
    Map<UUID, Long> eventos = eventoRepository.countCanonicosByAnuncioIdIn(ids).stream()
        .collect(Collectors.toMap(
            EventoVisualizacaoRepository.ContagemCanonicaProjection::getAnuncioId,
            EventoVisualizacaoRepository.ContagemCanonicaProjection::getTotalEventos));
    Set<UUID> legados = Set.copyOf(anuncioRepository.findIdsComMapeamentoLegado(ids));

    Map<UUID, VisualizacoesCanonicasDto> resultado = new LinkedHashMap<>();
    for (UUID id : ids) {
      AgregadoVisualizacaoInicialEntity historico = historicos.get(id);
      if (historico == null && legados.contains(id)) {
        resultado.put(id, VisualizacoesCanonicasDto.historicoPendente());
        continue;
      }

      long totalInicial = historico == null ? 0 : historico.getTotalVisualizacoes();
      long eventosV3 = eventos.getOrDefault(id, 0L);
      resultado.put(id, VisualizacoesCanonicasDto.total(Math.addExact(totalInicial, eventosV3)));
    }
    return Map.copyOf(resultado);
  }

}
