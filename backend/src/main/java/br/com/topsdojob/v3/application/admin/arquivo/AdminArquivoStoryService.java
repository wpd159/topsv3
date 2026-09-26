package br.com.topsdojob.v3.application.admin.arquivo;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Arquivo;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Midia;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Versao;
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
    Objects.requireNonNull(finalidade, "finalidade obrigatoria para consulta privada");
    int pagina = Math.max(0, page);
    int tamanho = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    long total = jdbc.queryForObject(
        "select count(*) from arquivo_publicidade_story_veiculacao", Long.class);
    List<Item> itens = jdbc.query("""
        select v.id, v.story_id, v.anuncio_id, x.titulo, v.modo_conteudo,
               v.classificacao, v.cobertura, v.inicio_em, v.fim_em
          from arquivo_publicidade_story_veiculacao v
          left join lateral (
            select av.conteudo_json ->> 'titulo' as titulo
              from arquivo_publicidade_story_versao av
             where av.veiculacao_id = v.id
             order by av.numero desc limit 1
          ) x on true
         order by v.inicio_em desc, v.id desc
         limit ? offset ?
        """, (rs, row) -> {
          OffsetDateTime fim = instante(rs, "fim_em");
          return new Item(uuid(rs, "id"), uuid(rs, "story_id"), uuid(rs, "anuncio_id"),
              rs.getString("titulo"), rs.getString("modo_conteudo"),
              fim.isAfter(OffsetDateTime.now(ZoneOffset.UTC)) ? "EM_VEICULACAO" : "ENCERRADA",
              rs.getString("classificacao"), rs.getString("cobertura"),
              instante(rs, "inicio_em"), fim);
        }, tamanho, (long) pagina * tamanho);
    audit.registrar(atorId, null, "ARQUIVO_PUBLICIDADE_STORY_LISTA_CONSULTADA", requestId,
        finalidade);
    int totalPages = (int) Math.min(Integer.MAX_VALUE, (total + tamanho - 1) / tamanho);
    return new AdminPaginaDto<>(itens, pagina, tamanho, total, totalPages,
        pagina >= totalPages - 1);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  public Detalhe detalhar(UUID id, UUID atorId, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade, boolean exportacao) {
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
    audit.registrar(atorId, id, exportacao
        ? "ARQUIVO_PUBLICIDADE_STORY_EXPORTACAO_PREPARADA"
        : "ARQUIVO_PUBLICIDADE_STORY_DETALHE_CONSULTADO", requestId, finalidade);
    Detalhe v = base.get(0);
    return new Detalhe(v.id(), v.storyId(), v.anuncioId(), v.contratanteUsuarioId(),
        v.ativacaoBeneficioId(), v.grupoAtivacaoId(),
        exportacao ? v.movimentoCreditoId() : null,
        exportacao ? v.pagamentoId() : null,
        v.modoConteudo(), v.natureza(), v.relacaoMaterial(),
        v.cobertura(), v.inicioEm(), v.fimEm(), v.retencaoAte(),
        v.encerramentoMotivo(), versoes);
  }

  public Arquivo midia(UUID veiculacaoId, UUID midiaId, UUID atorId, String requestId,
      FinalidadeAcessoArquivoPublicidade finalidade) {
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
