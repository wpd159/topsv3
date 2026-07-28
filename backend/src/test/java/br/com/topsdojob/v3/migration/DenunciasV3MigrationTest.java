package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DenunciasV3MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V039__denuncias_anuncios_funcionais.sql");

    @Test
    void v039CriaPersistenciaIdempotenteSemAcaoAutomaticaNoAnuncio() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE denuncia_anuncio")
                .contains("REFERENCES anuncio(id)")
                .contains("denunciante_contexto_hash")
                .contains("idempotency_key")
                .contains("denuncia_anuncio_idempotencia_uk")
                .contains("'PENDENTE', 'PUNIDA', 'IGNORADA'")
                .contains("ip_hash")
                .contains("user_agent_hash")
                .doesNotContain("ON DELETE CASCADE")
                .doesNotContain("UPDATE anuncio")
                .doesNotContain("DELETE FROM anuncio");
    }
}
