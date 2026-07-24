package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SexoVirtualModalidadeAdicionalMigrationTest {

    @Test
    void v033RegularizaCategoriaVirtualComoModalidadeSemAlterarEstadoOperacional() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V033__sexo_virtual_modalidade_adicional.sql"));

        assertThat(sql)
                .contains("ADD COLUMN IF NOT EXISTS atendimento_exclusivamente_virtual")
                .contains("NOT NULL DEFAULT false")
                .contains("INSERT INTO anuncio_servicos")
                .contains("'VIDEOCHAMADA'")
                .contains("ON CONFLICT (anuncio_id, servico) DO NOTHING")
                .contains("SET categoria = 'ACOMPANHANTE_FEMININA'")
                .contains("WHERE categoria = 'VENDA_DE_CONTEUDO'")
                .contains("UPDATE documento_busca_anuncio")
                .contains("CREATE INDEX IF NOT EXISTS anuncio_sexo_virtual_catalogo_idx")
                .doesNotContain("SET status =")
                .doesNotContain("SET status_moderacao =")
                .doesNotContain("SET slug =")
                .doesNotContain("UPDATE arquivo_midia")
                .doesNotContain("UPDATE ativacao_beneficio")
                .doesNotContain("DELETE FROM")
                .doesNotContain("DROP TABLE");
    }
}
