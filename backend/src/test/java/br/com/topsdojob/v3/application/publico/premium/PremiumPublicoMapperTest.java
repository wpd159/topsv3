package br.com.topsdojob.v3.application.publico.premium;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PremiumPublicoMapperTest {

    private final BeneficioAnuncioConsultaService beneficioService = mock(BeneficioAnuncioConsultaService.class);
    private final PremiumPublicoMapper mapper = new PremiumPublicoMapper(beneficioService);

    @Test
    void dtoPublicoExpoeApenasFlagsSanitizadas() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado("DESTAQUE", PremiumBeneficioStatusCalculado.ATIVO),
                calculado("RELATORIO", PremiumBeneficioStatusCalculado.ATIVO),
                calculado("FOTOS_EXTRA", PremiumBeneficioStatusCalculado.VENCENDO)));

        PremiumPublicoFlagsDto flags = mapper.flags(anuncio);

        assertThat(flags.premiumAtivo()).isTrue();
        assertThat(flags.destaqueAtivo()).isTrue();
        assertThat(flags.possuiMidiaExtra()).isTrue();
        assertThat(flags.beneficiosPublicos()).containsExactly("Destaque", "Mídia extra");
        assertThat(flags.toString())
                .doesNotContain("RELATORIO")
                .doesNotContain("valor")
                .doesNotContain("saldo")
                .doesNotContain("credito")
                .doesNotContain("grupo");
    }

    @Test
    void beneficioExpiradoNaoAparecePublicamente() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado("DESTAQUE", PremiumBeneficioStatusCalculado.EXPIRADO)));

        PremiumPublicoFlagsDto flags = mapper.flags(anuncio);

        assertThat(flags.premiumAtivo()).isFalse();
        assertThat(flags.beneficiosPublicos()).isEmpty();
        assertThat(flags.idadeOculta()).isFalse();
    }

    @Test
    void somenteOcultarIdadePagoAtivoOcultaIdade() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado("OCULTAR_IDADE", PremiumBeneficioStatusCalculado.ATIVO, OrigemBeneficio.COMPRA),
                calculado("OCULTAR_IDADE", PremiumBeneficioStatusCalculado.EXPIRADO, OrigemBeneficio.COMPRA),
                calculado("OCULTAR_IDADE", PremiumBeneficioStatusCalculado.ATIVO, OrigemBeneficio.CORTESIA)));

        PremiumPublicoFlagsDto flags = mapper.flags(anuncio);

        assertThat(flags.idadeOculta()).isTrue();
        assertThat(flags.beneficiosPublicos()).isEmpty();
    }

    @Test
    void ocultarIdadeExpiradoOuNaoPagoNaoOcultaIdade() {
        AnuncioEntity anuncio = anuncio();
        when(beneficioService.consultarCalculados(anuncio.getId())).thenReturn(List.of(
                calculado("OCULTAR_IDADE", PremiumBeneficioStatusCalculado.EXPIRADO, OrigemBeneficio.COMPRA),
                calculado("OCULTAR_IDADE", PremiumBeneficioStatusCalculado.ATIVO, OrigemBeneficio.ADMIN)));

        assertThat(mapper.flags(anuncio).idadeOculta()).isFalse();
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
