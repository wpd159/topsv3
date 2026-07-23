package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnunciantePerformanceDto;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import br.com.topsdojob.v3.web.publico.anunciante.PainelAnunciantePerformanceController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PainelAnunciantePerformanceContratoTest {

    @Test
    void endpointCanonicoUsaSessaoSemReceberUsuarioNoContrato() throws Exception {
        RequestMapping mapping = PainelAnunciantePerformanceController.class.getAnnotation(RequestMapping.class);
        GetMapping get = PainelAnunciantePerformanceController.class
                .getMethod("consultar", org.springframework.security.core.Authentication.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/api/public/painel-anunciante/performance");
        assertThat(get).isNotNull();
        assertThat(PainelAnunciantePerformanceController.class.getDeclaredMethods())
                .allMatch(method -> java.util.Arrays.stream(method.getParameterTypes())
                        .noneMatch(type -> type.equals(UUID.class)));
    }

    @Test
    void cliquesSaoAgregadosNoPostgresqlPorProprietarioEPeriodo() throws Exception {
        Query porAnuncio = CliqueWhatsappRepository.class
                .getMethod("countPermitidosPorUsuario", UUID.class)
                .getAnnotation(Query.class);
        Query porDia = CliqueWhatsappRepository.class
                .getMethod(
                        "countPermitidosDiariosPorUsuario",
                        UUID.class,
                        OffsetDateTime.class,
                        OffsetDateTime.class)
                .getAnnotation(Query.class);

        assertThat(porAnuncio.nativeQuery()).isTrue();
        assertThat(porAnuncio.value())
                .contains("a.usuario_id = :usuarioId")
                .contains("c.permitido = true")
                .contains("group by c.anuncio_id")
                .doesNotContain("agregado_clique_whatsapp_diario");
        assertThat(porDia.nativeQuery()).isTrue();
        assertThat(porDia.value())
                .contains("a.usuario_id = :usuarioId")
                .contains("c.criado_em >= :inicio")
                .contains("c.criado_em < :fimExclusivo")
                .contains("at time zone 'America/Sao_Paulo'")
                .doesNotContain("agregado_clique_whatsapp_diario");
    }

    @Test
    void premiumVigenteTambemEAgregadoPorProprietario() throws Exception {
        Query query = AtivacaoBeneficioRepository.class
                .getMethod("countVigentesPorUsuario", UUID.class, OffsetDateTime.class)
                .getAnnotation(Query.class);

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("a.usuario_id = :usuarioId")
                .contains("bp.escopo = 'ANUNCIO'")
                .contains("ab.status = 'ATIVA'")
                .contains("ab.revogada_em is null")
                .contains("ab.fim_em > :agora")
                .contains("gb.status = 'ATIVO'")
                .contains("group by ab.anuncio_id");
    }

    @Test
    void dtoNaoIntroduzScoreOuRecomendacoes() {
        assertThat(PainelAnunciantePerformanceDto.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly(
                        "visualizacoes",
                        "totalCliquesWhatsapp",
                        "ctrGeral",
                        "anunciosComBeneficioPremiumVigente",
                        "comparativo",
                        "serieCliquesWhatsapp",
                        "ranking")
                .doesNotContain("score", "recomendacoes");
    }

    @Test
    void openApiDocumentaHistoricoPendenteESessaoPublica() throws Exception {
        String openApi = Files.readString(Path.of(
                "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

        assertThat(openApi)
                .contains("/api/public/painel-anunciante/performance:")
                .contains("operationId: getCurrentAdvertiserPerformance")
                .contains("$ref: \"#/components/schemas/PainelAnunciantePerformance\"")
                .contains("ctrGeral:")
                .contains("anunciosComBeneficioPremiumVigente:")
                .doesNotContain("PainelPerformanceScore");
    }
}
