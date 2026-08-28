package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackagedMigrationCatalogTest {

    @Test
    void derivaVersaoMinimaDoSchemaDasMigrationsEmpacotadas() {
        var catalog = new PackagedMigrationCatalog();

        assertThat(catalog.minimumCompatibleVersion()).isEqualTo(53);
    }
}
