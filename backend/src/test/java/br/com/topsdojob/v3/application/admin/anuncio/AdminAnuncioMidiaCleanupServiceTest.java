package br.com.topsdojob.v3.application.admin.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

class AdminAnuncioMidiaCleanupServiceTest {

  private static final String PUBLIC_PREFIX = "hml/preprod/midias-aprovadas/";
  private static final String PRIVATE_PREFIX = "hml/preprod/midias-pendentes/";
  private static final String DOCUMENT_PREFIX = "hml/preprod/documentos/";

  private final AnuncioMidiaRepository anuncioMidiaRepository =
      mock(AnuncioMidiaRepository.class);
  private final ArquivoMidiaRepository arquivoMidiaRepository =
      mock(ArquivoMidiaRepository.class);
  private final DocumentoUsuarioRepository documentoUsuarioRepository =
      mock(DocumentoUsuarioRepository.class);
  private final StoryAnuncioRepository storyRepository = mock(StoryAnuncioRepository.class);
  private final StorySelecaoAdministrativaRepository storyAdminRepository =
      mock(StorySelecaoAdministrativaRepository.class);
  private final MemoriaObjectStorage storage = new MemoriaObjectStorage();
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
  private final R2StorageProperties properties = properties();
  private final AdminAnuncioMidiaCleanupService service = new AdminAnuncioMidiaCleanupService(
      anuncioMidiaRepository,
      arquivoMidiaRepository,
      documentoUsuarioRepository,
      storyRepository,
      storyAdminRepository,
      storageProvider,
      properties);

  @BeforeEach
  void setUp() {
    when(storageProvider.getIfAvailable()).thenReturn(storage);
    when(storyAdminRepository.bloquearSingleton()).thenReturn(Optional.empty());
  }

  @Test
  void excluiFotosVideosVariantesEStoryPreservandoKycEObjetoCompartilhado() {
    UUID anuncioId = uuid(1);
    UUID outroAnuncioId = uuid(2);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);

    List<ArquivoMidiaEntity> arquivos = List.of(
        arquivo(uuid(101), "publicas", PUBLIC_PREFIX + "anuncios/a/foto-v1.jpg", "image/jpeg"),
        arquivo(uuid(102), "privadas", PRIVATE_PREFIX + "anuncios/a/video.mp4", "video/mp4"),
        arquivo(uuid(103), "publicas", PUBLIC_PREFIX + "anuncios/a/thumbnail.webp", "image/webp"),
        arquivo(uuid(104), "privadas", PRIVATE_PREFIX + "anuncios/a/preview.jpg", "image/jpeg"),
        arquivo(uuid(105), "publicas", PUBLIC_PREFIX + "legado/a-versao-processada.jpg", "image/jpeg"),
        arquivo(uuid(106), "privadas", PRIVATE_PREFIX + "anuncios/a/story-exclusivo.webp", "image/webp"),
        arquivo(uuid(107), "publicas", PUBLIC_PREFIX + "importacao/sha256/compartilhada.jpg", "image/jpeg"));
    List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
    for (int index = 0; index < arquivos.size(); index++) {
      TipoAnuncioMidia tipo = index == 1
          ? TipoAnuncioMidia.VIDEO
          : index == 5 ? TipoAnuncioMidia.STORY : TipoAnuncioMidia.FOTO;
      vinculos.add(vinculo(uuid(201 + index), anuncioId, arquivos.get(index).getId(), tipo, index));
    }
    AnuncioMidiaEntity compartilhadaOutro = vinculo(
        uuid(299),
        outroAnuncioId,
        arquivos.get(6).getId(),
        TipoAnuncioMidia.FOTO,
        0);
    prepararRepositorios(anuncioId, arquivos, vinculos, compartilhadaOutro);

    StoryAnuncioEntity story = StoryAnuncioEntity.criarFixtureHomologacao(
        uuid(301),
        vinculos.get(5).getId(),
        agora.minusHours(1),
        agora.plusHours(23),
        0,
        uuid(3),
        agora.minusHours(1));
    when(storyRepository.findByAnuncioMidiaIdInForUpdate(any())).thenReturn(List.of(story));
    StorySelecaoAdministrativaEntity selecao = StorySelecaoAdministrativaEntity.nova(agora.minusDays(1));
    selecao.ativar(anuncioId, uuid(3), agora.minusHours(1));
    when(storyAdminRepository.bloquearSingleton()).thenReturn(Optional.of(selecao));

    colocar(StorageArea.PUBLIC_MEDIA, PUBLIC_PREFIX + "anuncios/a/foto-v1.jpg");
    colocar(StorageArea.PRIVATE_MEDIA, PRIVATE_PREFIX + "anuncios/a/foto-v1.jpg");
    colocar(StorageArea.PRIVATE_MEDIA, PRIVATE_PREFIX + "anuncios/a/video.mp4");
    colocar(StorageArea.PUBLIC_MEDIA, PUBLIC_PREFIX + "anuncios/a/thumbnail.webp");
    colocar(StorageArea.PRIVATE_MEDIA, PRIVATE_PREFIX + "anuncios/a/preview.jpg");
    colocar(StorageArea.PUBLIC_MEDIA, PUBLIC_PREFIX + "legado/a-versao-processada.jpg");
    colocar(StorageArea.PRIVATE_MEDIA, PRIVATE_PREFIX + "anuncios/a/story-exclusivo.webp");
    colocar(StorageArea.PUBLIC_MEDIA, PUBLIC_PREFIX + "importacao/sha256/compartilhada.jpg");
    String kycKey = DOCUMENT_PREFIX + "usuario/kyc-frente.jpg";
    colocar(StorageArea.PRIVATE_DOCUMENT, kycKey);

    var resultado = service.limpar(anuncioId, agora);

    assertThat(resultado.midiasRemovidas()).isEqualTo(7);
    assertThat(resultado.objetosExcluidos()).isEqualTo(7);
    assertThat(resultado.objetosJaAusentes()).isEqualTo(5);
    assertThat(resultado.objetosCompartilhadosPreservados()).isEqualTo(1);
    assertThat(resultado.storiesEncerrados()).isEqualTo(1);
    assertThat(resultado.storyAdministrativoEncerrado()).isTrue();
    assertThat(vinculos).allMatch(item -> item.getStatus() == StatusAnuncioMidia.REMOVIDA);
    assertThat(arquivos.subList(0, 6))
        .allMatch(item -> item.getStatusArquivo() == StatusArquivoMidia.REMOVIDO);
    assertThat(arquivos.get(6).getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
    assertThat(compartilhadaOutro.getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
    assertThat(story.getStatus()).isEqualTo(StatusStoryAnuncio.EXPIRADO);
    assertThat(selecao.isAtiva()).isFalse();
    assertThat(storage.exists(
        StorageArea.PUBLIC_MEDIA,
        PUBLIC_PREFIX + "anuncios/a/foto-v1.jpg")).isFalse();
    assertThat(storage.exists(
        StorageArea.PRIVATE_MEDIA,
        PRIVATE_PREFIX + "anuncios/a/story-exclusivo.webp")).isFalse();
    assertThat(storage.exists(StorageArea.PRIVATE_DOCUMENT, kycKey)).isTrue();
    assertThat(storage.exists(
        StorageArea.PUBLIC_MEDIA,
        PUBLIC_PREFIX + "importacao/sha256/compartilhada.jpg")).isTrue();

    var retry = service.limpar(anuncioId, agora.plusSeconds(1));
    assertThat(retry.midiasRemovidas()).isZero();
    assertThat(retry.objetosExcluidos()).isZero();
    assertThat(retry.objetosJaAusentes()).isEqualTo(12);
    assertThat(retry.storiesEncerrados()).isZero();
  }

  @Test
  void excluiFotoAprovadaSelecionadaEPreservaOutraMidiaKycEStoryAdministrativo() {
    UUID anuncioId = uuid(30);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    ArquivoMidiaEntity alvoArquivo = arquivo(
        uuid(130),
        "privadas",
        PRIVATE_PREFIX + "anuncios/lote/foto-alvo.jpg",
        "image/jpeg");
    ArquivoMidiaEntity outraArquivo = arquivo(
        uuid(131),
        "publicas",
        PUBLIC_PREFIX + "anuncios/lote/foto-preservada.jpg",
        "image/jpeg");
    AnuncioMidiaEntity alvo = AnuncioMidiaEntity.criarFixtureHomologacao(
        uuid(230),
        anuncioId,
        alvoArquivo.getId(),
        TipoAnuncioMidia.FOTO,
        FinalidadeAnuncioMidia.GALERIA,
        0,
        StatusAnuncioMidia.PUBLICAVEL,
        VisibilidadeMidia.LIVRE,
        agora.minusHours(1));
    AnuncioMidiaEntity outra = vinculo(
        uuid(231),
        anuncioId,
        outraArquivo.getId(),
        TipoAnuncioMidia.FOTO,
        1);
    when(anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId))
        .thenReturn(List.of(alvo, outra));
    when(arquivoMidiaRepository.findByIdInForUpdate(List.of(alvoArquivo.getId())))
        .thenReturn(List.of(alvoArquivo));
    when(anuncioMidiaRepository.findByArquivoMidiaId(alvoArquivo.getId()))
        .thenReturn(List.of(alvo));
    when(documentoUsuarioRepository
        .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(alvoArquivo.getId()))
        .thenReturn(false);

    StoryAnuncioEntity story = StoryAnuncioEntity.criarFixtureHomologacao(
        uuid(330),
        alvo.getId(),
        agora.minusHours(1),
        agora.plusHours(23),
        0,
        uuid(3),
        agora.minusHours(1));
    when(storyRepository.findByAnuncioMidiaIdInForUpdate(List.of(alvo.getId())))
        .thenReturn(List.of(story));
    StorySelecaoAdministrativaEntity selecao = StorySelecaoAdministrativaEntity.nova(
        agora.minusDays(1));
    selecao.ativar(anuncioId, uuid(3), agora.minusHours(1));
    when(storyAdminRepository.bloquearSingleton()).thenReturn(Optional.of(selecao));

    colocar(StorageArea.PRIVATE_MEDIA, alvoArquivo.getChaveObjeto());
    colocar(StorageArea.PUBLIC_MEDIA, outraArquivo.getChaveObjeto());
    String kycKey = DOCUMENT_PREFIX + "usuario/controle-kyc.jpg";
    colocar(StorageArea.PRIVATE_DOCUMENT, kycKey);

    var resultado = service.limparMidia(anuncioId, alvo.getId(), agora);

    assertThat(resultado.midiasRemovidas()).isEqualTo(1);
    assertThat(resultado.objetosExcluidos()).isEqualTo(1);
    assertThat(resultado.objetosJaAusentes()).isEqualTo(1);
    assertThat(resultado.storiesEncerrados()).isEqualTo(1);
    assertThat(resultado.storyAdministrativoEncerrado()).isFalse();
    assertThat(alvo.getStatus()).isEqualTo(StatusAnuncioMidia.REMOVIDA);
    assertThat(alvoArquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.REMOVIDO);
    assertThat(outra.getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
    assertThat(outraArquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
    assertThat(story.getStatus()).isEqualTo(StatusStoryAnuncio.EXPIRADO);
    assertThat(selecao.isAtiva()).isTrue();
    assertThat(storage.exists(
        StorageArea.PRIVATE_MEDIA,
        alvoArquivo.getChaveObjeto())).isFalse();
    assertThat(storage.exists(
        StorageArea.PUBLIC_MEDIA,
        outraArquivo.getChaveObjeto())).isTrue();
    assertThat(storage.exists(StorageArea.PRIVATE_DOCUMENT, kycKey)).isTrue();
  }

  @Test
  void falhaR2NaoMudaMetadadosENovaTentativaConcluiSemDuplicar() {
    UUID anuncioId = uuid(10);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    ArquivoMidiaEntity foto = arquivo(
        uuid(110),
        "publicas",
        PUBLIC_PREFIX + "anuncios/retry/foto.jpg",
        "image/jpeg");
    AnuncioMidiaEntity vinculo = vinculo(
        uuid(210),
        anuncioId,
        foto.getId(),
        TipoAnuncioMidia.FOTO,
        0);
    prepararRepositorios(anuncioId, List.of(foto), List.of(vinculo), null);
    when(storyRepository.findByAnuncioMidiaIdInForUpdate(any())).thenReturn(List.of());
    colocar(StorageArea.PUBLIC_MEDIA, foto.getChaveObjeto());
    storage.falharUmaVez(StorageArea.PUBLIC_MEDIA, foto.getChaveObjeto());

    assertThatThrownBy(() -> service.limpar(anuncioId, agora))
        .isInstanceOfSatisfying(
            AdminAnuncioMidiaCleanupService.CleanupException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(error.codigo()).isEqualTo("FALHA_OPERACIONAL_R2");
            });
    assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
    assertThat(foto.getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
    verify(anuncioMidiaRepository, never()).saveAll(any());
    verify(arquivoMidiaRepository, never()).saveAll(any());

    var retry = service.limpar(anuncioId, agora.plusSeconds(1));
    assertThat(retry.objetosExcluidos()).isEqualTo(1);
    assertThat(retry.objetosJaAusentes()).isEqualTo(1);
    assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.REMOVIDA);
    assertThat(foto.getStatusArquivo()).isEqualTo(StatusArquivoMidia.REMOVIDO);
    assertThat(storage.exists(StorageArea.PUBLIC_MEDIA, foto.getChaveObjeto())).isFalse();
  }

  @Test
  void arquivoDocumentalVinculadoBloqueiaSemTocarNoR2() {
    UUID anuncioId = uuid(20);
    ArquivoMidiaEntity documento = arquivo(
        uuid(120),
        "documentos",
        DOCUMENT_PREFIX + "usuario/documento.jpg",
        "image/jpeg");
    AnuncioMidiaEntity vinculo = vinculo(
        uuid(220),
        anuncioId,
        documento.getId(),
        TipoAnuncioMidia.FOTO,
        0);
    when(anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId)).thenReturn(List.of(vinculo));
    when(arquivoMidiaRepository.findByIdInForUpdate(List.of(documento.getId())))
        .thenReturn(List.of(documento));
    when(documentoUsuarioRepository
        .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(documento.getId()))
        .thenReturn(true);
    colocar(StorageArea.PRIVATE_DOCUMENT, documento.getChaveObjeto());

    assertThatThrownBy(() -> service.limpar(anuncioId, OffsetDateTime.now(ZoneOffset.UTC)))
        .isInstanceOfSatisfying(
            AdminAnuncioMidiaCleanupService.CleanupException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.codigo()).isEqualTo("ARQUIVO_DOCUMENTAL_VINCULADO");
            });
    assertThat(storage.exists(StorageArea.PRIVATE_DOCUMENT, documento.getChaveObjeto())).isTrue();
    assertThat(vinculo.getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
  }

  private void prepararRepositorios(
      UUID anuncioId,
      List<ArquivoMidiaEntity> arquivos,
      List<AnuncioMidiaEntity> vinculos,
      AnuncioMidiaEntity compartilhadaOutro) {
    when(anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId)).thenReturn(vinculos);
    List<UUID> ids = arquivos.stream().map(ArquivoMidiaEntity::getId).sorted().toList();
    when(arquivoMidiaRepository.findByIdInForUpdate(ids)).thenReturn(arquivos);
    for (int index = 0; index < arquivos.size(); index++) {
      ArquivoMidiaEntity arquivo = arquivos.get(index);
      AnuncioMidiaEntity vinculo = vinculos.get(index);
      List<AnuncioMidiaEntity> referencias = compartilhadaOutro != null
          && compartilhadaOutro.getArquivoMidiaId().equals(arquivo.getId())
          ? List.of(vinculo, compartilhadaOutro)
          : List.of(vinculo);
      when(anuncioMidiaRepository.findByArquivoMidiaId(arquivo.getId())).thenReturn(referencias);
      when(documentoUsuarioRepository
          .existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivo.getId()))
          .thenReturn(false);
    }
  }

  private ArquivoMidiaEntity arquivo(UUID id, String bucket, String key, String mimeType) {
    ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
        id,
        "R2",
        bucket,
        key,
        "arquivo-sintetico",
        mimeType,
        128,
        mimeType.startsWith("image/") ? 100 : null,
        mimeType.startsWith("image/") ? 100 : null,
        mimeType.startsWith("video/") ? 1000 : null,
        "0".repeat(64),
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    arquivo.aplicarDecisao(StatusArquivoMidia.VALIDADO);
    return arquivo;
  }

  private AnuncioMidiaEntity vinculo(
      UUID id,
      UUID anuncioId,
      UUID arquivoId,
      TipoAnuncioMidia tipo,
      int ordem) {
    return AnuncioMidiaEntity.criarFixtureHomologacao(
        id,
        anuncioId,
        arquivoId,
        tipo,
        tipo == TipoAnuncioMidia.STORY
            ? FinalidadeAnuncioMidia.STORY
            : FinalidadeAnuncioMidia.GALERIA,
        ordem,
        StatusAnuncioMidia.PUBLICAVEL,
        tipo == TipoAnuncioMidia.VIDEO ? VisibilidadeMidia.RESTRITA_18 : VisibilidadeMidia.LIVRE,
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
  }

  private void colocar(StorageArea area, String key) {
    storage.put(area, key, new byte[] {1, 2, 3}, "application/octet-stream");
  }

  private static UUID uuid(int suffix) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
  }

  private static R2StorageProperties properties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setPublicMediaBucket("publicas");
    properties.setPrivateMediaBucket("privadas");
    properties.setDocumentBucket("documentos");
    properties.setPublicMediaPrefix(PUBLIC_PREFIX);
    properties.setPrivateMediaPrefix(PRIVATE_PREFIX);
    properties.setDocumentPrefix(DOCUMENT_PREFIX);
    properties.setPublicBaseUrl("https://public.example.invalid");
    return properties;
  }

  private static final class MemoriaObjectStorage implements ObjectStorage {
    private final Map<StorageArea, Map<String, StoredObject>> objects =
        new EnumMap<>(StorageArea.class);
    private StorageArea failArea;
    private String failKey;

    private MemoriaObjectStorage() {
      for (StorageArea area : StorageArea.values()) {
        objects.put(area, new HashMap<>());
      }
    }

    void falharUmaVez(StorageArea area, String key) {
      failArea = area;
      failKey = key;
    }

    @Override
    public void put(StorageArea area, String key, byte[] content, String contentType) {
      objects.get(area).put(key, new StoredObject(content, contentType));
    }

    @Override
    public ObjectWriteResult putIfAbsent(
        StorageArea area,
        String key,
        byte[] content,
        String contentType) {
      if (objects.get(area).containsKey(key)) {
        return ObjectWriteResult.ALREADY_EXISTS;
      }
      put(area, key, content, contentType);
      return ObjectWriteResult.CREATED;
    }

    @Override
    public boolean exists(StorageArea area, String key) {
      return objects.get(area).containsKey(key);
    }

    @Override
    public StoredObject get(StorageArea area, String key) {
      return objects.get(area).get(key);
    }

    @Override
    public void delete(StorageArea area, String key) {
      if (area == failArea && key.equals(failKey)) {
        failArea = null;
        failKey = null;
        throw new IllegalStateException("falha sintetica R2");
      }
      objects.get(area).remove(key);
    }

    @Override
    public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
      return URI.create("https://private.example.invalid/token");
    }

    @Override
    public Optional<URI> publicUrl(StorageArea area, String key) {
      return Optional.of(URI.create("https://public.example.invalid/" + key));
    }
  }
}
