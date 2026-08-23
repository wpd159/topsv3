package br.com.topsdojob.v3.application.publico.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MidiaPublicaSeguraPolicyTest {

    private final MidiaPublicaSeguraPolicy policy = new MidiaPublicaSeguraPolicy();

    @Test
    void videoExpiradoNaoEhExpostoNoCard() {
        List<MidiaPublicaDto> resultado = policy.paraCard(
                List.of(foto(1), video(9), foto(0)),
                true,
                false);

        assertThat(resultado).extracting(MidiaPublicaDto::tipo)
                .containsExactly("FOTO", "FOTO");
    }

    @Test
    void videoAtivoVemAntesDaFotoMesmoSemCarrosselDeFotos() {
        List<MidiaPublicaDto> resultado = policy.paraCard(
                List.of(foto(1), video(9), foto(0)),
                false,
                true);

        assertThat(resultado).extracting(MidiaPublicaDto::tipo)
                .containsExactly("VIDEO", "FOTO");
        assertThat(resultado.get(0).autorizada()).isFalse();
        assertThat(resultado.get(1).ordem()).isZero();
    }

    @Test
    void videoAtivoECarrosselPreservamVideoPrimeiroETodasAsFotos() {
        List<MidiaPublicaDto> resultado = policy.paraCard(
                List.of(foto(1), video(9), foto(0)),
                true,
                true);

        assertThat(resultado).extracting(MidiaPublicaDto::tipo)
                .containsExactly("VIDEO", "FOTO", "FOTO");
        assertThat(resultado).extracting(MidiaPublicaDto::ordem)
                .containsExactly(9, 0, 1);
    }

    private MidiaPublicaDto foto(int ordem) {
        return midia("FOTO", ordem, "LIVRE", true, "/foto-" + ordem, "image/webp");
    }

    private MidiaPublicaDto video(int ordem) {
        return midia("VIDEO", ordem, "RESTRITA_18", false, null, "video/quicktime");
    }

    private MidiaPublicaDto midia(
            String tipo,
            int ordem,
            String visibilidade,
            boolean autorizada,
            String url,
            String mimeType) {
        return new MidiaPublicaDto(
                UUID.randomUUID(),
                tipo,
                "GALERIA",
                ordem,
                visibilidade,
                autorizada,
                url,
                null,
                autorizada ? null : "MIDIA_RESTRITA_IDADE",
                1080,
                1920,
                mimeType);
    }
}
