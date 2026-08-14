package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.PlanejadorMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.PlanejadorMidiaFaseCinco.Candidato;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.importacao.midia.GeradorChaveDestinoMidiaMigracao;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ValidadorManifestoMidiaFaseCinco {

  private final ObjectMapper mapper;
  private final PlanejadorMidiaFaseCinco planejador;

  public ValidadorManifestoMidiaFaseCinco(
      ObjectMapper mapper,
      R2StorageProperties destinoProperties) {
    this.mapper = mapper;
    this.planejador = new PlanejadorMidiaFaseCinco(
        new GeradorChaveDestinoMidiaMigracao(
            Objects.requireNonNull(destinoProperties, "destinoProperties obrigatorio")));
  }

  public void validar(
      ArquivoManifestoMidiaFaseCinco arquivo,
      String origemEsperada,
      String snapshotEsperado) {
    if (arquivo == null) {
      throw new IllegalArgumentException("arquivo do manifesto deve ser informado");
    }
    if (!ArquivoManifestoMidiaFaseCinco.SCHEMA_VERSION.equals(arquivo.schemaVersion())) {
      throw new IllegalArgumentException("schemaVersion do manifesto nao e suportada");
    }
    if (origemEsperada == null || !arquivo.origemId().equals(origemEsperada.trim())) {
      throw new IllegalArgumentException("origem do manifesto diverge da origem declarada");
    }
    if (snapshotEsperado == null
        || !arquivo.snapshotSha256().equals(snapshotEsperado.trim().toLowerCase())) {
      throw new IllegalArgumentException("snapshot do manifesto diverge da fotografia declarada");
    }
    if (arquivo.quantidadeEntradas() != arquivo.manifesto().itens().size()) {
      throw new IllegalArgumentException("quantidade do manifesto diverge do conteudo");
    }
    Map<String, Long> contagens = ArquivoManifestoMidiaFaseCinco.contagens(
        arquivo.manifesto());
    if (!contagens.equals(arquivo.contagens())) {
      throw new IllegalArgumentException("contagens do manifesto divergem do conteudo");
    }
    if (!arquivo.manifesto().sha256().equals(arquivo.manifestoSha256())) {
      throw new IllegalArgumentException("fingerprint logico do manifesto diverge do conteudo");
    }
    if (!ArquivoManifestoMidiaFaseCinco.fingerprint(mapper, arquivo)
        .equals(arquivo.fingerprint())) {
      throw new IllegalArgumentException("fingerprint do artefato de manifesto divergiu");
    }
    validarItens(arquivo.manifesto());
  }

  private void validarItens(ManifestoMidiaFaseCinco manifesto) {
    Set<String> ids = new HashSet<>();
    for (ManifestoMidiaFaseCinco.Item item : manifesto.itens()) {
      if (!ids.add(item.idOrigem())) {
        throw new IllegalArgumentException("id duplicado no manifesto de midia");
      }
      validarChave(item.origem().localizador(), "origem");
      if (item.origem().tipo() == TipoOrigem.OBJECT_STORAGE && item.origem().area() == null) {
        throw new IllegalArgumentException("midia R2 sem area de origem");
      }
      if (item.decisao() == Decisao.IMPORTAR) {
        if (item.destino() == null) {
          throw new IllegalArgumentException("midia importavel sem destino");
        }
        validarChave(item.destino().chave(), "destino");
        if (!item.destino().chave().startsWith("hml/")) {
          throw new IllegalArgumentException("chave de destino fora do prefixo V3");
        }
        if (item.sha256() == null || !item.sha256().matches("[0-9a-f]{64}")) {
          throw new IllegalArgumentException("midia importavel sem checksum valido");
        }
        validarDestinoCanonico(item);
      } else if (item.motivo() == null || item.motivo().isBlank()) {
        throw new IllegalArgumentException("midia nao importavel sem motivo");
      }
    }
  }

  private void validarDestinoCanonico(ManifestoMidiaFaseCinco.Item item) {
    if (item.entidadeTipo() == EntidadeTipo.KYC
        && item.origem().area() != StorageArea.PRIVATE_DOCUMENT) {
      throw new IllegalArgumentException("area de origem KYC diverge da fonte canonica");
    }
    String chave = item.destino().chave();
    int separadorExtensao = chave.lastIndexOf('.');
    if (separadorExtensao < 0 || separadorExtensao == chave.length() - 1) {
      throw new IllegalArgumentException("chave de destino sem extensao canonica");
    }
    String origemCanonica = item.origem().tipo() + "|"
        + (item.origem().area() == null ? "" : item.origem().area().name()) + "|"
        + item.origem().localizador();
    String fingerprintOrigem = FingerprintMigracaoIntegral.sha256(
        origemCanonica.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    Candidato candidato = new Candidato(
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
        true,
        item.originalExiste(),
        item.capaValida(),
        item.ordem(),
        item.mimeType(),
        item.tamanhoBytes(),
        item.sha256(),
        chave.substring(separadorExtensao + 1),
        item.origem());
    ManifestoMidiaFaseCinco.Item esperado = planejador.planejar(List.of(candidato))
        .itens().get(0);
    if (!esperado.equals(item)) {
      throw new IllegalArgumentException(
          "destino ou decisao da midia diverge do planejamento canonico");
    }
  }

  static void validarChave(String chave, String tipo) {
    if (chave == null || chave.isBlank()
        || chave.startsWith("/")
        || chave.contains("://")
        || chave.contains("?")
        || chave.contains("#")
        || chave.contains("\\")
        || chave.indexOf('\0') >= 0
        || chave.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("chave de " + tipo + " invalida");
    }
    for (String segmento : chave.split("/", -1)) {
      if (segmento.isBlank() || ".".equals(segmento) || "..".equals(segmento)) {
        throw new IllegalArgumentException("chave de " + tipo + " invalida");
      }
    }
  }
}
