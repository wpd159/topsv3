package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.ReordenarMinhasMidiasRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
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
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.net.URI;
import java.time.Clock;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MinhasMidiasServiceTest {

    private static final UUID ANUNCIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SLUG = "anuncio-proprio";
    private static final OffsetDateTime AGORA_PREVIEW = OffsetDateTime.parse("2026-09-13T12:00:00Z");

    private final MeusAnunciosConsultaService consultaService = mock(MeusAnunciosConsultaService.class);
    private final MeuAnuncioCicloVidaService cicloVidaService = mock(MeuAnuncioCicloVidaService.class);
    private final ArquivoPublicidadeRegistroService arquivoPublicidade = mock(ArquivoPublicidadeRegistroService.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final LimiteMidiasAnuncioService limiteService = mock(LimiteMidiasAnuncioService.class);
    private final MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
    private final FotoUploadProcessor fotoProcessor = mock(FotoUploadProcessor.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    private final List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
    private final Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
    private final Map<String, StoredObject> objetos = new LinkedHashMap<>();
    private final Authentication authentication = mock(Authentication.class);
    private final R2StorageProperties storageProperties = storageProperties();
    private final FotoElegivelAnuncioPolicy fotoElegivelPolicy = mock(FotoElegivelAnuncioPolicy.class);
    private final AnuncioMidiaUploadCoreService uploadCoreService = new AnuncioMidiaUploadCoreService(
            midiaRepository, arquivoRepository, validator, fotoProcessor, storageProperties, storageProvider);
    private final MinhasMidiasService service = new MinhasMidiasService(
            consultaService, midiaRepository, arquivoRepository, revisaoRepository, limiteService,
            new MidiaUploadProperties(), new MinhaMidiaPreviewService(storageProperties, storageProvider,
                    Clock.fixed(AGORA_PREVIEW.toInstant(), ZoneOffset.UTC)),
            uploadCoreService, fotoElegivelPolicy, cicloVidaService, arquivoPublicidade);

    @BeforeEach
    void setUp() {
        AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID, UUID.randomUUID(), SLUG, "Perfil de teste", "Descricao publica de teste",
                StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, OffsetDateTime.now(ZoneOffset.UTC));
        when(consultaService.anuncioDoUsuario(SLUG, authentication)).thenReturn(anuncio);
        when(consultaService.anuncioDoUsuarioParaAtualizacao(SLUG, authentication)).thenReturn(anuncio);
        when(consultaService.anuncioDoUsuarioParaRemocaoMidia(SLUG, authentication)).thenReturn(anuncio);
        when(midiaRepository.findFotosValidasAtivasIds(ANUNCIO_ID)).thenAnswer(ignored -> vinculos.stream()
                .filter(item -> item.getTipo() == TipoAnuncioMidia.FOTO && item.getStatus() != StatusAnuncioMidia.REMOVIDA)
                .map(AnuncioMidiaEntity::getId).toList());
        when(revisaoRepository.existsByAnuncioIdAndStatusIn(eq(ANUNCIO_ID), anyList())).thenReturn(false);
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(4, 0, false, false));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storage.temporaryGetUrl(eq(StorageArea.PRIVATE_MEDIA), any(), any()))
                .thenReturn(URI.create("https://privado.invalid/temporaria"));
        when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1);
            if (objetos.containsKey(key)) return ObjectWriteResult.ALREADY_EXISTS;
            objetos.put(key, new StoredObject(invocation.getArgument(2), invocation.getArgument(3)));
            return ObjectWriteResult.CREATED;
        });
        when(storage.get(eq(StorageArea.PRIVATE_MEDIA), any())).thenAnswer(invocation ->
                objetos.get(invocation.getArgument(1)));
        when(fotoProcessor.processar(any())).thenReturn(processada());
        when(midiaRepository.findByAnuncioId(ANUNCIO_ID)).thenAnswer(ignored -> List.copyOf(vinculos));
        when(midiaRepository.findByAnuncioIdForUpdate(ANUNCIO_ID)).thenAnswer(ignored -> vinculos.stream()
                .filter(item -> ANUNCIO_ID.equals(item.getAnuncioId()))
                .toList());
        when(midiaRepository.findByIdInForUpdate(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = (List<UUID>) invocation.getArgument(0);
            return vinculos.stream().filter(item -> ids.contains(item.getId())).toList();
        });
        when(midiaRepository.findByArquivoMidiaId(any())).thenAnswer(invocation ->
                vinculos.stream()
                        .filter(item -> item.getArquivoMidiaId().equals(invocation.getArgument(0)))
                        .toList());
        when(midiaRepository.existsDocumentoUsuarioHistoricoPorArquivoId(any())).thenReturn(false);
        when(midiaRepository.findById(any())).thenAnswer(invocation ->
                vinculos.stream().filter(item -> item.getId().equals(invocation.getArgument(0))).findFirst());
        when(midiaRepository.existsById(any())).thenAnswer(invocation ->
                vinculos.stream().anyMatch(item -> item.getId().equals(invocation.getArgument(0))));
        when(midiaRepository.save(any())).thenAnswer(invocation -> {
            AnuncioMidiaEntity value = invocation.getArgument(0);
            if (!vinculos.contains(value)) vinculos.add(value);
            return value;
        });
        when(arquivoRepository.findByIdIn(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = (List<UUID>) invocation.getArgument(0);
            return ids.stream().map(arquivos::get).filter(java.util.Objects::nonNull).toList();
        });
        when(arquivoRepository.findById(any())).thenAnswer(invocation ->
                java.util.Optional.ofNullable(arquivos.get(invocation.getArgument(0))));
        when(arquivoRepository.findByIdInForUpdate(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = (List<UUID>) invocation.getArgument(0);
            return ids.stream().map(arquivos::get).filter(java.util.Objects::nonNull).toList();
        });
        when(arquivoRepository.save(any())).thenAnswer(invocation -> {
            ArquivoMidiaEntity value = invocation.getArgument(0);
            arquivos.put(value.getId(), value);
            return value;
        });
    }

    @Test
    void enviaFotoSomenteParaR2PrivadoComoPendenteSemClassificacao() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        var response = service.enviar(SLUG, multipart, "foto-1", authentication);

        assertThat(vinculos).singleElement().satisfies(vinculo -> {
            assertThat(vinculo.getTipo()).isEqualTo(TipoAnuncioMidia.FOTO);
            assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PENDENTE);
            assertThat(vinculo.getVisibilidadeMidia()).isNull();
        });
        ArquivoMidiaEntity persistido = arquivos.values().iterator().next();
        assertThat(persistido.getBucket()).isEqualTo("privadas");
        assertThat(persistido.getChaveObjeto())
                .startsWith("hml/midias-pendentes/anuncios/" + ANUNCIO_ID + "/")
                .endsWith("/foto-v1.jpg");
        assertThat(persistido.getPipelineVersao()).isEqualTo(1);
        assertThat(persistido.getSha256Origem()).isEqualTo("a".repeat(64));
        assertThat(objetos).hasSize(1);
        assertThat(objetos.get(persistido.getChaveObjeto()).content()).containsExactly(9, 8, 7);
        verify(storage).putIfAbsent(
                eq(StorageArea.PRIVATE_MEDIA), eq(persistido.getChaveObjeto()), any(), eq("image/jpeg"));
        assertThat(response.toString()).doesNotContain("privadas").doesNotContain("hml/midias-pendentes");
        assertThat(response.midias()).singleElement().satisfies(midia -> {
            assertThat(midia.previewUrl()).isEqualTo("https://privado.invalid/temporaria");
            assertThat(midia.previewExpiraEm()).isEqualTo(AGORA_PREVIEW.plusMinutes(5));
        });
    }

    @Test
    void bloqueiaLinhaDoAnuncioAntesDeCalcularOrdemEEscreverNoStorage() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        service.enviar(SLUG, multipart, "foto-lock", authentication);

        var ordem = inOrder(consultaService, midiaRepository, storage);
        ordem.verify(consultaService).anuncioDoUsuarioParaAtualizacao(SLUG, authentication);
        ordem.verify(midiaRepository, org.mockito.Mockito.times(2)).findByAnuncioIdForUpdate(ANUNCIO_ID);
        ordem.verify(storage).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any());
    }

    @Test
    void videoNasceRestritoESegundoVideoEhBloqueado() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(true));
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(4, 1, false, true));

        service.enviar(SLUG, multipart, "video-1", authentication);

        assertThat(vinculos).singleElement().satisfies(vinculo -> {
            assertThat(vinculo.getTipo()).isEqualTo(TipoAnuncioMidia.VIDEO);
            assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PENDENTE);
            assertThat(vinculo.getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
        });
        assertThat(arquivos.values()).singleElement().satisfies(arquivo -> {
            assertThat(arquivo.getPipelineVersao()).isNull();
            assertThat(arquivo.getMarcaDaguaVersao()).isNull();
            assertThat(arquivo.getSha256Origem()).isNull();
        });
        assertThatThrownBy(() -> service.enviar(SLUG, multipart, "video-2", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(fotoProcessor, never()).processar(any());
    }

    @Test
    void uploadExigeChaveIdempotente() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        assertThatThrownBy(() -> service.enviar(SLUG, multipart, null, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(fotoProcessor, never()).processar(any());
    }

    @Test
    void planoBaseBloqueiaQuintaFoto() {
        adicionarFotos(4);
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        assertThatThrownBy(() -> service.enviar(SLUG, multipart, "foto-5", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    }

    @Test
    void loteBaseAceitaQuatroFotosERecusaCincoAntesDeEscreverNoR2() {
        List<MultipartFile> quatro = multiparts(4);
        quatro.forEach(file -> when(validator.validar(file)).thenReturn(validada(false)));

        var response = service.enviarLote(
                SLUG, quatro, "lote-base-quatro", authentication);

        assertThat(response.limites().fotosAtivas()).isEqualTo(4);
        assertThat(vinculos).hasSize(4);
        assertThat(objetos).hasSize(4);

        List<MultipartFile> quintaSelecao = multiparts(5);
        quintaSelecao.forEach(file -> when(validator.validar(file)).thenReturn(validada(false)));
        org.mockito.Mockito.clearInvocations(storage);

        assertThatThrownBy(() -> service.enviarLote(
                SLUG, quintaSelecao, "lote-base-cinco", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));
        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
        assertThat(vinculos).hasSize(4);
    }

    @Test
    void loteValidaTodosOsArquivosAntesDeEscrever() {
        MultipartFile primeira = mock(MultipartFile.class);
        MultipartFile segunda = mock(MultipartFile.class);
        when(validator.validar(primeira)).thenReturn(validada(false));
        when(validator.validar(segunda)).thenThrow(
                new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "arquivo invalido"));

        assertThatThrownBy(() -> service.enviarLote(
                SLUG,
                List.of(primeira, segunda),
                "lote-invalido",
                authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
        verify(arquivoRepository, never()).save(any());
        verify(midiaRepository, never()).save(any());
    }

    @Test
    void videoSemBeneficioEhRecusadoAntesDoStorage() {
        MultipartFile video = mock(MultipartFile.class);
        when(validator.validar(video)).thenReturn(validada(true));

        assertThatThrownBy(() -> service.enviarLote(
                SLUG, List.of(video), "video-bloqueado", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("beneficio Video");
                });
        verify(storage, never()).putIfAbsent(any(), any(), any(), any());
    }

    @Test
    void fotosExtraPermiteDezEBloqueiaDecimaPrimeira() {
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(10, 0, true, false));
        adicionarFotos(9);
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        var response = service.enviar(SLUG, multipart, "foto-10", authentication);

        assertThat(response.limites().fotosAtivas()).isEqualTo(10);
        assertThat(response.limites().maxFotos()).isEqualTo(10);
        assertThatThrownBy(() -> service.enviar(SLUG, multipart, "foto-11", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loteComFotosExtraAceitaDezERetryNaoDuplicaNemReprocessa() {
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(10, 0, true, false));
        List<MultipartFile> dez = multiparts(10);
        dez.forEach(file -> when(validator.validar(file)).thenReturn(validada(false)));

        service.enviarLote(SLUG, dez, "lote-extra-dez", authentication);
        service.enviarLote(SLUG, dez, "lote-extra-dez", authentication);

        assertThat(vinculos).hasSize(10);
        assertThat(arquivos).hasSize(10);
        assertThat(objetos).hasSize(10);
        verify(fotoProcessor, org.mockito.Mockito.times(10)).processar(any());
        verify(storage, org.mockito.Mockito.times(10))
                .putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), eq("image/jpeg"));
    }

    @Test
    void expiracaoRetornaLimiteAQuatroSemExcluirArquivos() {
        adicionarFotos(6);
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(10, 0, true, false));
        assertThat(service.listar(SLUG, authentication).midias())
                .noneMatch(item -> item.ocultaPorLimite());

        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(4, 0, false, false));
        var aposExpiracao = service.listar(SLUG, authentication);

        assertThat(aposExpiracao.limites().maxFotos()).isEqualTo(4);
        assertThat(aposExpiracao.midias()).filteredOn(item -> item.ocultaPorLimite()).hasSize(2);
        assertThat(vinculos).hasSize(6);
        verify(storage, never()).delete(any(), any());
    }

    @Test
    void reordenaPorIdRealSemDuplicidade() {
        AnuncioMidiaEntity primeira = vinculo(TipoAnuncioMidia.FOTO, 0);
        AnuncioMidiaEntity segunda = vinculo(TipoAnuncioMidia.FOTO, 1);
        vinculos.addAll(List.of(primeira, segunda));

        service.reordenar(SLUG,
                new ReordenarMinhasMidiasRequestDto(List.of(segunda.getId(), primeira.getId())), authentication);

        assertThat(segunda.getOrdem()).isZero();
        assertThat(primeira.getOrdem()).isEqualTo(1);
        verify(midiaRepository, org.mockito.Mockito.times(2)).flush();
        verify(arquivoPublicidade).registrarEstado(eq(ANUNCIO_ID),
                eq("MIDIAS_REORDENADAS_PELO_PROPRIETARIO"), eq(null), any(OffsetDateTime.class));
    }

    @Test
    void removeSomenteOVinculoSemExcluirObjetoFisico() {
        AnuncioMidiaEntity vinculo = vinculo(TipoAnuncioMidia.FOTO, 0);
        vinculos.add(vinculo);

        var response = service.remover(SLUG, vinculo.getId(), authentication, "request-remocao");

        assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.REMOVIDA);
        assertThat(response.midias()).isEmpty();
        verify(cicloVidaService).encerrarPorUltimaFoto(any(AnuncioEntity.class), eq(vinculo.getId()), eq("request-remocao"), any());
        verify(fotoElegivelPolicy, never()).validarRemocaoIndividual(any(), any());
        verify(storage, never()).delete(any(), any());
    }

    @Test
    void remocaoDeFotoNaoFinalAtualizaArquivoDaVeiculacao() {
        AnuncioMidiaEntity primeira = vinculo(TipoAnuncioMidia.FOTO, 0);
        AnuncioMidiaEntity segunda = vinculo(TipoAnuncioMidia.FOTO, 1);
        vinculos.addAll(List.of(primeira, segunda));
        when(arquivoPublicidade.possuiFotoPublicaSelecionada(ANUNCIO_ID)).thenReturn(true);

        service.remover(SLUG, primeira.getId(), authentication, "request-remocao-nao-final");

        assertThat(primeira.getStatus()).isEqualTo(StatusAnuncioMidia.REMOVIDA);
        assertThat(segunda.getStatus()).isNotEqualTo(StatusAnuncioMidia.REMOVIDA);
        verify(cicloVidaService, never()).encerrarPorUltimaFoto(any(), any(), any(), any());
        verify(arquivoPublicidade).registrarEstado(eq(ANUNCIO_ID),
                eq("MIDIA_REMOVIDA_PELO_PROPRIETARIO"), eq("request-remocao-nao-final"),
                any(OffsetDateTime.class));
    }

    @Test
    void remocaoEncerraQuandoPromovidaSemCopiaDeixaGaleriaSemFoto() {
        AnuncioMidiaEntity primeira = vinculo(TipoAnuncioMidia.FOTO, 0);
        AnuncioMidiaEntity promovida = vinculo(TipoAnuncioMidia.FOTO, 1);
        vinculos.addAll(List.of(primeira, promovida));
        when(arquivoPublicidade.prepararRetiradaSemNovaCopia(eq(ANUNCIO_ID), any(),
                eq("request-sem-copia"), any(), any())).thenReturn(List.of(promovida.getId()));
        when(arquivoPublicidade.possuiFotoPublicaSelecionada(ANUNCIO_ID)).thenReturn(false);

        service.remover(SLUG, primeira.getId(), authentication, "request-sem-copia");

        verify(cicloVidaService).encerrarPorFaltaDeMidiaArquivavel(any(AnuncioEntity.class),
                any(), eq(primeira.getId()), eq(List.of(promovida.getId())),
                eq("request-sem-copia"), any());
        verify(arquivoPublicidade, never()).registrarEstado(eq(ANUNCIO_ID),
                eq("MIDIA_REMOVIDA_PELO_PROPRIETARIO"), any(), any());
    }

    @Test
    void remocaoConsultaGuardDaUltimaAprovadaQuandoRestaOutraFotoPendente() {
        AnuncioMidiaEntity vinculo = vinculo(TipoAnuncioMidia.FOTO, 0);
        vinculos.add(vinculo);
        vinculos.add(vinculo(TipoAnuncioMidia.FOTO, 1));
        org.mockito.Mockito.doThrow(new FotoElegivelAnuncioPolicy.UltimaFotoAprovadaException())
                .when(fotoElegivelPolicy)
                .validarRemocaoIndividual(any(AnuncioEntity.class), eq(vinculo.getId()));

        assertThatThrownBy(() -> service.remover(SLUG, vinculo.getId(), authentication, "request-remocao"))
                .isInstanceOfSatisfying(
                        FotoElegivelAnuncioPolicy.UltimaFotoAprovadaException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(FotoElegivelAnuncioPolicy.MENSAGEM_ULTIMA_FOTO_APROVADA));

        assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PENDENTE);
        verify(midiaRepository).findByAnuncioIdForUpdate(ANUNCIO_ID);
        verify(midiaRepository, never()).flush();
        verify(arquivoPublicidade, never()).registrarEstado(any(), any(), any(), any());
    }

    @Test
    void recusaMidiaDeOutroAnuncioEEstadoEmAnalise() {
        AnuncioMidiaEntity alheia = AnuncioMidiaEntity.criarUploadPendente(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TipoAnuncioMidia.FOTO, 0,
                OffsetDateTime.now(ZoneOffset.UTC));
        vinculos.add(alheia);

        assertThatThrownBy(() -> service.remover(SLUG, alheia.getId(), authentication, "request-remocao"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        when(revisaoRepository.existsByAnuncioIdAndStatusIn(eq(ANUNCIO_ID), anyList())).thenReturn(true);
        assertThatThrownBy(() -> service.enviar(SLUG, mock(MultipartFile.class), "foto-analise", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void retryTecnicoReutilizaRegistroEObjetoSemReaplicarMarca() {
        MultipartFile multipart = mock(MultipartFile.class);
        MidiaValidada upload = validada(false);
        when(validator.validar(multipart)).thenReturn(upload);

        service.enviar(SLUG, multipart, "retry-1", authentication);
        service.enviar(SLUG, multipart, "retry-1", authentication);

        assertThat(vinculos).hasSize(1);
        assertThat(arquivos).hasSize(1);
        assertThat(objetos).hasSize(1);
        verify(fotoProcessor, org.mockito.Mockito.times(1)).processar(upload);
        verify(storage, org.mockito.Mockito.times(1))
                .putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), eq("image/jpeg"));
    }

    @Test
    void retryTecnicoFalhaFechadoQuandoObjetoPersistidoDesaparece() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        service.enviar(SLUG, multipart, "retry-sem-objeto", authentication);
        objetos.clear();

        assertThatThrownBy(() -> service.enviar(SLUG, multipart, "retry-sem-objeto", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(fotoProcessor, org.mockito.Mockito.times(1)).processar(any());
    }

    @Test
    void novoUploadIntencionalDoMesmoArquivoCriaNovaMidia() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        service.enviar(SLUG, multipart, "intencional-1", authentication);
        service.enviar(SLUG, multipart, "intencional-2", authentication);

        assertThat(vinculos).hasSize(2);
        assertThat(arquivos).hasSize(2);
        assertThat(objetos).hasSize(2);
    }

    @Test
    void falhaR2NaoCriaVinculoNemFingeSucesso() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));
        when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), any(), any(), any()))
                .thenThrow(new IllegalStateException("falha sintetica R2"));

        assertThatThrownBy(() -> service.enviar(SLUG, multipart, "falha-r2", authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha sintetica");

        assertThat(vinculos).isEmpty();
        verify(midiaRepository, never()).save(any());
    }

    @Test
    void rollbackDoBancoRemoveSomenteObjetoCriadoPelaTentativa() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));
        org.mockito.Mockito.doThrow(new IllegalStateException("falha sintetica banco"))
                .when(midiaRepository).save(any());
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.enviar(SLUG, multipart, "falha-banco", authentication))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("falha sintetica banco");
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storage).delete(eq(StorageArea.PRIVATE_MEDIA), any());
    }

    private AnuncioMidiaEntity vinculo(TipoAnuncioMidia tipo, int ordem) {
        return AnuncioMidiaEntity.criarUploadPendente(
                UUID.randomUUID(), ANUNCIO_ID, UUID.randomUUID(), tipo, ordem, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void adicionarFotos(int quantidade) {
        for (int ordem = 0; ordem < quantidade; ordem++) {
            vinculos.add(vinculo(TipoAnuncioMidia.FOTO, ordem));
        }
    }

    private List<MultipartFile> multiparts(int quantidade) {
        List<MultipartFile> resultado = new ArrayList<>();
        for (int index = 0; index < quantidade; index++) {
            resultado.add(mock(MultipartFile.class));
        }
        return resultado;
    }

    private MidiaValidada validada(boolean video) {
        return new MidiaValidada(
                new byte[] {1, 2, 3}, video, video ? "video/mp4" : "image/png",
                video ? "mp4" : "png", video ? "video.mp4" : "foto.png",
                video ? 720 : 2, video ? 1280 : 3, video ? 15_000L : null,
                "a".repeat(64));
    }

    private FotoProcessada processada() {
        return new FotoProcessada(
                new byte[] {9, 8, 7}, "image/jpeg", "jpg", 2, 3,
                "06df4f7e1394f1c57cc6583fba4d8060a5a66f4f4771c14aeff6b9af8a28c9b3", "a".repeat(64),
                1, FotoUploadProcessor.WATERMARK_VERSION, OffsetDateTime.now(ZoneOffset.UTC));
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
