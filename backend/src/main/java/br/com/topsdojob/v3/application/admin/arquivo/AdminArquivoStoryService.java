package br.com.topsdojob.v3.application.admin.arquivo;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Arquivo;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Midia;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Versao;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Preservacao;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Relatorio;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.Filtros;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.RelatorioRequest;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.Tipo;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoStoryDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoStoryDtos.Item;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Consulta somente o snapshot prospectivo do Story, nunca seu estado atual. */
@Service
public class AdminArquivoStoryService {
  private static final int MAX_PAGE_SIZE = 50;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;
  private final AdminArquivoStoryAccessAuditService audit;

  public AdminArquivoStoryService(JdbcTemplate jdbc, ObjectMapper mapper,
      ObjectProvider<ObjectStorage> storageProvider, R2StorageProperties storageProperties,
      AdminArquivoStoryAccessAuditService audit) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
    this.audit = audit;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  public AdminPaginaDto<Item> listar(int page, int size, UUID atorId, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade) {
    return listar(page, size, Filtros.todos(), atorId, requestId, finalidade);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  public AdminPaginaDto<Item> listar(int page, int size, Filtros filtros, UUID atorId,
      String requestId, FinalidadeAcessoArquivoPublicidade finalidade) {
    Objects.requireNonNull(finalidade, "finalidade obrigatoria para consulta privada");
    int pagina = Math.max(0, page);
    int tamanho = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    OffsetDateTime observadoEm = OffsetDateTime.now(ZoneOffset.UTC);
    var sql = AdminArquivoPublicidadeConsulta.consulta(Tipo.STORY, filtros, List.of(), observadoEm);
    long total = jdbc.queryForObject("select count(*)" + sql.fromWhere(), Long.class,
        sql.argumentos());
    List<Item> itens = jdbc.query("""
        select v.id, v.story_id, v.anuncio_id, v.contratante_usuario_id,
               coalesce(x.conteudo_json ->> 'titulo', x.conteudo_json ->> 'nomePublico') as titulo,
               v.modo_conteudo, v.classificacao, v.cobertura, v.inicio_em, v.fim_em,
               v.encerramento_motivo, v.retencao_ate,
        """ + AdminArquivoPublicidadeConsulta.resumo(Tipo.STORY) + sql.fromWhere()
        + AdminArquivoPublicidadeConsulta.ordem(filtros) + " limit ? offset ? ", (rs, row) -> {
          OffsetDateTime fim = instante(rs, "fim_em");
          return new Item(uuid(rs, "id"), uuid(rs, "story_id"), uuid(rs, "anuncio_id"),
              rs.getString("titulo"), rs.getString("modo_conteudo"),
              fim.isAfter(observadoEm) ? "EM_VEICULACAO" : "ENCERRADA",
              rs.getString("classificacao"), rs.getString("cobertura"),
              instante(rs, "inicio_em"), fim, "STORY", rs.getString("slug"),
              uuid(rs, "contratante_usuario_id"), rs.getString("anunciante_nome"),
              rs.getString("beneficio_codigo"), rs.getLong("total_versoes"),
              rs.getString("encerramento_motivo"), instante(rs, "retencao_ate"),
              rs.getBoolean("preservacao_ativa"), AdminArquivoPublicidadeConsulta.fimTipo(
                  fim, rs.getString("encerramento_motivo")));
        }, sql.comPagina(tamanho, (long) pagina * tamanho));
    audit.registrar(atorId, null, "ARQUIVO_PUBLICIDADE_STORY_LISTA_CONSULTADA", requestId,
        finalidade);
    int totalPages = (int) Math.min(Integer.MAX_VALUE, (total + tamanho - 1) / tamanho);
    return new AdminPaginaDto<>(itens, pagina, tamanho, total, totalPages,
        pagina >= totalPages - 1);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  public Detalhe detalhar(UUID id, UUID atorId, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade, boolean exportacao) {
    if (exportacao) {
      AdminArquivoExportadorAccessGuard.exigirAcessoAtual(jdbc, atorId);
    }
    return detalharInterno(id, atorId, requestId, finalidade, exportacao, true);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  public Relatorio<Detalhe> relatorio(RelatorioRequest request, UUID atorId,
      String requestId, FinalidadeAcessoArquivoPublicidade finalidade) {
    AdminArquivoExportadorAccessGuard.exigirAcessoAtual(jdbc, atorId);
    Objects.requireNonNull(finalidade, "finalidade obrigatoria para relatorio privado");
    OffsetDateTime geradoEm = OffsetDateTime.now(ZoneOffset.UTC);
    var sql = AdminArquivoPublicidadeConsulta.consulta(
        Tipo.STORY, request.filtros(), request.ids(), geradoEm);
    long total = jdbc.queryForObject("select count(*)" + sql.fromWhere(), Long.class,
        sql.argumentos());
    AdminArquivoPublicidadeConsulta.conferirEscopo(total, request.ids());
    List<UUID> ids = jdbc.query("select v.id" + sql.fromWhere()
        + AdminArquivoPublicidadeConsulta.ordem(request.filtros()),
        (rs, row) -> uuid(rs, "id"), sql.argumentos());
    AdminArquivoPublicidadeConsulta.conferirCompletude(total, ids);
    List<Detalhe> registros = ids.stream().map(id ->
        detalharInterno(id, atorId, requestId, finalidade, true, false)).toList();
    audit.registrarRelatorio(atorId, requestId, finalidade, registros.size(),
        AdminArquivoPublicidadeConsulta.escopoSha256(request));
    return new Relatorio<>("STORY", geradoEm, request.fusoHorario(), atorId, finalidade,
        request.filtros(), request.ids(), registros.size(), AdminArquivoPublicidadeConsulta.LIMITE_RELATORIO,
        AdminArquivoPublicidadeConsulta.lacunasRelatorio(), registros);
  }

  private Detalhe detalharInterno(UUID id, UUID atorId, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade, boolean exportacao, boolean registrarAcesso) {
    Objects.requireNonNull(finalidade, "finalidade obrigatoria para consulta privada");
    List<Detalhe> base = jdbc.query("""
        select id, story_id, anuncio_id, contratante_usuario_id,
               ativacao_beneficio_id, grupo_ativacao_id, movimento_credito_id,
               pagamento_id, modo_conteudo, classificacao, relacao_material,
               cobertura, inicio_em, fim_em, retencao_ate, encerramento_motivo
          from arquivo_publicidade_story_veiculacao where id = ?
        """, (rs, row) -> new Detalhe(
            uuid(rs, "id"), uuid(rs, "story_id"), uuid(rs, "anuncio_id"),
            uuid(rs, "contratante_usuario_id"), uuid(rs, "ativacao_beneficio_id"),
            uuid(rs, "grupo_ativacao_id"), uuid(rs, "movimento_credito_id"),
            uuid(rs, "pagamento_id"), rs.getString("modo_conteudo"),
            rs.getString("classificacao"), rs.getString("relacao_material"),
            rs.getString("cobertura"), instante(rs, "inicio_em"), instante(rs, "fim_em"),
            instante(rs, "retencao_ate"), rs.getString("encerramento_motivo"), List.of()), id);
    if (base.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Story arquivado nao encontrado");
    }
    List<Versao> versoes = jdbc.query("""
        select id, numero, capturado_em, vigente_desde, vigente_ate, motivo,
               conteudo_json::text as conteudo, contratante_json::text as contratante,
               comercial_json::text as comercial, segmentacao_json::text as segmentacao,
               alcance_json::text as alcance, conteudo_sha256
          from arquivo_publicidade_story_versao
         where veiculacao_id = ? order by numero
        """, (rs, row) -> new Versao(
            uuid(rs, "id"), rs.getInt("numero"), instante(rs, "capturado_em"),
            instante(rs, "vigente_desde"), instante(rs, "vigente_ate"),
            rs.getString("motivo"), json(rs, "conteudo"), json(rs, "contratante"),
            json(rs, "comercial"), json(rs, "segmentacao"), json(rs, "alcance"),
            rs.getString("conteudo_sha256"), midias(id, uuid(rs, "id"))), id);
    if (!exportacao) {
      versoes = versoes.stream().map(item -> new Versao(
          item.id(), item.numero(), item.capturadoEm(), item.vigenteDesde(),
          item.vigenteAte(), item.motivo(), AdminArquivoPublicidadeRedacao.conteudo(item.conteudo()),
          mapper.getNodeFactory().nullNode(), AdminArquivoPublicidadeRedacao.comercial(item.comercial()), item.segmentacao(),
          item.alcance(), item.conteudoSha256(), item.midias())).toList();
    }
    if (registrarAcesso) {
      audit.registrar(atorId, id, exportacao
          ? "ARQUIVO_PUBLICIDADE_STORY_EXPORTACAO_PREPARADA"
          : "ARQUIVO_PUBLICIDADE_STORY_DETALHE_CONSULTADO", requestId, finalidade);
    }
    Detalhe v = base.get(0);
    return new Detalhe(v.id(), v.storyId(), v.anuncioId(), v.contratanteUsuarioId(),
        v.ativacaoBeneficioId(), v.grupoAtivacaoId(),
        exportacao ? v.movimentoCreditoId() : null,
        exportacao ? v.pagamentoId() : null,
        v.modoConteudo(), v.natureza(), v.relacaoMaterial(),
        v.cobertura(), v.inicioEm(), v.fimEm(), v.retencaoAte(),
        v.encerramentoMotivo(), versoes, v.fimTipo(), exportacao ? preservacoes(id) : List.of());
  }

  private List<Preservacao> preservacoes(UUID id) {
    return jdbc.query("""
        select id, fundamento, responsavel_usuario_id, inicio_em, revisar_em
          from arquivo_publicidade_story_hold where veiculacao_id = ? and encerrado_em is null
         order by inicio_em, id
        """, (rs, row) -> new Preservacao(uuid(rs, "id"), rs.getString("fundamento"),
            uuid(rs, "responsavel_usuario_id"), instante(rs, "inicio_em"),
            instante(rs, "revisar_em")), id);
  }

  public Arquivo midia(UUID veiculacaoId, UUID midiaId, UUID atorId, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade) {
    AdminArquivoExportadorAccessGuard.exigirAcessoAtual(jdbc, atorId);
    Objects.requireNonNull(finalidade, "finalidade obrigatoria para midia privada");
    List<MidiaPrivada> encontradas = jdbc.query("""
        with efetiva as (
          select m.id, m.versao_id as versao_destino_id, m.versao_id,
                 m.anuncio_midia_id, m.arquivo_midia_id, m.variante,
                 m.storage_provider, m.bucket, m.chave_privada, m.sha256,
                 m.mime_type, m.tamanho_bytes
            from arquivo_publicidade_story_midia m
          union all
          select r.id, r.versao_id as versao_destino_id, origem.versao_id,
                 origem.anuncio_midia_id, origem.arquivo_midia_id, origem.variante,
                 origem.storage_provider, origem.bucket, origem.chave_privada,
                 origem.sha256, origem.mime_type, origem.tamanho_bytes
            from arquivo_publicidade_story_midia_referencia r
            join arquivo_publicidade_story_midia origem on origem.id = r.origem_midia_id
           where r.arquivo_midia_id = origem.arquivo_midia_id
             and r.variante = origem.variante
        )
        select m.versao_id, m.anuncio_midia_id, m.arquivo_midia_id,
               m.variante, m.storage_provider, m.bucket, m.chave_privada,
               m.sha256, m.mime_type, m.tamanho_bytes
          from efetiva m
          join arquivo_publicidade_story_versao v on v.id = m.versao_destino_id
          join arquivo_publicidade_story_versao origem_v on origem_v.id = m.versao_id
          join arquivo_publicidade_story_veiculacao destino_j on destino_j.id = v.veiculacao_id
          join arquivo_publicidade_story_veiculacao origem_j on origem_j.id = origem_v.veiculacao_id
         where v.veiculacao_id = ? and m.id = ?
           and origem_j.story_id = destino_j.story_id
        """, (rs, row) -> new MidiaPrivada(uuid(rs, "versao_id"),
            uuid(rs, "anuncio_midia_id"), uuid(rs, "arquivo_midia_id"),
            rs.getString("variante"), rs.getString("storage_provider"),
            rs.getString("bucket"), rs.getString("chave_privada"),
            rs.getString("sha256"), rs.getString("mime_type"),
            rs.getLong("tamanho_bytes")), veiculacaoId, midiaId);
    if (encontradas.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Midia do Story nao encontrada");
    }
    MidiaPrivada meta = encontradas.get(0);
    String prefixo = storageProperties.getPrivateMediaPrefix();
    String origem = meta.anuncioMidiaId() == null ? "direta" : meta.anuncioMidiaId().toString();
    String chaveEsperada = prefixo + "arquivo-publicidade/stories/" + meta.versaoId()
        + "/" + origem + "/" + meta.arquivoMidiaId() + "/"
        + meta.variante().toLowerCase(Locale.ROOT);
    if (prefixo == null || !"R2".equals(meta.provider())
        || !Objects.equals(storageProperties.getPrivateMediaBucket(), meta.bucket())
        || !chaveEsperada.equals(meta.key())) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Identidade da midia do Story nao comprovada");
    }
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Storage privado indisponivel para o Story arquivado");
    }
    StoredObject object = storage.get(StorageArea.PRIVATE_MEDIA, meta.key());
    if (object == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Midia do Story indisponivel no storage privado");
    }
    byte[] bytes = object.content();
    if (bytes == null || bytes.length != meta.size()
        || !sha256(bytes).equalsIgnoreCase(meta.sha256())) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Integridade da midia do Story nao comprovada");
    }
    audit.registrarMidia(atorId, veiculacaoId, midiaId,
        "ARQUIVO_PUBLICIDADE_STORY_MIDIA_PREPARADA", requestId, finalidade);
    return new Arquivo(bytes, meta.mimeType());
  }

  private List<Midia> midias(UUID veiculacaoId, UUID versaoId) {
    return jdbc.query("""
        select id, variante, mime_type, tamanho_bytes, sha256, ordem
          from (
            select m.id, m.variante, m.mime_type, m.tamanho_bytes, m.sha256, m.ordem
              from arquivo_publicidade_story_midia m where m.versao_id = ?
            union all
            select r.id, origem.variante, origem.mime_type, origem.tamanho_bytes,
                   origem.sha256, r.ordem
              from arquivo_publicidade_story_midia_referencia r
              join arquivo_publicidade_story_midia origem on origem.id = r.origem_midia_id
              join arquivo_publicidade_story_versao destino_v on destino_v.id = r.versao_id
              join arquivo_publicidade_story_versao origem_v on origem_v.id = origem.versao_id
              join arquivo_publicidade_story_veiculacao destino_j on destino_j.id = destino_v.veiculacao_id
              join arquivo_publicidade_story_veiculacao origem_j on origem_j.id = origem_v.veiculacao_id
             where r.versao_id = ? and r.arquivo_midia_id = origem.arquivo_midia_id
               and r.variante = origem.variante
               and origem_j.story_id = destino_j.story_id
          ) midias order by ordem, id
        """, (rs, row) -> {
          UUID id = uuid(rs, "id");
          return new Midia(id, rs.getString("variante"), rs.getString("mime_type"),
              rs.getLong("tamanho_bytes"), rs.getString("sha256"), rs.getInt("ordem"),
              "/api/admin/registros/stories/" + veiculacaoId + "/midias/" + id + "/arquivo");
        }, versaoId, versaoId);
  }

  private JsonNode json(ResultSet rs, String column) throws SQLException {
    String value = rs.getString(column);
    if (value == null) return mapper.getNodeFactory().nullNode();
    try {
      return mapper.readTree(value);
    } catch (JsonProcessingException e) {
      throw new SQLException("JSON do Story arquivado invalido", e);
    }
  }

  private static UUID uuid(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, UUID.class);
  }

  private static OffsetDateTime instante(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class);
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponivel", e);
    }
  }

  private record MidiaPrivada(UUID versaoId, UUID anuncioMidiaId, UUID arquivoMidiaId,
      String variante, String provider, String bucket, String key,
      String sha256, String mimeType, long size) {
  }
}
