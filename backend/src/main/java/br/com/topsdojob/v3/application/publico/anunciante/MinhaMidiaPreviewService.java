package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Owner media URLs; callers must validate the advertisement owner before resolving a preview. */
@Service
public class MinhaMidiaPreviewService {

    private static final Duration VALIDADE = Duration.ofMinutes(5);
    private static final Preview INDISPONIVEL = new Preview(null, null);

    private final R2StorageProperties storageProperties;
    private final ObjectProvider<ObjectStorage> storageProvider;
    private final Clock clock;

    @Autowired
    public MinhaMidiaPreviewService(
            R2StorageProperties storageProperties, ObjectProvider<ObjectStorage> storageProvider) {
        this(storageProperties, storageProvider, Clock.systemUTC());
    }

    MinhaMidiaPreviewService(
            R2StorageProperties storageProperties, ObjectProvider<ObjectStorage> storageProvider, Clock clock) {
        this.storageProperties = storageProperties;
        this.storageProvider = storageProvider;
        this.clock = clock;
    }

    public Preview resolver(ArquivoMidiaEntity arquivo) {
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null || arquivo == null || arquivo.getChaveObjeto() == null) return INDISPONIVEL;
        try {
            if (storageProperties.getPrivateMediaBucket().equals(arquivo.getBucket())
                    && arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix())) {
                OffsetDateTime expiraEm = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
                        .withNano(0).plus(VALIDADE);
                String url = storage.temporaryGetUrl(
                        StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto(), VALIDADE).toString();
                return new Preview(url, expiraEm);
            }
            if (storageProperties.getPublicMediaBucket().equals(arquivo.getBucket())
                    && arquivo.getChaveObjeto().startsWith(storageProperties.getPublicMediaPrefix())) {
                return new Preview(storage.publicUrl(StorageArea.PUBLIC_MEDIA, arquivo.getChaveObjeto())
                        .map(URI::toString).orElse(null), null);
            }
        } catch (RuntimeException ignored) {
            return INDISPONIVEL;
        }
        return INDISPONIVEL;
    }

    public record Preview(String url, OffsetDateTime expiraEm) { }
}
