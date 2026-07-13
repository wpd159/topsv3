package br.com.topsdojob.v3.application.publico.anunciante.midia;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MidiaUploadValidator {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov");
    private static final Set<String> ISO_VIDEO_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "qt  ");
    private static final Set<String> ISO_VIDEO_CONTAINERS = Set.of("moov", "trak", "mdia");

    private final MidiaUploadProperties properties;

    public MidiaUploadValidator(MidiaUploadProperties properties) {
        this.properties = properties;
    }

    public MidiaValidada validar(MultipartFile multipart) {
        if (multipart == null || multipart.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo obrigatorio");
        }
        if (multipart.getSize() > properties.getMaxVideoBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "arquivo acima do limite permitido");
        }
        byte[] bytes = bytes(multipart);
        String extensao = extensao(multipart.getOriginalFilename());
        TipoDetectado tipo = detectar(bytes, extensao);
        long maximo = tipo.video() ? properties.getMaxVideoBytes() : properties.getMaxImageBytes();
        if (bytes.length > maximo) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "arquivo acima do limite permitido");
        }
        Dimensoes dimensoes = tipo.video() ? new Dimensoes(null, null) : dimensoesImagem(bytes, tipo);
        return new MidiaValidada(
                bytes,
                tipo.video(),
                tipo.mimeType(),
                tipo.extensaoCanonica(),
                nomeOriginalSeguro(multipart.getOriginalFilename()),
                dimensoes.largura(),
                dimensoes.altura(),
                sha256(bytes));
    }

    private byte[] bytes(MultipartFile multipart) {
        try {
            return multipart.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo nao pode ser lido");
        }
    }

    private TipoDetectado detectar(byte[] bytes, String extensao) {
        if (bytes.length < 12) {
            throw formatoInvalido();
        }
        if (jpeg(bytes)) return exigirExtensao(extensao, false, "image/jpeg", "jpg");
        if (png(bytes)) return exigirExtensao(extensao, false, "image/png", "png");
        if (webp(bytes)) return exigirExtensao(extensao, false, "image/webp", "webp");
        if (isoBmffVideo(bytes)) {
            String canonica = "mov".equals(extensao) ? "mov" : "mp4";
            String mime = "mov".equals(canonica) ? "video/quicktime" : "video/mp4";
            return exigirExtensao(extensao, true, mime, canonica);
        }
        throw formatoInvalido();
    }

    private TipoDetectado exigirExtensao(
            String extensao,
            boolean video,
            String mimeType,
            String canonica) {
        Set<String> permitidas = video ? VIDEO_EXTENSIONS : IMAGE_EXTENSIONS;
        if (!permitidas.contains(extensao)) {
            throw formatoInvalido();
        }
        if (!video && "image/jpeg".equals(mimeType) && !("jpg".equals(extensao) || "jpeg".equals(extensao))) {
            throw formatoInvalido();
        }
        if (!video && !"image/jpeg".equals(mimeType) && !canonica.equals(extensao)) {
            throw formatoInvalido();
        }
        if (video && !("mp4".equals(extensao) || "mov".equals(extensao))) {
            throw formatoInvalido();
        }
        return new TipoDetectado(video, mimeType, canonica);
    }

    private Dimensoes dimensoesImagem(byte[] bytes, TipoDetectado tipo) {
        if ("image/webp".equals(tipo.mimeType())) {
            return dimensoesWebp(bytes);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                throw formatoInvalido();
            }
            return new Dimensoes(image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            throw formatoInvalido();
        }
    }

    private Dimensoes dimensoesWebp(byte[] bytes) {
        if (bytes.length < 30) throw formatoInvalido();
        String chunk = ascii(bytes, 12, 4);
        if ("VP8X".equals(chunk)) {
            int width = 1 + littleEndian24(bytes, 24);
            int height = 1 + littleEndian24(bytes, 27);
            if (width > 0 && height > 0) return new Dimensoes(width, height);
        }
        if ("VP8L".equals(chunk) && bytes.length >= 25 && (bytes[20] & 0xff) == 0x2f) {
            int b1 = bytes[21] & 0xff;
            int b2 = bytes[22] & 0xff;
            int b3 = bytes[23] & 0xff;
            int b4 = bytes[24] & 0xff;
            return new Dimensoes(1 + (b1 | ((b2 & 0x3f) << 8)), 1 + ((b2 >> 6) | (b3 << 2) | ((b4 & 0x0f) << 10)));
        }
        if ("VP8 ".equals(chunk) && bytes.length >= 30
                && (bytes[23] & 0xff) == 0x9d && (bytes[24] & 0xff) == 0x01 && (bytes[25] & 0xff) == 0x2a) {
            return new Dimensoes(littleEndian16(bytes, 26) & 0x3fff, littleEndian16(bytes, 28) & 0x3fff);
        }
        throw formatoInvalido();
    }

    private boolean jpeg(byte[] bytes) {
        return (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[bytes.length - 2] & 0xff) == 0xff && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }

    private boolean png(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) return false;
        }
        return true;
    }

    private boolean webp(byte[] bytes) {
        return "RIFF".equals(ascii(bytes, 0, 4)) && "WEBP".equals(ascii(bytes, 8, 4));
    }

    private boolean isoBmffVideo(byte[] bytes) {
        boolean ftyp = false;
        boolean moov = false;
        boolean mdat = false;
        boolean videoTrack = false;
        long cursor = 0;
        while (cursor < bytes.length) {
            Box box = box(bytes, cursor, bytes.length);
            if (box == null) return false;
            if ("ftyp".equals(box.type())) {
                ftyp = marcaVideoPermitida(bytes, box.payloadStart(), box.end());
            } else if ("moov".equals(box.type())) {
                moov = true;
                videoTrack = contemHandlerVideo(bytes, box.payloadStart(), box.end());
            } else if ("mdat".equals(box.type())) {
                mdat = box.end() > box.payloadStart();
            }
            cursor = box.end();
        }
        return ftyp && moov && mdat && videoTrack;
    }

    private boolean marcaVideoPermitida(byte[] bytes, long inicio, long fim) {
        if (fim - inicio < 8) return false;
        for (long cursor = inicio; cursor + 4 <= fim; cursor += 4) {
            if (ISO_VIDEO_BRANDS.contains(ascii(bytes, Math.toIntExact(cursor), 4))) return true;
        }
        return false;
    }

    private boolean contemHandlerVideo(byte[] bytes, long inicio, long fim) {
        long cursor = inicio;
        while (cursor < fim) {
            Box box = box(bytes, cursor, fim);
            if (box == null) return false;
            if ("hdlr".equals(box.type())
                    && box.end() - box.payloadStart() >= 12
                    && "vide".equals(ascii(bytes, Math.toIntExact(box.payloadStart() + 8), 4))) {
                return true;
            }
            if (ISO_VIDEO_CONTAINERS.contains(box.type())
                    && contemHandlerVideo(bytes, box.payloadStart(), box.end())) return true;
            cursor = box.end();
        }
        return false;
    }

    private Box box(byte[] bytes, long inicio, long limite) {
        if (inicio < 0 || inicio + 8 > limite || limite > bytes.length) return null;
        long tamanho = uint32(bytes, Math.toIntExact(inicio));
        String tipo = ascii(bytes, Math.toIntExact(inicio + 4), 4);
        long cabecalho = 8;
        if (tamanho == 1) {
            if (inicio + 16 > limite) return null;
            tamanho = uint64(bytes, Math.toIntExact(inicio + 8));
            cabecalho = 16;
        } else if (tamanho == 0) {
            tamanho = limite - inicio;
        }
        if (tamanho < cabecalho || tamanho > limite - inicio) return null;
        return new Box(tipo, inicio + cabecalho, inicio + tamanho);
    }

    private long uint32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 24)
                | ((long) (bytes[offset + 1] & 0xff) << 16)
                | ((long) (bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xffL);
    }

    private long uint64(byte[] bytes, int offset) {
        long alto = uint32(bytes, offset);
        long baixo = uint32(bytes, offset + 4);
        if ((alto & 0x80000000L) != 0) return -1;
        return (alto << 32) | baixo;
    }

    private String extensao(String filename) {
        String value = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        int dot = value.lastIndexOf('.');
        return dot < 0 || dot == value.length() - 1 ? "" : value.substring(dot + 1);
    }

    private String nomeOriginalSeguro(String filename) {
        String value = filename == null ? "arquivo" : filename.replaceAll("[\\p{Cntrl}\\r\\n]", "").trim();
        value = value.replace('\\', '_').replace('/', '_');
        if (value.isBlank()) return "arquivo";
        return value.length() > 160 ? value.substring(value.length() - 160) : value;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private String ascii(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > bytes.length) return "";
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private int littleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private int littleEndian24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8) | ((bytes[offset + 2] & 0xff) << 16);
    }

    private ResponseStatusException formatoInvalido() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "formato de arquivo nao permitido");
    }

    public record MidiaValidada(
            byte[] bytes,
            boolean video,
            String mimeType,
            String extensao,
            String nomeOriginal,
            Integer largura,
            Integer altura,
            String sha256) {
    }

    private record TipoDetectado(boolean video, String mimeType, String extensaoCanonica) {
    }

    private record Dimensoes(Integer largura, Integer altura) {
    }

    private record Box(String type, long payloadStart, long end) {
    }
}
