package br.com.topsdojob.v3.application.arquivo;

import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_BASE;
import static br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService.FOTOS_COM_EXTRA;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Materializes only expiry effects derivable from an already captured private
 * version. A plan is not a historical version: it freezes the source version,
 * projected content and verified private media references before the boundary.
 */
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ArquivoPublicidadeTransicaoTemporalService implements ApplicationRunner {
  private static final Logger LOG = LoggerFactory.getLogger(ArquivoPublicidadeTransicaoTemporalService.class);
  private static final TypeReference<LinkedHashMap<String, Object>> JSON_MAP = new TypeReference<>() { };
  private static final String DERIVED_REASON = "EXPIRACAO_TEMPORAL_DERIVADA";
  private static final int BATCH_SIZE = 100;

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final BeneficioAnuncioConsultaService beneficios;
  private final TransactionTemplate transactions;
  private final Clock clock;

  @Autowired
  public ArquivoPublicidadeTransicaoTemporalService(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper mapper,
      BeneficioAnuncioConsultaService beneficios,
      PlatformTransactionManager transactionManager) {
    this(jdbc, mapper, beneficios, transactionManager, Clock.systemUTC());
  }

  ArquivoPublicidadeTransicaoTemporalService(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper mapper,
      BeneficioAnuncioConsultaService beneficios,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.beneficios = beneficios;
    this.transactions = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  /** Caller already holds the advertisement row lock, before reading its new state. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void processarAnuncioAte(UUID anuncioId, OffsetDateTime instanteProcessamento) {
    processar(Tipo.ANUNCIO, anuncioId, instanteProcessamento);
  }

  /** Caller already holds the advertisement row lock, after updating its archive. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void reconciliarAnuncioAposCaptura(UUID anuncioId, OffsetDateTime instanteCaptura) {
    reconciliar(Tipo.ANUNCIO, anuncioId, instanteCaptura);
  }

  /** Caller already holds the Story row lock, before reading its new state. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void processarStoryAte(UUID storyId, OffsetDateTime instanteProcessamento) {
    processar(Tipo.STORY, storyId, instanteProcessamento);
  }

  /** Caller already holds the Story row lock, after updating its archive. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void reconciliarStoryAposCaptura(UUID storyId, OffsetDateTime instanteCaptura) {
    reconciliar(Tipo.STORY, storyId, instanteCaptura);
  }

  /** Existing pre-V057 evidence is examined only from startup time forward. */
  @Override
  public void run(ApplicationArguments args) {
    String produto = jdbc.getJdbcTemplate().execute(
        (ConnectionCallback<String>) conexao -> conexao.getMetaData().getDatabaseProductName());
    if ("H2".equalsIgnoreCase(produto)) {
      LOG.info("Bootstrap temporal ignorado em banco H2 de teste");
      return;
    }
    if (!"PostgreSQL".equalsIgnoreCase(produto)) {
      throw new IllegalStateException("bootstrap temporal requer PostgreSQL: " + produto);
    }
    for (Tipo tipo : Tipo.values()) {
      inicializarFontesLegadas(tipo);
    }
  }

  private void inicializarFontesLegadas(Tipo tipo) {
    UUID cursor = null;
    long verificadas = 0;
    while (true) {
      OffsetDateTime observado = agoraBanco();
      Map<String, Object> parametros = new HashMap<>();
      parametros.put("agora", observado);
      String filtroCursor = "";
      if (cursor != null) {
        filtroCursor = " AND v.id > :cursor";
        parametros.put("cursor", cursor);
      }
      List<Map<String, Object>> candidatas = jdbc.queryForList("""
          SELECT v.id AS versao_id, j.%s AS sujeito_id
          FROM %s v JOIN %s j ON j.id = v.veiculacao_id
          WHERE v.transicoes_reconciliadas_em IS NULL
            AND v.vigente_desde <= :agora
            AND (v.vigente_ate IS NULL OR v.vigente_ate > :agora)
            AND j.inicio_em <= :agora AND (j.fim_em IS NULL OR j.fim_em > :agora)%s%s
            AND NOT EXISTS (
              SELECT 1 FROM %s posterior
              WHERE posterior.veiculacao_id = v.veiculacao_id
                AND posterior.numero > v.numero
                AND posterior.vigente_desde <= :agora
                AND (posterior.vigente_ate IS NULL OR posterior.vigente_ate > :agora)
            )
          ORDER BY v.id LIMIT %d
          """.formatted(tipo.subjectColumn, tipo.versionTable, tipo.windowTable,
              tipo == Tipo.STORY ? " AND j.modo_conteudo = 'ANUNCIO'" : "",
              filtroCursor, tipo.versionTable, BATCH_SIZE), parametros);
      if (candidatas.isEmpty()) break;
      for (Map<String, Object> candidata : candidatas) {
        UUID sujeito = (UUID) candidata.get("sujeito_id");
        // A failure aborts startup before ApplicationReadyEvent; a malformed
        // legacy source is not silently skipped to claim a complete archive.
        transactions.executeWithoutResult(status -> {
          bloquearSujeito(tipo, sujeito);
          OffsetDateTime sobLock = agoraBanco();
          processar(tipo, sujeito, sobLock);
          diagnosticarLacunaLegada(tipo, sujeito, sobLock);
          reconciliar(tipo, sujeito, sobLock);
        });
        cursor = (UUID) candidata.get("versao_id");
        verificadas++;
      }
    }
    LOG.info("Bootstrap temporal prospectivo concluido: tipo={}, fontes={}", tipo, verificadas);
  }

  private void diagnosticarLacunaLegada(Tipo tipo, UUID sujeito, OffsetDateTime instante) {
    for (Fonte fonte : fontesAtivas(tipo, sujeito, instante)) {
      Object midiasCampo = lerMapa(fonte.conteudoJson()).get("midias");
      if (!(midiasCampo instanceof List<?> midias)) continue;
      List<Direito> direitos = direitosNaCaptura(fonte.anuncioId(), instante);
      boolean extraAtivo = direitos.stream().anyMatch(direito ->
          PremiumBeneficioCodigo.FOTOS_EXTRA_5.equals(direito.codigo()));
      boolean videoAtivo = direitos.stream().anyMatch(direito ->
          PremiumBeneficioCodigo.VIDEO_1.equals(direito.codigo()));
      Map<UUID, Integer> posicoes = posicoesFotosPublicas(fonte.anuncioId());
      boolean possivelLacuna = false;
      for (Object entrada : midias) {
        if (!(entrada instanceof Map<?, ?> midia)) continue;
        String tipoMidia = String.valueOf(midia.get("tipo"));
        if ("VIDEO".equals(tipoMidia) && !videoAtivo) {
          possivelLacuna = true;
        } else if ("FOTO".equals(tipoMidia)) {
          Object vinculo = midia.get("anuncioMidiaId");
          UUID vinculoId = vinculo == null ? null : UUID.fromString(String.valueOf(vinculo));
          Integer posicao = vinculoId == null ? null : posicoes.get(vinculoId);
          if (posicao == null || (!extraAtivo && posicao > FOTOS_BASE)) {
            possivelLacuna = true;
          }
        }
      }
      if (possivelLacuna) {
        LOG.warn("Arquivo {} legado ativo pode ter fronteira de midia ja vencida; "
            + "nenhuma versao passada foi reconstruida", tipo);
      }
    }
  }

  /** Bounded background reconciliation for records without a later user action. */
  @Scheduled(fixedDelayString = "${app.arquivo.publicidade.transicoes.poll-delay-ms:30000}")
  public void processarVencidas() {
    OffsetDateTime agora = agoraBanco();
    for (Tipo tipo : Tipo.values()) {
      List<UUID> ids = jdbc.queryForList("""
          SELECT %s FROM %s
          WHERE estado = 'PENDENTE' AND fronteira_em <= :agora
          GROUP BY %s ORDER BY MIN(fronteira_em), %s LIMIT %d
          """.formatted(tipo.subjectColumn, tipo.planTable, tipo.subjectColumn,
              tipo.subjectColumn, BATCH_SIZE),
          Map.of("agora", agora), UUID.class);
      for (UUID id : ids) {
        try {
          transactions.executeWithoutResult(status -> {
            bloquearSujeito(tipo, id);
            processar(tipo, id, agora);
          });
        } catch (RuntimeException exception) {
          LOG.error("Falha na transicao temporal do arquivo: tipo={}, id={}", tipo, id, exception);
        }
      }
    }
  }

  private void bloquearSujeito(Tipo tipo, UUID id) {
    jdbc.queryForList("SELECT id FROM %s WHERE id = :id FOR UPDATE".formatted(tipo.lockTable),
        Map.of("id", id));
  }

  private void reconciliar(Tipo tipo, UUID id, OffsetDateTime instante) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(instante, "instante");
    List<Map<String, Object>> pendentes = jdbc.queryForList("""
        SELECT id, veiculacao_id, evidencia_versao_id, fronteira_em, plano_sha256
        FROM %s WHERE %s = :id AND estado = 'PENDENTE'
        ORDER BY fronteira_em, id FOR UPDATE
        """.formatted(tipo.planTable, tipo.subjectColumn), Map.of("id", id));
    for (Map<String, Object> pendente : pendentes) {
      if (!instanteJdbc(pendente.get("fronteira_em")).isAfter(instante)) {
        throw new IllegalStateException("transicao vencida deve preceder nova captura: " + id);
      }
    }

    List<Fonte> fontes = fontesAtivas(tipo, id, instante);
    List<PlanoNovo> desejados = new ArrayList<>();
    for (Fonte fonte : fontes) {
      List<Direito> direitos = direitosNaCaptura(fonte.anuncioId(), instante);
      Map<UUID, Integer> posicoesFotos = posicoesFotosPublicas(fonte.anuncioId());
      List<OffsetDateTime> fronteiras = direitos.stream().map(Direito::fimEm)
          .filter(fim -> fim.isAfter(instante))
          .filter(fim -> fonte.fimEm() == null || fim.isBefore(fonte.fimEm()))
          .distinct().sorted().toList();
      for (OffsetDateTime fronteira : fronteiras) {
        Projecao projecao = projetar(fonte.conteudoJson(), direitos, fronteira,
            posicoesFotos, fonte.versaoId());
        List<MidiaOrigem> midias = origensDaProjecao(tipo, fonte.versaoId(), projecao.midias());
        String dependencias = json(Map.of(
            "ativacoes", direitos.stream().map(direito -> Map.of(
                "ativacaoBeneficioId", direito.ativacaoId().toString(),
                "codigo", direito.codigo(),
                "fimEm", direito.fimEm().toString())).toList(),
            "posicoesFotosPublicaveis", posicoesFotos.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(item -> Map.of("anuncioMidiaId", item.getKey().toString(),
                    "posicao", item.getValue())).toList()));
        String hashPlano = sha256((fonte.versaoId() + "|" + fronteira + "|"
            + projecao.conteudoJson() + "|" + dependencias + "|"
            + midias.stream().map(MidiaOrigem::identidade).sorted().toList())
            .getBytes(StandardCharsets.UTF_8));
        desejados.add(new PlanoNovo(fonte, fronteira, projecao.conteudoJson(),
            dependencias, hashPlano, midias));
      }
    }

    Set<UUID> manter = new HashSet<>();
    for (PlanoNovo desejado : desejados) {
      for (Map<String, Object> pendente : pendentes) {
        if (Objects.equals(pendente.get("veiculacao_id"), desejado.fonte().janelaId())
            && Objects.equals(pendente.get("evidencia_versao_id"), desejado.fonte().versaoId())
            && instanteJdbc(pendente.get("fronteira_em")).isEqual(desejado.fronteira())
            && Objects.equals(pendente.get("plano_sha256"), desejado.hash())) {
          manter.add((UUID) pendente.get("id"));
          break;
        }
      }
    }
    OffsetDateTime processadoEm = agora();
    for (Map<String, Object> pendente : pendentes) {
      UUID planoId = (UUID) pendente.get("id");
      if (!manter.contains(planoId)) {
        jdbc.update("""
            UPDATE %s SET estado = 'CANCELADA', cancelado_em = :agora
            WHERE id = :id AND estado = 'PENDENTE'
            """.formatted(tipo.planTable), Map.of("agora", processadoEm, "id", planoId));
      }
    }
    for (PlanoNovo desejado : desejados) {
      boolean existente = pendentes.stream().anyMatch(pendente -> manter.contains(pendente.get("id"))
          && Objects.equals(pendente.get("veiculacao_id"), desejado.fonte().janelaId())
          && instanteJdbc(pendente.get("fronteira_em")).isEqual(desejado.fronteira()));
      if (!existente) inserirPlano(tipo, id, desejado, processadoEm);
    }
    for (Fonte fonte : fontes) {
      jdbc.update("""
          UPDATE %s SET transicoes_reconciliadas_em = :instante WHERE id = :id
          """.formatted(tipo.versionTable), Map.of(
              "instante", instante, "id", fonte.versaoId()));
    }
  }

  private List<Fonte> fontesAtivas(Tipo tipo, UUID id, OffsetDateTime instante) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT j.id AS janela_id, j.anuncio_id, j.fim_em,
               v.id AS versao_id, v.conteudo_json::text AS conteudo_json,
               v.conteudo_sha256
        FROM %s j JOIN LATERAL (
          SELECT id, conteudo_json, conteudo_sha256
          FROM %s WHERE veiculacao_id = j.id AND vigente_desde <= :instante
            AND (vigente_ate IS NULL OR vigente_ate > :instante)
          ORDER BY numero DESC LIMIT 1
        ) v ON true
        WHERE j.%s = :id AND j.inicio_em <= :instante
          AND (j.fim_em IS NULL OR j.fim_em > :instante)%s
        ORDER BY j.id
        """.formatted(tipo.windowTable, tipo.versionTable,
            tipo.subjectColumn, tipo == Tipo.STORY ? " AND j.modo_conteudo = 'ANUNCIO'" : ""),
        Map.of("id", id, "instante", instante));
    return rows.stream().map(row -> new Fonte(
        (UUID) row.get("janela_id"), (UUID) row.get("anuncio_id"),
        (UUID) row.get("versao_id"), instanteNullable(row.get("fim_em")),
        (String) row.get("conteudo_json"), (String) row.get("conteudo_sha256")))
        .toList();
  }

  /** Mirrors the public position selector before invalid files consume their slots. */
  private Map<UUID, Integer> posicoesFotosPublicas(UUID anuncioId) {
    if (anuncioId == null) return Map.of();
    List<Map<String, Object>> candidatas = jdbc.queryForList("""
        SELECT id, ordem FROM anuncio_midia
        WHERE anuncio_id = :anuncio AND status = 'PUBLICAVEL' AND tipo = 'FOTO'
          AND finalidade IS DISTINCT FROM 'STORY' AND visibilidade_midia IS NOT NULL
        """, Map.of("anuncio", anuncioId));
    candidatas.sort(Comparator
        .comparing((Map<String, Object> row) -> (Integer) row.get("ordem"),
            Comparator.nullsLast(Integer::compareTo))
        .thenComparing(row -> (UUID) row.get("id"), Comparator.nullsLast(UUID::compareTo)));
    Map<UUID, Integer> posicoes = new LinkedHashMap<>();
    for (Map<String, Object> candidata : candidatas) {
      posicoes.put((UUID) candidata.get("id"), posicoes.size() + 1);
    }
    return Map.copyOf(posicoes);
  }

  private List<Direito> direitosNaCaptura(UUID anuncioId, OffsetDateTime instante) {
    if (anuncioId == null) return List.of();
    return beneficios.consultarCalculadosPorAnuncio(List.of(anuncioId), instante)
        .getOrDefault(anuncioId, List.of()).stream()
        .filter(item -> item.ativacao() != null && item.beneficio() != null)
        .filter(item -> item.status() == PremiumBeneficioStatusCalculado.ATIVO
            || item.status() == PremiumBeneficioStatusCalculado.VENCENDO)
        .filter(item -> PremiumBeneficioCodigo.FOTOS_EXTRA_5.equals(item.beneficio().getCodigo())
            || PremiumBeneficioCodigo.VIDEO_1.equals(item.beneficio().getCodigo()))
        .map(item -> {
          OffsetDateTime fim = item.ativacao().getFimEm();
          GrupoAtivacaoBeneficioEntity grupo = item.grupo();
          if (grupo != null && grupo.getValidadeFimEm() != null
              && (fim == null || grupo.getValidadeFimEm().isBefore(fim))) {
            fim = grupo.getValidadeFimEm();
          }
          return new Direito(item.ativacao().getId(), item.beneficio().getCodigo(), fim);
        })
        .filter(direito -> direito.ativacaoId() != null && direito.fimEm() != null
            && direito.fimEm().isAfter(instante))
        .sorted(Comparator.comparing(Direito::fimEm).thenComparing(Direito::codigo)
            .thenComparing(Direito::ativacaoId))
        .toList();
  }

  Projecao projetar(String conteudoFonte, List<Direito> direitos, OffsetDateTime fronteira,
      Map<UUID, Integer> posicoesFotos, UUID fonteVersaoId) {
    Map<String, Object> conteudo = lerMapa(conteudoFonte);
    Object midiasCampo = conteudo.get("midias");
    if (!(midiasCampo instanceof List<?> midiasFonte)) {
      throw new IllegalStateException("snapshot de midias ausente para transicao temporal");
    }
    boolean extraAtivo = direitos.stream().anyMatch(direito ->
        PremiumBeneficioCodigo.FOTOS_EXTRA_5.equals(direito.codigo())
            && direito.fimEm().isAfter(fronteira));
    boolean videoAtivo = direitos.stream().anyMatch(direito ->
        PremiumBeneficioCodigo.VIDEO_1.equals(direito.codigo())
            && direito.fimEm().isAfter(fronteira));
    int limiteFotos = extraAtivo ? FOTOS_COM_EXTRA : FOTOS_BASE;
    List<Map<String, Object>> selecionadas = new ArrayList<>();
    for (Object entrada : midiasFonte) {
      if (!(entrada instanceof Map<?, ?> dados)) {
        throw new IllegalStateException("midia de evidencia invalida");
      }
      Map<String, Object> midia = new LinkedHashMap<>();
      dados.forEach((chave, valor) -> midia.put(String.valueOf(chave), valor));
      String tipo = String.valueOf(midia.get("tipo"));
      if ("FOTO".equals(tipo)) {
        UUID vinculoId = UUID.fromString(String.valueOf(midia.get("anuncioMidiaId")));
        Integer posicao = posicoesFotos.get(vinculoId);
        if (posicao == null) {
          throw new IllegalStateException("foto arquivada sem posicao publica comprovada: " + vinculoId);
        }
        if (posicao <= limiteFotos) selecionadas.add(midia);
      } else if ("VIDEO".equals(tipo)) {
        if (videoAtivo) selecionadas.add(midia);
      } else {
        throw new IllegalStateException("tipo de midia arquivada nao derivavel: " + tipo);
      }
    }
    conteudo.put("midias", selecionadas);
    String marcador = "DERIVADO_DE_EXPIRACAO_TEMPORAL";
    if (conteudo.containsKey("estado")) {
      conteudo.put("estado", marcador);
    } else if (conteudo.containsKey("proveniencia")) {
      conteudo.put("proveniencia", marcador);
    } else {
      throw new IllegalStateException("proveniencia da evidencia fonte ausente");
    }
    conteudo.put("derivacaoTemporal", Map.of(
        "evidenciaOrigemVersaoId", fonteVersaoId.toString(),
        "fronteiraEm", fronteira.toString()));
    return new Projecao(json(conteudo), List.copyOf(selecionadas));
  }

  private List<MidiaOrigem> origensDaProjecao(
      Tipo tipo, UUID fonteVersaoId, List<Map<String, Object>> selecionadas) {
    String sql = tipo == Tipo.ANUNCIO ? """
        SELECT m.id AS origem_midia_id, m.anuncio_midia_id AS midia_id,
               m.variante, m.ordem,
               (am.anuncio_id = j.anuncio_id
                 AND am.arquivo_midia_id = m.arquivo_midia_id) AS origem_valida
        FROM arquivo_publicidade_midia m
        JOIN arquivo_publicidade_versao v ON v.id = m.versao_id
        JOIN arquivo_publicidade_veiculacao j ON j.id = v.veiculacao_id
        JOIN anuncio_midia am ON am.id = m.anuncio_midia_id
        WHERE m.versao_id = :versao
        UNION ALL
        SELECT r.origem_midia_id, r.anuncio_midia_id AS midia_id,
               r.variante, r.ordem,
               (oj.anuncio_id = sj.anuncio_id
                 AND om.anuncio_midia_id = r.anuncio_midia_id
                 AND om.variante = r.variante
                 AND oam.anuncio_id = oj.anuncio_id
                 AND oam.arquivo_midia_id = om.arquivo_midia_id) AS origem_valida
        FROM arquivo_publicidade_midia_referencia r
        JOIN arquivo_publicidade_versao sv ON sv.id = r.versao_id
        JOIN arquivo_publicidade_veiculacao sj ON sj.id = sv.veiculacao_id
        JOIN arquivo_publicidade_midia om ON om.id = r.origem_midia_id
        JOIN arquivo_publicidade_versao ov ON ov.id = om.versao_id
        JOIN arquivo_publicidade_veiculacao oj ON oj.id = ov.veiculacao_id
        JOIN anuncio_midia oam ON oam.id = om.anuncio_midia_id
        WHERE r.versao_id = :versao
        """ : """
        SELECT m.id AS origem_midia_id, m.arquivo_midia_id AS midia_id,
               m.variante, m.ordem,
               (j.modo_conteudo = 'ANUNCIO'
                 AND am.anuncio_id = j.anuncio_id
                 AND am.arquivo_midia_id = m.arquivo_midia_id) AS origem_valida
        FROM arquivo_publicidade_story_midia m
        JOIN arquivo_publicidade_story_versao v ON v.id = m.versao_id
        JOIN arquivo_publicidade_story_veiculacao j ON j.id = v.veiculacao_id
        LEFT JOIN anuncio_midia am ON am.id = m.anuncio_midia_id
        WHERE m.versao_id = :versao
        UNION ALL
        SELECT r.origem_midia_id, r.arquivo_midia_id AS midia_id,
               r.variante, r.ordem,
               (oj.story_id = sj.story_id
                 AND oj.modo_conteudo = 'ANUNCIO'
                 AND om.arquivo_midia_id = r.arquivo_midia_id
                 AND om.variante = r.variante
                 AND oam.anuncio_id = oj.anuncio_id
                 AND oam.arquivo_midia_id = om.arquivo_midia_id) AS origem_valida
        FROM arquivo_publicidade_story_midia_referencia r
        JOIN arquivo_publicidade_story_versao sv ON sv.id = r.versao_id
        JOIN arquivo_publicidade_story_veiculacao sj ON sj.id = sv.veiculacao_id
        JOIN arquivo_publicidade_story_midia om ON om.id = r.origem_midia_id
        JOIN arquivo_publicidade_story_versao ov ON ov.id = om.versao_id
        JOIN arquivo_publicidade_story_veiculacao oj ON oj.id = ov.veiculacao_id
        LEFT JOIN anuncio_midia oam ON oam.id = om.anuncio_midia_id
        WHERE r.versao_id = :versao
        """;
    List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("versao", fonteVersaoId));
    Set<MidiaChave> requeridas = new HashSet<>();
    for (Map<String, Object> midia : selecionadas) {
      Object id = midia.get(tipo.jsonMediaId);
      Object ordem = midia.get("ordem");
      if (!(id instanceof String || id instanceof UUID) || !(ordem instanceof Number)) {
        throw new IllegalStateException("referencia de midia incompleta em evidencia preservada");
      }
      requeridas.add(new MidiaChave(UUID.fromString(String.valueOf(id)),
          ((Number) ordem).intValue()));
    }
    List<MidiaOrigem> origens = new ArrayList<>();
    Set<String> vistas = new HashSet<>();
    Set<MidiaChave> originais = new HashSet<>();
    for (Map<String, Object> row : rows) {
      MidiaChave chave = new MidiaChave((UUID) row.get("midia_id"),
          ((Number) row.get("ordem")).intValue());
      if (!requeridas.contains(chave)) continue;
      if (!Boolean.TRUE.equals(row.get("origem_valida"))) {
        throw new IllegalStateException("origem privada nao corresponde a entidade e midia: "
            + fonteVersaoId);
      }
      String variante = (String) row.get("variante");
      String identidade = chave + "/" + variante;
      if (!vistas.add(identidade)) {
        throw new IllegalStateException("referencia privada ambigua na versao fonte: " + fonteVersaoId);
      }
      if ("ORIGINAL".equals(variante)) originais.add(chave);
      origens.add(new MidiaOrigem((UUID) row.get("origem_midia_id"),
          chave.id(), variante, chave.ordem()));
    }
    if (!originais.containsAll(requeridas)) {
      throw new IllegalStateException("versao fonte sem bytes originais verificados: " + fonteVersaoId);
    }
    return List.copyOf(origens);
  }

  private void inserirPlano(Tipo tipo, UUID sujeitoId, PlanoNovo plano, OffsetDateTime planejadoEm) {
    UUID id = UUID.randomUUID();
    Map<String, Object> p = new HashMap<>();
    p.put("id", id);
    p.put("sujeito", sujeitoId);
    p.put("janela", plano.fonte().janelaId());
    p.put("origem", plano.fonte().versaoId());
    p.put("hashOrigem", plano.fonte().sha256());
    p.put("fronteira", plano.fronteira());
    p.put("planejado", planejadoEm);
    p.put("conteudo", plano.conteudoProjetado());
    p.put("dependencias", plano.dependencias());
    p.put("hashPlano", plano.hash());
    jdbc.update("""
        INSERT INTO %s (
          id, %s, veiculacao_id, evidencia_versao_id, evidencia_sha256,
          fronteira_em, planejado_em, estado, conteudo_projetado_json,
          dependencias_json, plano_sha256)
        VALUES (:id, :sujeito, :janela, :origem, :hashOrigem,
          :fronteira, :planejado, 'PENDENTE', CAST(:conteudo AS jsonb),
          CAST(:dependencias AS jsonb), :hashPlano)
        """.formatted(tipo.planTable, tipo.subjectColumn), p);
    for (MidiaOrigem midia : plano.midias()) {
      jdbc.update("""
          INSERT INTO %s (plano_id, origem_midia_id, %s, variante, ordem)
          VALUES (:plano, :origem, :midia, :variante, :ordem)
          """.formatted(tipo.planMediaTable, tipo.mediaIdColumn), Map.of(
              "plano", id, "origem", midia.origemId(), "midia", midia.midiaId(),
              "variante", midia.variante(), "ordem", midia.ordem()));
    }
  }

  private void processar(Tipo tipo, UUID sujeitoId, OffsetDateTime ate) {
    Objects.requireNonNull(sujeitoId, "sujeitoId");
    Objects.requireNonNull(ate, "ate");
    // The writer's observed instant comes from PostgreSQL clock_timestamp(). A
    // slower JVM clock must not leave an already due plan pending and roll back
    // the following reconciliation under the same subject lock.
    OffsetDateTime processamento = agoraBanco();
    OffsetDateTime limite = ate.isBefore(processamento) ? ate : processamento;
    List<Map<String, Object>> planos = jdbc.queryForList("""
        SELECT id, veiculacao_id, evidencia_versao_id, evidencia_sha256,
               fronteira_em, planejado_em,
               conteudo_projetado_json::text AS conteudo_projetado_json
        FROM %s WHERE %s = :id AND estado = 'PENDENTE' AND fronteira_em <= :ate
        ORDER BY fronteira_em, id FOR UPDATE
        """.formatted(tipo.planTable, tipo.subjectColumn),
        Map.of("id", sujeitoId, "ate", limite));
    for (Map<String, Object> plano : planos) {
      processarPlano(tipo, plano, processamento);
    }
  }

  private void processarPlano(Tipo tipo, Map<String, Object> plano, OffsetDateTime processamento) {
    UUID planoId = (UUID) plano.get("id");
    UUID janelaId = (UUID) plano.get("veiculacao_id");
    UUID fonteId = (UUID) plano.get("evidencia_versao_id");
    OffsetDateTime fronteira = instanteJdbc(plano.get("fronteira_em"));
    Map<String, Object> janela = unica("""
        SELECT fim_em, anuncio_id, %s AS sujeito_id FROM %s WHERE id = :id FOR UPDATE
        """.formatted(tipo.subjectColumn, tipo.windowTable), Map.of("id", janelaId));
    if (janela == null || (janela.get("fim_em") != null
        && !instanteJdbc(janela.get("fim_em")).isAfter(fronteira))) {
      cancelar(tipo, planoId, processamento);
      return;
    }
    Map<String, Object> fonte = unica("""
        SELECT conteudo_sha256, vigente_desde, contratante_json::text AS contratante_json,
               comercial_json::text AS comercial_json,
               segmentacao_json::text AS segmentacao_json,
               alcance_json::text AS alcance_json
        FROM %s WHERE id = :id
        """.formatted(tipo.versionTable), Map.of("id", fonteId));
    if (fonte == null || !Objects.equals(fonte.get("conteudo_sha256"), plano.get("evidencia_sha256"))) {
      throw new IllegalStateException("evidencia temporal de origem alterada: " + fonteId);
    }
    Map<String, Object> atual = unica("""
        SELECT id, numero, conteudo_json::text AS conteudo_json,
               conteudo_sha256, vigente_desde, vigente_ate,
               evidencia_origem_versao_id
        FROM %s WHERE veiculacao_id = :janela
        ORDER BY numero DESC LIMIT 1 FOR UPDATE
        """.formatted(tipo.versionTable), Map.of("janela", janelaId));
    if (atual == null) throw new IllegalStateException("janela sem versao: " + janelaId);
    OffsetDateTime inicioAtual = instanteJdbc(atual.get("vigente_desde"));
    if (inicioAtual.isAfter(fronteira)) {
      throw new IllegalStateException("edicao posterior precedeu transicao pendente: " + janelaId);
    }
    UUID atualId = (UUID) atual.get("id");
    UUID evidenciaAtual = (UUID) atual.get("evidencia_origem_versao_id");
    if (!atualId.equals(fonteId) && !fonteId.equals(evidenciaAtual)) {
      cancelar(tipo, planoId, processamento);
      return;
    }
    OffsetDateTime fimAtual = instanteNullable(atual.get("vigente_ate"));
    if (fimAtual != null && !fimAtual.isAfter(fronteira)) {
      cancelar(tipo, planoId, processamento);
      return;
    }
    Map<String, Object> descontinuidade = descontinuidadeSemCaptura(janela, fonte, fronteira);
    if (descontinuidade != null) {
      registrarLacunaECancelar(tipo, planoId, fonteId, janela, fronteira, processamento,
          descontinuidade);
      return;
    }
    Map<String, Object> projetado = lerMapa((String) plano.get("conteudo_projetado_json"));
    Map<String, Object> atualConteudo = lerMapa((String) atual.get("conteudo_json"));
    boolean midiasMudaram = !Objects.equals(atualConteudo.get("midias"), projetado.get("midias"));
    @SuppressWarnings("unchecked")
    Map<String, Object> derivacao = new LinkedHashMap<>(
        (Map<String, Object>) projetado.get("derivacaoTemporal"));
    derivacao.put("planejadoEm", instanteJdbc(plano.get("planejado_em")).toString());
    derivacao.put("processadoEm", processamento.toString());
    projetado.put("derivacaoTemporal", derivacao);
    String conteudo = json(projetado);
    String contratante = json(lerMapa((String) fonte.get("contratante_json")));
    String comercial = json(lerMapa((String) fonte.get("comercial_json")));
    String segmentacao = json(lerMapa((String) fonte.get("segmentacao_json")));
    String alcance = json(lerMapa((String) fonte.get("alcance_json")));
    String hash = sha256((conteudo + contratante + comercial + segmentacao + alcance)
        .getBytes(StandardCharsets.UTF_8));
    UUID resultado = null;
    if (midiasMudaram) {
      resultado = UUID.randomUUID();
      jdbc.update("""
          UPDATE %s SET vigente_ate = :fronteira WHERE id = :id
          """.formatted(tipo.versionTable), Map.of("fronteira", fronteira, "id", atualId));
      Map<String, Object> p = new HashMap<>();
      p.put("id", resultado);
      p.put("janela", janelaId);
      p.put("numero", ((Number) atual.get("numero")).intValue() + 1);
      p.put("fronteira", fronteira);
      p.put("limite", janela.get("fim_em"));
      p.put("processado", processamento);
      p.put("conteudo", conteudo);
      p.put("contratante", contratante);
      p.put("comercial", comercial);
      p.put("segmentacao", segmentacao);
      p.put("alcance", alcance);
      p.put("hash", hash);
      p.put("origem", fonteId);
      jdbc.update("""
          INSERT INTO %s (
            id, veiculacao_id, numero, vigente_desde, vigente_ate,
            capturado_em, motivo, request_id, conteudo_json, contratante_json,
            comercial_json, segmentacao_json, alcance_json, conteudo_sha256,
            evidencia_origem_versao_id)
          VALUES (:id, :janela, :numero, :fronteira, :limite,
            :processado, '%s', NULL, CAST(:conteudo AS jsonb),
            CAST(:contratante AS jsonb), CAST(:comercial AS jsonb),
            CAST(:segmentacao AS jsonb), CAST(:alcance AS jsonb), :hash, :origem)
          """.formatted(tipo.versionTable, DERIVED_REASON), p);
      for (Map<String, Object> midia : jdbc.queryForList("""
          SELECT origem_midia_id, %s AS midia_id, variante, ordem
          FROM %s WHERE plano_id = :id
          ORDER BY ordem, origem_midia_id
          """.formatted(tipo.mediaIdColumn, tipo.planMediaTable), Map.of("id", planoId))) {
        jdbc.update("""
            INSERT INTO %s (id, versao_id, origem_midia_id, %s, variante, ordem)
            VALUES (:id, :versao, :origem, :midia, :variante, :ordem)
            """.formatted(tipo.referenceTable, tipo.mediaIdColumn), Map.of(
                "id", UUID.randomUUID(), "versao", resultado,
                "origem", midia.get("origem_midia_id"), "midia", midia.get("midia_id"),
                "variante", midia.get("variante"), "ordem", midia.get("ordem")));
      }
    }
    Map<String, Object> fim = new HashMap<>();
    fim.put("id", planoId);
    fim.put("processado", processamento);
    fim.put("resultado", resultado);
    jdbc.update("""
        UPDATE %s SET estado = 'PROCESSADA', processado_em = :processado,
          resultado_versao_id = :resultado WHERE id = :id AND estado = 'PENDENTE'
        """.formatted(tipo.planTable), fim);
  }

  /**
   * An older runtime can withdraw and even reactivate a subject without closing
   * its archive. Current eligibility does not prove continuity at the boundary.
   * Only operational evidence after the frozen source is considered; a later
   * withdrawal does not invalidate an earlier, still-supported expiry.
   */
  private Map<String, Object> descontinuidadeSemCaptura(Map<String, Object> janela,
      Map<String, Object> fonte, OffsetDateTime fronteira) {
    Object anuncioId = janela.get("anuncio_id");
    if (anuncioId == null) return Map.of("motivo", "ANUNCIO_DA_FONTE_AUSENTE");
    Map<String, Object> parametros = Map.of("anuncio", anuncioId,
        "desde", instanteJdbc(fonte.get("vigente_desde")), "fronteira", fronteira);
    Map<String, Object> retirada = unica("""
        SELECT id, criado_em FROM anuncio_status_historico
        WHERE anuncio_id = :anuncio AND criado_em > :desde AND criado_em <= :fronteira
          AND status_novo <> 'PUBLICADO'
        ORDER BY criado_em, id LIMIT 1
        """, parametros);
    if (retirada != null) return evidenciaLacuna("ANUNCIO_STATUS_HISTORICO", retirada);
    Map<String, Object> anuncio = unica("SELECT status FROM anuncio WHERE id = :anuncio", parametros);
    if (anuncio == null || !"PUBLICADO".equals(anuncio.get("status"))) {
      Map<String, Object> primeiraPosterior = unica("""
          SELECT status_anterior FROM anuncio_status_historico
          WHERE anuncio_id = :anuncio AND criado_em > :fronteira
          ORDER BY criado_em, id LIMIT 1
          """, parametros);
      if (primeiraPosterior == null || !"PUBLICADO".equals(primeiraPosterior.get("status_anterior"))) {
        // An observation cannot supply a missing historical timestamp. Retain
        // the source/window unchanged and explicitly refuse this derivation.
        return Map.of("motivo", "ESTADO_INELEGIVEL_SEM_HISTORICO_SUFICIENTE");
      }
    }
    return null;
  }

  private Map<String, Object> evidenciaLacuna(String origem, Map<String, Object> evidencia) {
    return Map.of("motivo", "RETIRADA_SEM_CAPTURA_ENTRE_FONTE_E_FRONTEIRA",
        "origem", origem, "evidenciaId", evidencia.get("id").toString(),
        "evidenciaEm", instanteJdbc(evidencia.get("criado_em")).toString());
  }

  private void registrarLacunaECancelar(Tipo tipo, UUID planoId, UUID fonteId,
      Map<String, Object> janela, OffsetDateTime fronteira, OffsetDateTime processamento,
      Map<String, Object> evidencia) {
    cancelar(tipo, planoId, processamento);
    String lacuna = json(Map.of("estado", "LACUNA_DE_CAPTURA", "planoId", planoId.toString(),
        "evidenciaOrigemVersaoId", fonteId.toString(), "fronteiraPlanejadaEm", fronteira.toString(),
        "observadoEm", processamento.toString(), "evidencia", evidencia));
    jdbc.update("""
        INSERT INTO auditoria_evento(id,acao,recurso_tipo,recurso_id,depois_json,origem,resultado,criado_em)
        VALUES (:id, 'ARQUIVO_PUBLICIDADE_TRANSICAO_LACUNA', :tipo, :sujeito,
          CAST(:lacuna AS jsonb), 'SISTEMA', 'PENDENTE', :agora)
        """, Map.of("id", UUID.randomUUID(), "tipo", tipo == Tipo.ANUNCIO ? "ANUNCIO" : "STORY_ANUNCIO",
            "sujeito", janela.get("sujeito_id"), "lacuna", lacuna, "agora", processamento));
    LOG.warn("Derivacao temporal cancelada por lacuna de captura: tipo={}, plano={}", tipo, planoId);
  }

  private void cancelar(Tipo tipo, UUID planoId, OffsetDateTime agora) {
    jdbc.update("""
        UPDATE %s SET estado = 'CANCELADA', cancelado_em = :agora
        WHERE id = :id AND estado = 'PENDENTE'
        """.formatted(tipo.planTable), Map.of("id", planoId, "agora", agora));
  }

  private Map<String, Object> unica(String sql, Map<String, ?> params) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Map<String, Object> lerMapa(String json) {
    try {
      return mapper.readValue(json, JSON_MAP);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("JSON de evidencia temporal invalido", exception);
    }
  }

  private String json(Object valor) {
    try {
      return mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar transicao temporal", exception);
    }
  }

  private String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }

  private OffsetDateTime agoraBanco() {
    OffsetDateTime instante = jdbc.queryForObject(
        "SELECT clock_timestamp()", Map.of(), OffsetDateTime.class);
    if (instante == null) throw new IllegalStateException("relogio do banco indisponivel");
    return instante.withOffsetSameInstant(ZoneOffset.UTC);
  }

  private static OffsetDateTime instanteNullable(Object valor) {
    return valor == null ? null : instanteJdbc(valor);
  }

  private static OffsetDateTime instanteJdbc(Object valor) {
    if (valor instanceof OffsetDateTime instante) return instante;
    if (valor instanceof Timestamp instante) return instante.toInstant().atOffset(ZoneOffset.UTC);
    throw new IllegalStateException("instante temporal invalido");
  }

  record Direito(UUID ativacaoId, String codigo, OffsetDateTime fimEm) { }
  record Projecao(String conteudoJson, List<Map<String, Object>> midias) { }
  private record Fonte(UUID janelaId, UUID anuncioId, UUID versaoId, OffsetDateTime fimEm,
      String conteudoJson, String sha256) { }
  private record MidiaChave(UUID id, int ordem) { }
  private record MidiaOrigem(UUID origemId, UUID midiaId, String variante, int ordem) {
    String identidade() {
      return origemId + "/" + midiaId + "/" + variante + "/" + ordem;
    }
  }
  private record PlanoNovo(Fonte fonte, OffsetDateTime fronteira, String conteudoProjetado,
      String dependencias, String hash, List<MidiaOrigem> midias) { }

  private enum Tipo {
    ANUNCIO("anuncio_id", "anuncio", "arquivo_publicidade_veiculacao",
        "arquivo_publicidade_versao", "arquivo_publicidade_midia",
        "arquivo_publicidade_midia_referencia", "arquivo_publicidade_transicao_plano",
        "arquivo_publicidade_transicao_plano_midia", "anuncio_midia_id", "anuncioMidiaId"),
    STORY("story_id", "story_anuncio", "arquivo_publicidade_story_veiculacao",
        "arquivo_publicidade_story_versao", "arquivo_publicidade_story_midia",
        "arquivo_publicidade_story_midia_referencia", "arquivo_publicidade_story_transicao_plano",
        "arquivo_publicidade_story_transicao_plano_midia", "arquivo_midia_id", "arquivoMidiaId");

    private final String subjectColumn;
    private final String lockTable;
    private final String windowTable;
    private final String versionTable;
    private final String mediaTable;
    private final String referenceTable;
    private final String planTable;
    private final String planMediaTable;
    private final String mediaIdColumn;
    private final String jsonMediaId;

    Tipo(String subjectColumn, String lockTable, String windowTable, String versionTable,
        String mediaTable, String referenceTable, String planTable, String planMediaTable,
        String mediaIdColumn, String jsonMediaId) {
      this.subjectColumn = subjectColumn;
      this.lockTable = lockTable;
      this.windowTable = windowTable;
      this.versionTable = versionTable;
      this.mediaTable = mediaTable;
      this.referenceTable = referenceTable;
      this.planTable = planTable;
      this.planMediaTable = planMediaTable;
      this.mediaIdColumn = mediaIdColumn;
      this.jsonMediaId = jsonMediaId;
    }
  }
}
