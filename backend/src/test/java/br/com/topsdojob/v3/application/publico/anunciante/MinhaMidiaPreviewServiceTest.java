package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;

class MinhaMidiaPreviewServiceTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-13T12:00:00Z");
    private static final String OBJETO_PRIVADO = "hml/midias-pendentes/foto-sintetica.jpg";
    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    private final R2StorageProperties properties = new R2StorageProperties();
    private MinhaMidiaPreviewService service;

    @BeforeEach
    void setUp() {
        properties.setPrivateMediaBucket("privado");
        properties.setPublicMediaBucket("publico");
        when(provider.getIfAvailable()).thenReturn(storage);
        service = new MinhaMidiaPreviewService(properties, provider, Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void assinaPorCincoMinutosERenovaAExpiracaoEmNovaConsulta() {
        when(storage.temporaryGetUrl(StorageArea.PRIVATE_MEDIA, OBJETO_PRIVADO, Duration.ofMinutes(5)))
                .thenReturn(URI.create("https://storage.example.invalid/foto?assinatura=primeira"),
                        URI.create("https://storage.example.invalid/foto?assinatura=renovada"));
        var arquivo = arquivo("privado", OBJETO_PRIVADO);

        var primeira = service.resolver(arquivo);
        var renovada = new MinhaMidiaPreviewService(properties, provider,
                Clock.fixed(AGORA.plusMinutes(5).toInstant(), ZoneOffset.UTC)).resolver(arquivo);

        assertThat(primeira.url()).endsWith("assinatura=primeira");
        assertThat(primeira.expiraEm()).isEqualTo(AGORA.plusMinutes(5));
        assertThat(renovada.url()).endsWith("assinatura=renovada");
        assertThat(renovada.expiraEm()).isEqualTo(AGORA.plusMinutes(10));
    }

    @Test
    void midiaPublicaUsaUrlPermanenteSemExpiracao() {
        String chave = "hml/midias-aprovadas/foto.jpg";
        when(storage.publicUrl(StorageArea.PUBLIC_MEDIA, chave))
                .thenReturn(Optional.of(URI.create("https://cdn.example.invalid/foto.jpg")));

        var preview = service.resolver(arquivo("publico", chave));

        assertThat(preview.url()).isEqualTo("https://cdn.example.invalid/foto.jpg");
        assertThat(preview.expiraEm()).isNull();
        verify(storage).publicUrl(StorageArea.PUBLIC_MEDIA, chave);
    }

    @ParameterizedTest
    @CsvSource({
        "documentos,hml/documentos/documento.jpg",
        "outro,hml/midias-pendentes/foto.jpg",
        "privado,hml/documentos/documento.jpg",
        "publico,hml/midias-pendentes/foto.jpg"
    })
    void naoResolveObjetosForaDasAreasDeMidiaPermitidas(String bucket, String chave) {
        var preview = service.resolver(arquivo(bucket, chave));

        assertThat(preview.url()).isNull();
        assertThat(preview.expiraEm()).isNull();
        verifyNoInteractions(storage);
    }

    @Test
    void indisponibilidadeDoStorageOuArquivoAusenteNaoImpedeConsulta() {
        assertThat(service.resolver(null).url()).isNull();
        assertThat(service.resolver(arquivo("privado", null)).url()).isNull();
        when(provider.getIfAvailable()).thenReturn(null);

        var preview = service.resolver(arquivo("privado", OBJETO_PRIVADO));

        assertThat(preview.url()).isNull();
        assertThat(preview.expiraEm()).isNull();
        verifyNoInteractions(storage);
    }

    @Test
    void falhaNaAssinaturaNaoExpoeChaveOuExpiracaoSemUrl() {
        when(storage.temporaryGetUrl(StorageArea.PRIVATE_MEDIA, OBJETO_PRIVADO, Duration.ofMinutes(5)))
                .thenThrow(new IllegalStateException("storage sintetico indisponivel"));

        var preview = service.resolver(arquivo("privado", OBJETO_PRIVADO));

        assertThat(preview.url()).isNull();
        assertThat(preview.expiraEm()).isNull();
    }

    private ArquivoMidiaEntity arquivo(String bucket, String chave) {
        return ArquivoMidiaEntity.criarUploadPendente(new UUID(0, 101), "R2", bucket, chave,
                "foto-sintetica.jpg", "image/jpeg", 1024, 800, 1200, null, "a".repeat(64), AGORA);
    }
}
