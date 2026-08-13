package br.com.topsdojob.v3.importacao.conteudoseo;

import br.com.topsdojob.v3.application.blog.BlogConteudoValidator;
import br.com.topsdojob.v3.application.conteudo.ConteudoSiteCatalogo;
import br.com.topsdojob.v3.importacao.conteudoseo.GeradorMetadataSeoImportacao.MetadataSeo;
import br.com.topsdojob.v3.importacao.conteudoseo.PlanejadorConteudoSeoImportacao.SlugPlanejado;
import br.com.topsdojob.v3.importacao.conteudoseo.PlanejadorConteudoSeoImportacao.StatusPlanejado;
import br.com.topsdojob.v3.importacao.conteudoseo.PoliticaIndexacaoLocalidadeImportacao.Decisao;
import br.com.topsdojob.v3.importacao.conteudoseo.SanitizadorConteudoLegado.Resultado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.AvisoLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.BlogCategoriaLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.BlogPostLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.ConteudoInstitucionalLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.FaqLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LinkInterno;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.RedirectLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.ReferenciaAnuncioSeo;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.Snapshot;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.SeveridadePendenciaImportacao;
import br.com.topsdojob.v3.importacao.model.TipoEntidadeImportacao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
public class ImportadorConteudoSeoFaseDois {
  private static final String ORIGEM = "TOPSDOJOB_LEGADO";
  private static final String TABELA_FAQ = "faq";
  private static final String TABELA_AVISO = "avisos";
  private static final String TABELA_BLOG_CATEGORIA = "blog_categorias";
  private static final String TABELA_BLOG_POST = "blog_posts";
  private static final String TABELA_INSTITUCIONAL = "conteudo_institucional";
  private static final String TABELA_LOCALIDADE = "seo_localidades";
  private static final String TABELA_ANUNCIO = "seo_anuncios";
  private static final String TABELA_REDIRECT = "seo_redirects";

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transacao;
  private final ObjectMapper objectMapper;
  private final ObjectMapper hashMapper;
  private final SanitizadorConteudoLegado sanitizador;
  private final PoliticaIndexacaoLocalidadeImportacao politicaIndexacao;
  private final GeradorMetadataSeoImportacao geradorMetadata;
  private final PlanejadorConteudoSeoImportacao planejador;
  private final BlogConteudoValidator blogValidator;

  public ImportadorConteudoSeoFaseDois(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      ObjectMapper objectMapper,
      SanitizadorConteudoLegado sanitizador,
      PoliticaIndexacaoLocalidadeImportacao politicaIndexacao,
      GeradorMetadataSeoImportacao geradorMetadata,
      PlanejadorConteudoSeoImportacao planejador,
      BlogConteudoValidator blogValidator) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transacao = new TransactionTemplate(transactionManager);
    this.objectMapper = objectMapper;
    this.hashMapper = objectMapper.copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.sanitizador = sanitizador;
    this.politicaIndexacao = politicaIndexacao;
    this.geradorMetadata = geradorMetadata;
    this.planejador = planejador;
    this.blogValidator = blogValidator;
  }

  public ResultadoImportacaoConteudoSeo executar(Snapshot snapshot, int limite) {
    Objects.requireNonNull(snapshot, "snapshot deve ser informado");
    if (limite < 0) {
      throw new IllegalArgumentException("limite nao pode ser negativo");
    }
    if (!usuarioExiste(snapshot.atorSistemaV3Id())) {
      throw new IllegalStateException("Ator de sistema da importacao nao existe na V3");
    }
    UUID execucaoId = uuid("execucao-conteudo-seo", snapshot.snapshotId());
    transacao.executeWithoutResult(status -> preparar(snapshot, execucaoId));
    List<ChaveEntrada> pendentes = limite == 0 ? List.of() : jdbc.query(
        """
        SELECT tabela_origem, id_origem
        FROM stg_url
        WHERE execucao_id = ? AND status = 'PENDENTE'
        ORDER BY
          CASE tabela_origem
            WHEN 'blog_categorias' THEN 1
            WHEN 'blog_posts' THEN 2
            ELSE 3
          END,
          tabela_origem,
          id_origem
        LIMIT ?
        """,
        (rs, rowNum) -> new ChaveEntrada(rs.getString(1), rs.getString(2)),
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
    for (Entrada entrada : entradas(snapshot)) {
      prepararStage(snapshot, execucaoId, entrada);
    }
    jdbc.update(
        "UPDATE importacao_execucao SET status = 'EM_EXECUCAO', finalizado_em = NULL WHERE id = ?",
        execucaoId);
  }

  private void prepararStage(Snapshot snapshot, UUID execucaoId, Entrada entrada) {
    String hashOrigem = hash(entrada.dados());
    List<String> existente = jdbc.query(
        """
        SELECT hash_origem
        FROM stg_url
        WHERE execucao_id = ? AND sistema_origem = ? AND tabela_origem = ? AND id_origem = ?
        """,
        (rs, rowNum) -> rs.getString(1),
        execucaoId,
        ORIGEM,
        entrada.tabela(),
        entrada.idOrigem());
    if (!existente.isEmpty() && !Objects.equals(existente.get(0), hashOrigem)) {
      throw new IllegalStateException("Fonte diverge do staging existente");
    }
    jdbc.update(
        """
        INSERT INTO stg_url (
          id, execucao_id, sistema_origem, tabela_origem, id_origem, hash_origem,
          payload_normalizado_json, status, criado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDENTE', ?)
        ON CONFLICT (execucao_id, sistema_origem, tabela_origem, id_origem) DO NOTHING
        """,
        uuid("stage-conteudo-seo", snapshot.snapshotId(), entrada.tabela(), entrada.idOrigem()),
        execucaoId,
        ORIGEM,
        entrada.tabela(),
        entrada.idOrigem(),
        hashOrigem,
        json(payloadSeguro(entrada)),
        snapshot.capturadoEm());
  }

  private Map<String, Object> payloadSeguro(Entrada entrada) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tipo", entrada.tipo().name());
    payload.put("tabelaOrigem", entrada.tabela());
    payload.put("conteudoFingerprint", hash(entrada.dados()));
    Object dados = entrada.dados();
    if (dados instanceof FaqLegada faq) {
      payload.put("statusOrigem", faq.status());
      payload.put("categoria", faq.categoria());
      payload.put("ativa", faq.ativa());
    } else if (dados instanceof AvisoLegado aviso) {
      payload.put("statusOrigem", aviso.status());
      payload.put("localExibicao", aviso.localExibicao());
    } else if (dados instanceof BlogCategoriaLegada categoria) {
      payload.put("slug", categoria.slug());
      payload.put("ativa", categoria.ativa());
    } else if (dados instanceof BlogPostLegado post) {
      payload.put("statusOrigem", post.status());
      payload.put("slug", post.slug());
      payload.put("possuiImagemCapa", post.imagemCapaV3Id() != null);
      payload.put("possuiImagemOg", post.imagemOgV3Id() != null);
    } else if (dados instanceof ConteudoInstitucionalLegado conteudo) {
      payload.put("statusOrigem", conteudo.status());
      payload.put("chavePublica", conteudo.chavePublica());
    } else if (dados instanceof LocalidadeSeoLegada localidade) {
      payload.put("tipoLocalidade", localidade.tipo().name());
      payload.put("caminhoPublico", localidade.caminhoPublico());
      payload.put("anunciosIndexaveis", localidade.anunciosPublicosIndexaveis());
    } else if (dados instanceof ReferenciaAnuncioSeo anuncio) {
      payload.put("slug", anuncio.slug());
      payload.put("shouldIndexAnuncio", anuncio.shouldIndexAnuncio());
    } else if (dados instanceof RedirectLegado redirect) {
      payload.put("origemCaminho", redirect.origemCaminho());
      payload.put("destinoCaminho", redirect.destinoCaminho());
    } else {
      throw new IllegalStateException("Tipo de entrada nao suportado");
    }
    return payload;
  }

  private Processamento processar(Snapshot snapshot, UUID execucaoId, Entrada entrada) {
    List<String> estado = jdbc.query(
        """
        SELECT status FROM stg_url
        WHERE execucao_id = ? AND tabela_origem = ? AND id_origem = ?
        FOR UPDATE
        """,
        (rs, rowNum) -> rs.getString(1),
        execucaoId,
        entrada.tabela(),
        entrada.idOrigem());
    if (estado.isEmpty() || !"PENDENTE".equals(estado.get(0))) {
      return Processamento.vazio();
    }
    if (ehConteudoDuplicado(snapshot, entrada)) {
      return concluirItem(
          snapshot,
          execucaoId,
          entrada,
          Processamento.quarentena(CodigoPendenciaImportacao.CONTEUDO_DUPLICADO));
    }
    Object dados = entrada.dados();
    Processamento processamento;
    if (dados instanceof FaqLegada faq) {
      processamento = processarFaq(snapshot, faq);
    } else if (dados instanceof AvisoLegado aviso) {
      processamento = processarAviso(snapshot, aviso);
    } else if (dados instanceof BlogCategoriaLegada categoria) {
      processamento = processarCategoriaBlog(snapshot, categoria);
    } else if (dados instanceof BlogPostLegado post) {
      processamento = processarPostBlog(snapshot, post);
    } else if (dados instanceof ConteudoInstitucionalLegado conteudo) {
      processamento = processarConteudoInstitucional(snapshot, conteudo);
    } else if (dados instanceof LocalidadeSeoLegada localidade) {
      processamento = processarLocalidade(snapshot, localidade);
    } else if (dados instanceof ReferenciaAnuncioSeo anuncio) {
      processamento = processarAnuncioSeo(snapshot, anuncio);
    } else if (dados instanceof RedirectLegado redirect) {
      processamento = processarRedirect(snapshot, redirect);
    } else {
      throw new IllegalStateException("Tipo de entrada nao suportado");
    }
    return concluirItem(snapshot, execucaoId, entrada, processamento);
  }

  private Processamento concluirItem(
      Snapshot snapshot,
      UUID execucaoId,
      Entrada entrada,
      Processamento processamento) {
    String mapeamentoStatus = switch (processamento.statusStage()) {
      case "PROCESSADO" -> "MAPEADO";
      case "REJEITADO" -> "REJEITADO";
      default -> "DIVERGENTE";
    };
    mapear(
        execucaoId,
        entrada.tabela(),
        entrada.idOrigem(),
        hash(entrada.dados()),
        entrada.tipo(),
        processamento.entidadeId(),
        mapeamentoStatus,
        snapshot.capturadoEm());
    processamento.pendencias().stream().distinct().forEach(codigo -> pendencia(
        execucaoId,
        codigo,
        entrada.tipo(),
        entrada.idOrigem(),
        snapshot.capturadoEm()));
    jdbc.update(
        """
        UPDATE stg_url
        SET status = ?, pendencia_codigo = ?, entidade_v3_id = ?, processado_em = ?
        WHERE execucao_id = ? AND tabela_origem = ? AND id_origem = ?
        """,
        processamento.statusStage(),
        processamento.pendencias().isEmpty()
            ? null
            : processamento.pendencias().get(0).name(),
        processamento.entidadeId(),
        snapshot.capturadoEm(),
        execucaoId,
        entrada.tabela(),
        entrada.idOrigem());
    return processamento;
  }

  private Processamento processarFaq(Snapshot snapshot, FaqLegada faq) {
    if (!planejador.faqPublicavel(faq.ativa(), faq.status(), faq.publicadoEm())) {
      return Processamento.descartado();
    }
    Resultado pergunta = sanitizador.sanitizarTexto(faq.pergunta());
    Resultado resposta = sanitizador.sanitizarMarkdown(faq.resposta());
    String categoria = planejador.categoriaFaq(faq.categoria());
    if (pergunta.conteudo().length() < 5
        || pergunta.conteudo().length() > 240
        || resposta.conteudo().length() < 10
        || resposta.conteudo().length() > 4000
        || categoria == null) {
      return Processamento.quarentena(CodigoPendenciaImportacao.CONTEUDO_VAZIO_OU_FRACO);
    }
    UUID id = uuid("faq", faq.idOrigem());
    int inseridos = jdbc.update(
        """
        INSERT INTO faq_item (
          id, pergunta, resposta, categoria, status, ordem,
          criado_por_usuario_id, atualizado_por_usuario_id, criado_request_id,
          publicado_em, arquivado_em, criado_em, atualizado_em, versao
        ) VALUES (?, ?, ?, ?, 'PUBLICADO', ?, ?, ?, ?, ?, NULL, ?, ?, 0)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        pergunta.conteudo(),
        resposta.conteudo(),
        categoria,
        ordemFaq(faq.ordem()),
        snapshot.atorSistemaV3Id(),
        snapshot.atorSistemaV3Id(),
        requestId("faq", faq.idOrigem()),
        faq.publicadoEm(),
        faq.criadoEm(),
        faq.atualizadoEm());
    List<CodigoPendenciaImportacao> pendencias = new ArrayList<>();
    if (pergunta.alterado() || resposta.alterado()) {
      pendencias.add(CodigoPendenciaImportacao.CONTEUDO_HTML_SANITIZADO);
    }
    return new Processamento(inseridos, id, "PROCESSADO", List.copyOf(pendencias));
  }

  private Processamento processarAviso(Snapshot snapshot, AvisoLegado aviso) {
    Resultado titulo = sanitizador.sanitizarTexto(aviso.titulo());
    Resultado descricao = sanitizador.sanitizarMarkdown(aviso.descricao());
    StatusPlanejado status = planejador.statusAviso(
        aviso.status(), aviso.publicadoEm(), aviso.atualizadoEm());
    if (!status.valido()
        || titulo.conteudo().length() < 3
        || titulo.conteudo().length() > 160
        || descricao.conteudo().length() < 5
        || descricao.conteudo().length() > 4000
        || (aviso.ativoDe() != null
            && aviso.ativoAte() != null
            && !aviso.ativoAte().isAfter(aviso.ativoDe()))) {
      return Processamento.quarentena(CodigoPendenciaImportacao.CONTEUDO_VAZIO_OU_FRACO);
    }
    List<CodigoPendenciaImportacao> pendencias = new ArrayList<>();
    UUID ator = ator(snapshot, aviso.atorOrigemId(), pendencias);
    UUID id = uuid("aviso", aviso.idOrigem());
    int inseridos = jdbc.update(
        """
        INSERT INTO aviso_administrativo (
          id, titulo, descricao, local_exibicao, frequencia_exibicao, status,
          permite_dispensar, ativo_de, ativo_ate, criado_por_usuario_id,
          criado_por_nome, atualizado_por_usuario_id, criado_request_id,
          publicado_em, retirado_em, arquivado_em, criado_em, atualizado_em, versao
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Sistema de migracao', ?, ?, ?, ?, ?, ?, ?, 0)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        titulo.conteudo(),
        descricao.conteudo(),
        planejador.localAviso(aviso.localExibicao()),
        planejador.frequenciaAviso(aviso.frequenciaExibicao()),
        status.status(),
        aviso.permiteDispensar() == null || aviso.permiteDispensar(),
        aviso.ativoDe(),
        aviso.ativoAte(),
        ator,
        ator,
        requestId("aviso", aviso.idOrigem()),
        status.publicadoEm(),
        status.arquivadoEm(),
        status.arquivadoEm(),
        aviso.criadoEm(),
        aviso.atualizadoEm());
    if (titulo.alterado() || descricao.alterado()) {
      pendencias.add(CodigoPendenciaImportacao.CONTEUDO_HTML_SANITIZADO);
    }
    return new Processamento(inseridos, id, "PROCESSADO", List.copyOf(pendencias));
  }

  private Processamento processarCategoriaBlog(
      Snapshot snapshot,
      BlogCategoriaLegada categoria) {
    Resultado nome = sanitizador.sanitizarTexto(categoria.nome());
    UUID id = uuid("blog-categoria", categoria.idOrigem());
    SlugPlanejado slug = planejador.planejarSlug(
        categoria.slug(),
        categoria.idOrigem(),
        donoSlugCategoria(snapshot, categoria),
        candidato -> slugCategoriaDisponivel(candidato, id));
    if (nome.conteudo().length() < 2
        || nome.conteudo().length() > 120
        || slug.slug() == null
        || slug.slug().length() > 120
        || !nomeCategoriaDisponivel(nome.conteudo(), id)) {
      return Processamento.quarentena(CodigoPendenciaImportacao.CONTEUDO_VAZIO_OU_FRACO);
    }
    int inseridos = jdbc.update(
        """
        INSERT INTO blog_categoria (
          id, nome, slug, ordem, ativa, criado_em, atualizado_em, versao
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        nome.conteudo(),
        slug.slug(),
        Math.max(0, categoria.ordem() == null ? 0 : categoria.ordem()),
        categoria.ativa(),
        categoria.criadoEm(),
        categoria.atualizadoEm());
    List<CodigoPendenciaImportacao> pendencias = new ArrayList<>();
    if (nome.alterado()) {
      pendencias.add(CodigoPendenciaImportacao.CONTEUDO_HTML_SANITIZADO);
    }
    if (slug.normalizado()) {
      pendencias.add(CodigoPendenciaImportacao.SLUG_NORMALIZADO);
    }
    if (slug.colisao()) {
      pendencias.add(CodigoPendenciaImportacao.SLUG_DUPLICADO);
    }
    return new Processamento(inseridos, id, "PROCESSADO", List.copyOf(pendencias));
  }

  private Processamento processarPostBlog(Snapshot snapshot, BlogPostLegado post) {
    Resultado titulo = sanitizador.sanitizarTexto(post.titulo());
    Resultado resumo = sanitizador.sanitizarTexto(post.resumo());
    Resultado conteudo;
    try {
      conteudo = sanitizador.sanitizarBlog(post.conteudo());
    } catch (RuntimeException exception) {
      return Processamento.quarentena(CodigoPendenciaImportacao.CONTEUDO_VAZIO_OU_FRACO);
    }
    StatusPlanejado status = planejador.statusBlog(
        post.status(), post.publicadoEm(), post.arquivadoEm());
    UUID categoriaId = uuid("blog-categoria", post.categoriaOrigemId());
    UUID id = uuid("blog-post", post.idOrigem());
    SlugPlanejado slug = planejador.planejarSlug(
        post.slug(),
        post.idOrigem(),
        donoSlugPost(snapshot, post),
        candidato -> slugPostDisponivel(candidato, id));
    boolean categoriaExiste = registroExiste("blog_categoria", categoriaId);
    if (!status.valido()
        || !categoriaExiste
        || titulo.conteudo().length() < 3
        || titulo.conteudo().length() > 180
        || resumo.conteudo().isBlank()
        || resumo.conteudo().length() > 320
        || post.autorNomePublico() == null
        || post.autorNomePublico().length() < 2
        || post.autorNomePublico().length() > 120
        || slug.slug() == null
        || slug.slug().length() > 180) {
      CodigoPendenciaImportacao codigo = !categoriaExiste
          ? CodigoPendenciaImportacao.BLOG_CATEGORIA_AUSENTE
          : CodigoPendenciaImportacao.CONTEUDO_VAZIO_OU_FRACO;
      return Processamento.quarentena(codigo);
    }
    try {
      if (status.publico()) {
        blogValidator.validar(conteudo.conteudo());
      } else {
        blogValidator.validarRascunho(conteudo.conteudo());
      }
    } catch (RuntimeException exception) {
      return Processamento.quarentena(CodigoPendenciaImportacao.CONTEUDO_VAZIO_OU_FRACO);
    }
    List<CodigoPendenciaImportacao> pendencias = new ArrayList<>();
    UUID ator = ator(snapshot, post.atorOrigemId(), pendencias);
    UUID imagemCapa = imagemPublicaDisponivel(post.imagemCapaV3Id(), id)
        ? post.imagemCapaV3Id()
        : null;
    UUID imagemOg = imagemPublicaDisponivel(post.imagemOgV3Id(), id)
        ? post.imagemOgV3Id()
        : null;
    if ((post.imagemCapaV3Id() != null && imagemCapa == null)
        || (post.imagemOgV3Id() != null && imagemOg == null)) {
      pendencias.add(CodigoPendenciaImportacao.BLOG_IMAGEM_PRIVADA_REJEITADA);
    }
    String seoTitle = limitar(titulo.conteudo() + " - Tops do Job", 180);
    String seoDescription = limitar(resumo.conteudo(), 320);
    int inseridos = jdbc.update(
        """
        INSERT INTO blog_post (
          id, categoria_id, titulo, slug, resumo, conteudo, autor_nome, status,
          seo_title, seo_description, sitemap_priority, change_frequency,
          imagem_capa_id, imagem_og_id, criado_por_usuario_id,
          atualizado_por_usuario_id, criado_request_id, publicado_em,
          arquivado_em, criado_em, atualizado_em, versao
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0.7, 'weekly', ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        categoriaId,
        titulo.conteudo(),
        slug.slug(),
        resumo.conteudo(),
        conteudo.conteudo(),
        post.autorNomePublico(),
        status.status(),
        seoTitle,
        seoDescription,
        imagemCapa,
        imagemOg,
        ator,
        ator,
        requestId("blog-post", post.idOrigem()),
        status.publicadoEm(),
        status.arquivadoEm(),
        post.criadoEm(),
        post.atualizadoEm());
    String caminho = "/blog/" + slug.slug();
    MetadataSeo metadata = geradorMetadata.gerarEditorial(
        titulo.conteudo(), resumo.conteudo(), caminho);
    SeoPersistido seo = garantirSeoUrl(
        caminho,
        "BLOG",
        "BLOG_POST",
        id,
        status.publico(),
        status.publico(),
        status.publico() ? null : "CONTEUDO_NAO_PUBLICADO",
        post.criadoEm(),
        post.atualizadoEm());
    inseridos += seo.novos();
    inseridos += garantirMetadata(
        seo.id(),
        metadata,
        status.publico(),
        ator,
        post.criadoEm(),
        post.atualizadoEm());
    if (titulo.alterado() || resumo.alterado() || conteudo.alterado()) {
      pendencias.add(CodigoPendenciaImportacao.CONTEUDO_HTML_SANITIZADO);
    }
    if (slug.normalizado()) {
      pendencias.add(CodigoPendenciaImportacao.SLUG_NORMALIZADO);
    }
    if (slug.colisao()) {
      pendencias.add(CodigoPendenciaImportacao.SLUG_DUPLICADO);
    }
    return new Processamento(inseridos, id, "PROCESSADO", List.copyOf(pendencias));
  }

  private Processamento processarConteudoInstitucional(
      Snapshot snapshot,
      ConteudoInstitucionalLegado conteudo) {
    var definicao = ConteudoSiteCatalogo.porChavePublica(conteudo.chavePublica())
        .orElse(null);
    if (definicao == null) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.CONTEUDO_INSTITUCIONAL_FORA_CATALOGO);
    }
    Resultado titulo = sanitizador.sanitizarTexto(conteudo.titulo());
    Resultado corpo = sanitizador.sanitizarMarkdown(conteudo.corpo());
    StatusPlanejado status = planejador.statusInstitucional(
        conteudo.status(), conteudo.publicadoEm(), conteudo.atualizadoEm());
    if (!status.valido()
        || titulo.conteudo().isBlank()
        || titulo.conteudo().length() > 180
        || corpo.conteudo().length() < 40
        || corpo.conteudo().length() > 200_000
        || !geradorMetadata.caminhoPublicoSeguro(definicao.caminhoPublico())) {
      return Processamento.quarentena(CodigoPendenciaImportacao.CONTEUDO_VAZIO_OU_FRACO);
    }
    List<CodigoPendenciaImportacao> pendencias = new ArrayList<>();
    UUID ator = ator(snapshot, conteudo.atorOrigemId(), pendencias);
    MetadataSeo metadata = geradorMetadata.gerarEditorial(
        titulo.conteudo(),
        limitar(corpo.conteudo().replaceAll("[#*_\\[\\]()]+", " "), 160),
        definicao.caminhoPublico());
    SeoPersistido seo = garantirSeoUrl(
        definicao.caminhoPublico(),
        "INSTITUCIONAL",
        "CONTEUDO_INSTITUCIONAL",
        null,
        status.publico(),
        status.publico(),
        status.publico() ? null : "CONTEUDO_NAO_PUBLICADO",
        conteudo.criadoEm(),
        conteudo.atualizadoEm());
    int inseridos = seo.novos();
    inseridos += garantirMetadata(
        seo.id(),
        metadata,
        status.publico(),
        ator,
        conteudo.criadoEm(),
        conteudo.atualizadoEm());
    UUID id = uuid("conteudo-institucional", conteudo.idOrigem());
    inseridos += jdbc.update(
        """
        INSERT INTO seo_conteudo_pagina (
          id, seo_url_id, chave, titulo, corpo_markdown, status, origem,
          criado_por, aprovado_por, criado_em, atualizado_em, aprovado_em, versao
        ) VALUES (?, ?, ?, ?, ?, ?, 'IMPORTACAO', ?, ?, ?, ?, ?, 0)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        seo.id(),
        definicao.chavePersistida(),
        titulo.conteudo(),
        corpo.conteudo(),
        status.status(),
        ator,
        status.publico() ? ator : null,
        conteudo.criadoEm(),
        conteudo.atualizadoEm(),
        status.publicadoEm());
    if (titulo.alterado() || corpo.alterado()) {
      pendencias.add(CodigoPendenciaImportacao.CONTEUDO_HTML_SANITIZADO);
    }
    return new Processamento(inseridos, id, "PROCESSADO", List.copyOf(pendencias));
  }

  private Processamento processarLocalidade(
      Snapshot snapshot,
      LocalidadeSeoLegada localidade) {
    if (!geradorMetadata.caminhoPublicoSeguro(localidade.caminhoPublico())
        || !caminhoCompativelComLocalidade(localidade)) {
      return Processamento.quarentena(CodigoPendenciaImportacao.URL_SEM_DECISAO);
    }
    Decisao decisao = politicaIndexacao.avaliar(localidade);
    MetadataSeo metadata = geradorMetadata.gerarLocalidade(localidade);
    if (!schemaSeguro(metadata.schemaJson())) {
      return Processamento.quarentena(
          CodigoPendenciaImportacao.SEO_DADOS_ESTRUTURADOS_INVALIDOS);
    }
    String tipoSeo = switch (localidade.tipo()) {
      case ESTADO -> "OUTRO";
      case CIDADE -> "CIDADE";
      case BAIRRO -> "BAIRRO";
    };
    UUID entidadeId = uuid("localidade-seo", localidade.tipo().name(), localidade.idOrigem());
    SeoPersistido seo = garantirSeoUrl(
        localidade.caminhoPublico(),
        tipoSeo,
        localidade.tipo().name(),
        entidadeId,
        decisao.indexavel(),
        decisao.incluirSitemap(),
        decisao.motivo() == null ? null : decisao.motivo().name(),
        localidade.atualizadoEm(),
        localidade.atualizadoEm());
    int inseridos = seo.novos();
    inseridos += garantirMetadata(
        seo.id(),
        metadata,
        decisao.indexavel(),
        snapshot.atorSistemaV3Id(),
        localidade.atualizadoEm(),
        localidade.atualizadoEm());
    Resultado introducao = sanitizador.sanitizarMarkdown(metadata.introducao());
    if (localidade.conteudoValido() && introducao.conteudo().length() >= 20) {
      String corpo = corpoLocalidade(introducao.conteudo(), metadata.linksInternos());
      inseridos += jdbc.update(
          """
          INSERT INTO seo_conteudo_pagina (
            id, seo_url_id, chave, titulo, corpo_markdown, status, origem,
            criado_por, aprovado_por, criado_em, atualizado_em, aprovado_em, versao
          ) VALUES (?, ?, 'introducao_localidade', ?, ?, 'PUBLICADO', 'GERADO',
            ?, ?, ?, ?, ?, 0)
          ON CONFLICT (id) DO NOTHING
          """,
          uuid("conteudo-localidade", localidade.tipo().name(), localidade.idOrigem()),
          seo.id(),
          metadata.h1(),
          corpo,
          snapshot.atorSistemaV3Id(),
          snapshot.atorSistemaV3Id(),
          localidade.atualizadoEm(),
          localidade.atualizadoEm(),
          localidade.atualizadoEm());
    }
    List<CodigoPendenciaImportacao> pendencias = decisao.indexavel()
        ? List.of()
        : List.of(CodigoPendenciaImportacao.LOCALIDADE_NAO_INDEXAVEL);
    return new Processamento(inseridos, seo.id(), "PROCESSADO", pendencias);
  }

  private Processamento processarAnuncioSeo(
      Snapshot snapshot,
      ReferenciaAnuncioSeo anuncio) {
    String slug = planejador.normalizarSlug(anuncio.slug());
    if (slug == null || !slug.equals(anuncio.slug())) {
      return Processamento.quarentena(CodigoPendenciaImportacao.URL_SEM_DECISAO);
    }
    String caminho = "/anuncios/" + slug;
    SeoPersistido seo = garantirSeoUrl(
        caminho,
        "ANUNCIO",
        "ANUNCIO",
        anuncio.anuncioV3Id(),
        anuncio.shouldIndexAnuncio(),
        anuncio.shouldIndexAnuncio(),
        anuncio.shouldIndexAnuncio() ? null : "SHOULD_INDEX_ANUNCIO_FALSE",
        anuncio.atualizadoEm(),
        anuncio.atualizadoEm());
    List<CodigoPendenciaImportacao> pendencias = anuncio.shouldIndexAnuncio()
        ? List.of()
        : List.of(CodigoPendenciaImportacao.SEO_PENDENTE);
    return new Processamento(seo.novos(), seo.id(), "PROCESSADO", pendencias);
  }

  private Processamento processarRedirect(Snapshot snapshot, RedirectLegado redirect) {
    if (!donoOrigemRedirect(snapshot, redirect)) {
      return Processamento.quarentena(CodigoPendenciaImportacao.REDIRECT_INVALIDO);
    }
    var plano = planejador.planejarRedirect(redirect, snapshot.redirects());
    if (!plano.valido()) {
      return Processamento.quarentena(plano.erro());
    }
    UUID id = uuid("seo-redirect", redirect.origemCaminho());
    List<UUID> existente = jdbc.query(
        "SELECT id FROM seo_redirect WHERE origem_caminho = ? AND ativo = true",
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        redirect.origemCaminho());
    int inseridos = 0;
    UUID entidadeId = existente.isEmpty() ? id : existente.get(0);
    if (existente.isEmpty()) {
      inseridos = jdbc.update(
          """
          INSERT INTO seo_redirect (
            id, origem_caminho, destino_caminho, status_code, ativo, motivo,
            criado_por, criado_em, atualizado_em
          ) VALUES (?, ?, ?, 301, true, 'IMPORTACAO_LEGADA_FASE_2', ?, ?, ?)
          ON CONFLICT (id) DO NOTHING
          """,
          id,
          redirect.origemCaminho(),
          plano.destinoFinal(),
          snapshot.atorSistemaV3Id(),
          redirect.atualizadoEm(),
          redirect.atualizadoEm());
    }
    return new Processamento(inseridos, entidadeId, "PROCESSADO", List.of());
  }

  private List<Entrada> entradas(Snapshot snapshot) {
    List<Entrada> itens = new ArrayList<>();
    snapshot.faqs().forEach(item -> itens.add(
        new Entrada(TABELA_FAQ, item.idOrigem(), TipoEntidadeImportacao.FAQ, item)));
    snapshot.avisos().forEach(item -> itens.add(
        new Entrada(TABELA_AVISO, item.idOrigem(), TipoEntidadeImportacao.AVISO, item)));
    snapshot.categoriasBlog().forEach(item -> itens.add(new Entrada(
        TABELA_BLOG_CATEGORIA,
        item.idOrigem(),
        TipoEntidadeImportacao.BLOG_CATEGORIA,
        item)));
    snapshot.postsBlog().forEach(item -> itens.add(new Entrada(
        TABELA_BLOG_POST,
        item.idOrigem(),
        TipoEntidadeImportacao.BLOG_POST,
        item)));
    snapshot.conteudosInstitucionais().forEach(item -> itens.add(new Entrada(
        TABELA_INSTITUCIONAL,
        item.idOrigem(),
        TipoEntidadeImportacao.CONTEUDO_INSTITUCIONAL,
        item)));
    snapshot.localidadesSeo().forEach(item -> itens.add(new Entrada(
        TABELA_LOCALIDADE,
        item.idOrigem(),
        TipoEntidadeImportacao.SEO,
        item)));
    snapshot.anunciosSeo().forEach(item -> itens.add(new Entrada(
        TABELA_ANUNCIO,
        item.idOrigem(),
        TipoEntidadeImportacao.SEO,
        item)));
    snapshot.redirects().forEach(item -> itens.add(new Entrada(
        TABELA_REDIRECT,
        item.idOrigem(),
        TipoEntidadeImportacao.REDIRECT,
        item)));
    return itens.stream()
        .sorted(Comparator.comparing(Entrada::tabela).thenComparing(Entrada::idOrigem))
        .toList();
  }

  private boolean ehConteudoDuplicado(Snapshot snapshot, Entrada atual) {
    String assinatura = assinaturaConteudo(atual);
    if (assinatura == null) {
      return false;
    }
    return entradas(snapshot).stream()
        .filter(item -> assinatura.equals(assinaturaConteudo(item)))
        .map(Entrada::chave)
        .min(Comparator.comparing(ChaveEntrada::tabela)
            .thenComparing(ChaveEntrada::idOrigem))
        .filter(dono -> !dono.equals(atual.chave()))
        .isPresent();
  }

  private String assinaturaConteudo(Entrada entrada) {
    Object dados = entrada.dados();
    String conteudo;
    if (dados instanceof BlogPostLegado post) {
      conteudo = post.conteudo();
    } else if (dados instanceof ConteudoInstitucionalLegado institucional) {
      conteudo = institucional.corpo();
    } else {
      conteudo = null;
    }
    if (conteudo == null) {
      return null;
    }
    String normalizado = conteudo
        .replaceAll("(?is)<[^>]+>", " ")
        .replaceAll("\\s+", " ")
        .trim()
        .toLowerCase(Locale.ROOT);
    return normalizado.length() < 20 ? null : hash(normalizado);
  }

  private boolean donoSlugCategoria(
      Snapshot snapshot,
      BlogCategoriaLegada atual) {
    String slug = planejador.normalizarSlug(atual.slug());
    return snapshot.categoriasBlog().stream()
        .filter(item -> Objects.equals(planejador.normalizarSlug(item.slug()), slug))
        .map(BlogCategoriaLegada::idOrigem)
        .min(String::compareTo)
        .filter(atual.idOrigem()::equals)
        .isPresent();
  }

  private boolean donoSlugPost(Snapshot snapshot, BlogPostLegado atual) {
    String slug = planejador.normalizarSlug(atual.slug());
    return snapshot.postsBlog().stream()
        .filter(item -> Objects.equals(planejador.normalizarSlug(item.slug()), slug))
        .map(BlogPostLegado::idOrigem)
        .min(String::compareTo)
        .filter(atual.idOrigem()::equals)
        .isPresent();
  }

  private boolean donoOrigemRedirect(Snapshot snapshot, RedirectLegado atual) {
    return snapshot.redirects().stream()
        .filter(item -> item.origemCaminho().equals(atual.origemCaminho()))
        .map(RedirectLegado::idOrigem)
        .min(String::compareTo)
        .filter(atual.idOrigem()::equals)
        .isPresent();
  }

  private UUID ator(
      Snapshot snapshot,
      String atorOrigemId,
      List<CodigoPendenciaImportacao> pendencias) {
    if (atorOrigemId == null) {
      return snapshot.atorSistemaV3Id();
    }
    UUID mapeado = snapshot.atoresV3().get(atorOrigemId);
    if (mapeado != null && usuarioExiste(mapeado)) {
      return mapeado;
    }
    pendencias.add(CodigoPendenciaImportacao.AUTOR_CONTEUDO_NAO_MAPEADO);
    return snapshot.atorSistemaV3Id();
  }

  private boolean caminhoCompativelComLocalidade(LocalidadeSeoLegada localidade) {
    String[] partes = localidade.caminhoPublico().split("/");
    int esperado = switch (localidade.tipo()) {
      case ESTADO -> 3;
      case CIDADE -> 4;
      case BAIRRO -> 5;
    };
    return partes.length == esperado
        && "acompanhantes".equals(partes[1])
        && localidade.uf().equalsIgnoreCase(partes[2]);
  }

  private boolean schemaSeguro(Map<String, Object> schema) {
    String serializado = json(schema).toLowerCase(Locale.ROOT);
    return serializado.contains("\"@context\":\"https://schema.org\"")
        && !serializado.contains("v3.esle.cloud")
        && !serializado.contains("object_key")
        && !serializado.contains("usuarioid")
        && !serializado.contains("aggregateRating".toLowerCase(Locale.ROOT))
        && !serializado.contains("\"price\"")
        && !serializado.contains("\"availability\"");
  }

  private String corpoLocalidade(String introducao, List<LinkInterno> links) {
    if (links.isEmpty()) {
      return introducao;
    }
    StringBuilder corpo = new StringBuilder(introducao)
        .append("\n\n## Localidades relacionadas\n");
    for (LinkInterno link : links) {
      if (geradorMetadata.caminhoPublicoSeguro(link.caminho())) {
        corpo.append("\n- [")
            .append(link.rotulo().replace("[", "").replace("]", ""))
            .append("](")
            .append(link.caminho())
            .append(')');
      }
    }
    return corpo.toString();
  }

  private int ordemFaq(Integer ordem) {
    if (ordem == null) {
      return 0;
    }
    return Math.max(0, Math.min(100_000, ordem));
  }

  private String requestId(String tipo, String idOrigem) {
    return "importacao-" + tipo + "-" + uuid(tipo, idOrigem);
  }

  private String limitar(String valor, int limite) {
    String normalizado = valor == null ? "" : valor.replaceAll("\\s+", " ").trim();
    if (normalizado.length() <= limite) {
      return normalizado;
    }
    int corte = normalizado.lastIndexOf(' ', limite - 1);
    return normalizado.substring(0, corte > 20 ? corte : limite).trim();
  }

  private boolean slugCategoriaDisponivel(String slug, UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) = 0 FROM blog_categoria WHERE slug = ? AND id <> ?",
        Boolean.class,
        slug,
        id));
  }

  private boolean nomeCategoriaDisponivel(String nome, UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) = 0 FROM blog_categoria WHERE nome = ? AND id <> ?",
        Boolean.class,
        nome,
        id));
  }

  private boolean slugPostDisponivel(String slug, UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) = 0 FROM blog_post WHERE slug = ? AND id <> ?",
        Boolean.class,
        slug,
        id));
  }

  private boolean imagemPublicaDisponivel(UUID imagemId, UUID postId) {
    if (imagemId == null) {
      return false;
    }
    return Boolean.TRUE.equals(jdbc.queryForObject(
        """
        SELECT count(*) > 0
        FROM blog_imagem imagem
        WHERE imagem.id = ?
          AND imagem.estado = 'PUBLICA'
          AND imagem.public_object_key IS NOT NULL
          AND NOT EXISTS (
            SELECT 1
            FROM blog_post post
            WHERE post.id <> ?
              AND (post.imagem_capa_id = imagem.id OR post.imagem_og_id = imagem.id)
          )
        """,
        Boolean.class,
        imagemId,
        postId));
  }

  private boolean registroExiste(String tabela, UUID id) {
    if (!Set.of("blog_categoria", "blog_post", "usuario").contains(tabela)) {
      throw new IllegalArgumentException("Tabela nao permitida");
    }
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT count(*) > 0 FROM " + tabela + " WHERE id = ?",
        Boolean.class,
        id));
  }

  private boolean usuarioExiste(UUID id) {
    return id != null && registroExiste("usuario", id);
  }

  private SeoPersistido garantirSeoUrl(
      String caminho,
      String tipo,
      String entidadeTipo,
      UUID entidadeId,
      boolean indexavel,
      boolean incluirSitemap,
      String motivoNoindex,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm) {
    List<SeoUrlExistente> existentes = jdbc.query(
        """
        SELECT id, caminho_publico, canonical_path, indexavel, incluir_sitemap
        FROM seo_url
        WHERE caminho_publico = ? OR canonical_path = ?
        ORDER BY criado_em
        LIMIT 1
        """,
        (rs, rowNum) -> new SeoUrlExistente(
            rs.getObject("id", UUID.class),
            rs.getString("caminho_publico"),
            rs.getString("canonical_path"),
            rs.getBoolean("indexavel"),
            rs.getBoolean("incluir_sitemap")),
        caminho,
        caminho);
    if (!existentes.isEmpty()) {
      SeoUrlExistente existente = existentes.get(0);
      if (!caminho.equals(existente.caminhoPublico())
          || !caminho.equals(existente.canonicalPath())) {
        throw new IllegalStateException("Canonical conflitante para caminho importado");
      }
      if (indexavel && (!existente.indexavel() || !existente.incluirSitemap())) {
        jdbc.update(
            """
            UPDATE seo_url
            SET indexavel = true, incluir_sitemap = true, status_esperado = 'OK_200',
              qualidade_status = 'APROVADO', motivo_noindex = NULL,
              ultima_validacao_em = ?, atualizado_em = ?
            WHERE id = ?
            """,
            atualizadoEm,
            atualizadoEm,
            existente.id());
      }
      return new SeoPersistido(existente.id(), 0);
    }
    UUID id = uuid("seo-url", caminho);
    int inseridos = jdbc.update(
        """
        INSERT INTO seo_url (
          id, caminho_publico, canonical_path, tipo, entidade_tipo, entidade_id,
          status_esperado, indexavel, incluir_sitemap, qualidade_status,
          ultima_validacao_em, motivo_noindex, criado_em, atualizado_em, versao
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        caminho,
        caminho,
        tipo,
        entidadeTipo,
        entidadeId,
        indexavel ? "OK_200" : "NOINDEX",
        indexavel,
        incluirSitemap,
        indexavel ? "APROVADO" : "INSUFICIENTE",
        atualizadoEm,
        motivoNoindex,
        criadoEm,
        atualizadoEm);
    return new SeoPersistido(id, inseridos);
  }

  private int garantirMetadata(
      UUID seoUrlId,
      MetadataSeo metadata,
      boolean indexavel,
      UUID ator,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm) {
    UUID id = uuid("seo-metadado", seoUrlId.toString());
    List<UUID> existente = jdbc.query(
        "SELECT id FROM seo_metadado WHERE id = ?",
        (rs, rowNum) -> rs.getObject(1, UUID.class),
        id);
    if (!existente.isEmpty()) {
      if (indexavel) {
        jdbc.update(
            """
            UPDATE seo_metadado
            SET titulo = ?, descricao = ?, robots = 'INDEX_FOLLOW',
              og_titulo = ?, og_descricao = ?, schema_json = ?::jsonb,
              atualizado_em = ?
            WHERE id = ? AND robots <> 'INDEX_FOLLOW'
            """,
            metadata.titulo(),
            metadata.descricao(),
            metadata.titulo(),
            metadata.descricao(),
            json(metadata.schemaJson()),
            atualizadoEm,
            id);
      }
      return 0;
    }
    return jdbc.update(
        """
        INSERT INTO seo_metadado (
          id, seo_url_id, titulo, descricao, robots, og_titulo, og_descricao,
          schema_json, origem, criado_por, criado_em, atualizado_em
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'IMPORTACAO', ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        id,
        seoUrlId,
        metadata.titulo(),
        metadata.descricao(),
        indexavel ? "INDEX_FOLLOW" : "NOINDEX_FOLLOW",
        metadata.titulo(),
        metadata.descricao(),
        json(metadata.schemaJson()),
        ator,
        criadoEm,
        atualizadoEm);
  }

  private ResultadoImportacaoConteudoSeo finalizar(
      Snapshot snapshot,
      UUID execucaoId,
      ContadoresChamada chamada) {
    long analisados = entradas(snapshot).size();
    long importados = contarMapeamentos(execucaoId, "MAPEADO");
    long descartados = contarMapeamentos(execucaoId, "REJEITADO");
    long quarentena = contarMapeamentos(execucaoId, "DIVERGENTE");
    long publicados = contarPublicados(execucaoId);
    long rascunhos = contarRascunhos(execucaoId);
    long noindex = jdbc.queryForObject(
        """
        SELECT count(*)
        FROM seo_url url
        JOIN importacao_mapeamento mapa
          ON mapa.entidade_v3_id = url.id
        WHERE mapa.execucao_id = ?
          AND url.indexavel = false
        """,
        Long.class,
        execucaoId);
    long redirects = jdbc.queryForObject(
        """
        SELECT count(*)
        FROM importacao_mapeamento
        WHERE execucao_id = ?
          AND entidade_tipo = 'REDIRECT'
          AND status = 'MAPEADO'
        """,
        Long.class,
        execucaoId);
    long restantes = jdbc.queryForObject(
        "SELECT count(*) FROM stg_url WHERE execucao_id = ? AND status = 'PENDENTE'",
        Long.class,
        execucaoId);
    long pendenciasAbertas = jdbc.queryForObject(
        """
        SELECT count(*)
        FROM importacao_pendencia
        WHERE execucao_id = ? AND status = 'ABERTA'
        """,
        Long.class,
        execucaoId);
    String status = restantes > 0
        ? "EM_EXECUCAO"
        : (pendenciasAbertas > 0 ? "CONCLUIDA_COM_PENDENCIAS" : "CONCLUIDA");
    Map<String, Object> resumo = new LinkedHashMap<>();
    resumo.put("snapshotFingerprint", hash(snapshot));
    resumo.put("snapshotId", snapshot.snapshotId());
    resumo.put("analisados", analisados);
    resumo.put("importados", importados);
    resumo.put("publicados", publicados);
    resumo.put("rascunhos", rascunhos);
    resumo.put("noindex", noindex);
    resumo.put("descartados", descartados);
    resumo.put("quarentena", quarentena);
    resumo.put("redirects", redirects);
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
    return new ResultadoImportacaoConteudoSeo(
        execucaoId,
        status,
        analisados,
        importados,
        publicados,
        rascunhos,
        noindex,
        descartados,
        quarentena,
        redirects,
        chamada.processados,
        chamada.novos,
        restantes);
  }

  private long contarMapeamentos(UUID execucaoId, String status) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM importacao_mapeamento
        WHERE execucao_id = ? AND status = ?
        """,
        Long.class,
        execucaoId,
        status);
  }

  private long contarPublicados(UUID execucaoId) {
    return jdbc.queryForObject(
        """
        SELECT
          (SELECT count(*) FROM faq_item faq
            JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = faq.id
            WHERE mapa.execucao_id = ? AND faq.status = 'PUBLICADO')
          + (SELECT count(*) FROM aviso_administrativo aviso
            JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = aviso.id
            WHERE mapa.execucao_id = ? AND aviso.status = 'PUBLICADO')
          + (SELECT count(*) FROM blog_post post
            JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = post.id
            WHERE mapa.execucao_id = ? AND post.status = 'PUBLICADO')
          + (SELECT count(*) FROM seo_conteudo_pagina conteudo
            JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = conteudo.id
            WHERE mapa.execucao_id = ? AND conteudo.status = 'PUBLICADO')
        """,
        Long.class,
        execucaoId,
        execucaoId,
        execucaoId,
        execucaoId);
  }

  private long contarRascunhos(UUID execucaoId) {
    return jdbc.queryForObject(
        """
        SELECT
          (SELECT count(*) FROM aviso_administrativo aviso
            JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = aviso.id
            WHERE mapa.execucao_id = ? AND aviso.status = 'RASCUNHO')
          + (SELECT count(*) FROM blog_post post
            JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = post.id
            WHERE mapa.execucao_id = ? AND post.status = 'RASCUNHO')
          + (SELECT count(*) FROM seo_conteudo_pagina conteudo
            JOIN importacao_mapeamento mapa ON mapa.entidade_v3_id = conteudo.id
            WHERE mapa.execucao_id = ? AND conteudo.status = 'RASCUNHO')
        """,
        Long.class,
        execucaoId,
        execucaoId,
        execucaoId);
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
        uuid("mapeamento-conteudo-seo", execucaoId.toString(), tabela, idOrigem),
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
        uuid("pendencia-conteudo-seo", execucaoId.toString(), codigo.name(), tipo.name(), idOrigem),
        execucaoId,
        codigo.name(),
        severidadeBanco(codigo.severidadePadrao()),
        tipo.name(),
        idOrigem,
        "Revisao manual requerida: " + codigo.name(),
        criadoEm);
  }

  private String hash(Object valor) {
    try {
      byte[] serializado = hashMapper.writeValueAsBytes(valor);
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

  private static String severidadeBanco(SeveridadePendenciaImportacao severidade) {
    return switch (severidade) {
      case INFO -> "INFO";
      case ALERTA -> "MEDIA";
      case ERRO -> "ALTA";
      case BLOQUEANTE -> "CRITICA";
    };
  }

  private record SeoUrlExistente(
      UUID id,
      String caminhoPublico,
      String canonicalPath,
      boolean indexavel,
      boolean incluirSitemap) {
  }

  // Processadores de dominio sao mantidos abaixo para preservar transacao por item.

  private record ChaveEntrada(String tabela, String idOrigem) {
  }

  private record Entrada(
      String tabela,
      String idOrigem,
      TipoEntidadeImportacao tipo,
      Object dados) {
    private ChaveEntrada chave() {
      return new ChaveEntrada(tabela, idOrigem);
    }
  }

  private record Processamento(
      int novos,
      UUID entidadeId,
      String statusStage,
      List<CodigoPendenciaImportacao> pendencias) {
    private static Processamento vazio() {
      return new Processamento(0, null, "PROCESSADO", List.of());
    }

    private static Processamento quarentena(CodigoPendenciaImportacao codigo) {
      return new Processamento(0, null, "PENDENTE_REVISAO", List.of(codigo));
    }

    private static Processamento descartado() {
      return new Processamento(0, null, "REJEITADO", List.of());
    }
  }

  private record SeoPersistido(UUID id, int novos) {
  }

  private static final class ContadoresChamada {
    private long processados;
    private long novos;
  }
}
