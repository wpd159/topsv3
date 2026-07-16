package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RbacConfiguracaoAdminMigrationTest {

    @Test
    void v028CatalogaAdminConfigurarEVinculaSomenteAoAdmin() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V028__rbac_configuracao_admin.sql"));

        assertThat(sql)
                .contains("INSERT INTO permissao")
                .contains("'ADMIN_CONFIGURAR'")
                .contains("INSERT INTO papel_permissao")
                .contains("'ADMIN'")
                .contains("ON CONFLICT (papel, permissao_id) DO NOTHING")
                .doesNotContain("'MODERADOR'")
                .doesNotContain("'USUARIO'");
    }
}
