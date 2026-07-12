package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AtendimentoServicosAnuncioMigrationTest {

    @Test
    void v020CriaColecoesEstruturadasSemInferenciaOuDados() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V020__atendimento_servicos_anuncio.sql"));

        assertThat(sql)
                .contains("CREATE TABLE anuncio_local_atendimento")
                .contains("local_atendimento IN ('A_COMBINAR', 'HOTEL_MOTEL', 'MEU_LOCAL')")
                .contains("CREATE TABLE anuncio_servicos")
                .contains("'ANAL'")
                .contains("PRIMARY KEY (anuncio_id, local_atendimento)")
                .contains("PRIMARY KEY (anuncio_id, servico)")
                .doesNotContain("INSERT INTO")
                .doesNotContain("descricao")
                .doesNotContain("UPDATE anuncio");
    }
}
