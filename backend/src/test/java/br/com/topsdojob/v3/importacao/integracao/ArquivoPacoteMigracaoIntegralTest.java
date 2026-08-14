package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArquivoPacoteMigracaoIntegralTest {

  private static final String SNAPSHOT = "a".repeat(64);
  private static final String ORIGEM = "TOPSDOJOB_LEGADO";
  private static final OffsetDateTime CAPTURADO_EM =
      OffsetDateTime.parse("2026-08-01T09:00:00-03:00");
  private static final UUID ATOR = UUID.fromString("70000000-0000-4000-8000-000000000001");

  @TempDir
  Path temporario;

  @Test
  void fingerprintIgnoraTimestampTecnicoEEscritaAtomicaPreservaPacoteIdentico() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path manifestos = Files.createDirectory(temporario.resolve("manifestos"));
    Path faseCinco = manifestos.resolve("fase-5.json");
    gravarManifesto(mapper, faseCinco, new ManifestoMidiaFaseCinco(List.of()));
    ValidadorPacoteMigracaoIntegral validador = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties());
    Map<String, String> hashes = validador.calcularHashesManifestos(manifestos);
    PacoteMigracaoIntegral pacote = pacote("execucao-a");
    ArquivoPacoteMigracaoIntegral primeiro = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        hashes,
        CAPTURADO_EM,
        pacote,
        mapper);
    ArquivoPacoteMigracaoIntegral segundo = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        hashes,
        CAPTURADO_EM.plusHours(2),
        pacote,
        mapper);

    assertThat(segundo.fingerprint()).isEqualTo(primeiro.fingerprint());

    RepositorioPacoteMigracaoIntegral repositorio =
        new RepositorioPacoteMigracaoIntegral(mapper);
    assertThatThrownBy(() -> repositorio.gravarAtomico(
        Path.of("target", "pacote-proibido.json"),
        primeiro,
        validador,
        ORIGEM,
        manifestos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fora do Git");
    Path destino = temporario.resolve("pacote.json");
    var criado = repositorio.gravarAtomico(
        destino, primeiro, validador, ORIGEM, manifestos);
    var preservado = repositorio.gravarAtomico(
        destino, segundo, validador, ORIGEM, manifestos);

    assertThat(criado.estado())
        .isEqualTo(RepositorioPacoteMigracaoIntegral.EstadoEscrita.CRIADO);
    assertThat(preservado.estado())
        .isEqualTo(RepositorioPacoteMigracaoIntegral.EstadoEscrita.PRESERVADO);
    assertThat(preservado.arquivoSha256()).isEqualTo(criado.arquivoSha256());
    ArquivoPacoteMigracaoIntegral relido = repositorio.carregar(destino);
    assertThat(relido.fingerprint()).isEqualTo(primeiro.fingerprint());
    assertThat(relido.pacote().base().usuarios().get(0).papeis())
        .containsExactly("ADMIN", "USUARIO");
    try (var arquivos = Files.list(temporario)) {
      assertThat(arquivos.map(path -> path.getFileName().toString()).toList())
          .noneMatch(nome -> nome.endsWith(".tmp"));
    }

    ArquivoPacoteMigracaoIntegral divergente = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        hashes,
        CAPTURADO_EM,
        pacote("execucao-b"),
        mapper);
    assertThatThrownBy(() -> repositorio.gravarAtomico(
        destino, divergente, validador, ORIGEM, manifestos))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nao sera sobrescrito");
  }

  @Test
  void rejeitaTemporarioNoLeitorPublicoENoDestinoFinal() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path manifestos = Files.createDirectory(temporario.resolve("manifestos-temporario"));
    Path faseCinco = manifestos.resolve("fase-5.json");
    gravarManifesto(mapper, faseCinco, new ManifestoMidiaFaseCinco(List.of()));
    ValidadorPacoteMigracaoIntegral validador = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties());
    ArquivoPacoteMigracaoIntegral arquivo = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        validador.calcularHashesManifestos(manifestos),
        CAPTURADO_EM,
        pacote("execucao-temporaria"),
        mapper);
    RepositorioPacoteMigracaoIntegral repositorio =
        new RepositorioPacoteMigracaoIntegral(mapper);
    Path pacoteTmp = temporario.resolve("pacote.tmp");
    Path pacotePartial = temporario.resolve("pacote.partial");
    byte[] conteudo = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(arquivo);
    Files.write(pacoteTmp, conteudo);
    Files.write(pacotePartial, conteudo);

    assertThatThrownBy(() -> repositorio.carregar(pacoteTmp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("temporario");
    assertThatThrownBy(() -> repositorio.gravarAtomico(
        pacoteTmp, arquivo, validador, ORIGEM, manifestos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("temporario");
    assertThatThrownBy(() -> repositorio.carregar(pacotePartial))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("temporario");
  }

  @Test
  void rejeitaManifestAlteradoEPacoteTruncado() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path manifestos = Files.createDirectory(temporario.resolve("manifestos-validacao"));
    Path faseCinco = manifestos.resolve("fase-5.json");
    gravarManifesto(mapper, faseCinco, new ManifestoMidiaFaseCinco(List.of()));
    ValidadorPacoteMigracaoIntegral validador = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties());
    ArquivoPacoteMigracaoIntegral arquivo = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        validador.calcularHashesManifestos(manifestos),
        CAPTURADO_EM,
        pacote("execucao-a"),
        mapper);

    Files.writeString(faseCinco, mapper.writeValueAsString(
        new ManifestoMidiaFaseCinco(List.of())) + System.lineSeparator());

    assertThatThrownBy(() -> validador.validar(arquivo, ORIGEM, manifestos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("manifests divergiram");

    Path truncado = temporario.resolve("truncado.json");
    Files.writeString(truncado, "{\"schemaVersion\":");
    assertThatThrownBy(() -> new RepositorioPacoteMigracaoIntegral(mapper).carregar(truncado))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("truncado");
  }

  @Test
  void rejeitaManifestSelecionadoDiferenteDoManifestEmbutido() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path manifestos = Files.createDirectory(temporario.resolve("manifestos-logicos"));
    Path faseCinco = manifestos.resolve("fase-5.json");
    gravarManifesto(mapper, faseCinco, manifestoDescartado());
    ValidadorPacoteMigracaoIntegral validador = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties());
    ArquivoPacoteMigracaoIntegral arquivo = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        validador.calcularHashesManifestos(manifestos),
        CAPTURADO_EM,
        pacote("execucao-a"),
        mapper);

    assertThatThrownBy(() -> validador.validar(arquivo, ORIGEM, manifestos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("logico");
  }

  @Test
  void rejeitaVersoesIncompativeisESnapshotDivergente() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path manifestos = Files.createDirectory(temporario.resolve("manifestos-versoes"));
    Path faseCinco = manifestos.resolve("fase-5.json");
    gravarManifesto(mapper, faseCinco, new ManifestoMidiaFaseCinco(List.of()));
    ValidadorPacoteMigracaoIntegral validador = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties());
    Map<String, String> hashes = validador.calcularHashesManifestos(manifestos);
    ArquivoPacoteMigracaoIntegral valido = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        hashes,
        CAPTURADO_EM,
        pacote("execucao-a"),
        mapper);

    ArquivoPacoteMigracaoIntegral schemaIncompativel = new ArquivoPacoteMigracaoIntegral(
        "topsdojob.migracao.integral/v2",
        valido.origemId(),
        valido.snapshotSha256(),
        valido.manifestoFaseCincoArquivo(),
        valido.manifestoFaseCincoSha256(),
        valido.manifestosSha256(),
        valido.contagens(),
        valido.fingerprint(),
        valido.geradoEm(),
        valido.pacote());
    assertThatThrownBy(() -> validador.validar(schemaIncompativel, ORIGEM, manifestos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("schemaVersion");

    PacoteMigracaoIntegral versaoIncompativel = new PacoteMigracaoIntegral(
        valido.pacote().pacoteId(),
        "2",
        valido.pacote().base(),
        valido.pacote().faseUm(),
        valido.pacote().faseDois(),
        valido.pacote().faseTres(),
        valido.pacote().faseQuatro(),
        valido.pacote().faseCinco());
    ArquivoPacoteMigracaoIntegral arquivoVersaoIncompativel =
        ArquivoPacoteMigracaoIntegral.criar(
            ORIGEM,
            SNAPSHOT,
            "fase-5.json",
            hashes,
            CAPTURADO_EM,
            versaoIncompativel,
            mapper);
    assertThatThrownBy(() -> validador.validar(
        arquivoVersaoIncompativel, ORIGEM, manifestos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("versao interna");

    var base = valido.pacote().base();
    var baseDivergente = new SnapshotBaseMigracaoIntegral.Snapshot(
        "b".repeat(64),
        base.capturadoEm(),
        base.localidades(),
        base.usuarios(),
        base.credenciais(),
        base.documentosKyc(),
        base.usuariosStaging(),
        base.orfaos(),
        base.favoritos(),
        base.metricas());
    PacoteMigracaoIntegral pacoteDivergente = new PacoteMigracaoIntegral(
        valido.pacote().pacoteId(),
        valido.pacote().versao(),
        baseDivergente,
        valido.pacote().faseUm(),
        valido.pacote().faseDois(),
        valido.pacote().faseTres(),
        valido.pacote().faseQuatro(),
        valido.pacote().faseCinco());
    ArquivoPacoteMigracaoIntegral arquivoSnapshotDivergente =
        ArquivoPacoteMigracaoIntegral.criar(
            ORIGEM,
            SNAPSHOT,
            "fase-5.json",
            hashes,
            CAPTURADO_EM,
            pacoteDivergente,
            mapper);
    assertThatThrownBy(() -> validador.validar(
        arquivoSnapshotDivergente, ORIGEM, manifestos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("snapshots");
  }

  @Test
  void alteracaoRealDeNegocioMudaFingerprint() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path manifestos = Files.createDirectory(temporario.resolve("manifestos-negocio"));
    Path faseCinco = manifestos.resolve("fase-5.json");
    gravarManifesto(mapper, faseCinco, new ManifestoMidiaFaseCinco(List.of()));
    Map<String, String> hashes = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties())
        .calcularHashesManifestos(manifestos);

    ArquivoPacoteMigracaoIntegral semMidia = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        hashes,
        CAPTURADO_EM,
        pacote("execucao-a"),
        mapper);
    ArquivoPacoteMigracaoIntegral comMidiaDescartada = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        hashes,
        CAPTURADO_EM,
        pacote("execucao-a", manifestoDescartado()),
        mapper);

    assertThat(comMidiaDescartada.fingerprint()).isNotEqualTo(semMidia.fingerprint());
  }

  @Test
  void rejeitaContagemEFingerprintAdulterados() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Path manifestos = Files.createDirectory(temporario.resolve("manifestos-adulterados"));
    Path faseCinco = manifestos.resolve("fase-5.json");
    gravarManifesto(mapper, faseCinco, new ManifestoMidiaFaseCinco(List.of()));
    ValidadorPacoteMigracaoIntegral validador = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties());
    ArquivoPacoteMigracaoIntegral valido = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        validador.calcularHashesManifestos(manifestos),
        CAPTURADO_EM,
        pacote("execucao-a"),
        mapper);
    Map<String, Long> contagens = new LinkedHashMap<>(valido.contagens());
    contagens.put("base.usuarios", contagens.get("base.usuarios") + 1);
    ArquivoPacoteMigracaoIntegral contagemAdulterada = copiar(
        valido, Map.copyOf(contagens), valido.fingerprint());
    ArquivoPacoteMigracaoIntegral fingerprintAdulterado = copiar(
        valido, valido.contagens(), "c".repeat(64));

    assertThatThrownBy(() -> validador.validar(contagemAdulterada, ORIGEM, manifestos))
        .hasMessageContaining("contagens");
    assertThatThrownBy(() -> validador.validar(fingerprintAdulterado, ORIGEM, manifestos))
        .hasMessageContaining("fingerprint");
  }

  @Test
  void rejeitaReferenciaRemovidaEOwnershipDivergente() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    ValidadorPacoteMigracaoIntegral validador = new ValidadorPacoteMigracaoIntegral(
        mapper, destinoProperties());

    Path manifestosSemAnuncio = Files.createDirectory(temporario.resolve("sem-anuncio"));
    ManifestoMidiaFaseCinco semAnuncio = manifestoImportavel(
        "anuncio-ausente", "anuncio-v3-ausente", "usuario-sintetico", "usuario-v3");
    gravarManifesto(mapper, manifestosSemAnuncio.resolve("fase-5.json"), semAnuncio);
    ArquivoPacoteMigracaoIntegral pacoteSemAnuncio = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        validador.calcularHashesManifestos(manifestosSemAnuncio),
        CAPTURADO_EM,
        pacote("execucao-sem-anuncio", semAnuncio),
        mapper);

    Path manifestosOwnership = Files.createDirectory(temporario.resolve("ownership"));
    ManifestoMidiaFaseCinco ownershipDivergente = manifestoImportavel(
        "10", "anuncio-v3", "usuario-outro", "usuario-v3-outro");
    gravarManifesto(mapper, manifestosOwnership.resolve("fase-5.json"), ownershipDivergente);
    ArquivoPacoteMigracaoIntegral pacoteOwnership = ArquivoPacoteMigracaoIntegral.criar(
        ORIGEM,
        SNAPSHOT,
        "fase-5.json",
        validador.calcularHashesManifestos(manifestosOwnership),
        CAPTURADO_EM,
        pacote("execucao-ownership", ownershipDivergente),
        mapper);

    assertThatThrownBy(() -> validador.validar(
        pacoteSemAnuncio, ORIGEM, manifestosSemAnuncio))
        .hasMessageContaining("midia sem anuncio");
    assertThatThrownBy(() -> validador.validar(
        pacoteOwnership, ORIGEM, manifestosOwnership))
        .hasMessageContaining("ownership da midia");
  }

  private static PacoteMigracaoIntegral pacote(String id) {
    return pacote(id, new ManifestoMidiaFaseCinco(List.of()));
  }

  private static PacoteMigracaoIntegral pacote(
      String id,
      ManifestoMidiaFaseCinco faseCinco) {
    var usuario = new SnapshotBaseMigracaoIntegral.UsuarioLegado(
        "usuario-sintetico",
        null,
        "usuario-sintetico",
        null,
        null,
        "ATIVO",
        "ANUNCIANTE",
        null,
        null,
        null,
        null,
        null,
        CAPTURADO_EM,
        CAPTURADO_EM,
        null,
        new LinkedHashSet<>(List.of("USUARIO", "ADMIN")));
    var outroUsuario = new SnapshotBaseMigracaoIntegral.UsuarioLegado(
        "usuario-outro",
        null,
        "usuario-outro",
        null,
        null,
        "ATIVO",
        "ANUNCIANTE",
        null,
        null,
        null,
        null,
        null,
        CAPTURADO_EM,
        CAPTURADO_EM,
        null,
        new LinkedHashSet<>(List.of("USUARIO")));
    var base = new SnapshotBaseMigracaoIntegral.Snapshot(
        SNAPSHOT, CAPTURADO_EM, List.of(), List.of(usuario, outroUsuario), List.of(), List.of(),
        List.of(), List.of(), List.of());
    var anuncio = new SnapshotAnunciosFaseUm.AnuncioLegado(
        "10",
        "usuario-sintetico",
        "anuncio-sintetico",
        "Anuncio sintetico",
        null,
        "ATIVO",
        "categoria-sintetica",
        null,
        null,
        null,
        null,
        null,
        "LIVRE",
        null,
        List.of(),
        List.of(),
        CAPTURADO_EM,
        CAPTURADO_EM,
        null,
        List.of());
    var faseUm = new SnapshotAnunciosFaseUm.Snapshot(
        SNAPSHOT, CAPTURADO_EM, ATOR, Map.of(), Map.of(), Map.of(), List.of(anuncio),
        new ManifestoMidiaAnuncios(List.of()), List.of());
    var faseDois = new SnapshotConteudoSeoFaseDois.Snapshot(
        SNAPSHOT, CAPTURADO_EM, ATOR, Map.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of());
    var faseTres = new SnapshotConfiguracaoComercialFaseTres.Snapshot(
        SNAPSHOT, CAPTURADO_EM, ATOR, List.of(), List.of(), List.of(), List.of());
    var faseQuatro = new SnapshotFinanceiroFaseQuatro.Snapshot(
        SNAPSHOT, CAPTURADO_EM, ATOR, Map.of(), Map.of(), Map.of(), List.of(),
        List.of(), List.of());
    return new PacoteMigracaoIntegral(
        id,
        "1",
        base,
        faseUm,
        faseDois,
        faseTres,
        faseQuatro,
        faseCinco);
  }

  private static ManifestoMidiaFaseCinco manifestoDescartado() {
    return new ManifestoMidiaFaseCinco(List.of(new Item(
        "temporario-descartado",
        EntidadeTipo.TEMPORARIO,
        null,
        null,
        null,
        null,
        null,
        Finalidade.TEMPORARIO,
        TipoMidia.FOTO,
        Visibilidade.PRIVADA,
        EstadoModeracao.NAO_APLICAVEL,
        false,
        false,
        0,
        "image/jpeg",
        0,
        null,
        new Origem(
            TipoOrigem.OBJECT_STORAGE,
            StorageArea.PRIVATE_MEDIA,
            "legado/temporario/descartado.jpg"),
        null,
        Decisao.DESCARTAR,
        "FORA_DO_ESCOPO_CANONICO")));
  }

  private static ManifestoMidiaFaseCinco manifestoImportavel(
      String anuncioOrigemId,
      String anuncioV3Id,
      String proprietarioOrigemId,
      String proprietarioV3Id) {
    Origem origem = new Origem(
        TipoOrigem.OBJECT_STORAGE, StorageArea.PUBLIC_MEDIA, "legado/foto-sintetica.jpg");
    String origemFingerprint = FingerprintMigracaoIntegral.sha256(
        "OBJECT_STORAGE|PUBLIC_MEDIA|legado/foto-sintetica.jpg"
            .getBytes(StandardCharsets.UTF_8))
        .substring(0, 24);
    Destino destino = new GeradorChaveDestinoMidiaMigracao(destinoProperties()).gerar(
        EntidadeTipo.ANUNCIO,
        anuncioV3Id,
        proprietarioV3Id,
        "foto-sintetica",
        origemFingerprint,
        Finalidade.GALERIA,
        Visibilidade.LIVRE,
        "d".repeat(64),
        "jpg");
    return new ManifestoMidiaFaseCinco(List.of(new Item(
        "foto-sintetica",
        EntidadeTipo.ANUNCIO,
        anuncioOrigemId,
        anuncioV3Id,
        proprietarioOrigemId,
        proprietarioV3Id,
        "foto-sintetica",
        Finalidade.GALERIA,
        TipoMidia.FOTO,
        Visibilidade.LIVRE,
        EstadoModeracao.APROVADA,
        true,
        false,
        0,
        "image/jpeg",
        4,
        "d".repeat(64),
        origem,
        destino,
        Decisao.IMPORTAR,
        null)));
  }

  private static ArquivoPacoteMigracaoIntegral copiar(
      ArquivoPacoteMigracaoIntegral base,
      Map<String, Long> contagens,
      String fingerprint) {
    return new ArquivoPacoteMigracaoIntegral(
        base.schemaVersion(),
        base.origemId(),
        base.snapshotSha256(),
        base.manifestoFaseCincoArquivo(),
        base.manifestoFaseCincoSha256(),
        base.manifestosSha256(),
        contagens,
        fingerprint,
        base.geradoEm(),
        base.pacote());
  }

  private static void gravarManifesto(
      ObjectMapper mapper,
      Path destino,
      ManifestoMidiaFaseCinco manifesto) throws Exception {
    mapper.writeValue(
        destino.toFile(),
        ArquivoManifestoMidiaFaseCinco.criar(
            ORIGEM,
            SNAPSHOT,
            "execucao-manifesto",
            CAPTURADO_EM,
            "f".repeat(64),
            manifesto,
            mapper));
  }

  private static R2StorageProperties destinoProperties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setPublicMediaBucket("public-target");
    properties.setPrivateMediaBucket("private-target");
    properties.setDocumentBucket("document-target");
    return properties;
  }
}
