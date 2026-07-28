package br.com.topsdojob.v3.web.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.repository.AgregadoCliqueWhatsappDiarioRepository;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoDiariaRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminDashboardContratoTest {

    @Test
    void endpointExigeAdminEPermissaoDeLeituraDeAnuncio() throws Exception {
        Method method = AdminDashboardController.class.getMethod("hoje");
        PreAuthorize controleAcesso = method.getAnnotation(PreAuthorize.class);

        assertThat(controleAcesso).isNotNull();
        assertThat(controleAcesso.value())
                .contains("hasRole('ADMIN')")
                .contains("hasAuthority('ANUNCIO_LER')");
    }

    @Test
    void endpointsAnaliticosPermitemAdminEModeradorComLeituraDeAnuncio() {
        for (String nome : Arrays.asList(
                "desempenhoDiario",
                "topWhatsappHoje",
                "analises")) {
            Method method = Arrays.stream(AdminDashboardController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(nome))
                    .findFirst()
                    .orElseThrow();
            PreAuthorize controleAcesso = method.getAnnotation(PreAuthorize.class);

            assertThat(controleAcesso).isNotNull();
            assertThat(controleAcesso.value())
                    .contains("hasAnyRole('ADMIN','MODERADOR')")
                    .contains("hasAuthority('ANUNCIO_LER')");
        }
    }

    @Test
    void servicoDeclaraConstrutorDeProducaoParaInjecaoDoSpring() throws Exception {
        var constructor = br.com.topsdojob.v3.application.admin.dashboard.AdminDashboardHojeService.class
                .getConstructor(
                        AgregadoVisualizacaoDiariaRepository.class,
                        AgregadoCliqueWhatsappDiarioRepository.class,
                        AtivacaoBeneficioRepository.class);

        assertThat(constructor.getAnnotation(Autowired.class)).isNotNull();
    }

    @Test
    void metricasDiariasUsamSomenteAgregadosPersistidos() throws Exception {
        Query visualizacoes = AgregadoVisualizacaoDiariaRepository.class
                .getMethod("somarPorData", java.time.LocalDate.class)
                .getAnnotation(Query.class);
        Query cliques = AgregadoCliqueWhatsappDiarioRepository.class
                .getMethod("somarPorData", java.time.LocalDate.class)
                .getAnnotation(Query.class);

        assertThat(visualizacoes.value()).contains("AgregadoVisualizacaoDiariaEntity");
        assertThat(cliques.value()).contains("AgregadoCliqueWhatsappDiarioEntity");
        assertThat(visualizacoes.value()).doesNotContain("EventoVisualizacaoEntity");
        assertThat(cliques.value()).doesNotContain("CliqueWhatsappEntity");
    }

    @Test
    void premiumEStoriesRespeitamVigencia() throws Exception {
        Query premium = AtivacaoBeneficioRepository.class
                .getMethod("countVigentes", java.time.OffsetDateTime.class)
                .getAnnotation(Query.class);
        Query stories = StoryAnuncioRepository.class
                .getMethod(
                        "countAtivos",
                        br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio.class,
                        java.time.OffsetDateTime.class)
                .getAnnotation(Query.class);

        assertThat(premium.value())
                .contains("ab.inicio_em <= :agora")
                .contains("ab.fim_em > :agora")
                .contains("gb.validade_inicio_em <= :agora")
                .contains("gb.validade_fim_em > :agora");
        assertThat(stories.value())
                .contains("story.inicioEm <= :agora")
                .contains("story.fimEm > :agora");
    }

    @Test
    void openApiPublicaContratoDoDashboardOperacional() throws Exception {
        String openApi = Files.readString(Path.of(
                "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

        assertThat(openApi)
                .contains("/api/admin/dashboard/hoje:")
                .contains("operationId: getAdminDashboardHoje")
                .contains("$ref: \"#/components/schemas/AdminDashboardHoje\"")
                .contains("dataReferencia:")
                .contains("fusoHorario:")
                .contains("visualizacoes:")
                .contains("cliquesWhatsapp:")
                .contains("beneficiosPremiumVigentes:");
    }

    @Test
    void openApiPublicaContratosAnaliticosCoesos() throws Exception {
        String openApi = Files.readString(Path.of(
                "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

        assertThat(openApi)
                .contains("/api/admin/dashboard/desempenho-diario:")
                .contains("/api/admin/dashboard/top-whatsapp-hoje:")
                .contains("/api/admin/dashboard/analises:")
                .contains("AdminDashboardDesempenhoDiario:")
                .contains("AdminDashboardTopWhatsappHoje:")
                .contains("AdminDashboardAnalises:")
                .contains("enum: [7, 15, 30]")
                .contains("const: 100");
    }

    @Test
    void consultasAnaliticasSaoAgregadasLimitadasESemNMaisUm() throws Exception {
        String repository = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "persistence", "repository", "admin",
                "AdminDashboardAnalyticsJdbcRepository.java"));
        String service = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "application", "admin", "dashboard",
                "AdminDashboardAnalyticsService.java"));

        assertThat(repository)
                .contains("agregado_visualizacao_diaria")
                .contains("agregado_clique_whatsapp_diario")
                .contains("e.request_id LIKE 'import:anuncio_view_log:%'")
                .contains("e.criado_em > i.snapshot_corte_em")
                .contains("c.permitido = true")
                .contains("WHERE a.status = 'PUBLICADO'")
                .contains("AND a.status_moderacao = 'APROVADO'")
                .contains("LIMIT :limite");
        assertThat(service)
                .contains("midiaRepository.findByIdIn(ids)")
                .contains("arquivoRepository.findByIdIn")
                .doesNotContain("findById(item.anuncioId())");
    }
}
