package br.com.topsdojob.v3.application.operacional.hml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MidiaRestritaRegularizacaoServiceTest {

  @Test
  void segundaPassagemPreservaDerivacaoSemCriarOutra() {
    UUID arquivoId = UUID.randomUUID();
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    ArquivoMidiaEntity arquivo = mock(ArquivoMidiaEntity.class);
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaDerivacaoService derivacaoService = mock(MidiaRestritaDerivacaoService.class);
    when(vinculo.getArquivoMidiaId()).thenReturn(arquivoId);
    when(arquivo.getId()).thenReturn(arquivoId);
    when(arquivo.getStatusArquivo()).thenReturn(StatusArquivoMidia.VALIDADO);
    when(arquivo.getMimeType()).thenReturn("image/jpeg");
    when(vinculoRepository.findFotosRestritasPublicaveis(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
    when(derivacaoService.garantir(arquivo))
        .thenReturn(new MidiaRestritaDerivacaoService.ResultadoGeracao(
            true, "preview.jpg", "a".repeat(64), 960, 640, "image/jpeg"))
        .thenReturn(new MidiaRestritaDerivacaoService.ResultadoGeracao(
            false, "preview.jpg", "a".repeat(64), 960, 640, "image/jpeg"));
    MidiaRestritaRegularizacaoService service = new MidiaRestritaRegularizacaoService(
        vinculoRepository,
        arquivoRepository,
        derivacaoService);

    var primeira = service.regularizar();
    var segunda = service.regularizar();

    assertThat(primeira).isEqualTo(
        new MidiaRestritaRegularizacaoService.Resultado(1, 1, 1, 0));
    assertThat(segunda).isEqualTo(
        new MidiaRestritaRegularizacaoService.Resultado(1, 1, 0, 1));
  }
}
