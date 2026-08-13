package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class AdaptadorManifestoMidiaFasesAnteriores {

  public ManifestoMidiaAnuncios paraFaseUm(ManifestoMidiaFaseCinco manifesto) {
    List<ManifestoMidiaAnuncios.Item> items = manifesto.itens().stream()
        .filter(item -> item.entidadeTipo() == EntidadeTipo.ANUNCIO
            || item.entidadeTipo() == EntidadeTipo.REVISAO_ANUNCIO)
        .map(this::paraAnuncio)
        .toList();
    return new ManifestoMidiaAnuncios(items);
  }

  public List<MidiaEditorialPlanejada> editoriaisParaFaseDois(
      ManifestoMidiaFaseCinco manifesto) {
    return manifesto.itens().stream()
        .filter(item -> item.entidadeTipo() == EntidadeTipo.EDITORIAL)
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .filter(item -> item.destino().area() == StorageArea.PUBLIC_MEDIA)
        .map(item -> new MidiaEditorialPlanejada(
            uuid("editorial", item.idOrigem()),
            item.entidadeOrigemId(),
            item.finalidade(),
            item.destino().bucket(),
            item.destino().chave(),
            item.destino().area() == StorageArea.PUBLIC_MEDIA))
        .toList();
  }

  public List<DocumentoKycPlanejado> documentosKyc(ManifestoMidiaFaseCinco manifesto) {
    return manifesto.itens().stream()
        .filter(item -> item.entidadeTipo() == EntidadeTipo.KYC)
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .map(item -> new DocumentoKycPlanejado(
            uuid("documento-kyc", item.idOrigem()),
            item.proprietarioV3Id(),
            item.finalidade(),
            item.destino().bucket(),
            item.destino().chave(),
            item.sha256(),
            item.tamanhoBytes(),
            item.mimeType()))
        .toList();
  }

  private ManifestoMidiaAnuncios.Item paraAnuncio(Item item) {
    boolean imported = item.decisao() == Decisao.IMPORTAR;
    boolean publicValid = imported
        && item.estadoModeracao() == EstadoModeracao.APROVADA
        && SeteFinalidades.publicavel(item.finalidade());
    return new ManifestoMidiaAnuncios.Item(
        item.idOrigem(),
        item.entidadeOrigemId(),
        item.referenciaOrigemId(),
        item.originalExiste(),
        publicValid,
        item.finalidade() != Finalidade.CAPA || item.capaValida(),
        !imported,
        item.estadoModeracao().name(),
        "ANUNCIO",
        item.finalidade().name(),
        item.tipoMidia() == TipoMidia.VIDEO ? "VIDEO" : "FOTO",
        imported ? item.destino().bucket() : null,
        imported ? item.destino().chave() : null,
        item.mimeType(),
        item.tamanhoBytes(),
        item.ordem(),
        item.visibilidade() == Visibilidade.PRIVADA
            ? Visibilidade.RESTRITA_18.name()
            : item.visibilidade().name());
  }

  private static UUID uuid(String namespace, String sourceId) {
    return UUID.nameUUIDFromBytes(
        (namespace + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
  }

  public record MidiaEditorialPlanejada(
      UUID idV3,
      String conteudoOrigemId,
      Finalidade finalidade,
      String bucket,
      String chave,
      boolean publica) {
  }

  public record DocumentoKycPlanejado(
      UUID idV3,
      String usuarioV3Id,
      Finalidade finalidade,
      String bucket,
      String chave,
      String sha256,
      long tamanhoBytes,
      String mimeType) {
  }

  private static final class SeteFinalidades {

    private SeteFinalidades() {
    }

    private static boolean publicavel(Finalidade finalidade) {
      return finalidade == Finalidade.CAPA
          || finalidade == Finalidade.GALERIA
          || finalidade == Finalidade.VIDEO;
    }
  }
}
