package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LedgerSaldoInicialImportacaoMigrationTest {

    @Test
    void v026CriaTipoMetadataEUnicidadeProspectivos() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V026__ledger_saldo_inicial_importacao.sql"));

        assertThat(sql)
                .contains("MIGRACAO_SALDO_INICIAL")
                .contains("metadata_json jsonb NOT NULL")
                .contains("movimento_credito_migracao_saldo_usuario_uk")
                .contains("WHERE tipo = 'MIGRACAO_SALDO_INICIAL'")
                .contains("origem = 'IMPORTACAO'")
                .contains("saldo_antes = 0")
                .contains("snapshotFingerprint")
                .doesNotContain("UPDATE saldo_credito_usuario")
                .doesNotContain("AJUSTE_ADMIN_POSITIVO");
    }
}
