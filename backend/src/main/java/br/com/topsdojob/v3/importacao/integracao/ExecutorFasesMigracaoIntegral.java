package br.com.topsdojob.v3.importacao.integracao;

import java.util.Map;
import java.util.UUID;

public interface ExecutorFasesMigracaoIntegral {

  ResultadoEtapa executar(FaseMigracaoIntegral fase, Contexto contexto);

  record Contexto(
      PacoteMigracaoIntegral pacote,
      UUID execucaoId,
      String fingerprintPacote,
      ModoMigracaoIntegral modo,
      int tamanhoLote,
      boolean retomada,
      MetricasMigracaoIntegral metricas) {
  }

  record ResultadoEtapa(
      long processados,
      long quarentenas,
      long retries,
      long bytes,
      Map<String, Object> resumo) {

    public ResultadoEtapa {
      resumo = Map.copyOf(resumo == null ? Map.of() : resumo);
    }

    public static ResultadoEtapa vazio() {
      return new ResultadoEtapa(0, 0, 0, 0, Map.of());
    }
  }
}
