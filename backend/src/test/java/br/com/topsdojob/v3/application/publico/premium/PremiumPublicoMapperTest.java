package br.com.topsdojob.v3.application.publico.premium;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.ANUNCIO_TOPO;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.CARROSSEL_FOTOS;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.OCULTAR_IDADE;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.VIDEO_1;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.WHATSAPP_CARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PremiumPublicoMapperTest {

    private final BeneficioAnuncioConsultaService beneficioService = mock(BeneficioAnuncioConsultaService.class);
    private final PremiumPublicoMapper mapper = new PremiumPublicoMapper(beneficioService);

    @Test
    void dtoPublicoExpoeSomenteFlagsCanonicasSanitizadas() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado(ANUNCIO_TOPO, PremiumBeneficioStatusCalculado.ATIVO),
                calculado("RELATORIO", PremiumBeneficioStatusCalculado.ATIVO),
                calculado(FOTOS_EXTRA_5, PremiumBeneficioStatusCalculado.VENCENDO),
                calculado(CARROSSEL_FOTOS, PremiumBeneficioStatusCalculado.ATIVO),
                calculado(VIDEO_1, PremiumBeneficioStatusCalculado.ATIVO),
                calculado(WHATSAPP_CARD, PremiumBeneficioStatusCalculado.ATIVO)));

        PremiumPublicoFlagsDto flags = mapper.flags(anuncio);

        assertThat(flags.premiumAtivo()).isTrue();
        assertThat(flags.destaqueAtivo()).isTrue();
        assertThat(flags.topoAtivo()).isTrue();
        assertThat(flags.possuiMidiaExtra()).isTrue();
        assertThat(flags.fotosExtrasAtivo()).isTrue();
        assertThat(flags.carrosselFotosAtivo()).isTrue();
        assertThat(flags.videoAtivo()).isTrue();
        assertThat(flags.whatsappCardAtivo()).isTrue();
        assertThat(flags.possuiStories()).isFalse();
        assertThat(flags.beneficiosPublicos()).containsExactly(
                "Topo",
                "Fotos extras",
                "Carrossel de fotos",
                "Video",
                "WhatsApp no card");
        assertThat(flags.toString())
                .doesNotContain("RELATORIO")
                .doesNotContain("valor")
                .doesNotContain("saldo")
                .doesNotContain("credito")
                .doesNotContain("grupo");
    }

    @Test
    void beneficiosExpiradosNaoDeixamEfeitoPublicoParcial() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado(ANUNCIO_TOPO, PremiumBeneficioStatusCalculado.EXPIRADO),
                calculado(FOTOS_EXTRA_5, PremiumBeneficioStatusCalculado.EXPIRADO),
                calculado(CARROSSEL_FOTOS, PremiumBeneficioStatusCalculado.EXPIRADO),
                calculado(VIDEO_1, PremiumBeneficioStatusCalculado.EXPIRADO),
                calculado(WHATSAPP_CARD, PremiumBeneficioStatusCalculado.EXPIRADO),
                calculado(OCULTAR_IDADE, PremiumBeneficioStatusCalculado.EXPIRADO)));

        PremiumPublicoFlagsDto flags = mapper.flags(anuncio);

        assertThat(flags).isEqualTo(PremiumPublicoFlagsDto.vazio());
    }

    @Test
    void idsDeAtivacoesComEfeitoPublicoExcluemInconsistentesEExpiradas() {
        UUID anuncioId = UUID.randomUUID();
        PremiumBeneficioCalculado ativo = calculado(ANUNCIO_TOPO,
                PremiumBeneficioStatusCalculado.ATIVO);
        PremiumBeneficioCalculado inconsistente = calculado(FOTOS_EXTRA_5,
                PremiumBeneficioStatusCalculado.INCONSISTENTE);
        PremiumBeneficioCalculado expirado = calculado(VIDEO_1,
                PremiumBeneficioStatusCalculado.EXPIRADO);
        when(beneficioService.consultarCalculados(anuncioId)).thenReturn(
                List.of(ativo, inconsistente, expirado));

        assertThat(mapper.idsAtivacoesComEfeitoPublico(anuncioId))
                .containsExactly(ativo.ativacao().getId());
    }

    @Test
    void leituraPorIdsPreservaBeneficiosERecalculaSemCache() {
        AnuncioEntity anuncio = anuncio();
        var ids = List.of(anuncio.getId());
        when(beneficioService.consultarCalculadosPorAnuncio(ids))
                .thenReturn(Map.of(anuncio.getId(), List.of(
                        calculado(FOTOS_EXTRA_5, PremiumBeneficioStatusCalculado.VENCENDO),
                        calculado(VIDEO_1, PremiumBeneficioStatusCalculado.EXPIRADO))));
        var flags = mapper.flagsPorAnuncioIds(ids).get(anuncio.getId());
        assertThat(flags.fotosExtrasAtivo()).isTrue();
        assertThat(flags.videoAtivo()).isFalse();
        assertThat(flags.beneficiosPublicos()).containsExactly("Fotos extras");
        assertThat(mapper.flagsPorAnuncios(List.of(anuncio)).get(anuncio.getId())).isEqualTo(flags);
        when(beneficioService.consultarCalculadosPorAnuncio(ids)).thenReturn(Map.of());
        assertThat(mapper.flagsPorAnuncioIds(ids)).containsEntry(anuncio.getId(), PremiumPublicoFlagsDto.vazio());
        assertThat(mapper.flagsPorAnuncioIds(List.of())).isEmpty();
    }

    @Test
    void preSelecaoEscalarDeMidiaNaoDispensaAPoliticaNemHidrataAnuncio() {
        UUID comMidia = UUID.randomUUID(), semMidia = UUID.randomUUID();
        var repository = mock(br.com.topsdojob.v3.persistence.repository.AnuncioRepository.class);
        var focal = new PremiumPublicoMapper(beneficioService, repository);
        when(repository.findIdsComBeneficiosDeMidia(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(comMidia));
        when(beneficioService.consultarCalculadosPorAnuncio(List.of(comMidia)))
                .thenReturn(Map.of(comMidia, List.of(calculado(FOTOS_EXTRA_5, PremiumBeneficioStatusCalculado.INCONSISTENTE))));
        assertThat(focal.flagsMidiaPorAnuncioIds(List.of(comMidia, semMidia)))
                .containsOnlyKeys(comMidia).containsEntry(comMidia, PremiumPublicoFlagsDto.vazio());
        org.mockito.Mockito.verify(beneficioService).consultarCalculadosPorAnuncio(List.of(comMidia));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ocultarIdadeAtivoOcultaIndependentementeDaOrigemCanonica() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado(OCULTAR_IDADE, PremiumBeneficioStatusCalculado.ATIVO, OrigemBeneficio.ADMIN)));

        PremiumPublicoFlagsDto flags = mapper.flags(anuncio);

        assertThat(flags.idadeOculta()).isTrue();
        assertThat(flags.beneficiosPublicos()).isEmpty();
    }

    @Test
    void ocultarIdadeExpiradoNaoOcultaIdade() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado(OCULTAR_IDADE, PremiumBeneficioStatusCalculado.EXPIRADO, OrigemBeneficio.COMPRA)));

        assertThat(mapper.flags(anuncio).idadeOculta()).isFalse();
    }

    @Test
    void storiesOcultamIdadePorContaSomenteComAtivacaoVigente() {
        UUID usuarioAtivo = UUID.randomUUID();
        UUID usuarioVencendo = UUID.randomUUID();
        UUID usuarioExpirado = UUID.randomUUID();
        UUID usuarioRevogado = UUID.randomUUID();
        when(beneficioService.consultarCalculadosPorUsuario(
                Set.of(usuarioAtivo, usuarioVencendo, usuarioExpirado, usuarioRevogado)))
                .thenReturn(Map.of(
                        usuarioAtivo, List.of(calculado(
                                OCULTAR_IDADE, PremiumBeneficioStatusCalculado.ATIVO)),
                        usuarioVencendo, List.of(calculado(
                                OCULTAR_IDADE, PremiumBeneficioStatusCalculado.VENCENDO)),
                        usuarioExpirado, List.of(calculado(
                                OCULTAR_IDADE, PremiumBeneficioStatusCalculado.EXPIRADO)),
                        usuarioRevogado, List.of(calculado(
                                OCULTAR_IDADE, PremiumBeneficioStatusCalculado.INATIVO))));

        assertThat(mapper.usuariosComIdadeOcultaNosStories(
                Set.of(usuarioAtivo, usuarioVencendo, usuarioExpirado, usuarioRevogado)))
                .containsExactlyInAnyOrder(usuarioAtivo, usuarioVencendo);
    }

    private PremiumBeneficioCalculado calculado(String codigo, PremiumBeneficioStatusCalculado status) {
        return calculado(codigo, status, OrigemBeneficio.COMPRA);
    }

    private PremiumBeneficioCalculado calculado(
            String codigo,
            PremiumBeneficioStatusCalculado status,
            OrigemBeneficio origem) {
        return new PremiumBeneficioCalculado(
                ativacao(origem),
                beneficio(codigo),
                null,
                status,
                List.of(),
                status == PremiumBeneficioStatusCalculado.VENCENDO,
                false);
    }

    private AnuncioEntity anuncio() {
        AnuncioEntity entity = instantiate(AnuncioEntity.class);
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private AtivacaoBeneficioEntity ativacao(OrigemBeneficio origem) {
        AtivacaoBeneficioEntity entity = instantiate(AtivacaoBeneficioEntity.class);
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "origem", origem);
        ReflectionTestUtils.setField(entity, "precoSnapshot", origem == OrigemBeneficio.COMPRA ? BigDecimal.TEN : null);
        ReflectionTestUtils.setField(entity, "custoCreditosSnapshot", origem == OrigemBeneficio.CREDITO ? 10 : 0);
        return entity;
    }

    private BeneficioPremiumEntity beneficio(String codigo) {
        BeneficioPremiumEntity entity = instantiate(BeneficioPremiumEntity.class);
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "codigo", codigo);
        ReflectionTestUtils.setField(entity, "nome", codigo);
        return entity;
    }

    private <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("falha ao instanciar entidade de teste", exception);
        }
    }
}
