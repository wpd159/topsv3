package br.com.topsdojob.v3.application.operacional.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MidiaRestritaRegularizacaoServiceTest {

  @Test
  void planEhIdempotenteEUsaListagemEmLoteSemExists() {
    UUID arquivoId = UUID.randomUUID();
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
        arquivoId, "R2", "privado", "pendentes/origem.jpg", "origem.jpg",
        "image/jpeg", 10L, 100, 100, null, "a".repeat(64), OffsetDateTime.now());
    arquivo.aplicarDecisao(StatusArquivoMidia.VALIDADO);
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaDerivacaoService derivacaoService = mock(MidiaRestritaDerivacaoService.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    String key = "publicas/restritas-borradas/v1/preview.jpg";

    when(vinculo.getArquivoMidiaId()).thenReturn(arquivoId);
    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
    when(derivacaoService.chavePublica(arquivo)).thenReturn(key);
    when(derivacaoService.prefixoPreviews()).thenReturn("publicas/restritas-borradas/v1/");
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.list(any(), any(), isNull(), anyInt())).thenReturn(new StoredObjectPage(
        List.of(new StoredObjectMetadata(key, 10L, "etag", Instant.now())), null, false));

    MidiaRestritaRegularizacaoService service = new MidiaRestritaRegularizacaoService(
        vinculoRepository, arquivoRepository, derivacaoService, provider);

    var primeira = service.planejar();
    var segunda = service.planejar();

    assertThat(primeira.disponiveis()).isEqualTo(1);
    assertThat(segunda).usingRecursiveComparison().isEqualTo(primeira);
    verify(storage, times(2)).list(any(), any(), isNull(), anyInt());
    verify(storage, never()).exists(any(), any());
    verify(storage, never()).get(any(), any());
    verify(storage, never()).put(any(), any(), any(), any());
    verify(storage, never()).delete(any(), any());
  }

  @Test
  void falhaDaListagemMantemTodosNaoComprovados() {
    UUID arquivoId = UUID.randomUUID();
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
        arquivoId, "R2", "privado", "pendentes/origem.jpg", "origem.jpg",
        "image/jpeg", 10L, 100, 100, null, "a".repeat(64), OffsetDateTime.now());
    arquivo.aplicarDecisao(StatusArquivoMidia.VALIDADO);
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaDerivacaoService derivacaoService = mock(MidiaRestritaDerivacaoService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    when(vinculo.getArquivoMidiaId()).thenReturn(arquivoId);
    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
    when(derivacaoService.chavePublica(arquivo)).thenReturn("preview.jpg");
    when(provider.getIfAvailable()).thenReturn(null);

    var resultado = new MidiaRestritaRegularizacaoService(
        vinculoRepository, arquivoRepository, derivacaoService, provider).planejar();

    assertThat(resultado.naoComprovados()).isEqualTo(1);
    assertThat(resultado.disponiveis()).isZero();
  }

  @Test
  void milTrezentosEQuarentaEQuatroPreviewsUsamUmaListagemESemNMaisUm() {
    List<ArquivoMidiaEntity> arquivos = IntStream.range(0, 1_344)
        .mapToObj(index -> arquivoValidado(UUID.randomUUID()))
        .toList();
    List<AnuncioMidiaEntity> vinculos = arquivos.stream().map(arquivo -> {
      AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
      when(vinculo.getArquivoMidiaId()).thenReturn(arquivo.getId());
      return vinculo;
    }).toList();
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaDerivacaoService derivacaoService = mock(MidiaRestritaDerivacaoService.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    String prefix = "publicas/restritas-borradas/v1/";
    List<StoredObjectMetadata> objects = arquivos.stream()
        .map(arquivo -> new StoredObjectMetadata(
            prefix + arquivo.getId() + ".jpg", 10L, "etag", Instant.now()))
        .toList();

    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(vinculos);
    when(arquivoRepository.findByIdIn(any())).thenReturn(arquivos);
    when(derivacaoService.chavePublica(any())).thenAnswer(invocation ->
        prefix + invocation.<ArquivoMidiaEntity>getArgument(0).getId() + ".jpg");
    when(derivacaoService.prefixoPreviews()).thenReturn(prefix);
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.list(any(), any(), isNull(), anyInt()))
        .thenReturn(new StoredObjectPage(objects, null, false));

    var resultado = new MidiaRestritaRegularizacaoService(
        vinculoRepository, arquivoRepository, derivacaoService, provider).planejar();

    assertThat(resultado.arquivos()).isEqualTo(1_344);
    assertThat(resultado.disponiveis()).isEqualTo(1_344);
    verify(storage).list(any(), any(), isNull(), anyInt());
    verify(storage, never()).exists(any(), any());
    verify(storage, never()).get(any(), any());
    verify(storage, never()).put(any(), any(), any(), any());
    verify(storage, never()).delete(any(), any());
  }

  @Test
  void chavePersistidaDiferenteDaDeterministicaEhInconsistenteMesmoSeAusenteNoR2() {
    ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
    arquivo.marcarPreviewRestritoFalha("publicas/restritas-borradas/v1/antiga.jpg", "v1");
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaDerivacaoService derivacaoService = mock(MidiaRestritaDerivacaoService.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);

    when(vinculo.getArquivoMidiaId()).thenReturn(arquivo.getId());
    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
    when(derivacaoService.chavePublica(arquivo))
        .thenReturn("publicas/restritas-borradas/v1/atual.jpg");
    when(derivacaoService.prefixoPreviews())
        .thenReturn("publicas/restritas-borradas/v1/");
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.list(any(), any(), isNull(), anyInt()))
        .thenReturn(new StoredObjectPage(List.of(), null, false));

    var resultado = new MidiaRestritaRegularizacaoService(
        vinculoRepository, arquivoRepository, derivacaoService, provider).planejar();

    assertThat(resultado.inconsistentes()).isEqualTo(1);
    assertThat(resultado.ausentes()).isZero();
  }

  private ArquivoMidiaEntity arquivoValidado(UUID id) {
    ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
        id, "R2", "privado", "pendentes/" + id + ".jpg", "origem.jpg",
        "image/jpeg", 10L, 100, 100, null, "a".repeat(64), OffsetDateTime.now());
    arquivo.aplicarDecisao(StatusArquivoMidia.VALIDADO);
    return arquivo;
  }
}
