package br.com.topsdojob.v3.application.admin.premium;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.ANUNCIO_TOPO;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.CARROSSEL_FOTOS;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.OCULTAR_IDADE;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.VIDEO_1;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.WHATSAPP_CARD;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoGrupoAtivacaoBeneficio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PremiumExpiracaoPolicyServiceTest {

    private final PremiumExpiracaoPolicyService service = new PremiumExpiracaoPolicyService();
    private final OffsetDateTime agora = OffsetDateTime.parse("2026-07-15T12:00:00Z");

    @Test
    void beneficioVigenteAntesDaExpiracaoProduzEfeito() {
        Cenario cenario = cenario(
                ANUNCIO_TOPO,
                StatusAtivacaoBeneficio.ATIVA,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(1),
                agora.plusDays(20),
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);

        PremiumBeneficioCalculado resultado = avaliar(cenario, agora);

        assertThat(resultado.status()).isEqualTo(PremiumBeneficioStatusCalculado.ATIVO);
        assertThat(resultado.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_ATIVO);
        assertThat(resultado.inconsistente()).isFalse();
    }

    @Test
    void instanteExatoDaExpiracaoFalhaFechado() {
        Cenario cenario = cenario(
                OCULTAR_IDADE,
                StatusAtivacaoBeneficio.ATIVA,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(30),
                agora,
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);

        PremiumBeneficioCalculado resultado = avaliar(cenario, agora);

        assertThat(resultado.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(resultado.status()).isNotIn(
                PremiumBeneficioStatusCalculado.ATIVO,
                PremiumBeneficioStatusCalculado.VENCENDO);
    }

    @Test
    void beneficioJaExpiradoPermaneceHistoricoSemEfeito() {
        Cenario cenario = cenario(
                FOTOS_EXTRA_5,
                StatusAtivacaoBeneficio.EXPIRADA,
                StatusGrupoAtivacaoBeneficio.EXPIRADO,
                agora.minusDays(30),
                agora.minusSeconds(1),
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);

        PremiumBeneficioCalculado resultado = avaliar(cenario, agora);

        assertThat(resultado.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(resultado.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_EXPIRADO);
    }

    @Test
    void fotosExtrasAguardandoModeracaoNaoIniciamPrazoNemProduzemEfeito() {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        BeneficioPremiumEntity beneficio = beneficio(FOTOS_EXTRA_5);
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
                UUID.randomUUID(),
                beneficio.getId(),
                UUID.randomUUID(),
                usuarioId,
                anuncioId,
                grupoId,
                10,
                "premium-aguardando-" + UUID.randomUUID(),
                agora.minusDays(2));
        GrupoAtivacaoBeneficioEntity grupo = grupo(
                grupoId,
                usuarioId,
                anuncioId,
                OrigemBeneficio.CREDITO,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(2),
                agora.plusDays(5));

        PremiumBeneficioCalculado resultado = service.avaliar(ativacao, beneficio, grupo, agora);

        assertThat(resultado.status()).isEqualTo(PremiumBeneficioStatusCalculado.PENDENTE);
        assertThat(resultado.codigos())
                .contains(PremiumConsistenciaCodigo.BENEFICIO_AGUARDANDO_MODERACAO);
        assertThat(resultado.inconsistente()).isFalse();
        assertThat(ativacao.getInicioEm()).isNull();
        assertThat(ativacao.getFimEm()).isNull();
    }

    @Test
    void beneficioCanceladoAntesDaExpiracaoNaoProduzEfeito() {
        Cenario cenario = cenario(
                WHATSAPP_CARD,
                StatusAtivacaoBeneficio.CANCELADA,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(1),
                agora.plusDays(6),
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);

        PremiumBeneficioCalculado resultado = avaliar(cenario, agora);

        assertThat(resultado.status()).isEqualTo(PremiumBeneficioStatusCalculado.INATIVO);
        assertThat(resultado.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_CANCELADO_OU_REVOGADO);
    }

    @Test
    void origemNaoComprovadaFalhaFechado() {
        Cenario cenario = cenario(
                CARROSSEL_FOTOS,
                StatusAtivacaoBeneficio.ATIVA,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(1),
                agora.plusDays(6),
                null,
                null);

        PremiumBeneficioCalculado resultado = avaliar(cenario, agora);

        assertThat(resultado.status()).isEqualTo(PremiumBeneficioStatusCalculado.INCONSISTENTE);
        assertThat(resultado.codigos()).contains(
                PremiumConsistenciaCodigo.ORIGEM_DESCONHECIDA,
                PremiumConsistenciaCodigo.ORIGEM_GRUPO_DIVERGENTE);
    }

    @Test
    void leiturasRepetidasDepoisDaExpiracaoNaoReativamBeneficio() {
        Cenario cenario = cenario(
                VIDEO_1,
                StatusAtivacaoBeneficio.ATIVA,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(7),
                agora,
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);

        PremiumBeneficioCalculado primeira = avaliar(cenario, agora.plusNanos(1));
        PremiumBeneficioCalculado segunda = avaliar(cenario, agora.plusHours(1));

        assertThat(primeira.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(segunda.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
    }

    @Test
    void apenasUmDosVariosBeneficiosPodeExpirar() {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        GrupoAtivacaoBeneficioEntity grupo = grupo(
                grupoId,
                usuarioId,
                anuncioId,
                OrigemBeneficio.CREDITO,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(7),
                agora.plusDays(7));
        BeneficioPremiumEntity expirado = beneficio(FOTOS_EXTRA_5);
        BeneficioPremiumEntity vigente = beneficio(WHATSAPP_CARD);

        PremiumBeneficioCalculado primeiro = service.avaliar(
                ativacao(expirado.getId(), grupoId, usuarioId, anuncioId, OrigemBeneficio.CREDITO,
                        StatusAtivacaoBeneficio.ATIVA, agora.minusDays(7), agora),
                expirado,
                grupo,
                agora);
        PremiumBeneficioCalculado segundo = service.avaliar(
                ativacao(vigente.getId(), grupoId, usuarioId, anuncioId, OrigemBeneficio.CREDITO,
                        StatusAtivacaoBeneficio.ATIVA, agora.minusDays(7), agora.plusDays(7)),
                vigente,
                grupo,
                agora);

        assertThat(primeiro.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(segundo.status()).isIn(
                PremiumBeneficioStatusCalculado.ATIVO,
                PremiumBeneficioStatusCalculado.VENCENDO);
    }

    @Test
    void beneficiosDoMesmoGrupoEPeriodoExpiramJuntos() {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        GrupoAtivacaoBeneficioEntity grupo = grupo(
                grupoId,
                usuarioId,
                anuncioId,
                OrigemBeneficio.CREDITO,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(14),
                agora);
        BeneficioPremiumEntity topo = beneficio(ANUNCIO_TOPO);
        BeneficioPremiumEntity carrossel = beneficio(CARROSSEL_FOTOS);

        PremiumBeneficioCalculado primeiro = service.avaliar(
                ativacao(topo.getId(), grupoId, usuarioId, anuncioId, OrigemBeneficio.CREDITO,
                        StatusAtivacaoBeneficio.ATIVA, agora.minusDays(14), agora),
                topo,
                grupo,
                agora);
        PremiumBeneficioCalculado segundo = service.avaliar(
                ativacao(carrossel.getId(), grupoId, usuarioId, anuncioId, OrigemBeneficio.CREDITO,
                        StatusAtivacaoBeneficio.ATIVA, agora.minusDays(14), agora),
                carrossel,
                grupo,
                agora);

        assertThat(primeiro.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(segundo.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
    }

    @Test
    void expiracaoDeUmBeneficioNaoRemoveOutroLegitimo() {
        Cenario expirado = cenario(
                OCULTAR_IDADE,
                StatusAtivacaoBeneficio.EXPIRADA,
                StatusGrupoAtivacaoBeneficio.EXPIRADO,
                agora.minusDays(30),
                agora.minusDays(1),
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);
        Cenario vigente = cenario(
                VIDEO_1,
                StatusAtivacaoBeneficio.ATIVA,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(1),
                agora.plusDays(6),
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);

        assertThat(avaliar(expirado, agora).status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(avaliar(vigente, agora).status()).isIn(
                PremiumBeneficioStatusCalculado.ATIVO,
                PremiumBeneficioStatusCalculado.VENCENDO);
    }

    @Test
    void catalogoInativoNuncaConcedeEfeito() {
        Cenario cenario = cenario(
                ANUNCIO_TOPO,
                StatusAtivacaoBeneficio.ATIVA,
                StatusGrupoAtivacaoBeneficio.ATIVO,
                agora.minusDays(1),
                agora.plusDays(6),
                OrigemBeneficio.CREDITO,
                OrigemBeneficio.CREDITO);
        cenario.beneficio().atualizarCatalogo(
                ANUNCIO_TOPO,
                ANUNCIO_TOPO,
                false,
                0,
                agora);

        PremiumBeneficioCalculado resultado = avaliar(cenario, agora);

        assertThat(resultado.status()).isEqualTo(PremiumBeneficioStatusCalculado.INATIVO);
        assertThat(resultado.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_CATALOGO_INATIVO);
    }

    private PremiumBeneficioCalculado avaliar(Cenario cenario, OffsetDateTime referencia) {
        return service.avaliar(cenario.ativacao(), cenario.beneficio(), cenario.grupo(), referencia);
    }

    private Cenario cenario(
            String codigo,
            StatusAtivacaoBeneficio statusAtivacao,
            StatusGrupoAtivacaoBeneficio statusGrupo,
            OffsetDateTime inicio,
            OffsetDateTime fim,
            OrigemBeneficio origemAtivacao,
            OrigemBeneficio origemGrupo) {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UUID grupoId = UUID.randomUUID();
        BeneficioPremiumEntity beneficio = beneficio(codigo);
        return new Cenario(
                ativacao(
                        beneficio.getId(),
                        grupoId,
                        usuarioId,
                        anuncioId,
                        origemAtivacao,
                        statusAtivacao,
                        inicio,
                        fim),
                beneficio,
                grupo(
                        grupoId,
                        usuarioId,
                        anuncioId,
                        origemGrupo,
                        statusGrupo,
                        inicio,
                        fim));
    }

    private AtivacaoBeneficioEntity ativacao(
            UUID beneficioId,
            UUID grupoId,
            UUID usuarioId,
            UUID anuncioId,
            OrigemBeneficio origem,
            StatusAtivacaoBeneficio status,
            OffsetDateTime inicio,
            OffsetDateTime fim) {
        return AtivacaoBeneficioEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                beneficioId,
                usuarioId,
                anuncioId,
                grupoId,
                origem,
                inicio,
                fim,
                status,
                origem == OrigemBeneficio.CREDITO ? 10 : 0,
                origem == OrigemBeneficio.COMPRA ? BigDecimal.TEN : null,
                "premium-expiracao-" + UUID.randomUUID(),
                inicio);
    }

    private GrupoAtivacaoBeneficioEntity grupo(
            UUID id,
            UUID usuarioId,
            UUID anuncioId,
            OrigemBeneficio origem,
            StatusGrupoAtivacaoBeneficio status,
            OffsetDateTime inicio,
            OffsetDateTime fim) {
        return GrupoAtivacaoBeneficioEntity.criarFixtureHomologacao(
                id,
                TipoGrupoAtivacaoBeneficio.IMPORTACAO,
                origem,
                usuarioId,
                anuncioId,
                inicio,
                fim,
                status,
                "premium-grupo-" + id,
                inicio);
    }

    private BeneficioPremiumEntity beneficio(String codigo) {
        return BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                codigo,
                codigo,
                codigo,
                EscopoBeneficioPremium.ANUNCIO,
                ANUNCIO_TOPO.equals(codigo),
                true,
                agora.minusYears(1));
    }

    private record Cenario(
            AtivacaoBeneficioEntity ativacao,
            BeneficioPremiumEntity beneficio,
            GrupoAtivacaoBeneficioEntity grupo) {
    }
}
