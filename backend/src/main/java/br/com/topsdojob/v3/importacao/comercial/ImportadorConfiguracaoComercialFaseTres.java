package br.com.topsdojob.v3.importacao.comercial;

import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.Decisao;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.LinhaBeneficio;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.LinhaPacote;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.BeneficioLegado;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.ConfiguracaoStoryLegada;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.OpcaoBeneficioLegada;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.PacoteCreditoLegado;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.Snapshot;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ImportadorConfiguracaoComercialFaseTres {
  private static final String ORIGEM = "TOPSDOJOB_LEGADO";
  private static final String TABELA_BENEFICIO = "feature_catalogo";
  private static final String TABELA_OPCAO = "feature_catalogo_duracoes";
  private static final String TABELA_PACOTE = "planos_credito";
  private static final String TABELA_STORY = "feature_catalogo_stories";
  private static final String STAGE_PREMIUM = "stg_premium";
  private static final String STAGE_CREDITO = "stg_credito";
  private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999.99");
  private static final UUID STORIES_BENEFICIO_ID =
      UUID.fromString("f3000000-0000-4000-8000-000000000007");
  private static final Map<String, UUID> BENEFICIOS_IDS = Map.of(
      "OCULTAR_IDADE", UUID.fromString("f3000000-0000-4000-8000-000000000001"),
      "FOTOS_EXTRA_5", UUID.fromString("f3000000-0000-4000-8000-000000000002"),
      "ANUNCIO_TOPO", UUID.fromString("f3000000-0000-4000-8000-000000000003"),
      "WHATSAPP_CARD", UUID.fromString("f3000000-0000-4000-8000-000000000004"),
      "CARROSSEL_FOTOS", UUID.fromString("f3000000-0000-4000-8000-000000000005"),
      "VIDEO_1", UUID.fromString("f3000000-0000-4000-8000-000000000006"),
      "STORIES", STORIES_BENEFICIO_ID);
  private static final Map<String, UUID> PACOTES_IDS = Map.of(
      "PACOTE_50", UUID.fromString("f5000000-0000-4000-8000-000000000001"),
      "PACOTE_100", UUID.fromString("f5000000-0000-4000-8000-000000000002"),
      "PACOTE_250", UUID.fromString("f5000000-0000-4000-8000-000000000003"));

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacao;
  private final ObjectMapper objectMapper;
  private final ObjectMapper hashMapper;
  private final MatrizMapeamentoComercialImportacao matriz;
  private final SanitizadorDescricaoComercialLegada sanitizador;

  public ImportadorConfiguracaoComercialFaseTres(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      ObjectMapper objectMapper,
      MatrizMapeamentoComercialImportacao matriz,
      SanitizadorDescricaoComercialLegada sanitizador) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transacao = new TransactionTemplate(transactionManager);
    this.objectMapper = objectMapper;
    this.hashMapper = objectMapper.copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.matriz = matriz;
    this.sanitizador = sanitizador;
  }

  public ResultadoImportacaoConfiguracaoComercial executar(Snapshot snapshot, int limite) {
    Objects.requireNonNull(snapshot, "snapshot deve ser informado");
    if (limite < 0) {
      throw new IllegalArgumentException("limite nao pode ser negativo");
    }
    if (!usuarioExiste(snapshot.atorSistemaV3Id())) {
      throw new IllegalStateException("Ator de sistema da importacao nao existe na V3");
    }
    UUID execucaoId = uuid("execucao-configuracao-comercial", snapshot.snapshotId());
    transacao.executeWithoutResult(status -> preparar(snapshot, execucaoId));

    List<ChaveEntrada> pendentes = limite == 0 ? List.of() : jdbc.query(
        """
        SELECT stage_tabela, tabela_origem, id_origem
        FROM (
          SELECT 'stg_premium' AS stage_tabela, tabela_origem, id_origem
          FROM stg_premium
          WHERE execucao_id = ? AND status = 'PENDENTE'
          UNION ALL
          SELECT 'stg_credito' AS stage_tabela, tabela_origem, id_origem
          FROM stg_credito
          WHERE execucao_id = ? AND status = 'PENDENTE'
        ) pendentes
        ORDER BY
          CASE tabela_origem
            WHEN 'feature_catalogo' THEN 1
            WHEN 'feature_catalogo_duracoes' THEN 2
            WHEN 'planos_credito' THEN 3
            WHEN 'feature_catalogo_stories' THEN 4
            ELSE 5
          END,
          tabela_origem,
          id_origem
        LIMIT ?
        """,
        (rs, rowNum) -> new ChaveEntrada(
            rs.getString("stage_tabela"),
            rs.getString("tabela_origem"),
            rs.getString("id_origem")),
        execucaoId,
        execucaoId,
        limite);

    Map<ChaveEntrada, Entrada> porChave = new LinkedHashMap<>();
    entradas(snapshot).forEach(entrada -> porChave.put(entrada.chave(), entrada));
    ContadoresChamada contadores = new ContadoresChamada();
    for (ChaveEntrada chave : pendentes) {
      Entrada entrada = porChave.get(chave);
      if (entrada == null) {
        throw new IllegalStateException("Staging sem origem no snapshot atual");
      }
      Processamento resultado = transacao.execute(
          status -> processar(snapshot, execucaoId, entrada));
      if (resultado != null) {
        contadores.processados++;
        contadores.novos += resultado.novos();
      }
    }
    return transacao.execute(status -> finalizar(snapshot, execucaoId, contadores));
  }

  private void preparar(Snapshot snapshot, UUID execucaoId) {
    String fingerprint = hash(snapshot);
    jdbc.update(
        """
        INSERT INTO importacao_execucao (
          id, sistema_origem, status, iniciado_em, solicitado_por, resumo_json, criado_em
        ) VALUES (?, ?, 'EM_EXECUCAO', ?, ?, jsonb_build_object('snapshotFingerprint', ?), ?)
        ON CONFLICT (id) DO NOTHING
        """,
        execucaoId,
        ORIGEM,
        snapshot.capturadoEm(),
        snapshot.atorSistemaV3Id(),
        fingerprint,
        snapshot.capturadoEm());
    String existente = jdbc.queryForObject(
        "SELECT resumo_json ->> 'snapshotFingerprint' FROM importacao_execucao WHERE id = ?",
        String.class,
        execucaoId);
    if (existente != null && !fingerprint.equals(existente)) {
      throw new IllegalStateException("Snapshot alterado durante uma execucao existente");
    }
    entradas(snapshot).forEach(entrada -> prepararStage(snapshot, execucaoId, entrada));
    jdbc.update(
        "UPDATE importacao_execucao SET status = 'EM_EXECUCAO', finalizado_em = NULL WHERE id = ?",
        execucaoId);
  }

  private void prepararStage(Snapshot snapshot, UUID execucaoId, Entrada entrada) {
    String hashOrigem = hash(entrada.dados());
    List<String> existente = jdbc.query(
        "SELECT hash_origem FROM " + entrada.stageTabela()
            + " WHERE execucao_id = ? AND sistema_origem = ?"
            + " AND tabela_origem = ? AND id_origem = ?",
        (rs, rowNum) -> rs.getString(1),
        execucaoId,
        ORIGEM,
        entrada.tabelaOrigem(),
        entrada.idOrigem());
    if (!existente.isEmpty() && !Objects.equals(existente.get(0), hashOrigem)) {
      throw new IllegalStateException("Fonte diverge do staging existente");
    }
    jdbc.update(
        """
        INSERT INTO %s (
          id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
          payload_normalizado_json, status, criado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDENTE', ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem) DO NOTHING
        """.formatted(entrada.stageTabela()),
        uuid("stage-configuracao-comercial", snapshot.snapshotId(),
            entrada.tabelaOrigem(), entrada.idOrigem()),
        execucaoId,
        ORIGEM,
        entrada.tabelaOrigem(),
        entrada.idOrigem(),
        hashOrigem,
        json(payloadSeguro(entrada)),
        snapshot.capturadoEm());
  }

  private Map<String, Object> payloadSeguro(Entrada entrada) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tipo", entrada.tipo().name());
    payload.put("tabelaOrigem", entrada.tabelaOrigem());
    payload.put("fingerprint", hash(entrada.dados()));
    Object dados = entrada.dados();
    if (dados instanceof BeneficioLegado beneficio) {
      payload.put("codigo", beneficio.codigo());
      payload.put("escopo", beneficio.escopo());
      payload.put("ativo", beneficio.ativo());
      payload.put("ordemExibicao", beneficio.ordemExibicao());
    } else if (dados instanceof OpcaoBeneficioLegada opcao) {
      payload.put("beneficioOrigemId", opcao.beneficioOrigemId());
      payload.put("duracaoDias", opcao.duracaoDias());
      payload.put("custoCreditos", opcao.custoCreditos());
      payload.put("precoReferencia", opcao.precoReferencia());
      payload.put("ativo", opcao.ativo());
      payload.put("ordemExibicao", opcao.ordemExibicao());
    } else if (dados instanceof PacoteCreditoLegado pacote) {
      payload.put("codigoLegado", pacote.codigoLegado());
      payload.put("quantidadeCreditosBase", pacote.quantidadeCreditosBase());
      payload.put("bonusCreditos", pacote.bonusCreditos());
      payload.put("quantidadeCreditosTotal", totalCreditosSeguro(pacote));
      payload.put("valor", pacote.valor());
      payload.put("moeda", pacote.moeda());
      payload.put("ativo", pacote.ativo());
      payload.put("ordemExibicao", pacote.ordemExibicao());
      payload.put("validadeDias", pacote.validadeDias());
    } else if (dados instanceof ConfiguracaoStoryLegada story) {
      payload.put("custoCreditos", story.custoCreditos());
      payload.put("ativo", story.ativo());
      payload.put("ordemExibicao", story.ordemExibicao());
      payload.put("duracaoHoras", story.duracaoHoras());
      payload.put("modos", story.modos());
    } else {
      throw new IllegalStateException("Tipo comercial nao suportado");
    }
    return payload;
  }

  private Processamento processar(Snapshot snapshot, UUID execucaoId, Entrada entrada) {
    List<String> estado = jdbc.query(
        "SELECT status FROM " + entrada.stageTabela()
            + " WHERE execucao_id = ? AND tabela_origem = ? AND id_origem = ? FOR UPDATE",
        (rs, rowNum) -> rs.getString(1),
        execucaoId,
        entrada.tabelaOrigem(),
        entrada.idOrigem());
    if (estado.isEmpty() || !"PENDENTE".equals(estado.get(0))) {
      return Processamento.vazio();
    }

    Object dados = entrada.dados();
    Processamento processamento;
    if (dados instanceof BeneficioLegado beneficio) {
      processamento = processarBeneficio(snapshot, beneficio);
    } else if (dados instanceof OpcaoBeneficioLegada opcao) {
      processamento = processarOpcao(snapshot, opcao);
    } else if (dados instanceof PacoteCreditoLegado pacote) {
      processamento = processarPacote(snapshot, pacote);
    } else if (dados instanceof ConfiguracaoStoryLegada story) {
      processamento = processarStory(snapshot, story);
    } else {
      throw new IllegalStateException("Tipo comercial nao suportado");
    }
    return concluirItem(snapshot, execucaoId, entrada, processamento);
  }

  private Processamento processarBeneficio(Snapshot snapshot, BeneficioLegado beneficio) {
    LinhaBeneficio linha = matriz.beneficio(beneficio.codigo()).orElse(null);
    if (linha == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_CODIGO_DESCONHECIDO);
    }
    if (linha.decisao() == Decisao.DESCARTAR) {
      return Processamento.descartado();
    }
    if (linha.decisao() == Decisao.QUARENTENA) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_CODIGO_DESCONHECIDO);
    }
    if (!escopoCompativel(beneficio, linha)) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_ESCOPO_INCOMPATIVEL);
    }
    if (!beneficioDonoDoCodigo(snapshot, beneficio, linha.codigoV3())) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_CODIGO_DUPLICADO);
    }
    if (linha.decisao() == Decisao.PRESERVAR_HISTORICO) {
      return Processamento.historico();
    }

    boolean ativo = linha.decisao() == Decisao.IMPORTAR_INATIVO
        ? false
        : beneficio.ativo();
    if (ativo && !linha.storiesSeparado()
        && !possuiOpcaoValida(snapshot, beneficio.idOrigem())) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_PRODUTO_SEM_OPCAO_VALIDA);
    }
    int ordem = ordemValida(beneficio.ordemExibicao(), linha.ordemCanonica());
    if (ordem < 0) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_ORDEM_INVALIDA);
    }

    UUID id = idBeneficio(linha.codigoV3());
    boolean novo = !existe("beneficio_premium", "codigo", linha.codigoV3());
    jdbc.update(
        """
        INSERT INTO beneficio_premium (
          id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
          ordem_exibicao, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (codigo) DO UPDATE SET
          nome = EXCLUDED.nome,
          descricao = EXCLUDED.descricao,
          escopo = EXCLUDED.escopo,
          afeta_ranking = EXCLUDED.afeta_ranking,
          ativo = EXCLUDED.ativo,
          ordem_exibicao = EXCLUDED.ordem_exibicao,
          atualizado_em = EXCLUDED.atualizado_em
        """,
        id,
        linha.codigoV3(),
        linha.nomeCanonico(),
        linha.descricaoCanonica(),
        linha.escopo().name(),
        linha.afetaRanking(),
        ativo,
        ordem,
        snapshot.capturadoEm(),
        snapshot.capturadoEm());
    UUID persistido = jdbc.queryForObject(
        "SELECT id FROM beneficio_premium WHERE codigo = ?",
        UUID.class,
        linha.codigoV3());
    return Processamento.importado(novo ? 1 : 0, persistido);
  }

  private Processamento processarOpcao(Snapshot snapshot, OpcaoBeneficioLegada opcao) {
    BeneficioLegado beneficio = beneficioPorOrigem(snapshot, opcao.beneficioOrigemId());
    if (beneficio == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_BENEFICIO_ORFAO);
    }
    LinhaBeneficio linha = matriz.beneficio(beneficio.codigo()).orElse(null);
    if (linha == null || !escopoCompativel(beneficio, linha)) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_BENEFICIO_ORFAO);
    }
    if (linha.decisao() == Decisao.DESCARTAR
        || linha.decisao() == Decisao.PRESERVAR_HISTORICO) {
      return Processamento.descartado();
    }
    if (linha.storiesSeparado()) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_DURACAO_INCOMPATIVEL);
    }
    if (!beneficioDonoDoCodigo(snapshot, beneficio, linha.codigoV3())) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_CODIGO_DUPLICADO);
    }
    if (!matriz.duracaoPremiumCanonica(opcao.duracaoDias())) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_DURACAO_INCOMPATIVEL);
    }
    if (!opcaoDonaDaDuracao(snapshot, opcao, linha.codigoV3())) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_CODIGO_DUPLICADO);
    }
    if (opcao.custoCreditos() == null || opcao.custoCreditos() < 0
        || (beneficio.ativo() && opcao.ativo() && opcao.custoCreditos() == 0)) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_CREDITOS_INVALIDOS);
    }
    BigDecimal preco = monetarioOpcional(opcao.precoReferencia());
    if (opcao.precoReferencia() != null && preco == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_VALOR_INVALIDO);
    }
    int ordem = ordemValida(opcao.ordemExibicao(), ordemDuracao(opcao.duracaoDias()));
    if (ordem < 0) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_ORDEM_INVALIDA);
    }

    UUID beneficioId = idPersistidoBeneficio(linha.codigoV3());
    if (beneficioId == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_BENEFICIO_ORFAO);
    }
    boolean novo = jdbc.queryForObject(
        """
        SELECT count(*) = 0 FROM beneficio_premium_opcao
        WHERE beneficio_id = ? AND duracao_dias = ? AND versao_regra = 1
        """,
        Boolean.class,
        beneficioId,
        opcao.duracaoDias());
    UUID id = uuid("opcao-configuracao-comercial", linha.codigoV3(),
        Integer.toString(opcao.duracaoDias()));
    jdbc.update(
        """
        INSERT INTO beneficio_premium_opcao (
          id, beneficio_id, duracao_dias, custo_creditos, preco_referencia,
          versao_regra, ativo, vigencia_inicio_em, vigencia_fim_em,
          ordem_exibicao, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, 1, ?, NULL, NULL, ?, ?, ?)
        ON CONFLICT (beneficio_id, duracao_dias, versao_regra) DO UPDATE SET
          custo_creditos = EXCLUDED.custo_creditos,
          preco_referencia = EXCLUDED.preco_referencia,
          ativo = EXCLUDED.ativo,
          ordem_exibicao = EXCLUDED.ordem_exibicao,
          atualizado_em = EXCLUDED.atualizado_em
        """,
        id,
        beneficioId,
        opcao.duracaoDias(),
        opcao.custoCreditos(),
        preco,
        beneficio.ativo() && opcao.ativo(),
        ordem,
        snapshot.capturadoEm(),
        snapshot.capturadoEm());
    UUID persistido = jdbc.queryForObject(
        """
        SELECT id FROM beneficio_premium_opcao
        WHERE beneficio_id = ? AND duracao_dias = ? AND versao_regra = 1
        """,
        UUID.class,
        beneficioId,
        opcao.duracaoDias());
    return Processamento.importado(novo ? 1 : 0, persistido);
  }

  private Processamento processarPacote(Snapshot snapshot, PacoteCreditoLegado pacote) {
    LinhaPacote linha = matriz.pacote(pacote.codigoLegado()).orElse(null);
    if (linha == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_CODIGO_DESCONHECIDO);
    }
    if (linha.decisao() == Decisao.DESCARTAR) {
      return Processamento.descartado();
    }
    if (linha.decisao() == Decisao.QUARENTENA) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_CODIGO_DESCONHECIDO);
    }
    if (!pacoteDono(snapshot, pacote, linha)) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_DUPLICADO);
    }
    if (pacote.validadeDias() != null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_VALIDADE_NAO_SUPORTADA);
    }

    Integer total = totalCreditos(pacote);
    if (total == null || total <= 0 || total > 1_000_000) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_QUANTIDADE_INVALIDA);
    }
    BigDecimal valor = monetarioPacote(pacote.valor(), pacote.ativo());
    if (valor == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_PRECO_INVALIDO);
    }
    if (!"BRL".equals(normalizarMoeda(pacote.moeda()))) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_MOEDA_INVALIDA);
    }
    int ordem = ordemValida(pacote.ordemExibicao(), linha.ordemCanonica());
    if (ordem < 0) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONFIG_COMERCIAL_ORDEM_INVALIDA);
    }
    String nome = sanitizador.sanitizar(pacote.nome(), 120);
    String descricao = sanitizador.sanitizar(pacote.descricao(), 500);
    if (nome.length() < 2) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PACOTE_COMERCIAL_CODIGO_DESCONHECIDO);
    }

    UUID id = idPacote(linha.codigoV3());
    boolean novo = !existe("plano_credito", "codigo", linha.codigoV3());
    jdbc.update(
        """
        INSERT INTO plano_credito (
          id, codigo, nome, quantidade_creditos, valor, moeda, ativo,
          descricao, ordem_exibicao, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, 'BRL', ?, ?, ?, ?, ?)
        ON CONFLICT (codigo) DO UPDATE SET
          nome = EXCLUDED.nome,
          quantidade_creditos = EXCLUDED.quantidade_creditos,
          valor = EXCLUDED.valor,
          moeda = EXCLUDED.moeda,
          ativo = EXCLUDED.ativo,
          descricao = EXCLUDED.descricao,
          ordem_exibicao = EXCLUDED.ordem_exibicao,
          atualizado_em = EXCLUDED.atualizado_em
        """,
        id,
        linha.codigoV3(),
        nome,
        total,
        valor,
        linha.decisao() == Decisao.IMPORTAR_INATIVO ? false : pacote.ativo(),
        descricao,
        ordem,
        snapshot.capturadoEm(),
        snapshot.capturadoEm());
    UUID persistido = jdbc.queryForObject(
        "SELECT id FROM plano_credito WHERE codigo = ?",
        UUID.class,
        linha.codigoV3());
    return Processamento.importado(novo ? 1 : 0, persistido);
  }

  private Processamento processarStory(
      Snapshot snapshot,
      ConfiguracaoStoryLegada story) {
    if (!storyDona(snapshot, story)) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.STORY_CONFIGURACAO_INCOMPATIVEL);
    }
    if (story.custoCreditos() == null
        || story.custoCreditos() < 0
        || story.custoCreditos() > 1_000_000
        || !Objects.equals(story.duracaoHoras(), 24)
        || !modosStoryValidos(story.modos())
        || ordemValida(story.ordemExibicao(), 0) < 0) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.STORY_CONFIGURACAO_INCOMPATIVEL);
    }

    boolean identidadeNova = !existe("beneficio_premium", "codigo", "STORIES");
    garantirIdentidadeStory(snapshot);
    boolean configuracaoNova = jdbc.queryForObject(
        "SELECT count(*) = 0 FROM story_configuracao_comercial WHERE id = 1",
        Boolean.class);
    jdbc.update(
        """
        INSERT INTO story_configuracao_comercial (
          id, ativo, custo_creditos, versao, atualizado_em, atualizado_por
        ) VALUES (1, ?, ?, 0, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
          ativo = EXCLUDED.ativo,
          custo_creditos = EXCLUDED.custo_creditos,
          atualizado_em = EXCLUDED.atualizado_em,
          atualizado_por = EXCLUDED.atualizado_por
        """,
        story.ativo(),
        story.custoCreditos(),
        snapshot.capturadoEm(),
        snapshot.atorSistemaV3Id());
    return Processamento.importado(
        (identidadeNova ? 1 : 0) + (configuracaoNova ? 1 : 0),
        uuid("story-configuracao-comercial"));
  }

  private void garantirIdentidadeStory(Snapshot snapshot) {
    jdbc.update(
        """
        INSERT INTO beneficio_premium (
          id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
          ordem_exibicao, criado_em, atualizado_em
        ) VALUES (?, 'STORIES', 'Stories',
          'Identidade tecnica para ledger e ativacoes de Stories',
          'ANUNCIO', false, true, 0, ?, ?)
        ON CONFLICT (codigo) DO UPDATE SET
          nome = EXCLUDED.nome,
          descricao = EXCLUDED.descricao,
          escopo = EXCLUDED.escopo,
          afeta_ranking = EXCLUDED.afeta_ranking,
          ordem_exibicao = EXCLUDED.ordem_exibicao,
          atualizado_em = EXCLUDED.atualizado_em
        """,
        STORIES_BENEFICIO_ID,
        snapshot.capturadoEm(),
        snapshot.capturadoEm());
  }

  private Processamento concluirItem(
      Snapshot snapshot,
      UUID execucaoId,
      Entrada entrada,
      Processamento processamento) {
    String statusMapeamento = switch (processamento.statusStage()) {
      case "PROCESSADO" -> "MAPEADO";
      case "REJEITADO" -> "REJEITADO";
      default -> "DIVERGENTE";
    };
    mapear(
        execucaoId,
        entrada.tabelaOrigem(),
        entrada.idOrigem(),
        hash(entrada.dados()),
        entrada.tipo(),
        processamento.entidadeId(),
        statusMapeamento,
        snapshot.capturadoEm());
    processamento.pendencias().stream().distinct().forEach(codigo -> pendencia(
        execucaoId,
        codigo,
        entrada.tipo(),
        entrada.idOrigem(),
        snapshot.capturadoEm()));
    jdbc.update(
        """
        UPDATE %s
        SET status = ?, pendencia_codigo = ?, entidade_v3_id = ?, processado_em = ?
        WHERE execucao_id = ? AND tabela_origem = ? AND id_origem = ?
        """.formatted(entrada.stageTabela()),
        processamento.statusStage(),
        processamento.pendencias().isEmpty()
            ? null
            : processamento.pendencias().get(0).name(),
        processamento.entidadeId(),
        snapshot.capturadoEm(),
        execucaoId,
        entrada.tabelaOrigem(),
        entrada.idOrigem());
    return processamento;
  }

  private ResultadoImportacaoConfiguracaoComercial finalizar(
      Snapshot snapshot,
      UUID execucaoId,
      ContadoresChamada chamada) {
    long restantes = pendentes(execucaoId);
    long pendenciasAbertas = jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia WHERE execucao_id = ? AND status = 'ABERTA'",
        Long.class,
        execucaoId);
    String status = restantes > 0
        ? "EM_EXECUCAO"
        : (pendenciasAbertas > 0 ? "CONCLUIDA_COM_PENDENCIAS" : "CONCLUIDA");

    long produtosAtivos = contarDestino(execucaoId, TABELA_BENEFICIO,
        "MAPEADO", "beneficio_premium", true);
    long produtosInativos = contarDestino(execucaoId, TABELA_BENEFICIO,
        "MAPEADO", "beneficio_premium", false);
    long produtosHistoricos = contarMapeamentosSemDestino(
        execucaoId, TABELA_BENEFICIO, "MAPEADO");
    long produtosDescartados = contarMapeamentos(
        execucaoId, TABELA_BENEFICIO, "REJEITADO");
    long produtosQuarentena = contarMapeamentos(
        execucaoId, TABELA_BENEFICIO, "DIVERGENTE");
    long opcoesImportadas = contarMapeamentos(execucaoId, TABELA_OPCAO, "MAPEADO");
    long opcoesQuarentena = contarMapeamentos(execucaoId, TABELA_OPCAO, "DIVERGENTE");
    long pacotesAtivos = contarDestino(execucaoId, TABELA_PACOTE,
        "MAPEADO", "plano_credito", true);
    long pacotesInativos = contarDestino(execucaoId, TABELA_PACOTE,
        "MAPEADO", "plano_credito", false);
    long pacotesDescartados = contarMapeamentos(
        execucaoId, TABELA_PACOTE, "REJEITADO");
    long pacotesQuarentena = contarMapeamentos(
        execucaoId, TABELA_PACOTE, "DIVERGENTE");
    long storiesImportadas = contarMapeamentos(execucaoId, TABELA_STORY, "MAPEADO");
    long storiesQuarentena = contarMapeamentos(execucaoId, TABELA_STORY, "DIVERGENTE");

    Map<String, Object> resumo = new LinkedHashMap<>();
    resumo.put("snapshotFingerprint", hash(snapshot));
    resumo.put("snapshotId", snapshot.snapshotId());
    resumo.put("produtosAnalisados", snapshot.beneficios().size());
    resumo.put("produtosImportadosAtivos", produtosAtivos);
    resumo.put("produtosImportadosInativos", produtosInativos);
    resumo.put("produtosHistoricos", produtosHistoricos);
    resumo.put("produtosDescartados", produtosDescartados);
    resumo.put("produtosQuarentena", produtosQuarentena);
    resumo.put("opcoesAnalisadas", snapshot.opcoes().size());
    resumo.put("opcoesImportadas", opcoesImportadas);
    resumo.put("opcoesQuarentena", opcoesQuarentena);
    resumo.put("pacotesAnalisados", snapshot.pacotes().size());
    resumo.put("pacotesImportadosAtivos", pacotesAtivos);
    resumo.put("pacotesImportadosInativos", pacotesInativos);
    resumo.put("pacotesDescartados", pacotesDescartados);
    resumo.put("pacotesQuarentena", pacotesQuarentena);
    resumo.put("storiesAnalisadas", snapshot.configuracoesStory().size());
    resumo.put("storiesImportadas", storiesImportadas);
    resumo.put("storiesQuarentena", storiesQuarentena);
    resumo.put("restantes", restantes);
    jdbc.update(
        """
        UPDATE importacao_execucao
        SET status = ?, finalizado_em = ?, resumo_json = ?::jsonb
        WHERE id = ?
        """,
        status,
        restantes == 0 ? snapshot.capturadoEm() : null,
        json(resumo),
        execucaoId);

    return new ResultadoImportacaoConfiguracaoComercial(
        execucaoId,
        status,
        snapshot.beneficios().size(),
        produtosAtivos,
        produtosInativos,
        produtosHistoricos,
        produtosDescartados,
        produtosQuarentena,
        snapshot.opcoes().size(),
        opcoesImportadas,
        opcoesQuarentena,
        snapshot.pacotes().size(),
        pacotesAtivos,
        pacotesInativos,
        pacotesDescartados,
        pacotesQuarentena,
        snapshot.configuracoesStory().size(),
        storiesImportadas,
        storiesQuarentena,
        chamada.processados,
        chamada.novos,
        restantes);
  }

  private List<Entrada> entradas(Snapshot snapshot) {
    List<Entrada> entradas = new ArrayList<>();
    snapshot.beneficios().forEach(item -> entradas.add(new Entrada(
        STAGE_PREMIUM,
        TABELA_BENEFICIO,
        item.idOrigem(),
        TipoEntidadeImportacao.BENEFICIO_COMERCIAL,
        item)));
    snapshot.opcoes().forEach(item -> entradas.add(new Entrada(
        STAGE_PREMIUM,
        TABELA_OPCAO,
        item.idOrigem(),
        TipoEntidadeImportacao.OPCAO_BENEFICIO_COMERCIAL,
        item)));
    snapshot.pacotes().forEach(item -> entradas.add(new Entrada(
        STAGE_CREDITO,
        TABELA_PACOTE,
        item.idOrigem(),
        TipoEntidadeImportacao.PACOTE_CREDITO_COMERCIAL,
        item)));
    snapshot.configuracoesStory().forEach(item -> entradas.add(new Entrada(
        STAGE_PREMIUM,
        TABELA_STORY,
        item.idOrigem(),
        TipoEntidadeImportacao.CONFIGURACAO_STORY_COMERCIAL,
        item)));
    return entradas.stream()
        .sorted(Comparator.comparing(Entrada::tabelaOrigem)
            .thenComparing(Entrada::idOrigem))
        .toList();
  }

  private boolean possuiOpcaoValida(Snapshot snapshot, String beneficioOrigemId) {
    return snapshot.opcoes().stream()
        .filter(opcao -> opcao.beneficioOrigemId().equals(beneficioOrigemId))
        .anyMatch(opcao -> matriz.duracaoPremiumCanonica(opcao.duracaoDias())
            && opcao.ativo()
            && opcao.custoCreditos() != null
            && opcao.custoCreditos() > 0
            && opcao.ordemExibicao() != null
            && opcao.ordemExibicao() >= 0
            && (opcao.precoReferencia() == null
                || monetarioOpcional(opcao.precoReferencia()) != null));
  }

  private boolean beneficioDonoDoCodigo(
      Snapshot snapshot,
      BeneficioLegado atual,
      String codigoV3) {
    return snapshot.beneficios().stream()
        .filter(item -> matriz.beneficio(item.codigo())
            .map(linha -> codigoV3.equals(linha.codigoV3()))
            .orElse(false))
        .map(BeneficioLegado::idOrigem)
        .min(String::compareTo)
        .filter(atual.idOrigem()::equals)
        .isPresent();
  }

  private boolean opcaoDonaDaDuracao(
      Snapshot snapshot,
      OpcaoBeneficioLegada atual,
      String codigoV3) {
    return snapshot.opcoes().stream()
        .filter(item -> Objects.equals(item.duracaoDias(), atual.duracaoDias()))
        .filter(item -> {
          BeneficioLegado beneficio = beneficioPorOrigem(snapshot, item.beneficioOrigemId());
          return beneficio != null && matriz.beneficio(beneficio.codigo())
              .map(linha -> codigoV3.equals(linha.codigoV3()))
              .orElse(false);
        })
        .map(OpcaoBeneficioLegada::idOrigem)
        .min(String::compareTo)
        .filter(atual.idOrigem()::equals)
        .isPresent();
  }

  private boolean pacoteDono(
      Snapshot snapshot,
      PacoteCreditoLegado atual,
      LinhaPacote linhaAtual) {
    String nomeAtual = sanitizador.sanitizar(atual.nome(), 120).toLowerCase(Locale.ROOT);
    return snapshot.pacotes().stream()
        .filter(item -> matriz.pacote(item.codigoLegado())
            .map(linha -> linha.codigoV3().equals(linhaAtual.codigoV3())
                || (!nomeAtual.isBlank()
                    && sanitizador.sanitizar(item.nome(), 120)
                        .equalsIgnoreCase(nomeAtual)))
            .orElse(false))
        .map(PacoteCreditoLegado::idOrigem)
        .min(String::compareTo)
        .filter(atual.idOrigem()::equals)
        .isPresent();
  }

  private boolean storyDona(Snapshot snapshot, ConfiguracaoStoryLegada atual) {
    return snapshot.configuracoesStory().stream()
        .map(ConfiguracaoStoryLegada::idOrigem)
        .min(String::compareTo)
        .filter(atual.idOrigem()::equals)
        .isPresent();
  }

  private BeneficioLegado beneficioPorOrigem(Snapshot snapshot, String idOrigem) {
    return snapshot.beneficios().stream()
        .filter(item -> item.idOrigem().equals(idOrigem))
        .findFirst()
        .orElse(null);
  }

  private boolean escopoCompativel(BeneficioLegado beneficio, LinhaBeneficio linha) {
    return beneficio.escopo() != null
        && linha.escopo().name().equals(beneficio.escopo().trim().toUpperCase(Locale.ROOT));
  }

  private boolean modosStoryValidos(List<String> modos) {
    if (modos == null || modos.size() != 2) {
      return false;
    }
    Set<String> normalizados = new HashSet<>();
    for (String modo : modos) {
      if (modo == null) {
        return false;
      }
      normalizados.add(modo.trim().toUpperCase(Locale.ROOT));
    }
    return normalizados.equals(MatrizMapeamentoComercialImportacao.MODOS_STORY_CANONICOS);
  }

  private int ordemValida(Integer informada, int canonica) {
    int ordem = informada == null ? canonica : informada;
    return ordem >= 0 && ordem <= 1_000_000 ? ordem : -1;
  }

  private int ordemDuracao(int duracao) {
    return switch (duracao) {
      case 1 -> 1;
      case 7 -> 2;
      case 14 -> 3;
      case 30 -> 4;
      default -> -1;
    };
  }

  private Integer totalCreditos(PacoteCreditoLegado pacote) {
    if (pacote.quantidadeCreditosBase() == null
        || pacote.quantidadeCreditosBase() <= 0
        || pacote.bonusCreditos() == null
        || pacote.bonusCreditos() < 0) {
      return null;
    }
    try {
      return Math.addExact(pacote.quantidadeCreditosBase(), pacote.bonusCreditos());
    } catch (ArithmeticException exception) {
      return null;
    }
  }

  private Object totalCreditosSeguro(PacoteCreditoLegado pacote) {
    Integer total = totalCreditos(pacote);
    return total == null ? "INVALIDO" : total;
  }

  private BigDecimal monetarioOpcional(BigDecimal valor) {
    if (valor == null) {
      return null;
    }
    if (valor.signum() < 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
      return null;
    }
    try {
      return valor.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      return null;
    }
  }

  private BigDecimal monetarioPacote(BigDecimal valor, boolean ativo) {
    if (valor == null
        || valor.signum() < 0
        || (ativo && valor.signum() == 0)
        || valor.compareTo(VALOR_MAXIMO) > 0) {
      return null;
    }
    try {
      return valor.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      return null;
    }
  }

  private String normalizarMoeda(String moeda) {
    return moeda == null ? "" : moeda.trim().toUpperCase(Locale.ROOT);
  }

  private UUID idBeneficio(String codigo) {
    return BENEFICIOS_IDS.getOrDefault(
        codigo,
        uuid("beneficio-configuracao-comercial", codigo));
  }

  private UUID idPacote(String codigo) {
    return PACOTES_IDS.getOrDefault(
        codigo,
        uuid("pacote-configuracao-comercial", codigo));
  }

  private UUID idPersistidoBeneficio(String codigo) {
    List<UUID> encontrados = jdbc.query(
        "SELECT id FROM beneficio_premium WHERE codigo = ?",
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        codigo);
    return encontrados.isEmpty() ? null : encontrados.get(0);
  }

  private boolean existe(String tabela, String coluna, String valor) {
    if (!Set.of("beneficio_premium", "plano_credito").contains(tabela)
        || !"codigo".equals(coluna)) {
      throw new IllegalArgumentException("Consulta de existencia nao permitida");
    }
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) > 0 FROM " + tabela + " WHERE " + coluna + " = ?",
        Boolean.class,
        valor));
  }

  private boolean usuarioExiste(UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) > 0 FROM usuario WHERE id = ?",
        Boolean.class,
        id));
  }

  private long pendentes(UUID execucaoId) {
    return jdbc.queryForObject(
        """
        SELECT
          (SELECT count(*) FROM stg_premium WHERE execucao_id = ? AND status = 'PENDENTE')
          + (SELECT count(*) FROM stg_credito WHERE execucao_id = ? AND status = 'PENDENTE')
        """,
        Long.class,
        execucaoId,
        execucaoId);
  }

  private long contarMapeamentos(UUID execucaoId, String tabela, String status) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_mapeamento
        WHERE execucao_id = ? AND tabela_origem = ? AND status = ?
        """,
        Long.class,
        execucaoId,
        tabela,
        status);
  }

  private long contarMapeamentosSemDestino(UUID execucaoId, String tabela, String status) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_mapeamento
        WHERE execucao_id = ? AND tabela_origem = ? AND status = ?
          AND entidade_v3_id IS NULL
        """,
        Long.class,
        execucaoId,
        tabela,
        status);
  }

  private long contarDestino(
      UUID execucaoId,
      String tabelaOrigem,
      String status,
      String tabelaDestino,
      boolean ativo) {
    if (!Set.of("beneficio_premium", "plano_credito").contains(tabelaDestino)) {
      throw new IllegalArgumentException("Tabela de destino nao permitida");
    }
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM importacao_mapeamento mapa
        JOIN %s destino ON destino.id = mapa.entidade_v3_id
        WHERE mapa.execucao_id = ?
          AND mapa.tabela_origem = ?
          AND mapa.status = ?
          AND destino.ativo = ?
        """.formatted(tabelaDestino),
        Long.class,
        execucaoId,
        tabelaOrigem,
        status,
        ativo);
  }

  private boolean mapear(
      UUID execucaoId,
      String tabela,
      String idOrigem,
      String hash,
      TipoEntidadeImportacao tipo,
      UUID entidadeId,
      String status,
      OffsetDateTime criadoEm) {
    return jdbc.update(
        """
        INSERT INTO importacao_mapeamento (
          id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
          entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem) DO NOTHING
        """,
        uuid("mapeamento-configuracao-comercial",
            execucaoId.toString(), tabela, idOrigem),
        execucaoId,
        ORIGEM,
        tabela,
        idOrigem,
        hash,
        tipo.name(),
        entidadeId,
        status,
        criadoEm,
        criadoEm) == 1;
  }

  private void pendencia(
      UUID execucaoId,
      CodigoPendenciaImportacao codigo,
      TipoEntidadeImportacao tipo,
      String idOrigem,
      OffsetDateTime criadoEm) {
    jdbc.update(
        """
        INSERT INTO importacao_pendencia (
          id, execucao_id, codigo, severidade, status, entidade_tipo,
          id_origem, detalhe_resumido, criado_em
        ) VALUES (?, ?, ?, ?, 'ABERTA', ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        uuid("pendencia-configuracao-comercial",
            execucaoId.toString(), codigo.name(), tipo.name(), idOrigem),
        execucaoId,
        codigo.name(),
        severidadeBanco(codigo.severidadePadrao()),
        tipo.name(),
        idOrigem,
        "Revisao comercial requerida: " + codigo.name(),
        criadoEm);
  }

  private String hash(Object valor) {
    try {
      byte[] serializado = hashMapper.writeValueAsBytes(valor);
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(serializado));
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar hash comercial", exception);
    }
  }

  private String json(Object valor) {
    try {
      return objectMapper.writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao serializar metadados comerciais", exception);
    }
  }

  private static UUID uuid(String... partes) {
    return UUID.nameUUIDFromBytes(
        String.join(":", partes).getBytes(StandardCharsets.UTF_8));
  }

  private static String severidadeBanco(SeveridadePendenciaImportacao severidade) {
    return switch (severidade) {
      case INFO -> "INFO";
      case ALERTA -> "MEDIA";
      case ERRO -> "ALTA";
      case BLOQUEANTE -> "CRITICA";
    };
  }

  private record ChaveEntrada(
      String stageTabela,
      String tabelaOrigem,
      String idOrigem) {
  }

  private record Entrada(
      String stageTabela,
      String tabelaOrigem,
      String idOrigem,
      TipoEntidadeImportacao tipo,
      Object dados) {

    private ChaveEntrada chave() {
      return new ChaveEntrada(stageTabela, tabelaOrigem, idOrigem);
    }
  }

  private record Processamento(
      int novos,
      UUID entidadeId,
      String statusStage,
      List<CodigoPendenciaImportacao> pendencias) {

    private static Processamento importado(int novos, UUID entidadeId) {
      return new Processamento(novos, entidadeId, "PROCESSADO", List.of());
    }

    private static Processamento historico() {
      return new Processamento(0, null, "PROCESSADO", List.of());
    }

    private static Processamento quarentena(CodigoPendenciaImportacao codigo) {
      return new Processamento(0, null, "PENDENTE_REVISAO", List.of(codigo));
    }

    private static Processamento descartado() {
      return new Processamento(0, null, "REJEITADO", List.of());
    }

    private static Processamento vazio() {
      return new Processamento(0, null, "PROCESSADO", List.of());
    }
  }

  private static final class ContadoresChamada {
    private long processados;
    private long novos;
  }
}
