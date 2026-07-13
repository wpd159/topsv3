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
    void validaContainerMp4ComTrilhaDeVideoReal() {
        MidiaUploadValidator validator = new MidiaUploadValidator(new MidiaUploadProperties());

        var resultado = validator.validar(new MockMultipartFile(
                "arquivo", "video.mp4", "application/octet-stream", mp4ComTrilhaVideo()));

        assertThat(resultado.video()).isTrue();
        assertThat(resultado.mimeType()).isEqualTo("video/mp4");
        assertThat(resultado.extensao()).isEqualTo("mp4");
        assertThat(resultado.largura()).isNull();
        assertThat(resultado.altura()).isNull();
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

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] mp4ComTrilhaVideo() {
        byte[] ftyp = box("ftyp", concat("isom".getBytes(StandardCharsets.US_ASCII), new byte[4],
                "isom".getBytes(StandardCharsets.US_ASCII), "mp42".getBytes(StandardCharsets.US_ASCII)));
        byte[] hdlr = box("hdlr", concat(new byte[8], "vide".getBytes(StandardCharsets.US_ASCII), new byte[4]));
        byte[] moov = box("moov", box("trak", box("mdia", hdlr)));
        byte[] mdat = box("mdat", new byte[] {1, 2, 3, 4});
        return concat(ftyp, moov, mdat);
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
