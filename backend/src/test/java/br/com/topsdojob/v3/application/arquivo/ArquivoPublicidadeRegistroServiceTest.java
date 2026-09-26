package br.com.topsdojob.v3.application.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ArquivoPublicidadeRegistroServiceTest {

  private static final OffsetDateTime AGORA = OffsetDateTime.of(
      2026, 9, 25, 12, 0, 0, 0, ZoneOffset.UTC);

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
  private final R2StorageProperties properties = properties();
  private final ArquivoPublicidadeStoryRegistroService stories =
      mock(ArquivoPublicidadeStoryRegistroService.class);
  private final PremiumPublicoMapper premiumPublico = mock(PremiumPublicoMapper.class);
  private final ArquivoPublicidadeRegistroService service = new ArquivoPublicidadeRegistroService(
      jdbc, entityManager, new ObjectMapper(), provider, properties, stories, premiumPublico,
      mock(ArquivoPublicidadeTransicaoTemporalService.class));
  private boolean contaDesativada;
  private List<Map<String, Object>> midiasOverride;
  private List<Map<String, Object>> janelasOverride = List.of();
  private List<Map<String, Object>> versoesOverride = List.of();
  private List<Map<String, Object>> copiasOverride = List.of();
  private final Set<UUID> midiasOcultasOverride = new java.util.HashSet<>();

  @AfterEach
  void limparTransacao() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void semBeneficioNaoCriaArquivoPessoalGratuito() {
    UUID anuncioId = UUID.randomUUID();
    configurarLeituras(anuncioId, List.of(), false);

    service.registrarEstado(anuncioId, "PUBLICACAO", "req-sintetico", AGORA);

    verify(jdbc, never()).update(anyString(), any(Map.class));
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void contaDesativadaNaoAbreVeiculacaoMesmoComBeneficioAtivo() {
    UUID anuncioId = UUID.randomUUID();
    contaDesativada = true;
    configurarLeituras(anuncioId, List.of(ativacao(AGORA.plusDays(1))), false);

    service.registrarEstado(anuncioId, "ATIVACAO", null, AGORA);

    verify(jdbc, never()).update(anyString(), any(Map.class));
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void anuncioPagoComTextoPublicoSemMidiaExibivelAindaTemVersao() {
    UUID anuncioId = UUID.randomUUID();
    midiasOverride = List.of();
    configurarLeituras(anuncioId, List.of(ativacao(AGORA.plusDays(1))), false);

    service.registrarEstado(anuncioId, "PUBLICACAO", null, AGORA);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), parametros.capture());
    String conteudo = (String) parametrosPara(sql.getAllValues(), parametros.getAllValues(),
        "INSERT INTO arquivo_publicidade_versao").get("conteudo");
    assertThat(conteudo).contains("Titulo sintetico", "\"midias\":[]");
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void ativacaoInconsistenteSemEfeitoPublicoNaoCriaArquivoPessoal() {
    UUID anuncioId = UUID.randomUUID();
    configurarLeituras(anuncioId, List.of(ativacao(AGORA.plusDays(1))), false);
    when(premiumPublico.idsAtivacoesComEfeitoPublico(anuncioId)).thenReturn(Set.of());

    service.registrarEstado(anuncioId, "ATIVACAO", null, AGORA);

    verify(jdbc, never()).update(anyString(), any(Map.class));
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void creditoComSaldoFungivelEOrigemLegadaTemCopiaPrivadaSemInventarPagamento() {
    UUID anuncioId = UUID.randomUUID();
    OffsetDateTime fimGrupo = AGORA.plusDays(2);
    configurarLeituras(anuncioId, List.of(ativacao(fimGrupo)), true);
    when(jdbc.queryForObject(anyString(), any(Map.class), eq(OffsetDateTime.class)))
        .thenReturn(AGORA.plusSeconds(10));
    when(provider.getIfAvailable()).thenReturn(storage);
    byte[] bytes = new byte[] {1, 2, 3};
    StoredObject objeto = new StoredObject(bytes, "image/jpeg");
    when(storage.get(eq(StorageArea.PRESERVED_PUBLIC_MEDIA), anyString())).thenReturn(objeto);
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString())).thenReturn(objeto);
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg")))
        .thenReturn(ObjectWriteResult.CREATED);
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    service.registrarEstado(anuncioId, "PREMIUM_COMPRA_CREDITOS", "req-sintetico", AGORA);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), parametros.capture());
    Map<String, ?> janela = parametrosPara(sql.getAllValues(), parametros.getAllValues(),
        "INSERT INTO arquivo_publicidade_veiculacao", "PREVENTIVA");
    assertThat(janela.get("limite")).isEqualTo(fimGrupo);
    assertThat(janela.get("instante")).isEqualTo(AGORA.plusSeconds(10));
    assertThat(janela.get("classificacao")).isEqualTo("ORIGEM_INDETERMINADA");
    assertThat(janela.get("relacao")).isEqualTo("DESCONHECIDA");
    Map<String, ?> versao = parametrosPara(sql.getAllValues(), parametros.getAllValues(),
        "INSERT INTO arquivo_publicidade_versao", "SALDO_FUNGIVEL");
    assertThat(versao.get("comercial").toString()).contains("\"pagamentoId\":null")
        .contains("SALDO_FUNGIVEL_SEM_ALOCACAO_EXATA");
    verify(storage).get(eq(StorageArea.PRESERVED_PUBLIC_MEDIA), anyString());
    verify(storage).putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg"));
    // Unknown completion may have committed the DB transaction; never erase its proof.
    for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
    }
    verify(storage, never()).delete(eq(StorageArea.PRIVATE_MEDIA), anyString());
  }

  @Test
  void rollbackConhecidoCompensaSomenteCopiaNova() {
    UUID anuncioId = UUID.randomUUID();
    configurarLeituras(anuncioId, List.of(ativacao(AGORA.plusDays(1))), false);
    when(provider.getIfAvailable()).thenReturn(storage);
    StoredObject objeto = new StoredObject(new byte[] {4, 5, 6}, "image/jpeg");
    when(storage.get(eq(StorageArea.PUBLIC_MEDIA), anyString())).thenReturn(objeto);
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString())).thenReturn(objeto);
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg")))
        .thenReturn(ObjectWriteResult.CREATED);
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    service.registrarEstado(anuncioId, "PUBLICACAO", null, AGORA);
    for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    verify(storage).delete(eq(StorageArea.PRIVATE_MEDIA), anyString());
  }

  @Test
  void retiradaReusaCopiaConfirmadaMesmoComStorageIndisponivel() {
    UUID anuncioId = UUID.randomUUID();
    Map<String, Object> ativacao = ativacao(AGORA.plusDays(1));
    Map<String, Object> sobrevivente = midia(false);
    midiasOverride = List.of(sobrevivente);
    UUID janelaId = UUID.randomUUID();
    UUID versaoAnteriorId = UUID.randomUUID();
    UUID copiaId = UUID.randomUUID();
    janelasOverride = List.of(Map.of(
        "id", janelaId,
        "ativacao_beneficio_id", ativacao.get("id"),
        "classificacao", "ORIGEM_INDETERMINADA",
        "relacao_material", "DESCONHECIDA",
        "cobertura", "PREVENTIVA",
        "inicio_em", AGORA.minusHours(1),
        "fim_em", AGORA.plusDays(1)));
    versoesOverride = List.of(Map.of(
        "id", versaoAnteriorId,
        "numero", 1,
        "conteudo_sha256", "0".repeat(64),
        "capturado_em", AGORA.minusHours(1)));
    copiasOverride = List.of(Map.of(
        "origem_midia_id", copiaId,
        "anuncio_midia_id", sobrevivente.get("id"),
        "arquivo_midia_id", sobrevivente.get("arquivo_midia_id"),
        "variante", "ORIGINAL",
        "sha256", "1".repeat(64),
        "mime_type", "image/jpeg"));
    configurarLeituras(anuncioId, List.of(ativacao), false);
    when(provider.getIfAvailable()).thenThrow(new IllegalStateException("R2 indisponivel"));

    assertThatCode(() -> service.registrarEstado(
        anuncioId, "MIDIA_REMOVIDA_PELO_PROPRIETARIO", "req-retirada", AGORA))
        .doesNotThrowAnyException();

    verifyNoInteractions(storage);
    verify(provider, never()).getIfAvailable();
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), parametros.capture());
    Map<String, ?> referencia = parametrosPara(sql.getAllValues(), parametros.getAllValues(),
        "INSERT INTO arquivo_publicidade_midia_referencia");
    assertThat(referencia.get("origemMidiaId")).isEqualTo(copiaId);
    assertThat(referencia.get("anuncioMidiaId")).isEqualTo(sobrevivente.get("id"));
  }

  @Test
  void retiradaLegadaSemVersaoAnteriorFechaLacunaSemCapturaNova() {
    UUID anuncioId = UUID.randomUUID();
    Map<String, Object> ativacao = ativacao(AGORA.plusDays(1));
    UUID janelaId = UUID.randomUUID();
    midiasOverride = List.of(midia(false));
    janelasOverride = List.of(janela(janelaId, (UUID) ativacao.get("id")));
    configurarLeituras(anuncioId, List.of(ativacao), false);
    when(provider.getIfAvailable()).thenThrow(new IllegalStateException("R2 indisponivel"));

    assertThatCode(() -> service.registrarEstado(
        anuncioId, "MIDIA_REMOVIDA_PELO_PROPRIETARIO", "req-legado", AGORA))
        .doesNotThrowAnyException();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), parametros.capture());
    assertThat(sql.getAllValues()).noneMatch(item -> item.contains("INSERT INTO arquivo_publicidade_versao"));
    assertThat(parametrosPara(sql.getAllValues(), parametros.getAllValues(),
        "UPDATE arquivo_publicidade_veiculacao").get("motivo"))
        .isEqualTo("RETIRADA_SEM_COPIA_PRIVADA_ANTERIOR");
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void retiradaNaoReusaCopiaAnteriorAReprocessamentoDoMesmoArquivo() {
    UUID anuncioId = UUID.randomUUID();
    Map<String, Object> ativacao = ativacao(AGORA.plusDays(1));
    Map<String, Object> sobrevivente = midia(false);
    sobrevivente.put("processado_em", AGORA.minusMinutes(10));
    midiasOverride = List.of(sobrevivente);
    janelasOverride = List.of(janela(UUID.randomUUID(), (UUID) ativacao.get("id")));
    versoesOverride = List.of(Map.of(
        "id", UUID.randomUUID(), "numero", 1,
        "conteudo_sha256", "0".repeat(64), "capturado_em", AGORA.minusHours(1)));
    copiasOverride = List.of(Map.of(
        "origem_midia_id", UUID.randomUUID(),
        "anuncio_midia_id", sobrevivente.get("id"),
        "arquivo_midia_id", sobrevivente.get("arquivo_midia_id"),
        "variante", "ORIGINAL", "sha256", "1".repeat(64),
        "mime_type", "image/jpeg", "origem_capturada_em", AGORA.minusHours(1)));
    configurarLeituras(anuncioId, List.of(ativacao), false);

    service.registrarEstado(anuncioId, "MIDIA_REMOVIDA_PELO_PROPRIETARIO", null, AGORA);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), parametros.capture());
    assertThat(sql.getAllValues()).noneMatch(item -> item.contains("INSERT INTO arquivo_publicidade_midia_referencia"));
    assertThat(parametrosPara(sql.getAllValues(), parametros.getAllValues(),
        "UPDATE arquivo_publicidade_veiculacao").get("motivo"))
        .isEqualTo("RETIRADA_SEM_COPIA_PRIVADA_ANTERIOR");
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void retiradaNaoReusaPreviewReconfirmadoDepoisDaCopia() {
    UUID anuncioId = UUID.randomUUID();
    Map<String, Object> ativacao = ativacao(AGORA.plusDays(1));
    Map<String, Object> sobrevivente = midia(false);
    sobrevivente.put("visibilidade_midia", "RESTRITA_18");
    sobrevivente.put("preview_restrito_status", "DISPONIVEL");
    sobrevivente.put("preview_restrito_confirmado_em", AGORA.minusMinutes(10));
    midiasOverride = List.of(sobrevivente);
    janelasOverride = List.of(janela(UUID.randomUUID(), (UUID) ativacao.get("id")));
    versoesOverride = List.of(Map.of(
        "id", UUID.randomUUID(), "numero", 1,
        "conteudo_sha256", "0".repeat(64), "capturado_em", AGORA.minusHours(1)));
    Map<String, Object> original = new HashMap<>(Map.of(
        "origem_midia_id", UUID.randomUUID(), "anuncio_midia_id", sobrevivente.get("id"),
        "arquivo_midia_id", sobrevivente.get("arquivo_midia_id"),
        "variante", "ORIGINAL", "sha256", "1".repeat(64), "mime_type", "image/jpeg",
        "origem_capturada_em", AGORA.minusHours(1)));
    Map<String, Object> preview = new HashMap<>(original);
    preview.put("origem_midia_id", UUID.randomUUID());
    preview.put("variante", "PREVIEW_RESTRITO");
    copiasOverride = List.of(original, preview);
    configurarLeituras(anuncioId, List.of(ativacao), false);

    service.registrarEstado(anuncioId, "MIDIA_REMOVIDA_PELO_PROPRIETARIO", null, AGORA);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), any(Map.class));
    assertThat(sql.getAllValues()).noneMatch(item -> item.contains("INSERT INTO arquivo_publicidade_midia_referencia"));
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void retiradaLegadaOcultaSomenteFotoPromovidaSemCopiaEMantemSobreviventeAnterior() {
    UUID anuncioId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    UUID atorId = UUID.randomUUID();
    Map<String, Object> ativacao = ativacao(AGORA.plusDays(1));
    Map<String, Object> sobreviventeAntiga = midia(false);
    Map<String, Object> promovida = midia(false);
    promovida.put("ordem", 1);
    promovida.put("finalidade", "GALERIA");
    midiasOverride = List.of(sobreviventeAntiga, promovida);
    janelasOverride = List.of(janela(UUID.randomUUID(), (UUID) ativacao.get("id")));
    configurarLeituras(anuncioId, List.of(ativacao), false);
    AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(anuncioId, usuarioId,
        "perfil-sintetico", "Titulo sintetico", "Descricao sintetica",
        StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, AGORA.minusDays(1));
    when(entityManager.find(AnuncioEntity.class, anuncioId)).thenReturn(anuncio);
    AnuncioMidiaEntity vinculoPromovido = mock(AnuncioMidiaEntity.class);
    UUID promovidaId = (UUID) promovida.get("id");
    when(entityManager.find(AnuncioMidiaEntity.class, promovidaId)).thenReturn(vinculoPromovido);
    when(vinculoPromovido.getAnuncioId()).thenReturn(anuncioId);
    when(vinculoPromovido.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
    when(vinculoPromovido.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
    when(vinculoPromovido.getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
    doAnswer(invocation -> {
      midiasOcultasOverride.add(promovidaId);
      return null;
    }).when(vinculoPromovido).aplicarDecisao(
        StatusAnuncioMidia.PENDENTE, VisibilidadeMidia.LIVRE, AGORA);

    List<UUID> suprimidas = service.prepararRetiradaSemNovaCopia(anuncioId, atorId,
        "req-promovida", AGORA, Set.of((UUID) sobreviventeAntiga.get("id")));

    assertThat(suprimidas).containsExactly(promovidaId);
    assertThat(service.possuiFotoPublicaSelecionada(anuncioId)).isTrue();
    verify(vinculoPromovido).aplicarDecisao(
        StatusAnuncioMidia.PENDENTE, VisibilidadeMidia.LIVRE, AGORA);
    verify(entityManager, never()).find(AnuncioMidiaEntity.class,
        (UUID) sobreviventeAntiga.get("id"));
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void retiradaLegadaContinuaAteOcultarMaisDeDezPromocoesSemCopia() {
    UUID anuncioId = UUID.randomUUID();
    Map<String, Object> ativacao = ativacao(AGORA.plusDays(1));
    List<Map<String, Object>> fotos = new ArrayList<>();
    for (int ordem = 0; ordem < 16; ordem++) {
      Map<String, Object> foto = midia(false);
      foto.put("ordem", ordem);
      foto.put("finalidade", ordem == 0 ? "CAPA" : "GALERIA");
      fotos.add(foto);
    }
    midiasOverride = fotos;
    janelasOverride = List.of(janela(UUID.randomUUID(), (UUID) ativacao.get("id")));
    configurarLeituras(anuncioId, List.of(ativacao), false);
    AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(anuncioId,
        UUID.randomUUID(), "perfil-legado", "Titulo sintetico", "Descricao sintetica",
        StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO, AGORA.minusDays(1));
    when(entityManager.find(AnuncioEntity.class, anuncioId)).thenReturn(anuncio);
    for (Map<String, Object> foto : fotos.subList(3, fotos.size())) {
      UUID id = (UUID) foto.get("id");
      AnuncioMidiaEntity vinculo = mock(AnuncioMidiaEntity.class);
      when(entityManager.find(AnuncioMidiaEntity.class, id)).thenReturn(vinculo);
      when(vinculo.getAnuncioId()).thenReturn(anuncioId);
      when(vinculo.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
      when(vinculo.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
      when(vinculo.getVisibilidadeMidia()).thenReturn(VisibilidadeMidia.LIVRE);
      doAnswer(invocation -> {
        midiasOcultasOverride.add(id);
        return null;
      }).when(vinculo).aplicarDecisao(
          StatusAnuncioMidia.PENDENTE, VisibilidadeMidia.LIVRE, AGORA);
    }
    Set<UUID> exibidasAntes = fotos.subList(0, 3).stream()
        .map(foto -> (UUID) foto.get("id"))
        .collect(java.util.stream.Collectors.toSet());

    List<UUID> suprimidas = service.prepararRetiradaSemNovaCopia(anuncioId,
        UUID.randomUUID(), "req-mais-dez", AGORA, exibidasAntes);

    assertThat(suprimidas).hasSize(13)
        .doesNotContainAnyElementsOf(exibidasAntes);
    assertThat(service.midiasExibidasAntesDaRetirada(anuncioId)).containsExactlyInAnyOrderElementsOf(exibidasAntes);
    verify(provider, never()).getIfAvailable();
  }

  @Test
  void preservaSoMidiasSelecionadasPelaGaleriaPublicaMesmoComArquivoInvalidoNaPosicao() {
    UUID anuncioId = UUID.randomUUID();
    Map<String, Object> video = midia(false);
    video.put("tipo", "VIDEO");
    video.put("finalidade", "GALERIA");
    List<Map<String, Object>> fotos = new ArrayList<>();
    for (int ordem = 0; ordem < 6; ordem++) {
      Map<String, Object> foto = midia(false);
      foto.put("ordem", ordem);
      fotos.add(foto);
    }
    fotos.get(1).put("status_arquivo", "PENDENTE");
    midiasOverride = new ArrayList<>(List.of(video));
    midiasOverride.addAll(fotos);
    configurarLeituras(anuncioId, List.of(ativacao(AGORA.plusDays(1))), false);
    when(provider.getIfAvailable()).thenReturn(storage);
    StoredObject objeto = new StoredObject(new byte[] {1, 2, 3}, "image/jpeg");
    when(storage.get(eq(StorageArea.PUBLIC_MEDIA), anyString())).thenReturn(objeto);
    when(storage.get(eq(StorageArea.PRIVATE_MEDIA), anyString())).thenReturn(objeto);
    when(storage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), eq("image/jpeg")))
        .thenReturn(ObjectWriteResult.CREATED);
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    service.registrarEstado(anuncioId, "PUBLICACAO", null, AGORA);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, ?>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, org.mockito.Mockito.atLeastOnce()).update(sql.capture(), parametros.capture());
    Map<String, ?> versao = parametrosPara(sql.getAllValues(), parametros.getAllValues(),
        "INSERT INTO arquivo_publicidade_versao");
    String conteudo = (String) versao.get("conteudo");
    assertThat(conteudo).contains(fotos.get(0).get("id").toString())
        .contains(fotos.get(2).get("id").toString())
        .contains(fotos.get(3).get("id").toString());
    assertThat(conteudo).doesNotContain(video.get("id").toString(),
        fotos.get(1).get("id").toString(), fotos.get(4).get("id").toString(),
        fotos.get(5).get("id").toString());
    assertThat(versao.get("segmentacao")).isEqualTo("{\"estado\":\"NAO_AFERIDA_NA_CAPTURA\"}");
  }

  @Test
  void serializacaoCanonicaIndependeDaOrdemDeMapasAninhados() {
    Map<String, Object> primeira = new LinkedHashMap<>();
    primeira.put("z", Map.of("b", 2, "a", 1));
    primeira.put("a", 0);
    Map<String, Object> segunda = new LinkedHashMap<>();
    segunda.put("a", 0);
    segunda.put("z", Map.of("a", 1, "b", 2));

    assertThat(service.json(primeira)).isEqualTo(service.json(segunda));
    assertThat(service.json(primeira)).isEqualTo("{\"a\":0,\"z\":{\"a\":1,\"b\":2}}");
  }

  private void configurarLeituras(UUID anuncioId, List<Map<String, Object>> ativacoes, boolean legado) {
    when(jdbc.queryForObject(anyString(), any(Map.class), eq(OffsetDateTime.class))).thenReturn(AGORA);
    when(premiumPublico.flagsPorAnuncioIds(any())).thenReturn(
        Map.of(anuncioId, PremiumPublicoFlagsDto.vazio()));
    when(premiumPublico.idsAtivacoesComEfeitoPublico(anuncioId)).thenReturn(
        ativacoes.stream().map(item -> (UUID) item.get("id")).collect(
            java.util.stream.Collectors.toSet()));
    when(jdbc.queryForList(anyString(), any(Map.class))).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("FROM anuncio a JOIN usuario")) {
        return List.of(anuncio(anuncioId));
      }
      if (sql.contains("FROM anuncio_midia am")) {
        return (midiasOverride == null ? List.of(midia(legado)) : midiasOverride).stream()
            .filter(midia -> !midiasOcultasOverride.contains(midia.get("id")))
            .map(midia -> {
              Map<String, Object> linha = new HashMap<>(midia);
              linha.putIfAbsent("anuncio_id", anuncioId);
              return linha;
            }).toList();
      }
      if (sql.contains("FROM ativacao_beneficio ab")) {
        assertThat(sql).contains("LEAST(ab.fim_em, gb.validade_fim_em)");
        return ativacoes;
      }
      if (sql.contains("FROM arquivo_publicidade_veiculacao")) {
        return janelasOverride;
      }
      if (sql.contains("FROM arquivo_publicidade_versao")) {
        return versoesOverride;
      }
      if (sql.contains("FROM arquivo_publicidade_midia")) {
        return copiasOverride.stream().map(copia -> {
          Map<String, Object> linha = new HashMap<>(copia);
          linha.putIfAbsent("origem_anuncio_id", anuncioId);
          return linha;
        }).toList();
      }
      if (sql.contains("FROM anuncio_localizacao")) {
        return List.of();
      }
      throw new AssertionError("consulta inesperada: " + sql);
    });
    when(jdbc.queryForList(anyString(), any(Map.class), eq(String.class))).thenReturn(List.of());
  }

  private Map<String, Object> anuncio(UUID anuncioId) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", anuncioId);
    row.put("usuario_id", UUID.randomUUID());
    row.put("status", "PUBLICADO");
    row.put("status_moderacao", "APROVADO");
    row.put("usuario_status", "ATIVO");
    row.put("tipo_conta", "ANUNCIANTE");
    row.put("desativado_em", contaDesativada ? AGORA.minusHours(1) : null);
    row.put("slug", "perfil-sintetico");
    row.put("titulo", "Titulo sintetico");
    row.put("descricao", "Descricao sintetica");
    row.put("categoria", "MASSAGENS");
    row.put("nome", "Identidade Sintetica");
    row.put("nome_civil", "Identidade Civil Sintetica");
    row.put("cpf_normalizado", null);
    row.put("email_normalizado", "sintetico@example.invalid");
    row.put("atendimento_exclusivamente_virtual", false);
    return row;
  }

  private Map<String, Object> midia(boolean legado) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", UUID.randomUUID());
    row.put("arquivo_midia_id", UUID.randomUUID());
    row.put("tipo", "FOTO");
    row.put("finalidade", "CAPA");
    row.put("ordem", 0);
    row.put("visibilidade_midia", "LIVRE");
    row.put("status_arquivo", "VALIDADO");
    row.put("documento_usuario", false);
    row.put("storage_provider", "R2");
    row.put("bucket", legado ? "legacy-public" : "public");
    row.put("chave_objeto", legado
        ? "anuncios/fotos/original/abcdef.jpg" : "hml/midias-aprovadas/teste.jpg");
    return row;
  }

  private Map<String, Object> ativacao(OffsetDateTime fim) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", UUID.randomUUID());
    row.put("grupo_ativacao_id", UUID.randomUUID());
    row.put("origem", "CREDITO");
    row.put("codigo", "ANUNCIO_TOPO");
    row.put("movimento_id", UUID.randomUUID());
    row.put("fim_em", fim);
    return row;
  }

  private Map<String, Object> janela(UUID janelaId, UUID ativacaoId) {
    return Map.of("id", janelaId, "ativacao_beneficio_id", ativacaoId,
        "classificacao", "ORIGEM_INDETERMINADA", "relacao_material", "DESCONHECIDA",
        "cobertura", "PREVENTIVA", "inicio_em", AGORA.minusHours(1),
        "fim_em", AGORA.plusDays(1));
  }

  private Map<String, ?> parametrosPara(List<String> sqls, List<Map<String, ?>> parametros,
      String marcador) {
    return parametrosPara(sqls, parametros, marcador, null);
  }

  private Map<String, ?> parametrosPara(List<String> sqls, List<Map<String, ?>> parametros,
      String marcador, String valor) {
    for (int i = 0; i < sqls.size(); i++) {
      if (sqls.get(i).contains(marcador)
          && (valor == null || parametros.get(i).toString().contains(valor))) {
        return parametros.get(i);
      }
    }
    throw new AssertionError("escrita nao encontrada: " + marcador);
  }

  private static R2StorageProperties properties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setPublicMediaBucket("public");
    properties.setPrivateMediaBucket("private");
    properties.setPreservedPublicMediaBucket("legacy-public");
    properties.setPreservedPublicMediaPrefix("anuncios/fotos/original/");
    return properties;
  }
}
