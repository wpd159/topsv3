package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MetricasMigracaoIntegralTest {

  @Test
  void agregaDuracaoThroughputRetriesQuarentenaEPercentis() {
    MetricasMigracaoIntegral metricas = new MetricasMigracaoIntegral();

    metricas.registrar(
        FaseMigracaoIntegral.MANIFESTO_MIDIA,
        Duration.ofMillis(20),
        2,
        1,
        0,
        1_000);
    metricas.registrar(
        FaseMigracaoIntegral.MANIFESTO_MIDIA,
        Duration.ofMillis(90),
        3,
        2,
        1,
        2_000);

    var relatorio = metricas.relatorio();
    var fase = relatorio.fases().get(FaseMigracaoIntegral.MANIFESTO_MIDIA);
    assertThat(relatorio.processados()).isEqualTo(5);
    assertThat(relatorio.throughput()).isPositive();
    assertThat(fase.duracao()).isEqualTo(Duration.ofMillis(110));
    assertThat(fase.processados()).isEqualTo(5);
    assertThat(fase.retries()).isEqualTo(3);
    assertThat(fase.quarentenas()).isEqualTo(1);
    assertThat(fase.bytes()).isEqualTo(3_000);
    assertThat(fase.p50()).isEqualTo(Duration.ofMillis(30));
    assertThat(fase.p95()).isEqualTo(Duration.ofMillis(30));
    assertThat(fase.maximo()).isEqualTo(Duration.ofMillis(30));
    assertThat(fase.bytesPorSegundo()).isPositive();

    MetricasMigracaoIntegral retomadas = MetricasMigracaoIntegral.retomar(metricas.checkpoint());
    retomadas.registrar(
        FaseMigracaoIntegral.MANIFESTO_MIDIA,
        Duration.ofMillis(40),
        1,
        0,
        0,
        500);
    var faseRetomada = retomadas.relatorio().fases().get(FaseMigracaoIntegral.MANIFESTO_MIDIA);
    assertThat(faseRetomada.processados()).isEqualTo(6);
    assertThat(faseRetomada.duracao()).isEqualTo(Duration.ofMillis(150));
    assertThat(faseRetomada.retries()).isEqualTo(3);
    assertThat(faseRetomada.bytes()).isEqualTo(3_500);
    assertThat(faseRetomada.maximo()).isEqualTo(Duration.ofMillis(40));
  }
}
