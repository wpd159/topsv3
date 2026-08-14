package br.com.topsdojob.v3.importacao.integracao;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MetricasMigracaoIntegral {

  private final long inicioNanos;
  private final long duracaoAnteriorNanos;
  private final Map<FaseMigracaoIntegral, Acumulador> fases =
      new EnumMap<>(FaseMigracaoIntegral.class);

  public MetricasMigracaoIntegral() {
    this(System.nanoTime(), 0);
  }

  private MetricasMigracaoIntegral(long inicioNanos, long duracaoAnteriorNanos) {
    this.inicioNanos = inicioNanos;
    this.duracaoAnteriorNanos = Math.max(0, duracaoAnteriorNanos);
  }

  public static MetricasMigracaoIntegral retomar(Checkpoint checkpoint) {
    if (checkpoint == null) {
      return new MetricasMigracaoIntegral();
    }
    MetricasMigracaoIntegral metricas = new MetricasMigracaoIntegral(
        System.nanoTime(), checkpoint.duracaoTotalNanos());
    checkpoint.fases().forEach((fase, estado) ->
        metricas.fases.put(fase, new Acumulador(estado)));
    return metricas;
  }

  public synchronized void registrar(
      FaseMigracaoIntegral fase,
      Duration duracao,
      long processados,
      long retries,
      long quarentenas,
      long bytes) {
    if (processados < 0 || retries < 0 || quarentenas < 0 || bytes < 0) {
      throw new IllegalArgumentException("metricas negativas nao sao permitidas");
    }
    long nanos = Math.max(0, duracao.toNanos());
    Acumulador acumulador = fases.computeIfAbsent(fase, ignored -> new Acumulador());
    acumulador.duracaoNanos += nanos;
    acumulador.processados += processados;
    acumulador.retries += retries;
    acumulador.quarentenas += quarentenas;
    acumulador.bytes += bytes;
    if (processados > 0) {
      long mediaObjeto = nanos / processados;
      for (long indice = 0; indice < Math.min(processados, 100_000); indice++) {
        acumulador.amostrasObjetoNanos.add(mediaObjeto);
      }
    }
  }

  public synchronized Relatorio relatorio() {
    Map<FaseMigracaoIntegral, ResumoFase> resumos = new EnumMap<>(FaseMigracaoIntegral.class);
    fases.forEach((fase, acumulador) -> resumos.put(fase, acumulador.resumo()));
    long processados = resumos.values().stream().mapToLong(ResumoFase::processados).sum();
    long duracaoNanos = duracaoTotalNanos();
    double throughput = duracaoNanos == 0
        ? 0
        : processados / (duracaoNanos / 1_000_000_000d);
    return new Relatorio(
        Duration.ofNanos(duracaoNanos),
        processados,
        throughput,
        Map.copyOf(resumos));
  }

  public synchronized Checkpoint checkpoint() {
    Map<FaseMigracaoIntegral, EstadoFase> estados =
        new EnumMap<>(FaseMigracaoIntegral.class);
    fases.forEach((fase, acumulador) -> estados.put(fase, acumulador.checkpoint()));
    return new Checkpoint(duracaoTotalNanos(), Map.copyOf(estados));
  }

  private long duracaoTotalNanos() {
    return duracaoAnteriorNanos + Math.max(0, System.nanoTime() - inicioNanos);
  }

  private static long percentil(List<Long> valores, double percentil) {
    if (valores.isEmpty()) {
      return 0;
    }
    List<Long> ordenados = valores.stream().sorted(Comparator.naturalOrder()).toList();
    int indice = (int) Math.ceil(percentil * ordenados.size()) - 1;
    return ordenados.get(Math.max(0, Math.min(indice, ordenados.size() - 1)));
  }

  private static final class Acumulador {
    private long duracaoNanos;
    private long processados;
    private long retries;
    private long quarentenas;
    private long bytes;
    private final List<Long> amostrasObjetoNanos = new ArrayList<>();

    private Acumulador() {
    }

    private Acumulador(EstadoFase estado) {
      this.duracaoNanos = estado.duracaoNanos();
      this.processados = estado.processados();
      this.retries = estado.retries();
      this.quarentenas = estado.quarentenas();
      this.bytes = estado.bytes();
      this.amostrasObjetoNanos.addAll(estado.amostrasObjetoNanos());
    }

    private ResumoFase resumo() {
      double segundos = duracaoNanos / 1_000_000_000d;
      double throughput = segundos == 0 ? 0 : processados / segundos;
      double bytesPorSegundo = segundos == 0 ? 0 : bytes / segundos;
      return new ResumoFase(
          Duration.ofNanos(duracaoNanos),
          processados,
          throughput,
          retries,
          quarentenas,
          bytes,
          Duration.ofNanos(percentil(amostrasObjetoNanos, 0.50)),
          Duration.ofNanos(percentil(amostrasObjetoNanos, 0.95)),
          Duration.ofNanos(amostrasObjetoNanos.stream().mapToLong(Long::longValue).max().orElse(0)),
          bytesPorSegundo);
    }

    private EstadoFase checkpoint() {
      return new EstadoFase(
          duracaoNanos,
          processados,
          retries,
          quarentenas,
          bytes,
          List.copyOf(amostrasObjetoNanos));
    }
  }

  public record Checkpoint(
      long duracaoTotalNanos,
      Map<FaseMigracaoIntegral, EstadoFase> fases) {

    public Checkpoint {
      duracaoTotalNanos = Math.max(0, duracaoTotalNanos);
      fases = Map.copyOf(fases == null ? Map.of() : fases);
    }
  }

  public record EstadoFase(
      long duracaoNanos,
      long processados,
      long retries,
      long quarentenas,
      long bytes,
      List<Long> amostrasObjetoNanos) {

    public EstadoFase {
      duracaoNanos = Math.max(0, duracaoNanos);
      processados = Math.max(0, processados);
      retries = Math.max(0, retries);
      quarentenas = Math.max(0, quarentenas);
      bytes = Math.max(0, bytes);
      amostrasObjetoNanos = List.copyOf(
          amostrasObjetoNanos == null ? List.of() : amostrasObjetoNanos);
    }
  }

  public record Relatorio(
      Duration duracaoTotal,
      long processados,
      double throughput,
      Map<FaseMigracaoIntegral, ResumoFase> fases) {
  }

  public record ResumoFase(
      Duration duracao,
      long processados,
      double throughput,
      long retries,
      long quarentenas,
      long bytes,
      Duration p50,
      Duration p95,
      Duration maximo,
      double bytesPorSegundo) {
  }
}
