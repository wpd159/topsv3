package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.dto.ReordenarMinhasMidiasRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
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

class MinhasMidiasServiceTest {

    private static final UUID ANUNCIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SLUG = "anuncio-proprio";

    private final MeusAnunciosConsultaService consultaService = mock(MeusAnunciosConsultaService.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final LimiteMidiasAnuncioService limiteService = mock(LimiteMidiasAnuncioService.class);
    private final MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    private final List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
    private final Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
    private final Authentication authentication = mock(Authentication.class);
    private final R2StorageProperties storageProperties = storageProperties();
    private final MinhasMidiasService service = new MinhasMidiasService(
            consultaService, midiaRepository, arquivoRepository, revisaoRepository, limiteService,
            validator, new MidiaUploadProperties(), storageProperties, storageProvider);

    @BeforeEach
    void setUp() {
        AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID, UUID.randomUUID(), SLUG, "Perfil de teste", "Descricao publica de teste",
                StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, OffsetDateTime.now(ZoneOffset.UTC));
        when(consultaService.anuncioDoUsuario(SLUG, authentication)).thenReturn(anuncio);
        when(revisaoRepository.existsByAnuncioIdAndStatusIn(eq(ANUNCIO_ID), anyList())).thenReturn(false);
        when(limiteService.resolver(ANUNCIO_ID)).thenReturn(new LimiteMidiasAnuncioService.Resultado(4, 1, false));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storage.temporaryGetUrl(eq(StorageArea.PRIVATE_MEDIA), any(), any()))
                .thenReturn(URI.create("https://privado.invalid/temporaria"));
        when(midiaRepository.findByAnuncioId(ANUNCIO_ID)).thenAnswer(ignored -> List.copyOf(vinculos));
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

        var response = service.enviar(SLUG, multipart, authentication);

        assertThat(vinculos).singleElement().satisfies(vinculo -> {
            assertThat(vinculo.getTipo()).isEqualTo(TipoAnuncioMidia.FOTO);
            assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PENDENTE);
            assertThat(vinculo.getVisibilidadeMidia()).isNull();
        });
        ArquivoMidiaEntity persistido = arquivos.values().iterator().next();
        assertThat(persistido.getBucket()).isEqualTo("privadas");
        assertThat(persistido.getChaveObjeto()).startsWith("hml/midias-pendentes/anuncios/" + ANUNCIO_ID + "/");
        verify(storage).put(eq(StorageArea.PRIVATE_MEDIA), eq(persistido.getChaveObjeto()), any(), eq("image/png"));
        assertThat(response.toString()).doesNotContain("privadas").doesNotContain("hml/midias-pendentes");
    }

    @Test
    void videoNasceRestritoESegundoVideoEhBloqueado() {
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(true));

        service.enviar(SLUG, multipart, authentication);

        assertThat(vinculos).singleElement().satisfies(vinculo -> {
            assertThat(vinculo.getTipo()).isEqualTo(TipoAnuncioMidia.VIDEO);
            assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PENDENTE);
            assertThat(vinculo.getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
        });
        assertThatThrownBy(() -> service.enviar(SLUG, multipart, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void planoBaseBloqueiaQuintaFoto() {
        adicionarFotos(4);
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        assertThatThrownBy(() -> service.enviar(SLUG, multipart, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(storage, never()).put(any(), any(), any(), any());
    }

    @Test
    void fotosExtraPermiteDezEBloqueiaDecimaPrimeira() {
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(10, 1, true));
        adicionarFotos(9);
        MultipartFile multipart = mock(MultipartFile.class);
        when(validator.validar(multipart)).thenReturn(validada(false));

        var response = service.enviar(SLUG, multipart, authentication);

        assertThat(response.limites().fotosAtivas()).isEqualTo(10);
        assertThat(response.limites().maxFotos()).isEqualTo(10);
        assertThatThrownBy(() -> service.enviar(SLUG, multipart, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void expiracaoRetornaLimiteAQuatroSemExcluirArquivos() {
        adicionarFotos(6);
        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(10, 1, true));
        assertThat(service.listar(SLUG, authentication).midias())
                .noneMatch(item -> item.ocultaPorLimite());

        when(limiteService.resolver(ANUNCIO_ID))
                .thenReturn(new LimiteMidiasAnuncioService.Resultado(4, 1, false));
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
    }

    @Test
    void removeSomenteOVinculoSemExcluirObjetoFisico() {
        AnuncioMidiaEntity vinculo = vinculo(TipoAnuncioMidia.FOTO, 0);
        vinculos.add(vinculo);

        var response = service.remover(SLUG, vinculo.getId(), authentication);

        assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.REMOVIDA);
        assertThat(response.midias()).isEmpty();
        verify(storage, never()).delete(any(), any());
    }

    @Test
    void recusaMidiaDeOutroAnuncioEEstadoEmAnalise() {
        AnuncioMidiaEntity alheia = AnuncioMidiaEntity.criarUploadPendente(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TipoAnuncioMidia.FOTO, 0,
                OffsetDateTime.now(ZoneOffset.UTC));
        vinculos.add(alheia);

        assertThatThrownBy(() -> service.remover(SLUG, alheia.getId(), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        when(revisaoRepository.existsByAnuncioIdAndStatusIn(eq(ANUNCIO_ID), anyList())).thenReturn(true);
        assertThatThrownBy(() -> service.enviar(SLUG, mock(MultipartFile.class), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
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

    private MidiaValidada validada(boolean video) {
        return new MidiaValidada(
                new byte[] {1, 2, 3}, video, video ? "video/mp4" : "image/png",
                video ? "mp4" : "png", video ? "video.mp4" : "foto.png",
                video ? null : 2, video ? null : 3, "a".repeat(64));
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
