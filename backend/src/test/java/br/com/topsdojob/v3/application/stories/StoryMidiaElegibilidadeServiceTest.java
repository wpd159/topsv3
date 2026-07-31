package br.com.topsdojob.v3.application.stories;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryMidiaElegibilidadeServiceTest {

    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final StoryMidiaElegibilidadeService service =
            new StoryMidiaElegibilidadeService(midiaRepository, arquivoRepository);

    @Test
    void carregaMidiasDeVariosAnunciosEmDuasConsultasTotais() {
        UUID anuncioA = UUID.randomUUID();
        UUID anuncioB = UUID.randomUUID();
        ArquivoMidiaEntity arquivoA = arquivo();
        ArquivoMidiaEntity arquivoB = arquivo();
        AnuncioMidiaEntity midiaA = midia(anuncioA, arquivoA.getId(), 2);
        AnuncioMidiaEntity midiaB = midia(anuncioB, arquivoB.getId(), 1);
        when(midiaRepository.findByAnuncioIdIn(List.of(anuncioA, anuncioB)))
                .thenReturn(List.of(midiaA, midiaB));
        when(arquivoRepository.findByIdIn(List.of(arquivoA.getId(), arquivoB.getId())))
                .thenReturn(List.of(arquivoA, arquivoB));

        var resultado = service.listarPorAnuncios(List.of(anuncioA, anuncioB));

        assertThat(resultado).containsOnlyKeys(anuncioA, anuncioB);
        assertThat(resultado.get(anuncioA)).singleElement()
                .satisfies(item -> assertThat(item.vinculo().getId()).isEqualTo(midiaA.getId()));
        assertThat(resultado.get(anuncioB)).singleElement()
                .satisfies(item -> assertThat(item.vinculo().getId()).isEqualTo(midiaB.getId()));
        verify(midiaRepository).findByAnuncioIdIn(List.of(anuncioA, anuncioB));
        verify(arquivoRepository).findByIdIn(List.of(arquivoA.getId(), arquivoB.getId()));
        verify(midiaRepository, never()).findByAnuncioId(anuncioA);
        verify(midiaRepository, never()).findByAnuncioId(anuncioB);
    }

    private AnuncioMidiaEntity midia(UUID anuncioId, UUID arquivoId, int ordem) {
        AnuncioMidiaEntity midia = entity(AnuncioMidiaEntity.class);
        set(midia, "id", UUID.randomUUID());
        set(midia, "anuncioId", anuncioId);
        set(midia, "arquivoMidiaId", arquivoId);
        set(midia, "tipo", TipoAnuncioMidia.FOTO);
        set(midia, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(midia, "ordem", ordem);
        return midia;
    }

    private ArquivoMidiaEntity arquivo() {
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", UUID.randomUUID());
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        return arquivo;
    }
}
