package br.com.topsdojob.v3.application.arquivo;

import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_BASE;
import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_COM_EXTRA;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.SelecaoMidiasPublicas;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Prospective private evidence of a Story that was actually eligible for public display. */
@Service
public class ArquivoPublicidadeStoryRegistroService {
  private static final Logger LOG = LoggerFactory.getLogger(ArquivoPublicidadeStoryRegistroService.class);

  private final NamedParameterJdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;
  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final ArquivoMidiaRepository arquivoMidiaRepository;
  private final MidiaPublicaMapper midiaPublicaMapper;
  private final PremiumPublicoMapper premiumPublicoMapper;
  private final ArquivoPublicidadeTransicaoTemporalService transicoesTemporais;

  public ArquivoPublicidadeStoryRegistroService(
      NamedParameterJdbcTemplate jdbc,
      EntityManager entityManager,
      ObjectMapper mapper,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties,
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      MidiaPublicaMapper midiaPublicaMapper,
      PremiumPublicoMapper premiumPublicoMapper,
      ArquivoPublicidadeTransicaoTemporalService transicoesTemporais) {
    this.jdbc = jdbc;
    this.entityManager = entityManager;
    this.mapper = mapper;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.arquivoMidiaRepository = arquivoMidiaRepository;
    this.midiaPublicaMapper = midiaPublicaMapper;
    this.premiumPublicoMapper = premiumPublicoMapper;
    this.transicoesTemporais = transicoesTemporais;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void registrarEstado(UUID storyId, String motivo, String requestId, OffsetDateTime instante) {
    validarEntrada(storyId, motivo, instante);
    entityManager.flush();
    registrarEstadoInterno(storyId, motivo, requestId, instante);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void registrarEstadoPorAnuncio(
      UUID anuncioId, String motivo, String requestId, OffsetDateTime instante) {
    validarEntrada(anuncioId, motivo, instante);
    entityManager.flush();
    List<UUID> ids = jdbc.queryForList("""
        SELECT s.id FROM story_anuncio s
        WHERE s.anuncio_id = :id AND s.modo_conteudo = 'ANUNCIO'
          AND (s.status = 'PUBLICADO' OR EXISTS (
            SELECT 1 FROM arquivo_publicidade_story_veiculacao j
            WHERE j.story_id = s.id AND j.fim_em > :instante))
        ORDER BY s.id
        """, Map.of("id", anuncioId, "instante", instante), UUID.class);
    for (UUID id : ids) {
      registrarEstadoInterno(id, motivo, requestId, instante);
    }
  }

  /** Selected photos that an active ANUNCIO Story cannot preserve without reading the source storage. */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<UUID> midiasSemCopiaParaRetiradaPorAnuncio(UUID anuncioId) {
    Objects.requireNonNull(anuncioId, "anuncioId");
    entityManager.flush();
    List<Map<String, Object>> stories = jdbc.queryForList("""
        SELECT s.id, j.id AS veiculacao_id
          FROM story_anuncio s
          JOIN anuncio a ON a.id = s.anuncio_id
          JOIN usuario u ON u.id = s.criado_por
          LEFT JOIN LATERAL (
            SELECT id FROM arquivo_publicidade_story_veiculacao
             WHERE story_id = s.id ORDER BY inicio_em DESC, id DESC LIMIT 1
          ) j ON true
         WHERE s.anuncio_id = :id AND s.modo_conteudo = 'ANUNCIO'
           AND s.status = 'PUBLICADO' AND s.encerrado_em IS NULL
           AND s.inicio_em <= clock_timestamp() AND s.fim_em > clock_timestamp()
           AND a.status = 'PUBLICADO' AND a.status_moderacao = 'APROVADO'
           AND a.removido_em IS NULL
           AND u.status = 'ATIVO' AND u.tipo_conta = 'ANUNCIANTE'
           AND u.desativado_em IS NULL AND u.excluido_em IS NULL
        ORDER BY s.id
        """, Map.of("id", anuncioId));
    if (stories.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> selecionadas = midiasExibidasNoAnuncio(anuncioId, true);
    List<UUID> descobertas = new ArrayList<>();
    for (Map<String, Object> story : stories) {
      UUID janelaId = (UUID) story.get("veiculacao_id");
      Map<String, Object> versao = janelaId == null ? null
          : versaoVigente(janelaId, OffsetDateTime.now(ZoneOffset.UTC));
      if (versao == null) {
        LOG.warn("Story ativo sem versao arquivada anterior na preflight de retirada; lacuna sem historico fabricado: story={}",
            story.get("id"));
        continue; // A legacy Story gap must not demote a valid advertisement's photos.
      }
      Map<ChaveMidia, Map<String, Object>> origens = origensDaVersao((UUID) versao.get("id"));
      for (Map<String, Object> midia : selecionadas) {
        if ("FOTO".equals(midia.get("tipo")) && referenciasAusentes(midia, origens)) {
          descobertas.add((UUID) midia.get("anuncio_midia_id"));
        }
      }
    }
    return descobertas.stream().distinct().toList();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void registrarEstadoPorUsuario(
      UUID usuarioId, String motivo, String requestId, OffsetDateTime instante) {
    validarEntrada(usuarioId, motivo, instante);
    entityManager.flush();
    List<UUID> ids = jdbc.queryForList("""
        SELECT s.id FROM story_anuncio s
        WHERE s.criado_por = :id AND s.modo_conteudo IN ('ANUNCIO', 'MIDIA_UPLOAD')
          AND (s.status = 'PUBLICADO' OR EXISTS (
            SELECT 1 FROM arquivo_publicidade_story_veiculacao j
            WHERE j.story_id = s.id AND j.fim_em > :instante))
        ORDER BY s.id
        """, Map.of("id", usuarioId, "instante", instante), UUID.class);
    for (UUID id : ids) {
      registrarEstadoInterno(id, motivo, requestId, instante);
    }
  }

  private void registrarEstadoInterno(
      UUID storyId, String motivo, String requestId, OffsetDateTime instante) {
    Map<String, Object> story = unico("""
        SELECT s.id, s.anuncio_id, s.anuncio_midia_id, s.arquivo_midia_id,
               s.modo_conteudo, s.ativacao_beneficio_id, s.status, s.inicio_em,
               s.fim_em, s.encerrado_em, s.criado_por,
               u.nome, u.nome_civil, u.cpf_normalizado, u.email_normalizado,
               u.status AS usuario_status, u.tipo_conta, u.desativado_em, u.excluido_em,
               ab.origem, ab.grupo_ativacao_id, ab.custo_creditos_snapshot,
               gb.ator_usuario_id AS grupo_ator_id, bp.codigo AS beneficio_codigo,
               bp.escopo AS beneficio_escopo,
               mc.id AS movimento_credito_id,
               a.slug, a.titulo, a.descricao, a.categoria, a.preco,
               a.whatsapp_normalizado, a.link_conteudo, a.atendimento_exclusivamente_virtual,
               a.status AS anuncio_status, a.status_moderacao, a.removido_em
        FROM story_anuncio s
        JOIN usuario u ON u.id = s.criado_por
        JOIN ativacao_beneficio ab ON ab.id = s.ativacao_beneficio_id
        JOIN beneficio_premium bp ON bp.id = ab.beneficio_id
        LEFT JOIN grupo_ativacao_beneficio gb ON gb.id = ab.grupo_ativacao_id
        LEFT JOIN LATERAL (
          SELECT id FROM movimento_credito
          WHERE referencia_tipo = 'ATIVACAO_BENEFICIO' AND referencia_id = ab.id
            AND tipo = 'SAIDA' AND direcao = 'DEBITO'
          ORDER BY criado_em, id LIMIT 1
        ) mc ON true
        LEFT JOIN anuncio a ON a.id = s.anuncio_id
        WHERE s.id = :id FOR UPDATE OF s
        """, Map.of("id", storyId));
    if (story == null || !List.of("ANUNCIO", "MIDIA_UPLOAD").contains(story.get("modo_conteudo"))) {
      return; // Legacy rows without a defined commercial right are not backfilled.
    }
    String consultaUltimoPeriodo = """
        SELECT id, inicio_em, fim_em, encerramento_motivo FROM arquivo_publicidade_story_veiculacao
        WHERE story_id = :id ORDER BY inicio_em DESC, id DESC LIMIT 1 FOR UPDATE
        """;
    Map<String, Object> janela = unico(consultaUltimoPeriodo, Map.of("id", storyId));
    OffsetDateTime inicioStory = instanteJdbc(story.get("inicio_em"));
    OffsetDateTime fimStory = instanteJdbc(story.get("fim_em"));
    if (inicioStory == null || fimStory == null || !fimStory.isAfter(inicioStory)) {
      throw new IllegalStateException("Story sem janela temporal valida: " + storyId);
    }
    MarcosCaptura marcos = marcosAposLocks(instante, inicioStory, janela);
    transicoesTemporais.processarStoryAte(storyId, marcos.observado());
    // A due derived version can advance the last version start or close the period.
    janela = unico(consultaUltimoPeriodo, Map.of("id", storyId));
    marcos = marcosAposLocks(marcos.observado(), inicioStory, janela);
    OffsetDateTime observado = marcos.observado();
    boolean publicavel = elegivel(story, observado);
    if (!publicavel) {
      if (janela != null) {
        encerrar(janela, story, observado, marcos.ultimoInicio(), motivo);
      }
      transicoesTemporais.reconciliarStoryAposCaptura(storyId, observado);
      return;
    }
    if (janela != null && !instanteJdbc(janela.get("fim_em")).isAfter(observado)) {
      janela = null; // A closed period is immutable; a new eligible interval gets a new identity.
    }
    if (retiradaDeMidia(motivo) && "ANUNCIO".equals(story.get("modo_conteudo"))) {
      if (janela == null) {
        LOG.warn("Story ativo sem versao arquivada anterior na retirada; lacuna sem historico fabricado: story={}", storyId);
        transicoesTemporais.reconciliarStoryAposCaptura(storyId, observado);
        return;
      }
      if (versaoVigente((UUID) janela.get("id"), observado) == null) {
        LOG.warn("Story com periodo sem versao arquivada na retirada; lacuna sem historico fabricado: story={}, periodo={}",
            storyId, janela.get("id"));
        encerrar(janela, story, observado, marcos.ultimoInicio(),
            "LACUNA_MIDIA_RETIRADA_SEM_COPIA_VERIFICADA");
        transicoesTemporais.reconciliarStoryAposCaptura(storyId, observado);
        return;
      }
    }
    if (!"MIDIA".equals(story.get("beneficio_escopo"))) {
      throw new IllegalStateException("Story exibido sem beneficio de escopo MIDIA: " + storyId);
    }
    List<Map<String, Object>> midias = "ANUNCIO".equals(story.get("modo_conteudo"))
        ? midiasExibidasNoAnuncio((UUID) story.get("anuncio_id"), retiradaDeMidia(motivo))
        : List.of(midiaDireta(story));
    if (janela != null && retiradaDeMidia(motivo)
        && "ANUNCIO".equals(story.get("modo_conteudo"))) {
      Map<String, Object> versao = versaoVigente((UUID) janela.get("id"), observado);
      Map<ChaveMidia, Map<String, Object>> origens = versao == null ? Map.of()
          : origensDaVersao((UUID) versao.get("id"));
      if (versao == null || midias.stream().anyMatch(midia -> referenciasAusentes(midia, origens))) {
        LOG.warn("Story com lacuna de copia verificavel na retirada; periodo encerrado sem versao fabricada: story={}, periodo={}",
            storyId, janela.get("id"));
        encerrar(janela, story, observado, marcos.ultimoInicio(),
            "LACUNA_MIDIA_RETIRADA_SEM_COPIA_VERIFICADA");
        transicoesTemporais.reconciliarStoryAposCaptura(storyId, observado);
        return;
      }
    }
    if (janela == null) {
      janela = abrirJanela(story, observado);
    }
    criarVersaoSeMudou(story, janela, midias, motivo, requestId, observado);
    transicoesTemporais.reconciliarStoryAposCaptura(storyId, observado);
  }

  private MarcosCaptura marcosAposLocks(
      OffsetDateTime instante, OffsetDateTime inicioStory, Map<String, Object> janela) {
    Map<String, Object> parametros = new HashMap<>();
    parametros.put("instante", instante);
    parametros.put("inicioStory", inicioStory);
    parametros.put("janelaId", janela == null ? null : janela.get("id"));
    // A fresh statement after both row locks sees versions committed while this
    // command waited. Never date a post-lock state before an existing version.
    Map<String, Object> marcos = unico("""
        SELECT COALESCE(v.ultimo_inicio, CAST(:inicioStory AS timestamptz)) AS ultimo_inicio,
               GREATEST(clock_timestamp(), CAST(:instante AS timestamptz),
                 CAST(:inicioStory AS timestamptz),
                 COALESCE(v.ultimo_inicio, CAST(:inicioStory AS timestamptz))) AS observado
        FROM (SELECT max(vigente_desde) AS ultimo_inicio
              FROM arquivo_publicidade_story_versao
              WHERE veiculacao_id = :janelaId) v
        """, parametros);
    return new MarcosCaptura(
        instanteJdbc(marcos.get("observado")), instanteJdbc(marcos.get("ultimo_inicio")));
  }

  private boolean elegivel(Map<String, Object> story, OffsetDateTime observado) {
    if (!"PUBLICADO".equals(story.get("status")) || story.get("encerrado_em") != null
        || observado.isBefore(instanteJdbc(story.get("inicio_em")))
        || !observado.isBefore(instanteJdbc(story.get("fim_em")))
        || !"ATIVO".equals(story.get("usuario_status"))
        || !"ANUNCIANTE".equals(story.get("tipo_conta"))
        || story.get("desativado_em") != null || story.get("excluido_em") != null) {
      return false;
    }
    if ("ANUNCIO".equals(story.get("modo_conteudo"))) {
      return "PUBLICADO".equals(story.get("anuncio_status"))
          && "APROVADO".equals(story.get("status_moderacao"))
          && story.get("removido_em") == null;
    }
    return true;
  }

  private Map<String, Object> abrirJanela(
      Map<String, Object> story, OffsetDateTime instante) {
    UUID id = UUID.randomUUID();
    Classificacao classe = Classificacao.de(String.valueOf(story.get("origem")));
    OffsetDateTime fim = instanteJdbc(story.get("fim_em"));
    Map<String, Object> p = new HashMap<>();
    p.put("id", id);
    p.put("storyId", story.get("id"));
    p.put("anuncioId", story.get("anuncio_id"));
    p.put("usuarioId", story.get("criado_por"));
    p.put("ativacaoId", story.get("ativacao_beneficio_id"));
    p.put("grupoId", story.get("grupo_ativacao_id"));
    p.put("movimentoId", story.get("movimento_credito_id"));
    p.put("modo", story.get("modo_conteudo"));
    p.put("classificacao", classe.nome());
    p.put("relacao", classe.relacaoMaterial());
    p.put("cobertura", classe.cobertura());
    p.put("inicio", instante);
    p.put("fim", fim);
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_veiculacao (
          id, story_id, anuncio_id, contratante_usuario_id, ativacao_beneficio_id,
          grupo_ativacao_id, movimento_credito_id, pagamento_id, modo_conteudo,
          classificacao, relacao_material, cobertura, inicio_em, fim_em, retencao_ate,
          encerramento_motivo, capturado_em, atualizado_em)
        VALUES (:id, :storyId, :anuncioId, :usuarioId, :ativacaoId,
          :grupoId, :movimentoId, NULL, :modo,
          :classificacao, :relacao, :cobertura, :inicio, :fim, :fim + INTERVAL '1 year',
          'LIMITE_AUTOMATICO_STORY', :inicio, :inicio)
        """, p);
    return Map.of("id", id, "inicio_em", instante, "fim_em", fim,
        "encerramento_motivo", "LIMITE_AUTOMATICO_STORY");
  }

  private void encerrar(
      Map<String, Object> janela, Map<String, Object> story, OffsetDateTime instante,
      OffsetDateTime ultimoInicio, String motivo) {
    OffsetDateTime limite = instanteJdbc(janela.get("fim_em"));
    OffsetDateTime encerrado = instanteJdbc(story.get("encerrado_em"));
    OffsetDateTime fim = encerrado != null && encerrado.isBefore(instante) ? encerrado : instante;
    if (fim.isAfter(limite)) {
      fim = limite;
    }
    OffsetDateTime inicio = instanteJdbc(janela.get("inicio_em"));
    if (ultimoInicio.isAfter(inicio)) {
      inicio = ultimoInicio;
    }
    if (fim.isBefore(inicio)) {
      fim = inicio;
    }
    if (!fim.isBefore(limite)) {
      return;
    }
    Map<String, Object> p = Map.of("id", janela.get("id"), "fim", fim, "motivo", motivo);
    jdbc.update("""
        UPDATE arquivo_publicidade_story_versao SET vigente_ate = :fim
        WHERE veiculacao_id = :id AND vigente_ate > :fim
        """, p);
    jdbc.update("""
        UPDATE arquivo_publicidade_story_veiculacao
        SET fim_em = :fim, retencao_ate = :fim + INTERVAL '1 year',
            encerramento_motivo = :motivo, atualizado_em = :fim
        WHERE id = :id AND fim_em > :fim
        """, p);
  }

  private void criarVersaoSeMudou(
      Map<String, Object> story,
      Map<String, Object> janela,
      List<Map<String, Object>> midias,
      String motivo,
      String requestId,
      OffsetDateTime instante) {
    Map<String, Object> conteudo = new LinkedHashMap<>();
    conteudo.put("proveniencia", "CAPTURADO_NA_EXIBICAO_PROSPECTIVA");
    conteudo.put("storyId", story.get("id"));
    conteudo.put("modoConteudo", story.get("modo_conteudo"));
    conteudo.put("urlPublicaNaCaptura", "/api/public/stories/" + story.get("id"));
    conteudo.put("nomePublico", story.get("nome"));
    if ("ANUNCIO".equals(story.get("modo_conteudo"))) {
      conteudo.put("anuncioId", story.get("anuncio_id"));
      for (String campo : List.of("slug", "titulo", "descricao", "categoria", "preco",
          "whatsapp_normalizado", "link_conteudo", "atendimento_exclusivamente_virtual")) {
        conteudo.put(campo, story.get(campo));
      }
      conteudo.put("urlAnuncioNaCaptura", "/anuncios/" + story.get("slug"));
      UUID anuncioId = (UUID) story.get("anuncio_id");
      Map<String, Object> localizacao = unico("""
          SELECT l.estado_id, e.uf, l.cidade_id, c.nome AS cidade,
                 l.bairro_id, b.nome AS bairro, l.endereco_resumido
          FROM anuncio_localizacao l
          LEFT JOIN estado e ON e.id = l.estado_id
          LEFT JOIN cidade c ON c.id = l.cidade_id
          LEFT JOIN bairro b ON b.id = l.bairro_id
          WHERE l.anuncio_id = :id
          """, Map.of("id", anuncioId));
      conteudo.put("localizacao", localizacao);
      Map<String, Object> apresentacao = new LinkedHashMap<>();
      apresentacao.put("titulo", story.get("titulo"));
      apresentacao.put("cidade", localizacao == null ? null : localizacao.get("cidade"));
      apresentacao.put("uf", localizacao == null ? null : localizacao.get("uf"));
      apresentacao.put("preco", story.get("preco"));
      apresentacao.put("resumo", resumoStory((String) story.get("descricao")));
      conteudo.put("apresentacaoStory", apresentacao);
      conteudo.put("servicos", jdbc.queryForList(
          "SELECT servico FROM anuncio_servicos WHERE anuncio_id = :id ORDER BY servico",
          Map.of("id", anuncioId), String.class));
      conteudo.put("locaisAtendimento", jdbc.queryForList(
          "SELECT local_atendimento FROM anuncio_local_atendimento WHERE anuncio_id = :id ORDER BY local_atendimento",
          Map.of("id", anuncioId), String.class));
    } else {
      conteudo.put("arquivoMidiaId", story.get("arquivo_midia_id"));
      conteudo.put("urlMidiaNaCaptura", "/api/public/compliance/visitor/media/stories/" + story.get("id"));
    }
    conteudo.put("midias", midias.stream().map(m -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("anuncioMidiaId", m.get("anuncio_midia_id"));
      item.put("arquivoMidiaId", m.get("arquivo_midia_id"));
      item.put("tipo", m.get("tipo"));
      item.put("ordem", m.get("ordem"));
      item.put("visibilidade", m.get("visibilidade_midia"));
      return item;
    }).toList());
    Map<String, Object> contratante = new LinkedHashMap<>();
    contratante.put("usuarioId", story.get("criado_por"));
    contratante.put("nome", story.get("nome"));
    contratante.put("nomeCivil", story.get("nome_civil"));
    contratante.put("cpf", story.get("cpf_normalizado"));
    contratante.put("email", story.get("email_normalizado"));
    contratante.put("terceiroBeneficiario", "NAO_REGISTRADO");
    Classificacao classe = Classificacao.de(String.valueOf(story.get("origem")));
    Map<String, Object> comercial = new LinkedHashMap<>();
    comercial.put("classificacao", classe.nome());
    comercial.put("relacaoMaterial", classe.relacaoMaterial());
    comercial.put("cobertura", classe.cobertura());
    comercial.put("ativacaoBeneficioId", story.get("ativacao_beneficio_id"));
    comercial.put("grupoAtivacaoId", story.get("grupo_ativacao_id"));
    comercial.put("beneficioCodigo", story.get("beneficio_codigo"));
    comercial.put("beneficioEscopo", story.get("beneficio_escopo"));
    comercial.put("origem", story.get("origem"));
    comercial.put("atorAdministrativoId", story.get("grupo_ator_id"));
    comercial.put("movimentoCreditoId", story.get("movimento_credito_id"));
    comercial.put("pagamentoId", null);
    comercial.put("vinculoPagamento", "SALDO_FUNGIVEL_SEM_ALOCACAO_EXATA");
    Map<String, Object> segmentacao = Map.of("estado", "NAO_AFERIDA_NA_CAPTURA");
    Map<String, Object> alcance = Map.of("estado", "NAO_MENSURADO", "destinatariosUnicos", "DESCONHECIDO");
    String conteudoJson = json(conteudo);
    String contratanteJson = json(contratante);
    String comercialJson = json(comercial);
    String segmentacaoJson = json(segmentacao);
    String alcanceJson = json(alcance);
    String hash = sha256((conteudoJson + contratanteJson + comercialJson
        + segmentacaoJson + alcanceJson).getBytes(StandardCharsets.UTF_8));
    Map<String, Object> anterior = unico("""
        SELECT id, numero, conteudo_sha256 FROM arquivo_publicidade_story_versao
        WHERE veiculacao_id = :id AND vigente_ate > :instante
        ORDER BY numero DESC LIMIT 1 FOR UPDATE
        """, Map.of("id", janela.get("id"), "instante", instante));
    if (anterior != null && hash.equals(anterior.get("conteudo_sha256"))) {
      return;
    }
    UUID versaoId = UUID.randomUUID();
    boolean retirada = retiradaDeMidia(motivo) && "ANUNCIO".equals(story.get("modo_conteudo"))
        && anterior != null;
    List<MidiaReferencia> referencias = retirada
        ? referenciasDaRetirada((UUID) anterior.get("id"), midias) : new ArrayList<>();
    Map<ChaveMidia, Map<String, Object>> origens = !retirada && anterior != null
        && "ANUNCIO".equals(story.get("modo_conteudo"))
        ? origensReutilizaveis(story, janela, (UUID) anterior.get("id")) : Map.of();
    List<MidiaCopiada> copias = retirada ? List.of()
        : copiarMidias(versaoId, midias, origens, referencias);
    if (anterior != null) {
      jdbc.update("UPDATE arquivo_publicidade_story_versao SET vigente_ate = :instante WHERE id = :id",
          Map.of("instante", instante, "id", anterior.get("id")));
    }
    Map<String, Object> p = new HashMap<>();
    p.put("id", versaoId);
    p.put("janelaId", janela.get("id"));
    p.put("numero", anterior == null ? 1 : ((Number) anterior.get("numero")).intValue() + 1);
    p.put("inicio", instante);
    p.put("fim", janela.get("fim_em"));
    p.put("motivo", motivo);
    p.put("requestId", requestId);
    p.put("conteudo", conteudoJson);
    p.put("contratante", contratanteJson);
    p.put("comercial", comercialJson);
    p.put("segmentacao", segmentacaoJson);
    p.put("alcance", alcanceJson);
    p.put("hash", hash);
    jdbc.update("""
        INSERT INTO arquivo_publicidade_story_versao (
          id, veiculacao_id, numero, vigente_desde, vigente_ate, capturado_em,
          motivo, request_id, conteudo_json, contratante_json, comercial_json,
          segmentacao_json, alcance_json, conteudo_sha256)
        VALUES (:id, :janelaId, :numero, :inicio, :fim, :inicio,
          :motivo, :requestId, CAST(:conteudo AS jsonb), CAST(:contratante AS jsonb),
          CAST(:comercial AS jsonb), CAST(:segmentacao AS jsonb),
          CAST(:alcance AS jsonb), :hash)
        """, p);
    for (MidiaCopiada midia : copias) {
      Map<String, Object> mp = new HashMap<>();
      mp.put("id", midia.id());
      mp.put("versaoId", versaoId);
      mp.put("anuncioMidiaId", midia.anuncioMidiaId());
      mp.put("arquivoMidiaId", midia.arquivoMidiaId());
      mp.put("variante", midia.variante());
      mp.put("bucket", storageProperties.getPrivateMediaBucket());
      mp.put("chave", midia.chave());
      mp.put("sha256", midia.sha256());
      mp.put("mimeType", midia.mimeType());
      mp.put("bytes", midia.bytes());
      mp.put("ordem", midia.ordem());
      jdbc.update("""
          INSERT INTO arquivo_publicidade_story_midia (
            id, versao_id, anuncio_midia_id, arquivo_midia_id, variante,
            storage_provider, bucket, chave_privada, sha256, mime_type,
            tamanho_bytes, ordem)
          VALUES (:id, :versaoId, :anuncioMidiaId, :arquivoMidiaId, :variante,
            'R2', :bucket, :chave, :sha256, :mimeType, :bytes, :ordem)
          """, mp);
    }
    for (MidiaReferencia referencia : referencias) {
      jdbc.update("""
          INSERT INTO arquivo_publicidade_story_midia_referencia (
            id, versao_id, origem_midia_id, arquivo_midia_id, variante, ordem)
          VALUES (:id, :versaoId, :origemId, :arquivoId, :variante, :ordem)
          """, Map.of("id", UUID.randomUUID(), "versaoId", versaoId,
              "origemId", referencia.origemId(), "arquivoId", referencia.arquivoId(),
              "variante", referencia.variante(), "ordem", referencia.ordem()));
    }
  }

  private boolean retiradaDeMidia(String motivo) {
    return "MIDIA_REMOVIDA_PELO_PROPRIETARIO".equals(motivo)
        || "MODERACAO_FOTO_EXCLUIDA".equals(motivo);
  }

  private Map<String, Object> versaoVigente(UUID janelaId, OffsetDateTime instante) {
    return unico("""
        SELECT id, numero, conteudo_sha256 FROM arquivo_publicidade_story_versao
         WHERE veiculacao_id = :id AND vigente_ate > :instante
         ORDER BY numero DESC LIMIT 1
        """, Map.of("id", janelaId, "instante", instante));
  }

  private List<MidiaReferencia> referenciasDaRetirada(
      UUID versaoAnteriorId, List<Map<String, Object>> midias) {
    Map<ChaveMidia, Map<String, Object>> origens = origensDaVersao(versaoAnteriorId);
    List<MidiaReferencia> referencias = new ArrayList<>();
    for (Map<String, Object> midia : midias) {
      if (referenciasAusentes(midia, origens)) {
        throw new IllegalStateException("Story sem copia verificada para retirada: "
            + midia.get("anuncio_midia_id"));
      }
      referencias.add(referencia(midia, "ORIGINAL", origens));
      if (precisaPreview(midia)) {
        referencias.add(referencia(midia, "PREVIEW_RESTRITO", origens));
      }
    }
    return List.copyOf(referencias);
  }

  private MidiaReferencia referencia(Map<String, Object> midia, String variante,
      Map<ChaveMidia, Map<String, Object>> origens) {
    UUID arquivoId = (UUID) midia.get("arquivo_midia_id");
    Map<String, Object> origem = origens.get(new ChaveMidia(
        (UUID) midia.get("anuncio_midia_id"), arquivoId, variante));
    return new MidiaReferencia((UUID) origem.get("origem_midia_id"), arquivoId,
        variante, ((Number) midia.get("ordem")).intValue());
  }

  private boolean referenciasAusentes(Map<String, Object> midia,
      Map<ChaveMidia, Map<String, Object>> origens) {
    if (midia.get("anuncio_midia_id") == null) {
      return true;
    }
    ChaveMidia chave = new ChaveMidia((UUID) midia.get("anuncio_midia_id"),
        (UUID) midia.get("arquivo_midia_id"), "ORIGINAL");
    Map<String, Object> original = origens.get(chave);
    if (original == null || !origemVerificavel(original)
        || midia.get("sha256") != null && !midia.get("sha256").equals(original.get("sha256"))
        || !Objects.equals(midia.get("mime_type"), original.get("mime_type"))
        || fontePosteriorAoProcessamento(midia.get("processado_em"), original)) {
      return true;
    }
    if (precisaPreview(midia)) {
      Map<String, Object> preview = origens.get(new ChaveMidia(
          chave.anuncioMidiaId(), chave.arquivoMidiaId(), "PREVIEW_RESTRITO"));
      return preview == null || !origemVerificavel(preview)
          || fontePosteriorAoProcessamento(midia.get("preview_restrito_confirmado_em"), preview);
    }
    return false;
  }

  private boolean precisaPreview(Map<String, Object> midia) {
    return "RESTRITA_18".equals(midia.get("visibilidade_midia"))
        && "DISPONIVEL".equals(midia.get("preview_restrito_status"));
  }

  private boolean fontePosteriorAoProcessamento(Object processamento, Map<String, Object> origem) {
    if (processamento == null) {
      return false;
    }
    Object capturado = origem.get("origem_capturada_em");
    return capturado == null || instanteJdbc(processamento).isAfter(instanteJdbc(capturado));
  }

  private boolean origemVerificavel(Map<String, Object> origem) {
    String prefixo = storageProperties.getPrivateMediaPrefix();
    String variante = (String) origem.get("variante");
    String vinculo = origem.get("anuncio_midia_id") == null
        ? "direta" : origem.get("anuncio_midia_id").toString();
    String esperado = prefixo + "arquivo-publicidade/stories/" + origem.get("origem_versao_id")
        + "/" + vinculo + "/" + origem.get("arquivo_midia_id")
        + "/" + variante.toLowerCase(Locale.ROOT);
    return prefixo != null && "R2".equals(origem.get("storage_provider"))
        && Objects.equals(storageProperties.getPrivateMediaBucket(), origem.get("bucket"))
        && esperado.equals(origem.get("chave_privada"))
        && origem.get("sha256") instanceof String hash && hash.matches("[0-9a-f]{64}")
        && origem.get("tamanho_bytes") instanceof Number tamanho && tamanho.longValue() > 0;
  }

  private Map<ChaveMidia, Map<String, Object>> origensReutilizaveis(
      Map<String, Object> story, Map<String, Object> janela, UUID versaoId) {
    Map<ChaveMidia, Map<String, Object>> origens = origensDaVersao(versaoId);
    origens.values().removeIf(origem ->
        !Objects.equals(janela.get("id"), origem.get("origem_veiculacao_id"))
        || !Objects.equals(story.get("id"), origem.get("origem_story_id"))
        || !Objects.equals(story.get("anuncio_id"), origem.get("origem_anuncio_id"))
        || !Objects.equals(story.get("criado_por"), origem.get("origem_usuario_id")));
    return origens;
  }

  private Map<ChaveMidia, Map<String, Object>> origensDaVersao(UUID versaoId) {
    List<Map<String, Object>> linhas = jdbc.queryForList("""
        SELECT m.id AS origem_midia_id, m.versao_id AS origem_versao_id,
               m.anuncio_midia_id, m.arquivo_midia_id, m.variante,
               m.storage_provider, m.bucket, m.chave_privada, m.sha256,
               m.mime_type, m.tamanho_bytes, v.capturado_em AS origem_capturada_em,
               j.id AS origem_veiculacao_id, j.story_id AS origem_story_id,
               j.anuncio_id AS origem_anuncio_id, j.contratante_usuario_id AS origem_usuario_id
          FROM arquivo_publicidade_story_midia m
          JOIN arquivo_publicidade_story_versao v ON v.id = m.versao_id
          JOIN arquivo_publicidade_story_veiculacao j ON j.id = v.veiculacao_id
         WHERE m.versao_id = :versaoId
        UNION ALL
        SELECT origem.id AS origem_midia_id, origem.versao_id AS origem_versao_id,
               origem.anuncio_midia_id, origem.arquivo_midia_id, origem.variante,
               origem.storage_provider, origem.bucket, origem.chave_privada,
               origem.sha256, origem.mime_type, origem.tamanho_bytes,
               v.capturado_em AS origem_capturada_em,
               origem_j.id AS origem_veiculacao_id, origem_j.story_id AS origem_story_id,
               origem_j.anuncio_id AS origem_anuncio_id,
               origem_j.contratante_usuario_id AS origem_usuario_id
          FROM arquivo_publicidade_story_midia_referencia ref
          JOIN arquivo_publicidade_story_midia origem ON origem.id = ref.origem_midia_id
          JOIN arquivo_publicidade_story_versao v ON v.id = origem.versao_id
          JOIN arquivo_publicidade_story_versao destino_v ON destino_v.id = ref.versao_id
          JOIN arquivo_publicidade_story_veiculacao origem_j ON origem_j.id = v.veiculacao_id
          JOIN arquivo_publicidade_story_veiculacao destino_j ON destino_j.id = destino_v.veiculacao_id
         WHERE ref.versao_id = :versaoId
           AND ref.arquivo_midia_id = origem.arquivo_midia_id
           AND ref.variante = origem.variante
           AND origem_j.story_id = destino_j.story_id
        """, Map.of("versaoId", versaoId));
    Map<ChaveMidia, Map<String, Object>> origens = new HashMap<>();
    for (Map<String, Object> linha : linhas) {
      ChaveMidia chave = new ChaveMidia((UUID) linha.get("anuncio_midia_id"),
          (UUID) linha.get("arquivo_midia_id"), (String) linha.get("variante"));
      if (origens.putIfAbsent(chave, linha) != null) {
        throw new IllegalStateException("Story com copia ambigua na versao: " + versaoId);
      }
    }
    return origens;
  }

  private List<Map<String, Object>> midiasExibidasNoAnuncio(UUID anuncioId, boolean retirada) {
    List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioId(anuncioId);
    PremiumPublicoFlagsDto flags = premiumPublicoMapper.flagsPorAnuncioIds(List.of(anuncioId))
        .getOrDefault(anuncioId, PremiumPublicoFlagsDto.vazio());
    int maxFotos = flags.fotosExtrasAtivo() ? FOTOS_COM_EXTRA : FOTOS_BASE;
    List<UUID> exibidas;
    if (retirada) {
      // The position policy is shared with the public gallery. A transient URL/provider
      // outage cannot turn a previously verified survivor into an empty Story version;
      // SQL eligibility and the preceding private copy are checked below.
      exibidas = SelecaoMidiasPublicas.selecionar(
          vinculos.stream().map(MidiaVinculoLeitura::de).toList(), maxFotos,
          flags.videoAtivo()).stream().map(MidiaVinculoLeitura::id).toList();
    } else {
      List<UUID> arquivoIds = vinculos.stream().map(AnuncioMidiaEntity::getArquivoMidiaId)
          .filter(Objects::nonNull).distinct().toList();
      Map<UUID, ArquivoMidiaEntity> arquivos = arquivoIds.isEmpty() ? Map.of()
          : arquivoMidiaRepository.findByIdIn(arquivoIds).stream()
              .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
      exibidas = midiaPublicaMapper.publicas(
          vinculos, arquivos, true, maxFotos, flags.videoAtivo()).stream()
          .filter(MidiaPublicaDto::autorizada)
          .filter(item -> item.urlPublica() != null && !item.urlPublica().isBlank())
          .map(MidiaPublicaDto::id).toList();
    }
    if (exibidas.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> dados = jdbc.queryForList("""
        SELECT am.id AS anuncio_midia_id, am.arquivo_midia_id, am.tipo,
               am.ordem, am.visibilidade_midia, ar.storage_provider,
               ar.bucket, ar.chave_objeto, ar.sha256, ar.mime_type,
               ar.processado_em, ar.preview_restrito_chave,
               ar.preview_restrito_status, ar.preview_restrito_confirmado_em
        FROM anuncio_midia am JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
        WHERE am.id IN (:ids) AND am.anuncio_id = :anuncioId
          AND am.status = 'PUBLICAVEL' AND ar.status_arquivo = 'VALIDADO'
          AND NOT EXISTS (SELECT 1 FROM documento_usuario d WHERE d.arquivo_midia_id = ar.id)
        """, Map.of("ids", exibidas, "anuncioId", anuncioId));
    Map<UUID, Map<String, Object>> porId = dados.stream().collect(Collectors.toMap(
        item -> (UUID) item.get("anuncio_midia_id"), Function.identity()));
    if (!retirada && porId.size() != exibidas.size()) {
      throw new IllegalStateException("Story exibiu midia documental ou indisponivel: " + anuncioId);
    }
    // An invalid or documentary file still consumes its public position but is not
    // eligible for a private copy. The withdrawal path must not treat it as evidence.
    return exibidas.stream().map(porId::get).filter(Objects::nonNull).toList();
  }

  private Map<String, Object> midiaDireta(Map<String, Object> story) {
    UUID arquivoId = (UUID) story.get("arquivo_midia_id");
    UUID vinculoId = (UUID) story.get("anuncio_midia_id");
    if (arquivoId == null && vinculoId == null) {
      throw new IllegalStateException("Story MIDIA_UPLOAD sem arquivo: " + story.get("id"));
    }
    Map<String, Object> midia = unico("""
        SELECT am.id AS anuncio_midia_id, ar.id AS arquivo_midia_id,
               COALESCE(am.tipo, CASE WHEN ar.mime_type LIKE 'video/%' THEN 'VIDEO' ELSE 'FOTO' END) AS tipo,
               COALESCE(am.ordem, 0) AS ordem, 'RESTRITA_18' AS visibilidade_midia,
               ar.storage_provider, ar.bucket, ar.chave_objeto, ar.sha256, ar.mime_type,
               ar.preview_restrito_chave, ar.preview_restrito_status
        FROM arquivo_midia ar LEFT JOIN anuncio_midia am ON am.id = :vinculoId
        WHERE ar.id = COALESCE(:arquivoId, am.arquivo_midia_id)
          AND ar.status_arquivo = 'VALIDADO'
          AND NOT EXISTS (SELECT 1 FROM documento_usuario d WHERE d.arquivo_midia_id = ar.id)
        """, nullable("arquivoId", arquivoId, "vinculoId", vinculoId));
    if (midia == null) {
      throw new IllegalStateException("Story exibiu arquivo documental ou indisponivel: " + story.get("id"));
    }
    return midia;
  }

  private List<MidiaCopiada> copiarMidias(UUID versaoId, List<Map<String, Object>> midias,
      Map<ChaveMidia, Map<String, Object>> origens, List<MidiaReferencia> referencias) {
    if (midias.isEmpty()) {
      return List.of();
    }
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()
        || storageProperties.getPrivateMediaBucket() == null
        || storageProperties.getPrivateMediaPrefix() == null
        || !TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("storage privado indisponivel para arquivo de Story");
    }
    List<MidiaCopiada> copias = new ArrayList<>();
    for (Map<String, Object> midia : midias) {
      if (!"R2".equals(midia.get("storage_provider"))) {
        throw new IllegalStateException("origem de Story fora do storage verificavel: " + midia.get("arquivo_midia_id"));
      }
      String bucket = String.valueOf(midia.get("bucket"));
      String chave = String.valueOf(midia.get("chave_objeto"));
      String visibilidade = String.valueOf(midia.get("visibilidade_midia"));
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
        throw new IllegalStateException("origem de Story nao verificavel: " + midia.get("arquivo_midia_id"));
      }
      copias.add(copiarUma(storage, versaoId, midia, "ORIGINAL", area, chave, origens, referencias));
      if (midia.get("anuncio_midia_id") != null
          && "RESTRITA_18".equals(visibilidade)
          && "DISPONIVEL".equals(midia.get("preview_restrito_status"))) {
        String preview = (String) midia.get("preview_restrito_chave");
        if (preview == null || storageProperties.getPublicMediaPrefix() == null
            || !preview.startsWith(storageProperties.getPublicMediaPrefix())) {
          throw new IllegalStateException("preview de Story sem origem verificavel: " + midia.get("arquivo_midia_id"));
        }
        copias.add(copiarUma(storage, versaoId, midia,
            "PREVIEW_RESTRITO", StorageArea.PUBLIC_MEDIA, preview, origens, referencias));
      }
    }
    return copias.stream().filter(Objects::nonNull).toList();
  }

  private MidiaCopiada copiarUma(
      ObjectStorage storage,
      UUID versaoId,
      Map<String, Object> midia,
      String variante,
      StorageArea origem,
      String chaveOrigem,
      Map<ChaveMidia, Map<String, Object>> origens,
      List<MidiaReferencia> referencias) {
    StoredObject original = storage.get(origem, chaveOrigem);
    if (original == null || original.content() == null || original.content().length == 0
        || original.contentType() == null) {
      throw new IllegalStateException("bytes do Story indisponiveis: " + midia.get("arquivo_midia_id"));
    }
    String hash = sha256(original.content());
    if ("ORIGINAL".equals(variante) && midia.get("sha256") != null
        && !hash.equals(midia.get("sha256"))) {
      throw new IllegalStateException("hash da fonte de Story divergente: " + midia.get("arquivo_midia_id"));
    }
    Map<String, Object> anterior = origens.get(new ChaveMidia(
        (UUID) midia.get("anuncio_midia_id"), (UUID) midia.get("arquivo_midia_id"), variante));
    if (copiaReutilizavel(storage, anterior, original, hash)) {
      referencias.add(referencia(midia, variante, origens));
      return null; // The physical copy remains owned by its original version.
    }
    String caminhoArquivoPrivado = storageProperties.getPrivateMediaPrefix()
        + "arquivo-publicidade/stories/" + versaoId + "/"
        + (midia.get("anuncio_midia_id") == null ? "direta" : midia.get("anuncio_midia_id"))
        + "/" + midia.get("arquivo_midia_id")
        + "/" + variante.toLowerCase(java.util.Locale.ROOT);
    ObjectWriteResult resultado = storage.putIfAbsent(
        StorageArea.PRIVATE_MEDIA, caminhoArquivoPrivado, original.content(), original.contentType());
    if (resultado == ObjectWriteResult.CREATED) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status == STATUS_ROLLED_BACK) {
            try {
              storage.delete(StorageArea.PRIVATE_MEDIA, caminhoArquivoPrivado);
            } catch (RuntimeException exception) {
              LOG.error("Falha ao limpar copia privada de Story apos rollback: versao={}, arquivo={}, variante={}",
                  versaoId, midia.get("arquivo_midia_id"), variante, exception);
            }
          }
        }
      });
    }
    StoredObject confirmada = storage.get(StorageArea.PRIVATE_MEDIA, caminhoArquivoPrivado);
    if (confirmada == null || confirmada.content() == null
        || !hash.equals(sha256(confirmada.content()))
        || !Objects.equals(original.contentType(), confirmada.contentType())) {
      throw new IllegalStateException("copia privada do Story nao confirmou integridade: " + midia.get("arquivo_midia_id"));
    }
    return new MidiaCopiada(UUID.randomUUID(), (UUID) midia.get("anuncio_midia_id"),
        (UUID) midia.get("arquivo_midia_id"), variante, caminhoArquivoPrivado,
        hash, original.contentType(), original.content().length,
        ((Number) midia.get("ordem")).intValue());
  }

  private boolean copiaReutilizavel(ObjectStorage storage, Map<String, Object> origem,
      StoredObject atual, String hash) {
    if (origem == null || !(origem.get("origem_midia_id") instanceof UUID)
        || !origemVerificavel(origem) || !hash.equals(origem.get("sha256"))
        || !Objects.equals(atual.contentType(), origem.get("mime_type"))
        || ((Number) origem.get("tamanho_bytes")).longValue() != atual.content().length) {
      return false;
    }
    // Normal capture proves current bytes and the retained object separately.
    // Withdrawal continues through its existing path without storage I/O.
    StoredObject retida;
    try {
      retida = storage.get(StorageArea.PRIVATE_MEDIA, (String) origem.get("chave_privada"));
    } catch (RuntimeException exception) {
      return false;
    }
    return retida != null && retida.content() != null
        && retida.content().length == atual.content().length
        && hash.equals(sha256(retida.content()))
        && Objects.equals(atual.contentType(), retida.contentType());
  }

  private Map<String, Object> unico(String sql, Map<String, ?> parametros) {
    List<Map<String, Object>> linhas = jdbc.queryForList(sql, parametros);
    return linhas.isEmpty() ? null : linhas.get(0);
  }

  private Map<String, Object> nullable(String a, Object av, String b, Object bv) {
    Map<String, Object> valores = new HashMap<>();
    valores.put(a, av);
    valores.put(b, bv);
    return valores;
  }

  private void validarEntrada(UUID id, String motivo, OffsetDateTime instante) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(instante, "instante");
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("motivo obrigatorio para arquivo de Story");
    }
  }

  private OffsetDateTime instanteJdbc(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime instante) {
      return instante;
    }
    if (valor instanceof Timestamp instante) {
      return instante.toInstant().atOffset(ZoneOffset.UTC);
    }
    throw new IllegalStateException("instante de Story invalido");
  }

  private String json(Object valor) {
    try {
      return mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar arquivo de Story", exception);
    }
  }

  private String resumoStory(String descricao) {
    if (descricao == null) {
      return null;
    }
    String normalizado = descricao.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
        .replaceAll("\\s+", " ").trim();
    if (normalizado.isEmpty()) {
      return null;
    }
    return normalizado.length() <= 220 ? normalizado
        : normalizado.substring(0, 217).stripTrailing() + "...";
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private record Classificacao(String nome, String relacaoMaterial, String cobertura) {
    static Classificacao de(String origem) {
      if ("ADMIN".equals(origem)) {
        return new Classificacao("ADMINISTRATIVA", "DESCONHECIDA", "PREVENTIVA");
      }
      if ("CORTESIA".equals(origem) || "CAMPANHA".equals(origem)) {
        return new Classificacao("PROMOCIONAL", "DESCONHECIDA", "PREVENTIVA");
      }
      // Debiting fungible credit does not identify a particular confirmed payment.
      return new Classificacao("ORIGEM_INDETERMINADA", "DESCONHECIDA", "PREVENTIVA");
    }
  }

  private record MidiaCopiada(UUID id, UUID anuncioMidiaId, UUID arquivoMidiaId,
      String variante, String chave, String sha256, String mimeType, long bytes, int ordem) {
  }

  private record MarcosCaptura(OffsetDateTime observado, OffsetDateTime ultimoInicio) {
  }

  private record ChaveMidia(UUID anuncioMidiaId, UUID arquivoMidiaId, String variante) {
  }

  private record MidiaReferencia(UUID origemId, UUID arquivoId, String variante, int ordem) {
  }
}
