package br.com.topsdojob.v3.application.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ArquivoPublicidadeStoryRegistroServiceTest {
  private static final UUID STORY_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID USUARIO_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID ATIVACAO_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
  private static final UUID MIDIA_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
  private static final UUID VINCULO_ID = UUID.fromString("40000000-0000-4000-8000-000000000002");
  private static final UUID ANUNCIO_ID = UUID.fromString("40000000-0000-4000-8000-000000000003");
  private static final UUID JANELA_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");
  private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-09-25T12:00:00Z");
  private static final OffsetDateTime FIM = INICIO.plusHours(24);
  private static final byte[] BYTES = "imagem sintética".getBytes(StandardCharsets.UTF_8);

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
  private final R2StorageProperties properties = mock(R2StorageProperties.class);
  private final AnuncioMidiaRepository anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
  private final ArquivoMidiaRepository arquivoMidiaRepository = mock(ArquivoMidiaRepository.class);
  private final MidiaPublicaMapper midiaPublicaMapper = mock(MidiaPublicaMapper.class);
  private final PremiumPublicoMapper premiumPublicoMapper = mock(PremiumPublicoMapper.class);
  private final ArquivoPublicidadeTransicaoTemporalService transicoesTemporais =
      mock(ArquivoPublicidadeTransicaoTemporalService.class);
  private ArquivoPublicidadeStoryRegistroService service;

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    when(provider.getIfAvailable()).thenReturn(storage);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getPrivateMediaBucket()).thenReturn("privado");
    when(properties.getPrivateMediaPrefix()).thenReturn("private/");
    service = new ArquivoPublicidadeStoryRegistroService(jdbc, entityManager,
        new ObjectMapper().findAndRegisterModules(), provider, properties,
        anuncioMidiaRepository, arquivoMidiaRepository, midiaPublicaMapper,
        premiumPublicoMapper, transicoesTemporais);
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void storyDeCreditoFungivelArquivaBytesPrivadosSemInventarPagamento() {
    Map<String, Object> story = story("PUBLICADO");
    Map<String, Object> midia = midia();
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM arquivo_midia ar")) return List.of(midia);
      return List.of();
    });
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString())).thenAnswer(invocation ->
        new StoredObject(BYTES, "image/jpeg"));
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg")))
        .thenReturn(ObjectWriteResult.CREATED);

    service.registrarEstado(STORY_ID, "STORY_PUBLICADO", "req-story", INICIO);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> captor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_veiculacao"), captor.capture());
    assertThat(captor.getValue().get("classificacao")).isEqualTo("ORIGEM_INDETERMINADA");
    assertThat(captor.getValue().get("relacao")).isEqualTo("DESCONHECIDA");
    assertThat(captor.getValue().get("cobertura")).isEqualTo("PREVENTIVA");
    assertThat(captor.getValue().get("fim")).isEqualTo(FIM);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_midia"), anyMap());
    verify(storage).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA),
        org.mockito.ArgumentMatchers.contains("arquivo-publicidade/stories/"),
        eq(BYTES), eq("image/jpeg"));
  }

  @Test
  void encerramentoAntecipadoNaoAcessaStorageNemRecriaHistorico() {
    Map<String, Object> story = story("REMOVIDO");
    story.put("encerrado_em", INICIO.plusHours(2));
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao")) return List.of(Map.of(
          "id", JANELA_ID, "inicio_em", INICIO, "fim_em", FIM,
          "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
      return List.of();
    });

    service.registrarEstado(STORY_ID, "STORY_ENCERRADO_PELO_USUARIO", "req-close", INICIO.plusHours(2));

    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "UPDATE arquivo_publicidade_story_veiculacao"), anyMap());
    verify(storage, never()).get(any(), anyString());
    verify(storage, never()).putIfAbsent(any(), anyString(), any(), anyString());
  }

  @Test
  void encerramentoAtrasadoNaoFechaAntesDaVersaoMaisRecente() {
    OffsetDateTime pedido = INICIO.plusMinutes(1);
    OffsetDateTime ultimoInicio = INICIO.plusMinutes(3);
    OffsetDateTime observado = INICIO.plusMinutes(4);
    Map<String, Object> story = story("REMOVIDO");
    story.put("encerrado_em", pedido);
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return List.of(Map.of(
          "observado", observado, "ultimo_inicio", ultimoInicio));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao")) return List.of(Map.of(
          "id", JANELA_ID, "inicio_em", INICIO, "fim_em", FIM,
          "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
      return List.of();
    });

    service.registrarEstado(STORY_ID, "STORY_ENCERRADO_PELO_USUARIO", "req-atrasado", pedido);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> versao = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> janela = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "UPDATE arquivo_publicidade_story_versao"), versao.capture());
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "UPDATE arquivo_publicidade_story_veiculacao"), janela.capture());
    assertThat(versao.getValue().get("fim")).isEqualTo(ultimoInicio);
    assertThat(janela.getValue().get("fim")).isEqualTo(ultimoInicio);
    verify(jdbc, times(2)).queryForList(org.mockito.ArgumentMatchers.contains("clock_timestamp()"), anyMap());
    verify(storage, never()).putIfAbsent(any(), anyString(), any(), anyString());
  }

  @Test
  void mudancaAtrasadaUsaMarcoPosteriorAoLockParaNovaVersao() {
    OffsetDateTime pedido = INICIO.plusMinutes(1);
    OffsetDateTime ultimoInicio = INICIO.plusMinutes(3);
    OffsetDateTime observado = INICIO.plusMinutes(4);
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return List.of(Map.of(
          "observado", observado, "ultimo_inicio", ultimoInicio));
      if (sql.contains("FROM story_anuncio s")) return List.of(story("PUBLICADO"));
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao")) return List.of(Map.of(
          "id", JANELA_ID, "inicio_em", INICIO, "fim_em", FIM,
          "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
      if (sql.contains("FROM arquivo_publicidade_story_versao")) return List.of(Map.of(
          "id", UUID.randomUUID(), "numero", 1, "conteudo_sha256", "0".repeat(64)));
      if (sql.contains("FROM arquivo_midia ar")) return List.of(midia());
      return List.of();
    });
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString()))
        .thenReturn(new StoredObject(BYTES, "image/jpeg"));
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg")))
        .thenReturn(ObjectWriteResult.CREATED);

    service.registrarEstado(STORY_ID, "STORY_ATUALIZADO", "req-atrasado", pedido);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> versao = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_versao"), versao.capture());
    assertThat(versao.getValue().get("inicio")).isEqualTo(observado);
    assertThat(versao.getValue().get("numero")).isEqualTo(2);
    verify(jdbc, times(2)).queryForList(org.mockito.ArgumentMatchers.contains("clock_timestamp()"), anyMap());
  }

  @Test
  void rollbackLimpaSomenteCopiaCriada() {
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story("PUBLICADO"));
      if (sql.contains("FROM arquivo_midia ar")) return List.of(midia());
      return List.of();
    });
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString()))
        .thenReturn(new StoredObject(BYTES, "image/jpeg"));
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), anyString()))
        .thenReturn(ObjectWriteResult.CREATED);

    service.registrarEstado(STORY_ID, "STORY_PUBLICADO", "req-rollback", INICIO);
    for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    verify(storage).delete(eq(StorageArea.PRIVATE_MEDIA),
        org.mockito.ArgumentMatchers.contains("arquivo-publicidade/stories/"));
  }

  @Test
  void reconciliacaoRepetidaNaoDuplicaJanelaVersaoNemCopia() {
    Map<String, Object> story = story("PUBLICADO");
    AtomicReference<Map<String, Object>> janela = new AtomicReference<>();
    AtomicReference<Map<String, Object>> versao = new AtomicReference<>();
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM arquivo_midia ar")) return List.of(midia());
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao"))
        return janela.get() == null ? List.of() : List.of(janela.get());
      if (sql.contains("FROM arquivo_publicidade_story_versao"))
        return versao.get() == null ? List.of() : List.of(versao.get());
      return List.of();
    });
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString()))
        .thenReturn(new StoredObject(BYTES, "image/jpeg"));
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), anyString()))
        .thenReturn(ObjectWriteResult.CREATED);

    service.registrarEstado(STORY_ID, "STORY_PUBLICADO", "req-1", INICIO);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> janelaCaptor = ArgumentCaptor.forClass(Map.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> versaoCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_veiculacao"), janelaCaptor.capture());
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_versao"), versaoCaptor.capture());
    janela.set(Map.of("id", janelaCaptor.getValue().get("id"), "inicio_em", INICIO,
        "fim_em", FIM, "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
    versao.set(Map.of("id", versaoCaptor.getValue().get("id"), "numero", 1,
        "conteudo_sha256", versaoCaptor.getValue().get("hash")));

    service.registrarEstado(STORY_ID, "STORY_PUBLICADO_REPETIDO", "req-2", INICIO.plusMinutes(1));

    verify(jdbc, times(1)).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_veiculacao"), anyMap());
    verify(jdbc, times(1)).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_versao"), anyMap());
    verify(jdbc, times(1)).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_midia"), anyMap());
    verify(storage, times(1)).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), anyString());
  }

  @Test
  void storyAnuncioCongelaApresentacaoVisivelEBytesSelecionados() throws Exception {
    Map<String, Object> story = story("PUBLICADO");
    story.put("modo_conteudo", "ANUNCIO");
    story.put("anuncio_id", ANUNCIO_ID);
    story.put("arquivo_midia_id", null);
    story.put("anuncio_status", "PUBLICADO");
    story.put("status_moderacao", "APROVADO");
    story.put("removido_em", null);
    story.put("slug", "exemplo-story");
    story.put("titulo", "Exemplo de Story");
    story.put("descricao", "Resumo   visível\ncom espaço");
    story.put("categoria", "EXEMPLO");
    story.put("preco", java.math.BigDecimal.valueOf(90));
    Map<String, Object> media = midia();
    media.put("anuncio_midia_id", VINCULO_ID);
    media.put("visibilidade_midia", "LIVRE");
    media.put("bucket", "publico");
    media.put("chave_objeto", "public/foto.jpg");
    Map<String, Object> localizacao = new HashMap<>();
    localizacao.put("estado_id", UUID.randomUUID());
    localizacao.put("uf", "SP");
    localizacao.put("cidade_id", UUID.randomUUID());
    localizacao.put("cidade", "São Paulo");
    localizacao.put("bairro_id", null);
    localizacao.put("bairro", null);
    localizacao.put("endereco_resumido", null);
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM anuncio_midia am JOIN arquivo_midia ar")) return List.of(media);
      if (sql.contains("FROM anuncio_localizacao l")) return List.of(localizacao);
      return List.of();
    });
    when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of());
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    when(vinculo.getArquivoMidiaId()).thenReturn(MIDIA_ID);
    when(anuncioMidiaRepository.findByAnuncioId(ANUNCIO_ID)).thenReturn(List.of(vinculo));
    ArquivoMidiaEntity arquivo = mock(ArquivoMidiaEntity.class);
    when(arquivo.getId()).thenReturn(MIDIA_ID);
    when(arquivoMidiaRepository.findByIdIn(List.of(MIDIA_ID))).thenReturn(List.of(arquivo));
    when(premiumPublicoMapper.flagsPorAnuncioIds(List.of(ANUNCIO_ID)))
        .thenReturn(Map.of(ANUNCIO_ID, PremiumPublicoFlagsDto.vazio()));
    when(midiaPublicaMapper.publicas(any(), anyMap(), eq(true), anyInt(), eq(false)))
        .thenReturn(List.of(new MidiaPublicaDto(VINCULO_ID, "FOTO", "CAPA", 0, "LIVRE",
            true, "https://example.invalid/foto.jpg", null, null, 100, 100, "image/jpeg")));
    when(properties.getPublicMediaBucket()).thenReturn("publico");
    when(properties.getPublicMediaPrefix()).thenReturn("public/");
    when(storage.get(eq(StorageArea.PUBLIC_MEDIA), eq("public/foto.jpg")))
        .thenReturn(new StoredObject(BYTES, "image/jpeg"));
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString()))
        .thenReturn(new StoredObject(BYTES, "image/jpeg"));
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg")))
        .thenReturn(ObjectWriteResult.CREATED);

    service.registrarEstado(STORY_ID, "STORY_PUBLICADO", "req-anuncio", INICIO);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> captor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_versao"), captor.capture());
    var conteudo = new ObjectMapper().readTree((String) captor.getValue().get("conteudo"));
    assertThat(conteudo.path("apresentacaoStory").path("cidade").asText()).isEqualTo("São Paulo");
    assertThat(conteudo.path("apresentacaoStory").path("uf").asText()).isEqualTo("SP");
    assertThat(conteudo.path("apresentacaoStory").path("resumo").asText())
        .isEqualTo("Resumo visível com espaço");
    assertThat(conteudo.path("urlAnuncioNaCaptura").asText()).isEqualTo("/anuncios/exemplo-story");
    assertThat(conteudo.path("midias").size()).isEqualTo(1);
    verify(storage).get(StorageArea.PUBLIC_MEDIA, "public/foto.jpg");
  }

  @Test
  void retomadaAbreSegundoPeriodoSemAlterarOPrimeiroENaoUltrapassaPrazoOriginal() {
    OffsetDateTime fechamento = INICIO.plusHours(1);
    OffsetDateTime retomada = INICIO.plusHours(2);
    Map<String, Object> antigo = Map.of("id", JANELA_ID, "inicio_em", INICIO,
        "fim_em", fechamento, "encerramento_motivo", "ANUNCIO_PAUSADO");
    AtomicReference<Map<String, Object>> ultimo = new AtomicReference<>(antigo);
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story("PUBLICADO"));
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao")) return List.of(ultimo.get());
      if (sql.contains("FROM arquivo_midia ar")) return List.of(midia());
      return List.of();
    });
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString()))
        .thenReturn(new StoredObject(BYTES, "image/jpeg"));
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg")))
        .thenReturn(ObjectWriteResult.CREATED);

    service.registrarEstado(STORY_ID, "STORY_RETOMADO", "req-retomada", retomada);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> nova = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_veiculacao"), nova.capture());
    assertThat(nova.getValue().get("id")).isNotEqualTo(JANELA_ID);
    assertThat(nova.getValue().get("inicio")).isEqualTo(retomada);
    assertThat(nova.getValue().get("fim")).isEqualTo(FIM);
    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains(
        "UPDATE arquivo_publicidade_story_veiculacao"), anyMap());
    verify(jdbc, times(2)).queryForList(org.mockito.ArgumentMatchers.contains(
        "ORDER BY inicio_em DESC, id DESC LIMIT 1 FOR UPDATE"), anyMap());
    ultimo.set(Map.of("id", nova.getValue().get("id"), "inicio_em", retomada,
        "fim_em", FIM, "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
    service.registrarEstado(STORY_ID, "STORY_REPETIDO", "req-repetido", FIM.plusMinutes(1));
    verify(jdbc, times(1)).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_veiculacao"), anyMap());
  }

  @Test
  void retiradaDeStoryAnuncioUsaReferenciaDaCopiaAnteriorSemStorage() {
    Map<String, Object> story = storyAnuncio();
    Map<String, Object> selecionada = midiaAnuncio();
    UUID versaoAnterior = UUID.randomUUID();
    UUID copiaAnterior = UUID.randomUUID();
    Map<String, Object> origem = origem(versaoAnterior, copiaAnterior, selecionada);
    prepararSelecaoAnuncio();
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao")) return List.of(Map.of(
          "id", JANELA_ID, "inicio_em", INICIO, "fim_em", FIM,
          "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
      if (sql.contains("FROM arquivo_publicidade_story_versao")) return List.of(Map.of(
          "id", versaoAnterior, "numero", 1, "conteudo_sha256", "0".repeat(64)));
      if (sql.contains("FROM anuncio_midia am JOIN arquivo_midia ar")) return List.of(selecionada);
      if (sql.contains("FROM arquivo_publicidade_story_midia m")) return List.of(origem);
      return List.of();
    });
    when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of());
    doThrow(new IllegalStateException("provider sinteticamente indisponivel"))
        .when(provider).getIfAvailable();
    when(midiaPublicaMapper.publicas(any(), anyMap(), eq(true), anyInt(), eq(false)))
        .thenThrow(new IllegalStateException("URL nao deve ser resolvida na retirada"));

    service.registrarEstado(STORY_ID, "MIDIA_REMOVIDA_PELO_PROPRIETARIO", "req-retirada",
        INICIO.plusHours(1));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> referencia = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_midia_referencia"), referencia.capture());
    assertThat(referencia.getValue().get("origemId")).isEqualTo(copiaAnterior);
    assertThat(referencia.getValue().get("arquivoId")).isEqualTo(MIDIA_ID);
    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_midia ("), anyMap());
    verify(storage, never()).get(any(), anyString());
    verify(storage, never()).putIfAbsent(any(), anyString(), any(), anyString());
    verify(provider, never()).getIfAvailable();
    verify(midiaPublicaMapper, never()).publicas(any(), anyMap(), eq(true), anyInt(), eq(false));
  }

  @Test
  void retiradaComSobreviventeSemCopiaEncerraPeriodoComLacunaSemFabricarVersao() {
    Map<String, Object> story = storyAnuncio();
    Map<String, Object> selecionada = midiaAnuncio();
    prepararSelecaoAnuncio();
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao")) return List.of(Map.of(
          "id", JANELA_ID, "inicio_em", INICIO, "fim_em", FIM,
          "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
      if (sql.contains("FROM arquivo_publicidade_story_versao")) return List.of(Map.of(
          "id", UUID.randomUUID(), "numero", 1, "conteudo_sha256", "0".repeat(64)));
      if (sql.contains("FROM anuncio_midia am JOIN arquivo_midia ar")) return List.of(selecionada);
      return List.of();
    });
    when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of());

    service.registrarEstado(STORY_ID, "MODERACAO_FOTO_EXCLUIDA",
        "req-descoberta", INICIO.plusHours(1));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> fechamento = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "UPDATE arquivo_publicidade_story_veiculacao"), fechamento.capture());
    assertThat(fechamento.getValue().get("motivo"))
        .isEqualTo("LACUNA_MIDIA_RETIRADA_SEM_COPIA_VERIFICADA");
    verify(storage, never()).get(any(), anyString());
    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_versao"), anyMap());
  }

  @Test
  void retiradaDeStoryPreexistenteSemVersaoNaoFabricaHistorico() {
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(storyAnuncio());
      return List.of();
    });

    service.registrarEstado(STORY_ID, "MIDIA_REMOVIDA_PELO_PROPRIETARIO",
        "req-lacuna", INICIO.plusHours(1));

    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_veiculacao"), anyMap());
    verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains(
        "INSERT INTO arquivo_publicidade_story_versao"), anyMap());
    verify(storage, never()).get(any(), anyString());
  }

  @Test
  void preflightIgnoraLacunaLegadaSemDespublicarFotosDoAnuncio() {
    prepararSelecaoAnuncio();
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("SELECT s.id, j.id AS veiculacao_id")) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", STORY_ID);
        row.put("veiculacao_id", null);
        return List.of(row);
      }
      if (sql.contains("FROM anuncio_midia am JOIN arquivo_midia ar")) {
        return List.of(midiaAnuncio());
      }
      return List.of();
    });

    assertThat(service.midiasSemCopiaParaRetiradaPorAnuncio(ANUNCIO_ID)).isEmpty();
    verify(storage, never()).get(any(), anyString());
  }

  @Test
  void previewRegeneradoAposCopiaAnteriorNaoPodeSerReferenciado() {
    Map<String, Object> story = storyAnuncio();
    Map<String, Object> selecionada = midiaAnuncio();
    selecionada.put("visibilidade_midia", "RESTRITA_18");
    selecionada.put("preview_restrito_status", "DISPONIVEL");
    selecionada.put("preview_restrito_confirmado_em", INICIO.plusMinutes(20));
    UUID versaoAnterior = UUID.randomUUID();
    Map<String, Object> original = origem(versaoAnterior, UUID.randomUUID(), selecionada);
    Map<String, Object> preview = origem(versaoAnterior, UUID.randomUUID(), selecionada);
    preview.put("variante", "PREVIEW_RESTRITO");
    preview.put("chave_privada", "private/arquivo-publicidade/stories/" + versaoAnterior + "/"
        + VINCULO_ID + "/" + MIDIA_ID + "/preview_restrito");
    prepararSelecaoAnuncio();
    when(jdbc.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("AS ultimo_inicio")) return marcos(invocation.getArgument(1));
      if (sql.contains("FROM story_anuncio s")) return List.of(story);
      if (sql.contains("FROM arquivo_publicidade_story_veiculacao")) return List.of(Map.of(
          "id", JANELA_ID, "inicio_em", INICIO, "fim_em", FIM,
          "encerramento_motivo", "LIMITE_AUTOMATICO_STORY"));
      if (sql.contains("FROM arquivo_publicidade_story_versao")) return List.of(Map.of(
          "id", versaoAnterior, "numero", 1, "conteudo_sha256", "0".repeat(64)));
      if (sql.contains("FROM anuncio_midia am JOIN arquivo_midia ar")) return List.of(selecionada);
      if (sql.contains("FROM arquivo_publicidade_story_midia m")) return List.of(original, preview);
      return List.of();
    });
    when(jdbc.queryForList(anyString(), anyMap(), eq(String.class))).thenReturn(List.of());

    service.registrarEstado(STORY_ID,
        "MIDIA_REMOVIDA_PELO_PROPRIETARIO", "req-preview", INICIO.plusHours(1));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> fechamento = ArgumentCaptor.forClass(Map.class);
    verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
        "UPDATE arquivo_publicidade_story_veiculacao"), fechamento.capture());
    assertThat(fechamento.getValue().get("motivo"))
        .isEqualTo("LACUNA_MIDIA_RETIRADA_SEM_COPIA_VERIFICADA");
    verify(storage, never()).get(any(), anyString());
  }

  private Map<String, Object> storyAnuncio() {
    Map<String, Object> row = story("PUBLICADO");
    row.put("modo_conteudo", "ANUNCIO");
    row.put("anuncio_id", ANUNCIO_ID);
    row.put("arquivo_midia_id", null);
    row.put("anuncio_status", "PUBLICADO");
    row.put("status_moderacao", "APROVADO");
    row.put("removido_em", null);
    row.put("slug", "anuncio-story");
    row.put("titulo", "Anuncio Story");
    row.put("descricao", "Descricao");
    return row;
  }

  private Map<String, Object> midiaAnuncio() {
    Map<String, Object> row = midia();
    row.put("anuncio_midia_id", VINCULO_ID);
    row.put("visibilidade_midia", "LIVRE");
    row.put("bucket", "publico");
    row.put("chave_objeto", "public/foto.jpg");
    return row;
  }

  private Map<String, Object> origem(UUID versao, UUID copia, Map<String, Object> midia) {
    Map<String, Object> row = new HashMap<>();
    row.put("origem_midia_id", copia);
    row.put("origem_versao_id", versao);
    row.put("anuncio_midia_id", VINCULO_ID);
    row.put("arquivo_midia_id", MIDIA_ID);
    row.put("variante", "ORIGINAL");
    row.put("storage_provider", "R2");
    row.put("bucket", "privado");
    row.put("chave_privada", "private/arquivo-publicidade/stories/" + versao + "/"
        + VINCULO_ID + "/" + MIDIA_ID + "/original");
    row.put("sha256", midia.get("sha256"));
    row.put("mime_type", midia.get("mime_type"));
    row.put("tamanho_bytes", BYTES.length);
    row.put("origem_capturada_em", INICIO);
    return row;
  }

  private void prepararSelecaoAnuncio() {
    AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
    when(vinculo.getId()).thenReturn(VINCULO_ID);
    when(vinculo.getAnuncioId()).thenReturn(ANUNCIO_ID);
    when(vinculo.getArquivoMidiaId()).thenReturn(MIDIA_ID);
    when(vinculo.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
    when(vinculo.getFinalidade()).thenReturn(FinalidadeAnuncioMidia.CAPA);
    when(vinculo.getOrdem()).thenReturn(0);
    when(vinculo.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
    when(vinculo.getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
    when(anuncioMidiaRepository.findByAnuncioId(ANUNCIO_ID)).thenReturn(List.of(vinculo));
    ArquivoMidiaEntity arquivo = mock(ArquivoMidiaEntity.class);
    when(arquivo.getId()).thenReturn(MIDIA_ID);
    when(arquivoMidiaRepository.findByIdIn(List.of(MIDIA_ID))).thenReturn(List.of(arquivo));
    when(premiumPublicoMapper.flagsPorAnuncioIds(List.of(ANUNCIO_ID)))
        .thenReturn(Map.of(ANUNCIO_ID, PremiumPublicoFlagsDto.vazio()));
    when(midiaPublicaMapper.publicas(any(), anyMap(), eq(true), anyInt(), eq(false)))
        .thenReturn(List.of(new MidiaPublicaDto(VINCULO_ID, "FOTO", "CAPA", 0, "LIVRE",
            true, "https://example.invalid/foto.jpg", null, null, 100, 100, "image/jpeg")));
  }

  private Map<String, Object> story(String status) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", STORY_ID);
    row.put("anuncio_id", null);
    row.put("anuncio_midia_id", null);
    row.put("arquivo_midia_id", MIDIA_ID);
    row.put("modo_conteudo", "MIDIA_UPLOAD");
    row.put("ativacao_beneficio_id", ATIVACAO_ID);
    row.put("status", status);
    row.put("inicio_em", INICIO);
    row.put("fim_em", FIM);
    row.put("encerrado_em", null);
    row.put("criado_por", USUARIO_ID);
    row.put("nome", "Usuária exemplo");
    row.put("nome_civil", "Nome Civil Exemplo");
    row.put("cpf_normalizado", "12345678909");
    row.put("email_normalizado", "exemplo@example.invalid");
    row.put("usuario_status", "ATIVO");
    row.put("tipo_conta", "ANUNCIANTE");
    row.put("desativado_em", null);
    row.put("excluido_em", null);
    row.put("origem", "CREDITO");
    row.put("grupo_ativacao_id", UUID.randomUUID());
    row.put("custo_creditos_snapshot", 3);
    row.put("grupo_ator_id", null);
    row.put("beneficio_codigo", "STORIES");
    row.put("beneficio_escopo", "MIDIA");
    row.put("movimento_credito_id", UUID.randomUUID());
    return row;
  }

  private List<Map<String, Object>> marcos(Map<String, Object> parametros) {
    OffsetDateTime pedido = (OffsetDateTime) parametros.get("instante");
    OffsetDateTime inicio = (OffsetDateTime) parametros.get("inicioStory");
    return List.of(Map.of("observado", pedido.isAfter(inicio) ? pedido : inicio,
        "ultimo_inicio", inicio));
  }

  private Map<String, Object> midia() {
    Map<String, Object> row = new HashMap<>();
    row.put("anuncio_midia_id", null);
    row.put("arquivo_midia_id", MIDIA_ID);
    row.put("tipo", "FOTO");
    row.put("ordem", 0);
    row.put("visibilidade_midia", "RESTRITA_18");
    row.put("storage_provider", "R2");
    row.put("bucket", "privado");
    row.put("chave_objeto", "private/stories/contas/foto.jpg");
    row.put("sha256", sha(BYTES));
    row.put("mime_type", "image/jpeg");
    row.put("preview_restrito_chave", null);
    row.put("preview_restrito_status", "DESCONHECIDO");
    return row;
  }

  private String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
