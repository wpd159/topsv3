package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.DocumentoKycLegado;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PoliticaKycMigracaoIntegral {

  public Consolidacao consolidar(
      List<DocumentoKycLegado> documentos,
      ManifestoMidiaFaseCinco manifesto) {
    List<DocumentoKycLegado> entrada = List.copyOf(
        documentos == null ? List.of() : documentos);
    Map<String, Item> itens = manifesto.itens().stream()
        .filter(item -> item.entidadeTipo() == EntidadeTipo.KYC)
        .collect(Collectors.toMap(Item::idOrigem, Function.identity(), (a, b) -> a));
    Set<String> referenciasCruzadas = referenciasCruzadas(entrada, itens);
    Set<String> usuariosAfetados = usuariosAfetados(
        entrada, itens, referenciasCruzadas);
    Map<String, String> codigosQuarentena = new LinkedHashMap<>();
    List<DocumentoKycLegado> quarentena = entrada.stream()
        .filter(documento -> {
          String codigo = codigoQuarentena(
              documento, itens, referenciasCruzadas, usuariosAfetados);
          if (codigo != null) {
            codigosQuarentena.put(documento.idOrigem(), codigo);
          }
          return codigo != null;
        })
        .sorted(ordemDocumento())
        .toList();
    Set<String> idsQuarentena = quarentena.stream()
        .map(DocumentoKycLegado::idOrigem)
        .collect(Collectors.toSet());
    List<DocumentoKycLegado> elegiveis = entrada.stream()
        .filter(documento -> !idsQuarentena.contains(documento.idOrigem()))
        .toList();

    List<DocumentoKycLegado> canonicos = new ArrayList<>();
    List<DocumentoKycLegado> historicos = new ArrayList<>();
    Map<ChaveDocumento, List<DocumentoKycLegado>> grupos = elegiveis.stream()
        .collect(Collectors.groupingBy(
            documento -> new ChaveDocumento(documento.usuarioOrigemId(), documento.tipo()),
            LinkedHashMap::new,
            Collectors.toList()));
    grupos.values().forEach(grupo -> selecionar(grupo, itens, canonicos, historicos));
    canonicos.sort(ordemDocumento());
    historicos.sort(ordemDocumento());

    return new Consolidacao(
        List.copyOf(canonicos),
        List.copyOf(historicos),
        quarentena,
        Set.copyOf(usuariosAfetados),
        Map.copyOf(codigosQuarentena),
        filtrarManifesto(manifesto, referenciasCruzadas, usuariosAfetados));
  }

  private void selecionar(
      List<DocumentoKycLegado> grupo,
      Map<String, Item> itens,
      List<DocumentoKycLegado> canonicos,
      List<DocumentoKycLegado> historicos) {
    Map<String, List<DocumentoKycLegado>> porEnvio = grupo.stream()
        .collect(Collectors.groupingBy(
            DocumentoKycLegado::envioOrigemId,
            LinkedHashMap::new,
            Collectors.toList()));
    String envioCanonico = porEnvio.entrySet().stream()
        .max(Comparator
            .comparingInt((Map.Entry<String, List<DocumentoKycLegado>> entry) ->
                prioridadeEnvio(entry.getValue()))
            .thenComparing(entry -> dataEnvio(entry.getValue()))
            .thenComparing(entry -> checksumEnvio(entry.getValue(), itens))
            .thenComparing(Map.Entry::getKey))
        .orElseThrow()
        .getKey();
    Map<String, DocumentoKycLegado> porParte = new LinkedHashMap<>();
    porEnvio.get(envioCanonico).stream()
        .sorted(ordemDocumento().reversed())
        .forEach(documento -> porParte.putIfAbsent(documento.parte(), documento));
    canonicos.addAll(porParte.values());
    Set<String> idsCanonicos = porParte.values().stream()
        .map(DocumentoKycLegado::idOrigem)
        .collect(Collectors.toSet());
    grupo.stream()
        .filter(documento -> !idsCanonicos.contains(documento.idOrigem()))
        .forEach(historicos::add);
  }

  private Set<String> referenciasCruzadas(
      List<DocumentoKycLegado> documentos,
      Map<String, Item> itens) {
    Map<String, Set<String>> proprietarios = new HashMap<>();
    for (DocumentoKycLegado documento : documentos) {
      proprietarios.computeIfAbsent(documento.manifestoItemId(), ignored -> new LinkedHashSet<>())
          .add(documento.usuarioOrigemId());
      Item item = itens.get(documento.manifestoItemId());
      if (item != null && item.proprietarioOrigemId() != null) {
        proprietarios.get(documento.manifestoItemId()).add(item.proprietarioOrigemId());
      }
    }
    return proprietarios.entrySet().stream()
        .filter(entry -> entry.getValue().size() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> usuariosAfetados(
      List<DocumentoKycLegado> documentos,
      Map<String, Item> itens,
      Set<String> referenciasCruzadas) {
    Set<String> usuarios = new LinkedHashSet<>();
    documentos.stream()
        .filter(documento -> referenciasCruzadas.contains(documento.manifestoItemId()))
        .map(DocumentoKycLegado::usuarioOrigemId)
        .forEach(usuarios::add);
    referenciasCruzadas.stream()
        .map(itens::get)
        .filter(java.util.Objects::nonNull)
        .map(Item::proprietarioOrigemId)
        .filter(java.util.Objects::nonNull)
        .forEach(usuarios::add);
    return Set.copyOf(usuarios);
  }

  private String codigoQuarentena(
      DocumentoKycLegado documento,
      Map<String, Item> itens,
      Set<String> referenciasCruzadas,
      Set<String> usuariosAfetados) {
    Item item = itens.get(documento.manifestoItemId());
    if (referenciasCruzadas.contains(documento.manifestoItemId())) {
      return "KYC_OWNERSHIP_CRUZADO";
    }
    if (usuariosAfetados.contains(documento.usuarioOrigemId())) {
      return "KYC_USUARIO_AFETADO_VINCULO_CRUZADO";
    }
    if (item == null) {
      return "KYC_MANIFESTO_AUSENTE";
    }
    if (item.decisao() != Decisao.IMPORTAR) {
      return "KYC_MIDIA_NAO_IMPORTAVEL";
    }
    if (item.proprietarioOrigemId() == null
        || !item.proprietarioOrigemId().equals(documento.usuarioOrigemId())) {
      return "KYC_OWNERSHIP_DIVERGENTE";
    }
    return null;
  }

  private ManifestoMidiaFaseCinco filtrarManifesto(
      ManifestoMidiaFaseCinco manifesto,
      Set<String> referenciasCruzadas,
      Set<String> usuariosAfetados) {
    List<Item> itens = manifesto.itens().stream()
        .map(item -> item.entidadeTipo() == EntidadeTipo.KYC
            && item.decisao() == Decisao.IMPORTAR
            && (referenciasCruzadas.contains(item.idOrigem())
                || usuariosAfetados.contains(item.proprietarioOrigemId()))
                    ? quarentena(
                        item,
                        referenciasCruzadas.contains(item.idOrigem())
                            ? "KYC_OWNERSHIP_CRUZADO"
                            : "KYC_USUARIO_AFETADO_VINCULO_CRUZADO")
                    : item)
        .toList();
    return new ManifestoMidiaFaseCinco(itens);
  }

  private Item quarentena(Item item, String motivo) {
    return new Item(
        item.idOrigem(),
        item.entidadeTipo(),
        item.entidadeOrigemId(),
        item.entidadeV3Id(),
        item.proprietarioOrigemId(),
        item.proprietarioV3Id(),
        item.referenciaOrigemId(),
        item.finalidade(),
        item.tipoMidia(),
        item.visibilidade(),
        item.estadoModeracao(),
        item.originalExiste(),
        item.capaValida(),
        item.ordem(),
        item.mimeType(),
        item.tamanhoBytes(),
        item.sha256(),
        item.origem(),
        null,
        Decisao.QUARENTENA,
        motivo);
  }

  private int prioridadeEnvio(List<DocumentoKycLegado> documentos) {
    if (documentos.stream().allMatch(item -> "VALIDADO".equals(item.status()))) {
      return 5;
    }
    if (documentos.stream().anyMatch(item -> "EM_ANALISE".equals(item.status()))) {
      return 4;
    }
    if (documentos.stream().anyMatch(item -> "PENDENTE".equals(item.status()))) {
      return 3;
    }
    if (documentos.stream().anyMatch(item -> "AJUSTE_SOLICITADO".equals(item.status()))) {
      return 2;
    }
    if (documentos.stream().anyMatch(item -> "REJEITADO".equals(item.status()))) {
      return 1;
    }
    return 0;
  }

  private OffsetDateTime dataEnvio(List<DocumentoKycLegado> documentos) {
    return documentos.stream()
        .map(DocumentoKycLegado::atualizadoEm)
        .max(Comparator.naturalOrder())
        .orElse(OffsetDateTime.MIN);
  }

  private String checksumEnvio(
      List<DocumentoKycLegado> documentos,
      Map<String, Item> itens) {
    return documentos.stream()
        .map(DocumentoKycLegado::manifestoItemId)
        .map(itens::get)
        .filter(java.util.Objects::nonNull)
        .map(Item::sha256)
        .filter(java.util.Objects::nonNull)
        .sorted()
        .collect(Collectors.joining("|"));
  }

  private Comparator<DocumentoKycLegado> ordemDocumento() {
    return Comparator.comparing(DocumentoKycLegado::usuarioOrigemId)
        .thenComparing(DocumentoKycLegado::tipo)
        .thenComparing(DocumentoKycLegado::envioOrigemId)
        .thenComparing(DocumentoKycLegado::parte)
        .thenComparing(DocumentoKycLegado::atualizadoEm)
        .thenComparing(DocumentoKycLegado::idOrigem);
  }

  private record ChaveDocumento(String usuarioOrigemId, String tipo) {
  }

  public record Consolidacao(
      List<DocumentoKycLegado> canonicos,
      List<DocumentoKycLegado> historicosConsolidados,
      List<DocumentoKycLegado> quarentena,
      Set<String> usuariosAfetados,
      Map<String, String> codigosQuarentena,
      ManifestoMidiaFaseCinco manifestoSeguro) {

    public Consolidacao {
      canonicos = List.copyOf(canonicos);
      historicosConsolidados = List.copyOf(historicosConsolidados);
      quarentena = List.copyOf(quarentena);
      usuariosAfetados = Set.copyOf(usuariosAfetados);
      codigosQuarentena = Map.copyOf(codigosQuarentena);
    }

    public String codigoQuarentena(DocumentoKycLegado documento) {
      return codigosQuarentena.getOrDefault(
          documento.idOrigem(), "KYC_ESTRUTURA_INVALIDA");
    }
  }
}
