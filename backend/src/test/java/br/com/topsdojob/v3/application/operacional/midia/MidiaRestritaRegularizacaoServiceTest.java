package br.com.topsdojob.v3.application.operacional.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService.Classificacao;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService.Item;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService.Resultado;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.EstadoArquivo;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.EstadoVinculo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

class MidiaRestritaRegularizacaoServiceTest {

  @Test
  void planUsaTransacaoReadOnly() throws Exception {
    Transactional transaction = MidiaRestritaRegularizacaoService.class
        .getMethod("planejar")
        .getAnnotation(Transactional.class);

    assertThat(transaction).isNotNull();
    assertThat(transaction.readOnly()).isTrue();
  }

  @Test
  void planEhIdempotenteEUsaListagemEmLoteSemExists() {
    UUID arquivoId = UUID.randomUUID();
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    ArquivoMidiaEntity arquivo = arquivoValidado(arquivoId);
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaPreviewIdentity previewIdentity = mock(MidiaRestritaPreviewIdentity.class);
    ObjectStorageInventory storage = mock(ObjectStorageInventory.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorageInventory> provider = mock(ObjectProvider.class);
    String key = "publicas/restritas-borradas/v1/preview.jpg";

    when(vinculo.getArquivoMidiaId()).thenReturn(arquivoId);
    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
    when(previewIdentity.chavePublica(arquivo)).thenReturn(key);
    when(previewIdentity.versaoPipeline()).thenReturn("v1");
    when(previewIdentity.prefixoPreviews()).thenReturn("publicas/restritas-borradas/v1/");
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.list(any(), any(), isNull(), anyInt())).thenReturn(new StoredObjectPage(
        List.of(new StoredObjectMetadata(key, 10L, "etag", Instant.now())), null, false));

    MidiaRestritaRegularizacaoService service = novoServico(
        vinculoRepository, arquivoRepository, previewIdentity, provider);

    var primeira = service.planejar();
    var segunda = service.planejar();

    assertThat(primeira.disponiveis()).isEqualTo(1);
    assertThat(segunda).usingRecursiveComparison().isEqualTo(primeira);
    verify(storage, times(2)).list(any(), any(), isNull(), anyInt());
    verifyNoMoreInteractions(storage);
  }

  @Test
  void falhaDaListagemMantemTodosNaoComprovados() {
    UUID arquivoId = UUID.randomUUID();
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    ArquivoMidiaEntity arquivo = arquivoValidado(arquivoId);
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaPreviewIdentity previewIdentity = mock(MidiaRestritaPreviewIdentity.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorageInventory> provider = mock(ObjectProvider.class);
    when(vinculo.getArquivoMidiaId()).thenReturn(arquivoId);
    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
    when(previewIdentity.chavePublica(arquivo)).thenReturn("preview.jpg");
    when(provider.getIfAvailable()).thenReturn(null);

    var resultado = novoServico(
        vinculoRepository, arquivoRepository, previewIdentity, provider).planejar();

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
    MidiaRestritaPreviewIdentity previewIdentity = mock(MidiaRestritaPreviewIdentity.class);
    ObjectStorageInventory storage = mock(ObjectStorageInventory.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorageInventory> provider = mock(ObjectProvider.class);
    String prefix = "publicas/restritas-borradas/v1/";
    List<StoredObjectMetadata> objects = arquivos.stream()
        .map(arquivo -> new StoredObjectMetadata(
            prefix + arquivo.getId() + ".jpg", 10L, "etag", Instant.now()))
        .toList();

    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(vinculos);
    when(arquivoRepository.findByIdIn(any())).thenReturn(arquivos);
    when(previewIdentity.chavePublica(any())).thenAnswer(invocation ->
        prefix + invocation.<ArquivoMidiaEntity>getArgument(0).getId() + ".jpg");
    when(previewIdentity.versaoPipeline()).thenReturn("v1");
    when(previewIdentity.prefixoPreviews()).thenReturn(prefix);
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.list(any(), any(), isNull(), anyInt()))
        .thenReturn(new StoredObjectPage(objects, null, false));

    var resultado = novoServico(
        vinculoRepository, arquivoRepository, previewIdentity, provider).planejar();

    assertThat(resultado.arquivos()).isEqualTo(1_344);
    assertThat(resultado.disponiveis()).isEqualTo(1_344);
    verify(storage).list(any(), any(), isNull(), anyInt());
    verify(arquivoRepository).findByIdIn(any());
    verifyNoMoreInteractions(storage);
  }

  @Test
  void chavePersistidaDiferenteDaDeterministicaEhInconsistenteMesmoSeAusenteNoR2() {
    ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
    arquivo.marcarPreviewRestritoFalha("publicas/restritas-borradas/v1/antiga.jpg", "v1");
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    AnuncioMidiaRepository vinculoRepository = mock(AnuncioMidiaRepository.class);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaPreviewIdentity previewIdentity = mock(MidiaRestritaPreviewIdentity.class);
    ObjectStorageInventory storage = mock(ObjectStorageInventory.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorageInventory> provider = mock(ObjectProvider.class);

    when(vinculo.getArquivoMidiaId()).thenReturn(arquivo.getId());
    when(vinculoRepository.findFotosRestritasPublicaveis(any(), any(), any()))
        .thenReturn(List.of(vinculo));
    when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
    when(previewIdentity.chavePublica(arquivo))
        .thenReturn("publicas/restritas-borradas/v1/atual.jpg");
    when(previewIdentity.versaoPipeline()).thenReturn("v1");
    when(previewIdentity.prefixoPreviews())
        .thenReturn("publicas/restritas-borradas/v1/");
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.list(any(), any(), isNull(), anyInt()))
        .thenReturn(new StoredObjectPage(List.of(), null, false));

    var resultado = novoServico(
        vinculoRepository, arquivoRepository, previewIdentity, provider).planejar();

    assertThat(resultado.inconsistentes()).isEqualTo(1);
    assertThat(resultado.ausentes()).isZero();
  }

  @Test
  void applyRecusaPlanoIncompletoAntesDeBloquearOuEscrever() {
    ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    PreviewRestritoBackfillJdbcRepository backfill =
        mock(PreviewRestritoBackfillJdbcRepository.class);
    MidiaRestritaRegularizacaoService service = novoServicoAplicacao(
        arquivoRepository, backfill, mock(MidiaRestritaPreviewIdentity.class));
    Resultado plano = Resultado.de(
        1, 1, 1, List.of(new Item(arquivo, "preview.jpg", Classificacao.AUSENTE)));

    assertThatThrownBy(() -> service.aplicar(plano, 200))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ausente, inconsistente ou nao comprovado");

    verify(arquivoRepository, never()).findByIdInForUpdate(any());
    verifyNoInteractions(backfill);
  }

  @Test
  void applyDe1344RegistrosUsaSeteLotesSemNMaisUm() {
    List<ArquivoMidiaEntity> arquivos = IntStream.range(0, 1_344)
        .mapToObj(index -> arquivoValidado(UUID.randomUUID()))
        .toList();
    Map<UUID, ArquivoMidiaEntity> porId = arquivos.stream().collect(Collectors.toMap(
        ArquivoMidiaEntity::getId,
        arquivo -> arquivo,
        (primeiro, ignorado) -> primeiro,
        LinkedHashMap::new));
    String prefix = "publicas/restritas-borradas/v1/";
    List<Item> itens = arquivos.stream()
        .map(arquivo -> new Item(
            arquivo,
            prefix + arquivo.getId() + ".jpg",
            Classificacao.DISPONIVEL))
        .toList();
    Resultado plano = Resultado.de(1_344, 1_344, 2, itens);
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    PreviewRestritoBackfillJdbcRepository backfill =
        mock(PreviewRestritoBackfillJdbcRepository.class);
    MidiaRestritaPreviewIdentity previewIdentity = mock(MidiaRestritaPreviewIdentity.class);
    when(previewIdentity.versaoPipeline()).thenReturn("v1");
    when(previewIdentity.chavePublica(any())).thenAnswer(invocation ->
        prefix + invocation.<ArquivoMidiaEntity>getArgument(0).getId() + ".jpg");
    when(arquivoRepository.findByIdInForUpdate(any())).thenAnswer(invocation -> {
      Collection<UUID> ids = invocation.getArgument(0);
      return ids.stream().map(porId::get).toList();
    });
    when(backfill.marcarDisponiveis(any())).thenAnswer(invocation ->
        invocation.<List<?>>getArgument(0).size());
    MidiaRestritaRegularizacaoService service = novoServicoAplicacao(
        arquivoRepository, backfill, previewIdentity);

    var aplicacao = service.aplicar(prepararSnapshot(plano, backfill), 200);

    assertThat(aplicacao.atualizados()).isEqualTo(1_344);
    assertThat(aplicacao.inalterados()).isZero();
    assertThat(aplicacao.lotes()).isEqualTo(7);
    verify(arquivoRepository, times(7)).findByIdInForUpdate(any());
    verify(arquivoRepository, never()).findByIdForUpdate(any());
    verify(backfill, times(7)).marcarDisponiveis(any());
  }

  @Test
  void segundaPassagemNaoEscrevePreviewJaDisponivel() {
    ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
    String key = "publicas/restritas-borradas/v1/preview.jpg";
    arquivo.marcarPreviewRestritoDisponivel(key, "v1", OffsetDateTime.now());
    Resultado plano = Resultado.de(
        1, 1, 1, List.of(new Item(arquivo, key, Classificacao.DISPONIVEL)));
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    PreviewRestritoBackfillJdbcRepository backfill =
        mock(PreviewRestritoBackfillJdbcRepository.class);
    MidiaRestritaPreviewIdentity previewIdentity = mock(MidiaRestritaPreviewIdentity.class);
    when(previewIdentity.versaoPipeline()).thenReturn("v1");
    when(previewIdentity.chavePublica(arquivo)).thenReturn(key);
    when(arquivoRepository.findByIdInForUpdate(any())).thenReturn(List.of(arquivo));
    MidiaRestritaRegularizacaoService service = novoServicoAplicacao(
        arquivoRepository, backfill, previewIdentity);

    var aplicacao = service.aplicar(prepararSnapshot(plano, backfill), 200);

    assertThat(aplicacao.atualizados()).isZero();
    assertThat(aplicacao.inalterados()).isEqualTo(1);
    verify(backfill, never()).marcarDisponiveis(any());
  }

  @Test
  void applyRecusaPendenteEDesconhecidoComIdentidadePreenchida() {
    for (boolean pendente : List.of(true, false)) {
      ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
      String key = "publicas/restritas-borradas/v1/preview.jpg";
      if (pendente) arquivo.marcarPreviewRestritoPendente(key, "v1");
      else arquivo.marcarPreviewRestritoDesconhecido(key, "v1");
      var arquivoRepository = mock(ArquivoMidiaRepository.class);
      var backfill = mock(PreviewRestritoBackfillJdbcRepository.class);
      var identity = mock(MidiaRestritaPreviewIdentity.class);
      when(identity.versaoPipeline()).thenReturn("v1");
      when(identity.chavePublica(arquivo)).thenReturn(key);
      when(arquivoRepository.findByIdInForUpdate(any())).thenReturn(List.of(arquivo));
      var plano = prepararSnapshot(Resultado.de(1, 1, 1,
          List.of(new Item(arquivo, key, Classificacao.DISPONIVEL))), backfill);

      assertThatThrownBy(() -> novoServicoAplicacao(arquivoRepository, backfill, identity)
          .aplicar(plano, 200)).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("nao permite promocao segura");
      verify(backfill, never()).marcarDisponiveis(any());
    }
  }

  @Test
  void applyRecusaArquivoAlteradoEChaveCanonicaAlteradaAntesDeEscrever() {
    for (boolean mudarFingerprint : List.of(true, false)) {
      ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
      String key = "publicas/restritas-borradas/v1/preview.jpg";
      var arquivoRepository = mock(ArquivoMidiaRepository.class);
      var backfill = mock(PreviewRestritoBackfillJdbcRepository.class);
      var identity = mock(MidiaRestritaPreviewIdentity.class);
      when(identity.versaoPipeline()).thenReturn("v1");
      when(identity.chavePublica(arquivo)).thenReturn(mudarFingerprint ? key : key + "-mudou");
      when(arquivoRepository.findByIdInForUpdate(any())).thenReturn(List.of(arquivo));
      var plano = prepararSnapshot(Resultado.de(1, 1, 1,
          List.of(new Item(arquivo, key, Classificacao.DISPONIVEL))), backfill);
      if (mudarFingerprint) {
        doReturn(Map.of(arquivo.getId(),
            new EstadoArquivo(arquivo.getId(), "concorrente", "source", "DESCONHECIDO", true)))
            .when(backfill).capturarArquivos(any());
      }

      assertThatThrownBy(() -> novoServicoAplicacao(arquivoRepository, backfill, identity)
          .aplicar(plano, 200)).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Fonte, estado ou identidade mudou");
      verify(backfill, never()).marcarDisponiveis(any());
    }
  }

  @Test
  void applyRecusaDeltaNoUniversoAntesDeBloquearArquivos() {
    ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
    var arquivoRepository = mock(ArquivoMidiaRepository.class);
    var backfill = mock(PreviewRestritoBackfillJdbcRepository.class);
    var plano = prepararSnapshot(Resultado.de(1, 1, 1,
        List.of(new Item(arquivo, "preview.jpg", Classificacao.DISPONIVEL))), backfill);
    when(backfill.capturarVinculosElegiveis(true)).thenReturn(List.of());

    assertThatThrownBy(() -> novoServicoAplicacao(arquivoRepository, backfill,
        mock(MidiaRestritaPreviewIdentity.class)).aplicar(plano, 200))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Universo ou estado dos vinculos");
    verify(arquivoRepository, never()).findByIdInForUpdate(any());
    verify(backfill, never()).marcarDisponiveis(any());
  }

  @Test
  void applyRecusaPlanoSemSnapshotMesmoComInventarioComprovado() {
    ArquivoMidiaEntity arquivo = arquivoValidado(UUID.randomUUID());
    var backfill = mock(PreviewRestritoBackfillJdbcRepository.class);
    var plano = Resultado.de(1, 1, 1,
        List.of(new Item(arquivo, "preview.jpg", Classificacao.DISPONIVEL)));
    assertThatThrownBy(() -> novoServicoAplicacao(mock(ArquivoMidiaRepository.class), backfill,
        mock(MidiaRestritaPreviewIdentity.class)).aplicar(plano, 200))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("snapshot completo");
    verifyNoInteractions(backfill);
  }

  @Test
  void validacaoDistingueDisponivelDesconhecidoPendenteEInconsistente() {
    String prefix = "publicas/restritas-borradas/v1/";
    ArquivoMidiaEntity disponivel = arquivoValidado(UUID.randomUUID());
    ArquivoMidiaEntity desconhecido = arquivoValidado(UUID.randomUUID());
    ArquivoMidiaEntity pendente = arquivoValidado(UUID.randomUUID());
    ArquivoMidiaEntity falho = arquivoValidado(UUID.randomUUID());
    String keyDisponivel = prefix + disponivel.getId() + ".jpg";
    String keyDesconhecido = prefix + desconhecido.getId() + ".jpg";
    String keyPendente = prefix + pendente.getId() + ".jpg";
    String keyFalho = prefix + falho.getId() + ".jpg";
    disponivel.marcarPreviewRestritoDisponivel(keyDisponivel, "v1", OffsetDateTime.now());
    pendente.marcarPreviewRestritoPendente(keyPendente, "v1");
    falho.marcarPreviewRestritoFalha(keyFalho, "v1");
    List<ArquivoMidiaEntity> arquivos = List.of(disponivel, desconhecido, pendente, falho);
    List<Item> itens = List.of(
        new Item(disponivel, keyDisponivel, Classificacao.DISPONIVEL),
        new Item(desconhecido, keyDesconhecido, Classificacao.DISPONIVEL),
        new Item(pendente, keyPendente, Classificacao.DISPONIVEL),
        new Item(falho, keyFalho, Classificacao.DISPONIVEL));
    ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    MidiaRestritaPreviewIdentity previewIdentity = mock(MidiaRestritaPreviewIdentity.class);
    when(previewIdentity.versaoPipeline()).thenReturn("v1");
    when(previewIdentity.chavePublica(any())).thenAnswer(invocation ->
        prefix + invocation.<ArquivoMidiaEntity>getArgument(0).getId() + ".jpg");
    when(arquivoRepository.findByIdIn(any())).thenReturn(arquivos);
    MidiaRestritaRegularizacaoService service = novoServicoAplicacao(
        arquivoRepository,
        mock(PreviewRestritoBackfillJdbcRepository.class),
        previewIdentity);

    var validacao = service.validarPersistencia(Resultado.de(4, 4, 1, itens), 200);

    assertThat(validacao.disponiveis()).isEqualTo(1);
    assertThat(validacao.desconhecidos()).isEqualTo(1);
    assertThat(validacao.pendentes()).isEqualTo(1);
    assertThat(validacao.inconsistentes()).isEqualTo(1);
    assertThat(validacao.aprovada()).isFalse();
  }

  private MidiaRestritaRegularizacaoService novoServico(
      AnuncioMidiaRepository vinculoRepository,
      ArquivoMidiaRepository arquivoRepository,
      MidiaRestritaPreviewIdentity previewIdentity,
      ObjectProvider<ObjectStorageInventory> provider) {
    PreviewRestritoBackfillJdbcRepository backfill = mock(PreviewRestritoBackfillJdbcRepository.class);
    when(backfill.capturarArquivos(any())).thenAnswer(invocation -> {
      Collection<UUID> ids = invocation.getArgument(0);
      return ids.stream().collect(Collectors.toMap(Function.identity(), id ->
          new EstadoArquivo(id, "snapshot-" + id, "source-" + id, "DESCONHECIDO", true)));
    });
    when(backfill.capturarVinculosElegiveis(false)).thenAnswer(invocation ->
        vinculoRepository.findFotosRestritasPublicaveis(
            br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia.FOTO,
            br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia.PUBLICAVEL,
            br.com.topsdojob.v3.domain.shared.VisibilidadeMidia.RESTRITA_18).stream()
            .map(v -> new EstadoVinculo(v.getArquivoMidiaId(), v.getArquivoMidiaId(), "vinculo"))
            .toList());
    return new MidiaRestritaRegularizacaoService(
        vinculoRepository,
        arquivoRepository,
        backfill,
        previewIdentity,
        provider);
  }

  private Resultado prepararSnapshot(Resultado plano, PreviewRestritoBackfillJdbcRepository backfill) {
    Map<UUID, EstadoArquivo> estados = plano.itens().stream().collect(Collectors.toMap(
        item -> item.arquivo().getId(), item -> {
          var arquivo = item.arquivo();
          return new EstadoArquivo(arquivo.getId(), "snapshot-" + arquivo.getId(),
              "source-" + arquivo.getId(), arquivo.getPreviewRestritoStatus().name(),
              arquivo.getPreviewRestritoTipo() == null && arquivo.getPreviewRestritoChave() == null
                  && arquivo.getPreviewRestritoPipelineVersao() == null
                  && arquivo.getPreviewRestritoConfirmadoEm() == null
                  && arquivo.getPreviewRestritoStatus()
                      == br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDerivadoMidia.DESCONHECIDO);
        }));
    List<EstadoVinculo> vinculos = plano.itens().stream().map(item -> new EstadoVinculo(
        item.arquivo().getId(), item.arquivo().getId(), "vinculo")).toList();
    when(backfill.capturarArquivos(any())).thenAnswer(invocation -> {
      Collection<UUID> ids = invocation.getArgument(0);
      return ids.stream().collect(Collectors.toMap(Function.identity(), estados::get));
    });
    when(backfill.capturarVinculosElegiveis(anyBoolean())).thenReturn(vinculos);
    return plano.comSnapshot(new MidiaRestritaRegularizacaoService.Snapshot(estados, vinculos));
  }

  private MidiaRestritaRegularizacaoService novoServicoAplicacao(
      ArquivoMidiaRepository arquivoRepository,
      PreviewRestritoBackfillJdbcRepository backfill,
      MidiaRestritaPreviewIdentity previewIdentity) {
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorageInventory> provider = mock(ObjectProvider.class);
    return new MidiaRestritaRegularizacaoService(
        mock(AnuncioMidiaRepository.class),
        arquivoRepository,
        backfill,
        previewIdentity,
        provider);
  }

  @Test
  void contratoDeInventarioExpoeSomenteListagem() {
    assertThat(ObjectStorageInventory.class.getDeclaredMethods())
        .extracting(java.lang.reflect.Method::getName)
        .containsExactly("list");
  }

  private ArquivoMidiaEntity arquivoValidado(UUID id) {
    ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
        id, "R2", "privado", "pendentes/" + id + ".jpg", "origem.jpg",
        "image/jpeg", 10L, 100, 100, null, "a".repeat(64), OffsetDateTime.now());
    arquivo.aplicarDecisao(StatusArquivoMidia.VALIDADO);
    return arquivo;
  }
}
