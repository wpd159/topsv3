package br.com.topsdojob.v3.application.publico.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.service.IdadeAnunciantePublicaService;
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
                List.of(video(), foto(0), foto(1)),
                PremiumPublicoFlagsDto.vazio(),
                new IdadeAnunciantePublicaService.Resultado("Perfil teste", 25, false),
                true,
                null,
                VisualizacoesCanonicasDto.total(0));

        assertThat(dto.topo()).isFalse();
        assertThat(dto.midiaExtra()).isFalse();
        assertThat(dto.whatsappCard()).isFalse();
        assertThat(dto.contatoDisponivel()).isTrue();
        assertThat(dto.midias()).hasSize(1);
        assertThat(dto.midias()).extracting(MidiaPublicaDto::tipo).containsExactly("FOTO");
        assertThat(dto.idade()).isEqualTo(25);
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
                true,
                true,
                List.of("Topo", "Carrossel de fotos", "WhatsApp no card"));

        var dto = mapper.toCard(
                anuncio(),
                null,
                List.of(foto(0), video(), foto(1)),
                vigentes,
                new IdadeAnunciantePublicaService.Resultado("Perfil teste", null, true),
                true,
                null,
                VisualizacoesCanonicasDto.total(12));

        assertThat(dto.topo()).isTrue();
        assertThat(dto.midiaExtra()).isTrue();
        assertThat(dto.whatsappCard()).isTrue();
        assertThat(dto.midias()).extracting(MidiaPublicaDto::tipo)
                .containsExactly("VIDEO", "FOTO", "FOTO");
        assertThat(dto.idade()).isNull();
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
                null,
                800,
                1200,
                "image/webp");
    }

    private MidiaPublicaDto video() {
        return new MidiaPublicaDto(
                UUID.randomUUID(),
                "VIDEO",
                "GALERIA",
                10,
                "RESTRITA_18",
                false,
                null,
                null,
                "MIDIA_RESTRITA_IDADE",
                1080,
                1920,
                "video/quicktime");
    }
}
