package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class AdminMidiaDetalhadaConsultaServiceTest {

  private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
  private final AdminMidiaDetalhadaConsultaService service =
      new AdminMidiaDetalhadaConsultaService(
          midiaRepository,
          arquivoRepository,
          anuncioRepository);

  @Test
  void detalheNaoExpoeMidiaRemovida() {
    UUID midiaId = UUID.randomUUID();
    AnuncioMidiaEntity midia = mock(AnuncioMidiaEntity.class);
    when(midia.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
    when(midia.getStatus()).thenReturn(StatusAnuncioMidia.REMOVIDA);
    when(midiaRepository.findById(midiaId)).thenReturn(Optional.of(midia));

    assertThatThrownBy(() -> service.detalhar(midiaId))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
  }

  @Test
  void abaDoAnuncioConsultaSomenteMidiasAindaAcessiveis() {
    UUID anuncioId = UUID.randomUUID();
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    when(anuncio.getId()).thenReturn(anuncioId);
    when(midiaRepository.findByAnuncioIdAndTipoNotAndStatusNot(
        eq(anuncioId),
        eq(TipoAnuncioMidia.STORY),
        eq(StatusAnuncioMidia.REMOVIDA),
        any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    var pagina = service.listarPorAnuncio(anuncio, 0, 20);

    assertThat(pagina.itens()).isEmpty();
    verify(midiaRepository).findByAnuncioIdAndTipoNotAndStatusNot(
        eq(anuncioId),
        eq(TipoAnuncioMidia.STORY),
        eq(StatusAnuncioMidia.REMOVIDA),
        any(Pageable.class));
  }
}
