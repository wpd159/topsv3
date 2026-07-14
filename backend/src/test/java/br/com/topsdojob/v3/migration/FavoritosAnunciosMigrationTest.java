package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FavoritosAnunciosMigrationTest {

    @Test
    void migrationEAdditivaComUnicidadeFksEIndices() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration", "V025__favoritos_anuncios.sql"));

        assertThat(sql)
                .contains("CREATE TABLE favorito_anuncio")
                .contains("usuario_id uuid NOT NULL REFERENCES usuario (id) ON DELETE CASCADE")
                .contains("anuncio_id uuid NOT NULL REFERENCES anuncio (id) ON DELETE CASCADE")
                .contains("UNIQUE (usuario_id, anuncio_id)")
                .contains("favorito_anuncio_usuario_criado_idx")
                .contains("favorito_anuncio_anuncio_idx")
                .doesNotContain("DROP TABLE")
                .doesNotContain("ALTER TABLE anuncio")
                .doesNotContain("INSERT INTO");
    }
}
