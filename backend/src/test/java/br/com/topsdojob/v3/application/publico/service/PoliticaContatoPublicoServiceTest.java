package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PoliticaContatoPublicoServiceTest {

    private final PoliticaContatoPublicoService service = new PoliticaContatoPublicoService();

    @Test
    void liberaWhatsappSomenteParaAnuncioLivrePublicadoAprovadoNaoRemovidoComContatoValido() {
        AnuncioEntity anuncio = anuncio(
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                ClassificacaoConteudo.LIVRE,
                null,
                "+5500000000000");

        assertThat(service.podeExporContato(anuncio)).isTrue();
        assertThat(service.whatsappUrl(anuncio)).isEqualTo("https://wa.me/5500000000000");
    }

    @Test
    void bloqueiaWhatsappParaClassificacaoBloqueadaOuAnuncioRemovido() {
        AnuncioEntity bloqueado = anuncio(
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                ClassificacaoConteudo.BLOQUEADO,
                null,
                "+5500000000000");
        AnuncioEntity removido = anuncio(
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                ClassificacaoConteudo.LIVRE,
                OffsetDateTime.now(),
                "+5500000000000");

        assertThat(service.podeExporContato(bloqueado)).isFalse();
        assertThat(service.podeExporContato(removido)).isFalse();
        assertThat(service.whatsappUrl(bloqueado)).isNull();
        assertThat(service.whatsappUrl(removido)).isNull();
    }

    @Test
    void liberaWhatsappBloqueadoSomenteComIdadeConfirmada() {
        AnuncioEntity bloqueado = anuncio(
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                ClassificacaoConteudo.BLOQUEADO,
                null,
                "+5500000000000");

        assertThat(service.podeExporContato(bloqueado, false)).isFalse();
        assertThat(service.podeExporContato(bloqueado, true)).isTrue();
        assertThat(service.whatsappUrl(bloqueado, true)).isEqualTo("https://wa.me/5500000000000");
    }

    private AnuncioEntity anuncio(
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            ClassificacaoConteudo classificacao,
            OffsetDateTime removidoEm,
            String whatsapp) {
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "status", status);
        set(anuncio, "statusModeracao", statusModeracao);
        set(anuncio, "classificacaoConteudo", classificacao);
        set(anuncio, "removidoEm", removidoEm);
        set(anuncio, "whatsappNormalizado", whatsapp);
        return anuncio;
    }
}
