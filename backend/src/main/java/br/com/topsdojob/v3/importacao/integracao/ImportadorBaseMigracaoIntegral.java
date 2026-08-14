package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.integracao.PoliticaKycMigracaoIntegral.Consolidacao;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.CredencialLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.DocumentoKycLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.FavoritoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.LocalidadeLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.MetricaAnuncioLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.OrfaoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.Snapshot;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoLocalidade;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoOrfao;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.UsuarioLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.UsuarioStagingLegado;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class ImportadorBaseMigracaoIntegral {

  private static final String ORIGEM = "TOPSDOJOB_LEGADO";
  private static final Set<String> STATUS_USUARIO = Set.of(
      "ATIVO", "PENDENTE", "SUSPENSO", "DESATIVADO", "IMPORTADO", "EXCLUIDO");
  private static final Set<String> TIPOS_CONTA = Set.of("ANUNCIANTE", "STAFF", "SISTEMA");
  private static final Set<String> PAPEIS = Set.of("ADMIN", "MODERADOR", "COMERCIAL", "USUARIO");
  private static final Set<String> TIPOS_DOCUMENTO = Set.of(
      "IDENTIDADE", "VERIFICACAO_IDADE", "COMPROVANTE", "OUTRO");
  private static final Set<String> PARTES_DOCUMENTO = Set.of("UNICO", "FRENTE", "VERSO");
  private static final Set<String> STATUS_DOCUMENTO = Set.of(
      "PENDENTE", "EM_ANALISE", "VALIDADO", "REJEITADO", "AJUSTE_SOLICITADO");

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacao;
  private final ObjectMapper mapper;

  public ImportadorBaseMigracaoIntegral(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      ObjectMapper mapper) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transacao = new TransactionTemplate(transactionManager);
    this.mapper = mapper;
  }

  public ResultadoLote importarLocalidades(Snapshot snapshot, UUID execucaoId, int limite) {
    prepararLocalidades(snapshot, execucaoId);
    List<LocalidadeLegada> pendentes = pendentesLocalidades(snapshot, execucaoId, limite);
    long quarentenas = 0;
    for (LocalidadeLegada localidade : pendentes) {
      boolean importada = importarLocalidadeIsolada(snapshot, execucaoId, localidade);
      if (!importada) {
        quarentenas++;
      }
    }
    return new ResultadoLote(
        pendentes.size(),
        contarStagePendente("stg_localidade", execucaoId),
        quarentenas,
        0,
        0);
  }

  public ResultadoLote importarUsuarios(Snapshot snapshot, UUID execucaoId, int limite) {
    validarUnicidadeUsuarios(snapshot.usuarios());
    prepararUsuarios(snapshot, execucaoId);
    List<UsuarioLegado> pendentes = pendentesUsuarios(snapshot, execucaoId, limite);
    long quarentenas = 0;
    for (UsuarioLegado usuario : pendentes) {
      boolean importado = importarUsuarioIsolado(snapshot, execucaoId, usuario);
      if (!importado) {
        quarentenas++;
      }
    }
    return new ResultadoLote(
        pendentes.size(),
        contarStagePendente("stg_usuario", execucaoId),
        quarentenas,
        0,
        0);
  }

  public ResultadoLote importarCredenciais(Snapshot snapshot, UUID execucaoId, int limite) {
    List<CredencialLegada> pendentes = snapshot.credenciais().stream()
        .filter(item -> !mapeado(execucaoId, "credenciais", item.idOrigem()))
        .sorted(Comparator.comparing(CredencialLegada::idOrigem))
        .limit(limite)
        .toList();
    long quarentenas = 0;
    for (CredencialLegada credencial : pendentes) {
      if (!Boolean.TRUE.equals(transacao.execute(
          ignored -> importarCredencial(snapshot, execucaoId, credencial)))) {
        quarentenas++;
      }
    }
    long restantes = snapshot.credenciais().stream()
        .filter(item -> !mapeado(execucaoId, "credenciais", item.idOrigem()))
        .count();
    return new ResultadoLote(pendentes.size(), restantes, quarentenas, 0, 0);
  }

  public ResultadoLote registrarPlanejamentoKycEOrfaos(
      Snapshot snapshot,
      UUID execucaoId,
      Consolidacao consolidacao) {
    return transacao.execute(status -> {
      consolidacao.historicosConsolidados().forEach(documento -> mapear(
          execucaoId,
          "documentos_kyc",
          documento.idOrigem(),
          hash(documento),
          "DOCUMENTO_KYC_HISTORICO",
          null,
          "REJEITADO",
          snapshot.capturadoEm()));
      consolidacao.quarentena().forEach(documento -> {
        String codigo = consolidacao.codigoQuarentena(documento);
        mapear(
            execucaoId,
            "documentos_kyc",
            documento.idOrigem(),
            hash(documento),
            "DOCUMENTO_KYC",
            null,
            "DIVERGENTE",
            snapshot.capturadoEm());
        pendencia(
            execucaoId,
            codigo,
            "ALTA",
            "DOCUMENTO_KYC",
            documento.idOrigem(),
            "Documento KYC sem contexto operacional seguro para importacao",
            snapshot.capturadoEm());
      });
      for (OrfaoLegado orfao : snapshot.orfaos()) {
        registrarOrfao(snapshot, execucaoId, orfao);
      }
      long stagingQuarentena = registrarUsuariosStaging(snapshot, execucaoId);
      return new ResultadoLote(
          consolidacao.historicosConsolidados().size()
              + consolidacao.quarentena().size()
              + snapshot.orfaos().size()
              + snapshot.usuariosStaging().size(),
          0,
          consolidacao.quarentena().size()
              + snapshot.orfaos().stream().filter(item -> item.tipo() != TipoOrfao.SUPORTE).count()
              + stagingQuarentena,
          0,
          0);
    });
  }

  public ResultadoLote importarKyc(
      Snapshot snapshot,
      UUID execucaoId,
      Consolidacao consolidacao,
      int limite,
      UUID atorSistemaId) {
    Map<String, Item> manifesto = consolidacao.manifestoSeguro().itens().stream()
        .collect(Collectors.toMap(Item::idOrigem, Function.identity(), (a, b) -> a));
    List<DocumentoKycLegado> pendentes = consolidacao.canonicos().stream()
        .filter(item -> !mapeado(execucaoId, "documentos_kyc", item.idOrigem()))
        .sorted(Comparator.comparing(DocumentoKycLegado::idOrigem))
        .limit(limite)
        .toList();
    long quarentenas = 0;
    long bytes = 0;
    for (DocumentoKycLegado documento : pendentes) {
      Item item = manifesto.get(documento.manifestoItemId());
      boolean importado = Boolean.TRUE.equals(transacao.execute(
          ignored -> importarDocumentoKyc(
              snapshot, execucaoId, documento, item, atorSistemaId)));
      if (!importado) {
        quarentenas++;
      } else if (item != null) {
        bytes += item.tamanhoBytes();
      }
    }
    long restantes = consolidacao.canonicos().stream()
        .filter(item -> !mapeado(execucaoId, "documentos_kyc", item.idOrigem()))
        .count();
    return new ResultadoLote(pendentes.size(), restantes, quarentenas, 0, bytes);
  }

  public ResultadoLote importarComplementos(
      Snapshot snapshot,
      UUID execucaoId,
      String fingerprintPacote) {
    return transacao.execute(status -> {
      long processados = 0;
      long quarentenas = 0;
      for (FavoritoLegado favorito : snapshot.favoritos()) {
        if (mapeado(execucaoId, "favoritos", favorito.idOrigem())) {
          continue;
        }
        processados++;
        UUID usuarioId = destinoMapeado(execucaoId, "usuarios", favorito.usuarioOrigemId());
        UUID anuncioId = destinoMapeadoQualquerExecucao("anuncios", favorito.anuncioOrigemId());
        if (usuarioId == null || anuncioId == null) {
          quarentenas++;
          mapearComplementoDivergente(
              snapshot, execucaoId, "favoritos", favorito.idOrigem(), hash(favorito));
          continue;
        }
        UUID id = IdsMigracaoIntegral.uuid("favorito", favorito.idOrigem());
        jdbc.update(
            """
            INSERT INTO favorito_anuncio (id, usuario_id, anuncio_id, criado_em)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (usuario_id, anuncio_id) DO NOTHING
            """,
            id,
            usuarioId,
            anuncioId,
            favorito.criadoEm());
        mapear(
            execucaoId, "favoritos", favorito.idOrigem(), hash(favorito),
            "FAVORITO", id, "MAPEADO", snapshot.capturadoEm());
      }
      for (MetricaAnuncioLegada metrica : snapshot.metricas()) {
        if (mapeado(execucaoId, "metricas_anuncio", metrica.idOrigem())) {
          continue;
        }
        processados++;
        UUID anuncioId = destinoMapeadoQualquerExecucao("anuncios", metrica.anuncioOrigemId());
        if (anuncioId == null) {
          quarentenas++;
          mapearComplementoDivergente(
              snapshot, execucaoId, "metricas_anuncio", metrica.idOrigem(), hash(metrica));
          continue;
        }
        UUID id = IdsMigracaoIntegral.uuid("metrica-inicial", metrica.idOrigem());
        String origemHash = hash(metrica).substring(0, 32);
        jdbc.update(
            """
            INSERT INTO agregado_visualizacao_inicial (
              id, anuncio_id, execucao_id, total_visualizacoes,
              snapshot_fingerprint, origem_hash, snapshot_corte_em,
              criado_em, atualizado_em
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (anuncio_id) DO NOTHING
            """,
            id,
            anuncioId,
            execucaoId,
            metrica.totalVisualizacoes(),
            fingerprintPacote,
            origemHash,
            metrica.corteEm(),
            snapshot.capturadoEm(),
            snapshot.capturadoEm());
        mapear(
            execucaoId, "metricas_anuncio", metrica.idOrigem(), hash(metrica),
            "METRICA", id, "MAPEADO", snapshot.capturadoEm());
      }
      return new ResultadoLote(processados, 0, quarentenas, 0, 0);
    });
  }

  public ReconciliacaoBase reconciliar(Snapshot snapshot, UUID execucaoId) {
    long movimentosOrfaos = jdbc.queryForObject(
        """
        SELECT count(*)
          FROM movimento_credito mc
         WHERE mc.tipo = 'MIGRACAO_SALDO_INICIAL'
           AND NOT EXISTS (
             SELECT 1 FROM importacao_mapeamento im
              WHERE im.entidade_tipo = 'USUARIO'
                AND im.entidade_v3_id = mc.usuario_id
                AND im.status = 'MAPEADO'
           )
        """,
        Long.class);
    long suportePendente = jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_pendencia
         WHERE execucao_id = ? AND codigo = 'SUPORTE_ORFAO'
        """,
        Long.class,
        execucaoId);
    long kycCompartilhado = jdbc.queryForObject(
        """
        SELECT count(*)
          FROM documento_usuario primeiro
          JOIN documento_usuario segundo
            ON segundo.arquivo_midia_id = primeiro.arquivo_midia_id
           AND segundo.usuario_id <> primeiro.usuario_id
         WHERE primeiro.id < segundo.id
        """,
        Long.class);
    long documentosQuarentenaPersistidos = consolidarIdsQuarentena(snapshot, execucaoId).stream()
        .filter(id -> jdbc.queryForObject(
            "SELECT count(*) FROM documento_usuario WHERE id = ?", Long.class, id) > 0)
        .count();
    return new ReconciliacaoBase(
        movimentosOrfaos,
        suportePendente,
        kycCompartilhado,
        documentosQuarentenaPersistidos,
        movimentosOrfaos == 0
            && suportePendente == 0
            && kycCompartilhado == 0
            && documentosQuarentenaPersistidos == 0);
  }

  Map<String, UUID> usuariosMapeados(Snapshot snapshot, UUID execucaoId) {
    Map<String, UUID> resultado = new LinkedHashMap<>();
    snapshot.usuarios().forEach(usuario -> {
      UUID id = destinoMapeado(execucaoId, "usuarios", usuario.idOrigem());
      if (id != null) {
        resultado.put(usuario.idOrigem(), id);
      }
    });
    return Map.copyOf(resultado);
  }

  Map<String, UUID> localidadesMapeadas(Snapshot snapshot, UUID execucaoId) {
    Map<String, UUID> resultado = new LinkedHashMap<>();
    snapshot.localidades().forEach(localidade -> {
      UUID id = destinoMapeado(execucaoId, tabelaLocalidade(localidade.tipo()), localidade.idOrigem());
      if (id != null) {
        resultado.put(localidade.idOrigem(), id);
      }
    });
    return Map.copyOf(resultado);
  }

  UUID destinoMapeadoQualquerExecucao(String tabela, String idOrigem) {
    return jdbc.query(
        """
        SELECT entidade_v3_id
          FROM importacao_mapeamento
         WHERE sistema_origem = ?
           AND tabela_origem = ?
           AND id_origem = ?
           AND status = 'MAPEADO'
           AND entidade_v3_id IS NOT NULL
         ORDER BY atualizado_em DESC, id DESC
         LIMIT 1
        """,
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        ORIGEM,
        tabela,
        idOrigem).stream().findFirst().orElse(null);
  }

  private void prepararLocalidades(Snapshot snapshot, UUID execucaoId) {
    transacao.executeWithoutResult(status -> snapshot.localidades().forEach(localidade -> jdbc.update(
        """
        INSERT INTO stg_localidade (
          id, execucao_id, sistema_origem, tabela_origem, id_origem,
          hash_origem, payload_normalizado_json, status, criado_em
        ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDENTE', ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem) DO NOTHING
        """,
        IdsMigracaoIntegral.uuid("stage-localidade", execucaoId.toString(), localidade.idOrigem()),
        execucaoId,
        ORIGEM,
        tabelaLocalidade(localidade.tipo()),
        localidade.idOrigem(),
        hash(localidade),
        json(Map.of("tipo", localidade.tipo().name())),
        snapshot.capturadoEm())));
  }

  private void prepararUsuarios(Snapshot snapshot, UUID execucaoId) {
    transacao.executeWithoutResult(status -> snapshot.usuarios().forEach(usuario -> jdbc.update(
        """
        INSERT INTO stg_usuario (
          id, execucao_id, sistema_origem, tabela_origem, id_origem,
          hash_origem, payload_normalizado_json, status, criado_em
        ) VALUES (?, ?, ?, 'usuarios', ?, ?, CAST(? AS jsonb), 'PENDENTE', ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem) DO NOTHING
        """,
        IdsMigracaoIntegral.uuid("stage-usuario", execucaoId.toString(), usuario.idOrigem()),
        execucaoId,
        ORIGEM,
        usuario.idOrigem(),
        hash(usuario),
        json(Map.of("tipoConta", usuario.tipoConta(), "status", usuario.status())),
        snapshot.capturadoEm())));
  }

  private List<LocalidadeLegada> pendentesLocalidades(
      Snapshot snapshot,
      UUID execucaoId,
      int limite) {
    Map<String, LocalidadeLegada> porChave = snapshot.localidades().stream()
        .collect(Collectors.toMap(
            item -> tabelaLocalidade(item.tipo()) + ":" + item.idOrigem(),
            Function.identity()));
    return jdbc.query(
        """
        SELECT tabela_origem, id_origem
          FROM stg_localidade
         WHERE execucao_id = ? AND status = 'PENDENTE'
         ORDER BY CASE tabela_origem
           WHEN 'estados' THEN 1 WHEN 'cidades' THEN 2 ELSE 3 END,
           id_origem
         LIMIT ?
        """,
        (rs, rowNum) -> porChave.get(rs.getString(1) + ":" + rs.getString(2)),
        execucaoId,
        limite).stream().filter(Objects::nonNull).toList();
  }

  private List<UsuarioLegado> pendentesUsuarios(Snapshot snapshot, UUID execucaoId, int limite) {
    Map<String, UsuarioLegado> porId = snapshot.usuarios().stream()
        .collect(Collectors.toMap(UsuarioLegado::idOrigem, Function.identity()));
    return jdbc.query(
        """
        SELECT id_origem FROM stg_usuario
         WHERE execucao_id = ? AND status = 'PENDENTE'
         ORDER BY id_origem LIMIT ?
        """,
        (rs, rowNum) -> porId.get(rs.getString(1)),
        execucaoId,
        limite).stream().filter(Objects::nonNull).toList();
  }

  private boolean importarLocalidade(
      Snapshot snapshot,
      UUID execucaoId,
      LocalidadeLegada localidade) {
    UUID id = IdsMigracaoIntegral.uuid(
        "localidade", localidade.tipo().name(), localidade.idOrigem());
    UUID pai = localidade.paiOrigemId() == null ? null : destinoMapeado(
        execucaoId,
        localidade.tipo() == TipoLocalidade.CIDADE ? "estados" : "cidades",
        localidade.paiOrigemId());
    if (localidade.tipo() != TipoLocalidade.ESTADO && pai == null) {
      return quarentenaLocalidade(snapshot, execucaoId, localidade, "LOCALIDADE_PAI_AUSENTE");
    }
    if (localidade.tipo() == TipoLocalidade.ESTADO) {
        if (localidade.uf() == null || !localidade.uf().matches("^[A-Z]{2}$")) {
          return quarentenaLocalidade(snapshot, execucaoId, localidade, "LOCALIDADE_UF_INVALIDA");
        }
        jdbc.update(
            """
            INSERT INTO estado (id, uf, nome, nome_normalizado, criado_em)
            VALUES (?, ?, ?, ?, ?) ON CONFLICT (uf) DO NOTHING
            """,
            id, localidade.uf(), localidade.nome(), localidade.nomeNormalizado(), localidade.criadoEm());
        id = jdbc.queryForObject("SELECT id FROM estado WHERE uf = ?", UUID.class, localidade.uf());
    } else if (localidade.tipo() == TipoLocalidade.CIDADE) {
        jdbc.update(
            """
            INSERT INTO cidade (id, estado_id, nome, nome_normalizado, slug, criado_em)
            VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (estado_id, nome_normalizado) DO NOTHING
            """,
            id, pai, localidade.nome(), localidade.nomeNormalizado(), localidade.slug(),
            localidade.criadoEm());
        id = jdbc.queryForObject(
            "SELECT id FROM cidade WHERE estado_id = ? AND nome_normalizado = ?",
            UUID.class, pai, localidade.nomeNormalizado());
    } else {
        jdbc.update(
            """
            INSERT INTO bairro (id, cidade_id, nome, nome_normalizado, slug, criado_em)
            VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (cidade_id, nome_normalizado) DO NOTHING
            """,
            id, pai, localidade.nome(), localidade.nomeNormalizado(), localidade.slug(),
            localidade.criadoEm());
        id = jdbc.queryForObject(
            "SELECT id FROM bairro WHERE cidade_id = ? AND nome_normalizado = ?",
            UUID.class, pai, localidade.nomeNormalizado());
    }
    concluirStage("stg_localidade", execucaoId, localidade.idOrigem(), id, null);
    mapear(
        execucaoId,
        tabelaLocalidade(localidade.tipo()),
        localidade.idOrigem(),
        hash(localidade),
        localidade.tipo().name(),
        id,
        "MAPEADO",
        snapshot.capturadoEm());
    return true;
  }

  private boolean importarUsuario(Snapshot snapshot, UUID execucaoId, UsuarioLegado usuario) {
    if (!STATUS_USUARIO.contains(usuario.status()) || !TIPOS_CONTA.contains(usuario.tipoConta())) {
      return quarentenaUsuario(snapshot, execucaoId, usuario, "USUARIO_STATUS_INVALIDO");
    }
    int inseridos = jdbc.update(
          """
          INSERT INTO usuario (
            id, nome, email_normalizado, telefone_normalizado, status, tipo_conta,
            email_verificado_em, telefone_verificado_em, criado_em, atualizado_em,
            desativado_em, nome_civil, cpf_normalizado, data_nascimento
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT (id) DO NOTHING
          """,
          usuario.idV3(), usuario.nomePublico(), usuario.emailNormalizado(),
          usuario.telefoneNormalizado(), usuario.status(), usuario.tipoConta(),
          usuario.emailVerificadoEm(), usuario.telefoneVerificadoEm(), usuario.criadoEm(),
          usuario.atualizadoEm(), usuario.desativadoEm(), usuario.nomeCivil(),
          usuario.cpfNormalizado(), usuario.dataNascimento());
    if (inseridos == 0 && !usuarioCompativel(usuario)) {
      return quarentenaUsuario(snapshot, execucaoId, usuario, "USUARIO_DESTINO_DIVERGENTE");
    }
    Set<String> papeis = usuario.papeis().isEmpty() && "ANUNCIANTE".equals(usuario.tipoConta())
        ? Set.of("USUARIO")
        : usuario.papeis();
    for (String papel : papeis) {
      String normalizado = papel.toUpperCase(Locale.ROOT);
      if (!PAPEIS.contains(normalizado)) {
        return quarentenaUsuario(snapshot, execucaoId, usuario, "USUARIO_PAPEL_INVALIDO");
      }
      jdbc.update(
            """
            INSERT INTO papel_usuario (usuario_id, papel, criado_por, criado_em)
            VALUES (?, ?, NULL, ?) ON CONFLICT (usuario_id, papel) DO NOTHING
            """,
          usuario.idV3(), normalizado, usuario.criadoEm());
    }
    concluirStage("stg_usuario", execucaoId, usuario.idOrigem(), usuario.idV3(), null);
    mapear(
        execucaoId, "usuarios", usuario.idOrigem(), hash(usuario),
        "USUARIO", usuario.idV3(), "MAPEADO", snapshot.capturadoEm());
    return true;
  }

  private boolean importarLocalidadeIsolada(
      Snapshot snapshot,
      UUID execucaoId,
      LocalidadeLegada localidade) {
    try {
      return Boolean.TRUE.equals(transacao.execute(
          ignored -> importarLocalidade(snapshot, execucaoId, localidade)));
    } catch (DataIntegrityViolationException exception) {
      transacao.executeWithoutResult(ignored -> quarentenaLocalidade(
          snapshot, execucaoId, localidade, "LOCALIDADE_DIVERGENTE"));
      return false;
    }
  }

  private boolean importarUsuarioIsolado(
      Snapshot snapshot,
      UUID execucaoId,
      UsuarioLegado usuario) {
    try {
      return Boolean.TRUE.equals(transacao.execute(
          ignored -> importarUsuario(snapshot, execucaoId, usuario)));
    } catch (DataIntegrityViolationException exception) {
      transacao.executeWithoutResult(ignored -> quarentenaUsuario(
          snapshot, execucaoId, usuario, "USUARIO_UNICIDADE_DIVERGENTE"));
      return false;
    }
  }

  private boolean importarCredencial(Snapshot snapshot, UUID execucaoId, CredencialLegada credencial) {
    UUID usuarioId = destinoMapeado(execucaoId, "usuarios", credencial.usuarioOrigemId());
    if (usuarioId == null || credencial.senhaHash().length() < 20
        || credencial.senhaHash().chars().anyMatch(Character::isWhitespace)) {
      mapearDivergente(
          snapshot, execucaoId, "credenciais", credencial.idOrigem(), hash(credencial),
          "CREDENCIAL_INVALIDA_OU_USUARIO_AUSENTE", "CREDENCIAL");
      return false;
    }
    UUID id = IdsMigracaoIntegral.uuid("credencial", credencial.idOrigem());
    jdbc.update(
        """
        INSERT INTO credencial_usuario (
          id, usuario_id, senha_hash, algoritmo, alterada_em, precisa_redefinir, criado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (usuario_id) DO NOTHING
        """,
        id, usuarioId, credencial.senhaHash(), credencial.algoritmo(),
        credencial.alteradaEm(), credencial.precisaRedefinir(), credencial.criadoEm());
    mapear(
        execucaoId, "credenciais", credencial.idOrigem(), hash(credencial),
        "CREDENCIAL", id, "MAPEADO", snapshot.capturadoEm());
    return true;
  }

  private boolean importarDocumentoKyc(
      Snapshot snapshot,
      UUID execucaoId,
      DocumentoKycLegado documento,
      Item item,
      UUID atorSistemaId) {
    UUID usuarioId = destinoMapeado(execucaoId, "usuarios", documento.usuarioOrigemId());
    if (usuarioId == null || item == null || item.destino() == null
        || !TIPOS_DOCUMENTO.contains(documento.tipo())
        || !PARTES_DOCUMENTO.contains(documento.parte())
        || !STATUS_DOCUMENTO.contains(documento.status())) {
      mapearDivergente(
          snapshot, execucaoId, "documentos_kyc", documento.idOrigem(), hash(documento),
          "KYC_ESTRUTURA_INVALIDA", "DOCUMENTO_KYC");
      return false;
    }
    UUID arquivoId = IdsMigracaoIntegral.uuid("arquivo-kyc", item.idOrigem());
    UUID documentoId = IdsMigracaoIntegral.uuid("documento-kyc", documento.idOrigem());
    UUID envioId = IdsMigracaoIntegral.uuid(
        "envio-kyc", documento.usuarioOrigemId(), documento.envioOrigemId());
    UUID revisor = documento.revisorV3Id() == null ? atorSistemaId : documento.revisorV3Id();
    OffsetDateTime revisadoEm = documento.revisadoEm() == null
        ? documento.atualizadoEm()
        : documento.revisadoEm();
    UUID validadoPor = "VALIDADO".equals(documento.status()) ? revisor : null;
    OffsetDateTime validadoEm = "VALIDADO".equals(documento.status()) ? revisadoEm : null;
    jdbc.update(
        """
        INSERT INTO arquivo_midia (
          id, storage_provider, bucket, chave_objeto, nome_original, mime_type,
          tamanho_bytes, sha256, status_arquivo, criado_em
        ) VALUES (?, 'R2', ?, ?, NULL, ?, ?, ?, 'VALIDADO', ?)
        ON CONFLICT (storage_provider, bucket, chave_objeto) DO NOTHING
        """,
        arquivoId,
        item.destino().bucket(),
        item.destino().chave(),
        item.mimeType(),
        item.tamanhoBytes(),
        item.sha256(),
        documento.criadoEm());
    arquivoId = jdbc.queryForObject(
        """
        SELECT id FROM arquivo_midia
         WHERE storage_provider = 'R2' AND bucket = ? AND chave_objeto = ?
        """,
        UUID.class,
        item.destino().bucket(),
        item.destino().chave());
    jdbc.update(
        """
        INSERT INTO documento_usuario (
          id, usuario_id, arquivo_midia_id, envio_id, parte, tipo, status,
          politica_retencao, criado_em, atualizado_em, validado_por, validado_em,
          motivo_moderacao, revisado_por, revisado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ENQUANTO_HOUVER_ANUNCIO', ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        documentoId,
        usuarioId,
        arquivoId,
        envioId,
        documento.parte(),
        documento.tipo(),
        documento.status(),
        documento.criadoEm(),
        documento.atualizadoEm(),
        validadoPor,
        validadoEm,
        documento.motivoSanitizado(),
        revisor,
        revisadoEm);
    mapear(
        execucaoId, "documentos_kyc", documento.idOrigem(), hash(documento),
        "DOCUMENTO_KYC", documentoId, "MAPEADO", snapshot.capturadoEm());
    return true;
  }

  private void registrarOrfao(Snapshot snapshot, UUID execucaoId, OrfaoLegado orfao) {
    String tabela = switch (orfao.tipo()) {
      case CARTEIRA -> "carteiras_orfas";
      case HISTORICO_CREDITO -> "historicos_credito_orfaos";
      case PAGAMENTO -> "pagamentos_orfaos";
      case SUPORTE -> "mensagens_suporte_orfas";
    };
    if (orfao.tipo() == TipoOrfao.SUPORTE) {
      mapear(
          execucaoId, tabela, orfao.idOrigem(), hash(orfao),
          "SUPORTE_DESCARTADO", null, "REJEITADO", snapshot.capturadoEm());
      return;
    }
    String codigo = switch (orfao.tipo()) {
      case CARTEIRA -> "CARTEIRA_ORFA_QUARENTENA";
      case HISTORICO_CREDITO -> "HISTORICO_CREDITO_ORFAO_QUARENTENA";
      case PAGAMENTO -> "PAGAMENTO_ORFAO_QUARENTENA";
      case SUPORTE -> throw new IllegalStateException();
    };
    mapear(
        execucaoId, tabela, orfao.idOrigem(), hash(orfao),
        orfao.tipo().name(), null, "DIVERGENTE", snapshot.capturadoEm());
    pendencia(
        execucaoId, codigo, "ALTA", orfao.tipo().name(), orfao.idOrigem(),
        "Registro orfao preservado sem efeito financeiro; quantidade="
            + orfao.quantidade() + "; valorAgregado=" + orfao.valorAgregado(),
        snapshot.capturadoEm());
  }

  private long registrarUsuariosStaging(Snapshot snapshot, UUID execucaoId) {
    long quarentenas = 0;
    for (UsuarioStagingLegado staging : snapshot.usuariosStaging()) {
      UUID usuarioId = staging.usuarioCanonicoOrigemId() == null
          ? null
          : destinoMapeado(execucaoId, "usuarios", staging.usuarioCanonicoOrigemId());
      boolean referenciaSegura = usuarioId != null
          && switch (staging.classificacao()) {
            case CORRESPONDENCIA_CANONICA, DUPLICADO -> true;
            case STAGING_ONLY, AMBIGUO, SEM_IDENTIDADE -> false;
          };
      if (referenciaSegura) {
        mapear(
            execucaoId,
            "usuarios_staging",
            staging.idOrigem(),
            staging.fingerprintIdentidade(),
            "USUARIO_STAGING_REFERENCIA",
            usuarioId,
            "MAPEADO",
            snapshot.capturadoEm());
        continue;
      }

      String codigo = switch (staging.classificacao()) {
        case CORRESPONDENCIA_CANONICA -> "USUARIO_STAGING_DESTINO_AUSENTE";
        case STAGING_ONLY -> "USUARIO_STAGING_SEM_CORRESPONDENCIA";
        case AMBIGUO -> "USUARIO_STAGING_AMBIGUO";
        case DUPLICADO -> "USUARIO_STAGING_DUPLICADO_SEM_CANONICO";
        case SEM_IDENTIDADE -> "USUARIO_STAGING_SEM_IDENTIDADE";
      };
      mapear(
          execucaoId,
          "usuarios_staging",
          staging.idOrigem(),
          staging.fingerprintIdentidade(),
          "USUARIO_STAGING",
          null,
          "DIVERGENTE",
          snapshot.capturadoEm());
      pendencia(
          execucaoId,
          codigo,
          "ALTA",
          "USUARIO_STAGING",
          staging.idOrigem(),
          "Registro de staging classificado sem criar conta ou compartilhar identidade",
          snapshot.capturadoEm());
      quarentenas++;
    }
    return quarentenas;
  }

  private boolean quarentenaLocalidade(
      Snapshot snapshot,
      UUID execucaoId,
      LocalidadeLegada localidade,
      String codigo) {
    concluirStage("stg_localidade", execucaoId, localidade.idOrigem(), null, codigo);
    mapearDivergente(
        snapshot, execucaoId, tabelaLocalidade(localidade.tipo()), localidade.idOrigem(),
        hash(localidade), codigo, localidade.tipo().name());
    return false;
  }

  private boolean quarentenaUsuario(
      Snapshot snapshot,
      UUID execucaoId,
      UsuarioLegado usuario,
      String codigo) {
    concluirStage("stg_usuario", execucaoId, usuario.idOrigem(), null, codigo);
    mapearDivergente(
        snapshot, execucaoId, "usuarios", usuario.idOrigem(), hash(usuario),
        codigo, "USUARIO");
    return false;
  }

  private void mapearComplementoDivergente(
      Snapshot snapshot,
      UUID execucaoId,
      String tabela,
      String idOrigem,
      String hash) {
    mapearDivergente(
        snapshot, execucaoId, tabela, idOrigem, hash,
        "REFERENCIA_DESTINO_AUSENTE", tabela.toUpperCase(Locale.ROOT));
  }

  private void mapearDivergente(
      Snapshot snapshot,
      UUID execucaoId,
      String tabela,
      String idOrigem,
      String hash,
      String codigo,
      String entidadeTipo) {
    mapear(
        execucaoId, tabela, idOrigem, hash, entidadeTipo, null,
        "DIVERGENTE", snapshot.capturadoEm());
    pendencia(
        execucaoId, codigo, "ALTA", entidadeTipo, idOrigem,
        "Inconsistencia estrutural na importacao integral", snapshot.capturadoEm());
  }

  private void concluirStage(
      String tabela,
      UUID execucaoId,
      String idOrigem,
      UUID entidadeId,
      String pendencia) {
    if (!Set.of("stg_usuario", "stg_localidade").contains(tabela)) {
      throw new IllegalArgumentException("staging fora da politica");
    }
    jdbc.update(
        "UPDATE " + tabela + " SET status = ?, pendencia_codigo = ?, "
            + "entidade_v3_id = ?, processado_em = now() "
            + "WHERE execucao_id = ? AND id_origem = ?",
        pendencia == null ? "PROCESSADO" : "PENDENTE_REVISAO",
        pendencia,
        entidadeId,
        execucaoId,
        idOrigem);
  }

  private void mapear(
      UUID execucaoId,
      String tabela,
      String idOrigem,
      String hash,
      String entidadeTipo,
      UUID entidadeId,
      String status,
      OffsetDateTime agora) {
    jdbc.update(
        """
        INSERT INTO importacao_mapeamento (
          id, execucao_id, sistema_origem, tabela_origem, id_origem,
          hash_origem, entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem)
        DO UPDATE SET hash_origem = EXCLUDED.hash_origem,
          entidade_tipo = EXCLUDED.entidade_tipo,
          entidade_v3_id = EXCLUDED.entidade_v3_id,
          status = EXCLUDED.status,
          atualizado_em = EXCLUDED.atualizado_em
        """,
        IdsMigracaoIntegral.uuid("mapeamento-integral", execucaoId.toString(), tabela, idOrigem),
        execucaoId, ORIGEM, tabela, idOrigem, hash, entidadeTipo, entidadeId, status, agora, agora);
  }

  private void pendencia(
      UUID execucaoId,
      String codigo,
      String severidade,
      String entidadeTipo,
      String idOrigem,
      String detalhe,
      OffsetDateTime agora) {
    jdbc.update(
        """
        INSERT INTO importacao_pendencia (
          id, execucao_id, codigo, severidade, status, entidade_tipo,
          id_origem, detalhe_resumido, criado_em
        ) VALUES (?, ?, ?, ?, 'ABERTA', ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        IdsMigracaoIntegral.uuid(
            "pendencia-integral", execucaoId.toString(), codigo, entidadeTipo, idOrigem),
        execucaoId, codigo, severidade, entidadeTipo, idOrigem, detalhe, agora);
  }

  private UUID destinoMapeado(UUID execucaoId, String tabela, String idOrigem) {
    return jdbc.query(
        """
        SELECT entidade_v3_id FROM importacao_mapeamento
         WHERE execucao_id = ? AND sistema_origem = ? AND tabela_origem = ?
           AND id_origem = ? AND status = 'MAPEADO'
        """,
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        execucaoId, ORIGEM, tabela, idOrigem).stream().findFirst().orElse(null);
  }

  private boolean mapeado(UUID execucaoId, String tabela, String idOrigem) {
    Integer total = jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_mapeamento
         WHERE execucao_id = ? AND sistema_origem = ? AND tabela_origem = ?
           AND id_origem = ?
        """,
        Integer.class, execucaoId, ORIGEM, tabela, idOrigem);
    return total != null && total > 0;
  }

  private long contarStagePendente(String tabela, UUID execucaoId) {
    if (!Set.of("stg_usuario", "stg_localidade").contains(tabela)) {
      throw new IllegalArgumentException("staging fora da politica");
    }
    Long total = jdbc.queryForObject(
        "SELECT count(*) FROM " + tabela + " WHERE execucao_id = ? AND status = 'PENDENTE'",
        Long.class,
        execucaoId);
    return total == null ? 0 : total;
  }

  private boolean usuarioCompativel(UsuarioLegado usuario) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        """
        SELECT email_normalizado IS NOT DISTINCT FROM ?
           AND telefone_normalizado IS NOT DISTINCT FROM ?
           AND status = ?
           AND tipo_conta = ?
          FROM usuario WHERE id = ?
        """,
        Boolean.class,
        usuario.emailNormalizado(),
        usuario.telefoneNormalizado(),
        usuario.status(),
        usuario.tipoConta(),
        usuario.idV3()));
  }

  private void validarUnicidadeUsuarios(List<UsuarioLegado> usuarios) {
    validarUnico(usuarios, UsuarioLegado::idOrigem, "id de origem de usuario duplicado");
    validarUnico(usuarios, item -> item.idV3().toString(), "id V3 de usuario duplicado");
    validarUnicoOpcional(usuarios, UsuarioLegado::emailNormalizado, "email de usuario duplicado");
    validarUnicoOpcional(usuarios, UsuarioLegado::telefoneNormalizado, "telefone de usuario duplicado");
    validarUnicoOpcional(usuarios, UsuarioLegado::cpfNormalizado, "CPF de usuario duplicado");
  }

  private void validarUnico(
      List<UsuarioLegado> usuarios,
      Function<UsuarioLegado, String> extrator,
      String mensagem) {
    Set<String> vistos = new HashSet<>();
    if (usuarios.stream().map(extrator).anyMatch(valor -> !vistos.add(valor))) {
      throw new IllegalArgumentException(mensagem);
    }
  }

  private void validarUnicoOpcional(
      List<UsuarioLegado> usuarios,
      Function<UsuarioLegado, String> extrator,
      String mensagem) {
    Set<String> vistos = new HashSet<>();
    if (usuarios.stream().map(extrator).filter(Objects::nonNull)
        .anyMatch(valor -> !vistos.add(valor))) {
      throw new IllegalArgumentException(mensagem);
    }
  }

  private List<UUID> consolidarIdsQuarentena(Snapshot snapshot, UUID execucaoId) {
    return jdbc.query(
        """
        SELECT id_origem FROM importacao_mapeamento
         WHERE execucao_id = ? AND tabela_origem = 'documentos_kyc' AND status = 'DIVERGENTE'
        """,
        (rs, rowNum) -> IdsMigracaoIntegral.uuid("documento-kyc", rs.getString(1)),
        execucaoId);
  }

  private static String tabelaLocalidade(TipoLocalidade tipo) {
    return switch (tipo) {
      case ESTADO -> "estados";
      case CIDADE -> "cidades";
      case BAIRRO -> "bairros";
    };
  }

  private String hash(Object valor) {
    try {
      return FingerprintMigracaoIntegral.sha256(mapper.writeValueAsBytes(valor));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao gerar hash de origem", exception);
    }
  }

  private String json(Object valor) {
    try {
      return mapper.writeValueAsString(valor);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao gerar staging sanitizado", exception);
    }
  }

  public record ResultadoLote(
      long processados,
      long restantes,
      long quarentenas,
      long retries,
      long bytes) {
  }

  public record ReconciliacaoBase(
      long movimentosOrfaos,
      long suporteOrfaoPendente,
      long documentosCompartilhadosEntreUsuarios,
      long documentosQuarentenaPersistidos,
      boolean aprovada) {
  }
}
