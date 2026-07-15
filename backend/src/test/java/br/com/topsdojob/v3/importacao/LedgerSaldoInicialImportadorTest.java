package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LedgerSaldoInicialImportadorTest {

    @Test
    void reconciliadorUsaSaldoOperacionalEPermaneceIdempotente() throws Exception {
        String sql = Files.readString(Path.of(
                "..", "scripts", "local", "importacao",
                "reconciliar-ledger-saldo-inicial.sql"));

        assertThat(sql)
                .contains("MIGRACAO_SALDO_INICIAL")
                .contains("ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING")
                .contains("snapshotFingerprint")
                .contains("divergenciaHistorica")
                .contains("usuarioLegadoHash")
                .contains("saldo_antes")
                .contains("0,")
                .doesNotContain("saldo_credito_usuario")
                .doesNotContain("AJUSTE_ADMIN")
                .doesNotContain("historico_creditos");
    }
}
