package br.com.topsdojob.v3.importacao.financeiro;

import br.com.topsdojob.v3.importacao.financeiro.MatrizPagamentoHistoricoImportacao.Decisao;
import br.com.topsdojob.v3.importacao.financeiro.MatrizPagamentoHistoricoImportacao.Linha;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.AtivacaoLegada;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.CarteiraLegada;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.GrupoAtivacaoLegado;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.PagamentoLegado;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.Snapshot;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
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
public class ImportadorFinanceiroFaseQuatro {
  private static final String ORIGEM = "TOPSDOJOB_LEGADO";
  private static final String TABELA_PAGAMENTO = "pagamentos_historicos";
  private static final String TABELA_GRUPO = "ativacoes_vigentes";
  private static final String TABELA_ATIVACAO = "ativacoes_vigentes_componentes";
  private static final String TABELA_CARTEIRA = "carteiras_saldo_final";
  private static final String STAGE_PAGAMENTO = "stg_pagamento";
  private static final String STAGE_PREMIUM = "stg_premium";
  private static final String STAGE_CREDITO = "stg_credito";
  private static final String VERSAO_IMPORTADOR = "financeiro-fase-4-v1";
  private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999.99");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacao;
  private final ObjectMapper objectMapper;
  private final ObjectMapper hashMapper;
  private final MatrizPagamentoHistoricoImportacao matrizPagamento;

  public ImportadorFinanceiroFaseQuatro(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      ObjectMapper objectMapper,
      MatrizPagamentoHistoricoImportacao matrizPagamento) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transacao = new TransactionTemplate(transactionManager);
    this.objectMapper = objectMapper;
    this.hashMapper = objectMapper.copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.matrizPagamento = matrizPagamento;
  }

  public ResultadoImportacaoFinanceiraFaseQuatro executar(Snapshot snapshot, int limite) {
    Objects.requireNonNull(snapshot, "snapshot deve ser informado");
    if (limite < 0) {
      throw new IllegalArgumentException("limite nao pode ser negativo");
    }
    if (!usuarioExiste(snapshot.atorSistemaV3Id())) {
      throw new IllegalStateException("Ator de sistema da importacao nao existe na V3");
    }
    UUID execucaoId = uuid("execucao-financeira-fase-quatro", snapshot.snapshotId());
    transacao.executeWithoutResult(status -> preparar(snapshot, execucaoId));

    List<ChaveEntrada> pendentes = limite == 0
        ? List.of()
        : buscarPendentes(execucaoId, limite);
    Map<ChaveEntrada, Entrada> porChave = new LinkedHashMap<>();
    entradas(snapshot).forEach(entrada -> porChave.put(entrada.chave(), entrada));
    ContadoresChamada chamada = new ContadoresChamada();
    for (ChaveEntrada chave : pendentes) {
      Entrada entrada = porChave.get(chave);
      if (entrada == null) {
        throw new IllegalStateException("Staging financeiro sem origem no snapshot atual");
      }
      Processamento resultado = transacao.execute(
          status -> processar(snapshot, execucaoId, entrada));
      if (resultado != null) {
        chamada.processados++;
        chamada.pagamentos += resultado.pagamentosNovos();
        chamada.eventos += resultado.eventosNovos();
        chamada.grupos += resultado.gruposNovos();
        chamada.ativacoes += resultado.ativacoesNovas();
        chamada.movimentos += resultado.movimentosNovos();
      }
    }
    return transacao.execute(status -> finalizar(snapshot, execucaoId, chamada));
  }

  private List<ChaveEntrada> buscarPendentes(UUID execucaoId, int limite) {
    return jdbc.query(
        """
        SELECT stage_tabela, tabela_origem, id_origem
        FROM (
          SELECT 'stg_pagamento' AS stage_tabela, tabela_origem, id_origem
          FROM stg_pagamento
          WHERE execucao_id = ? AND status = 'PENDENTE'
          UNION ALL
          SELECT 'stg_premium' AS stage_tabela, tabela_origem, id_origem
          FROM stg_premium
          WHERE execucao_id = ? AND status = 'PENDENTE'
          UNION ALL
          SELECT 'stg_credito' AS stage_tabela, tabela_origem, id_origem
          FROM stg_credito
          WHERE execucao_id = ? AND status = 'PENDENTE'
        ) fila
        ORDER BY
          CASE tabela_origem
            WHEN 'pagamentos_historicos' THEN 1
            WHEN 'ativacoes_vigentes' THEN 2
            WHEN 'carteiras_saldo_final' THEN 3
            ELSE 4
          END,
          id_origem
        LIMIT ?
        """,
        (rs, rowNum) -> new ChaveEntrada(
            rs.getString("stage_tabela"),
            rs.getString("tabela_origem"),
            rs.getString("id_origem")),
        execucaoId,
        execucaoId,
        execucaoId,
        limite);
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
      throw new IllegalStateException("Snapshot financeiro alterado durante execucao existente");
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
      throw new IllegalStateException("Fonte financeira diverge do staging existente");
    }
    jdbc.update(
        """
        INSERT INTO %s (
          id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
          payload_normalizado_json, status, criado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDENTE', ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem) DO NOTHING
        """.formatted(entrada.stageTabela()),
        uuid("stage-financeiro", snapshot.snapshotId(),
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
    payload.put("fingerprint", hash(entrada.dados()));
    if (entrada.dados() instanceof PagamentoLegado pagamento) {
      payload.put("usuarioLegadoHash", hashTexto(pagamento.usuarioOrigemId()));
      payload.put("planoLegadoHash", hashOpcional(pagamento.planoOrigemId()));
      payload.put("provedor", normalizarToken(pagamento.provedor()));
      payload.put("estado", normalizarToken(pagamento.estado()));
      payload.put("valor", pagamento.valor());
      payload.put("creditos", pagamento.creditos());
      payload.put("creditado", pagamento.creditado());
      payload.put("criadoEm", pagamento.criadoEm());
      payload.put("atualizadoEm", pagamento.atualizadoEm());
      payload.put("expiracaoEm", pagamento.expiracaoEm());
    } else if (entrada.dados() instanceof GrupoAtivacaoLegado grupo) {
      payload.put("usuarioLegadoHash", hashTexto(grupo.usuarioOrigemId()));
      payload.put("anuncioLegadoHash", hashOpcional(grupo.anuncioOrigemId()));
      payload.put("pagamentoLegadoHash", hashOpcional(grupo.pagamentoOrigemId()));
      payload.put("origem", normalizarToken(grupo.origem()));
      payload.put("status", normalizarToken(grupo.status()));
      payload.put("inicioEm", grupo.inicioEm());
      payload.put("fimEm", grupo.fimEm());
      payload.put("componentes", grupo.ativacoes().stream().map(ativacao -> Map.of(
          "codigo", ativacao.beneficioCodigoV3(),
          "creditos", ativacao.creditosCobrados() == null ? "AUSENTE" : ativacao.creditosCobrados(),
          "preco", ativacao.precoSnapshot() == null ? "AUSENTE" : ativacao.precoSnapshot(),
          "fingerprint", hash(ativacao))).toList());
    } else if (entrada.dados() instanceof CarteiraLegada carteira) {
      payload.put("usuarioLegadoHash", hashTexto(carteira.usuarioOrigemId()));
      payload.put("saldoFinal", carteira.saldoFinal());
      payload.put("divergenciaHistorica", carteira.divergenciaHistorica());
      payload.put("statusUsuarioLegado", normalizarToken(carteira.statusUsuarioLegado()));
    } else {
      throw new IllegalStateException("Tipo financeiro nao suportado");
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
    Processamento processamento;
    if (entrada.dados() instanceof PagamentoLegado pagamento) {
      processamento = processarPagamento(snapshot, pagamento);
    } else if (entrada.dados() instanceof GrupoAtivacaoLegado grupo) {
      processamento = processarGrupo(snapshot, execucaoId, grupo);
    } else if (entrada.dados() instanceof CarteiraLegada carteira) {
      processamento = processarCarteira(snapshot, carteira);
    } else {
      throw new IllegalStateException("Tipo financeiro nao suportado");
    }
    return concluirItem(snapshot, execucaoId, entrada, processamento);
  }

  private Processamento processarPagamento(Snapshot snapshot, PagamentoLegado pagamento) {
    Linha linha = matrizPagamento.classificar(pagamento, snapshot.capturadoEm());
    if (linha.decisao() == Decisao.QUARENTENA) {
      return Processamento.quarentena(linha.pendencia());
    }
    UUID usuarioId = snapshot.usuariosV3().get(pagamento.usuarioOrigemId());
    if (usuarioId == null || !usuarioExiste(usuarioId)) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_USUARIO_NAO_MAPEADO);
    }
    UUID planoId = null;
    if (pagamento.planoOrigemId() != null) {
      planoId = snapshot.planosCreditoV3().get(pagamento.planoOrigemId());
      if (planoId == null || !registroExiste("plano_credito", planoId)) {
        return Processamento.quarentena(
            CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_PLANO_NAO_MAPEADO);
      }
    }
    if (!"BRL".equals(normalizarToken(pagamento.moeda()))) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_VALOR_INCONSISTENTE);
    }
    OffsetDateTime referencia = dataReferencia(pagamento, linha.statusV3());
    if (referencia == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.PAGAMENTO_HISTORICO_DATA_AUSENTE);
    }
    OffsetDateTime criadoEm = pagamento.criadoEm() == null ? referencia : pagamento.criadoEm();
    OffsetDateTime aprovadoEm = linha.statusV3() == StatusInternoPagamento.APROVADO
        ? referencia
        : null;
    OffsetDateTime creditadoEm = linha.statusV3() == StatusInternoPagamento.APROVADO
        ? referencia
        : null;
    OffsetDateTime canceladoEm = Set.of(
        StatusInternoPagamento.CANCELADO,
        StatusInternoPagamento.EXPIRADO).contains(linha.statusV3())
            ? referencia
            : null;
    UUID pagamentoId = uuid("pagamento-historico", pagamento.idOrigem());
    String identificador = "historico-" + hashTexto(
        pagamento.identificadorProvedor() == null
            ? pagamento.idOrigem()
            : pagamento.identificadorProvedor()).substring(0, 32);
    int pagamentoNovo = jdbc.update(
        """
        INSERT INTO pagamento (
          id, usuario_id, plano_credito_id, provedor, metodo, ambiente, txid,
          identificador_provedor, valor, moeda, quantidade_creditos, status_interno,
          status_provedor, expiracao_em, aprovado_em, cancelado_em, creditado_em,
          idempotency_key, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, 'BRL', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        pagamentoId,
        usuarioId,
        planoId,
        linha.provedorV3().name(),
        linha.metodoV3().name(),
        identificador,
        pagamento.valor().setScale(2, RoundingMode.UNNECESSARY),
        pagamento.creditos(),
        linha.statusV3().name(),
        "HISTORICO_" + normalizarToken(pagamento.estado()),
        pagamento.expiracaoEm(),
        aprovadoEm,
        canceladoEm,
        creditadoEm,
        "importacao-financeira-pagamento-" + hashTexto(pagamento.idOrigem()),
        criadoEm,
        referencia);
    validarPagamentoExistente(pagamentoId, linha, pagamento);

    OffsetDateTime processadoEm = snapshot.capturadoEm().isBefore(referencia)
        ? referencia
        : snapshot.capturadoEm();
    int eventoNovo = jdbc.update(
        """
        INSERT INTO pagamento_evento (
          id, pagamento_id, provedor, provedor_evento_id, tipo_evento, payload_hash,
          status_provedor, recebido_em, processado_em, resultado
        ) VALUES (?, ?, ?, ?, 'IMPORTACAO_HISTORICA', ?, ?, ?, ?,
          'HISTORICO_SEM_EFEITO_FINANCEIRO')
        ON CONFLICT (id) DO NOTHING
        """,
        uuid("evento-pagamento-historico", pagamento.idOrigem()),
        pagamentoId,
        linha.provedorV3().name(),
        "historico-evento-" + hashTexto(pagamento.idOrigem()).substring(0, 32),
        hash(pagamento),
        "HISTORICO_" + normalizarToken(pagamento.estado()),
        referencia,
        processadoEm);
    return Processamento.importado(
        pagamentoId, pagamentoNovo, eventoNovo, 0, 0, 0);
  }

  private void validarPagamentoExistente(
      UUID pagamentoId,
      Linha linha,
      PagamentoLegado origem) {
    Map<String, Object> persistido = jdbc.queryForMap(
        """
        SELECT provedor, metodo, ambiente, txid, valor, quantidade_creditos, status_interno
        FROM pagamento WHERE id = ?
        """,
        pagamentoId);
    if (!linha.provedorV3().name().equals(persistido.get("provedor"))
        || !linha.metodoV3().name().equals(persistido.get("metodo"))
        || persistido.get("ambiente") != null
        || persistido.get("txid") != null
        || new BigDecimal(persistido.get("valor").toString()).compareTo(origem.valor()) != 0
        || !Objects.equals(persistido.get("quantidade_creditos"), origem.creditos())
        || !linha.statusV3().name().equals(persistido.get("status_interno"))) {
      throw new IllegalStateException("Pagamento historico existente diverge da origem");
    }
  }

  private OffsetDateTime dataReferencia(
      PagamentoLegado pagamento,
      StatusInternoPagamento status) {
    OffsetDateTime referencia = matrizPagamento.dataReferencia(pagamento);
    if (referencia == null && status == StatusInternoPagamento.EXPIRADO) {
      return pagamento.expiracaoEm();
    }
    return referencia;
  }

  private Processamento processarGrupo(
      Snapshot snapshot,
      UUID execucaoId,
      GrupoAtivacaoLegado grupo) {
    String status = normalizarToken(grupo.status());
    if (Set.of("EXPIRADO", "EXPIRED", "REVOGADO", "REVOKED", "CANCELADO", "CANCELLED")
        .contains(status)) {
      mapearComponentesSemDestino(
          snapshot, execucaoId, grupo, "REJEITADO", null);
      return Processamento.descartado();
    }
    if (!Set.of("ATIVO", "ACTIVE", "PUBLICADO", "PUBLISHED").contains(status)
        || grupo.inicioEm() == null
        || grupo.fimEm() == null
        || !grupo.fimEm().isAfter(grupo.inicioEm())
        || snapshot.capturadoEm().isBefore(grupo.inicioEm())
        || !snapshot.capturadoEm().isBefore(grupo.fimEm())) {
      return quarentenaGrupo(snapshot, execucaoId, grupo,
          CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_VIGENCIA_INVALIDA);
    }
    if (grupo.ativacoes().isEmpty()) {
      return quarentenaGrupo(snapshot, execucaoId, grupo,
          CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_GRUPO_INCONSISTENTE);
    }
    UUID usuarioId = snapshot.usuariosV3().get(grupo.usuarioOrigemId());
    if (usuarioId == null || !usuarioExiste(usuarioId)) {
      return quarentenaGrupo(snapshot, execucaoId, grupo,
          CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_USUARIO_NAO_MAPEADO);
    }
    UUID anuncioId = null;
    if (grupo.anuncioOrigemId() != null) {
      anuncioId = snapshot.anunciosV3().get(grupo.anuncioOrigemId());
      if (anuncioId == null || !registroExiste("anuncio", anuncioId)) {
        return quarentenaGrupo(snapshot, execucaoId, grupo,
            CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_ANUNCIO_NAO_MAPEADO);
      }
      UUID dono = jdbc.queryForObject(
          "SELECT usuario_id FROM anuncio WHERE id = ?", UUID.class, anuncioId);
      if (!usuarioId.equals(dono)) {
        return quarentenaGrupo(snapshot, execucaoId, grupo,
            CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_OWNERSHIP_DIVERGENTE);
      }
    }
    OrigemAtivacao origem = resolverOrigem(snapshot, execucaoId, grupo);
    if (origem == null) {
      return quarentenaGrupo(snapshot, execucaoId, grupo,
          CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_ORIGEM_INCONSISTENTE);
    }
    List<AtivacaoResolvida> ativacoes = new ArrayList<>();
    for (AtivacaoLegada ativacao : grupo.ativacoes()) {
      if (!grupo.inicioEm().equals(ativacao.inicioEm())
          || !grupo.fimEm().equals(ativacao.fimEm())
          || ativacao.creditosCobrados() == null
          || ativacao.creditosCobrados() < 0
          || !precoValido(ativacao.precoSnapshot())) {
        return quarentenaGrupo(snapshot, execucaoId, grupo,
            CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_GRUPO_INCONSISTENTE);
      }
      BeneficioResolvido beneficio = buscarBeneficio(ativacao.beneficioCodigoV3());
      if (beneficio == null) {
        return quarentenaGrupo(snapshot, execucaoId, grupo,
            CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_PRODUTO_NAO_MAPEADO);
      }
      if ("ANUNCIO".equals(beneficio.escopo()) && anuncioId == null) {
        return quarentenaGrupo(snapshot, execucaoId, grupo,
            CodigoPendenciaImportacao.ATIVACAO_FINANCEIRA_ANUNCIO_NAO_MAPEADO);
      }
      ativacoes.add(new AtivacaoResolvida(ativacao, beneficio));
    }

    UUID grupoId = uuid("grupo-ativacao-financeira", grupo.idOrigem());
    String observacao = "Importacao financeira historica; origemHash="
        + hashTexto(grupo.idOrigem()).substring(0, 16)
        + (grupo.pagamentoOrigemId() == null
            ? ""
            : "; pagamentoHash=" + hashTexto(grupo.pagamentoOrigemId()).substring(0, 16));
    int grupoNovo = jdbc.update(
        """
        INSERT INTO grupo_ativacao_beneficio (
          id, tipo, origem, usuario_id, anuncio_id, ator_usuario_id, campanha_codigo,
          validade_inicio_em, validade_fim_em, status, idempotency_key, observacao,
          criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, 'ATIVO', ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        grupoId,
        origem.tipoGrupo(),
        origem.origem(),
        usuarioId,
        anuncioId,
        snapshot.atorSistemaV3Id(),
        grupo.inicioEm(),
        grupo.fimEm(),
        "importacao-financeira-grupo-" + hashTexto(grupo.idOrigem()),
        observacao,
        grupo.inicioEm(),
        snapshot.capturadoEm());

    int ativacoesNovas = 0;
    for (AtivacaoResolvida resolvida : ativacoes) {
      AtivacaoLegada ativacao = resolvida.origem();
      UUID ativacaoId = uuid("ativacao-beneficio-financeira", ativacao.idOrigem());
      int nova = jdbc.update(
          """
          INSERT INTO ativacao_beneficio (
            id, beneficio_id, opcao_id, usuario_id, anuncio_id, grupo_ativacao_id,
            origem, ator_usuario_id, campanha_codigo, inicio_em, fim_em, status,
            custo_creditos_snapshot, preco_snapshot, idempotency_key, criado_em
          ) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, NULL, ?, ?, 'ATIVA', ?, ?, ?, ?)
          ON CONFLICT (id) DO NOTHING
          """,
          ativacaoId,
          resolvida.beneficio().id(),
          usuarioId,
          anuncioId,
          grupoId,
          origem.origem(),
          snapshot.atorSistemaV3Id(),
          ativacao.inicioEm(),
          ativacao.fimEm(),
          ativacao.creditosCobrados(),
          ativacao.precoSnapshot(),
          "importacao-financeira-ativacao-" + hashTexto(ativacao.idOrigem()),
          ativacao.inicioEm());
      ativacoesNovas += nova;
      mapear(
          execucaoId,
          TABELA_ATIVACAO,
          ativacao.idOrigem(),
          hash(ativacao),
          TipoEntidadeImportacao.ATIVACAO_BENEFICIO_FINANCEIRA,
          ativacaoId,
          "MAPEADO",
          snapshot.capturadoEm());
    }
    return Processamento.importado(grupoId, 0, 0, grupoNovo, ativacoesNovas, 0);
  }

  private Processamento quarentenaGrupo(
      Snapshot snapshot,
      UUID execucaoId,
      GrupoAtivacaoLegado grupo,
      CodigoPendenciaImportacao codigo) {
    mapearComponentesSemDestino(snapshot, execucaoId, grupo, "DIVERGENTE", codigo);
    return Processamento.quarentena(codigo);
  }

  private void mapearComponentesSemDestino(
      Snapshot snapshot,
      UUID execucaoId,
      GrupoAtivacaoLegado grupo,
      String status,
      CodigoPendenciaImportacao codigo) {
    for (AtivacaoLegada ativacao : grupo.ativacoes()) {
      mapear(
          execucaoId,
          TABELA_ATIVACAO,
          ativacao.idOrigem(),
          hash(ativacao),
          TipoEntidadeImportacao.ATIVACAO_BENEFICIO_FINANCEIRA,
          null,
          status,
          snapshot.capturadoEm());
      if (codigo != null) {
        pendencia(
            execucaoId,
            codigo,
            TipoEntidadeImportacao.ATIVACAO_BENEFICIO_FINANCEIRA,
            ativacao.idOrigem(),
            snapshot.capturadoEm());
      }
    }
  }

  private OrigemAtivacao resolverOrigem(
      Snapshot snapshot,
      UUID execucaoId,
      GrupoAtivacaoLegado grupo) {
    boolean possuiCredito = grupo.ativacoes().stream()
        .allMatch(item -> item.creditosCobrados() != null && item.creditosCobrados() > 0);
    boolean semCredito = grupo.ativacoes().stream()
        .allMatch(item -> item.creditosCobrados() != null && item.creditosCobrados() == 0);
    if (!possuiCredito && !semCredito) {
      return null;
    }
    String origem = normalizarToken(grupo.origem());
    if (possuiCredito) {
      if (Set.of("ADMIN", "CORTESIA", "CAMPANHA").contains(origem)) {
        return null;
      }
      if (grupo.pagamentoOrigemId() != null
          && !pagamentoAprovadoMapeado(execucaoId, grupo.pagamentoOrigemId())) {
        return null;
      }
      return new OrigemAtivacao("PACOTE", "CREDITO");
    }
    return switch (origem) {
      case "ADMIN", "ADMINISTRATIVO" -> new OrigemAtivacao("ADMIN", "ADMIN");
      case "CORTESIA", "COURTESY" -> new OrigemAtivacao("CORTESIA", "CORTESIA");
      case "CAMPANHA", "CAMPAIGN" -> new OrigemAtivacao("CAMPANHA", "CAMPANHA");
      case "", "IMPORTACAO", "LEGADO", "UNKNOWN", "DESCONHECIDO" ->
          new OrigemAtivacao("IMPORTACAO", "IMPORTACAO");
      default -> null;
    };
  }

  private boolean pagamentoAprovadoMapeado(UUID execucaoId, String pagamentoOrigemId) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        """
        SELECT count(*) > 0
        FROM importacao_mapeamento mapa
        JOIN pagamento p ON p.id = mapa.entidade_v3_id
        WHERE mapa.execucao_id = ?
          AND mapa.tabela_origem = ?
          AND mapa.id_origem = ?
          AND mapa.status = 'MAPEADO'
          AND p.status_interno = 'APROVADO'
          AND p.ambiente IS NULL
        """,
        Boolean.class,
        execucaoId,
        TABELA_PAGAMENTO,
        pagamentoOrigemId));
  }

  private BeneficioResolvido buscarBeneficio(String codigo) {
    List<BeneficioResolvido> encontrados = jdbc.query(
        "SELECT id, escopo FROM beneficio_premium WHERE codigo = ?",
        (rs, rowNum) -> new BeneficioResolvido(
            rs.getObject("id", UUID.class), rs.getString("escopo")),
        codigo);
    return encontrados.size() == 1 ? encontrados.get(0) : null;
  }

  private Processamento processarCarteira(Snapshot snapshot, CarteiraLegada carteira) {
    if (carteira.saldoFinal() < 0) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.SALDO_FINANCEIRO_NEGATIVO);
    }
    if (carteira.saldoFinal() == 0) {
      return Processamento.descartado();
    }
    UUID usuarioId = snapshot.usuariosV3().get(carteira.usuarioOrigemId());
    if (usuarioId == null || !usuarioExiste(usuarioId)) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.SALDO_FINANCEIRO_USUARIO_NAO_MAPEADO);
    }
    long carteirasDoUsuario = snapshot.carteiras().stream()
        .filter(item -> Objects.equals(
            snapshot.usuariosV3().get(item.usuarioOrigemId()), usuarioId))
        .count();
    if (carteirasDoUsuario != 1) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.SALDO_FINANCEIRO_DUPLICADO);
    }
    UUID movimentoId = uuid("saldo-inicial-financeiro", usuarioId.toString());
    List<Map<String, Object>> movimentos = jdbc.queryForList(
        """
        SELECT id, tipo, quantidade, saldo_antes, saldo_depois, origem
        FROM movimento_credito WHERE usuario_id = ?
        """,
        usuarioId);
    if (!movimentos.isEmpty()) {
      if (movimentos.size() == 1
          && movimentoId.equals(movimentos.get(0).get("id"))
          && "MIGRACAO_SALDO_INICIAL".equals(movimentos.get(0).get("tipo"))
          && Objects.equals(movimentos.get(0).get("quantidade"), carteira.saldoFinal())
          && Objects.equals(movimentos.get(0).get("saldo_antes"), 0)
          && Objects.equals(movimentos.get(0).get("saldo_depois"), carteira.saldoFinal())
          && "IMPORTACAO".equals(movimentos.get(0).get("origem"))) {
        return Processamento.importado(movimentoId, 0, 0, 0, 0, 0);
      }
      return Processamento.quarentena(
          CodigoPendenciaImportacao.SALDO_FINANCEIRO_LEDGER_EXISTENTE);
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("snapshotId", snapshot.snapshotId());
    metadata.put("snapshotFingerprint", hash(snapshot));
    metadata.put("usuarioLegadoHash", hashTexto(carteira.usuarioOrigemId()));
    metadata.put("saldoImportado", carteira.saldoFinal());
    metadata.put("divergenciaHistorica", carteira.divergenciaHistorica());
    metadata.put("versaoImportador", VERSAO_IMPORTADOR);
    int movimentoNovo = jdbc.update(
        """
        INSERT INTO movimento_credito (
          id, usuario_id, tipo, direcao, quantidade, saldo_antes, saldo_depois,
          origem, referencia_tipo, referencia_id, idempotency_key, ator_usuario_id,
          observacao, criado_em, metadata_json
        ) VALUES (?, ?, 'MIGRACAO_SALDO_INICIAL', 'CREDITO', ?, 0, ?,
          'IMPORTACAO', 'SNAPSHOT_IMPORTACAO', ?, ?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
        """,
        movimentoId,
        usuarioId,
        carteira.saldoFinal(),
        carteira.saldoFinal(),
        uuid("snapshot-financeiro", snapshot.snapshotId()),
        "importacao-saldo-inicial-" + hashTexto(usuarioId.toString()),
        snapshot.atorSistemaV3Id(),
        "Saldo inicial da importacao financeira; sem origem em pagamento.",
        snapshot.capturadoEm(),
        json(metadata));
    return Processamento.importado(movimentoId, 0, 0, 0, 0, movimentoNovo);
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

  private ResultadoImportacaoFinanceiraFaseQuatro finalizar(
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

    long pagamentosImportados = contarMapeamentos(execucaoId, TABELA_PAGAMENTO, "MAPEADO");
    long pagamentosQuarentena = contarMapeamentos(execucaoId, TABELA_PAGAMENTO, "DIVERGENTE");
    long pagamentosDescartados = contarMapeamentos(execucaoId, TABELA_PAGAMENTO, "REJEITADO");
    long pagamentosAprovados = jdbc.queryForObject(
        """
        SELECT count(*)
        FROM importacao_mapeamento mapa
        JOIN pagamento p ON p.id = mapa.entidade_v3_id
        WHERE mapa.execucao_id = ? AND mapa.tabela_origem = ?
          AND mapa.status = 'MAPEADO' AND p.status_interno = 'APROVADO'
        """,
        Long.class,
        execucaoId,
        TABELA_PAGAMENTO);
    long eventosHistoricos = jdbc.queryForObject(
        """
        SELECT count(*)
        FROM pagamento_evento evento
        JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = evento.pagamento_id
        WHERE mapa.execucao_id = ? AND mapa.tabela_origem = ?
          AND evento.tipo_evento = 'IMPORTACAO_HISTORICA'
        """,
        Long.class,
        execucaoId,
        TABELA_PAGAMENTO);
    long gruposImportados = contarMapeamentos(execucaoId, TABELA_GRUPO, "MAPEADO");
    long gruposQuarentena = contarMapeamentos(execucaoId, TABELA_GRUPO, "DIVERGENTE");
    long gruposDescartados = contarMapeamentos(execucaoId, TABELA_GRUPO, "REJEITADO");
    long ativacoesImportadas = contarMapeamentos(execucaoId, TABELA_ATIVACAO, "MAPEADO");
    long ativacoesQuarentena = contarMapeamentos(execucaoId, TABELA_ATIVACAO, "DIVERGENTE");
    long ativacoesDescartadas = contarMapeamentos(execucaoId, TABELA_ATIVACAO, "REJEITADO");
    long carteirasImportadas = contarMapeamentos(execucaoId, TABELA_CARTEIRA, "MAPEADO");
    long carteirasQuarentena = contarMapeamentos(execucaoId, TABELA_CARTEIRA, "DIVERGENTE");
    long carteirasSaldoZero = snapshot.carteiras().stream()
        .filter(item -> item.saldoFinal() == 0)
        .filter(item -> "REJEITADO".equals(statusMapeamento(
            execucaoId, TABELA_CARTEIRA, item.idOrigem())))
        .count();
    long saldoPositivoAnalisado = snapshot.carteiras().stream()
        .filter(item -> item.saldoFinal() > 0)
        .mapToLong(CarteiraLegada::saldoFinal)
        .sum();
    long saldoInicialImportado = snapshot.carteiras().stream()
        .filter(item -> item.saldoFinal() > 0)
        .filter(item -> "MAPEADO".equals(statusMapeamento(
            execucaoId, TABELA_CARTEIRA, item.idOrigem())))
        .mapToLong(CarteiraLegada::saldoFinal)
        .sum();
    long saldoQuarentena = snapshot.carteiras().stream()
        .filter(item -> item.saldoFinal() > 0)
        .filter(item -> "DIVERGENTE".equals(statusMapeamento(
            execucaoId, TABELA_CARTEIRA, item.idOrigem())))
        .mapToLong(CarteiraLegada::saldoFinal)
        .sum();

    Map<String, Object> resumo = new LinkedHashMap<>();
    resumo.put("snapshotFingerprint", hash(snapshot));
    resumo.put("snapshotId", snapshot.snapshotId());
    resumo.put("pagamentosAnalisados", snapshot.pagamentos().size());
    resumo.put("pagamentosImportados", pagamentosImportados);
    resumo.put("pagamentosQuarentena", pagamentosQuarentena);
    resumo.put("gruposAnalisados", snapshot.gruposAtivacao().size());
    resumo.put("gruposImportados", gruposImportados);
    resumo.put("ativacoesAnalisadas", totalAtivacoes(snapshot));
    resumo.put("ativacoesImportadas", ativacoesImportadas);
    resumo.put("saldoPositivoAnalisado", saldoPositivoAnalisado);
    resumo.put("saldoInicialImportado", saldoInicialImportado);
    resumo.put("saldoPositivoQuarentena", saldoQuarentena);
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

    return new ResultadoImportacaoFinanceiraFaseQuatro(
        execucaoId,
        status,
        snapshot.pagamentos().size(),
        pagamentosImportados,
        pagamentosAprovados,
        pagamentosQuarentena,
        pagamentosDescartados,
        eventosHistoricos,
        snapshot.gruposAtivacao().size(),
        gruposImportados,
        gruposQuarentena,
        gruposDescartados,
        totalAtivacoes(snapshot),
        ativacoesImportadas,
        ativacoesQuarentena,
        ativacoesDescartadas,
        snapshot.carteiras().size(),
        carteirasImportadas,
        carteirasSaldoZero,
        carteirasQuarentena,
        carteirasImportadas,
        saldoPositivoAnalisado,
        saldoInicialImportado,
        saldoQuarentena,
        chamada.processados,
        chamada.pagamentos,
        chamada.eventos,
        chamada.grupos,
        chamada.ativacoes,
        chamada.movimentos,
        restantes);
  }

  private List<Entrada> entradas(Snapshot snapshot) {
    List<Entrada> entradas = new ArrayList<>();
    snapshot.pagamentos().forEach(item -> entradas.add(new Entrada(
        STAGE_PAGAMENTO,
        TABELA_PAGAMENTO,
        item.idOrigem(),
        TipoEntidadeImportacao.PAGAMENTO_HISTORICO,
        item)));
    snapshot.gruposAtivacao().forEach(item -> entradas.add(new Entrada(
        STAGE_PREMIUM,
        TABELA_GRUPO,
        item.idOrigem(),
        TipoEntidadeImportacao.GRUPO_ATIVACAO_FINANCEIRA,
        item)));
    snapshot.carteiras().forEach(item -> entradas.add(new Entrada(
        STAGE_CREDITO,
        TABELA_CARTEIRA,
        item.idOrigem(),
        TipoEntidadeImportacao.SALDO_INICIAL_FINANCEIRO,
        item)));
    return entradas.stream()
        .sorted(Comparator.comparingInt(this::ordemEntrada)
            .thenComparing(Entrada::idOrigem))
        .toList();
  }

  private int ordemEntrada(Entrada entrada) {
    return switch (entrada.tabelaOrigem()) {
      case TABELA_PAGAMENTO -> 1;
      case TABELA_GRUPO -> 2;
      case TABELA_CARTEIRA -> 3;
      default -> 4;
    };
  }

  private long totalAtivacoes(Snapshot snapshot) {
    return snapshot.gruposAtivacao().stream()
        .mapToLong(grupo -> grupo.ativacoes().size())
        .sum();
  }

  private long pendentes(UUID execucaoId) {
    return jdbc.queryForObject(
        """
        SELECT
          (SELECT count(*) FROM stg_pagamento WHERE execucao_id = ? AND status = 'PENDENTE')
          + (SELECT count(*) FROM stg_premium WHERE execucao_id = ? AND status = 'PENDENTE')
          + (SELECT count(*) FROM stg_credito WHERE execucao_id = ? AND status = 'PENDENTE')
        """,
        Long.class,
        execucaoId,
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

  private String statusMapeamento(UUID execucaoId, String tabela, String idOrigem) {
    List<String> status = jdbc.query(
        """
        SELECT status FROM importacao_mapeamento
        WHERE execucao_id = ? AND tabela_origem = ? AND id_origem = ?
        """,
        (rs, rowNum) -> rs.getString(1),
        execucaoId,
        tabela,
        idOrigem);
    return status.isEmpty() ? null : status.get(0);
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
        uuid("mapeamento-financeiro", execucaoId.toString(), tabela, idOrigem),
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
        uuid("pendencia-financeira", execucaoId.toString(), codigo.name(), tipo.name(), idOrigem),
        execucaoId,
        codigo.name(),
        severidadeBanco(codigo.severidadePadrao()),
        tipo.name(),
        idOrigem,
        "Revisao financeira requerida: " + codigo.name(),
        criadoEm);
  }

  private boolean usuarioExiste(UUID id) {
    return id != null && Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) > 0 FROM usuario WHERE id = ?",
        Boolean.class,
        id));
  }

  private boolean registroExiste(String tabela, UUID id) {
    if (!Set.of("plano_credito", "anuncio").contains(tabela)) {
      throw new IllegalArgumentException("Tabela de existencia nao permitida");
    }
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) > 0 FROM " + tabela + " WHERE id = ?",
        Boolean.class,
        id));
  }

  private boolean precoValido(BigDecimal valor) {
    if (valor == null) {
      return true;
    }
    if (valor.signum() < 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
      return false;
    }
    try {
      valor.setScale(2, RoundingMode.UNNECESSARY);
      return true;
    } catch (ArithmeticException exception) {
      return false;
    }
  }

  private String hash(Object valor) {
    try {
      byte[] serializado = hashMapper.writeValueAsBytes(valor);
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(serializado));
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar hash financeiro", exception);
    }
  }

  private String hashTexto(String valor) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(valor.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar identificador financeiro", exception);
    }
  }

  private String hashOpcional(String valor) {
    return valor == null ? null : hashTexto(valor);
  }

  private String json(Object valor) {
    try {
      return objectMapper.writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao serializar metadados financeiros", exception);
    }
  }

  private String normalizarToken(String valor) {
    if (valor == null) {
      return "";
    }
    return valor.trim().toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9_]+", "_")
        .replaceAll("^_+|_+$", "");
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

  private record BeneficioResolvido(UUID id, String escopo) {
  }

  private record AtivacaoResolvida(
      AtivacaoLegada origem,
      BeneficioResolvido beneficio) {
  }

  private record OrigemAtivacao(String tipoGrupo, String origem) {
  }

  private record Processamento(
      UUID entidadeId,
      String statusStage,
      List<CodigoPendenciaImportacao> pendencias,
      int pagamentosNovos,
      int eventosNovos,
      int gruposNovos,
      int ativacoesNovas,
      int movimentosNovos) {

    private static Processamento importado(
        UUID entidadeId,
        int pagamentos,
        int eventos,
        int grupos,
        int ativacoes,
        int movimentos) {
      return new Processamento(
          entidadeId,
          "PROCESSADO",
          List.of(),
          pagamentos,
          eventos,
          grupos,
          ativacoes,
          movimentos);
    }

    private static Processamento quarentena(CodigoPendenciaImportacao codigo) {
      return new Processamento(
          null, "PENDENTE_REVISAO", List.of(codigo), 0, 0, 0, 0, 0);
    }

    private static Processamento descartado() {
      return new Processamento(null, "REJEITADO", List.of(), 0, 0, 0, 0, 0);
    }

    private static Processamento vazio() {
      return new Processamento(null, "PROCESSADO", List.of(), 0, 0, 0, 0, 0);
    }
  }

  private static final class ContadoresChamada {
    private long processados;
    private long pagamentos;
    private long eventos;
    private long grupos;
    private long ativacoes;
    private long movimentos;
  }
}
