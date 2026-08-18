package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminResumoProprietarioAtivoTest {

    @Test
    void resumoDeAnunciosUsaSomentePendenciasComProprietarioAtivo() {
        AnuncioRepository repository = mock(AnuncioRepository.class);
        when(repository.countPendentesModeracaoComProprietarioAtivo()).thenReturn(2L);
        when(repository.countByStatusAndRemovidoEmIsNull(StatusAnuncio.PUBLICADO)).thenReturn(4L);
        when(repository.countByStatusAndRemovidoEmIsNull(StatusAnuncio.PAUSADO)).thenReturn(3L);
        when(repository.countByStatusInAndRemovidoEmIsNull(List.of(
                StatusAnuncio.PENDENTE_REVISAO,
                StatusAnuncio.APROVADO,
                StatusAnuncio.PUBLICADO,
                StatusAnuncio.PAUSADO))).thenReturn(9L);

        var resultado = new AdminAnuncioResumoConsultaService(repository).consultar();

        assertThat(resultado.pendentesRevisao()).isEqualTo(2L);
        assertThat(resultado.porStatus())
                .filteredOn(item -> item.codigo().equals(StatusAnuncio.PENDENTE_REVISAO.name()))
                .singleElement()
                .satisfies(item -> assertThat(item.total()).isEqualTo(2L));
        verify(repository, never())
                .countByStatusAndRemovidoEmIsNull(StatusAnuncio.PENDENTE_REVISAO);
    }

    @Test
    void resumoDeModeracaoNaoContaPendenciaDeProprietarioSuspenso() {
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
        DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
        when(anuncioRepository.countPendentesModeracaoComProprietarioAtivo()).thenReturn(5L);
        when(anuncioRepository.countByStatusModeracaoAndRemovidoEmIsNull(StatusModeracaoAnuncio.REJEITADO))
                .thenReturn(7L);
        when(revisaoRepository.countByStatus(StatusRevisaoAnuncio.ABERTA)).thenReturn(3L);
        when(revisaoRepository.countByStatus(StatusRevisaoAnuncio.EM_ANALISE)).thenReturn(1L);
        when(documentoRepository.countByStatus(StatusDocumentoUsuario.PENDENTE)).thenReturn(2L);
        when(documentoRepository.countByStatus(StatusDocumentoUsuario.EM_ANALISE)).thenReturn(4L);

        var resultado = new AdminModeracaoResumoConsultaService(
                anuncioRepository,
                revisaoRepository,
                documentoRepository).consultar();

        assertThat(resultado.anunciosPendentesModeracao()).isEqualTo(5L);
        assertThat(resultado.anunciosRejeitados()).isEqualTo(7L);
        assertThat(resultado.documentosPendentes()).isEqualTo(6L);
        verify(anuncioRepository, never())
                .countByStatusModeracaoAndRemovidoEmIsNull(StatusModeracaoAnuncio.PENDENTE);
    }
}
