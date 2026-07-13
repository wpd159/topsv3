package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MidiaStorageAprovacaoService {

    private final ObjectProvider<ObjectStorage> storageProvider;
    private final R2StorageProperties properties;

    public MidiaStorageAprovacaoService(
            ObjectProvider<ObjectStorage> storageProvider,
            R2StorageProperties properties) {
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    public void prepararAprovacao(ArquivoMidiaEntity arquivo, VisibilidadeMidia visibilidade) {
        if (arquivo == null || visibilidade != VisibilidadeMidia.LIVRE || !"R2".equals(arquivo.getStorageProvider())) {
            return;
        }
        if (properties.getPublicMediaBucket().equals(arquivo.getBucket())
                && arquivo.getChaveObjeto().startsWith(properties.getPublicMediaPrefix())) {
            return;
        }
        if (!properties.getPrivateMediaBucket().equals(arquivo.getBucket())
                || arquivo.getChaveObjeto() == null
                || !arquivo.getChaveObjeto().startsWith(properties.getPrivateMediaPrefix())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "midia nao esta na area privada canonica");
        }

        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null || !properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de midia indisponivel");
        }
        String privateObjectPath = arquivo.getChaveObjeto();
        String relativePath = privateObjectPath.substring(properties.getPrivateMediaPrefix().length());
        String publicObjectPath = properties.getPublicMediaPrefix() + relativePath;
        StoredObject object = storage.get(StorageArea.PRIVATE_MEDIA, privateObjectPath);
        storage.put(StorageArea.PUBLIC_MEDIA, publicObjectPath, object.content(), object.contentType());
        arquivo.moverNoStorage(properties.getPublicMediaBucket(), publicObjectPath);
        reconciliarDepoisDaTransacao(storage, privateObjectPath, publicObjectPath);
    }

    private void reconciliarDepoisDaTransacao(
            ObjectStorage storage,
            String privateObjectPath,
            String publicObjectPath
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        storage.delete(StorageArea.PRIVATE_MEDIA, privateObjectPath);
                    } else {
                        storage.delete(StorageArea.PUBLIC_MEDIA, publicObjectPath);
                    }
                } catch (RuntimeException ignored) {
                    // A reconciliacao operacional permanece segura: nunca publica a chave privada.
                }
            }
        });
    }
}
