package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImportadorBaseIdempotenciaTest {

  private static final Path IMPORTADOR = Path.of(
      "..", "scripts", "local", "importacao", "dryrun-producao-v3-saneado.sql");

  @Test
  void reutilizaSomenteExecucaoConcluidaDoMesmoSnapshot() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("e.status NOT IN ('CONCLUIDA', 'CONCLUIDA_COM_PENDENCIAS')")
        .contains("e.iniciado_em <> c.snapshot_at")
        .contains("(e.resumo_json ->> 'snapshotId') IS DISTINCT FROM c.snapshot_id")
        .contains("(e.resumo_json ->> 'snapshotFingerprint') IS DISTINCT FROM c.snapshot_fingerprint")
        .contains("(e.resumo_json ->> 'storageDestinationFingerprint')")
        .contains("IS DISTINCT FROM c.storage_destination_fingerprint")
        .contains("AS dryrun_snapshot_novo")
        .contains("\\gset");
    assertThat(sql)
        .contains("\\if :dryrun_snapshot_novo")
        .contains("\\endif\n\nCOMMIT;");
  }

  @Test
  void mantemIdDeterministicoSemApagarHistoricoOuSortearNovaExecucao() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id")
        .doesNotContain("gen_random_uuid()")
        .doesNotContain("DELETE FROM importacao_execucao")
        .doesNotContain("TRUNCATE importacao_execucao")
        .doesNotContain("ON CONFLICT (id) DO UPDATE");
  }
}
