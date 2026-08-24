package br.com.topsdojob.v3.application.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.ApplyAuthorization;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.Candidate;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.MediaType;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.Status;
import br.com.topsdojob.v3.application.importacao.PrivateMediaObjectTransport.Variant;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PrivateMediaObjectTransportTest {

  private final PrivateMediaObjectTransport transport = new PrivateMediaObjectTransport();

  @Test
  void planEReadOnlyEDeterministico() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    source.put(
        StorageArea.PUBLIC_MEDIA,
        "origem/anuncio-12/foto.png",
        png(4, 3),
        "image/png");

    var first = transport.plan(source, destination, candidate("origem/anuncio-12/foto.png"));
    var second = transport.plan(source, destination, candidate("origem/anuncio-12/foto.png"));

    assertThat(first).isEqualTo(second);
    assertThat(first.status()).isEqualTo(Status.PLANEJADA);
    assertThat(destination.mutationCalls()).isZero();
    assertThat(destination.objects).isEmpty();
  }

  @Test
  void applyExigeAutorizacaoExplicitaEHashDoManifesto() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    source.put(
        StorageArea.PUBLIC_MEDIA,
        "origem/anuncio-12/foto.png",
        png(4, 3),
        "image/png");
    Candidate candidate = candidate("origem/anuncio-12/foto.png");

    assertThatThrownBy(() -> transport.apply(source, destination, candidate, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("autorizacao");
    assertThatThrownBy(() -> transport.apply(
        source,
        destination,
        candidate,
        new ApplyAuthorization(false, "a".repeat(64))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA-256");
    assertThatThrownBy(() -> transport.apply(
        source,
        destination,
        candidate,
        new ApplyAuthorization(true, "hash-invalido")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA-256");

    assertThat(destination.mutationCalls()).isZero();
    assertThat(destination.objects).isEmpty();
  }

  @Test
  void copiaParaDestinoPrivadoComChaveDeterministicaEIdempotencia() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    byte[] image = png(4, 3);
    source.put(StorageArea.PUBLIC_MEDIA, "origem/anuncio-12/foto.png", image, "image/png");
    Candidate candidate = candidate("origem/anuncio-12/foto.png");

    var first = apply(source, destination, candidate);
    var second = apply(source, destination, candidate);

    assertThat(first.status()).isEqualTo(Status.MIGRADA);
    assertThat(second.status()).isEqualTo(Status.PRESERVADA);
    assertThat(second.destinationKey()).isEqualTo(first.destinationKey());
    assertThat(first.destinationKey())
        .startsWith("hml/preprod/midias-pendentes/importacao/anuncios/12/midias/")
        .contains("/original/");
    assertThat(first.width()).isEqualTo(4);
    assertThat(first.height()).isEqualTo(3);
    assertThat(destination.objects).hasSize(1);
    assertThat(destination.publicUrl(StorageArea.PRIVATE_MEDIA, first.destinationKey())).isEmpty();
  }

  @Test
  void objetoAusenteFicaEmQuarentenaSemEscrita() {
    MemoryStorage destination = new MemoryStorage();

    var first = apply(new MemoryStorage(), destination, candidate("ausente.png"));
    var second = apply(new MemoryStorage(), destination, candidate("ausente.png"));

    assertThat(second).isEqualTo(first);
    assertThat(first.status()).isEqualTo(Status.QUARENTENA);
    assertThat(first.reason()).isEqualTo("OBJETO_ORIGEM_AUSENTE");
    assertThat(first.destinationKey()).isNull();
    assertThat(destination.objects).isEmpty();
  }

  @Test
  void tipoDivergenteNaoETransportado() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    source.put(StorageArea.PUBLIC_MEDIA, "origem/video.mp4", png(2, 2), "image/png");
    Candidate video = new Candidate(
        12,
        "a".repeat(64),
        "anuncio_videos",
        "12:video",
        MediaType.VIDEO,
        Variant.ORIGINAL,
        true,
        "b".repeat(64),
        StorageArea.PUBLIC_MEDIA,
        "origem/video.mp4",
        0,
        "hml/preprod/midias-pendentes/");

    var result = apply(source, destination, video);

    assertThat(result.status()).isEqualTo(Status.QUARENTENA);
    assertThat(result.reason()).isEqualTo("TIPO_OU_ASSINATURA_DIVERGENTE");
    assertThat(destination.objects).isEmpty();
  }

  @Test
  void aceitaJpegLegadoDecodificavelComMetadadosAposFimDaImagem() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    byte[] jpeg = jpeg(3, 2);
    byte[] legacyJpeg = Arrays.copyOf(jpeg, jpeg.length + 4);
    legacyJpeg[jpeg.length] = 0x01;
    legacyJpeg[jpeg.length + 1] = 0x02;
    legacyJpeg[jpeg.length + 2] = 0x03;
    legacyJpeg[jpeg.length + 3] = 0x04;
    source.put(StorageArea.PUBLIC_MEDIA, "origem/foto-legada.jpeg", legacyJpeg, "image/jpeg");

    var result = apply(
        source, destination, candidate("origem/foto-legada.jpeg"));

    assertThat(result.status()).isEqualTo(Status.MIGRADA);
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(result.width()).isEqualTo(3);
    assertThat(result.height()).isEqualTo(2);
  }

  @Test
  void aceitaSomenteMarcasIsoConhecidasComoVideo() {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    source.put(StorageArea.PUBLIC_MEDIA, "origem/video.mp4", isoContainer("isom"), "video/mp4");
    source.put(
        StorageArea.PUBLIC_MEDIA,
        "origem/desconhecido.bin",
        isoContainer("xxxx"),
        "application/octet-stream");

    var valid = apply(
        source, destination, videoCandidate("origem/video.mp4", "c".repeat(64)));
    var invalid = apply(
        source, destination, videoCandidate("origem/desconhecido.bin", "d".repeat(64)));

    assertThat(valid.status()).isEqualTo(Status.MIGRADA);
    assertThat(valid.contentType()).isEqualTo("video/mp4");
    assertThat(invalid.status()).isEqualTo(Status.QUARENTENA);
    assertThat(invalid.reason()).isEqualTo("TIPO_OU_ASSINATURA_DIVERGENTE");
    assertThat(destination.objects).hasSize(1);
  }

  @Test
  void checksumOuTamanhoDivergenteNoDestinoBloqueiaSemSobrescrever() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    byte[] image = png(3, 2);
    source.put(StorageArea.PUBLIC_MEDIA, "origem/anuncio-12/foto.png", image, "image/png");
    Candidate candidate = candidate("origem/anuncio-12/foto.png");
    var first = apply(source, destination, candidate);
    destination.put(
        StorageArea.PRIVATE_MEDIA,
        first.destinationKey(),
        png(1, 1),
        "image/png");

    var result = apply(source, destination, candidate);

    assertThat(result.status()).isEqualTo(Status.BLOQUEADA);
    assertThat(result.reason()).isEqualTo("CHECKSUM_OU_TAMANHO_DESTINO_DIVERGENTE");
    assertThat(destination.objects).hasSize(1);
  }

  @Test
  void chavesDeAnunciosDiferentesNuncaColidem() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    byte[] image = png(2, 2);
    source.put(StorageArea.PUBLIC_MEDIA, "origem/a.png", image, "image/png");
    source.put(StorageArea.PUBLIC_MEDIA, "origem/b.png", image, "image/png");
    Candidate firstCandidate = candidate("origem/a.png");
    Candidate secondCandidate = new Candidate(
        13,
        "a".repeat(64),
        "anuncio_fotos",
        "13:foto",
        MediaType.FOTO,
        Variant.ORIGINAL,
        true,
        "c".repeat(64),
        StorageArea.PUBLIC_MEDIA,
        "origem/b.png",
        0,
        "hml/preprod/midias-pendentes/");

    var first = apply(source, destination, firstCandidate);
    var second = apply(source, destination, secondCandidate);

    assertThat(first.destinationKey()).isNotEqualTo(second.destinationKey());
    assertThat(destination.objects).hasSize(2);
  }

  @Test
  void varianteRecuperavelSubstituiPrincipalAusenteSemOcultarQuarentena() throws Exception {
    MemoryStorage source = new MemoryStorage();
    MemoryStorage destination = new MemoryStorage();
    source.put(StorageArea.PUBLIC_MEDIA, "origem/preview.png", png(2, 2), "image/png");
    Candidate original = candidate("origem/ausente.png");
    Candidate preview = new Candidate(
        12,
        "a".repeat(64),
        "protected_media_assets",
        "77",
        MediaType.FOTO,
        Variant.PREVIEW,
        false,
        "c".repeat(64),
        StorageArea.PUBLIC_MEDIA,
        "origem/preview.png",
        0,
        "hml/preprod/midias-pendentes/");

    var normalized = transport.normalizePrincipals(List.of(
        apply(source, destination, original),
        apply(source, destination, preview)));

    assertThat(normalized)
        .filteredOn(result -> result.status() == Status.QUARENTENA)
        .singleElement()
        .satisfies(result -> assertThat(result.primary()).isFalse());
    assertThat(normalized)
        .filteredOn(result -> result.status() == Status.MIGRADA)
        .singleElement()
        .satisfies(result -> assertThat(result.primary()).isTrue());
  }

  @Test
  void grupoSemObjetoRecuperavelPermaneceSemPrincipalParaQuarentenaFormal() {
    var normalized = transport.normalizePrincipals(List.of(
        apply(new MemoryStorage(), new MemoryStorage(), candidate("ausente.png"))));

    assertThat(normalized)
        .singleElement()
        .satisfies(result -> {
          assertThat(result.status()).isEqualTo(Status.QUARENTENA);
          assertThat(result.primary()).isFalse();
        });
  }

  private PrivateMediaObjectTransport.Result apply(
      ObjectStorage source,
      ObjectStorage destination,
      Candidate candidate) {
    return transport.apply(
        source,
        destination,
        candidate,
        new ApplyAuthorization(true, "a".repeat(64)));
  }

  private Candidate candidate(String sourceKey) {
    return new Candidate(
        12,
        "a".repeat(64),
        "anuncio_fotos",
        "12:foto",
        MediaType.FOTO,
        Variant.ORIGINAL,
        true,
        "b".repeat(64),
        StorageArea.PUBLIC_MEDIA,
        sourceKey,
        0,
        "hml/preprod/midias-pendentes/");
  }

  private Candidate videoCandidate(String sourceKey, String referenceHash) {
    return new Candidate(
        12,
        "a".repeat(64),
        "anuncio_videos",
        "12:video",
        MediaType.VIDEO,
        Variant.ORIGINAL,
        true,
        referenceHash,
        StorageArea.PUBLIC_MEDIA,
        sourceKey,
        0,
        "hml/preprod/midias-pendentes/");
  }

  private byte[] png(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private byte[] jpeg(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpeg", output);
    return output.toByteArray();
  }

  private byte[] isoContainer(String brand) {
    byte[] content = new byte[16];
    content[3] = 16;
    content[4] = 'f';
    content[5] = 't';
    content[6] = 'y';
    content[7] = 'p';
    byte[] encodedBrand = brand.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    System.arraycopy(encodedBrand, 0, content, 8, 4);
    return content;
  }

  private static final class MemoryStorage implements ObjectStorage {
    private final Map<String, StoredObject> objects = new LinkedHashMap<>();
    private int putCalls;
    private int putIfAbsentCalls;
    private int deleteCalls;

    @Override
    public void put(StorageArea area, String key, byte[] content, String contentType) {
      putCalls++;
      objects.put(location(area, key), new StoredObject(content, contentType));
    }

    @Override
    public ObjectWriteResult putIfAbsent(
        StorageArea area,
        String key,
        byte[] content,
        String contentType) {
      putIfAbsentCalls++;
      String location = location(area, key);
      if (objects.containsKey(location)) {
        return ObjectWriteResult.ALREADY_EXISTS;
      }
      objects.put(location, new StoredObject(content, contentType));
      return ObjectWriteResult.CREATED;
    }

    @Override
    public boolean exists(StorageArea area, String key) {
      return objects.containsKey(location(area, key));
    }

    @Override
    public StoredObject get(StorageArea area, String key) {
      StoredObject stored = objects.get(location(area, key));
      if (stored == null) {
        throw new IllegalStateException("objeto ausente");
      }
      return stored;
    }

    @Override
    public void delete(StorageArea area, String key) {
      deleteCalls++;
      objects.remove(location(area, key));
    }

    @Override
    public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
      return URI.create("https://private.invalid/" + key);
    }

    @Override
    public Optional<URI> publicUrl(StorageArea area, String key) {
      return area == StorageArea.PUBLIC_MEDIA
          ? Optional.of(URI.create("https://public.invalid/" + key))
          : Optional.empty();
    }

    private int mutationCalls() {
      return putCalls + putIfAbsentCalls + deleteCalls;
    }

    private String location(StorageArea area, String key) {
      return area + ":" + key;
    }
  }
}
