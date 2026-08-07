package br.com.topsdojob.v3.application.publico.anunciante.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class MidiaUploadValidatorTest {

    @Test
    void validaImagemDecodificavelEPreservaSomenteMetadadosSeguros() throws IOException {
        MidiaUploadValidator validator = new MidiaUploadValidator(new MidiaUploadProperties());

        var resultado = validator.validar(new MockMultipartFile(
                "arquivo", "../foto\r\n.png", "application/octet-stream", png(3, 2)));

        assertThat(resultado.video()).isFalse();
        assertThat(resultado.mimeType()).isEqualTo("image/png");
        assertThat(resultado.extensao()).isEqualTo("png");
        assertThat(resultado.nomeOriginal()).isEqualTo(".._foto.png");
        assertThat(resultado.largura()).isEqualTo(3);
        assertThat(resultado.altura()).isEqualTo(2);
        assertThat(resultado.sha256()).hasSize(64);
    }

    @Test
    void validaMp4H264ComDimensoesDuracaoEAudioAacLc() {
        MidiaUploadValidator validator = new MidiaUploadValidator(new MidiaUploadProperties());

        var resultado = validator.validarStory(new MockMultipartFile(
                "arquivo", "video.mp4", "application/octet-stream", mp4Compativel(true)));

        assertThat(resultado.video()).isTrue();
        assertThat(resultado.mimeType()).isEqualTo("video/mp4");
        assertThat(resultado.extensao()).isEqualTo("mp4");
        assertThat(resultado.largura()).isEqualTo(720);
        assertThat(resultado.altura()).isEqualTo(1280);
        assertThat(resultado.duracaoMs()).isEqualTo(15_000L);
    }

    @Test
    void recusaMovCodecNaoH264EAudioNaoAacLc() {
        MidiaUploadValidator validator = new MidiaUploadValidator(new MidiaUploadProperties());

        assertStoryStatus(validator, new MockMultipartFile(
                "arquivo", "video.mov", "video/quicktime", mp4Compativel(false)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "MOV");
        assertStoryStatus(validator, new MockMultipartFile(
                "arquivo", "video.mp4", "video/mp4", mp4ComCodec("hvc1", false)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "H.264");
        assertStoryStatus(validator, new MockMultipartFile(
                "arquivo", "video.mp4", "video/mp4", mp4ComAudio(false)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "AAC-LC");
    }

    @Test
    void recusaSvgExecutavelExtensaoIncompativelEVideoSemEstrutura() throws IOException {
        MidiaUploadValidator validator = new MidiaUploadValidator(new MidiaUploadProperties());

        assertStatus(validator, new MockMultipartFile(
                "arquivo", "imagem.svg", "image/svg+xml", "<svg><script/></svg>".getBytes(StandardCharsets.UTF_8)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(validator, new MockMultipartFile(
                "arquivo", "imagem.exe", "application/octet-stream", png(2, 2)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(validator, new MockMultipartFile(
                "arquivo", "imagem.png", "image/png", jpeg(2, 2)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(validator, new MockMultipartFile(
                "arquivo", "video.mp4", "video/mp4", "0000ftypisom0000moovvide0000mdat".getBytes(StandardCharsets.US_ASCII)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void recusaArquivoAcimaDoLimiteCanonico() throws IOException {
        MidiaUploadProperties properties = new MidiaUploadProperties();
        properties.setMaxImageBytes(10);
        properties.setMaxVideoBytes(1024);
        MidiaUploadValidator validator = new MidiaUploadValidator(properties);

        assertStatus(validator, new MockMultipartFile(
                "arquivo", "foto.png", "image/png", png(2, 2)), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    private void assertStatus(
            MidiaUploadValidator validator,
            MockMultipartFile arquivo,
            HttpStatus status) {
        assertThatThrownBy(() -> validator.validar(arquivo))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }

    private void assertStoryStatus(
            MidiaUploadValidator validator,
            MockMultipartFile arquivo,
            HttpStatus status,
            String mensagem) {
        assertThatThrownBy(() -> validator.validarStory(arquivo))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(status);
                            assertThat(exception.getReason()).contains(mensagem);
                        });
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private byte[] mp4Compativel(boolean audio) {
        return audio ? mp4ComAudio(true) : mp4ComCodec("avc1", false);
    }

    private byte[] mp4ComCodec(String codec, boolean audio) {
        byte[] ftyp = box("ftyp", concat("isom".getBytes(StandardCharsets.US_ASCII), new byte[4],
                "isom".getBytes(StandardCharsets.US_ASCII), "mp42".getBytes(StandardCharsets.US_ASCII)));
        byte[] video = videoTrack(codec);
        byte[] moov = box("moov", audio ? concat(video, audioTrack(true)) : video);
        byte[] mdat = box("mdat", new byte[] {1, 2, 3, 4});
        return concat(ftyp, moov, mdat);
    }

    private byte[] mp4ComAudio(boolean aacLc) {
        byte[] ftyp = box("ftyp", concat("isom".getBytes(StandardCharsets.US_ASCII), new byte[4],
                "isom".getBytes(StandardCharsets.US_ASCII), "mp42".getBytes(StandardCharsets.US_ASCII)));
        byte[] moov = box("moov", concat(videoTrack("avc1"), audioTrack(aacLc)));
        return concat(ftyp, moov, box("mdat", new byte[] {1, 2, 3, 4}));
    }

    private byte[] videoTrack(String codec) {
        byte[] mdhd = mdhd(1_000, 15_000);
        byte[] hdlr = box("hdlr", concat(new byte[8], "vide".getBytes(StandardCharsets.US_ASCII), new byte[4]));
        byte[] visual = new byte[78];
        ByteBuffer.wrap(visual).putShort(24, (short) 720).putShort(26, (short) 1280);
        byte[] codecConfig = box("avcC", new byte[] {1, 100, 0, 31});
        byte[] sample = box(codec, concat(visual, codecConfig));
        byte[] stsd = box("stsd", concat(new byte[4], intBytes(1), sample));
        byte[] minf = box("minf", box("stbl", stsd));
        return box("trak", box("mdia", concat(mdhd, hdlr, minf)));
    }

    private byte[] audioTrack(boolean aacLc) {
        byte[] mdhd = mdhd(48_000, 720_000);
        byte[] hdlr = box("hdlr", concat(new byte[8], "soun".getBytes(StandardCharsets.US_ASCII), new byte[4]));
        byte[] audioSample = new byte[28];
        byte[] decoderFixed = new byte[13];
        decoderFixed[0] = aacLc ? (byte) 0x40 : (byte) 0x6b;
        byte[] config = descriptor(0x05, new byte[] {0x12, 0x10});
        byte[] decoder = descriptor(0x04, concat(decoderFixed, config));
        byte[] es = descriptor(0x03, concat(new byte[] {0, 1, 0}, decoder));
        byte[] esds = box("esds", concat(new byte[4], es));
        byte[] sample = box("mp4a", concat(audioSample, esds));
        byte[] stsd = box("stsd", concat(new byte[4], intBytes(1), sample));
        byte[] minf = box("minf", box("stbl", stsd));
        return box("trak", box("mdia", concat(mdhd, hdlr, minf)));
    }

    private byte[] mdhd(int timescale, int duration) {
        ByteBuffer payload = ByteBuffer.allocate(20);
        payload.position(12);
        payload.putInt(timescale).putInt(duration);
        return box("mdhd", payload.array());
    }

    private byte[] descriptor(int tag, byte[] payload) {
        if (payload.length > 127) throw new IllegalArgumentException("fixture descriptor muito grande");
        return concat(new byte[] {(byte) tag, (byte) payload.length}, payload);
    }

    private byte[] intBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private byte[] box(String type, byte[] payload) {
        return concat(ByteBuffer.allocate(4).putInt(payload.length + 8).array(),
                type.getBytes(StandardCharsets.US_ASCII), payload);
    }

    private byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) size += part.length;
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
