package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.importacao.midia.GeradorChaveDestinoMidiaMigracao;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArquivoManifestoMidiaFaseCincoTest {

  private static final String ORIGEM = "TOPSDOJOB_LEGADO";
  private static final String SNAPSHOT = "a".repeat(64);
  private static final String FONTE = "b".repeat(64);
  private static final OffsetDateTime INSTANTE = OffsetDateTime.parse("2026-08-14T12:00:00Z");

  @TempDir
  Path temporario;

  @Test
  void fingerprintIgnoraCamposTecnicosEAlteraComDadoDeNegocio() {
    ObjectMapper mapper = mapper();
    ManifestoMidiaFaseCinco manifesto = manifesto("a".repeat(64));
    ArquivoManifestoMidiaFaseCinco primeiro = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM, SNAPSHOT, "execucao-a", INSTANTE, FONTE, manifesto, mapper);
    ArquivoManifestoMidiaFaseCinco segundo = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM, SNAPSHOT, "execucao-b", INSTANTE.plusHours(3), FONTE, manifesto, mapper);
    ArquivoManifestoMidiaFaseCinco alterado = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM,
        SNAPSHOT,
        "execucao-c",
        INSTANTE,
        FONTE,
        manifesto("c".repeat(64)),
        mapper);

    assertThat(segundo.fingerprint()).isEqualTo(primeiro.fingerprint());
    assertThat(alterado.fingerprint()).isNotEqualTo(primeiro.fingerprint());
  }

  @Test
  void gravaAtomicoPreservaIdenticoERecusaDivergente() {
    ObjectMapper mapper = mapper();
    ValidadorManifestoMidiaFaseCinco validador =
        new ValidadorManifestoMidiaFaseCinco(mapper, destinoProperties());
    RepositorioManifestoMidiaFaseCinco repositorio =
        new RepositorioManifestoMidiaFaseCinco(mapper);
    ArquivoManifestoMidiaFaseCinco primeiro = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM, SNAPSHOT, "execucao-a", INSTANTE, FONTE, manifesto("a".repeat(64)), mapper);
    ArquivoManifestoMidiaFaseCinco equivalente = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM,
        SNAPSHOT,
        "execucao-b",
        INSTANTE.plusMinutes(5),
        FONTE,
        manifesto("a".repeat(64)),
        mapper);
    Path destino = temporario.resolve("manifesto.json");

    var criado = repositorio.gravarAtomico(
        destino, primeiro, validador, ORIGEM, SNAPSHOT);
    var preservado = repositorio.gravarAtomico(
        destino, equivalente, validador, ORIGEM, SNAPSHOT);

    assertThat(criado.estado())
        .isEqualTo(RepositorioManifestoMidiaFaseCinco.EstadoEscrita.CRIADO);
    assertThat(preservado.estado())
        .isEqualTo(RepositorioManifestoMidiaFaseCinco.EstadoEscrita.PRESERVADO);
    assertThat(preservado.arquivoSha256()).isEqualTo(criado.arquivoSha256());
    assertThat(Files.exists(temporario.resolve("manifesto.json.partial"))).isFalse();

    ArquivoManifestoMidiaFaseCinco divergente = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM, SNAPSHOT, "execucao-c", INSTANTE, FONTE, manifesto("c".repeat(64)), mapper);
    assertThatThrownBy(() -> repositorio.gravarAtomico(
        destino, divergente, validador, ORIGEM, SNAPSHOT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nao sera sobrescrito");
  }

  @Test
  void rejeitaPartialTruncadoSchemaSnapshotEContagemDivergentes() throws Exception {
    ObjectMapper mapper = mapper();
    ValidadorManifestoMidiaFaseCinco validador =
        new ValidadorManifestoMidiaFaseCinco(mapper, destinoProperties());
    RepositorioManifestoMidiaFaseCinco repositorio =
        new RepositorioManifestoMidiaFaseCinco(mapper);
    ArquivoManifestoMidiaFaseCinco valido = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM, SNAPSHOT, "execucao-a", INSTANTE, FONTE, manifesto("a".repeat(64)), mapper);
    Path partial = temporario.resolve("manifesto.json.partial");
    mapper.writeValue(partial.toFile(), valido);
    assertThatThrownBy(() -> repositorio.carregar(partial))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parcial");

    Path truncado = temporario.resolve("truncado.json");
    Files.writeString(truncado, "{\"schemaVersion\":");
    assertThatThrownBy(() -> repositorio.carregar(truncado))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("truncado");

    ArquivoManifestoMidiaFaseCinco schema = copiar(valido, "v2", SNAPSHOT, 1);
    assertThatThrownBy(() -> validador.validar(schema, ORIGEM, SNAPSHOT))
        .hasMessageContaining("schemaVersion");
    assertThatThrownBy(() -> validador.validar(valido, ORIGEM, "d".repeat(64)))
        .hasMessageContaining("snapshot");
    ArquivoManifestoMidiaFaseCinco contagem = copiar(
        valido, valido.schemaVersion(), SNAPSHOT, 2);
    assertThatThrownBy(() -> validador.validar(contagem, ORIGEM, SNAPSHOT))
        .hasMessageContaining("quantidade");
  }

  @Test
  void rejeitaSaidaDentroDoGit() {
    ObjectMapper mapper = mapper();
    ArquivoManifestoMidiaFaseCinco arquivo = ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM, SNAPSHOT, "execucao-a", INSTANTE, FONTE, manifesto("a".repeat(64)), mapper);
    assertThatThrownBy(() -> new RepositorioManifestoMidiaFaseCinco(mapper).gravarAtomico(
        Path.of("target", "manifesto-proibido.json"),
        arquivo,
        new ValidadorManifestoMidiaFaseCinco(mapper, destinoProperties()),
        ORIGEM,
        SNAPSHOT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fora do Git");
  }

  @Test
  void rejeitaAreaBucketEChaveDeDestinoForaDoPlanejamentoCanonico() {
    ObjectMapper mapper = mapper();
    ValidadorManifestoMidiaFaseCinco validador =
        new ValidadorManifestoMidiaFaseCinco(mapper, destinoProperties());
    Item valido = manifesto("a".repeat(64)).itens().get(0);
    Item bucketInjetado = copiarItem(
        valido,
        valido.origem(),
        new Destino(valido.destino().area(), "bucket-injetado", valido.destino().chave()));
    Item chaveArbitraria = copiarItem(
        valido,
        valido.origem(),
        new Destino(
            valido.destino().area(),
            valido.destino().bucket(),
            "hml/midias-aprovadas/importacao/arbitraria.jpg"));
    Item kycComAreaPublica = new Item(
        "kyc-1",
        EntidadeTipo.KYC,
        "20",
        "kyc-v3",
        "1",
        "usuario-v3",
        "documento-1",
        Finalidade.KYC_IDENTIDADE,
        TipoMidia.DOCUMENTO,
        Visibilidade.PRIVADA,
        EstadoModeracao.APROVADA,
        true,
        false,
        0,
        "application/pdf",
        4,
        "b".repeat(64),
        new Origem(TipoOrigem.OBJECT_STORAGE, StorageArea.PUBLIC_MEDIA, "legacy/kyc.pdf"),
        new Destino(
            StorageArea.PRIVATE_DOCUMENT,
            "document-target",
            "hml/documentos/importacao/kyc.pdf"),
        Decisao.IMPORTAR,
        null);

    assertThatThrownBy(() -> validador.validar(
        artefato(mapper, new ManifestoMidiaFaseCinco(List.of(bucketInjetado))),
        ORIGEM,
        SNAPSHOT))
        .hasMessageContaining("planejamento canonico");
    assertThatThrownBy(() -> validador.validar(
        artefato(mapper, new ManifestoMidiaFaseCinco(List.of(chaveArbitraria))),
        ORIGEM,
        SNAPSHOT))
        .hasMessageContaining("planejamento canonico");
    assertThatThrownBy(() -> validador.validar(
        artefato(mapper, new ManifestoMidiaFaseCinco(List.of(kycComAreaPublica))),
        ORIGEM,
        SNAPSHOT))
        .hasMessageContaining("area de origem KYC");
  }

  private static ArquivoManifestoMidiaFaseCinco copiar(
      ArquivoManifestoMidiaFaseCinco base,
      String schema,
      String snapshot,
      long quantidade) {
    return new ArquivoManifestoMidiaFaseCinco(
        schema,
        base.origemId(),
        snapshot,
        base.execucaoId(),
        base.geradoEm(),
        quantidade,
        base.contagens(),
        base.fingerprintDadosOrigem(),
        base.manifestoSha256(),
        base.fingerprint(),
        base.manifesto());
  }

  private static ManifestoMidiaFaseCinco manifesto(String checksum) {
    Origem origem = new Origem(
        TipoOrigem.OBJECT_STORAGE, StorageArea.PUBLIC_MEDIA, "legacy/foto.jpg");
    String origemFingerprint = FingerprintMigracaoIntegral.sha256(
        "OBJECT_STORAGE|PUBLIC_MEDIA|legacy/foto.jpg".getBytes(StandardCharsets.UTF_8))
        .substring(0, 24);
    Destino destino = new GeradorChaveDestinoMidiaMigracao(destinoProperties()).gerar(
        EntidadeTipo.ANUNCIO,
        "anuncio-v3",
        "usuario-v3",
        "foto-1",
        origemFingerprint,
        Finalidade.CAPA,
        Visibilidade.LIVRE,
        checksum,
        "jpg");
    return new ManifestoMidiaFaseCinco(List.of(new Item(
        "foto-1",
        EntidadeTipo.ANUNCIO,
        "10",
        "anuncio-v3",
        "1",
        "usuario-v3",
        "foto-1",
        Finalidade.CAPA,
        TipoMidia.FOTO,
        Visibilidade.LIVRE,
        EstadoModeracao.APROVADA,
        true,
        true,
        0,
        "image/jpeg",
        4,
        checksum,
        origem,
        destino,
        Decisao.IMPORTAR,
        null)));
  }

  private static Item copiarItem(Item base, Origem origem, Destino destino) {
    return new Item(
        base.idOrigem(),
        base.entidadeTipo(),
        base.entidadeOrigemId(),
        base.entidadeV3Id(),
        base.proprietarioOrigemId(),
        base.proprietarioV3Id(),
        base.referenciaOrigemId(),
        base.finalidade(),
        base.tipoMidia(),
        base.visibilidade(),
        base.estadoModeracao(),
        base.originalExiste(),
        base.capaValida(),
        base.ordem(),
        base.mimeType(),
        base.tamanhoBytes(),
        base.sha256(),
        origem,
        destino,
        base.decisao(),
        base.motivo());
  }

  private static ArquivoManifestoMidiaFaseCinco artefato(
      ObjectMapper mapper,
      ManifestoMidiaFaseCinco manifesto) {
    return ArquivoManifestoMidiaFaseCinco.criar(
        ORIGEM, SNAPSHOT, "execucao-negativa", INSTANTE, FONTE, manifesto, mapper);
  }

  private static R2StorageProperties destinoProperties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setPublicMediaBucket("public-target");
    properties.setPrivateMediaBucket("private-target");
    properties.setDocumentBucket("document-target");
    return properties;
  }

  private static ObjectMapper mapper() {
    return new ObjectMapper().findAndRegisterModules();
  }
}
