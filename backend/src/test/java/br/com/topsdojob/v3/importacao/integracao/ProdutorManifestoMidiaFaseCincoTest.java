package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.DestinoProperties;
import br.com.topsdojob.v3.importacao.integracao.RepositorioCandidatosMidiaLegada.Descritor;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProdutorManifestoMidiaFaseCincoTest {

  private static final String SNAPSHOT = "a".repeat(64);
  private static final OffsetDateTime INSTANTE = OffsetDateTime.parse("2026-08-14T12:00:00Z");

  @TempDir
  Path temporario;

  @Test
  void planejaClassesCanonicasConsolidaLeituraEQuarentenaReferenciaInvalida() {
    RepositorioCandidatosMidiaLegada repositorioCandidatos =
        mock(RepositorioCandidatosMidiaLegada.class);
    Origem foto = origem(StorageArea.PUBLIC_MEDIA, "legacy/foto.jpg");
    Origem video = origem(StorageArea.PRIVATE_MEDIA, "legacy/video.mp4");
    Origem documento = origem(StorageArea.PRIVATE_DOCUMENT, "legacy/documento.pdf");
    Origem editorial = origem(StorageArea.PUBLIC_MEDIA, "legacy/editorial.png");
    Origem invalida = origem(StorageArea.PRIVATE_DOCUMENT, "quarentena/referencia-invalida/"
        + "f".repeat(64));
    List<Descritor> descritores = List.of(
        descritor("foto", EntidadeTipo.ANUNCIO, Finalidade.CAPA, TipoMidia.FOTO,
            Visibilidade.LIVRE, foto, true, true, "jpg", "image/jpeg", "10", "1"),
        descritor("foto-reuso", EntidadeTipo.ANUNCIO, Finalidade.GALERIA, TipoMidia.FOTO,
            Visibilidade.LIVRE, foto, true, false, "jpg", "image/jpeg", "11", "1"),
        descritor("video", EntidadeTipo.ANUNCIO, Finalidade.VIDEO, TipoMidia.VIDEO,
            Visibilidade.RESTRITA_18, video, true, false, "mp4", "video/mp4", "12", "1"),
        descritor("kyc", EntidadeTipo.KYC, Finalidade.KYC_IDENTIDADE, TipoMidia.DOCUMENTO,
            Visibilidade.PRIVADA, documento, true, false, "pdf", "application/pdf", "1", "1"),
        descritor("editorial", EntidadeTipo.EDITORIAL, Finalidade.BLOG_CAPA, TipoMidia.FOTO,
            Visibilidade.LIVRE, editorial, true, false, "png", "image/png", "20", "1"),
        descritor("invalida", EntidadeTipo.KYC, Finalidade.KYC_IDENTIDADE,
            TipoMidia.DOCUMENTO, Visibilidade.PRIVADA, invalida, false, false,
            null, null, "2", "2"));
    when(repositorioCandidatos.listar()).thenReturn(descritores);

    AtomicInteger leituras = new AtomicInteger();
    AtomicInteger falhasTransitorias = new AtomicInteger();
    Map<Origem, StoredObject> objetos = Map.of(
        foto, new StoredObject(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1},
            "image/jpeg"),
        video, new StoredObject(mp4(), "video/mp4"),
        documento, new StoredObject("%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            "application/pdf"),
        editorial, new StoredObject(
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "image/png"));
    FonteMidiaMigracao fonte = origem -> {
      leituras.incrementAndGet();
      if (origem.equals(video) && falhasTransitorias.getAndIncrement() == 0) {
        throw new R2StorageException("falha transitoria sintetica");
      }
      StoredObject objeto = objetos.get(origem);
      if (objeto == null) {
        throw new FonteMidiaMigracao.ObjetoOrigemAusenteException();
      }
      return objeto;
    };
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    ProdutorManifestoMidiaFaseCinco produtor = new ProdutorManifestoMidiaFaseCinco(
        repositorioCandidatos, fonte, destino(), mapper);
    Path saida = temporario.resolve("manifesto.json");

    var resultado = produtor.produzir(new ProdutorManifestoMidiaFaseCinco.Parametros(
        "TOPSDOJOB_LEGADO", SNAPSHOT, "execucao-a", INSTANTE, saida));
    ArquivoManifestoMidiaFaseCinco arquivo =
        new RepositorioManifestoMidiaFaseCinco(mapper).carregar(saida);

    assertThat(resultado.estado())
        .isEqualTo(RepositorioManifestoMidiaFaseCinco.EstadoEscrita.CRIADO);
    assertThat(leituras).hasValue(5);
    assertThat(arquivo.manifesto().itens()).hasSize(6);
    assertThat(arquivo.manifesto().itens())
        .filteredOn(item -> item.decisao() == Decisao.IMPORTAR)
        .hasSize(5);
    assertThat(arquivo.manifesto().itens())
        .filteredOn(item -> item.idOrigem().equals("invalida"))
        .singleElement()
        .extracting(item -> item.motivo())
        .isEqualTo("SEM_REFERENCIA_CANONICA");
    assertThat(resultado.storage())
        .containsEntry("RETRIES", 1L)
        .containsEntry("MUTACOES", 0L);
    verify(repositorioCandidatos).listar();
  }

  private static Descritor descritor(
      String id,
      EntidadeTipo entidade,
      Finalidade finalidade,
      TipoMidia tipo,
      Visibilidade visibilidade,
      Origem origem,
      boolean referenciaValida,
      boolean capa,
      String extensao,
      String mime,
      String entidadeId,
      String proprietarioId) {
    return new Descritor(
        id,
        entidade,
        entidadeId,
        java.util.UUID.nameUUIDFromBytes(
            ("entidade:" + entidadeId).getBytes(StandardCharsets.UTF_8)).toString(),
        proprietarioId,
        java.util.UUID.nameUUIDFromBytes(
            ("usuario:" + proprietarioId).getBytes(StandardCharsets.UTF_8)).toString(),
        "referencia:" + id,
        finalidade,
        tipo,
        visibilidade,
        EstadoModeracao.APROVADA,
        referenciaValida,
        true,
        capa,
        0,
        mime,
        extensao,
        origem,
        "e".repeat(64));
  }

  private static Origem origem(StorageArea area, String chave) {
    return new Origem(TipoOrigem.OBJECT_STORAGE, area, chave);
  }

  private static DestinoProperties destino() {
    DestinoProperties properties = new DestinoProperties();
    properties.setPublicMediaBucket("public-target");
    properties.setPrivateMediaBucket("private-target");
    properties.setDocumentBucket("document-target");
    return properties;
  }

  private static byte[] mp4() {
    byte[] bytes = new byte[16];
    bytes[4] = 'f';
    bytes[5] = 't';
    bytes[6] = 'y';
    bytes[7] = 'p';
    bytes[8] = 'i';
    bytes[9] = 's';
    bytes[10] = 'o';
    bytes[11] = 'm';
    return bytes;
  }
}
