package br.com.topsdojob.v3.application.arquivo;

import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_BASE;
import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_COM_EXTRA;

import br.com.topsdojob.v3.application.publico.mapper.SelecaoMidiasPublicas;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Prospective archive. Never reconstructs a past version from the current ad row. */
@Service
public class ArquivoPublicidadeRegistroService {
  private static final Logger LOG = LoggerFactory.getLogger(ArquivoPublicidadeRegistroService.class);
  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
  private static final Set<String> MOTIVOS_RETIRADA_FOTO = Set.of(
      "MIDIA_REMOVIDA_PELO_PROPRIETARIO", "MODERACAO_FOTO_EXCLUIDA");
  private static final String MOTIVO_LACUNA_RETIRADA = "RETIRADA_SEM_COPIA_PRIVADA_ANTERIOR";

  private final NamedParameterJdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;
  private final ArquivoPublicidadeStoryRegistroService stories;
  private final PremiumPublicoMapper premiumPublico;
  private final ArquivoPublicidadeTransicaoTemporalService transicoesTemporais;

  public ArquivoPublicidadeRegistroService(
      NamedParameterJdbcTemplate jdbc,
      EntityManager entityManager,
      ObjectMapper mapper,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties,
      ArquivoPublicidadeStoryRegistroService stories,
      PremiumPublicoMapper premiumPublico,
      ArquivoPublicidadeTransicaoTemporalService transicoesTemporais) {
    this.jdbc = jdbc;
    this.entityManager = entityManager;
    this.mapper = mapper;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
    this.stories = stories;
    this.premiumPublico = premiumPublico;
    this.transicoesTemporais = transicoesTemporais;
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Set<UUID> midiasExibidasAntesDaRetirada(UUID anuncioId) {
    Objects.requireNonNull(anuncioId, "anuncioId");
    return midiasExibiveis(anuncioId).stream()
        .map(midia -> (UUID) midia.get("id"))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public boolean possuiFotoPublicaSelecionada(UUID anuncioId) {
    Objects.requireNonNull(anuncioId, "anuncioId");
    return midiasExibiveis(anuncioId).stream().anyMatch(midia -> "FOTO".equals(midia.get("tipo")));
  }

  /** Hides only newly selected media for which withdrawal cannot reuse verified bytes. */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<UUID> prepararRetiradaSemNovaCopia(
      UUID anuncioId, UUID atorId, String requestId, OffsetDateTime instante,
      Set<UUID> exibidasAntes) {
    Objects.requireNonNull(anuncioId, "anuncioId");
    Objects.requireNonNull(instante, "instante");
    entityManager.flush();
    AnuncioEntity anuncio = entityManager.find(AnuncioEntity.class, anuncioId);
    if (anuncio == null || anuncio.getStatus() != br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio.PUBLICADO
        || anuncio.getStatusModeracao() != br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio.APROVADO
        || anuncio.getRemovidoEm() != null) {
      return List.of();
    }
    Set<UUID> suprimidas = new LinkedHashSet<>();
    Set<UUID> anteriores = exibidasAntes == null ? Set.of() : Set.copyOf(exibidasAntes);
    // Every pass must hide at least one *new* promoted photo. Legacy ads can
    // have more links than the current gallery limit, so a fixed retry cap
    // would turn a legitimate withdrawal into an avoidable rollback.
    while (true) {
      Set<UUID> descobertas = new LinkedHashSet<>(midiasSemCopiaNoAnuncio(anuncioId, instante));
      List<UUID> storySemCopia = stories.midiasSemCopiaParaRetiradaPorAnuncio(anuncioId);
      if (storySemCopia != null) {
        descobertas.addAll(storySemCopia);
      }
      descobertas.removeAll(anteriores);
      if (descobertas.isEmpty()) {
        break;
      }
      boolean mudou = false;
      for (UUID id : descobertas) {
        AnuncioMidiaEntity midia = entityManager.find(AnuncioMidiaEntity.class, id);
        if (midia == null || !anuncioId.equals(midia.getAnuncioId())
            || midia.getStatus() != StatusAnuncioMidia.PUBLICAVEL) {
          continue;
        }
        if (midia.getTipo() != TipoAnuncioMidia.FOTO) {
          throw new IllegalStateException("retirada encontrou midia nao fotografica sem copia: " + id);
        }
        if (!suprimidas.add(id)) {
          throw new IllegalStateException("retirada repetiu midia promovida sem progresso: " + id);
        }
        midia.aplicarDecisao(StatusAnuncioMidia.PENDENTE, midia.getVisibilidadeMidia(), instante);
        mudou = true;
      }
      if (!mudou) {
        throw new IllegalStateException("retirada nao conseguiu ocultar midia sem copia: " + anuncioId);
      }
      entityManager.flush();
    }
    if (!suprimidas.isEmpty()) {
      LOG.warn("Retirada suprimiu midias aprovadas sem copia privada anterior: anuncio={}, quantidade={}",
          anuncioId, suprimidas.size());
      entityManager.persist(AuditoriaEventoEntity.registrarSistema(
          UUID.randomUUID(), atorId == null ? anuncio.getUsuarioId() : atorId,
          "MIDIA_SUPRIMIDA_SEM_COPIA_PRIVADA", "ANUNCIO", anuncioId,
          null, json(Map.of("midiasSuprimidas", List.copyOf(suprimidas),
              "novoStatus", "PENDENTE", "causa", "SEM_COPIA_PRIVADA_ANTERIOR")),
          requestId, instante));
    }
    return List.copyOf(suprimidas);
  }

  private List<UUID> midiasSemCopiaNoAnuncio(UUID anuncioId, OffsetDateTime instante) {
    Set<UUID> ativacoesPublicas = premiumPublico.idsAtivacoesComEfeitoPublico(anuncioId);
    if (ativacoesPublicas.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> ativacoes = ativacoesVigentes(anuncioId, instante).stream()
        .filter(item -> ativacoesPublicas.contains(item.get("id"))).toList();
    if (ativacoes.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> midias = midiasExibiveis(anuncioId);
    if (midias.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> janelas = jdbc.queryForList("""
        SELECT id, ativacao_beneficio_id FROM arquivo_publicidade_veiculacao
        WHERE anuncio_id = :anuncioId AND (fim_em IS NULL OR fim_em > :instante)
        ORDER BY id FOR UPDATE
        """, Map.of("anuncioId", anuncioId, "instante", instante));
    Set<UUID> ausentes = new LinkedHashSet<>();
    for (Map<String, Object> ativacao : ativacoes) {
      UUID ativacaoId = (UUID) ativacao.get("id");
      List<Map<String, Object>> correspondentes = janelas.stream()
          .filter(janela -> ativacaoId.equals(janela.get("ativacao_beneficio_id"))).toList();
      if (correspondentes.isEmpty()) {
        midias.forEach(midia -> ausentes.add((UUID) midia.get("id")));
        continue;
      }
      for (Map<String, Object> janela : correspondentes) {
        Map<String, Object> versao = unico("""
            SELECT id FROM arquivo_publicidade_versao
            WHERE veiculacao_id = :janelaId
              AND (vigente_ate IS NULL OR vigente_ate > :instante)
            ORDER BY numero DESC LIMIT 1 FOR UPDATE
            """, Map.of("janelaId", janela.get("id"), "instante", instante));
        if (versao == null) {
          midias.forEach(midia -> ausentes.add((UUID) midia.get("id")));
          continue;
        }
        Map<ChaveMidia, Map<String, Object>> fontes = fontesDaVersao((UUID) versao.get("id"));
        for (Map<String, Object> midia : midias) {
          UUID vinculoId = (UUID) midia.get("id");
          if (!fonteCompativel(midia, "ORIGINAL", fontes.get(new ChaveMidia(vinculoId, "ORIGINAL")))
              || ("RESTRITA_18".equals(midia.get("visibilidade_midia"))
                  && "DISPONIVEL".equals(midia.get("preview_restrito_status"))
                  && !fonteCompativel(midia, "PREVIEW_RESTRITO",
                      fontes.get(new ChaveMidia(vinculoId, "PREVIEW_RESTRITO"))))) {
            ausentes.add(vinculoId);
          }
        }
      }
    }
    return List.copyOf(ausentes);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void registrarEstado(UUID anuncioId, String motivo, String requestId, OffsetDateTime instante) {
    Objects.requireNonNull(anuncioId, "anuncioId");
    Objects.requireNonNull(instante, "instante");
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("motivo obrigatorio para arquivo publicitario");
    }
    entityManager.flush();
    Map<String, Object> anuncio = unico("""
        SELECT a.id, a.usuario_id, a.slug, a.titulo, a.descricao, a.status,
               a.status_moderacao, a.categoria, a.preco, a.whatsapp_normalizado,
               a.link_conteudo, a.publicado_em, a.ultima_publicacao_em,
               a.atendimento_exclusivamente_virtual, a.removido_em,
               u.status AS usuario_status, u.tipo_conta, u.desativado_em, u.excluido_em,
               u.nome, u.nome_civil, u.cpf_normalizado, u.email_normalizado
        FROM anuncio a JOIN usuario u ON u.id = a.usuario_id
        WHERE a.id = :anuncioId FOR UPDATE OF a
        """, Map.of("anuncioId", anuncioId));
    if (anuncio == null) {
      return;
    }
    // A concurrent command may have waited for the ad lock. Never assign its old
    // request timestamp to a state observed only after a newer archive event.
    instante = jdbc.queryForObject("""
        SELECT GREATEST(
          clock_timestamp(), CAST(:instante AS timestamptz),
          COALESCE((SELECT max(inicio_em) FROM arquivo_publicidade_veiculacao
            WHERE anuncio_id = :anuncioId), CAST(:instante AS timestamptz)),
          COALESCE((SELECT max(v.vigente_desde) FROM arquivo_publicidade_versao v
            JOIN arquivo_publicidade_veiculacao j ON j.id = v.veiculacao_id
            WHERE j.anuncio_id = :anuncioId), CAST(:instante AS timestamptz)))
        """, Map.of("anuncioId", anuncioId, "instante", instante), OffsetDateTime.class);
    // Resolve due plans from evidence frozen before this command reads the new
    // mutable advertisement state. Never infer a past version from today's row.
    transicoesTemporais.processarAnuncioAte(anuncioId, instante);
    boolean publicada = "PUBLICADO".equals(anuncio.get("status"))
        && "APROVADO".equals(anuncio.get("status_moderacao"))
        && anuncio.get("removido_em") == null
        && "ATIVO".equals(anuncio.get("usuario_status"))
        && "ANUNCIANTE".equals(anuncio.get("tipo_conta"))
        && anuncio.get("desativado_em") == null
        && anuncio.get("excluido_em") == null;
    Set<UUID> ativacoesComEfeitoPublico = publicada
        ? premiumPublico.idsAtivacoesComEfeitoPublico(anuncioId) : Set.of();
    List<Map<String, Object>> ativacoes = publicada
        ? ativacoesVigentes(anuncioId, instante).stream()
            .filter(ativacao -> ativacoesComEfeitoPublico.contains(ativacao.get("id")))
            .toList()
        : List.of();
    List<Map<String, Object>> midias = ativacoes.isEmpty() ? List.of() : midiasExibiveis(anuncioId);
    List<Map<String, Object>> abertas = jdbc.queryForList("""
        SELECT id, ativacao_beneficio_id, classificacao, relacao_material, cobertura,
               inicio_em, fim_em
        FROM arquivo_publicidade_veiculacao
        WHERE anuncio_id = :anuncioId AND (fim_em IS NULL OR fim_em > :instante)
        ORDER BY id FOR UPDATE
        """, Map.of("anuncioId", anuncioId, "instante", instante));
    Map<UUID, Map<String, Object>> abertasPorAtivacao = new HashMap<>();
    for (Map<String, Object> aberta : abertas) {
      abertasPorAtivacao.put((UUID) aberta.get("ativacao_beneficio_id"), aberta);
    }

    if (!publicada) {
      for (Map<String, Object> aberta : abertas) {
        encerrar((UUID) aberta.get("id"), instante, motivo);
      }
      transicoesTemporais.reconciliarAnuncioAposCaptura(anuncioId, instante);
      stories.registrarEstadoPorAnuncio(anuncioId, motivo, requestId, instante);
      return;
    }

    for (Map<String, Object> ativacao : ativacoes) {
      UUID ativacaoId = (UUID) ativacao.get("id");
      atualizarJanela(anuncio, midias, ativacao, Classificacao.de(ativacao),
          abertasPorAtivacao.remove(ativacaoId), motivo, requestId, instante);
    }
    for (Map<String, Object> encerrada : abertasPorAtivacao.values()) {
      encerrar((UUID) encerrada.get("id"), instante, motivo);
    }
    transicoesTemporais.reconciliarAnuncioAposCaptura(anuncioId, instante);
    stories.registrarEstadoPorAnuncio(anuncioId, motivo, requestId, instante);
  }

  private void atualizarJanela(
      Map<String, Object> anuncio,
      List<Map<String, Object>> midias,
      Map<String, Object> ativacao,
      Classificacao classe,
      Map<String, Object> aberta,
      String motivo,
      String requestId,
      OffsetDateTime instante) {
    if (aberta == null && MOTIVOS_RETIRADA_FOTO.contains(motivo)) {
      LOG.warn("Retirada sem janela prospectiva anterior: anuncio={}, ativacao={}",
          anuncio.get("id"), ativacao.get("id"));
      return;
    }
    if (aberta != null && (!classe.nome().equals(aberta.get("classificacao"))
        || !classe.relacaoMaterial().equals(aberta.get("relacao_material"))
        || !classe.cobertura().equals(aberta.get("cobertura")))) {
      encerrar((UUID) aberta.get("id"), instante, "CLASSIFICACAO_ALTERADA");
      aberta = null;
    }
    UUID janelaId;
    if (aberta == null) {
      janelaId = UUID.randomUUID();
      Map<String, Object> parametros = new HashMap<>();
      parametros.put("id", janelaId);
      parametros.put("anuncioId", anuncio.get("id"));
      parametros.put("usuarioId", anuncio.get("usuario_id"));
      parametros.put("ativacaoId", ativacao == null ? null : ativacao.get("id"));
      parametros.put("grupoId", ativacao == null ? null : ativacao.get("grupo_ativacao_id"));
      parametros.put("movimentoId", ativacao == null ? null : ativacao.get("movimento_id"));
      parametros.put("classificacao", classe.nome());
      parametros.put("relacao", classe.relacaoMaterial());
      parametros.put("cobertura", classe.cobertura());
      parametros.put("instante", instante);
      parametros.put("limite", ativacao == null ? null : ativacao.get("fim_em"));
      parametros.put("encerramento", ativacao == null ? null : "LIMITE_AUTOMATICO_ATIVACAO");
      jdbc.update("""
          INSERT INTO arquivo_publicidade_veiculacao (
            id, anuncio_id, contratante_usuario_id, ativacao_beneficio_id,
            grupo_ativacao_id, movimento_credito_id, pagamento_id, classificacao,
            relacao_material, cobertura, inicio_em, fim_em, retencao_ate,
            encerramento_motivo, criado_em, atualizado_em)
          VALUES (:id, :anuncioId, :usuarioId, :ativacaoId,
            :grupoId, :movimentoId, NULL, :classificacao,
            :relacao, :cobertura, :instante, :limite,
            CASE WHEN :cobertura IN ('ABRANGIDA', 'PREVENTIVA')
                AND CAST(:limite AS timestamptz) IS NOT NULL
              THEN CAST(:limite AS timestamptz) + INTERVAL '1 year' ELSE NULL END,
            :encerramento, :instante, :instante)
          """, parametros);
    } else {
      janelaId = (UUID) aberta.get("id");
    }
    if (!criarVersaoSeMudou(janelaId, anuncio, midias, ativacao, classe,
        motivo, requestId, instante)) {
      LOG.warn("Retirada sem copia privada anterior suficiente: anuncio={}, janela={}",
          anuncio.get("id"), janelaId);
      encerrar(janelaId, instante, MOTIVO_LACUNA_RETIRADA);
    }
  }

  private boolean criarVersaoSeMudou(
      UUID janelaId,
      Map<String, Object> anuncio,
      List<Map<String, Object>> midias,
      Map<String, Object> ativacao,
      Classificacao classe,
      String motivo,
      String requestId,
      OffsetDateTime instante) {
    boolean completa = !"NAO_ABRANGIDA".equals(classe.cobertura());
    Map<String, Object> conteudo = new LinkedHashMap<>();
    conteudo.put("estado", completa ? "CAPTURADO_NA_EXIBICAO" : "METADADOS_OPERACIONAIS");
    conteudo.put("anuncioId", anuncio.get("id"));
    if (completa) {
      for (String campo : List.of("slug", "titulo", "descricao", "categoria", "preco",
          "whatsapp_normalizado", "link_conteudo", "atendimento_exclusivamente_virtual")) {
        conteudo.put(campo, anuncio.get(campo));
      }
      conteudo.put("urlPublicaNaCaptura", "/anuncios/" + anuncio.get("slug"));
      conteudo.put("produtoServicoMarca", "NAO_DISPONIVEL_EM_CAMPO_ESTRUTURADO");
      UUID anuncioId = (UUID) anuncio.get("id");
      conteudo.put("localizacao", unico("""
          SELECT estado_id, cidade_id, bairro_id, endereco_resumido
          FROM anuncio_localizacao WHERE anuncio_id = :anuncioId
          """, Map.of("anuncioId", anuncioId)));
      conteudo.put("servicos", valores("SELECT servico FROM anuncio_servicos WHERE anuncio_id = :anuncioId ORDER BY servico", anuncioId));
      conteudo.put("locaisAtendimento", valores("SELECT local_atendimento FROM anuncio_local_atendimento WHERE anuncio_id = :anuncioId ORDER BY local_atendimento", anuncioId));
      conteudo.put("midias", midias.stream().map(m -> Map.of(
          "anuncioMidiaId", m.get("id"),
          "arquivoMidiaId", m.get("arquivo_midia_id"),
          "tipo", m.get("tipo"),
          "finalidade", m.get("finalidade"),
          "ordem", m.get("ordem"),
          "visibilidade", m.get("visibilidade_midia"))).toList());
    }
    Map<String, Object> contratante = new LinkedHashMap<>();
    if (completa) {
      contratante.put("usuarioId", anuncio.get("usuario_id"));
      contratante.put("nome", anuncio.get("nome"));
      contratante.put("nomeCivil", anuncio.get("nome_civil"));
      contratante.put("cpf", anuncio.get("cpf_normalizado"));
      contratante.put("email", anuncio.get("email_normalizado"));
      contratante.put("terceiroBeneficiario", "NAO_REGISTRADO");
    }
    Map<String, Object> comercial = new LinkedHashMap<>();
    comercial.put("classificacao", classe.nome());
    comercial.put("relacaoMaterial", classe.relacaoMaterial());
    comercial.put("cobertura", classe.cobertura());
    if (ativacao != null) {
      comercial.put("ativacaoBeneficioId", ativacao.get("id"));
      comercial.put("grupoAtivacaoId", ativacao.get("grupo_ativacao_id"));
      comercial.put("beneficioCodigo", ativacao.get("codigo"));
      comercial.put("origem", ativacao.get("origem"));
      comercial.put("movimentoCreditoId", ativacao.get("movimento_id"));
      comercial.put("pagamentoId", null);
      comercial.put("vinculoPagamento", "SALDO_FUNGIVEL_SEM_ALOCACAO_EXATA");
    }
    Map<String, Object> segmentacao = Map.of("estado", "NAO_AFERIDA_NA_CAPTURA");
    Map<String, Object> alcance = Map.of("estado", "NAO_MENSURADO", "destinatariosUnicos", "DESCONHECIDO");
    String conteudoJson = json(conteudo);
    String contratanteJson = json(contratante);
    String comercialJson = json(comercial);
    String segmentacaoJson = json(segmentacao);
    String alcanceJson = json(alcance);
    String hash = sha256((conteudoJson + contratanteJson + comercialJson
        + segmentacaoJson + alcanceJson).getBytes(StandardCharsets.UTF_8));
    OffsetDateTime limite = ativacao == null ? null : instanteJdbc(ativacao.get("fim_em"));
    Map<String, Object> anterior = unico("""
        SELECT id, numero, conteudo_sha256 FROM arquivo_publicidade_versao
        WHERE veiculacao_id = :janelaId
          AND (vigente_ate IS NULL OR vigente_ate > :instante)
        ORDER BY numero DESC LIMIT 1 FOR UPDATE
        """, Map.of("janelaId", janelaId, "instante", instante));
    boolean retiradaFoto = MOTIVOS_RETIRADA_FOTO.contains(motivo);
    if (retiradaFoto && anterior == null) {
      return false;
    }
    if (anterior != null && hash.equals(anterior.get("conteudo_sha256"))) {
      return true;
    }
    UUID versaoId = UUID.randomUUID();
    Optional<List<MidiaReferencia>> referenciasAnteriores = completa && retiradaFoto
        ? referenciasAnteriores(anterior, midias) : Optional.of(List.of());
    if (referenciasAnteriores.isEmpty()) {
      return false;
    }
    List<MidiaReferencia> referencias = new ArrayList<>(referenciasAnteriores.get());
    List<MidiaCopiada> copias = completa && !retiradaFoto
        ? copiarMidias(versaoId, janelaId, (UUID) anuncio.get("usuario_id"), midias,
            anterior == null ? Map.of() : fontesDaVersao((UUID) anterior.get("id")), referencias)
        : List.of();
    if (anterior != null) {
      jdbc.update("""
          UPDATE arquivo_publicidade_versao SET vigente_ate = :instante WHERE id = :id
          """, Map.of("instante", instante, "id", anterior.get("id")));
    }
    Map<String, Object> parametros = new HashMap<>();
    parametros.put("id", versaoId);
    parametros.put("janelaId", janelaId);
    parametros.put("numero", anterior == null ? 1 : ((Number) anterior.get("numero")).intValue() + 1);
    parametros.put("instante", instante);
    parametros.put("limite", limite);
    parametros.put("motivo", motivo);
    parametros.put("requestId", requestId);
    parametros.put("conteudo", conteudoJson);
    parametros.put("contratante", contratanteJson);
    parametros.put("comercial", comercialJson);
    parametros.put("segmentacao", segmentacaoJson);
    parametros.put("alcance", alcanceJson);
    parametros.put("hash", hash);
    jdbc.update("""
        INSERT INTO arquivo_publicidade_versao (
          id, veiculacao_id, numero, vigente_desde, vigente_ate,
          capturado_em, motivo, request_id,
          conteudo_json, contratante_json, comercial_json, segmentacao_json,
          alcance_json, conteudo_sha256)
        VALUES (:id, :janelaId, :numero, :instante, :limite,
          :instante, :motivo, :requestId,
          CAST(:conteudo AS jsonb), CAST(:contratante AS jsonb), CAST(:comercial AS jsonb),
          CAST(:segmentacao AS jsonb), CAST(:alcance AS jsonb), :hash)
        """, parametros);
    for (MidiaCopiada midia : copias) {
      Map<String, Object> midiaParametros = new HashMap<>();
      midiaParametros.put("id", midia.id());
      midiaParametros.put("versaoId", versaoId);
      midiaParametros.put("anuncioMidiaId", midia.anuncioMidiaId());
      midiaParametros.put("arquivoMidiaId", midia.arquivoMidiaId());
      midiaParametros.put("variante", midia.variante());
      midiaParametros.put("bucket", storageProperties.getPrivateMediaBucket());
      midiaParametros.put("chave", midia.chave());
      midiaParametros.put("sha256", midia.sha256());
      midiaParametros.put("mimeType", midia.mimeType());
      midiaParametros.put("tamanhoBytes", midia.tamanhoBytes());
      midiaParametros.put("ordem", midia.ordem());
      jdbc.update("""
          INSERT INTO arquivo_publicidade_midia (
            id, versao_id, anuncio_midia_id, arquivo_midia_id, variante,
            storage_provider, bucket, chave_privada, sha256, mime_type,
            tamanho_bytes, ordem)
          VALUES (:id, :versaoId, :anuncioMidiaId, :arquivoMidiaId, :variante,
            'R2', :bucket, :chave, :sha256, :mimeType, :tamanhoBytes, :ordem)
          """, midiaParametros);
    }
    for (MidiaReferencia referencia : referencias) {
      jdbc.update("""
          INSERT INTO arquivo_publicidade_midia_referencia (
            id, versao_id, origem_midia_id, anuncio_midia_id, variante, ordem)
          VALUES (:id, :versaoId, :origemMidiaId, :anuncioMidiaId, :variante, :ordem)
          """, Map.of(
              "id", UUID.randomUUID(),
              "versaoId", versaoId,
              "origemMidiaId", referencia.origemMidiaId(),
              "anuncioMidiaId", referencia.anuncioMidiaId(),
              "variante", referencia.variante(),
              "ordem", referencia.ordem()));
    }
    return true;
  }

  private Optional<List<MidiaReferencia>> referenciasAnteriores(
      Map<String, Object> anterior, List<Map<String, Object>> midias) {
    if (midias.isEmpty()) {
      return Optional.of(List.of());
    }
    if (anterior == null) {
      return Optional.empty();
    }
    Map<ChaveMidia, Map<String, Object>> fontes = fontesDaVersao((UUID) anterior.get("id"));
    List<MidiaReferencia> referencias = new ArrayList<>();
    for (Map<String, Object> midia : midias) {
      MidiaReferencia original = referenciaPara(midia, "ORIGINAL", fontes);
      if (original == null) {
        return Optional.empty();
      }
      referencias.add(original);
      if ("RESTRITA_18".equals(midia.get("visibilidade_midia"))
          && "DISPONIVEL".equals(midia.get("preview_restrito_status"))) {
        MidiaReferencia preview = referenciaPara(midia, "PREVIEW_RESTRITO", fontes);
        if (preview == null) {
          return Optional.empty();
        }
        referencias.add(preview);
      }
    }
    return Optional.of(List.copyOf(referencias));
  }

  private MidiaReferencia referenciaPara(
      Map<String, Object> midia, String variante,
      Map<ChaveMidia, Map<String, Object>> fontes) {
    UUID vinculoId = (UUID) midia.get("id");
    Map<String, Object> fonte = fontes.get(new ChaveMidia(vinculoId, variante));
    if (!fonteCompativel(midia, variante, fonte)) {
      return null;
    }
    return new MidiaReferencia((UUID) fonte.get("origem_midia_id"), vinculoId,
        variante, ((Number) midia.get("ordem")).intValue());
  }

  private boolean fonteCompativel(
      Map<String, Object> midia, String variante, Map<String, Object> fonte) {
    if (fonte == null || midia.get("anuncio_id") == null
        || !Objects.equals(midia.get("anuncio_id"), fonte.get("origem_anuncio_id"))
        || !Objects.equals(midia.get("arquivo_midia_id"), fonte.get("arquivo_midia_id"))) {
      return false;
    }
    if ("ORIGINAL".equals(variante)) {
      return (midia.get("sha256") == null || Objects.equals(midia.get("sha256"), fonte.get("sha256")))
          && (midia.get("mime_type") == null
              || Objects.equals(midia.get("mime_type"), fonte.get("mime_type")))
          && !fontePosteriorAoProcessamento(midia.get("processado_em"), fonte);
    }
    return !fontePosteriorAoProcessamento(midia.get("preview_restrito_confirmado_em"), fonte);
  }

  private boolean fontePosteriorAoProcessamento(Object processamento, Map<String, Object> fonte) {
    if (processamento == null) {
      return false;
    }
    Object capturado = fonte.get("origem_capturada_em");
    return capturado == null || instanteJdbc(processamento).isAfter(instanteJdbc(capturado));
  }

  private Map<ChaveMidia, Map<String, Object>> fontesDaVersao(UUID versaoId) {
    List<Map<String, Object>> linhas = jdbc.queryForList("""
        SELECT m.id AS origem_midia_id, m.anuncio_midia_id, m.arquivo_midia_id,
               m.variante, m.sha256, m.mime_type, m.tamanho_bytes,
               m.storage_provider, m.bucket, m.chave_privada,
               v.id AS origem_versao_id, v.capturado_em AS origem_capturada_em,
               j.anuncio_id AS origem_anuncio_id, j.id AS origem_janela_id,
               j.contratante_usuario_id AS origem_usuario_id
        FROM arquivo_publicidade_midia m
        JOIN arquivo_publicidade_versao v ON v.id = m.versao_id
        JOIN arquivo_publicidade_veiculacao j ON j.id = v.veiculacao_id
        WHERE m.versao_id = :versaoId
        UNION ALL
        SELECT m.id AS origem_midia_id, r.anuncio_midia_id, m.arquivo_midia_id,
               r.variante, m.sha256, m.mime_type, m.tamanho_bytes,
               m.storage_provider, m.bucket, m.chave_privada,
               v.id AS origem_versao_id, v.capturado_em AS origem_capturada_em,
               j.anuncio_id AS origem_anuncio_id, j.id AS origem_janela_id,
               j.contratante_usuario_id AS origem_usuario_id
        FROM arquivo_publicidade_midia_referencia r
        JOIN arquivo_publicidade_midia m ON m.id = r.origem_midia_id
          AND m.anuncio_midia_id = r.anuncio_midia_id AND m.variante = r.variante
        JOIN arquivo_publicidade_versao v ON v.id = m.versao_id
        JOIN arquivo_publicidade_veiculacao j ON j.id = v.veiculacao_id
        WHERE r.versao_id = :versaoId
        """, Map.of("versaoId", versaoId));
    Map<ChaveMidia, Map<String, Object>> fontes = new HashMap<>();
    Set<ChaveMidia> duplicadas = new LinkedHashSet<>();
    for (Map<String, Object> linha : linhas) {
      ChaveMidia chave = new ChaveMidia(
          (UUID) linha.get("anuncio_midia_id"), (String) linha.get("variante"));
      if (duplicadas.contains(chave)) {
        continue;
      }
      if (fontes.putIfAbsent(chave, linha) != null) {
        LOG.warn("Copia privada duplicada na versao: versao={}, midia={}, variante={}",
            versaoId, chave.anuncioMidiaId(), chave.variante());
        fontes.remove(chave);
        duplicadas.add(chave);
      }
    }
    return fontes;
  }

  private List<MidiaCopiada> copiarMidias(
      UUID versaoId, UUID janelaId, UUID usuarioId, List<Map<String, Object>> midias,
      Map<ChaveMidia, Map<String, Object>> fontes, List<MidiaReferencia> referencias) {
    if (midias.isEmpty()) {
      return List.of();
    }
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()
        || storageProperties.getPrivateMediaBucket() == null
        || storageProperties.getPrivateMediaPrefix() == null) {
      throw new IllegalStateException("storage privado indisponivel para arquivo publicitario");
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("captura de midia exige transacao ativa");
    }
    List<MidiaCopiada> copias = new java.util.ArrayList<>();
    for (Map<String, Object> midia : midias) {
      String bucket = String.valueOf(midia.get("bucket"));
      String chave = String.valueOf(midia.get("chave_objeto"));
      String provider = String.valueOf(midia.get("storage_provider"));
      String visibilidade = String.valueOf(midia.get("visibilidade_midia"));
      if (!"R2".equals(provider)) {
        throw new IllegalStateException("midia exibida fora do storage verificavel: " + midia.get("id"));
      }
      StorageArea area;
      if ("LIVRE".equals(visibilidade)
          && bucket.equals(storageProperties.getPublicMediaBucket())
          && chave.startsWith(storageProperties.getPublicMediaPrefix())) {
        area = StorageArea.PUBLIC_MEDIA;
      } else if ("LIVRE".equals(visibilidade)
          && bucket.equals(storageProperties.getPreservedPublicMediaBucket())
          && storageProperties.getPreservedPublicMediaPrefix() != null
          && chave.startsWith(storageProperties.getPreservedPublicMediaPrefix())) {
        area = StorageArea.PRESERVED_PUBLIC_MEDIA;
      } else if ("RESTRITA_18".equals(visibilidade)
          && bucket.equals(storageProperties.getPrivateMediaBucket())
          && chave.startsWith(storageProperties.getPrivateMediaPrefix())) {
        area = StorageArea.PRIVATE_MEDIA;
      } else {
        throw new IllegalStateException("origem de midia exibida nao verificavel: " + midia.get("id"));
      }
      MidiaCopiada original = copiarUma(storage, versaoId, janelaId, usuarioId, midia,
          "ORIGINAL", area, chave, fontes, referencias);
      if (original != null) {
        copias.add(original);
      }
      if ("RESTRITA_18".equals(visibilidade)
          && "DISPONIVEL".equals(midia.get("preview_restrito_status"))) {
        String preview = (String) midia.get("preview_restrito_chave");
        if (preview == null || !preview.startsWith(storageProperties.getPublicMediaPrefix())) {
          throw new IllegalStateException("preview restrito exibido sem origem integra: " + midia.get("id"));
        }
        MidiaCopiada copiaPreview = copiarUma(storage, versaoId, janelaId, usuarioId, midia,
            "PREVIEW_RESTRITO", StorageArea.PUBLIC_MEDIA, preview, fontes, referencias);
        if (copiaPreview != null) {
          copias.add(copiaPreview);
        }
      }
    }
    return List.copyOf(copias);
  }

  private MidiaCopiada copiarUma(
      ObjectStorage storage,
      UUID versaoId,
      UUID janelaId,
      UUID usuarioId,
      Map<String, Object> midia,
      String variante,
      StorageArea origem,
      String chaveOrigem,
      Map<ChaveMidia, Map<String, Object>> fontes,
      List<MidiaReferencia> referencias) {
    StoredObject original = storage.get(origem, chaveOrigem);
    if (original == null || original.content() == null || original.content().length == 0
        || original.contentType() == null) {
      throw new IllegalStateException("bytes da midia exibida indisponiveis: " + midia.get("id"));
    }
    if ("FOTO".equals(midia.get("tipo")) && !IMAGE_TYPES.contains(original.contentType())) {
      throw new IllegalStateException("tipo de foto exibida divergente: " + midia.get("id"));
    }
    String hash = sha256(original.content());
    Object esperado = midia.get("sha256");
    if ("ORIGINAL".equals(variante) && esperado != null && !hash.equals(esperado)) {
      throw new IllegalStateException("hash da fonte de midia divergente: " + midia.get("id"));
    }
    Map<String, Object> fonte = fontes.get(new ChaveMidia((UUID) midia.get("id"), variante));
    if (copiaAnteriorIntegra(storage, janelaId, usuarioId, midia, variante, fonte, original, hash)) {
      referencias.add(new MidiaReferencia((UUID) fonte.get("origem_midia_id"),
          (UUID) midia.get("id"), variante, ((Number) midia.get("ordem")).intValue()));
      return null;
    }
    String objetoDestino = storageProperties.getPrivateMediaPrefix()
        + "arquivo-publicidade/" + versaoId + "/" + midia.get("id")
        + "/" + variante.toLowerCase(java.util.Locale.ROOT);
    ObjectWriteResult resultado = storage.putIfAbsent(
        StorageArea.PRIVATE_MEDIA, objetoDestino, original.content(), original.contentType());
    if (resultado == ObjectWriteResult.CREATED) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status == STATUS_ROLLED_BACK) {
            try {
              storage.delete(StorageArea.PRIVATE_MEDIA, objetoDestino);
            } catch (RuntimeException exception) {
              LOG.error("Falha ao limpar copia privada apos rollback: versao={}, midia={}, variante={}",
                  versaoId, midia.get("id"), variante, exception);
            }
          }
        }
      });
    }
    StoredObject confirmada = storage.get(StorageArea.PRIVATE_MEDIA, objetoDestino);
    if (confirmada == null || confirmada.content() == null
        || !hash.equals(sha256(confirmada.content()))
        || !Objects.equals(original.contentType(), confirmada.contentType())) {
      throw new IllegalStateException("copia privada da midia nao confirmou integridade: " + midia.get("id"));
    }
    return new MidiaCopiada(UUID.randomUUID(), (UUID) midia.get("id"),
        (UUID) midia.get("arquivo_midia_id"), variante, objetoDestino,
        hash, original.contentType(), original.content().length,
        ((Number) midia.get("ordem")).intValue());
  }

  /** Reuse is local to the preceding version of this window, never a hash lookup. */
  private boolean copiaAnteriorIntegra(
      ObjectStorage storage, UUID janelaId, UUID usuarioId, Map<String, Object> midia,
      String variante, Map<String, Object> fonte, StoredObject original, String hash) {
    if (fonte == null || !(fonte.get("origem_midia_id") instanceof UUID)
        || !janelaId.equals(fonte.get("origem_janela_id"))
        || !usuarioId.equals(fonte.get("origem_usuario_id"))
        || !Objects.equals(midia.get("anuncio_id"), fonte.get("origem_anuncio_id"))
        || !Objects.equals(midia.get("id"), fonte.get("anuncio_midia_id"))
        || !Objects.equals(midia.get("arquivo_midia_id"), fonte.get("arquivo_midia_id"))
        || !variante.equals(fonte.get("variante"))
        || !"R2".equals(fonte.get("storage_provider"))
        || !storageProperties.getPrivateMediaBucket().equals(fonte.get("bucket"))
        || !(fonte.get("origem_versao_id") instanceof UUID origemVersaoId)
        || !(fonte.get("chave_privada") instanceof String chave)
        || !chave.equals(storageProperties.getPrivateMediaPrefix() + "arquivo-publicidade/"
            + origemVersaoId + "/" + midia.get("id") + "/"
            + variante.toLowerCase(java.util.Locale.ROOT))
        || !hash.equals(fonte.get("sha256"))
        || !original.contentType().equals(fonte.get("mime_type"))
        || !(fonte.get("tamanho_bytes") instanceof Number tamanho)
        || tamanho.longValue() != original.content().length) {
      return false;
    }
    // A new version confirms both current source bytes and retained private bytes.
    // Withdrawal deliberately follows the separate path without storage I/O.
    StoredObject retida;
    try {
      retida = storage.get(StorageArea.PRIVATE_MEDIA, chave);
    } catch (RuntimeException exception) {
      return false;
    }
    return retida != null && retida.content() != null
        && retida.content().length == original.content().length
        && hash.equals(sha256(retida.content()))
        && original.contentType().equals(retida.contentType());
  }

  private void encerrar(UUID janelaId, OffsetDateTime instante, String motivo) {
    jdbc.update("""
        UPDATE arquivo_publicidade_versao
        SET vigente_ate = :instante
        WHERE veiculacao_id = :janelaId AND (vigente_ate IS NULL OR vigente_ate > :instante)
        """, Map.of("instante", instante, "janelaId", janelaId));
    jdbc.update("""
        UPDATE arquivo_publicidade_veiculacao
        SET fim_em = :instante,
            retencao_ate = CASE WHEN cobertura IN ('ABRANGIDA', 'PREVENTIVA')
              THEN :instante + INTERVAL '1 year' ELSE NULL END,
            encerramento_motivo = :motivo,
            atualizado_em = :instante
        WHERE id = :janelaId AND (fim_em IS NULL OR fim_em > :instante)
        """, Map.of("instante", instante, "motivo", motivo, "janelaId", janelaId));
  }

  private List<Map<String, Object>> ativacoesVigentes(UUID anuncioId, OffsetDateTime instante) {
    return jdbc.queryForList("""
        SELECT ab.id, ab.grupo_ativacao_id, ab.origem, ab.inicio_em,
               CASE WHEN gb.id IS NULL THEN ab.fim_em
                 ELSE LEAST(ab.fim_em, gb.validade_fim_em) END AS fim_em,
               bp.codigo, mc.id AS movimento_id
        FROM ativacao_beneficio ab
        JOIN beneficio_premium bp ON bp.id = ab.beneficio_id AND bp.escopo = 'ANUNCIO'
        LEFT JOIN grupo_ativacao_beneficio gb ON gb.id = ab.grupo_ativacao_id
        LEFT JOIN LATERAL (
          SELECT id FROM movimento_credito
          WHERE referencia_tipo = 'ATIVACAO_BENEFICIO' AND referencia_id = ab.id
            AND tipo = 'SAIDA' AND direcao = 'DEBITO'
          ORDER BY criado_em, id LIMIT 1
        ) mc ON true
        WHERE ab.anuncio_id = :anuncioId AND ab.status = 'ATIVA'
          AND ab.revogada_em IS NULL AND ab.inicio_em <= :instante AND ab.fim_em > :instante
          AND (gb.id IS NULL OR (gb.status = 'ATIVO' AND gb.validade_inicio_em <= :instante
            AND gb.validade_fim_em > :instante))
        ORDER BY ab.id
        """, Map.of("anuncioId", anuncioId, "instante", instante));
  }

  private List<Map<String, Object>> midiasExibiveis(UUID anuncioId) {
    // Match the public gallery's position policy before filtering invalid files:
    // an invalid photo still consumes one of the available public positions.
    List<Map<String, Object>> candidatas = jdbc.queryForList("""
        SELECT am.id, am.anuncio_id, am.arquivo_midia_id, am.tipo, am.finalidade, am.ordem,
               am.visibilidade_midia, ar.storage_provider, ar.bucket, ar.chave_objeto,
                 ar.mime_type, ar.sha256, ar.status_arquivo, ar.processado_em,
                ar.preview_restrito_chave, ar.preview_restrito_status,
                 ar.preview_restrito_confirmado_em,
                EXISTS (SELECT 1 FROM documento_usuario d
                  WHERE d.arquivo_midia_id = ar.id) AS documento_usuario
        FROM anuncio_midia am LEFT JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
        WHERE am.anuncio_id = :anuncioId AND am.status = 'PUBLICAVEL'
          AND am.tipo IN ('FOTO', 'VIDEO')
          AND am.visibilidade_midia IS NOT NULL
        ORDER BY am.ordem, am.id
        """, Map.of("anuncioId", anuncioId));
    PremiumPublicoFlagsDto flags = premiumPublico.flagsPorAnuncioIds(List.of(anuncioId))
        .getOrDefault(anuncioId, PremiumPublicoFlagsDto.vazio());
    int maxFotos = flags.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE;
    Map<UUID, Map<String, Object>> porId = new HashMap<>();
    List<MidiaVinculoLeitura> vinculos = candidatas.stream().map(midia -> {
      UUID id = (UUID) midia.get("id");
      porId.put(id, midia);
      return new MidiaVinculoLeitura(id, anuncioId, (UUID) midia.get("arquivo_midia_id"),
          TipoAnuncioMidia.valueOf((String) midia.get("tipo")),
          FinalidadeAnuncioMidia.valueOf((String) midia.get("finalidade")),
          (Integer) midia.get("ordem"), StatusAnuncioMidia.PUBLICAVEL,
          VisibilidadeMidia.valueOf((String) midia.get("visibilidade_midia")), null);
    }).toList();
    return SelecaoMidiasPublicas.selecionar(vinculos, maxFotos, flags.videoAtivo()).stream()
        .map(vinculo -> porId.get(vinculo.id()))
        .filter(midia -> "VALIDADO".equals(midia.get("status_arquivo")))
        .filter(midia -> !Boolean.TRUE.equals(midia.get("documento_usuario")))
        .toList();
  }

  private List<String> valores(String sql, UUID anuncioId) {
    return jdbc.queryForList(sql, Map.of("anuncioId", anuncioId), String.class);
  }

  private Map<String, Object> unico(String sql, Map<String, ?> parametros) {
    List<Map<String, Object>> linhas = jdbc.queryForList(sql, parametros);
    return linhas.isEmpty() ? null : linhas.get(0);
  }

  String json(Object valor) {
    try {
      return mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar arquivo publicitario", exception);
    }
  }

  private String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private OffsetDateTime instanteJdbc(Object valor) {
    if (valor instanceof OffsetDateTime instante) {
      return instante;
    }
    if (valor instanceof java.sql.Timestamp instante) {
      return instante.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    throw new IllegalStateException("instante de ativacao invalido");
  }

  private record Classificacao(String nome, String relacaoMaterial, String cobertura) {
    static Classificacao de(Map<String, Object> ativacao) {
      String origem = String.valueOf(ativacao.get("origem"));
      if ("CREDITO".equals(origem) || "COMPRA".equals(origem)) {
        // A debit against a fungible balance does not identify the payment that funded it.
        return new Classificacao("ORIGEM_INDETERMINADA", "DESCONHECIDA", "PREVENTIVA");
      }
      if ("CORTESIA".equals(origem) || "CAMPANHA".equals(origem)) {
        return new Classificacao("PROMOCIONAL", "DESCONHECIDA", "PREVENTIVA");
      }
      if ("ADMIN".equals(origem)) {
        return new Classificacao("ADMINISTRATIVA", "DESCONHECIDA", "PREVENTIVA");
      }
      return new Classificacao("ORIGEM_INDETERMINADA", "DESCONHECIDA", "PREVENTIVA");
    }
  }

  private record MidiaCopiada(UUID id, UUID anuncioMidiaId, UUID arquivoMidiaId,
      String variante, String chave, String sha256, String mimeType, long tamanhoBytes, int ordem) {
  }

  private record MidiaReferencia(UUID origemMidiaId, UUID anuncioMidiaId,
      String variante, int ordem) {
  }

  private record ChaveMidia(UUID anuncioMidiaId, String variante) {
  }
}
