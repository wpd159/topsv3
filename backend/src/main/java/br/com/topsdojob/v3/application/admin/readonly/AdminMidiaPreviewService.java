package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaPreviewDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminMidiaPreviewService {

    private static final Duration PRIVATE_PREVIEW_TTL = Duration.ofMinutes(2);

    private final AnuncioMidiaRepository midiaRepository;
    private final ArquivoMidiaRepository arquivoRepository;
    private final DocumentoUsuarioRepository documentoRepository;
    private final ObjectProvider<ObjectStorage> storageProvider;
    private final R2StorageProperties properties;

    public AdminMidiaPreviewService(
            AnuncioMidiaRepository midiaRepository,
            ArquivoMidiaRepository arquivoRepository,
            DocumentoUsuarioRepository documentoRepository,
            ObjectProvider<ObjectStorage> storageProvider,
            R2StorageProperties properties) {
        this.midiaRepository = midiaRepository;
        this.arquivoRepository = arquivoRepository;
        this.documentoRepository = documentoRepository;
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AdminMidiaPreviewDto gerar(UUID id) {
        AnuncioMidiaEntity midia = midiaRepository.findById(id)
                .filter(item -> item.getTipo() != TipoAnuncioMidia.STORY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "midia nao encontrada"));
        if (midia.getArquivoMidiaId() == null
                || documentoRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(
                        midia.getArquivoMidiaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "midia nao encontrada");
        }
        ArquivoMidiaEntity arquivo = arquivoRepository.findById(midia.getArquivoMidiaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "arquivo da midia nao encontrado"));
        validarTipo(arquivo.getMimeType());

        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null || !properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "preview de midia indisponivel");
        }

        String key = arquivo.getChaveObjeto();
        if (properties.getPrivateMediaBucket().equals(arquivo.getBucket())
                && key != null
                && key.startsWith(properties.getPrivateMediaPrefix())) {
            if (!storage.exists(StorageArea.PRIVATE_MEDIA, key)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "objeto da midia nao encontrado");
            }
            OffsetDateTime expiraEm = OffsetDateTime.now(ZoneOffset.UTC).plus(PRIVATE_PREVIEW_TTL);
            URI url = storage.temporaryGetUrl(StorageArea.PRIVATE_MEDIA, key, PRIVATE_PREVIEW_TTL);
            return new AdminMidiaPreviewDto(url.toString(), expiraEm, false, arquivo.getMimeType());
        }

        if (properties.getPublicMediaBucket().equals(arquivo.getBucket())
                && key != null
                && key.startsWith(properties.getPublicMediaPrefix())) {
            if (midia.getTipo() != TipoAnuncioMidia.FOTO
                    || midia.getStatus() != StatusAnuncioMidia.PUBLICAVEL
                    || midia.getVisibilidadeMidia() != VisibilidadeMidia.LIVRE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "midia restrita nao possui preview publico");
            }
            if (!storage.exists(StorageArea.PUBLIC_MEDIA, key)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "objeto da midia nao encontrado");
            }
            URI url = storage.publicUrl(StorageArea.PUBLIC_MEDIA, key)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "url publica da midia indisponivel"));
            return new AdminMidiaPreviewDto(url.toString(), null, true, arquivo.getMimeType());
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "midia fora da area canonica");
    }

    private void validarTipo(String mimeType) {
        if (mimeType == null || (!mimeType.startsWith("image/") && !mimeType.startsWith("video/"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tipo de midia incompativel");
        }
    }
}
