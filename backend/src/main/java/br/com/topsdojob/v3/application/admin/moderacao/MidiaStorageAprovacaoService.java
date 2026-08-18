package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MidiaStorageAprovacaoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MidiaStorageAprovacaoService.class);

    private final ObjectProvider<ObjectStorage> storageProvider;
    private final R2StorageProperties properties;
    private final MidiaRestritaDerivacaoService derivacaoService;

    public MidiaStorageAprovacaoService(
            ObjectProvider<ObjectStorage> storageProvider,
            R2StorageProperties properties,
            MidiaRestritaDerivacaoService derivacaoService) {
        this.storageProvider = storageProvider;
        this.properties = properties;
        this.derivacaoService = derivacaoService;
    }

    public void prepararAprovacao(ArquivoMidiaEntity arquivo, VisibilidadeMidia visibilidade) {
        if (arquivo == null || !"R2".equals(arquivo.getStorageProvider())) {
            return;
        }
        if (visibilidade == VisibilidadeMidia.RESTRITA_18) {
            if (arquivo.getMimeType() == null
                    || !arquivo.getMimeType().toLowerCase(Locale.ROOT).startsWith("image/")) {
                return;
            }
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("Aprovacao de midia restrita exige transacao ativa");
            }
            derivacaoService.garantir(arquivo);
            return;
        }
        if (visibilidade != VisibilidadeMidia.LIVRE) {
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
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Promocao de midia exige transacao ativa");
        }

        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null || !properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de midia indisponivel");
        }
        long inicio = System.nanoTime();
        String privateObjectPath = arquivo.getChaveObjeto();
        String relativePath = privateObjectPath.substring(properties.getPrivateMediaPrefix().length());
        String publicObjectPath = properties.getPublicMediaPrefix() + relativePath;
        StoredObject object = storage.get(StorageArea.PRIVATE_MEDIA, privateObjectPath);
        long fimLeitura = System.nanoTime();
        ObjectWriteResult writeResult = storage.putIfAbsent(
                StorageArea.PUBLIC_MEDIA, publicObjectPath, object.content(), object.contentType());
        long fimEscrita = System.nanoTime();
        if (writeResult == ObjectWriteResult.ALREADY_EXISTS) {
            StoredObject publicObject = storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath);
            validarCopia(object, publicObject, "objeto publico diverge da midia processada", () -> { });
        }
        long fimValidacao = System.nanoTime();
        LOGGER.info(
                "Promocao de midia livre concluida: leituraR2Ms={}, escritaR2Ms={}, validacaoR2Ms={}, totalMs={}, resultado={}",
                millis(inicio, fimLeitura),
                millis(fimLeitura, fimEscrita),
                millis(fimEscrita, fimValidacao),
                millis(inicio, fimValidacao),
                writeResult);
        arquivo.moverNoStorage(properties.getPublicMediaBucket(), publicObjectPath);
        reconciliarDepoisDaTransacao(
                storage, privateObjectPath, publicObjectPath, writeResult == ObjectWriteResult.CREATED);
    }

    public void prepararReclassificacao(
            ArquivoMidiaEntity arquivo,
            VisibilidadeMidia visibilidadeAtual,
            VisibilidadeMidia novaVisibilidade) {
        if (arquivo == null || visibilidadeAtual == novaVisibilidade) {
            return;
        }
        if (!"R2".equals(arquivo.getStorageProvider())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "reclassificacao exige storage R2 canonico");
        }
        if (novaVisibilidade == VisibilidadeMidia.LIVRE) {
            prepararAprovacao(arquivo, novaVisibilidade);
            return;
        }
        if (novaVisibilidade != VisibilidadeMidia.RESTRITA_18
                || visibilidadeAtual != VisibilidadeMidia.LIVRE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "transicao de visibilidade invalida");
        }
        if (!properties.getPublicMediaBucket().equals(arquivo.getBucket())
                || arquivo.getChaveObjeto() == null
                || !arquivo.getChaveObjeto().startsWith(properties.getPublicMediaPrefix())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "midia nao esta na area publica canonica");
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Reclassificacao de midia exige transacao ativa");
        }

        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null || !properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de midia indisponivel");
        }
        derivacaoService.garantir(arquivo);
        String publicObjectPath = arquivo.getChaveObjeto();
        String relativePath = publicObjectPath.substring(properties.getPublicMediaPrefix().length());
        String privateObjectPath = properties.getPrivateMediaPrefix() + relativePath;
        StoredObject object = storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath);
        ObjectWriteResult writeResult = storage.putIfAbsent(
                StorageArea.PRIVATE_MEDIA, privateObjectPath, object.content(), object.contentType());
        StoredObject privateObject = storage.get(StorageArea.PRIVATE_MEDIA, privateObjectPath);
        validarCopia(object, privateObject, "objeto privado diverge da midia publica", () -> {
            if (writeResult == ObjectWriteResult.CREATED) {
                storage.delete(StorageArea.PRIVATE_MEDIA, privateObjectPath);
            }
        });

        compensarRestricaoDepoisDaTransacao(
                storage,
                object,
                publicObjectPath,
                privateObjectPath,
                writeResult == ObjectWriteResult.CREATED);

        storage.delete(StorageArea.PUBLIC_MEDIA, publicObjectPath);
        if (storage.exists(StorageArea.PUBLIC_MEDIA, publicObjectPath)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "objeto publico permaneceu acessivel");
        }

        arquivo.moverNoStorage(properties.getPrivateMediaBucket(), privateObjectPath);
    }

    private void reconciliarDepoisDaTransacao(
            ObjectStorage storage,
            String privateObjectPath,
            String publicObjectPath,
            boolean publicObjectCreated
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        storage.delete(StorageArea.PRIVATE_MEDIA, privateObjectPath);
                    } else if (publicObjectCreated) {
                        storage.delete(StorageArea.PUBLIC_MEDIA, publicObjectPath);
                    }
                } catch (RuntimeException ignored) {
                    // A reconciliacao operacional permanece segura: nunca publica a chave privada.
                }
            }
        });
    }

    private void compensarRestricaoDepoisDaTransacao(
            ObjectStorage storage,
            StoredObject object,
            String publicObjectPath,
            String privateObjectPath,
            boolean privateObjectCreated) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    storage.putIfAbsent(
                            StorageArea.PUBLIC_MEDIA,
                            publicObjectPath,
                            object.content(),
                            object.contentType());
                    StoredObject restored = storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath);
                    validarCopia(object, restored, "falha ao restaurar objeto publico", () -> { });
                    if (privateObjectCreated) {
                        storage.delete(StorageArea.PRIVATE_MEDIA, privateObjectPath);
                    }
                } catch (RuntimeException ignored) {
                    // A divergencia fica fail-closed na referencia transacional e exige reconciliacao operacional.
                }
            }
        });
    }

    private void validarCopia(
            StoredObject origem,
            StoredObject destino,
            String mensagem,
            Runnable compensacao) {
        if (!sha256(origem.content()).equals(sha256(destino.content()))
                || !mime(origem.contentType()).equals(mime(destino.contentType()))) {
            compensacao.run();
            throw new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private String mime(String value) {
        return value == null ? "application/octet-stream" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private long millis(long inicio, long fim) {
        return Math.max(0L, (fim - inicio) / 1_000_000L);
    }
}
