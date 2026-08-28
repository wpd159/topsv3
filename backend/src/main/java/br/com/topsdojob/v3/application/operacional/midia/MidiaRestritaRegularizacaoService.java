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
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.Atualizacao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDerivadoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoDerivadoMidia;
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
  private static final int MAX_BATCH_SIZE = 1_000;

  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final ArquivoMidiaRepository arquivoMidiaRepository;
  private final PreviewRestritoBackfillJdbcRepository backfillRepository;
  private final MidiaRestritaDerivacaoService derivacaoService;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final Clock clock;

  @Autowired
  public MidiaRestritaRegularizacaoService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      PreviewRestritoBackfillJdbcRepository backfillRepository,
      MidiaRestritaDerivacaoService derivacaoService,
      ObjectProvider<ObjectStorage> storageProvider) {
    this(anuncioMidiaRepository, arquivoMidiaRepository, backfillRepository,
        derivacaoService, storageProvider, Clock.systemUTC());
  }

  MidiaRestritaRegularizacaoService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      PreviewRestritoBackfillJdbcRepository backfillRepository,
      MidiaRestritaDerivacaoService derivacaoService,
      ObjectProvider<ObjectStorage> storageProvider,
      Clock clock) {
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.arquivoMidiaRepository = arquivoMidiaRepository;
    this.backfillRepository = backfillRepository;
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
  public Aplicacao aplicar(Resultado plano, int batchSize) {
    validarPlanoComprovado(plano);
    validarBatchSize(batchSize);
    OffsetDateTime agora = OffsetDateTime.now(clock);
    int atualizados = 0;
    int inalterados = 0;
    int lotes = 0;

    for (List<Item> lote : particionar(plano.itens(), batchSize)) {
      lotes++;
      List<UUID> ids = lote.stream().map(item -> item.arquivo().getId()).toList();
      Map<UUID, ArquivoMidiaEntity> bloqueados = arquivoMidiaRepository
          .findByIdInForUpdate(ids).stream()
          .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
      if (bloqueados.size() != ids.size()) {
        throw new IllegalStateException("Arquivo do plano nao encontrado durante o APPLY");
      }

      List<Atualizacao> atualizacoes = new ArrayList<>();
      for (Item item : lote) {
        ArquivoMidiaEntity atual = bloqueados.get(item.arquivo().getId());
        if (previewDisponivelExato(atual, item.chaveEsperada())) {
          inalterados++;
        } else if (estadoAplicavel(atual, item.chaveEsperada())) {
          atualizacoes.add(new Atualizacao(
              atual.getId(),
              item.chaveEsperada(),
              derivacaoService.versaoPipeline(),
              agora));
        } else {
          throw new IllegalStateException(
              "Estado persistido do preview mudou ou nao permite promocao segura");
        }
      }

      int aplicados = atualizacoes.isEmpty()
          ? 0
          : backfillRepository.marcarDisponiveis(atualizacoes);
      if (aplicados != atualizacoes.size()) {
        throw new IllegalStateException("Concorrencia detectada durante o APPLY do preview");
      }
      atualizados += aplicados;
    }
    return new Aplicacao(atualizados, inalterados, lotes);
  }

  @Transactional(readOnly = true)
  public Validacao validarPersistencia(Resultado plano, int batchSize) {
    validarPlanoComprovado(plano);
    validarBatchSize(batchSize);
    int disponiveis = 0;
    int desconhecidos = 0;
    int pendentes = 0;
    int inconsistentes = 0;

    for (List<Item> lote : particionar(plano.itens(), batchSize)) {
      List<UUID> ids = lote.stream().map(item -> item.arquivo().getId()).toList();
      Map<UUID, ArquivoMidiaEntity> persistidos = arquivoMidiaRepository.findByIdIn(ids).stream()
          .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
      for (Item item : lote) {
        ArquivoMidiaEntity arquivo = persistidos.get(item.arquivo().getId());
        if (arquivo == null) {
          inconsistentes++;
          continue;
        }
        StatusDerivadoMidia status = arquivo.getPreviewRestritoStatus();
        if (status == StatusDerivadoMidia.DISPONIVEL
            && previewDisponivelExato(arquivo, item.chaveEsperada())) {
          disponiveis++;
        } else if (status == StatusDerivadoMidia.DESCONHECIDO
            && estadoDesconhecidoCompativel(arquivo, item.chaveEsperada())) {
          desconhecidos++;
        } else if (status == StatusDerivadoMidia.PENDENTE
            && estadoPendenteExato(arquivo, item.chaveEsperada())) {
          pendentes++;
        } else {
          inconsistentes++;
        }
      }
    }
    return new Validacao(
        plano.arquivos(),
        disponiveis,
        desconhecidos,
        pendentes,
        inconsistentes);
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
    if (!listagem.keys().contains(esperada)) {
      return Classificacao.AUSENTE;
    }
    return switch (arquivo.getPreviewRestritoStatus()) {
      case DISPONIVEL -> previewDisponivelExato(arquivo, esperada)
          ? Classificacao.DISPONIVEL
          : Classificacao.INCONSISTENTE;
      case DESCONHECIDO -> estadoDesconhecidoCompativel(arquivo, esperada)
          ? Classificacao.DISPONIVEL
          : Classificacao.INCONSISTENTE;
      case PENDENTE -> estadoPendenteExato(arquivo, esperada)
          ? Classificacao.DISPONIVEL
          : Classificacao.INCONSISTENTE;
      case FALHA, REMOVIDO -> Classificacao.INCONSISTENTE;
    };
  }

  private boolean estadoAplicavel(ArquivoMidiaEntity arquivo, String chaveEsperada) {
    return switch (arquivo.getPreviewRestritoStatus()) {
      case DESCONHECIDO -> estadoDesconhecidoCompativel(arquivo, chaveEsperada);
      case PENDENTE -> estadoPendenteExato(arquivo, chaveEsperada);
      case DISPONIVEL, FALHA, REMOVIDO -> false;
    };
  }

  private boolean estadoDesconhecidoCompativel(
      ArquivoMidiaEntity arquivo,
      String chaveEsperada) {
    boolean legadoSemMetadados = arquivo.getPreviewRestritoTipo() == null
        && arquivo.getPreviewRestritoChave() == null
        && arquivo.getPreviewRestritoPipelineVersao() == null
        && arquivo.getPreviewRestritoConfirmadoEm() == null;
    return legadoSemMetadados || identidadePendenteExata(arquivo, chaveEsperada);
  }

  private boolean estadoPendenteExato(ArquivoMidiaEntity arquivo, String chaveEsperada) {
    return identidadePendenteExata(arquivo, chaveEsperada);
  }

  private boolean identidadePendenteExata(
      ArquivoMidiaEntity arquivo,
      String chaveEsperada) {
    return arquivo.getPreviewRestritoTipo() == TipoDerivadoMidia.PREVIEW_RESTRITO
        && chaveEsperada.equals(arquivo.getPreviewRestritoChave())
        && derivacaoService.versaoPipeline().equals(
            arquivo.getPreviewRestritoPipelineVersao())
        && arquivo.getPreviewRestritoConfirmadoEm() == null;
  }

  private boolean previewDisponivelExato(
      ArquivoMidiaEntity arquivo,
      String chaveEsperada) {
    return arquivo.previewRestritoDisponivel()
        && chaveEsperada.equals(arquivo.getPreviewRestritoChave())
        && derivacaoService.versaoPipeline().equals(
            arquivo.getPreviewRestritoPipelineVersao());
  }

  private void validarPlanoComprovado(Resultado plano) {
    if (plano == null) {
      throw new IllegalArgumentException("Plano de reconciliacao obrigatorio");
    }
    if (plano.ausentes() != 0
        || plano.inconsistentes() != 0
        || plano.naoComprovados() != 0
        || plano.disponiveis() != plano.arquivos()) {
      throw new IllegalStateException(
          "Inventario de previews ausente, inconsistente ou nao comprovado");
    }
  }

  private void validarBatchSize(int batchSize) {
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("Tamanho de lote invalido");
    }
  }

  private List<List<Item>> particionar(List<Item> itens, int batchSize) {
    List<List<Item>> lotes = new ArrayList<>();
    for (int inicio = 0; inicio < itens.size(); inicio += batchSize) {
      lotes.add(itens.subList(inicio, Math.min(inicio + batchSize, itens.size())));
    }
    return lotes;
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

  public record Aplicacao(int atualizados, int inalterados, int lotes) {
  }

  public record Validacao(
      int elegiveis,
      int disponiveis,
      int desconhecidos,
      int pendentes,
      int inconsistentes) {

    public boolean aprovada() {
      return disponiveis == elegiveis
          && desconhecidos == 0
          && pendentes == 0
          && inconsistentes == 0;
    }
  }

  private record Listagem(Set<String> keys, int paginas, boolean completa) {
    static Listagem naoComprovada() {
      return new Listagem(Set.of(), 0, false);
    }
  }
}
