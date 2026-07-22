package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MidiaStorageAprovacaoServiceTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    private final R2StorageProperties properties = properties();
    private final MidiaStorageAprovacaoService service = new MidiaStorageAprovacaoService(provider, properties);

    @BeforeEach
    void setUp() {
        when(provider.getIfAvailable()).thenReturn(storage);
        when(storage.putIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(ObjectWriteResult.CREATED);
    }

    @Test
    void fotoLivreAprovadaEhCopiadaParaAreaPublicaCanonica() {
        ArquivoMidiaEntity arquivo = arquivoPrivado("hml/midias-pendentes/anuncios/a/foto.jpg");
        when(storage.get(StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto()))
                .thenReturn(new StoredObject(new byte[] {1, 2, 3}, "image/jpeg"));
        when(storage.get(StorageArea.PUBLIC_MEDIA, "hml/midias-aprovadas/anuncios/a/foto.jpg"))
                .thenReturn(new StoredObject(new byte[] {1, 2, 3}, "image/jpeg"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prepararAprovacao(arquivo, VisibilidadeMidia.LIVRE);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(arquivo.getBucket()).isEqualTo("publicas");
        assertThat(arquivo.getChaveObjeto()).isEqualTo("hml/midias-aprovadas/anuncios/a/foto.jpg");
        verify(storage).putIfAbsent(
                StorageArea.PUBLIC_MEDIA, arquivo.getChaveObjeto(), new byte[] {1, 2, 3}, "image/jpeg");
        verify(storage).delete(StorageArea.PRIVATE_MEDIA, "hml/midias-pendentes/anuncios/a/foto.jpg");
    }

    @Test
    void rollbackRemoveSomenteCopiaPublicaCriadaEPreservaPrivada() {
        String privateObjectPath = "hml/midias-pendentes/anuncios/a/foto.jpg";
        String publicObjectPath = "hml/midias-aprovadas/anuncios/a/foto.jpg";
        ArquivoMidiaEntity arquivo = arquivoPrivado(privateObjectPath);
        when(storage.get(StorageArea.PRIVATE_MEDIA, privateObjectPath))
                .thenReturn(new StoredObject(new byte[] {1, 2, 3}, "image/jpeg"));
        when(storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath))
                .thenReturn(new StoredObject(new byte[] {1, 2, 3}, "image/jpeg"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prepararAprovacao(arquivo, VisibilidadeMidia.LIVRE);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storage).delete(StorageArea.PUBLIC_MEDIA, publicObjectPath);
        verify(storage, never()).delete(StorageArea.PRIVATE_MEDIA, privateObjectPath);
    }

    @Test
    void restritaPermanecePrivadaSemUrlPublicaPermanente() {
        ArquivoMidiaEntity arquivo = arquivoPrivado("hml/midias-pendentes/anuncios/a/video.mp4");

        service.prepararAprovacao(arquivo, VisibilidadeMidia.RESTRITA_18);

        assertThat(arquivo.getBucket()).isEqualTo("privadas");
        assertThat(arquivo.getChaveObjeto()).startsWith("hml/midias-pendentes/");
        verifyNoInteractions(storage);
    }

    @Test
    void fotoLivreFalhaFechadoSemTransacaoParaNaoDeixarDuasCopiasPermanentes() {
        ArquivoMidiaEntity arquivo = arquivoPrivado("hml/midias-pendentes/anuncios/a/foto.jpg");

        assertThatThrownBy(() -> service.prepararAprovacao(arquivo, VisibilidadeMidia.LIVRE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transacao ativa");
        verifyNoInteractions(storage);
    }

    @Test
    void rejeitaObjetoForaDaAreaPrivadaCanonica() {
        ArquivoMidiaEntity arquivo = arquivoPrivado("producao/midias/foto.jpg");

        assertThatThrownBy(() -> service.prepararAprovacao(arquivo, VisibilidadeMidia.LIVRE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private ArquivoMidiaEntity arquivoPrivado(String key) {
        return ArquivoMidiaEntity.criarUploadPendente(
                UUID.randomUUID(), "R2", "privadas", key, "arquivo", "image/jpeg",
                3, 1, 1, null, "a".repeat(64), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private R2StorageProperties properties() {
        R2StorageProperties value = new R2StorageProperties();
        value.setEnabled(true);
        value.setPublicMediaBucket("publicas");
        value.setPrivateMediaBucket("privadas");
        value.setPublicMediaPrefix("hml/midias-aprovadas/");
        value.setPrivateMediaPrefix("hml/midias-pendentes/");
        return value;
    }
}
