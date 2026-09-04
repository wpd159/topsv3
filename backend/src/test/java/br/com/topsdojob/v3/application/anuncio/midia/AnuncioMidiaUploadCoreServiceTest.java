package br.com.topsdojob.v3.application.anuncio.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AnuncioMidiaUploadCoreServiceTest {

    private static final UUID ANUNCIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
    private final FotoUploadProcessor fotoProcessor = mock(FotoUploadProcessor.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    private final R2StorageProperties storageProperties = storageProperties();
    private final AnuncioMidiaUploadCoreService service = new AnuncioMidiaUploadCoreService(
            midiaRepository, arquivoRepository, validator, fotoProcessor, storageProperties, storageProvider);
    private final List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
    private final Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
    private final Map<String, StoredObject> objetos = new LinkedHashMap<>();
    private final AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
            ANUNCIO_ID, UUID.randomUUID(), "anuncio", "Anuncio", "Descricao",
            StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO,
            OffsetDateTime.now(ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(midiaRepository.findByAnuncioIdForUpdate(ANUNCIO_ID))
                .thenAnswer(ignored -> List.copyOf(vinculos));
        when(midiaRepository.findByIdInForUpdate(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = (List<UUID>) invocation.getArgument(0);
            return vinculos.stream().filter(item -> ids.contains(item.getId())).toList();
        });
        when(arquivoRepository.findByIdInForUpdate(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = (List<UUID>) invocation.getArgument(0);
            return ids.stream().map(arquivos::get).filter(java.util.Objects::nonNull).toList();
        });
        when(midiaRepository.findByArquivoMidiaId(any())).thenAnswer(invocation ->
                vinculos.stream()
                        .filter(item -> item.getArquivoMidiaId().equals(invocation.getArgument(0)))
                        .toList());
        when(midiaRepository.existsDocumentoUsuarioHistoricoPorArquivoId(any())).thenReturn(false);
        when(arquivoRepository.save(any())).thenAnswer(invocation -> {
            ArquivoMidiaEntity value = invocation.getArgument(0);
            arquivos.put(value.getId(), value);
            return value;
        });
        when(midiaRepository.save(any())).thenAnswer(invocation -> {
            AnuncioMidiaEntity value = invocation.getArgument(0);
            vinculos.add(value);
            return value;
        });
        when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(1);
                    if (objetos.containsKey(key)) return ObjectWriteResult.ALREADY_EXISTS;
                    objetos.put(key, new StoredObject(invocation.getArgument(2), invocation.getArgument(3)));
                    return ObjectWriteResult.CREATED;
                });
        when(storage.get(eq(StorageArea.PRIVATE_MEDIA), any())).thenAnswer(invocation ->
                objetos.get(invocation.getArgument(1)));
        when(fotoProcessor.processar(any())).thenReturn(processada());
    }

    @Test
    void adminCriaFotoPrivadaPendenteGaleriaSemNomeOriginal() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false, "a".repeat(64)));

        var resultado = service.enviarAdministrativo(anuncio, multipart, "admin-foto-1");

        assertThat(resultado.idempotente()).isFalse();
        assertThat(resultado.itemUnico()).satisfies(item -> {
            assertThat(item.anuncioId()).isEqualTo(ANUNCIO_ID);
            assertThat(item.tipo()).isEqualTo(TipoAnuncioMidia.FOTO);
            assertThat(item.finalidade()).isEqualTo(FinalidadeAnuncioMidia.GALERIA);
            assertThat(item.ordem()).isZero();
            assertThat(item.status()).isEqualTo(StatusAnuncioMidia.PENDENTE);
            assertThat(item.statusArquivo()).isEqualTo(StatusArquivoMidia.PENDENTE);
        });
        assertThat(vinculos).singleElement().satisfies(item -> {
            assertThat(item.getVisibilidadeMidia()).isNull();
            assertThat(item.getFinalidade()).isEqualTo(FinalidadeAnuncioMidia.GALERIA);
        });
        assertThat(arquivos.values()).singleElement().satisfies(item -> {
            assertThat(item.getBucket()).isEqualTo("privadas");
            assertThat(item.getChaveObjeto()).startsWith("hml/midias-pendentes/anuncios/");
            assertThat(item.getNomeOriginal()).isNull();
            assertThat(item.getStatusArquivo()).isEqualTo(StatusArquivoMidia.PENDENTE);
        });
        verify(storage).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), eq("image/jpeg"));
        verify(storage, never()).putIfAbsent(eq(StorageArea.PUBLIC_MEDIA), any(), any(), any());
    }

    @Test
    void ownerPreservaSeedHistoricoEContratoDeVideo() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(true, "a".repeat(64)));

        var resultado = service.enviarProprietario(
                anuncio,
                List.of(multipart),
                "video-1",
                new AnuncioMidiaUploadCoreService.CapacidadeProprietario(4, 1, true));

        UUID arquivoEsperado = UUID.nameUUIDFromBytes(
                ("midia-upload-v1:arquivo:" + ANUNCIO_ID + ":video-1:0")
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(arquivos).containsKey(arquivoEsperado);
        assertThat(resultado.itemUnico().tipo()).isEqualTo(TipoAnuncioMidia.VIDEO);
        assertThat(vinculos.get(0).getVisibilidadeMidia()).isNotNull();
        verify(fotoProcessor, never()).processar(any());
    }

    @Test
    void retryComMesmosBytesRetornaMesmaMidiaSemReprocessar() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false, "a".repeat(64)));

        var primeiro = service.enviarAdministrativo(anuncio, multipart, "retry-1");
        vinculos.get(0).aplicarDecisao(
                StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, OffsetDateTime.now(ZoneOffset.UTC));
        arquivos.values().iterator().next().aplicarDecisao(StatusArquivoMidia.VALIDADO);
        var repetido = service.enviarAdministrativo(anuncio, multipart, "retry-1");

        assertThat(repetido.idempotente()).isTrue();
        assertThat(repetido.itemUnico().midiaId()).isEqualTo(primeiro.itemUnico().midiaId());
        assertThat(repetido.itemUnico().status()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
        assertThat(repetido.itemUnico().statusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);

        vinculos.get(0).removerLogicamente(OffsetDateTime.now(ZoneOffset.UTC));
        arquivos.values().iterator().next().aplicarDecisao(StatusArquivoMidia.REMOVIDO);
        var removido = service.enviarAdministrativo(anuncio, multipart, "retry-1");
        assertThat(removido.itemUnico().status()).isEqualTo(StatusAnuncioMidia.REMOVIDA);
        assertThat(removido.itemUnico().statusArquivo()).isEqualTo(StatusArquivoMidia.REMOVIDO);
        assertThat(vinculos).hasSize(1);
        assertThat(arquivos).hasSize(1);
        verify(fotoProcessor, times(1)).processar(any());
        verify(storage, times(1)).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any());
    }

    @Test
    void retryComBytesDiferentesRetornaConflitoAntesDeEscrever() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart))
                .thenReturn(validada(false, "a".repeat(64)), validada(false, "b".repeat(64)));

        service.enviarAdministrativo(anuncio, multipart, "retry-divergente");

        assertThatThrownBy(() -> service.enviarAdministrativo(anuncio, multipart, "retry-divergente"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(storage, times(1)).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any());
    }

    @Test
    void adminRecusaVideoAntesDeResolverOuEscreverStorage() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(true, "a".repeat(64)));

        assertThatThrownBy(() -> service.enviarAdministrativo(anuncio, multipart, "video-admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        verify(storageProvider, never()).getIfAvailable();
        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    }

    @Test
    void retryFalhaFechadoParaDocumentoHistoricoOuAssociacaoCruzada() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false, "a".repeat(64)));
        var criado = service.enviarAdministrativo(anuncio, multipart, "historico");
        UUID arquivoId = vinculos.get(0).getArquivoMidiaId();
        when(midiaRepository.existsDocumentoUsuarioHistoricoPorArquivoId(arquivoId)).thenReturn(true);

        assertThatThrownBy(() -> service.enviarAdministrativo(anuncio, multipart, "historico"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        when(midiaRepository.existsDocumentoUsuarioHistoricoPorArquivoId(arquivoId)).thenReturn(false);
        vinculos.add(AnuncioMidiaEntity.criarUploadPendente(
                UUID.randomUUID(), UUID.randomUUID(), arquivoId, TipoAnuncioMidia.FOTO, 0,
                OffsetDateTime.now(ZoneOffset.UTC)));
        assertThatThrownBy(() -> service.enviarAdministrativo(anuncio, multipart, "historico"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(criado.itemUnico().midiaId()).isNotNull();
    }

    @Test
    void rollbackCompensaSomenteObjetoCriadoNestaTentativa() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false, "a".repeat(64)));
        org.mockito.Mockito.doThrow(new IllegalStateException("falha banco"))
                .when(midiaRepository).save(any());
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.enviarAdministrativo(anuncio, multipart, "rollback-created"))
                    .isInstanceOf(IllegalStateException.class);
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(item ->
                    item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(storage).delete(eq(StorageArea.PRIVATE_MEDIA), any());

        org.mockito.Mockito.clearInvocations(storage);
        when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any()))
                .thenReturn(ObjectWriteResult.ALREADY_EXISTS);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.enviarAdministrativo(anuncio, multipart, "rollback-existing"))
                    .isInstanceOf(ResponseStatusException.class);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(storage, never()).delete(any(), any());
    }

    private MidiaValidada validada(boolean video, String sha) {
        return new MidiaValidada(
                new byte[] {1, 2, 3}, video, video ? "video/mp4" : "image/png",
                video ? "mp4" : "png", video ? "video.mp4" : "foto.png",
                video ? 720 : 2, video ? 1280 : 3, video ? 15_000L : null, sha);
    }

    private FotoProcessada processada() {
        return new FotoProcessada(
                new byte[] {9, 8, 7}, "image/jpeg", "jpg", 2, 3,
                "06df4f7e1394f1c57cc6583fba4d8060a5a66f4f4771c14aeff6b9af8a28c9b3",
                "a".repeat(64), 1, FotoUploadProcessor.WATERMARK_VERSION,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private R2StorageProperties storageProperties() {
        R2StorageProperties value = new R2StorageProperties();
        value.setEnabled(true);
        value.setPrivateMediaBucket("privadas");
        value.setPublicMediaBucket("publicas");
        value.setPrivateMediaPrefix("hml/midias-pendentes/");
        value.setPublicMediaPrefix("hml/midias-aprovadas/");
        return value;
    }
}
