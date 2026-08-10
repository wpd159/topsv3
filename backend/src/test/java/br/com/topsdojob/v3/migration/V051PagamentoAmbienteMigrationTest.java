package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V051PagamentoAmbienteMigrationTest {

    @Test
    void migrationEhAditivaSemInferenciaDoHistorico() throws IOException {
        String sql = Files.readString(migration())
                .toUpperCase(Locale.ROOT);

        assertThat(sql)
                .contains("ALTER TABLE PAGAMENTO")
                .contains("ADD COLUMN AMBIENTE TEXT")
                .contains("'SANDBOX'")
                .contains("'PRODUCAO'")
                .doesNotContain(" NOT NULL")
                .doesNotContain(" DEFAULT ")
                .doesNotMatch("(?s).*\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE)\\b.*");
    }

    private Path migration() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path direct = current.resolve(
                "src/main/resources/db/migration/V051__pagamento_ambiente_efi.sql");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path nested = current.resolve(
                "backend/src/main/resources/db/migration/V051__pagamento_ambiente_efi.sql");
        assertThat(nested).isRegularFile();
        return nested;
    }
}
