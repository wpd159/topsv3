package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.FonteProperties;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
@ConditionalOnExpression("'${app.migracao.integral.operacao:}' == 'PRODUZIR_MANIFESTO'")
public class RepositorioCandidatosMidiaLegada {

  private static final List<String> TABELAS_OBRIGATORIAS = List.of(
      "usuarios",
      "anuncios",
      "anuncio_fotos",
      "anuncio_videos",
      "protected_media_assets",
      "anuncio_revisions",
      "anuncio_revision_media",
      "usuario_documentos",
      "blog_posts",
      "site_images");

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate leitura;
  private final NormalizadorReferenciaMidiaLegada normalizador;

  public RepositorioCandidatosMidiaLegada(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      FonteProperties fonteProperties) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource obrigatorio");
    this.jdbc = new JdbcTemplate(dataSource);
    this.leitura = new TransactionTemplate(transactionManager);
    this.leitura.setReadOnly(true);
    this.leitura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.normalizador = new NormalizadorReferenciaMidiaLegada(fonteProperties.r2());
  }

  public List<Descritor> listar() {
    validarBancoRestauradoLocal();
    List<Descritor> resultado = leitura.execute(status -> {
      validarTransacaoSomenteLeitura();
      validarEstrutura();
      List<Descritor> itens = new ArrayList<>();
      itens.addAll(protegidas());
      itens.addAll(fotosPublicasSemAtivoProtegido());
      itens.addAll(videosPublicosSemAtivoProtegido());
      itens.addAll(revisoes());
      itens.addAll(documentosKyc());
      itens.addAll(editoriais());
      return consolidar(itens);
    });
    if (resultado == null) {
      throw new IllegalStateException("fotografia de candidatos nao foi produzida");
    }
    return resultado;
  }

  private List<Descritor> protegidas() {
    String sql = """
        SELECT p.id, p.anuncio_id, a.usuario_id, a.status anuncio_status,
               a.content_classification, p.sort_order, p.media_type, p.storage_mode,
               p.content_type, p.original_storage_ref, p.legacy_original_url,
               row_number() OVER (
                 PARTITION BY p.anuncio_id
                 ORDER BY CASE WHEN upper(p.media_type) = 'VIDEO' THEN 1 ELSE 0 END,
                          p.sort_order, p.id
               ) ordem_canonica
          FROM protected_media_assets p
          JOIN anuncios a ON a.id = p.anuncio_id
         WHERE p.active IS TRUE
         ORDER BY p.anuncio_id, ordem_canonica, p.id
        """;
    return jdbc.queryForList(sql).stream().map(row -> {
      String modo = maiusculo(row.get("storage_mode"));
      StorageArea area = "PRIVATE_R2".equals(modo)
          ? StorageArea.PRIVATE_MEDIA
          : StorageArea.PUBLIC_MEDIA;
      String referencia = escolherReferenciaProtegida(row, modo);
      NormalizadorReferenciaMidiaLegada.Resultado origem = normalizador.normalizar(
          referencia, area);
      boolean video = "VIDEO".equals(maiusculo(row.get("media_type")));
      int ordem = inteiro(row.get("ordem_canonica"), 1) - 1;
      Finalidade finalidade = video
          ? Finalidade.VIDEO
          : ordem == 0 ? Finalidade.CAPA : Finalidade.GALERIA;
      String anuncioId = texto(row.get("anuncio_id"));
      String proprietarioId = texto(row.get("usuario_id"));
      return descritor(
          "protected_media_assets:" + texto(row.get("id")),
          EntidadeTipo.ANUNCIO,
          anuncioId,
          idV3("anuncio", anuncioId),
          proprietarioId,
          idV3("usuario", proprietarioId),
          texto(row.get("id")),
          finalidade,
          video ? TipoMidia.VIDEO : TipoMidia.FOTO,
          visibilidade(row.get("content_classification")),
          moderacao(row.get("anuncio_status")),
          origem.valida(),
          !video && ordem == 0,
          ordem,
          texto(row.get("content_type")),
          origem);
    }).toList();
  }

  private List<Descritor> fotosPublicasSemAtivoProtegido() {
    String sql = """
        WITH fotos AS (
          SELECT DISTINCT anuncio_id, url_foto
            FROM anuncio_fotos
           WHERE nullif(btrim(url_foto), '') IS NOT NULL
        )
        SELECT f.anuncio_id, f.url_foto, a.usuario_id, a.status anuncio_status,
               a.content_classification,
               row_number() OVER (
                 PARTITION BY f.anuncio_id ORDER BY f.url_foto
               ) ordem_canonica
          FROM fotos f
          JOIN anuncios a ON a.id = f.anuncio_id
         WHERE NOT EXISTS (
           SELECT 1
             FROM protected_media_assets p
            WHERE p.active IS TRUE
              AND p.anuncio_id = f.anuncio_id
              AND (p.legacy_original_url = f.url_foto
                   OR p.original_storage_ref = f.url_foto)
         )
         ORDER BY f.anuncio_id, ordem_canonica
        """;
    return jdbc.queryForList(sql).stream().map(row -> {
      String anuncioId = texto(row.get("anuncio_id"));
      String proprietarioId = texto(row.get("usuario_id"));
      String referencia = texto(row.get("url_foto"));
      NormalizadorReferenciaMidiaLegada.Resultado origem = normalizador.normalizar(
          referencia, StorageArea.PUBLIC_MEDIA);
      int ordem = 100_000 + inteiro(row.get("ordem_canonica"), 1) - 1;
      return descritor(
          "anuncio_fotos:" + anuncioId + ":" + hashCurto(referencia),
          EntidadeTipo.ANUNCIO,
          anuncioId,
          idV3("anuncio", anuncioId),
          proprietarioId,
          idV3("usuario", proprietarioId),
          "foto:" + hashCurto(referencia),
          Finalidade.GALERIA,
          TipoMidia.FOTO,
          visibilidade(row.get("content_classification")),
          moderacao(row.get("anuncio_status")),
          origem.valida(),
          false,
          ordem,
          null,
          origem);
    }).toList();
  }

  private List<Descritor> videosPublicosSemAtivoProtegido() {
    String sql = """
        WITH videos AS (
          SELECT DISTINCT anuncio_id, url_video
            FROM anuncio_videos
           WHERE nullif(btrim(url_video), '') IS NOT NULL
        )
        SELECT v.anuncio_id, v.url_video, a.usuario_id, a.status anuncio_status,
               a.content_classification,
               row_number() OVER (
                 PARTITION BY v.anuncio_id ORDER BY v.url_video
               ) ordem_canonica
          FROM videos v
          JOIN anuncios a ON a.id = v.anuncio_id
         WHERE NOT EXISTS (
           SELECT 1
             FROM protected_media_assets p
            WHERE p.active IS TRUE
              AND p.anuncio_id = v.anuncio_id
              AND (p.legacy_original_url = v.url_video
                   OR p.original_storage_ref = v.url_video)
         )
         ORDER BY v.anuncio_id, ordem_canonica
        """;
    return jdbc.queryForList(sql).stream().map(row -> {
      String anuncioId = texto(row.get("anuncio_id"));
      String proprietarioId = texto(row.get("usuario_id"));
      String referencia = texto(row.get("url_video"));
      NormalizadorReferenciaMidiaLegada.Resultado origem = normalizador.normalizar(
          referencia, StorageArea.PUBLIC_MEDIA);
      return descritor(
          "anuncio_videos:" + anuncioId + ":" + hashCurto(referencia),
          EntidadeTipo.ANUNCIO,
          anuncioId,
          idV3("anuncio", anuncioId),
          proprietarioId,
          idV3("usuario", proprietarioId),
          "video:" + hashCurto(referencia),
          Finalidade.VIDEO,
          TipoMidia.VIDEO,
          visibilidade(row.get("content_classification")),
          moderacao(row.get("anuncio_status")),
          origem.valida(),
          false,
          200_000 + inteiro(row.get("ordem_canonica"), 1) - 1,
          null,
          origem);
    }).toList();
  }

  private List<Descritor> revisoes() {
    String sql = """
        SELECT m.id, m.revision_id, r.anuncio_id, a.usuario_id,
               r.status revision_status, m.sort_order, m.media_type,
               m.source_type, m.content_type, m.storage_ref,
               m.public_url, m.preview_public_url
          FROM anuncio_revision_media m
          JOIN anuncio_revisions r ON r.id = m.revision_id
          JOIN anuncios a ON a.id = r.anuncio_id
         ORDER BY r.anuncio_id, m.revision_id, m.sort_order, m.id
        """;
    return jdbc.queryForList(sql).stream().map(row -> {
      String sourceType = maiusculo(row.get("source_type"));
      boolean privada = "STAGED_PRIVATE_PROTECTED".equals(sourceType);
      String referencia = privada
          ? primeiro(row.get("storage_ref"), row.get("public_url"), row.get("preview_public_url"))
          : primeiro(row.get("public_url"), row.get("storage_ref"), row.get("preview_public_url"));
      NormalizadorReferenciaMidiaLegada.Resultado origem = normalizador.normalizar(
          referencia,
          privada ? StorageArea.PRIVATE_MEDIA : StorageArea.PUBLIC_MEDIA);
      String anuncioId = texto(row.get("anuncio_id"));
      String proprietarioId = texto(row.get("usuario_id"));
      boolean video = "VIDEO".equals(maiusculo(row.get("media_type")));
      return descritor(
          "anuncio_revision_media:" + texto(row.get("id")),
          EntidadeTipo.REVISAO_ANUNCIO,
          anuncioId,
          idV3("anuncio", anuncioId),
          proprietarioId,
          idV3("usuario", proprietarioId),
          texto(row.get("revision_id")) + ":" + texto(row.get("id")),
          Finalidade.REVISAO,
          video ? TipoMidia.VIDEO : TipoMidia.FOTO,
          Visibilidade.PRIVADA,
          moderacao(row.get("revision_status")),
          origem.valida(),
          false,
          inteiro(row.get("sort_order"), 0),
          texto(row.get("content_type")),
          origem);
    }).toList();
  }

  private List<Descritor> documentosKyc() {
    String sql = """
        SELECT d.usuario_id, d.documento_url, (u.id IS NOT NULL) proprietario_existe
          FROM usuario_documentos d
          LEFT JOIN usuarios u ON u.id = d.usuario_id
         WHERE nullif(btrim(d.documento_url), '') IS NOT NULL
         ORDER BY d.usuario_id, d.documento_url
        """;
    List<Descritor> candidatos = jdbc.queryForList(sql).stream().map(row -> {
      String proprietarioId = texto(row.get("usuario_id"));
      boolean proprietarioExiste = Boolean.TRUE.equals(row.get("proprietario_existe"));
      String referencia = texto(row.get("documento_url"));
      NormalizadorReferenciaMidiaLegada.Resultado origem = normalizador.normalizar(
          referencia, StorageArea.PRIVATE_DOCUMENT);
      String referenciaHash = hashCurto(referencia);
      return descritor(
          "usuario_documentos:" + proprietarioId + ":" + referenciaHash,
          EntidadeTipo.KYC,
          proprietarioId,
          proprietarioExiste ? idV3("usuario", proprietarioId) : null,
          proprietarioId,
          proprietarioExiste ? idV3("usuario", proprietarioId) : null,
          "documento:" + referenciaHash,
          Finalidade.KYC_IDENTIDADE,
          TipoMidia.DOCUMENTO,
          Visibilidade.PRIVADA,
          EstadoModeracao.NAO_APLICAVEL,
          origem.valida(),
          false,
          0,
          null,
          origem);
    }).toList();

    Map<String, Set<String>> proprietariosPorOrigem = candidatos.stream()
        .filter(Descritor::referenciaValida)
        .collect(Collectors.groupingBy(
            Descritor::chaveOrigem,
            Collectors.mapping(Descritor::proprietarioOrigemId, Collectors.toSet())));
    Map<String, Descritor> unicos = new LinkedHashMap<>();
    for (Descritor candidato : candidatos) {
      boolean cruzado = proprietariosPorOrigem
          .getOrDefault(candidato.chaveOrigem(), Set.of())
          .size() > 1;
      Descritor seguro = cruzado ? candidato.semOwnershipComprovado() : candidato;
      unicos.putIfAbsent(
          seguro.proprietarioOrigemId() + "|" + seguro.chaveOrigem(),
          seguro);
    }
    return List.copyOf(unicos.values());
  }

  private List<Descritor> editoriais() {
    List<Descritor> resultado = new ArrayList<>();
    String posts = """
        SELECT p.id, p.created_by_user_id, (u.id IS NOT NULL) proprietario_existe,
               p.status, p.imagem_url, p.og_image_url
          FROM blog_posts p
          LEFT JOIN usuarios u ON u.id = p.created_by_user_id
         WHERE nullif(btrim(p.imagem_url), '') IS NOT NULL
            OR nullif(btrim(p.og_image_url), '') IS NOT NULL
         ORDER BY p.id
        """;
    jdbc.queryForList(posts).forEach(row -> {
      adicionarEditorial(resultado, row, "imagem_url", Finalidade.BLOG_CAPA, 0);
      adicionarEditorial(resultado, row, "og_image_url", Finalidade.BLOG_OG, 1);
    });
    String imagens = """
        SELECT id, path, url, type
          FROM site_images
         WHERE nullif(btrim(url), '') IS NOT NULL
         ORDER BY id
        """;
    jdbc.queryForList(imagens).forEach(row -> {
      String referencia = primeiro(row.get("url"), row.get("path"));
      NormalizadorReferenciaMidiaLegada.Resultado origem = normalizador.normalizar(
          referencia, StorageArea.PUBLIC_MEDIA);
      String id = texto(row.get("id"));
      resultado.add(descritor(
          "site_images:" + id,
          EntidadeTipo.EDITORIAL,
          "site-image:" + id,
          idV3("editorial", "site-image:" + id),
          null,
          null,
          "site-image:" + id,
          Finalidade.EDITORIAL,
          TipoMidia.FOTO,
          Visibilidade.LIVRE,
          EstadoModeracao.NAO_APLICAVEL,
          origem.valida(),
          false,
          0,
          null,
          origem));
    });
    return List.copyOf(resultado);
  }

  private void adicionarEditorial(
      List<Descritor> destino,
      Map<String, Object> row,
      String coluna,
      Finalidade finalidade,
      int ordem) {
    String referencia = texto(row.get(coluna));
    if (referencia == null) {
      return;
    }
    String postId = texto(row.get("id"));
    String proprietarioId = texto(row.get("created_by_user_id"));
    boolean proprietarioExiste = Boolean.TRUE.equals(row.get("proprietario_existe"));
    NormalizadorReferenciaMidiaLegada.Resultado origem = normalizador.normalizar(
        referencia, StorageArea.PUBLIC_MEDIA);
    destino.add(descritor(
        "blog_posts:" + postId + ":" + finalidade.name().toLowerCase(Locale.ROOT),
        EntidadeTipo.EDITORIAL,
        "blog-post:" + postId,
        idV3("editorial", "blog-post:" + postId),
        proprietarioId,
        proprietarioExiste ? idV3("usuario", proprietarioId) : null,
        "blog:" + postId + ":" + finalidade.name(),
        finalidade,
        TipoMidia.FOTO,
        Visibilidade.LIVRE,
        moderacao(row.get("status")),
        origem.valida(),
        false,
        ordem,
        null,
        origem));
  }

  private List<Descritor> consolidar(List<Descritor> candidatos) {
    Map<String, Descritor> porId = new HashMap<>();
    for (Descritor candidato : candidatos) {
      Descritor anterior = porId.putIfAbsent(candidato.idOrigem(), candidato);
      if (anterior != null && !anterior.equals(candidato)) {
        throw new IllegalStateException("candidato de midia possui identidade duplicada");
      }
    }
    return porId.values().stream()
        .sorted(Comparator
            .comparing(Descritor::entidadeTipo)
            .thenComparing(item -> nulo(item.entidadeOrigemId()))
            .thenComparingInt(Descritor::ordem)
            .thenComparing(Descritor::idOrigem))
        .toList();
  }

  private void validarBancoRestauradoLocal() {
    try (Connection connection = dataSource.getConnection()) {
      String url = connection.getMetaData().getURL();
      java.net.URI uri = java.net.URI.create(url.substring("jdbc:".length()));
      String host = uri.getHost();
      if (host == null || !("localhost".equalsIgnoreCase(host)
          || "127.0.0.1".equals(host)
          || "::1".equals(host))) {
        throw new IllegalStateException(
            "produtor de manifesto aceita somente PostgreSQL restaurado em loopback");
      }
    } catch (java.sql.SQLException exception) {
      throw new IllegalStateException("banco restaurado nao pode ser validado", exception);
    }
  }

  private void validarTransacaoSomenteLeitura() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      throw new IllegalStateException("adapter de midia exige transacao somente leitura");
    }
    Boolean readOnly = jdbc.queryForObject(
        "SELECT current_setting('transaction_read_only') = 'on'", Boolean.class);
    Integer versao = jdbc.queryForObject(
        "SELECT current_setting('server_version_num')::integer", Integer.class);
    if (!Boolean.TRUE.equals(readOnly) || versao == null || versao < 170_000) {
      throw new IllegalStateException("adapter exige PostgreSQL 17 em modo somente leitura");
    }
  }

  private void validarEstrutura() {
    List<String> presentes = jdbc.queryForList(
        "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema='public' AND table_type='BASE TABLE'",
        String.class);
    List<String> ausentes = TABELAS_OBRIGATORIAS.stream()
        .filter(tabela -> !presentes.contains(tabela))
        .toList();
    if (!ausentes.isEmpty()) {
      throw new IllegalStateException(
          "snapshot restaurado nao possui todas as tabelas de midia obrigatorias");
    }
  }

  private Descritor descritor(
      String idOrigem,
      EntidadeTipo entidadeTipo,
      String entidadeOrigemId,
      String entidadeV3Id,
      String proprietarioOrigemId,
      String proprietarioV3Id,
      String referenciaOrigemId,
      Finalidade finalidade,
      TipoMidia tipoMidia,
      Visibilidade visibilidade,
      EstadoModeracao estadoModeracao,
      boolean referenciaValida,
      boolean capaValida,
      int ordem,
      String mimeType,
      NormalizadorReferenciaMidiaLegada.Resultado origem) {
    return new Descritor(
        idOrigem,
        entidadeTipo,
        entidadeOrigemId,
        entidadeV3Id,
        proprietarioOrigemId,
        proprietarioV3Id,
        referenciaOrigemId,
        finalidade,
        tipoMidia,
        visibilidade,
        estadoModeracao,
        referenciaValida,
        origem.referenciaPersistida(),
        capaValida,
        ordem,
        mimeType,
        origem.extensao(),
        origem.origem(),
        origem.referenciaFingerprint());
  }

  private static String escolherReferenciaProtegida(Map<String, Object> row, String modo) {
    if ("PRIVATE_R2".equals(modo)) {
      return primeiro(row.get("original_storage_ref"), row.get("legacy_original_url"));
    }
    return primeiro(row.get("legacy_original_url"), row.get("original_storage_ref"));
  }

  private static String primeiro(Object... valores) {
    for (Object valor : valores) {
      String texto = texto(valor);
      if (texto != null) {
        return texto;
      }
    }
    return null;
  }

  private static EstadoModeracao moderacao(Object status) {
    return switch (maiusculo(status)) {
      case "ATIVO", "APROVADA", "APROVADO", "PUBLICADO", "PUBLISHED" ->
          EstadoModeracao.APROVADA;
      case "REJEITADO", "REJEITADA" -> EstadoModeracao.REJEITADA;
      default -> EstadoModeracao.PENDENTE;
    };
  }

  private static Visibilidade visibilidade(Object classificacao) {
    return "RESTRITA_18".equals(maiusculo(classificacao))
        ? Visibilidade.RESTRITA_18
        : Visibilidade.LIVRE;
  }

  private static String idV3(String dominio, String id) {
    return id == null ? null : IdsMigracaoIntegral.uuid(dominio, id).toString();
  }

  private static String hashCurto(String valor) {
    return FingerprintMigracaoIntegral.sha256(
        (valor == null ? "<ausente>" : valor).getBytes(StandardCharsets.UTF_8));
  }

  private static String texto(Object valor) {
    if (valor == null) {
      return null;
    }
    String texto = valor.toString().trim();
    return texto.isEmpty() ? null : texto;
  }

  private static String maiusculo(Object valor) {
    String texto = texto(valor);
    return texto == null ? "" : texto.toUpperCase(Locale.ROOT);
  }

  private static int inteiro(Object valor, int padrao) {
    return valor instanceof Number numero ? numero.intValue() : padrao;
  }

  private static String nulo(String valor) {
    return valor == null ? "" : valor;
  }

  public record Descritor(
      String idOrigem,
      EntidadeTipo entidadeTipo,
      String entidadeOrigemId,
      String entidadeV3Id,
      String proprietarioOrigemId,
      String proprietarioV3Id,
      String referenciaOrigemId,
      Finalidade finalidade,
      TipoMidia tipoMidia,
      Visibilidade visibilidade,
      EstadoModeracao estadoModeracao,
      boolean referenciaValida,
      boolean referenciaPersistida,
      boolean capaValida,
      int ordem,
      String mimeType,
      String extensao,
      Origem origem,
      String referenciaFingerprint) {

    public Descritor {
      Objects.requireNonNull(idOrigem, "idOrigem obrigatorio");
      Objects.requireNonNull(entidadeTipo, "entidadeTipo obrigatorio");
      Objects.requireNonNull(finalidade, "finalidade obrigatoria");
      Objects.requireNonNull(tipoMidia, "tipoMidia obrigatorio");
      Objects.requireNonNull(visibilidade, "visibilidade obrigatoria");
      Objects.requireNonNull(estadoModeracao, "estadoModeracao obrigatorio");
      Objects.requireNonNull(origem, "origem obrigatoria");
      Objects.requireNonNull(referenciaFingerprint, "fingerprint da referencia obrigatorio");
    }

    Descritor semOwnershipComprovado() {
      return new Descritor(
          idOrigem,
          entidadeTipo,
          entidadeOrigemId,
          entidadeV3Id,
          proprietarioOrigemId,
          null,
          referenciaOrigemId,
          finalidade,
          tipoMidia,
          visibilidade,
          estadoModeracao,
          referenciaValida,
          referenciaPersistida,
          capaValida,
          ordem,
          mimeType,
          extensao,
          origem,
          referenciaFingerprint);
    }

    String chaveOrigem() {
      return origem.area() + "|" + origem.localizador();
    }

    String representacaoCanonica() {
      return String.join("|",
          idOrigem,
          entidadeTipo.name(),
          nulo(entidadeOrigemId),
          nulo(entidadeV3Id),
          nulo(proprietarioOrigemId),
          nulo(proprietarioV3Id),
          nulo(referenciaOrigemId),
          finalidade.name(),
          tipoMidia.name(),
          visibilidade.name(),
          estadoModeracao.name(),
          Boolean.toString(referenciaValida),
          Boolean.toString(referenciaPersistida),
          Boolean.toString(capaValida),
          Integer.toString(ordem),
          nulo(mimeType),
          nulo(extensao),
          origem.tipo().name(),
          origem.area() == null ? "" : origem.area().name(),
          origem.localizador(),
          referenciaFingerprint);
    }

    @Override
    public String toString() {
      return "Descritor[idOrigem=" + idOrigem + ", entidadeTipo=" + entidadeTipo
          + ", origem=<sanitizada>]";
    }
  }
}
