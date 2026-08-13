package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.RelatorioExecucao;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.ResultadoItem;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.StatusResultado;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ReconciliadorMidiaFaseCinco {

  public Reconciliacao reconciliar(
      ManifestoMidiaFaseCinco manifest,
      RelatorioExecucao execution) {
    Map<StatusResultado, Long> counts = new EnumMap<>(StatusResultado.class);
    for (StatusResultado status : StatusResultado.values()) {
      counts.put(status, 0L);
    }
    execution.resultados().stream()
        .map(ResultadoItem::status)
        .forEach(status -> counts.compute(status, (ignored, value) -> value + 1));

    long expected = manifest.itens().size();
    long importable = manifest.itens().stream()
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .count();
    Map<String, Item> importableObjects = manifest.itens().stream()
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .collect(Collectors.toMap(
            CheckpointMidiaMigracaoJdbc::destinoFingerprint,
            Function.identity(),
            (first, second) -> first));
    long completed = counts.get(StatusResultado.COPIADA)
        + counts.get(StatusResultado.PRESERVADA);
    if (execution.dryRun()) {
      completed += counts.get(StatusResultado.VALIDADA);
    }
    long classified = counts.values().stream().mapToLong(Long::longValue).sum();
    long discardedManifest = manifest.itens().stream()
        .filter(item -> item.decisao() == Decisao.DESCARTAR)
        .count();
    long quarantineManifest = manifest.itens().stream()
        .filter(item -> item.decisao() == Decisao.QUARENTENA)
        .count();
    long importableBytes = importableObjects.values().stream()
        .mapToLong(Item::tamanhoBytes)
        .sum();
    Map<String, Item> itemsByFingerprint = manifest.itens().stream()
        .collect(Collectors.toMap(Item::fingerprint, Function.identity()));
    Set<String> completedDestinations = execution.resultados().stream()
        .filter(ResultadoItem::sucesso)
        .map(ResultadoItem::itemFingerprint)
        .map(itemsByFingerprint::get)
        .filter(java.util.Objects::nonNull)
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .map(CheckpointMidiaMigracaoJdbc::destinoFingerprint)
        .collect(Collectors.toSet());
    long validatedBytes = completedDestinations.stream()
        .map(importableObjects::get)
        .filter(java.util.Objects::nonNull)
        .mapToLong(Item::tamanhoBytes)
        .sum();
    boolean classificationEquation = expected == importable + discardedManifest + quarantineManifest;
    boolean executionEquation = expected == classified;
    boolean bytesEquation = importableBytes == validatedBytes;
    boolean approved = classificationEquation
        && executionEquation
        && bytesEquation
        && completed == importable
        && completedDestinations.size() == importableObjects.size()
        && counts.get(StatusResultado.BLOQUEADA) == 0
        && counts.get(StatusResultado.FALHA) == 0
        && counts.get(StatusResultado.INTERROMPIDA) == 0;
    return new Reconciliacao(
        expected,
        importable,
        completed,
        importableObjects.size(),
        completedDestinations.size(),
        counts.get(StatusResultado.DESCARTADA),
        counts.get(StatusResultado.QUARENTENA),
        counts.get(StatusResultado.BLOQUEADA),
        counts.get(StatusResultado.FALHA),
        counts.get(StatusResultado.INTERROMPIDA),
        importableBytes,
        validatedBytes,
        classificationEquation,
        executionEquation,
        bytesEquation,
        porClasse(manifest, execution),
        approved);
  }

  private Map<String, ResumoClasse> porClasse(
      ManifestoMidiaFaseCinco manifest,
      RelatorioExecucao execution) {
    Map<String, ResultadoItem> results = execution.resultados().stream()
        .collect(Collectors.toMap(
            ResultadoItem::itemFingerprint,
            Function.identity(),
            (first, second) -> first));
    Map<String, List<Item>> grouped = manifest.itens().stream()
        .collect(Collectors.groupingBy(
            this::classe,
            LinkedHashMap::new,
            Collectors.toList()));
    Map<String, ResumoClasse> summaries = new LinkedHashMap<>();
    grouped.forEach((classification, items) -> {
      long importable = items.stream().filter(item -> item.decisao() == Decisao.IMPORTAR).count();
      long discarded = items.stream().filter(item -> item.decisao() == Decisao.DESCARTAR).count();
      long quarantine = items.stream().filter(item -> item.decisao() == Decisao.QUARENTENA).count();
      long completedReferences = items.stream()
          .map(item -> results.get(item.fingerprint()))
          .filter(java.util.Objects::nonNull)
          .filter(ResultadoItem::sucesso)
          .count();
      Map<String, Item> classObjects = items.stream()
          .filter(item -> item.decisao() == Decisao.IMPORTAR)
          .collect(Collectors.toMap(
              CheckpointMidiaMigracaoJdbc::destinoFingerprint,
              Function.identity(),
              (first, second) -> first));
      Set<String> classCompletedObjects = items.stream()
          .filter(item -> {
            ResultadoItem result = results.get(item.fingerprint());
            return result != null && result.sucesso();
          })
          .map(CheckpointMidiaMigracaoJdbc::destinoFingerprint)
          .collect(Collectors.toSet());
      long bytes = classCompletedObjects.stream()
          .map(classObjects::get)
          .filter(java.util.Objects::nonNull)
          .mapToLong(Item::tamanhoBytes)
          .sum();
      summaries.put(
          classification,
          new ResumoClasse(
              items.size(),
              importable,
              classObjects.size(),
              completedReferences,
              classCompletedObjects.size(),
              discarded,
              quarantine,
              bytes));
    });
    return Map.copyOf(summaries);
  }

  private String classe(Item item) {
    if (item.origem().tipo() == TipoOrigem.ARQUIVO_LOCAL) {
      return "LOCAL";
    }
    if (item.decisao() == Decisao.DESCARTAR) {
      return "DESCARTAVEL";
    }
    if (item.decisao() == Decisao.QUARENTENA) {
      return "ORFAO_OU_AMBIGUO";
    }
    if (item.entidadeTipo() == EntidadeTipo.KYC) {
      return "KYC";
    }
    if (item.entidadeTipo() == EntidadeTipo.EDITORIAL) {
      return "EDITORIAL";
    }
    if (item.entidadeTipo() == EntidadeTipo.REVISAO_ANUNCIO) {
      return "REVISAO";
    }
    return item.visibilidade() == Visibilidade.LIVRE ? "PUBLICO" : "RESTRITO";
  }

  public record Reconciliacao(
      long totalManifesto,
      long importaveis,
      long concluidas,
      long objetosImportaveis,
      long objetosConcluidos,
      long descartadas,
      long quarentena,
      long bloqueadas,
      long falhas,
      long interrompidas,
      long bytesImportaveis,
      long bytesValidados,
      boolean equacaoClassificacao,
      boolean equacaoExecucao,
      boolean equacaoBytes,
      Map<String, ResumoClasse> porClasse,
      boolean aprovada) {

    public boolean equacaoFechada() {
      return equacaoClassificacao && equacaoExecucao && equacaoBytes;
    }
  }

  public record ResumoClasse(
      long referencias,
      long importaveis,
      long objetosImportaveis,
      long referenciasConcluidas,
      long objetosConcluidos,
      long descartadas,
      long quarentena,
      long bytesValidados) {
  }
}
