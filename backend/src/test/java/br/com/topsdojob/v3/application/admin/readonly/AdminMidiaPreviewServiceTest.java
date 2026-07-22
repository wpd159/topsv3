package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Constructor;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminMidiaPreviewServiceTest {

    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    private final R2StorageProperties properties = new R2StorageProperties();
    private AdminMidiaPreviewService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setPublicMediaBucket("midias-publicas");
        properties.setPrivateMediaBucket("midias-privadas");
        properties.setPublicMediaPrefix("hml/preprod/midias-aprovadas/");
        properties.setPrivateMediaPrefix("hml/preprod/midias-pendentes/");
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        service = new AdminMidiaPreviewService(
                midiaRepository,
                arquivoRepository,
                documentoRepository,
                storageProvider,
                properties);
    }

    @Test
    void midiaPrivadaUsaUrlTemporariaCurtaSemExporChaveNoDto() {
        UUID id = fixture(
                TipoAnuncioMidia.FOTO,
                "midias-privadas",
                "hml/preprod/midias-pendentes/foto.webp",
                "image/webp");
        when(storage.exists(StorageArea.PRIVATE_MEDIA, "hml/preprod/midias-pendentes/foto.webp"))
                .thenReturn(true);
        when(storage.temporaryGetUrl(
                eq(StorageArea.PRIVATE_MEDIA),
                eq("hml/preprod/midias-pendentes/foto.webp"),
                any(Duration.class)))
                .thenReturn(URI.create("https://preview.example.invalid/token-curto"));

        var response = service.gerar(id);

        assertThat(response.publica()).isFalse();
        assertThat(response.expiraEm()).isNotNull();
        assertThat(response.url()).isEqualTo("https://preview.example.invalid/token-curto");
        verify(storage).temporaryGetUrl(
                StorageArea.PRIVATE_MEDIA,
                "hml/preprod/midias-pendentes/foto.webp",
                Duration.ofMinutes(2));
        verify(storage, never()).publicUrl(any(), any());
    }

    @Test
    void fotoPublicaUsaSomenteAreaPublicaCanonico() {
        UUID id = fixture(
                TipoAnuncioMidia.FOTO,
                "midias-publicas",
                "hml/preprod/midias-aprovadas/foto.webp",
                "image/webp");
        when(storage.exists(StorageArea.PUBLIC_MEDIA, "hml/preprod/midias-aprovadas/foto.webp"))
                .thenReturn(true);
        when(storage.publicUrl(StorageArea.PUBLIC_MEDIA, "hml/preprod/midias-aprovadas/foto.webp"))
                .thenReturn(Optional.of(URI.create("https://public.example.invalid/foto.webp")));

        var response = service.gerar(id);

        assertThat(response.publica()).isTrue();
        assertThat(response.expiraEm()).isNull();
        assertThat(response.url()).isEqualTo("https://public.example.invalid/foto.webp");
        verify(storage, never()).temporaryGetUrl(any(), any(), any());
    }

    @Test
    void videoOuFotoRestritaNuncaRecebemUrlPublica() {
        UUID videoId = fixture(
                TipoAnuncioMidia.VIDEO,
                "midias-publicas",
                "hml/preprod/midias-aprovadas/video.mp4",
                "video/mp4");
        AnuncioMidiaEntity video = midiaRepository.findById(videoId).orElseThrow();
        ReflectionTestUtils.setField(video, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);

        UUID fotoId = fixture(
                TipoAnuncioMidia.FOTO,
                "midias-publicas",
                "hml/preprod/midias-aprovadas/restrita.webp",
                "image/webp");
        AnuncioMidiaEntity foto = midiaRepository.findById(fotoId).orElseThrow();
        ReflectionTestUtils.setField(foto, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);

        assertThatThrownBy(() -> service.gerar(videoId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThatThrownBy(() -> service.gerar(fotoId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(storage, never()).publicUrl(any(), any());
    }

    @Test
    void storyEDocumentoNaoRecebemPreviewDeModeracao() {
        UUID storyId = fixture(
                TipoAnuncioMidia.STORY,
                "midias-privadas",
                "hml/preprod/midias-pendentes/story.webp",
                "image/webp");

        assertThatThrownBy(() -> service.gerar(storyId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        UUID documentoId = fixture(
                TipoAnuncioMidia.FOTO,
                "midias-privadas",
                "hml/preprod/midias-pendentes/documento.jpg",
                "image/jpeg");
        ArquivoMidiaEntity arquivo = arquivoRepository.findById(
                midiaRepository.findById(documentoId).orElseThrow().getArquivoMidiaId()).orElseThrow();
        when(documentoRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivo.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.gerar(documentoId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(storage, never()).temporaryGetUrl(any(), any(), any());
        verify(storage, never()).publicUrl(any(), any());
    }

    private UUID fixture(TipoAnuncioMidia tipo, String bucket, String key, String mimeType) {
        UUID id = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = entity(AnuncioMidiaEntity.class);
        ReflectionTestUtils.setField(midia, "id", id);
        ReflectionTestUtils.setField(midia, "arquivoMidiaId", arquivoId);
        ReflectionTestUtils.setField(midia, "tipo", tipo);
        ReflectionTestUtils.setField(
                midia,
                "status",
                "midias-publicas".equals(bucket) ? StatusAnuncioMidia.PUBLICAVEL : StatusAnuncioMidia.PENDENTE);
        ReflectionTestUtils.setField(
                midia,
                "visibilidadeMidia",
                "midias-publicas".equals(bucket) ? VisibilidadeMidia.LIVRE : null);
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        ReflectionTestUtils.setField(arquivo, "id", arquivoId);
        ReflectionTestUtils.setField(arquivo, "storageProvider", "R2");
        ReflectionTestUtils.setField(arquivo, "bucket", bucket);
        ReflectionTestUtils.setField(arquivo, "chaveObjeto", key);
        ReflectionTestUtils.setField(arquivo, "mimeType", mimeType);
        when(midiaRepository.findById(id)).thenReturn(Optional.of(midia));
        when(arquivoRepository.findById(arquivoId)).thenReturn(Optional.of(arquivo));
        when(documentoRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivoId))
                .thenReturn(false);
        return id;
    }

    private <T> T entity(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
