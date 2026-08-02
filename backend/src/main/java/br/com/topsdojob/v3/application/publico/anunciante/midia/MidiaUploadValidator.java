package br.com.topsdojob.v3.application.publico.anunciante.midia;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MidiaUploadValidator {

    static {
        ImageIO.scanForPlugins();
    }

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov");
    private static final Set<String> MP4_VIDEO_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ");
    private static final Set<String> ISO_VIDEO_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "qt  ");
    private static final Set<String> ISO_VIDEO_CONTAINERS = Set.of("moov", "trak", "mdia");

    private final MidiaUploadProperties properties;

    public MidiaUploadValidator(MidiaUploadProperties properties) {
        this.properties = properties;
    }

    public MidiaValidada validar(MultipartFile multipart) {
        return validar(multipart, false);
    }

    public MidiaValidada validarStory(MultipartFile multipart) {
        return validar(multipart, true);
    }

    private MidiaValidada validar(MultipartFile multipart, boolean compatibilidadeWebObrigatoria) {
        if (multipart == null || multipart.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo obrigatorio");
        }
        if (multipart.getSize() > properties.getMaxVideoBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "arquivo acima do limite permitido");
        }
        byte[] bytes = bytes(multipart);
        String extensao = extensao(multipart.getOriginalFilename());
        TipoDetectado tipo = detectar(bytes, extensao, compatibilidadeWebObrigatoria);
        long maximo = tipo.video() ? properties.getMaxVideoBytes() : properties.getMaxImageBytes();
        if (bytes.length > maximo) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "arquivo acima do limite permitido");
        }
        Dimensoes dimensoes = tipo.video()
                ? new Dimensoes(tipo.largura(), tipo.altura())
                : dimensoesImagem(bytes, tipo);
        return new MidiaValidada(
                bytes,
                tipo.video(),
                tipo.mimeType(),
                tipo.extensaoCanonica(),
                nomeOriginalSeguro(multipart.getOriginalFilename()),
                dimensoes.largura(),
                dimensoes.altura(),
                tipo.duracaoMs(),
                sha256(bytes));
    }

    private byte[] bytes(MultipartFile multipart) {
        try {
            return multipart.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arquivo nao pode ser lido");
        }
    }

    private TipoDetectado detectar(
            byte[] bytes,
            String extensao,
            boolean compatibilidadeWebObrigatoria) {
        if (bytes.length < 12) {
            throw formatoInvalido();
        }
        if (jpeg(bytes)) return exigirExtensao(extensao, false, "image/jpeg", "jpg");
        if (png(bytes)) return exigirExtensao(extensao, false, "image/png", "png");
        if (webp(bytes)) return exigirExtensao(extensao, false, "image/webp", "webp");
        if (compatibilidadeWebObrigatoria) {
            VideoMetadata video = mp4Compativel(bytes);
            if (video == null) throw formatoInvalido();
            if (!VIDEO_EXTENSIONS.contains(extensao)) {
                throw formatoInvalido();
            }
            if (!"mp4".equals(extensao)) throw formatoInvalido();
            if (video.duracaoMs() > Integer.MAX_VALUE) throw formatoInvalido();
            return new TipoDetectado(
                    true,
                    "video/mp4",
                    "mp4",
                    video.largura(),
                    video.altura(),
                    video.duracaoMs());
        }
        if (!compatibilidadeWebObrigatoria && isoBmffVideoBasico(bytes)) {
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
        return new TipoDetectado(video, mimeType, canonica, null, null, null);
    }

    private Dimensoes dimensoesImagem(byte[] bytes, TipoDetectado tipo) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(tipo.mimeType());
        if (!readers.hasNext()) throw formatoInvalido();
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw formatoInvalido();
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            validarDimensoesSeguras(width, height);
            BufferedImage image = reader.read(0, reader.getDefaultReadParam());
            if (image == null || image.getWidth() != width || image.getHeight() != height) throw formatoInvalido();
            return new Dimensoes(width, height);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw formatoInvalido();
        } finally {
            reader.dispose();
        }
    }

    private void validarDimensoesSeguras(int width, int height) {
        long pixels = (long) width * height;
        if (width < 1 || height < 1
                || width > properties.getMaxImageDimension()
                || height > properties.getMaxImageDimension()
                || pixels > properties.getMaxImagePixels()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "imagem excede o limite seguro de pixels");
        }
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

    private boolean isoBmffVideoBasico(byte[] bytes) {
        boolean ftyp = false;
        boolean moov = false;
        boolean mdat = false;
        boolean videoTrack = false;
        long cursor = 0;
        while (cursor < bytes.length) {
            Box box = box(bytes, cursor, bytes.length);
            if (box == null) return false;
            if ("ftyp".equals(box.type())) {
                ftyp = marcaVideoBasicaPermitida(bytes, box.payloadStart(), box.end());
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

    private boolean marcaVideoBasicaPermitida(byte[] bytes, long inicio, long fim) {
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

    private VideoMetadata mp4Compativel(byte[] bytes) {
        boolean ftyp = false;
        boolean mdat = false;
        VideoMetadata video = null;
        boolean audioIncompativel = false;
        long cursor = 0;
        while (cursor < bytes.length) {
            Box box = box(bytes, cursor, bytes.length);
            if (box == null) return null;
            if ("ftyp".equals(box.type())) {
                ftyp = marcaMp4Permitida(bytes, box.payloadStart(), box.end());
            } else if ("moov".equals(box.type())) {
                TrackSummary summary = trilhas(bytes, box.payloadStart(), box.end());
                if (summary == null) return null;
                video = summary.video();
                audioIncompativel = summary.audioIncompativel();
            } else if ("mdat".equals(box.type())) {
                mdat = box.end() > box.payloadStart();
            }
            cursor = box.end();
        }
        return ftyp && mdat && video != null && !audioIncompativel ? video : null;
    }

    private boolean marcaMp4Permitida(byte[] bytes, long inicio, long fim) {
        if (fim - inicio < 8) return false;
        String majorBrand = ascii(bytes, Math.toIntExact(inicio), 4);
        return MP4_VIDEO_BRANDS.contains(majorBrand);
    }

    private TrackSummary trilhas(byte[] bytes, long inicio, long fim) {
        VideoMetadata video = null;
        boolean audioIncompativel = false;
        long cursor = inicio;
        while (cursor < fim) {
            Box box = box(bytes, cursor, fim);
            if (box == null) return null;
            if ("trak".equals(box.type())) {
                TrackMetadata track = trilha(bytes, box.payloadStart(), box.end());
                if (track == null) return null;
                if ("vide".equals(track.handler())) {
                    if (track.video() == null) return null;
                    if (video == null || track.video().duracaoMs() > video.duracaoMs()) {
                        video = track.video();
                    }
                } else if ("soun".equals(track.handler()) && !track.audioCompativel()) {
                    audioIncompativel = true;
                }
            }
            cursor = box.end();
        }
        return new TrackSummary(video, audioIncompativel);
    }

    private TrackMetadata trilha(byte[] bytes, long inicio, long fim) {
        Box mdia = filho(bytes, inicio, fim, "mdia");
        if (mdia == null) return null;
        Box hdlr = filho(bytes, mdia.payloadStart(), mdia.end(), "hdlr");
        Box mdhd = filho(bytes, mdia.payloadStart(), mdia.end(), "mdhd");
        Box minf = filho(bytes, mdia.payloadStart(), mdia.end(), "minf");
        if (hdlr == null || mdhd == null || minf == null || hdlr.end() - hdlr.payloadStart() < 12) {
            return null;
        }
        String handler = ascii(bytes, Math.toIntExact(hdlr.payloadStart() + 8), 4);
        Long duracaoMs = duracaoMs(bytes, mdhd);
        Box stbl = filho(bytes, minf.payloadStart(), minf.end(), "stbl");
        Box stsd = stbl == null ? null : filho(bytes, stbl.payloadStart(), stbl.end(), "stsd");
        if (duracaoMs == null || stsd == null) return null;
        if ("vide".equals(handler)) {
            VideoSample sample = amostraVideo(bytes, stsd);
            if (sample == null) return new TrackMetadata(handler, null, false);
            validarDimensoesSeguras(sample.largura(), sample.altura());
            return new TrackMetadata(
                    handler,
                    new VideoMetadata(sample.largura(), sample.altura(), duracaoMs),
                    true);
        }
        if ("soun".equals(handler)) {
            return new TrackMetadata(handler, null, amostraAudioAacLc(bytes, stsd));
        }
        return new TrackMetadata(handler, null, true);
    }

    private Box filho(byte[] bytes, long inicio, long fim, String tipo) {
        long cursor = inicio;
        while (cursor < fim) {
            Box box = box(bytes, cursor, fim);
            if (box == null) return null;
            if (tipo.equals(box.type())) return box;
            cursor = box.end();
        }
        return null;
    }

    private Long duracaoMs(byte[] bytes, Box mdhd) {
        long inicio = mdhd.payloadStart();
        if (mdhd.end() - inicio < 20) return null;
        int version = bytes[Math.toIntExact(inicio)] & 0xff;
        long timescale;
        long duration;
        if (version == 0) {
            timescale = uint32(bytes, Math.toIntExact(inicio + 12));
            duration = uint32(bytes, Math.toIntExact(inicio + 16));
        } else if (version == 1 && mdhd.end() - inicio >= 32) {
            timescale = uint32(bytes, Math.toIntExact(inicio + 20));
            duration = uint64(bytes, Math.toIntExact(inicio + 24));
        } else {
            return null;
        }
        if (timescale < 1 || duration < 1 || duration > Long.MAX_VALUE / 1000L) return null;
        long millis = duration * 1000L / timescale;
        return millis > 0 ? millis : null;
    }

    private VideoSample amostraVideo(byte[] bytes, Box stsd) {
        long cursor = stsd.payloadStart() + 8;
        if (stsd.end() - stsd.payloadStart() < 8) return null;
        long count = uint32(bytes, Math.toIntExact(stsd.payloadStart() + 4));
        if (count < 1 || count > 32) return null;
        VideoSample encontrada = null;
        for (long index = 0; index < count; index++) {
            Box entry = box(bytes, cursor, stsd.end());
            if (entry == null || !("avc1".equals(entry.type()) || "avc3".equals(entry.type()))) return null;
            if (entry.end() - entry.payloadStart() < 78) return null;
            int largura = uint16(bytes, Math.toIntExact(entry.payloadStart() + 24));
            int altura = uint16(bytes, Math.toIntExact(entry.payloadStart() + 26));
            Box avcC = filho(bytes, entry.payloadStart() + 78, entry.end(), "avcC");
            if (avcC == null || avcC.end() - avcC.payloadStart() < 4) return null;
            encontrada = new VideoSample(largura, altura);
            cursor = entry.end();
        }
        return encontrada;
    }

    private boolean amostraAudioAacLc(byte[] bytes, Box stsd) {
        if (stsd.end() - stsd.payloadStart() < 8) return false;
        long count = uint32(bytes, Math.toIntExact(stsd.payloadStart() + 4));
        long cursor = stsd.payloadStart() + 8;
        if (count < 1 || count > 32) return false;
        for (long index = 0; index < count; index++) {
            Box entry = box(bytes, cursor, stsd.end());
            if (entry == null || !"mp4a".equals(entry.type()) || entry.end() - entry.payloadStart() < 28) {
                return false;
            }
            if (uint16(bytes, Math.toIntExact(entry.payloadStart() + 8)) != 0) return false;
            Box esds = filho(bytes, entry.payloadStart() + 28, entry.end(), "esds");
            if (esds == null || !esdsAacLc(bytes, esds)) return false;
            cursor = entry.end();
        }
        return true;
    }

    private boolean esdsAacLc(byte[] bytes, Box esds) {
        long inicio = esds.payloadStart() + 4;
        if (inicio >= esds.end()) return false;
        Descriptor es = descriptor(bytes, inicio, esds.end());
        if (es == null || es.tag() != 0x03 || es.end() - es.payloadStart() < 3) return false;
        long cursor = es.payloadStart() + 2;
        int flags = bytes[Math.toIntExact(cursor++)] & 0xff;
        if ((flags & 0x80) != 0) cursor += 2;
        if ((flags & 0x40) != 0) {
            if (cursor >= es.end()) return false;
            int length = bytes[Math.toIntExact(cursor++)] & 0xff;
            cursor += length;
        }
        if ((flags & 0x20) != 0) cursor += 2;
        Descriptor decoder = descriptor(bytes, cursor, es.end());
        if (decoder == null || decoder.tag() != 0x04 || decoder.end() - decoder.payloadStart() < 13
                || (bytes[Math.toIntExact(decoder.payloadStart())] & 0xff) != 0x40) {
            return false;
        }
        Descriptor config = descriptor(bytes, decoder.payloadStart() + 13, decoder.end());
        if (config == null || config.tag() != 0x05 || config.payloadStart() >= config.end()) return false;
        int audioObjectType = (bytes[Math.toIntExact(config.payloadStart())] & 0xff) >>> 3;
        return audioObjectType == 2;
    }

    private Descriptor descriptor(byte[] bytes, long inicio, long limite) {
        if (inicio < 0 || inicio + 2 > limite || limite > bytes.length) return null;
        int tag = bytes[Math.toIntExact(inicio)] & 0xff;
        long cursor = inicio + 1;
        long length = 0;
        int octets = 0;
        int value;
        do {
            if (cursor >= limite || octets++ == 4) return null;
            value = bytes[Math.toIntExact(cursor++)] & 0xff;
            length = (length << 7) | (value & 0x7f);
        } while ((value & 0x80) != 0);
        if (length < 0 || length > limite - cursor) return null;
        return new Descriptor(tag, cursor, cursor + length);
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

    private int uint16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
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
            Long duracaoMs,
            String sha256) {
    }

    private record TipoDetectado(
            boolean video,
            String mimeType,
            String extensaoCanonica,
            Integer largura,
            Integer altura,
            Long duracaoMs) {
    }

    private record Dimensoes(Integer largura, Integer altura) {
    }

    private record Box(String type, long payloadStart, long end) {
    }

    private record VideoMetadata(Integer largura, Integer altura, Long duracaoMs) {
    }

    private record TrackMetadata(String handler, VideoMetadata video, boolean audioCompativel) {
    }

    private record TrackSummary(VideoMetadata video, boolean audioIncompativel) {
    }

    private record VideoSample(int largura, int altura) {
    }

    private record Descriptor(int tag, long payloadStart, long end) {
    }
}
