package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService;
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
import org.springframework.test.util.ReflectionTestUtils;

class MidiaStorageAprovacaoServiceTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    private final R2StorageProperties properties = properties();
    private final MidiaRestritaDerivacaoService derivacaoService = mock(MidiaRestritaDerivacaoService.class);
    private final MidiaStorageAprovacaoService service =
            new MidiaStorageAprovacaoService(provider, properties, derivacaoService);

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
        ReflectionTestUtils.setField(arquivo, "mimeType", "video/mp4");

        service.prepararAprovacao(arquivo, VisibilidadeMidia.RESTRITA_18);

        assertThat(arquivo.getBucket()).isEqualTo("privadas");
        assertThat(arquivo.getChaveObjeto()).startsWith("hml/midias-pendentes/");
        verifyNoInteractions(storage);
    }

    @Test
    void fotoRestritaGaranteDerivacaoBorradaDentroDaTransacao() {
        ArquivoMidiaEntity arquivo = arquivoPrivado("hml/midias-pendentes/anuncios/a/foto.jpg");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prepararAprovacao(arquivo, VisibilidadeMidia.RESTRITA_18);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(derivacaoService).garantir(arquivo);
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

    @Test
    void fotoPublicaEhMovidaParaPrivadaAntesDoCommitDaRestricao() {
        String publicObjectPath = "hml/midias-aprovadas/anuncios/a/foto.jpg";
        String restrictedObjectPath = "hml/midias-pendentes/anuncios/a/foto.jpg";
        ArquivoMidiaEntity arquivo = arquivoPublico(publicObjectPath);
        StoredObject object = new StoredObject(new byte[] {4, 5, 6}, "image/jpeg");
        when(storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath)).thenReturn(object);
        when(storage.get(StorageArea.PRIVATE_MEDIA, restrictedObjectPath)).thenReturn(object);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prepararReclassificacao(arquivo, VisibilidadeMidia.LIVRE, VisibilidadeMidia.RESTRITA_18);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(arquivo.getBucket()).isEqualTo("privadas");
        assertThat(arquivo.getChaveObjeto()).isEqualTo(restrictedObjectPath);
        verify(storage).putIfAbsent(StorageArea.PRIVATE_MEDIA, restrictedObjectPath, object.content(), object.contentType());
        verify(storage).delete(StorageArea.PUBLIC_MEDIA, publicObjectPath);
        verify(storage, never()).delete(StorageArea.PRIVATE_MEDIA, restrictedObjectPath);
    }

    @Test
    void rollbackDaRestricaoRestauraPublicaERemovePrivadaCriada() {
        String publicObjectPath = "hml/midias-aprovadas/anuncios/a/foto.jpg";
        String restrictedObjectPath = "hml/midias-pendentes/anuncios/a/foto.jpg";
        ArquivoMidiaEntity arquivo = arquivoPublico(publicObjectPath);
        StoredObject object = new StoredObject(new byte[] {7, 8, 9}, "image/jpeg");
        when(storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath)).thenReturn(object);
        when(storage.get(StorageArea.PRIVATE_MEDIA, restrictedObjectPath)).thenReturn(object);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prepararReclassificacao(arquivo, VisibilidadeMidia.LIVRE, VisibilidadeMidia.RESTRITA_18);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storage).putIfAbsent(StorageArea.PUBLIC_MEDIA, publicObjectPath, object.content(), object.contentType());
        verify(storage).delete(StorageArea.PRIVATE_MEDIA, restrictedObjectPath);
    }

    @Test
    void falhaAoRetirarObjetoPublicoJaPossuiCompensacaoRegistrada() {
        String publicObjectPath = "hml/midias-aprovadas/anuncios/a/foto.jpg";
        String restrictedObjectPath = "hml/midias-pendentes/anuncios/a/foto.jpg";
        ArquivoMidiaEntity arquivo = arquivoPublico(publicObjectPath);
        StoredObject object = new StoredObject(new byte[] {9, 8, 7}, "image/jpeg");
        when(storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath)).thenReturn(object);
        when(storage.get(StorageArea.PRIVATE_MEDIA, restrictedObjectPath)).thenReturn(object);
        doThrow(new IllegalStateException("falha sintetica de delete"))
                .when(storage).delete(StorageArea.PUBLIC_MEDIA, publicObjectPath);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.prepararReclassificacao(
                    arquivo,
                    VisibilidadeMidia.LIVRE,
                    VisibilidadeMidia.RESTRITA_18))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1)
                    .allSatisfy(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storage).putIfAbsent(StorageArea.PUBLIC_MEDIA, publicObjectPath, object.content(), object.contentType());
        verify(storage).delete(StorageArea.PRIVATE_MEDIA, restrictedObjectPath);
    }

    @Test
    void reclassificacaoRestritaParaLivreReutilizaPromocaoCanonica() {
        String restrictedObjectPath = "hml/midias-pendentes/anuncios/a/foto.jpg";
        String publicObjectPath = "hml/midias-aprovadas/anuncios/a/foto.jpg";
        ArquivoMidiaEntity arquivo = arquivoPrivado(restrictedObjectPath);
        StoredObject object = new StoredObject(new byte[] {1, 3, 5}, "image/jpeg");
        when(storage.get(StorageArea.PRIVATE_MEDIA, restrictedObjectPath)).thenReturn(object);
        when(storage.get(StorageArea.PUBLIC_MEDIA, publicObjectPath)).thenReturn(object);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.prepararReclassificacao(arquivo, VisibilidadeMidia.RESTRITA_18, VisibilidadeMidia.LIVRE);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(arquivo.getBucket()).isEqualTo("publicas");
        assertThat(arquivo.getChaveObjeto()).isEqualTo(publicObjectPath);
        verify(storage).delete(StorageArea.PRIVATE_MEDIA, restrictedObjectPath);
    }

    private ArquivoMidiaEntity arquivoPrivado(String key) {
        return ArquivoMidiaEntity.criarUploadPendente(
                UUID.randomUUID(), "R2", "privadas", key, "arquivo", "image/jpeg",
                3, 1, 1, null, "a".repeat(64), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private ArquivoMidiaEntity arquivoPublico(String key) {
        ArquivoMidiaEntity arquivo = arquivoPrivado(key);
        ReflectionTestUtils.setField(arquivo, "bucket", "publicas");
        return arquivo;
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
