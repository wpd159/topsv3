package br.com.topsdojob.v3.application.publico.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelecaoMidiasPublicasTest {
    @Test
    void oraculoExplicitoIncluiNulosSinaisDeUuidTiposEFinalidades() {
        UUID negativeHigh = new UUID(Long.MIN_VALUE, 7);
        UUID negativeLow = new UUID(1, Long.MIN_VALUE);
        UUID positive = new UUID(1, 7);
        UUID nullOrder = new UUID(0, 8);
        UUID video = new UUID(0, 9);
        UUID other = new UUID(0, 10);
        var links = new ArrayList<>(List.of(
                link(positive, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 0, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.RESTRITA_18),
                link(nullOrder, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.CAPA, null, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE),
                link(negativeLow, TipoAnuncioMidia.FOTO, null, 0, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.RESTRITA_18),
                link(video, TipoAnuncioMidia.VIDEO, FinalidadeAnuncioMidia.GALERIA, 99, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE),
                link(other, null, null, -20, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE),
                link(negativeHigh, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 0, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE),
                link(null, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 0, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE),
                link(new UUID(0, 11), TipoAnuncioMidia.STORY, FinalidadeAnuncioMidia.GALERIA, -10, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE),
                link(new UUID(0, 12), TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.STORY, -10, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE),
                link(new UUID(0, 13), TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, -10, StatusAnuncioMidia.REJEITADA, VisibilidadeMidia.LIVRE),
                link(new UUID(0, 14), TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, -10, StatusAnuncioMidia.PUBLICAVEL, null)));
        links.add(null);
        assertThat(SelecaoMidiasPublicas.selecionar(links, 10, true))
                .extracting(MidiaVinculoLeitura::id)
                .containsExactly(video, negativeHigh, negativeLow, positive, null, nullOrder, other);
        assertThat(SelecaoMidiasPublicas.selecionar(links, 4, false))
                .extracting(MidiaVinculoLeitura::id)
                .containsExactly(negativeHigh, negativeLow, positive, null, other);
        assertThat(SelecaoMidiasPublicas.selecionar(links, -1, true))
                .extracting(MidiaVinculoLeitura::id).containsExactly(video, other);
        assertThat(links).hasSize(12); // Selection never changes persisted/input order.
    }

    private MidiaVinculoLeitura link(UUID id, TipoAnuncioMidia tipo, FinalidadeAnuncioMidia finalidade,
            Integer ordem, StatusAnuncioMidia status, VisibilidadeMidia visibilidade) {
        return new MidiaVinculoLeitura(id, new UUID(0, 1), id, tipo, finalidade, ordem, status, visibilidade, null);
    }
}
