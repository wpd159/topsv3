package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RbacModeracaoV3MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V031__rbac_moderacao_v3.sql");

    @Test
    void v031CatalogaPermissoesEVinculaSomenteAdminEModerador() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("'ANUNCIO_LER'")
                .contains("'ANUNCIO_MODERAR'")
                .contains("FROM (VALUES ('ADMIN'), ('MODERADOR')) AS papeis(papel)")
                .contains("ON CONFLICT (codigo) DO UPDATE")
                .contains("ON CONFLICT (papel, permissao_id) DO NOTHING")
                .doesNotContain("('COMERCIAL')")
                .doesNotContain("('USUARIO')")
                .doesNotContain("DELETE FROM papel_permissao")
                .doesNotContain("UPDATE papel_permissao");
    }

    @Test
    void fixtureNaoConcedePermissoesDeModeracaoAComercialOuUsuario() throws Exception {
        String sql = Files.readString(Path.of(
                "..", "scripts", "local", "dados-sinteticos", "dados-admin-minimos.sql"));

        assertThat(sql)
                .contains("('ADMIN', 'ANUNCIO_LER')")
                .contains("('ADMIN', 'ANUNCIO_MODERAR')")
                .contains("('MODERADOR', 'ANUNCIO_LER')")
                .contains("('MODERADOR', 'ANUNCIO_MODERAR')")
                .doesNotContain("comercial.local@example.invalid")
                .doesNotContain("('COMERCIAL', 'ANUNCIO_LER')")
                .doesNotContain("('COMERCIAL', 'ANUNCIO_MODERAR')")
                .doesNotContain("('USUARIO', 'ANUNCIO_LER')")
                .doesNotContain("('USUARIO', 'ANUNCIO_MODERAR')");
    }
}
