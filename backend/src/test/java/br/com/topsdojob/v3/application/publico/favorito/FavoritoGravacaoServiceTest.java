package br.com.topsdojob.v3.application.publico.favorito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.anuncio.FavoritoAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.FavoritoAnuncioRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FavoritoGravacaoServiceTest {

    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000001001");
    private static final UUID ANUNCIO_ID = UUID.fromString("00000000-0000-4000-8000-000000001011");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 7, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    private final FavoritoAnuncioRepository repository = mock(FavoritoAnuncioRepository.class);
    private final FavoritoGravacaoService service = new FavoritoGravacaoService(repository);

    @Test
    void inclusaoRepetidaNaoCriaOutraLinha() {
        when(repository.existsByUsuarioIdAndAnuncioId(USUARIO_ID, ANUNCIO_ID)).thenReturn(true);

        service.incluirSeAusente(USUARIO_ID, ANUNCIO_ID, AGORA);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void inclusaoNovaPersisteSomenteUsuarioEAnuncioInformados() {
        when(repository.existsByUsuarioIdAndAnuncioId(USUARIO_ID, ANUNCIO_ID)).thenReturn(false);

        service.incluirSeAusente(USUARIO_ID, ANUNCIO_ID, AGORA);

        ArgumentCaptor<FavoritoAnuncioEntity> captor = ArgumentCaptor.forClass(FavoritoAnuncioEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(captor.getValue().getAnuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(captor.getValue().getCriadoEm()).isEqualTo(AGORA);
    }

    @Test
    void remocaoRepetidaNaoFalhaNemTentaExcluirOutraRelacao() {
        when(repository.findByUsuarioIdAndAnuncioId(USUARIO_ID, ANUNCIO_ID)).thenReturn(Optional.empty());

        service.removerSeExistente(USUARIO_ID, ANUNCIO_ID);

        verify(repository, never()).delete(any(FavoritoAnuncioEntity.class));
    }
}
