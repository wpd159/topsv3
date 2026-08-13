package br.com.topsdojob.v3.importacao.midia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.importacao.midia.PlanejadorMidiaFaseCinco.Candidato;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestoMidiaFaseCincoTest {

  private static final String CHECKSUM = "a".repeat(64);

  private final R2StorageProperties properties = properties();
  private final PlanejadorMidiaFaseCinco planner = new PlanejadorMidiaFaseCinco(
      new GeradorChaveDestinoMidiaMigracao(properties));

  @Test
  void classificaSomenteEntidadesPermitidasComPrivacidadeEOwnershipComprovados() {
    Candidato publicPhoto = candidate(
        "foto-1", EntidadeTipo.ANUNCIO, Finalidade.CAPA, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg");
    Candidato pendingRevision = candidate(
        "revisao-1", EntidadeTipo.REVISAO_ANUNCIO, Finalidade.REVISAO, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.PENDENTE, "image/jpeg", 100, "jpg");
    Candidato kyc = candidate(
        "kyc-1", EntidadeTipo.KYC, Finalidade.KYC_IDENTIDADE, TipoMidia.DOCUMENTO,
        Visibilidade.PRIVADA, EstadoModeracao.NAO_APLICAVEL, "application/pdf", 100, "pdf");
    Candidato editorial = candidate(
        "editorial-1", EntidadeTipo.EDITORIAL, Finalidade.BLOG_CAPA, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/png", 100, "png");
    Candidato restricted = candidate(
        "restrita-1", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.RESTRITA_18, EstadoModeracao.APROVADA, "image/webp", 100, "webp");
    Candidato video = candidate(
        "video-1", EntidadeTipo.ANUNCIO, Finalidade.VIDEO, TipoMidia.VIDEO,
        Visibilidade.RESTRITA_18, EstadoModeracao.APROVADA, "video/mp4", 100, "mp4");

    ManifestoMidiaFaseCinco manifest = planner.planejar(
        List.of(kyc, pendingRevision, editorial, publicPhoto, restricted, video));

    assertThat(manifest.itens()).hasSize(6).allMatch(item -> item.decisao() == Decisao.IMPORTAR);
    assertThat(item(manifest, "foto-1").destino().area()).isEqualTo(StorageArea.PUBLIC_MEDIA);
    assertThat(item(manifest, "revisao-1").destino().area()).isEqualTo(StorageArea.PRIVATE_MEDIA);
    assertThat(item(manifest, "revisao-1").visibilidade()).isEqualTo(Visibilidade.LIVRE);
    assertThat(item(manifest, "kyc-1").destino().area()).isEqualTo(StorageArea.PRIVATE_DOCUMENT);
    assertThat(item(manifest, "editorial-1").destino().area()).isEqualTo(StorageArea.PUBLIC_MEDIA);
    assertThat(item(manifest, "restrita-1").destino().area()).isEqualTo(StorageArea.PRIVATE_MEDIA);
    assertThat(item(manifest, "video-1").destino().area()).isEqualTo(StorageArea.PRIVATE_MEDIA);
    assertThat(manifest.itens()).allSatisfy(item -> {
      assertThat(item.destino().chave()).startsWith(switch (item.destino().area()) {
        case PUBLIC_MEDIA -> properties.getPublicMediaPrefix();
        case PRIVATE_MEDIA -> properties.getPrivateMediaPrefix();
        case PRIVATE_DOCUMENT -> properties.getDocumentPrefix();
      });
      assertThat(item.destino().chave())
          .doesNotContain("usuario-origem", "usuario-v3", "anuncio-origem", "anuncio-v3");
    });
  }

  @Test
  void descartaEscoposProibidosEDerivadosSemPromoverPreviewAOriginal() {
    List<Candidato> candidates = new ArrayList<>();
    for (EntidadeTipo type : List.of(
        EntidadeTipo.STORY,
        EntidadeTipo.SUPORTE,
        EntidadeTipo.DENUNCIA,
        EntidadeTipo.COMPLIANCE,
        EntidadeTipo.VISITANTE_COMPLIANCE,
        EntidadeTipo.WIZARD,
        EntidadeTipo.QA,
        EntidadeTipo.TEMPORARIO)) {
      candidates.add(candidate(
          type.name(), type, Finalidade.TEMPORARIO, TipoMidia.FOTO,
          Visibilidade.PRIVADA, EstadoModeracao.NAO_APLICAVEL,
          "image/jpeg", 100, "jpg"));
    }
    candidates.add(candidate(
        "preview", EntidadeTipo.ANUNCIO, Finalidade.PREVIEW, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"));
    candidates.add(candidate(
        "thumb", EntidadeTipo.ANUNCIO, Finalidade.THUMBNAIL, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"));

    ManifestoMidiaFaseCinco manifest = planner.planejar(candidates);

    assertThat(manifest.itens()).hasSize(10).allMatch(item -> item.decisao() == Decisao.DESCARTAR);
    assertThat(manifest.itens()).allSatisfy(item -> assertThat(item.destino()).isNull());
  }

  @Test
  void quarentenaAmbiguidadesObjetosAusentesEArquivosIncompativeis() {
    Candidato noReference = withReference(candidate(
        "sem-ref", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"), false);
    Candidato noOwner = withOwner(candidate(
        "sem-owner", EntidadeTipo.KYC, Finalidade.KYC_IDENTIDADE, TipoMidia.DOCUMENTO,
        Visibilidade.PRIVADA, EstadoModeracao.NAO_APLICAVEL, "application/pdf", 100, "pdf"), null);
    Candidato noOriginal = withOriginal(candidate(
        "sem-original", EntidadeTipo.EDITORIAL, Finalidade.BLOG_OG, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/png", 100, "png"), false);
    Candidato badMime = candidate(
        "mime", EntidadeTipo.ANUNCIO, Finalidade.VIDEO, TipoMidia.VIDEO,
        Visibilidade.RESTRITA_18, EstadoModeracao.APROVADA, "text/plain", 100, "txt");
    Candidato badExtension = candidate(
        "extensao", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "png");
    Candidato orphanLocal = withReference(withOrigin(candidate(
        "local-sem-ref", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"),
        new Origem(TipoOrigem.ARQUIVO_LOCAL, null, "arquivo-local.jpg")), false);

    ManifestoMidiaFaseCinco manifest = planner.planejar(
        List.of(noReference, noOwner, noOriginal, badMime, badExtension, orphanLocal));

    assertThat(manifest.itens()).allMatch(item -> item.decisao() == Decisao.QUARENTENA);
    assertThat(manifest.itens()).extracting(ManifestoMidiaFaseCinco.Item::motivo)
        .containsExactlyInAnyOrder(
            "SEM_REFERENCIA_CANONICA",
            "OWNERSHIP_NAO_COMPROVADO",
            "ORIGINAL_AUSENTE",
            "MIME_INCOMPATIVEL",
            "EXTENSAO_INCOMPATIVEL",
            "SEM_REFERENCIA_CANONICA");
  }

  @Test
  void manifestoEChavesSaoDeterministicosEDeduplicacaoNaoCruzaProprietarios() {
    Candidato firstOwner = candidate(
        "mesmo-conteudo-1", EntidadeTipo.KYC, Finalidade.KYC_IDADE, TipoMidia.DOCUMENTO,
        Visibilidade.PRIVADA, EstadoModeracao.NAO_APLICAVEL, "image/jpeg", 100, "jpg");
    Candidato secondOwner = withOwner(candidate(
        "mesmo-conteudo-2", EntidadeTipo.KYC, Finalidade.KYC_IDADE, TipoMidia.DOCUMENTO,
        Visibilidade.PRIVADA, EstadoModeracao.NAO_APLICAVEL, "image/jpeg", 100, "jpg"),
        "outro-usuario-v3");

    ManifestoMidiaFaseCinco forward = planner.planejar(List.of(firstOwner, secondOwner));
    ManifestoMidiaFaseCinco reversed = planner.planejar(List.of(secondOwner, firstOwner));

    assertThat(forward.sha256()).isEqualTo(reversed.sha256());
    assertThat(forward.itens()).extracting(item -> item.destino().chave()).doesNotHaveDuplicates();
  }

  @Test
  void mesmaOrigemFisicaComDuasReferenciasUsaUmDestinoSemFundirOrigensDistintas() {
    Origem shared = new Origem(
        TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_MEDIA, "origem/compartilhada.jpg");
    Candidato firstReference = withOrigin(candidate(
        "ref-1", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.RESTRITA_18, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"), shared);
    Candidato secondReference = withOrigin(candidate(
        "ref-2", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.RESTRITA_18, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"), shared);
    Candidato distinctObject = withOrigin(candidate(
        "ref-3", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.RESTRITA_18, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"),
        new Origem(
            TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_MEDIA, "origem/distinta.jpg"));

    ManifestoMidiaFaseCinco manifest = planner.planejar(
        List.of(firstReference, secondReference, distinctObject));

    assertThat(item(manifest, "ref-1").destino().chave())
        .isEqualTo(item(manifest, "ref-2").destino().chave())
        .isNotEqualTo(item(manifest, "ref-3").destino().chave());
  }

  @Test
  void adaptaAnunciosKycEEditorialSemPerderReferenciasDasFasesAnteriores() {
    Candidato privateEditorial = candidate(
        "blog-privado", EntidadeTipo.EDITORIAL, Finalidade.EDITORIAL, TipoMidia.FOTO,
        Visibilidade.PRIVADA, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg");
    Candidato invalidAd = withOriginal(candidate(
        "foto-ausente", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
        Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"), false);
    ManifestoMidiaFaseCinco manifest = planner.planejar(List.of(
        candidate("foto", EntidadeTipo.ANUNCIO, Finalidade.CAPA, TipoMidia.FOTO,
            Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/jpeg", 100, "jpg"),
        candidate("kyc", EntidadeTipo.KYC, Finalidade.KYC_IDENTIDADE, TipoMidia.DOCUMENTO,
            Visibilidade.PRIVADA, EstadoModeracao.NAO_APLICAVEL, "application/pdf", 100, "pdf"),
        candidate("blog", EntidadeTipo.EDITORIAL, Finalidade.BLOG_OG, TipoMidia.FOTO,
            Visibilidade.LIVRE, EstadoModeracao.APROVADA, "image/png", 100, "png"),
        privateEditorial,
        invalidAd));
    AdaptadorManifestoMidiaFasesAnteriores adapter =
        new AdaptadorManifestoMidiaFasesAnteriores();

    assertThat(adapter.paraFaseUm(manifest).itens()).hasSize(2);
    assertThat(adapter.paraFaseUm(manifest).itensPublicosValidos("anuncio-origem"))
        .singleElement()
        .satisfies(item -> assertThat(item.referenciaOrigemId()).isEqualTo("referencia-foto"));
    assertThat(adapter.documentosKyc(manifest)).singleElement()
        .satisfies(item -> assertThat(item.usuarioV3Id()).isEqualTo("usuario-v3"));
    assertThat(adapter.editoriaisParaFaseDois(manifest)).singleElement()
        .satisfies(item -> assertThat(item.publica()).isTrue());
  }

  private ManifestoMidiaFaseCinco.Item item(ManifestoMidiaFaseCinco manifest, String id) {
    return manifest.itens().stream().filter(value -> id.equals(value.idOrigem())).findFirst().orElseThrow();
  }

  private Candidato candidate(
      String id,
      EntidadeTipo entityType,
      Finalidade purpose,
      TipoMidia mediaType,
      Visibilidade visibility,
      EstadoModeracao moderation,
      String mime,
      long size,
      String extension) {
    return new Candidato(
        id,
        entityType,
        "anuncio-origem",
        "anuncio-v3",
        "usuario-origem",
        "usuario-v3",
        "referencia-" + id,
        purpose,
        mediaType,
        visibility,
        moderation,
        true,
        true,
        true,
        1,
        mime,
        size,
        CHECKSUM,
        extension,
        new Origem(TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_MEDIA, "origem/" + id));
  }

  private Candidato withReference(Candidato source, boolean referenced) {
    return copy(source, source.proprietarioV3Id(), referenced, source.originalExiste());
  }

  private Candidato withOwner(Candidato source, String owner) {
    return copy(source, owner, source.referenciada(), source.originalExiste());
  }

  private Candidato withOriginal(Candidato source, boolean exists) {
    return copy(source, source.proprietarioV3Id(), source.referenciada(), exists);
  }

  private Candidato withOrigin(Candidato source, Origem origin) {
    return new Candidato(
        source.idOrigem(), source.entidadeTipo(), source.entidadeOrigemId(), source.entidadeV3Id(),
        source.proprietarioOrigemId(), source.proprietarioV3Id(), source.referenciaOrigemId(),
        source.finalidade(), source.tipoMidia(), source.visibilidade(), source.estadoModeracao(),
        source.referenciada(), source.originalExiste(), source.capaValida(), source.ordem(),
        source.mimeType(), source.tamanhoBytes(), source.sha256(), source.extensao(), origin);
  }

  private Candidato copy(Candidato source, String owner, boolean referenced, boolean originalExists) {
    return new Candidato(
        source.idOrigem(), source.entidadeTipo(), source.entidadeOrigemId(), source.entidadeV3Id(),
        source.proprietarioOrigemId(), owner, source.referenciaOrigemId(), source.finalidade(),
        source.tipoMidia(), source.visibilidade(), source.estadoModeracao(), referenced, originalExists,
        source.capaValida(), source.ordem(), source.mimeType(), source.tamanhoBytes(), source.sha256(),
        source.extensao(), source.origem());
  }

  private static R2StorageProperties properties() {
    R2StorageProperties value = new R2StorageProperties();
    value.setPublicMediaBucket("bucket-publico-sintetico");
    value.setPrivateMediaBucket("bucket-privado-sintetico");
    value.setDocumentBucket("bucket-documentos-sintetico");
    value.setPublicMediaPrefix("hml/midias-aprovadas/");
    value.setPrivateMediaPrefix("hml/midias-pendentes/");
    value.setDocumentPrefix("hml/documentos/");
    return value;
  }
}
