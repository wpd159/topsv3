package br.com.topsdojob.v3.importacao.anuncio;

import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios.Item;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.AnuncioLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.FilhoRevisaoOrfao;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.LocalidadeMapeada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoLegada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoLocalAtendimentoLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoMidiaLegada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoServicoLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.Snapshot;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ImportadorAnunciosFaseUm {
  private static final String ORIGEM = "TOPSDOJOB_LEGADO";
  private static final String TABELA_ANUNCIO = "anuncios";
  private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
  private static final Set<String> TIPOS_REVISAO =
      Set.of("CRIACAO", "EDICAO", "MIDIA", "DOCUMENTO", "DENUNCIA");
  private static final Set<String> STATUS_REVISAO =
      Set.of("ABERTA", "EM_ANALISE", "APROVADA", "REJEITADA", "CANCELADA");
  private static final Set<String> ACOES_MIDIA =
      Set.of("ADICIONAR", "SUBSTITUIR", "REMOVER", "REORDENAR");
  private static final Set<String> STATUS_MIDIA =
      Set.of("PENDENTE", "APROVADA", "REJEITADA", "CANCELADA");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacao;
  private final ObjectMapper objectMapper;
  private final PlanejadorAnuncioImportacao planejador = new PlanejadorAnuncioImportacao();

  public ImportadorAnunciosFaseUm(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      ObjectMapper objectMapper) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transacao = new TransactionTemplate(transactionManager);
    this.objectMapper = objectMapper;
  }

  public ResultadoImportacaoAnuncios executar(Snapshot snapshot, int limite) {
    Objects.requireNonNull(snapshot, "snapshot deve ser informado");
    if (limite < 0) {
      throw new IllegalArgumentException("limite nao pode ser negativo");
    }
    UUID execucaoId = uuid("execucao", snapshot.snapshotId());
    transacao.executeWithoutResult(status -> preparar(snapshot, execucaoId));
    List<String> pendentes = limite == 0 ? List.of() : jdbc.query(
        """
        SELECT id_origem FROM stg_anuncio
        WHERE execucao_id = ? AND status = 'PENDENTE'
        ORDER BY id_origem LIMIT ?
        """,
        (rs, rowNum) -> rs.getString(1), execucaoId, limite);
    Map<String, AnuncioLegado> porId = new LinkedHashMap<>();
    snapshot.anuncios().forEach(anuncio -> porId.put(anuncio.idOrigem(), anuncio));
    Contadores contadores = new Contadores();
    for (String idOrigem : pendentes) {
      AnuncioLegado anuncio = porId.get(idOrigem);
      if (anuncio == null) {
        throw new IllegalStateException("Staging sem origem no snapshot atual");
      }
      Processamento resultado = transacao.execute(
          status -> processar(snapshot, execucaoId, anuncio));
      if (resultado != null) {
        contadores.somar(resultado);
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
        execucaoId, ORIGEM, snapshot.capturadoEm(), snapshot.atorSistemaV3Id(),
        fingerprint, snapshot.capturadoEm());
    String fingerprintExistente = jdbc.queryForObject(
        "SELECT resumo_json ->> 'snapshotFingerprint' FROM importacao_execucao WHERE id = ?",
        String.class, execucaoId);
    if (fingerprintExistente != null && !fingerprint.equals(fingerprintExistente)) {
      throw new IllegalStateException("Snapshot alterado durante uma execucao existente");
    }
    for (AnuncioLegado anuncio : snapshot.anuncios()) {
      prepararStage(snapshot, execucaoId, anuncio);
    }
    for (FilhoRevisaoOrfao orfao : snapshot.filhosOrfaos()) {
      mapear(execucaoId, orfao.tabelaOrigem(), orfao.idOrigem(), hash(orfao),
          TipoEntidadeImportacao.REVISAO_FILHO, null, "DIVERGENTE", snapshot.capturadoEm());
      pendencia(execucaoId, CodigoPendenciaImportacao.REVISAO_FILHO_ORFAO,
          TipoEntidadeImportacao.REVISAO_FILHO, orfao.idOrigem(), snapshot.capturadoEm());
    }
    jdbc.update(
        "UPDATE importacao_execucao SET status = 'EM_EXECUCAO', finalizado_em = NULL WHERE id = ?",
        execucaoId);
  }

  private void prepararStage(Snapshot snapshot, UUID execucaoId, AnuncioLegado anuncio) {
    Map<String, Object> fonte = new LinkedHashMap<>();
    fonte.put("anuncio", anuncio);
    fonte.put("manifesto", snapshot.manifesto().itensDoAnuncio(anuncio.idOrigem()));
    String hash = hash(fonte);
    List<String> existente = jdbc.query(
        """
        SELECT hash_origem FROM stg_anuncio
        WHERE execucao_id = ? AND sistema_origem = ? AND tabela_origem = ? AND id_origem = ?
        """,
        (rs, rowNum) -> rs.getString(1), execucaoId, ORIGEM, TABELA_ANUNCIO,
        anuncio.idOrigem());
    if (!existente.isEmpty() && !Objects.equals(existente.get(0), hash)) {
      throw new IllegalStateException("Fonte do anuncio diverge do staging existente");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("statusEfetivo", anuncio.status());
    payload.put("categoria", anuncio.categoria());
    payload.put("atendimentoVirtualInformado",
        anuncio.atendimentoExclusivamenteVirtual() != null);
    payload.put("atendimentoVirtual", anuncio.atendimentoExclusivamenteVirtual());
    payload.put("classificacaoConteudo", anuncio.classificacaoConteudo());
    payload.put("quantidadeRevisoes", anuncio.revisoes().size());
    payload.put("quantidadeReferenciasMidia",
        snapshot.manifesto().itensDoAnuncio(anuncio.idOrigem()).size());
    payload.put("bloqueioJuridico",
        anuncio.bloqueioJuridico() != null && anuncio.bloqueioJuridico().ativo());
    jdbc.update(
        """
        INSERT INTO stg_anuncio (
          id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
          payload_normalizado_json, status, criado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDENTE', ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem) DO NOTHING
        """,
        uuid("stage-anuncio", snapshot.snapshotId(), anuncio.idOrigem()), execucaoId,
        ORIGEM, TABELA_ANUNCIO, anuncio.idOrigem(), hash, json(payload),
        snapshot.capturadoEm());
  }

  private Processamento processar(Snapshot snapshot, UUID execucaoId, AnuncioLegado anuncio) {
    List<String> estado = jdbc.query(
        "SELECT status FROM stg_anuncio WHERE execucao_id = ? AND id_origem = ? FOR UPDATE",
        (rs, rowNum) -> rs.getString(1), execucaoId, anuncio.idOrigem());
    if (estado.isEmpty() || !"PENDENTE".equals(estado.get(0))) {
      return Processamento.vazio();
    }
    UUID proprietarioId = snapshot.proprietariosV3().get(anuncio.proprietarioOrigemId());
    if (proprietarioId != null && !usuarioExiste(proprietarioId)) {
      proprietarioId = null;
    }
    boolean proprietarioAtivo = proprietarioId != null && usuarioAtivo(proprietarioId);
    LocalidadeMapeada localidade = anuncio.localizacao() == null
        ? null
        : snapshot.localidadesV3().get(anuncio.localizacao().chaveMapeamento());
    if (!localidadeExiste(localidade)) {
      localidade = null;
    }
    List<Item> midiasValidas = snapshot.manifesto()
        .itensPublicosValidos(anuncio.idOrigem());
    var plano = planejador.planejar(
        anuncio, proprietarioId, proprietarioAtivo, localidade, midiasValidas);
    List<CodigoPendenciaImportacao> pendencias = new ArrayList<>(plano.pendencias());
    if (!SLUG.matcher(anuncio.slug()).matches()) {
      pendencias.add(CodigoPendenciaImportacao.ANUNCIO_SLUG_INVALIDO);
    }
    UUID anuncioId = uuid("anuncio", anuncio.idOrigem());
    if (!slugDisponivel(anuncio.slug(), anuncioId)) {
      pendencias.add(CodigoPendenciaImportacao.SLUG_DUPLICADO);
    }
    if (plano.quarentena() || pendencias.stream().anyMatch(this::bloqueante)) {
      return quarentena(snapshot, execucaoId, anuncio, pendencias);
    }
    int novoAnuncio = inserirAnuncio(
        snapshot, execucaoId, anuncioId, proprietarioId, anuncio, localidade, plano);
    Map<String, MidiaIds> midias = inserirMidiasAtuais(
        snapshot, execucaoId, anuncioId, anuncio);
    int revisoes = 0;
    int filhos = 0;
    for (RevisaoLegada revisao : anuncio.revisoes()) {
      ProcessamentoRevisao resultado = inserirRevisao(
          snapshot, execucaoId, anuncioId, anuncio.idOrigem(), revisao, midias);
      revisoes += resultado.revisoes();
      filhos += resultado.filhos();
    }
    mapear(execucaoId, TABELA_ANUNCIO, anuncio.idOrigem(), hash(anuncio),
        TipoEntidadeImportacao.ANUNCIO, anuncioId, "MAPEADO", snapshot.capturadoEm());
    pendencias.stream().distinct().forEach(codigo -> pendencia(
        execucaoId, codigo, TipoEntidadeImportacao.ANUNCIO,
        anuncio.idOrigem(), snapshot.capturadoEm()));
    jdbc.update(
        """
        UPDATE stg_anuncio SET status = 'PROCESSADO', pendencia_codigo = ?,
          entidade_v3_id = ?, processado_em = ?
        WHERE execucao_id = ? AND id_origem = ?
        """,
        pendencias.isEmpty() ? null : pendencias.get(0).name(), anuncioId,
        snapshot.capturadoEm(), execucaoId, anuncio.idOrigem());
    return new Processamento(1, novoAnuncio, revisoes, filhos);
  }

  private int inserirAnuncio(
      Snapshot snapshot,
      UUID execucaoId,
      UUID anuncioId,
      UUID proprietarioId,
      AnuncioLegado anuncio,
      LocalidadeMapeada localidade,
      PlanejadorAnuncioImportacao.Plano plano) {
    int inserido = jdbc.update(
        """
        INSERT INTO anuncio (
          id, usuario_id, slug, titulo, descricao, status, status_moderacao,
          categoria, preco, whatsapp_normalizado, publicado_em, ultima_publicacao_em,
          criado_em, atualizado_em, removido_em, origem_importacao_id,
          atendimento_exclusivamente_virtual
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        anuncioId, proprietarioId, anuncio.slug(), anuncio.titulo(), anuncio.descricao(),
        plano.status().name(), plano.statusModeracao().name(), plano.categoria(),
        anuncio.preco(), anuncio.whatsappNormalizado(), anuncio.publicadoEm(),
        anuncio.publicadoEm(), anuncio.criadoEm(), snapshot.capturadoEm(),
        anuncio.removidoEm(), execucaoId, plano.atendimentoExclusivamenteVirtual());
    jdbc.update(
        """
        INSERT INTO anuncio_status_historico (
          id, anuncio_id, status_anterior, status_novo, motivo, ator_usuario_id, criado_em
        ) VALUES (?, ?, NULL, ?, 'IMPORTACAO_LEGADA_FASE_1', NULL, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        uuid("anuncio-status", anuncio.idOrigem()), anuncioId,
        plano.status().name(), anuncio.criadoEm());
    jdbc.update(
        """
        INSERT INTO anuncio_localizacao (
          anuncio_id, estado_id, cidade_id, bairro_id, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (anuncio_id) DO NOTHING
        """,
        anuncioId, localidade.estadoId(), localidade.cidadeId(), localidade.bairroId(),
        anuncio.criadoEm(), snapshot.capturadoEm());
    plano.servicos().stream().sorted().forEach(servico -> jdbc.update(
        "INSERT INTO anuncio_servicos VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
        anuncioId, servico, anuncio.criadoEm()));
    plano.locaisAtendimento().stream().sorted().forEach(local -> jdbc.update(
        "INSERT INTO anuncio_local_atendimento VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
        anuncioId, local, anuncio.criadoEm()));
    inserirBusca(anuncioId, anuncio, localidade, plano, snapshot.capturadoEm());
    inserirBloqueio(snapshot, execucaoId, anuncioId, proprietarioId, anuncio);
    return inserido;
  }

  private Map<String, MidiaIds> inserirMidiasAtuais(
      Snapshot snapshot, UUID execucaoId, UUID anuncioId, AnuncioLegado anuncio) {
    Map<String, MidiaIds> resultado = new LinkedHashMap<>();
    Set<String> posicoes = new HashSet<>();
    snapshot.manifesto().itensDoAnuncio(anuncio.idOrigem()).stream()
        .filter(Item::persistivel)
        .sorted(Comparator.comparingInt(Item::ordem).thenComparing(Item::idOrigem))
        .forEach(item -> {
          String posicao = item.finalidade() + ":" + item.ordem();
          if (posicoes.add(posicao)) {
            resultado.put(item.referenciaOrigemId(),
                inserirMidia(execucaoId, anuncioId, item, anuncio.criadoEm()));
          } else {
            mapear(execucaoId, "manifesto_midia", item.idOrigem(), hash(item),
                TipoEntidadeImportacao.MIDIA, null, "DIVERGENTE", snapshot.capturadoEm());
            pendencia(execucaoId, CodigoPendenciaImportacao.MIDIA_SEM_MANIFESTO,
                TipoEntidadeImportacao.MIDIA, item.idOrigem(), snapshot.capturadoEm());
          }
        });
    return resultado;
  }

  private MidiaIds inserirMidia(
      UUID execucaoId, UUID anuncioId, Item item, OffsetDateTime criadoEm) {
    UUID arquivoId = uuid("arquivo-midia", item.idOrigem());
    UUID vinculoId = uuid("anuncio-midia", item.idOrigem());
    jdbc.update(
        """
        INSERT INTO arquivo_midia (
          id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
          status_arquivo, criado_em
        ) VALUES (?, 'R2', ?, ?, ?, ?, 'VALIDADO', ?) ON CONFLICT (id) DO NOTHING
        """,
        arquivoId, item.bucketDestino(), item.chaveDestino(), item.mimeType(),
        item.tamanhoBytes(), criadoEm);
    String status = item.publicaValidaComprovada()
        ? "PUBLICAVEL"
        : ("REJEITADA".equals(item.statusModeracao()) ? "REJEITADA" : "PENDENTE");
    String visibilidade = "VIDEO".equals(item.tipo())
        ? "RESTRITA_18"
        : ("PUBLICAVEL".equals(status) ? item.visibilidade() : null);
    jdbc.update(
        """
        INSERT INTO anuncio_midia (
          id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
          criado_em, atualizado_em, visibilidade_midia
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
        """,
        vinculoId, anuncioId, arquivoId, item.tipo(), item.finalidade(), item.ordem(),
        status, criadoEm, criadoEm, visibilidade);
    mapear(execucaoId, "manifesto_midia", item.idOrigem(), hash(item),
        TipoEntidadeImportacao.MIDIA, vinculoId, "MAPEADO", criadoEm);
    return new MidiaIds(arquivoId, vinculoId);
  }

  private void inserirBusca(
      UUID anuncioId,
      AnuncioLegado anuncio,
      LocalidadeMapeada localidade,
      PlanejadorAnuncioImportacao.Plano plano,
      OffsetDateTime atualizadoEm) {
    String statusBusca = switch (plano.status()) {
      case PUBLICADO -> plano.publicavel() ? "PUBLICAVEL" : "NAO_PUBLICAVEL";
      case REMOVIDO -> "REMOVIDO";
      case BLOQUEADO, REJEITADO -> "NOINDEX";
      default -> "NAO_PUBLICAVEL";
    };
    String texto = anuncio.titulo()
        + (anuncio.descricao() == null ? "" : " " + anuncio.descricao());
    jdbc.update(
        """
        INSERT INTO documento_busca_anuncio (
          anuncio_id, texto_busca, estado_id, cidade_id, bairro_id, categoria,
          preco, status_publicacao, tem_midia_valida, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (anuncio_id) DO NOTHING
        """,
        anuncioId, texto, localidade.estadoId(), localidade.cidadeId(),
        localidade.bairroId(), plano.categoria(), anuncio.preco(), statusBusca,
        !plano.midiasValidas().isEmpty(), atualizadoEm);
  }

  private void inserirBloqueio(
      Snapshot snapshot,
      UUID execucaoId,
      UUID anuncioId,
      UUID proprietarioId,
      AnuncioLegado anuncio) {
    var bloqueio = anuncio.bloqueioJuridico();
    if (bloqueio == null || !bloqueio.ativo()) {
      return;
    }
    UUID ator = atorMapeado(snapshot, bloqueio.atorOrigemId());
    if (ator == null) {
      ator = snapshot.atorSistemaV3Id();
    }
    if (ator == null || !usuarioExiste(ator)) {
      throw new IllegalStateException("Ator canonico ausente para bloqueio importado");
    }
    String categoria = normalizar(bloqueio.categoria());
    if (!Set.of("DENUNCIA_GRAVE", "USO_NAO_AUTORIZADO_IMAGEM", "FRAUDE",
        "ORDEM_OU_RISCO_JURIDICO", "OUTRA_INTERVENCAO").contains(categoria)) {
      categoria = "OUTRA_INTERVENCAO";
    }
    String motivo = bloqueio.motivoSanitizado();
    if (motivo == null || motivo.length() < 5) {
      motivo = "Bloqueio juridico importado";
    }
    OffsetDateTime data = bloqueio.bloqueadoEm() == null
        ? anuncio.criadoEm()
        : bloqueio.bloqueadoEm();
    jdbc.update(
        """
        INSERT INTO anuncio_bloqueio_juridico (
          id, anuncio_id, usuario_id, escopo, categoria, motivo, observacao_interna,
          bloqueado_por_id, bloqueado_em, bloqueio_request_id
        ) VALUES (?, ?, ?, 'ANUNCIO', ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
        """,
        uuid("bloqueio-juridico", anuncio.idOrigem()), anuncioId, proprietarioId,
        categoria, motivo, bloqueio.observacaoSanitizada(), ator, data,
        "importacao-bloqueio-" + uuid("request-bloqueio", anuncio.idOrigem()));
    pendencia(execucaoId, CodigoPendenciaImportacao.ANUNCIO_BLOQUEIO_JURIDICO_PRESERVADO,
        TipoEntidadeImportacao.ANUNCIO, anuncio.idOrigem(), snapshot.capturadoEm());
  }

  private ProcessamentoRevisao inserirRevisao(
      Snapshot snapshot,
      UUID execucaoId,
      UUID anuncioId,
      String anuncioOrigemId,
      RevisaoLegada revisao,
      Map<String, MidiaIds> midias) {
    String tipo = normalizar(revisao.tipo());
    String status = normalizar(revisao.status());
    if (!TIPOS_REVISAO.contains(tipo) || !STATUS_REVISAO.contains(status)) {
      mapear(execucaoId, "anuncio_revisions", revisao.idOrigem(), hash(revisao),
          TipoEntidadeImportacao.REVISAO_ANUNCIO, null, "DIVERGENTE",
          snapshot.capturadoEm());
      pendencia(execucaoId, CodigoPendenciaImportacao.REVISAO_INCONSISTENTE,
          TipoEntidadeImportacao.REVISAO_ANUNCIO, revisao.idOrigem(),
          snapshot.capturadoEm());
      return new ProcessamentoRevisao(0, 0);
    }
    UUID revisaoId = uuid("revisao-anuncio", revisao.idOrigem());
    UUID ator = atorMapeado(snapshot, revisao.atorOrigemId());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("origem", revisao.origem());
    payload.put("numeroLegado", revisao.numero());
    payload.put("classificacaoConteudo", revisao.classificacaoConteudo());
    payload.put("motivoSanitizado", revisao.motivoSanitizado());
    payload.put("servicos", revisao.servicos().stream()
        .map(RevisaoServicoLegado::servico).map(ImportadorAnunciosFaseUm::normalizar)
        .distinct().sorted().toList());
    payload.put("locaisAtendimento", revisao.locaisAtendimento().stream()
        .map(RevisaoLocalAtendimentoLegado::localAtendimento)
        .map(ImportadorAnunciosFaseUm::normalizar).distinct().sorted().toList());
    int novaRevisao = jdbc.update(
        """
        INSERT INTO revisao_anuncio (
          id, anuncio_id, tipo, status, payload_solicitado, criado_por,
          criado_em, finalizado_em
        ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?) ON CONFLICT (id) DO NOTHING
        """,
        revisaoId, anuncioId, tipo, status, json(payload), ator,
        revisao.submetidaEm(), revisao.finalizadaEm());
    mapear(execucaoId, "anuncio_revisions", revisao.idOrigem(), hash(revisao),
        TipoEntidadeImportacao.REVISAO_ANUNCIO, revisaoId, "MAPEADO",
        snapshot.capturadoEm());
    inserirDecisao(revisaoId, revisao, ator);
    int filhos = importarValoresRevisao(snapshot, execucaoId, revisaoId, revisao);
    for (RevisaoMidiaLegada midia : revisao.midias()) {
      filhos += inserirMidiaRevisao(snapshot, execucaoId, revisaoId,
          anuncioOrigemId, midia, midias);
    }
    return new ProcessamentoRevisao(novaRevisao, filhos);
  }

  private void inserirDecisao(UUID revisaoId, RevisaoLegada revisao, UUID ator) {
    String decisao = switch (normalizar(revisao.status())) {
      case "APROVADA" -> "APROVAR";
      case "REJEITADA" -> "REJEITAR";
      case "CANCELADA" -> "CANCELAR";
      default -> null;
    };
    if (decisao == null) {
      return;
    }
    OffsetDateTime data = revisao.finalizadaEm() == null
        ? revisao.submetidaEm()
        : revisao.finalizadaEm();
    jdbc.update(
        """
        INSERT INTO decisao_moderacao (
          id, revisao_anuncio_id, decisao, motivo, ator_usuario_id, criado_em
        ) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
        """,
        uuid("decisao-moderacao", revisao.idOrigem()), revisaoId, decisao,
        revisao.motivoSanitizado(), ator, data);
  }

  private int importarValoresRevisao(
      Snapshot snapshot, UUID execucaoId, UUID revisaoId, RevisaoLegada revisao) {
    int novos = 0;
    Set<String> vistos = new HashSet<>();
    for (RevisaoServicoLegado filho : revisao.servicos()) {
      String chave = "SERVICO:" + normalizar(filho.servico());
      if (!vistos.add(chave)) {
        filhoDuplicado(snapshot, execucaoId, "anuncio_revision_services", filho.idOrigem());
      } else if (mapear(execucaoId, "anuncio_revision_services", filho.idOrigem(), hash(filho),
          TipoEntidadeImportacao.REVISAO_FILHO, revisaoId, "MAPEADO",
          snapshot.capturadoEm())) {
        novos++;
      }
    }
    for (RevisaoLocalAtendimentoLegado filho : revisao.locaisAtendimento()) {
      String chave = "LOCAL:" + normalizar(filho.localAtendimento());
      if (!vistos.add(chave)) {
        filhoDuplicado(snapshot, execucaoId, "anuncio_revision_local", filho.idOrigem());
      } else if (mapear(execucaoId, "anuncio_revision_local", filho.idOrigem(), hash(filho),
          TipoEntidadeImportacao.REVISAO_FILHO, revisaoId, "MAPEADO",
          snapshot.capturadoEm())) {
        novos++;
      }
    }
    return novos;
  }

  private void filhoDuplicado(
      Snapshot snapshot, UUID execucaoId, String tabela, String idOrigem) {
    mapear(execucaoId, tabela, idOrigem, null, TipoEntidadeImportacao.REVISAO_FILHO,
        null, "DIVERGENTE", snapshot.capturadoEm());
    pendencia(execucaoId, CodigoPendenciaImportacao.REVISAO_FILHO_DUPLICADO_DESCARTADO,
        TipoEntidadeImportacao.REVISAO_FILHO, idOrigem, snapshot.capturadoEm());
  }

  private int inserirMidiaRevisao(
      Snapshot snapshot,
      UUID execucaoId,
      UUID revisaoId,
      String anuncioOrigemId,
      RevisaoMidiaLegada midia,
      Map<String, MidiaIds> midias) {
    Item item = snapshot.manifesto().porReferencia(midia.referenciaManifestoId())
        .filter(valor -> valor.anuncioOrigemId().equals(anuncioOrigemId))
        .filter(Item::persistivel)
        .orElse(null);
    String acao = normalizar(midia.acao());
    String status = normalizar(midia.status());
    boolean consistente = item != null && ACOES_MIDIA.contains(acao)
        && STATUS_MIDIA.contains(status)
        && (!"REORDENAR".equals(acao) || midia.ordem() != null);
    MidiaIds ids = item == null ? null : midias.get(item.referenciaOrigemId());
    if (!consistente || ids == null) {
      mapear(execucaoId, "anuncio_revision_media", midia.idOrigem(), hash(midia),
          TipoEntidadeImportacao.REVISAO_FILHO, null, "DIVERGENTE",
          snapshot.capturadoEm());
      pendencia(execucaoId, CodigoPendenciaImportacao.REVISAO_MIDIA_SEM_MANIFESTO,
          TipoEntidadeImportacao.REVISAO_FILHO, midia.idOrigem(), snapshot.capturadoEm());
      return 0;
    }
    UUID filhoId = uuid("revisao-midia", midia.idOrigem());
    UUID vinculoId = "ADICIONAR".equals(acao) ? null : ids.vinculoId();
    UUID arquivoId = Set.of("ADICIONAR", "SUBSTITUIR").contains(acao)
        ? ids.arquivoId()
        : null;
    jdbc.update(
        """
        INSERT INTO anuncio_midia_revisao (
          id, revisao_anuncio_id, anuncio_midia_id, arquivo_midia_id, acao,
          status, ordem, motivo, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
        """,
        filhoId, revisaoId, vinculoId, arquivoId, acao, status, midia.ordem(),
        midia.motivoSanitizado(), snapshot.capturadoEm(), snapshot.capturadoEm());
    return mapear(execucaoId, "anuncio_revision_media", midia.idOrigem(), hash(midia),
        TipoEntidadeImportacao.REVISAO_FILHO, filhoId, "MAPEADO",
        snapshot.capturadoEm()) ? 1 : 0;
  }

  private Processamento quarentena(
      Snapshot snapshot,
      UUID execucaoId,
      AnuncioLegado anuncio,
      List<CodigoPendenciaImportacao> pendencias) {
    List<CodigoPendenciaImportacao> codigos = pendencias.isEmpty()
        ? List.of(CodigoPendenciaImportacao.ANUNCIO_STATUS_DESCONHECIDO)
        : pendencias.stream().distinct().toList();
    mapear(execucaoId, TABELA_ANUNCIO, anuncio.idOrigem(), hash(anuncio),
        TipoEntidadeImportacao.ANUNCIO, null, "DIVERGENTE", snapshot.capturadoEm());
    codigos.forEach(codigo -> pendencia(execucaoId, codigo,
        TipoEntidadeImportacao.ANUNCIO, anuncio.idOrigem(), snapshot.capturadoEm()));
    jdbc.update(
        """
        UPDATE stg_anuncio SET status = 'PENDENTE_REVISAO', pendencia_codigo = ?,
          processado_em = ? WHERE execucao_id = ? AND id_origem = ?
        """,
        codigos.get(0).name(), snapshot.capturadoEm(), execucaoId, anuncio.idOrigem());
    return new Processamento(1, 0, 0, 0);
  }

  private ResultadoImportacaoAnuncios finalizar(
      Snapshot snapshot, UUID execucaoId, Contadores chamada) {
    long importados = contarMapeamentos(execucaoId, TipoEntidadeImportacao.ANUNCIO, "MAPEADO");
    long quarentena = contarMapeamentos(
        execucaoId, TipoEntidadeImportacao.ANUNCIO, "DIVERGENTE");
    long publicos = contarAnuncios(execucaoId, "PUBLICADO");
    long emRevisao = contarAnuncios(execucaoId, "PENDENTE_REVISAO");
    long revisoes = contarMapeamentos(
        execucaoId, TipoEntidadeImportacao.REVISAO_ANUNCIO, "MAPEADO");
    long filhos = contarMapeamentos(
        execucaoId, TipoEntidadeImportacao.REVISAO_FILHO, "MAPEADO");
    long restantes = jdbc.queryForObject(
        "SELECT count(*) FROM stg_anuncio WHERE execucao_id = ? AND status = 'PENDENTE'",
        Long.class, execucaoId);
    long pendencias = jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia WHERE execucao_id = ? AND status = 'ABERTA'",
        Long.class, execucaoId);
    String status = restantes > 0
        ? "EM_EXECUCAO"
        : (pendencias > 0 ? "CONCLUIDA_COM_PENDENCIAS" : "CONCLUIDA");
    Map<String, Object> resumo = new LinkedHashMap<>();
    resumo.put("snapshotFingerprint", hash(snapshot));
    resumo.put("snapshotId", snapshot.snapshotId());
    resumo.put("analisados", snapshot.anuncios().size());
    resumo.put("importados", importados);
    resumo.put("publicos", publicos);
    resumo.put("emRevisao", emRevisao);
    resumo.put("quarentena", quarentena);
    resumo.put("descartados", 0);
    resumo.put("revisoesImportadas", revisoes);
    resumo.put("filhosImportados", filhos);
    resumo.put("restantes", restantes);
    jdbc.update(
        """
        UPDATE importacao_execucao SET status = ?, finalizado_em = ?, resumo_json = ?::jsonb
        WHERE id = ?
        """,
        status, restantes == 0 ? snapshot.capturadoEm() : null, json(resumo), execucaoId);
    return new ResultadoImportacaoAnuncios(
        execucaoId, status, snapshot.anuncios().size(), importados, publicos,
        emRevisao, quarentena, 0, revisoes, filhos, chamada.processados,
        chamada.anuncios, chamada.revisoes, chamada.filhos, restantes);
  }

  private long contarMapeamentos(
      UUID execucaoId, TipoEntidadeImportacao tipo, String status) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM importacao_mapeamento "
            + "WHERE execucao_id = ? AND entidade_tipo = ? AND status = ?",
        Long.class, execucaoId, tipo.name(), status);
  }

  private long contarAnuncios(UUID execucaoId, String status) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM anuncio a JOIN importacao_mapeamento m
          ON m.entidade_v3_id = a.id AND m.entidade_tipo = 'ANUNCIO'
        WHERE m.execucao_id = ? AND m.status = 'MAPEADO' AND a.status = ?
        """,
        Long.class, execucaoId, status);
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
        uuid("mapeamento", execucaoId.toString(), tabela, idOrigem), execucaoId,
        ORIGEM, tabela, idOrigem, hash, tipo.name(), entidadeId, status,
        criadoEm, criadoEm) == 1;
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
        ) VALUES (?, ?, ?, ?, 'ABERTA', ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
        """,
        uuid("pendencia", execucaoId.toString(), codigo.name(), tipo.name(), idOrigem),
        execucaoId, codigo.name(), severidadeBanco(codigo.severidadePadrao()),
        tipo.name(), idOrigem, "Revisao manual requerida: " + codigo.name(), criadoEm);
  }

  private boolean usuarioExiste(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) > 0 FROM usuario WHERE id = ?", Boolean.class, id);
  }

  private boolean usuarioAtivo(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) > 0 FROM usuario WHERE id = ? AND status = 'ATIVO'",
        Boolean.class, id);
  }

  private boolean localidadeExiste(LocalidadeMapeada localidade) {
    if (localidade == null || !localidade.correspondenciaSegura()) {
      return false;
    }
    if (localidade.bairroId() == null) {
      return jdbc.queryForObject(
          "SELECT count(*) > 0 FROM cidade WHERE id = ? AND estado_id = ?",
          Boolean.class, localidade.cidadeId(), localidade.estadoId());
    }
    return jdbc.queryForObject(
        """
        SELECT count(*) > 0 FROM cidade c JOIN bairro b ON b.cidade_id = c.id
        WHERE c.id = ? AND c.estado_id = ? AND b.id = ?
        """,
        Boolean.class, localidade.cidadeId(), localidade.estadoId(), localidade.bairroId());
  }

  private boolean slugDisponivel(String slug, UUID anuncioId) {
    return jdbc.queryForObject(
        "SELECT count(*) = 0 FROM anuncio WHERE slug = ? AND id <> ?",
        Boolean.class, slug, anuncioId);
  }

  private UUID atorMapeado(Snapshot snapshot, String atorOrigemId) {
    if (atorOrigemId == null) {
      return null;
    }
    UUID ator = snapshot.atoresV3().get(atorOrigemId);
    return ator != null && usuarioExiste(ator) ? ator : null;
  }

  private boolean bloqueante(CodigoPendenciaImportacao codigo) {
    return codigo.severidadePadrao() == SeveridadePendenciaImportacao.BLOQUEANTE;
  }

  private String hash(Object valor) {
    try {
      byte[] serializado = objectMapper.writeValueAsBytes(valor);
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(serializado));
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar hash do snapshot", exception);
    }
  }

  private String json(Object valor) {
    try {
      return objectMapper.writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao serializar metadados da importacao", exception);
    }
  }

  private static UUID uuid(String... partes) {
    return UUID.nameUUIDFromBytes(
        String.join(":", partes).getBytes(StandardCharsets.UTF_8));
  }

  private static String normalizar(String valor) {
    return valor == null ? null : valor.trim().toUpperCase(Locale.ROOT);
  }

  private static String severidadeBanco(SeveridadePendenciaImportacao severidade) {
    return switch (severidade) {
      case INFO -> "INFO";
      case ALERTA -> "MEDIA";
      case ERRO -> "ALTA";
      case BLOQUEANTE -> "CRITICA";
    };
  }

  private record MidiaIds(UUID arquivoId, UUID vinculoId) {
  }

  private record Processamento(int processados, int anuncios, int revisoes, int filhos) {
    private static Processamento vazio() {
      return new Processamento(0, 0, 0, 0);
    }
  }

  private record ProcessamentoRevisao(int revisoes, int filhos) {
  }

  private static final class Contadores {
    private long processados;
    private long anuncios;
    private long revisoes;
    private long filhos;

    private void somar(Processamento resultado) {
      processados += resultado.processados();
      anuncios += resultado.anuncios();
      revisoes += resultado.revisoes();
      filhos += resultado.filhos();
    }
  }
}
