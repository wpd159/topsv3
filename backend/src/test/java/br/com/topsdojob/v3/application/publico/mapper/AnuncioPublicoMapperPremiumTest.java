package br.com.topsdojob.v3.application.publico.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnuncioPublicoMapperPremiumTest {

    private final AnuncioPublicoMapper mapper = new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy());

    @Test
    void beneficioExpiradoNaoMantemTopoCarrosselOuWhatsappNoCard() {
        var dto = mapper.toCard(
                anuncio(),
                null,
                List.of(foto(0), foto(1)),
                PremiumPublicoFlagsDto.vazio(),
                true,
                null);

        assertThat(dto.topo()).isFalse();
        assertThat(dto.midiaExtra()).isFalse();
        assertThat(dto.whatsappCard()).isFalse();
        assertThat(dto.contatoDisponivel()).isTrue();
        assertThat(dto.midias()).hasSize(1);
    }

    @Test
    void beneficioVigenteAplicaTopoCarrosselEWhatsappNoCard() {
        PremiumPublicoFlagsDto vigentes = new PremiumPublicoFlagsDto(
                true,
                true,
                true,
                false,
                true,
                false,
                false,
                true,
                false,
                true,
                List.of("Topo", "Carrossel de fotos", "WhatsApp no card"));

        var dto = mapper.toCard(
                anuncio(),
                null,
                List.of(foto(0), foto(1)),
                vigentes,
                true,
                null);

        assertThat(dto.topo()).isTrue();
        assertThat(dto.midiaExtra()).isTrue();
        assertThat(dto.whatsappCard()).isTrue();
        assertThat(dto.midias()).hasSize(2);
    }

    private AnuncioEntity anuncio() {
        AnuncioEntity anuncio = mock(AnuncioEntity.class);
        when(anuncio.getId()).thenReturn(UUID.randomUUID());
        when(anuncio.getSlug()).thenReturn("perfil-teste");
        when(anuncio.getTitulo()).thenReturn("Perfil teste");
        when(anuncio.getLocaisAtendimento()).thenReturn(Set.of());
        when(anuncio.getServicos()).thenReturn(Set.of());
        return anuncio;
    }

    private MidiaPublicaDto foto(int ordem) {
        return new MidiaPublicaDto(
                UUID.randomUUID(),
                "FOTO",
                "GALERIA",
                ordem,
                "LIVRE",
                true,
                "/foto-" + ordem,
                null,
                800,
                1200,
                "image/webp");
    }
}
