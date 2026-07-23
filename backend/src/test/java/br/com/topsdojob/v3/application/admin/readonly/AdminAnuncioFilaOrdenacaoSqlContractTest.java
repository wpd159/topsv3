package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminAnuncioFilaOrdenacaoSqlContractTest {

    @Test
    void ordenaEPaginaNoPostgresqlComMetricasCanonicas() throws Exception {
        String repository = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "persistence", "repository", "AnuncioRepository.java"));

        assertThat(repository)
                .contains("Page<AnuncioEntity> findFilaAdministrativa")
                .contains("agregado_visualizacao_inicial")
                .contains("evento_visualizacao")
                .contains("e.criado_em > corte.snapshot_corte_em")
                .contains("c.permitido = true")
                .contains("legado.anuncio_id is not null")
                .contains("vi.id is null")
                .contains("MAIS_RECENTES")
                .contains("MAIS_ANTIGOS")
                .contains("MAIS_VISUALIZACOES")
                .contains("MENOS_VISUALIZACOES")
                .contains("MAIS_CLIQUES_WHATSAPP")
                .contains("MENOS_CLIQUES_WHATSAPP")
                .contains(":situacao = 'PENDENTES_MODERACAO'")
                .contains("a.status = 'PAUSADO'")
                .contains(":situacao = 'REJEITADOS'")
                .doesNotContain("a.status_moderacao = 'BLOQUEADO'")
                .doesNotContain("agregado_visualizacao_diaria");
    }

    @Test
    void controllerNaoReintroduzFiltroDeEstadoEDefineTrintaItens() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "web", "admin", "readonly", "AdminAnuncioDetalhadoController.java"));

        assertThat(controller)
                .contains("@RequestParam(defaultValue = \"30\") int size")
                .contains("AdminAnuncioSituacao situacao")
                .contains("AdminAnuncioOrdenacao ordenacao")
                .contains("return semCache(service.listar")
                .doesNotContain("StatusModeracaoAnuncio statusModeracao")
                .doesNotContain("StatusAnuncio status");
    }
}
