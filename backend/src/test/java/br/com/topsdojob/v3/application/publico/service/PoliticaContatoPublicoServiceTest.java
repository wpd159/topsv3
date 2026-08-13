package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PoliticaContatoPublicoServiceTest {

    private final PoliticaContatoPublicoService service = new PoliticaContatoPublicoService();

    @Test
    void contatoDisponivelParaAnuncioPublicoAtivoSemDecisaoEtaria() {
        AnuncioEntity anuncio = anuncio(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, null, "+5500000000000");

        assertThat(service.podeExporContato(anuncio)).isTrue();
        assertThat(service.whatsappUrl(anuncio))
                .isEqualTo(
                        "https://wa.me/5500000000000?text="
                                + "Ol%C3%A1%2C%20vi%20o%20an%C3%BAncio%20Anuncio%20QA%20no%20Tops%20do%20Job%20"
                                + "e%20quero%20mais%20informa%C3%A7%C3%B5es%21");
    }

    @Test
    void contatoIndependeDaGaleriaEDaConfirmacaoDeIdade() {
        AnuncioEntity anuncio = anuncio(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, null, "+5500000000000");

        assertThat(service.avaliar(anuncio).disponivel()).isTrue();
    }

    @Test
    void contatoNegadoParaAnuncioNaoPublicadoRejeitadoOuRemovido() {
        assertThat(service.podeExporContato(anuncio(StatusAnuncio.PAUSADO, StatusModeracaoAnuncio.APROVADO, null, "+5500000000000"))).isFalse();
        assertThat(service.podeExporContato(anuncio(StatusAnuncio.REJEITADO, StatusModeracaoAnuncio.REJEITADO, null, "+5500000000000"))).isFalse();
        assertThat(service.podeExporContato(anuncio(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, java.time.OffsetDateTime.now(), "+5500000000000"))).isFalse();
    }

    @Test
    void contatoInvalidoNaoEhExposto() {
        assertThat(service.whatsappUrl(anuncio(StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, null, "62999999999"))).isNull();
    }

    private AnuncioEntity anuncio(
            StatusAnuncio status,
            StatusModeracaoAnuncio moderacao,
            java.time.OffsetDateTime removidoEm,
            String whatsapp) {
        AnuncioEntity entity = org.mockito.Mockito.mock(AnuncioEntity.class);
        org.mockito.Mockito.when(entity.getId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.when(entity.getStatus()).thenReturn(status);
        org.mockito.Mockito.when(entity.getStatusModeracao()).thenReturn(moderacao);
        org.mockito.Mockito.when(entity.getRemovidoEm()).thenReturn(removidoEm);
        org.mockito.Mockito.when(entity.getWhatsappNormalizado()).thenReturn(whatsapp);
        org.mockito.Mockito.when(entity.getTitulo()).thenReturn("Anuncio QA");
        return entity;
    }
}
