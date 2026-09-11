package br.com.topsdojob.v3.application.operacional.midia;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.Atualizacao;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.EstadoArquivo;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository.EstadoVinculo;
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
import java.util.Objects;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MidiaRestritaRegularizacaoService {

  private static final int PAGE_SIZE = 1_000;
  private static final int MAX_BATCH_SIZE = 1_000;

  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final ArquivoMidiaRepository arquivoMidiaRepository;
  private final PreviewRestritoBackfillJdbcRepository backfillRepository;
  private final MidiaRestritaPreviewIdentity previewIdentity;
  private final ObjectProvider<ObjectStorageInventory> storageProvider;
  private final Clock clock;

  @Autowired
  public MidiaRestritaRegularizacaoService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      PreviewRestritoBackfillJdbcRepository backfillRepository,
      MidiaRestritaPreviewIdentity previewIdentity,
      ObjectProvider<ObjectStorageInventory> storageProvider) {
    this(anuncioMidiaRepository, arquivoMidiaRepository, backfillRepository,
        previewIdentity, storageProvider, Clock.systemUTC());
  }

  MidiaRestritaRegularizacaoService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      PreviewRestritoBackfillJdbcRepository backfillRepository,
      MidiaRestritaPreviewIdentity previewIdentity,
      ObjectProvider<ObjectStorageInventory> storageProvider,
      Clock clock) {
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.arquivoMidiaRepository = arquivoMidiaRepository;
    this.backfillRepository = backfillRepository;
    this.previewIdentity = previewIdentity;
    this.storageProvider = storageProvider;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
      String esperada = previewIdentity.chavePublica(arquivo);
      itens.add(new Item(arquivo, esperada, classificar(arquivo, esperada, listagem)));
    }
    Snapshot snapshot = new Snapshot(
        backfillRepository.capturarArquivos(arquivos.keySet()),
        backfillRepository.capturarVinculosDosArquivos(arquivos.keySet(), false));
    if (!snapshot.arquivos().keySet().equals(arquivos.keySet())
        || !snapshot.arquivos().keySet().equals(snapshot.arquivosDosVinculos())) {
      throw new IllegalStateException("Snapshot do universo elegivel nao comprovado");
    }
    return Resultado.de(vinculos.size(), arquivos.size(), listagem.paginas(), itens)
        .comSnapshot(snapshot);
  }

  @Transactional
  public Aplicacao aplicar(Resultado plano, int batchSize) {
    return executarAplicacao(plano, batchSize);
  }

  @Transactional
  public Aplicacao aplicar(Resultado plano, int batchSize, Consumer<EstadoCommit> observador) {
    Objects.requireNonNull(observador, "Observador transacional obrigatorio");
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("APPLY observado exige transacao efetiva");
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        observador.accept(switch (status) {
          case STATUS_COMMITTED -> EstadoCommit.COMMITTED;
          case STATUS_ROLLED_BACK -> EstadoCommit.ROLLED_BACK;
          default -> EstadoCommit.UNKNOWN;
        });
      }
    });
    return executarAplicacao(plano, batchSize);
  }

  private Aplicacao executarAplicacao(Resultado plano, int batchSize) {
    validarPlanoComprovado(plano);
    validarBatchSize(batchSize);
    Snapshot completo = exigirSnapshot(plano);
    for (Item item : plano.itens()) {
      if (!completo.arquivos().get(item.arquivo().getId()).metadadosAusentes()
          && !previewDisponivelExato(item.arquivo(), item.chaveEsperada())) {
        throw new IllegalStateException("Estado persistido do preview nao permite promocao segura");
      }
    }
    List<Item> ordenados = plano.itens().stream()
        .filter(item -> completo.arquivos().get(item.arquivo().getId()).metadadosAusentes())
        .sorted(Comparator.comparing(item -> item.arquivo().getId().toString())).toList();
    Set<UUID> alvos = ordenados.stream().map(item -> item.arquivo().getId()).collect(Collectors.toSet());
    Snapshot snapshot = new Snapshot(completo.arquivos().entrySet().stream()
        .filter(entry -> alvos.contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
        completo.vinculos().stream().filter(vinculo -> alvos.contains(vinculo.arquivoId())).toList());
    backfillRepository.limitarEsperaTransacional();
    conferirVinculos(snapshot, true);
    OffsetDateTime agora = OffsetDateTime.now(clock);
    int atualizados = 0;
    int inalterados = plano.arquivos() - alvos.size();
    int lotes = 0;

    for (List<Item> lote : particionar(ordenados, batchSize)) {
      lotes++;
      List<UUID> ids = lote.stream().map(item -> item.arquivo().getId()).toList();
      Map<UUID, ArquivoMidiaEntity> bloqueados = arquivoMidiaRepository
          .findByIdInForUpdate(ids).stream()
          .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
      if (bloqueados.size() != ids.size()) {
        throw new IllegalStateException("Arquivo do plano nao encontrado durante o APPLY");
      }
      Map<UUID, EstadoArquivo> estadosAtuais = backfillRepository.capturarArquivos(ids);

      List<Atualizacao> atualizacoes = new ArrayList<>();
      for (Item item : lote) {
        ArquivoMidiaEntity atual = bloqueados.get(item.arquivo().getId());
        EstadoArquivo anterior = snapshot.arquivos().get(atual.getId());
        EstadoArquivo estadoAtual = estadosAtuais.get(atual.getId());
        if (!Objects.equals(anterior, estadoAtual) || !elegivel(atual)
            || !item.chaveEsperada().equals(previewIdentity.chavePublica(atual))) {
          throw new IllegalStateException("Fonte, estado ou identidade mudou depois do PLAN");
        }
        if (previewDisponivelExato(atual, item.chaveEsperada())) {
          inalterados++;
        } else if (estadoAtual.metadadosAusentes() && estadoAplicavel(atual)) {
          atualizacoes.add(new Atualizacao(
              atual.getId(),
              item.chaveEsperada(),
              previewIdentity.versaoPipeline(),
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
    conferirVinculos(snapshot, false);
    Map<UUID, EstadoArquivo> depois = backfillRepository.capturarArquivos(snapshot.arquivos().keySet());
    if (!depois.keySet().equals(snapshot.arquivos().keySet())) {
      throw new IllegalStateException("Conjunto de arquivos mudou durante o APPLY");
    }
    for (EstadoArquivo antes : snapshot.arquivos().values()) {
      EstadoArquivo atual = depois.get(antes.arquivoId());
      if (!antes.naoPreview().equals(atual.naoPreview())
          || (!antes.metadadosAusentes() && !antes.completo().equals(atual.completo()))) {
        throw new IllegalStateException("Registro regular ou campo nao-preview mudou durante o APPLY");
      }
    }
    return new Aplicacao(atualizados, inalterados, lotes);
  }

  private Snapshot exigirSnapshot(Resultado plano) {
    Snapshot snapshot = plano.snapshot();
    Set<UUID> ids = plano.itens().stream().map(item -> item.arquivo().getId())
        .collect(Collectors.toSet());
    if (snapshot == null || ids.size() != plano.arquivos()
        || !ids.equals(snapshot.arquivos().keySet())
        || !ids.equals(snapshot.arquivosDosVinculos())) {
      throw new IllegalStateException("APPLY exige snapshot completo do PLAN atual");
    }
    return snapshot;
  }

  private void conferirVinculos(Snapshot snapshot, boolean bloquear) {
    if (!snapshot.vinculos().equals(backfillRepository.capturarVinculosDosArquivos(
        snapshot.arquivos().keySet(), bloquear))) {
      throw new IllegalStateException("Universo ou estado dos vinculos alvo mudou depois do PLAN");
    }
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
        if (arquivo == null || !elegivel(arquivo)
            || !item.chaveEsperada().equals(previewIdentity.chavePublica(arquivo))) {
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
    ObjectStorageInventory storage = storageProvider.getIfAvailable();
    if (storage == null) {
      return Listagem.naoComprovada();
    }
    try {
      Set<String> keys = new LinkedHashSet<>();
      Set<String> cursores = new LinkedHashSet<>();
      String cursor = null;
      int paginas = 0;
      do {
        StoredObjectPage page = storage.list(
            StorageArea.PUBLIC_MEDIA,
            previewIdentity.prefixoPreviews(),
            cursor,
            PAGE_SIZE);
        paginas++;
        page.objects().stream().filter(object -> object.size() > 0)
            .map(StoredObjectMetadata::key).forEach(keys::add);
        cursor = page.truncated() ? page.nextContinuationToken() : null;
        if (page.truncated() && (cursor == null || cursor.isBlank() || !cursores.add(cursor))) {
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
      case DESCONHECIDO -> estadoAplicavel(arquivo)
          ? Classificacao.DISPONIVEL
          : Classificacao.INCONSISTENTE;
      case PENDENTE, FALHA, REMOVIDO -> Classificacao.INCONSISTENTE;
    };
  }

  private boolean estadoAplicavel(ArquivoMidiaEntity arquivo) {
    return arquivo.getPreviewRestritoStatus() == StatusDerivadoMidia.DESCONHECIDO
        && arquivo.getPreviewRestritoTipo() == null
        && arquivo.getPreviewRestritoChave() == null
        && arquivo.getPreviewRestritoPipelineVersao() == null
        && arquivo.getPreviewRestritoConfirmadoEm() == null;
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
        && previewIdentity.versaoPipeline().equals(
            arquivo.getPreviewRestritoPipelineVersao())
        && arquivo.getPreviewRestritoConfirmadoEm() == null;
  }

  private boolean previewDisponivelExato(
      ArquivoMidiaEntity arquivo,
      String chaveEsperada) {
    return arquivo.previewRestritoDisponivel()
        && chaveEsperada.equals(arquivo.getPreviewRestritoChave())
        && previewIdentity.versaoPipeline().equals(
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
      List<Item> itens,
      Snapshot snapshot) {

    public Resultado(int vinculos, int arquivos, int disponiveis, int ausentes,
        int inconsistentes, int naoComprovados, int paginasR2, List<Item> itens) {
      this(vinculos, arquivos, disponiveis, ausentes, inconsistentes, naoComprovados,
          paginasR2, itens, null);
    }

    public Resultado comSnapshot(Snapshot estado) {
      return new Resultado(vinculos, arquivos, disponiveis, ausentes, inconsistentes,
          naoComprovados, paginasR2, itens, estado);
    }

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

  public enum EstadoCommit { NOT_STARTED, UNKNOWN, COMMITTED, ROLLED_BACK }

  public record Snapshot(Map<UUID, EstadoArquivo> arquivos, List<EstadoVinculo> vinculos) {
    public Snapshot {
      arquivos = Map.copyOf(arquivos);
      vinculos = List.copyOf(vinculos);
    }

    Set<UUID> arquivosDosVinculos() {
      return vinculos.stream().map(EstadoVinculo::arquivoId).collect(Collectors.toSet());
    }
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
