package br.com.topsdojob.v3.application.operacional.midia;

import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MidiaRestritaRegularizacaoService {

  private static final int PAGE_SIZE = 1_000;

  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final ArquivoMidiaRepository arquivoMidiaRepository;
  private final MidiaRestritaDerivacaoService derivacaoService;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final Clock clock;

  @Autowired
  public MidiaRestritaRegularizacaoService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      MidiaRestritaDerivacaoService derivacaoService,
      ObjectProvider<ObjectStorage> storageProvider) {
    this(anuncioMidiaRepository, arquivoMidiaRepository, derivacaoService,
        storageProvider, Clock.systemUTC());
  }

  MidiaRestritaRegularizacaoService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      MidiaRestritaDerivacaoService derivacaoService,
      ObjectProvider<ObjectStorage> storageProvider,
      Clock clock) {
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.arquivoMidiaRepository = arquivoMidiaRepository;
    this.derivacaoService = derivacaoService;
    this.storageProvider = storageProvider;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Resultado planejar() {
    List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findFotosRestritasPublicaveis(
        TipoAnuncioMidia.FOTO,
        StatusAnuncioMidia.PUBLICAVEL,
        VisibilidadeMidia.RESTRITA_18);
    List<UUID> arquivoIds = vinculos.stream()
        .map(AnuncioMidiaEntity::getArquivoMidiaId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
    Map<UUID, ArquivoMidiaEntity> arquivos = arquivoIds.isEmpty()
        ? Map.of()
        : arquivoMidiaRepository.findByIdIn(arquivoIds).stream()
            .filter(this::elegivel)
            .collect(Collectors.toMap(
                ArquivoMidiaEntity::getId,
                Function.identity(),
                (primeiro, ignorado) -> primeiro,
                LinkedHashMap::new));

    Listagem listagem = listarPreviews();
    List<Item> itens = new ArrayList<>();
    for (ArquivoMidiaEntity arquivo : arquivos.values()) {
      String esperada = derivacaoService.chavePublica(arquivo);
      itens.add(new Item(arquivo, esperada, classificar(arquivo, esperada, listagem)));
    }
    return Resultado.de(vinculos.size(), arquivos.size(), listagem.paginas(), itens);
  }

  @Transactional
  public Resultado aplicar(Resultado plano) {
    if (plano == null) {
      throw new IllegalArgumentException("Plano de reconciliacao obrigatorio");
    }
    OffsetDateTime agora = OffsetDateTime.now(clock);
    List<ArquivoMidiaEntity> alterados = new ArrayList<>();
    for (Item item : plano.itens()) {
      ArquivoMidiaEntity arquivo = arquivoMidiaRepository.findByIdForUpdate(item.arquivo().getId())
          .orElseThrow(() -> new IllegalStateException("Arquivo do plano nao encontrado"));
      switch (item.classificacao()) {
        case DISPONIVEL -> arquivo.marcarPreviewRestritoDisponivel(
            item.chaveEsperada(), derivacaoService.versaoPipeline(), agora);
        case AUSENTE, INCONSISTENTE -> arquivo.marcarPreviewRestritoFalha(
            item.chaveEsperada(), derivacaoService.versaoPipeline());
        case NAO_COMPROVADO -> arquivo.marcarPreviewRestritoDesconhecido(
            item.chaveEsperada(), derivacaoService.versaoPipeline());
      }
      alterados.add(arquivo);
    }
    arquivoMidiaRepository.saveAllAndFlush(alterados);
    return plano;
  }

  private Listagem listarPreviews() {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      return Listagem.naoComprovada();
    }
    try {
      Set<String> keys = new LinkedHashSet<>();
      String cursor = null;
      int paginas = 0;
      do {
        StoredObjectPage page = storage.list(
            StorageArea.PUBLIC_MEDIA,
            derivacaoService.prefixoPreviews(),
            cursor,
            PAGE_SIZE);
        paginas++;
        page.objects().stream().map(StoredObjectMetadata::key).forEach(keys::add);
        cursor = page.truncated() ? page.nextContinuationToken() : null;
        if (page.truncated() && (cursor == null || cursor.isBlank())) {
          return Listagem.naoComprovada();
        }
      } while (cursor != null);
      return new Listagem(Set.copyOf(keys), paginas, true);
    } catch (RuntimeException exception) {
      return Listagem.naoComprovada();
    }
  }

  private Classificacao classificar(
      ArquivoMidiaEntity arquivo,
      String esperada,
      Listagem listagem) {
    if (!listagem.completa()) {
      return Classificacao.NAO_COMPROVADO;
    }
    String persistida = arquivo.getPreviewRestritoChave();
    if (persistida != null && !persistida.equals(esperada)) {
      return Classificacao.INCONSISTENTE;
    }
    if (listagem.keys().contains(esperada)) {
      return Classificacao.DISPONIVEL;
    }
    return Classificacao.AUSENTE;
  }

  private boolean elegivel(ArquivoMidiaEntity arquivo) {
    return arquivo != null
        && arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO
        && arquivo.getMimeType() != null
        && arquivo.getMimeType().toLowerCase(java.util.Locale.ROOT).startsWith("image/");
  }

  public enum Classificacao {
    DISPONIVEL, AUSENTE, INCONSISTENTE, NAO_COMPROVADO
  }

  public record Item(
      ArquivoMidiaEntity arquivo,
      String chaveEsperada,
      Classificacao classificacao) {
  }

  public record Resultado(
      int vinculos,
      int arquivos,
      int disponiveis,
      int ausentes,
      int inconsistentes,
      int naoComprovados,
      int paginasR2,
      List<Item> itens) {

    static Resultado de(int vinculos, int arquivos, int paginas, List<Item> itens) {
      return new Resultado(
          vinculos,
          arquivos,
          contar(itens, Classificacao.DISPONIVEL),
          contar(itens, Classificacao.AUSENTE),
          contar(itens, Classificacao.INCONSISTENTE),
          contar(itens, Classificacao.NAO_COMPROVADO),
          paginas,
          List.copyOf(itens));
    }

    private static int contar(List<Item> itens, Classificacao classificacao) {
      return (int) itens.stream().filter(item -> item.classificacao() == classificacao).count();
    }
  }

  private record Listagem(Set<String> keys, int paginas, boolean completa) {
    static Listagem naoComprovada() {
      return new Listagem(Set.of(), 0, false);
    }
  }
}
