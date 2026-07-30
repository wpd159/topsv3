package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioCapaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAcoesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioLocalizacaoDto;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class PainelAnunciantePerformanceServiceTest {

    private static final UUID USUARIO_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ANUNCIO_A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ANUNCIO_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ANUNCIO_ALHEIO = UUID.fromString("20000000-0000-0000-0000-000000000099");
    private static final Instant AGORA = Instant.parse("2026-07-21T12:00:00Z");

    private MeusAnunciosConsultaService meusAnunciosService;
    private CliqueWhatsappRepository cliqueRepository;
    private AtivacaoBeneficioRepository ativacaoRepository;
    private Authentication authentication;
    private PainelAnunciantePerformanceService service;

    @BeforeEach
    void setUp() {
        meusAnunciosService = mock(MeusAnunciosConsultaService.class);
        cliqueRepository = mock(CliqueWhatsappRepository.class);
        ativacaoRepository = mock(AtivacaoBeneficioRepository.class);
        authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(meusAnunciosService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(cliqueRepository.countPermitidosPorUsuario(USUARIO_ID)).thenReturn(List.of());
        when(cliqueRepository.countPermitidosDiariosPorUsuario(
                USUARIO_ID,
                OffsetDateTime.parse("2026-07-08T00:00:00Z"),
                OffsetDateTime.parse("2026-07-22T00:00:00Z"))).thenReturn(List.of());
        when(ativacaoRepository.countVigentesPorUsuario(
                USUARIO_ID,
                OffsetDateTime.parse("2026-07-21T12:00:00Z"))).thenReturn(List.of());
        service = new PainelAnunciantePerformanceService(
                meusAnunciosService,
                cliqueRepository,
                ativacaoRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void historicoPendenteNaoFabricaTotalNemCtrEIgnoraResultadosAlheios() {
        when(meusAnunciosService.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(
                anuncio(ANUNCIO_A, "legado", VisualizacoesCanonicasDto.historicoPendente())));
        when(cliqueRepository.countPermitidosPorUsuario(USUARIO_ID)).thenReturn(List.of(
                clique(ANUNCIO_A, 3),
                clique(ANUNCIO_ALHEIO, 90)));
        when(ativacaoRepository.countVigentesPorUsuario(
                USUARIO_ID,
                OffsetDateTime.parse("2026-07-21T12:00:00Z"))).thenReturn(List.of(
                        premium(ANUNCIO_A, 2),
                        premium(ANUNCIO_ALHEIO, 5)));

        var resposta = service.consultar(authentication);

        assertThat(resposta.visualizacoes()).isEqualTo(VisualizacoesCanonicasDto.historicoPendente());
        assertThat(resposta.totalCliquesWhatsapp()).isEqualTo(3);
        assertThat(resposta.ctrGeral()).isNull();
        assertThat(resposta.anunciosComBeneficioPremiumVigente()).isEqualTo(1);
        assertThat(resposta.ranking()).singleElement().satisfies(item -> {
            assertThat(item.anuncioId()).isEqualTo(ANUNCIO_A);
            assertThat(item.visualizacoes().situacao())
                    .isEqualTo(VisualizacoesCanonicasDto.Situacao.HISTORICO_PENDENTE);
            assertThat(item.ctr()).isNull();
            assertThat(item.beneficiosPremiumVigentes()).isEqualTo(2);
        });
        verify(meusAnunciosService).usuarioAutenticado(authentication);
        verify(meusAnunciosService).listarDoUsuario(USUARIO_ID);
        verify(cliqueRepository).countPermitidosPorUsuario(USUARIO_ID);
        verify(ativacaoRepository).countVigentesPorUsuario(
                USUARIO_ID,
                OffsetDateTime.parse("2026-07-21T12:00:00Z"));
    }

    @Test
    void zeroLegitimoPermaneceZeroReal() {
        when(meusAnunciosService.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(
                anuncio(ANUNCIO_A, "nativo", VisualizacoesCanonicasDto.total(0))));

        var resposta = service.consultar(authentication);

        assertThat(resposta.visualizacoes().situacao())
                .isEqualTo(VisualizacoesCanonicasDto.Situacao.ZERO_LEGITIMO);
        assertThat(resposta.visualizacoes().total()).isZero();
        assertThat(resposta.ctrGeral()).isEqualByComparingTo("0.00");
        assertThat(resposta.ranking().get(0).ctr()).isEqualByComparingTo("0.00");
    }

    @Test
    void totalDisponivelCalculaCtrComDuasCasas() {
        when(meusAnunciosService.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(
                anuncio(ANUNCIO_A, "publicado", VisualizacoesCanonicasDto.total(8))));
        when(cliqueRepository.countPermitidosPorUsuario(USUARIO_ID)).thenReturn(List.of(
                clique(ANUNCIO_A, 3)));

        var resposta = service.consultar(authentication);

        assertThat(resposta.visualizacoes().total()).isEqualTo(8);
        assertThat(resposta.visualizacoes().situacao())
                .isEqualTo(VisualizacoesCanonicasDto.Situacao.DISPONIVEL);
        assertThat(resposta.totalCliquesWhatsapp()).isEqualTo(3);
        assertThat(resposta.ctrGeral()).isEqualByComparingTo("37.50");
        assertThat(resposta.ranking().get(0).ctr()).isEqualByComparingTo("37.50");
    }

    @Test
    void seriePossuiQuatorzeDiasEComparativoUsaJanelasAuditadas() {
        when(meusAnunciosService.listarDoUsuario(USUARIO_ID)).thenReturn(List.of());
        when(cliqueRepository.countPermitidosDiariosPorUsuario(
                USUARIO_ID,
                OffsetDateTime.parse("2026-07-08T00:00:00Z"),
                OffsetDateTime.parse("2026-07-22T00:00:00Z"))).thenReturn(List.of(
                        dia(LocalDate.parse("2026-07-08"), 7),
                        dia(LocalDate.parse("2026-07-14"), 2),
                        dia(LocalDate.parse("2026-07-15"), 3),
                        dia(LocalDate.parse("2026-07-21"), 5)));

        var resposta = service.consultar(authentication);

        assertThat(resposta.serieCliquesWhatsapp()).hasSize(14);
        assertThat(resposta.serieCliquesWhatsapp().get(0).data()).isEqualTo("2026-07-08");
        assertThat(resposta.serieCliquesWhatsapp().get(13).data()).isEqualTo("2026-07-21");
        assertThat(resposta.comparativo().cliquesPeriodoAnterior()).isEqualTo(9);
        assertThat(resposta.comparativo().cliquesPeriodoAtual()).isEqualTo(8);
        assertThat(resposta.comparativo().variacaoPercentual()).isEqualByComparingTo("-11.11");
        verify(cliqueRepository).countPermitidosDiariosPorUsuario(
                USUARIO_ID,
                OffsetDateTime.parse("2026-07-08T00:00:00Z"),
                OffsetDateTime.parse("2026-07-22T00:00:00Z"));
    }

    @Test
    void variacaoPreservaCasosDeZeroAuditados() {
        assertThat(PainelAnunciantePerformanceService.variacao(0, 0)).isEqualByComparingTo("0.00");
        assertThat(PainelAnunciantePerformanceService.variacao(4, 0)).isEqualByComparingTo("100.00");
        assertThat(PainelAnunciantePerformanceService.variacao(3, 6)).isEqualByComparingTo("-50.00");
    }

    @Test
    void rankingUsaCliquesDepoisViewsSemScoreEContaPremiumPorAnuncio() {
        when(meusAnunciosService.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(
                anuncio(ANUNCIO_A, "primeiro", VisualizacoesCanonicasDto.total(20)),
                anuncio(ANUNCIO_B, "segundo", VisualizacoesCanonicasDto.total(10))));
        when(cliqueRepository.countPermitidosPorUsuario(USUARIO_ID)).thenReturn(List.of(
                clique(ANUNCIO_A, 2),
                clique(ANUNCIO_B, 5)));
        when(ativacaoRepository.countVigentesPorUsuario(
                USUARIO_ID,
                OffsetDateTime.parse("2026-07-21T12:00:00Z"))).thenReturn(List.of(
                        premium(ANUNCIO_A, 1),
                        premium(ANUNCIO_B, 3)));

        var resposta = service.consultar(authentication);

        assertThat(resposta.ranking()).extracting(item -> item.anuncioId())
                .containsExactly(ANUNCIO_B, ANUNCIO_A);
        assertThat(resposta.anunciosComBeneficioPremiumVigente()).isEqualTo(2);
        assertThat(resposta.ranking().get(0).beneficiosPremiumVigentes()).isEqualTo(3);
    }

    private MeuAnuncioDto anuncio(UUID id, String slug, VisualizacoesCanonicasDto visualizacoes) {
        return new MeuAnuncioDto(
                id,
                slug,
                "Anuncio " + slug,
                "Descricao segura",
                "MASSAGENS",
                new BigDecimal("100.00"),
                null,
                null,
                List.of(),
                List.of(),
                false,
                "PUBLICADO",
                "APROVADO",
                new MeuAnuncioLocalizacaoDto("GO", "Goiania", "goiania", "Centro", "centro"),
                new MeuAnuncioCapaDto("https://midias.example/hml/capa.jpg", false),
                List.of(),
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                new MeuAnuncioAcoesDto(true, false, true, false),
                visualizacoes,
                null);
    }

    private CliqueWhatsappRepository.ContagemPorAnuncioProjection clique(UUID anuncioId, long total) {
        return new CliqueWhatsappRepository.ContagemPorAnuncioProjection() {
            @Override
            public UUID getAnuncioId() {
                return anuncioId;
            }

            @Override
            public long getTotalCliques() {
                return total;
            }
        };
    }

    private CliqueWhatsappRepository.ContagemDiariaProjection dia(LocalDate data, long total) {
        return new CliqueWhatsappRepository.ContagemDiariaProjection() {
            @Override
            public LocalDate getDataReferencia() {
                return data;
            }

            @Override
            public long getTotalCliques() {
                return total;
            }
        };
    }

    private AtivacaoBeneficioRepository.ContagemPremiumVigenteProjection premium(
            UUID anuncioId,
            long total) {
        return new AtivacaoBeneficioRepository.ContagemPremiumVigenteProjection() {
            @Override
            public UUID getAnuncioId() {
                return anuncioId;
            }

            @Override
            public long getTotalBeneficios() {
                return total;
            }
        };
    }
}
