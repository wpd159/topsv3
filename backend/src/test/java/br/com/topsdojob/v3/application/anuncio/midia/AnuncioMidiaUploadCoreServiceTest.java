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
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
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
import java.nio.ByteBuffer;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AnuncioMidiaUploadCoreServiceTest {

    private static final UUID ANUNCIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
    private final LimiteMidiasAnuncioService limiteService = mock(LimiteMidiasAnuncioService.class);
    private final FotoUploadProcessor fotoProcessor = mock(FotoUploadProcessor.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    private final R2StorageProperties storageProperties = storageProperties();
    private final AnuncioMidiaUploadCoreService service = new AnuncioMidiaUploadCoreService(
            midiaRepository, arquivoRepository, validator, limiteService,
            fotoProcessor, storageProperties, storageProvider);
    private final List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
    private final Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
    private final Map<String, StoredObject> objetos = new LinkedHashMap<>();
    private final AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
            ANUNCIO_ID, UUID.randomUUID(), "anuncio", "Anuncio", "Descricao",
            StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO,
            OffsetDateTime.now(ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(4, 1, false, true));
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
    void ownerAceitaVideoValidadoSemDimensoesERepeteSemDuplicar() throws Exception {
        MockMultipartFile multipart = videoBasico();
        MidiaUploadValidator realValidator = new MidiaUploadValidator(new MidiaUploadProperties());
        assertThat(realValidator.validar(multipart)).satisfies(item -> {
            assertThat(item.video()).isTrue();
            assertThat(item.largura()).isNull();
            assertThat(item.altura()).isNull();
        });
        AnuncioMidiaUploadCoreService realService = serviceComValidador(realValidator);
        var capacidade = new AnuncioMidiaUploadCoreService.CapacidadeProprietario(4, 1, true);

        var primeiro = realService.enviarProprietario(anuncio, List.of(multipart), "video-sem-dimensoes", capacidade);
        var repetido = realService.enviarProprietario(anuncio, List.of(multipart), "video-sem-dimensoes", capacidade);

        assertThat(primeiro.idempotente()).isFalse();
        assertThat(repetido.idempotente()).isTrue();
        assertThat(repetido.itemUnico().midiaId()).isEqualTo(primeiro.itemUnico().midiaId());
        assertThat(primeiro.itemUnico().tipo()).isEqualTo(TipoAnuncioMidia.VIDEO);
        assertThat(arquivos.values()).singleElement().satisfies(item -> {
            assertThat(item.getLargura()).isNull();
            assertThat(item.getAltura()).isNull();
            assertThat(item.getDuracaoMs()).isNull();
            assertThat(item.getMimeType()).isEqualTo("video/mp4");
            assertThat(item.getStatusArquivo()).isEqualTo(StatusArquivoMidia.PENDENTE);
        });
        assertThat(vinculos).hasSize(1);
        assertThat(objetos).hasSize(1);
        verify(storage, times(1)).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), eq(multipart.getBytes()), eq("video/mp4"));
        verify(fotoProcessor, never()).processar(any());
    }

    @Test
    void adminAceitaVideoValidadoSemDimensoesPendenteRestrito() {
        MockMultipartFile multipart = videoBasico();
        MidiaUploadValidator realValidator = new MidiaUploadValidator(new MidiaUploadProperties());
        assertThat(realValidator.validar(multipart).video()).isTrue();

        var resultado = serviceComValidador(realValidator)
                .enviarAdministrativo(anuncio, multipart, "admin-video-valido");

        assertThat(resultado.itemUnico().tipo()).isEqualTo(TipoAnuncioMidia.VIDEO);
        assertThat(resultado.itemUnico().status()).isEqualTo(StatusAnuncioMidia.PENDENTE);
        assertThat(vinculos).singleElement().satisfies(item ->
                assertThat(item.getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18));
        assertThat(arquivos.values()).singleElement().satisfies(item -> {
            assertThat(item.getLargura()).isNull();
            assertThat(item.getAltura()).isNull();
        });
    }

    @Test
    void adminRepeteMesmoVideoSemDuplicarEBloqueiaOutraChaveNoLimite() {
        MockMultipartFile video = videoBasico();
        AnuncioMidiaUploadCoreService realService =
                serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()));

        var primeiro = realService.enviarAdministrativo(anuncio, video, "video-admin-1");
        var repetido = realService.enviarAdministrativo(anuncio, video, "video-admin-1");

        assertThat(repetido.idempotente()).isTrue();
        assertThat(repetido.itemUnico().midiaId()).isEqualTo(primeiro.itemUnico().midiaId());
        assertThatThrownBy(() -> realService.enviarAdministrativo(anuncio, video, "video-admin-2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("limite de video");
                });
        assertThat(vinculos).hasSize(1);
        assertThat(objetos).hasSize(1);
        verify(storage, times(1)).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), eq("video/mp4"));
    }

    @Test
    void adminVideoInvalidoEAcimaDoLimiteNaoCriamObjeto() throws Exception {
        MidiaUploadProperties propriedades = new MidiaUploadProperties();
        propriedades.setMaxVideoBytes(videoBasico().getSize() - 1);
        AnuncioMidiaUploadCoreService limitado = serviceComValidador(new MidiaUploadValidator(propriedades));
        assertThatThrownBy(() -> limitado.enviarAdministrativo(anuncio, videoBasico(), "video-grande"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        MockMultipartFile invalido = new MockMultipartFile(
                "arquivo", "video.mp4", "video/mp4", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()))
                .enviarAdministrativo(anuncio, invalido, "video-invalido"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        MockMultipartFile extensaoInvalida = new MockMultipartFile(
                "arquivo", "video.avi", "video/mp4", videoBasico().getBytes());
        assertThatThrownBy(() -> serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()))
                .enviarAdministrativo(anuncio, extensaoInvalida, "formato-invalido"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    }

    @Test
    void adminVideoCompensaObjetoCriadoQuandoVinculoFalha() {
        AnuncioMidiaUploadCoreService realService =
                serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()));
        org.mockito.Mockito.doThrow(new IllegalStateException("falha sintetica no vinculo"))
                .when(midiaRepository).save(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            objetos.remove(invocation.getArgument(1));
            return null;
        }).when(storage).delete(eq(StorageArea.PRIVATE_MEDIA), any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> realService.enviarAdministrativo(anuncio, videoBasico(), "video-falha"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("falha sintetica no vinculo");
            assertThat(objetos).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().forEach(item ->
                    item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(objetos).isEmpty();
        verify(storage, times(1)).delete(eq(StorageArea.PRIVATE_MEDIA), any());
    }

    @Test
    void adminVideoFalhaNaConfirmacaoStorageCompensaObjetoCriado() {
        AnuncioMidiaUploadCoreService realService =
                serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()));
        org.mockito.Mockito.doThrow(new IllegalStateException("falha sintetica no storage"))
                .when(storage).get(eq(StorageArea.PRIVATE_MEDIA), any());
        org.mockito.Mockito.doAnswer(invocation -> {
            objetos.remove(invocation.getArgument(1));
            return null;
        }).when(storage).delete(eq(StorageArea.PRIVATE_MEDIA), any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> realService.enviarAdministrativo(
                    anuncio, videoBasico(), "video-storage-falhou"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("falha sintetica no storage");
            assertThat(objetos).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations().forEach(item ->
                    item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(vinculos).isEmpty();
        assertThat(objetos).isEmpty();
        verify(storage).delete(eq(StorageArea.PRIVATE_MEDIA), any());
    }

    @Test
    void ownerVideoSemDimensoesPreservaRecusasDeBeneficioLimiteEFormato() {
        AnuncioMidiaUploadCoreService realService =
                serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()));
        MockMultipartFile video = videoBasico();

        assertThatThrownBy(() -> realService.enviarProprietario(anuncio, List.of(video), "sem-beneficio",
                new AnuncioMidiaUploadCoreService.CapacidadeProprietario(4, 0, false)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("beneficio Video");
                });
        assertThatThrownBy(() -> realService.enviarProprietario(anuncio, List.of(video), "limite-video",
                new AnuncioMidiaUploadCoreService.CapacidadeProprietario(4, 0, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("limite de video");
                });
        MockMultipartFile invalido = new MockMultipartFile(
                "arquivos", "video.mp4", "video/mp4", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> realService.enviarProprietario(anuncio, List.of(invalido), "formato-invalido",
                new AnuncioMidiaUploadCoreService.CapacidadeProprietario(4, 1, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        assertThat(arquivos).isEmpty();
        assertThat(vinculos).isEmpty();
        assertThat(objetos).isEmpty();
        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    }

    @Test
    void rollbackDeVideoSemDimensoesCompensaObjetoPrivadoCriado() {
        AnuncioMidiaUploadCoreService realService =
                serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()));
        org.mockito.Mockito.doThrow(new IllegalStateException("falha sintetica no vinculo"))
                .when(midiaRepository).save(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            objetos.remove(invocation.getArgument(1));
            return null;
        }).when(storage).delete(eq(StorageArea.PRIVATE_MEDIA), any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> realService.enviarProprietario(
                    anuncio, List.of(videoBasico()), "rollback-video",
                    new AnuncioMidiaUploadCoreService.CapacidadeProprietario(4, 1, true)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("falha sintetica no vinculo");
            assertThat(objetos).hasSize(1);
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(item ->
                    item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(objetos).isEmpty();
        verify(storage, times(1)).delete(eq(StorageArea.PRIVATE_MEDIA), any());
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
    void adminRecusaVideoSemBeneficioAntesDeEscreverStorage() {
        MultipartFile multipart = videoBasico();
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(4, 0, false, false));

        assertThatThrownBy(() -> serviceComValidador(new MidiaUploadValidator(new MidiaUploadProperties()))
                .enviarAdministrativo(anuncio, multipart, "video-admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("beneficio Video");
                });

        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
        verify(storageProvider, never()).getIfAvailable();
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

    private AnuncioMidiaUploadCoreService serviceComValidador(MidiaUploadValidator realValidator) {
        return new AnuncioMidiaUploadCoreService(
                midiaRepository, arquivoRepository, realValidator, limiteService, fotoProcessor,
                storageProperties, storageProvider);
    }

    private MockMultipartFile videoBasico() {
        byte[] ftyp = box("ftyp", concat("isom".getBytes(StandardCharsets.US_ASCII), new byte[4]));
        byte[] hdlr = box("hdlr", concat(new byte[8], "vide".getBytes(StandardCharsets.US_ASCII)));
        byte[] moov = box("moov", box("trak", box("mdia", hdlr)));
        byte[] mdat = box("mdat", new byte[] {1, 2, 3, 4});
        return new MockMultipartFile("arquivos", "video.mp4", "video/mp4", concat(ftyp, moov, mdat));
    }

    private byte[] box(String type, byte[] payload) {
        return concat(ByteBuffer.allocate(4).putInt(payload.length + 8).array(),
                type.getBytes(StandardCharsets.US_ASCII), payload);
    }

    private byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) size += part.length;
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
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
