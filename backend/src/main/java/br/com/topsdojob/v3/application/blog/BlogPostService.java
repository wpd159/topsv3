package br.com.topsdojob.v3.application.blog;

import br.com.topsdojob.v3.application.blog.dto.BlogPostDto;
import br.com.topsdojob.v3.application.blog.dto.BlogPostRequest;
import br.com.topsdojob.v3.application.blog.dto.BlogSitemapDto;
import br.com.topsdojob.v3.persistence.entity.blog.BlogCategoriaEntity;
import br.com.topsdojob.v3.persistence.entity.blog.BlogImagemEntity;
import br.com.topsdojob.v3.persistence.entity.blog.BlogPostEntity;
import br.com.topsdojob.v3.persistence.repository.blog.BlogCategoriaRepository;
import br.com.topsdojob.v3.persistence.repository.blog.BlogImagemRepository;
import br.com.topsdojob.v3.persistence.repository.blog.BlogPostRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlogPostService {

  private static final Set<String> STATUS = Set.of("RASCUNHO", "PUBLICADO", "ARQUIVADO");
  private static final Set<String> FREQUENCIAS = Set.of("daily", "weekly", "monthly");

  private final BlogPostRepository repository;
  private final BlogCategoriaRepository categoriaRepository;
  private final BlogImagemRepository imagemRepository;
  private final BlogImagemService imagemService;
  private final BlogConteudoValidator conteudoValidator;
  private final BlogAuditoriaService auditoria;

  public BlogPostService(
      BlogPostRepository repository,
      BlogCategoriaRepository categoriaRepository,
      BlogImagemRepository imagemRepository,
      BlogImagemService imagemService,
      BlogConteudoValidator conteudoValidator,
      BlogAuditoriaService auditoria) {
    this.repository = repository;
    this.categoriaRepository = categoriaRepository;
    this.imagemRepository = imagemRepository;
    this.imagemService = imagemService;
    this.conteudoValidator = conteudoValidator;
    this.auditoria = auditoria;
  }

  @Transactional(readOnly = true)
  public List<BlogPostDto> listarAdmin(String termo, String status) {
    String statusSeguro = status == null || status.isBlank() || "TODOS".equalsIgnoreCase(status)
        ? null
        : status.trim().toUpperCase(Locale.ROOT);
    if (statusSeguro != null && !STATUS.contains(statusSeguro)) {
      throw badRequest("status editorial invalido");
    }
    String termoSeguro = termo == null || termo.isBlank() ? null : termo.trim();
    List<BlogPostEntity> posts;
    if (termoSeguro != null) {
      posts = repository.buscarAdmin(termoSeguro, statusSeguro);
    } else if (statusSeguro != null) {
      posts = repository.findAllByStatusOrderByAtualizadoEmDescIdAsc(statusSeguro);
    } else {
      posts = repository.findAllByOrderByAtualizadoEmDescIdAsc();
    }
    return toDtos(posts, false);
  }

  @Transactional(readOnly = true)
  public BlogPostDto buscarAdmin(UUID id) {
    return toDto(repository.findById(id)
        .orElseThrow(() -> notFound("post do Blog nao encontrado")), false);
  }

  @Transactional(readOnly = true)
  public List<BlogPostDto> listarPublicados() {
    return toDtos(repository.findAllByStatusOrderByPublicadoEmDescIdAsc("PUBLICADO"), true);
  }

  @Transactional(readOnly = true)
  public BlogPostDto buscarPublicado(String slug) {
    return toDto(repository.findBySlugAndStatus(slugSeguro(slug), "PUBLICADO")
        .orElseThrow(() -> notFound("post do Blog nao encontrado")), true);
  }

  @Transactional(readOnly = true)
  public List<BlogPostDto> listarPublicadosPorCategoria(String categoriaSlug) {
    BlogCategoriaEntity categoria = categoriaRepository.findBySlug(slugSeguro(categoriaSlug))
        .filter(item -> Boolean.TRUE.equals(item.getAtiva()))
        .orElseThrow(() -> notFound("categoria do Blog nao encontrada"));
    return toDtos(repository.findAllByStatusAndCategoriaIdOrderByPublicadoEmDescIdAsc(
        "PUBLICADO", categoria.getId()), true);
  }

  @Transactional(readOnly = true)
  public List<BlogSitemapDto> sitemap() {
    return repository.findAllByStatusOrderByPublicadoEmDescIdAsc("PUBLICADO").stream()
        .map(post -> new BlogSitemapDto(
            post.getSlug(),
            post.getPublicadoEm(),
            post.getAtualizadoEm(),
            post.getSitemapPriority(),
            post.getChangeFrequency()))
        .toList();
  }

  @Transactional
  public BlogPostDto criar(
      BlogPostRequest request, UUID atorId, String requestId) {
    exigirAtor(atorId);
    String requestIdSeguro = requestId(requestId);
    var existente = repository.findByCriadoPorUsuarioIdAndCriadoRequestId(atorId, requestIdSeguro);
    if (existente.isPresent()) {
      return toDto(existente.get(), false);
    }
    Dados dados = validarRascunho(request);
    validarSlugUnico(dados.slug(), UUID.randomUUID());
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    UUID id = UUID.randomUUID();
    BlogPostEntity entity = BlogPostEntity.criarRascunho(id, atorId, requestIdSeguro, agora);
    atualizarEntity(entity, dados, atorId, agora);
    BlogPostEntity salva = repository.saveAndFlush(entity);
    auditoria.registrar(
        atorId,
        "BLOG_POST_CRIADO",
        "BLOG_POST",
        id,
        requestIdSeguro,
        agora,
        null,
        auditoria.postSnapshot(salva.getSlug(), salva.getStatus(), salva.getVersao()));
    return toDto(salva, false);
  }

  @Transactional
  public BlogPostDto atualizar(
      UUID id, BlogPostRequest request, UUID atorId, String requestId) {
    exigirAtor(atorId);
    BlogPostEntity entity = repository.findByIdForUpdate(id)
        .orElseThrow(() -> notFound("post do Blog nao encontrado"));
    validarVersao(request, entity);
    Dados dados = validarRascunho(request);
    validarSlugUnico(dados.slug(), id);
    var antes = auditoria.postSnapshot(entity.getSlug(), entity.getStatus(), entity.getVersao());
    UUID capaAnterior = entity.getImagemCapaId();
    UUID ogAnterior = entity.getImagemOgId();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    atualizarEntity(entity, dados, atorId, agora);
    if ("PUBLICADO".equals(entity.getStatus())) {
      validarPublicacao(entity, dados.categoria());
      promoverImagens(entity, agora);
      retirarImagemSubstituida(capaAnterior, dados.imagemCapaId(), agora);
      retirarImagemSubstituida(ogAnterior, dados.imagemOgId(), agora);
    }
    BlogPostEntity salva = repository.saveAndFlush(entity);
    auditoria.registrar(
        atorId,
        "BLOG_POST_ATUALIZADO",
        "BLOG_POST",
        id,
        requestId(requestId),
        agora,
        antes,
        auditoria.postSnapshot(salva.getSlug(), salva.getStatus(), salva.getVersao()));
    return toDto(salva, false);
  }

  @Transactional
  public BlogPostDto publicar(UUID id, UUID atorId, String requestId) {
    exigirAtor(atorId);
    BlogPostEntity entity = repository.findByIdForUpdate(id)
        .orElseThrow(() -> notFound("post do Blog nao encontrado"));
    if ("PUBLICADO".equals(entity.getStatus())) {
      return toDto(entity, false);
    }
    if ("ARQUIVADO".equals(entity.getStatus())) {
      throw conflito("post arquivado deve voltar a rascunho antes da publicacao");
    }
    BlogCategoriaEntity categoria = categoriaRepository.findById(entity.getCategoriaId())
        .orElseThrow(() -> conflito("categoria editorial inexistente"));
    validarPublicacao(entity, categoria);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    BlogImagemEntity capa = imagemService.buscar(entity.getImagemCapaId());
    BlogImagemEntity og = imagemService.buscar(entity.getImagemOgId());
    imagemService.promover(capa, agora);
    imagemService.promover(og, agora);
    var antes = auditoria.postSnapshot(entity.getSlug(), entity.getStatus(), entity.getVersao());
    entity.publicar(agora, atorId);
    BlogPostEntity salva = repository.saveAndFlush(entity);
    auditoria.registrar(
        atorId,
        "BLOG_POST_PUBLICADO",
        "BLOG_POST",
        id,
        requestId(requestId),
        agora,
        antes,
        auditoria.postSnapshot(salva.getSlug(), salva.getStatus(), salva.getVersao()));
    return toDto(salva, false);
  }

  @Transactional
  public BlogPostDto retirar(UUID id, UUID atorId, String requestId) {
    exigirAtor(atorId);
    BlogPostEntity entity = repository.findByIdForUpdate(id)
        .orElseThrow(() -> notFound("post do Blog nao encontrado"));
    if ("RASCUNHO".equals(entity.getStatus())) {
      return toDto(entity, false);
    }
    if (!"PUBLICADO".equals(entity.getStatus())) {
      throw conflito("somente post publicado pode ser retirado");
    }
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    var antes = auditoria.postSnapshot(entity.getSlug(), entity.getStatus(), entity.getVersao());
    imagemService.retirarDoPublico(imagemService.buscar(entity.getImagemCapaId()), agora);
    imagemService.retirarDoPublico(imagemService.buscar(entity.getImagemOgId()), agora);
    entity.retirar(agora, atorId);
    BlogPostEntity salva = repository.saveAndFlush(entity);
    auditoria.registrar(
        atorId,
        "BLOG_POST_RETIRADO",
        "BLOG_POST",
        id,
        requestId(requestId),
        agora,
        antes,
        auditoria.postSnapshot(salva.getSlug(), salva.getStatus(), salva.getVersao()));
    return toDto(salva, false);
  }

  @Transactional
  public BlogPostDto arquivar(UUID id, UUID atorId, String requestId) {
    exigirAtor(atorId);
    BlogPostEntity entity = repository.findByIdForUpdate(id)
        .orElseThrow(() -> notFound("post do Blog nao encontrado"));
    if ("ARQUIVADO".equals(entity.getStatus())) {
      return toDto(entity, false);
    }
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    var antes = auditoria.postSnapshot(entity.getSlug(), entity.getStatus(), entity.getVersao());
    imagemService.retirarDoPublico(imagemService.buscar(entity.getImagemCapaId()), agora);
    imagemService.retirarDoPublico(imagemService.buscar(entity.getImagemOgId()), agora);
    entity.arquivar(agora, atorId);
    BlogPostEntity salva = repository.saveAndFlush(entity);
    auditoria.registrar(
        atorId,
        "BLOG_POST_ARQUIVADO",
        "BLOG_POST",
        id,
        requestId(requestId),
        agora,
        antes,
        auditoria.postSnapshot(salva.getSlug(), salva.getStatus(), salva.getVersao()));
    return toDto(salva, false);
  }

  private Dados validarRascunho(BlogPostRequest request) {
    if (request == null) {
      throw badRequest("dados do post obrigatorios");
    }
    if (request.categoriaId() == null) {
      throw badRequest("categoria editorial obrigatoria");
    }
    BlogCategoriaEntity categoria = categoriaRepository.findById(request.categoriaId())
        .orElseThrow(() -> badRequest("categoria editorial inexistente"));
    String titulo = texto(request.titulo(), 3, 180, "titulo");
    String slug = slugSeguro(request.slug() == null || request.slug().isBlank()
        ? slugify(titulo)
        : request.slug());
    String resumo = textoOpcional(request.resumo(), 320, "resumo");
    String conteudo = conteudoValidator.validarRascunho(request.conteudo());
    String autor = texto(request.autorNome(), 2, 120, "autor");
    String seoTitle = textoOpcional(request.seoTitle(), 180, "SEO title");
    String seoDescription = textoOpcional(request.seoDescription(), 320, "SEO description");
    BigDecimal priority = request.sitemapPriority() == null
        ? new BigDecimal("0.7")
        : request.sitemapPriority().setScale(1, RoundingMode.HALF_UP);
    if (priority.compareTo(new BigDecimal("0.1")) < 0
        || priority.compareTo(BigDecimal.ONE) > 0) {
      throw badRequest("prioridade do sitemap invalida");
    }
    String frequency = request.changeFrequency() == null
        ? "weekly"
        : request.changeFrequency().trim().toLowerCase(Locale.ROOT);
    if (!FREQUENCIAS.contains(frequency)) {
      throw badRequest("frequencia de atualizacao invalida");
    }
    return new Dados(
        categoria, titulo, slug, resumo, conteudo, autor, seoTitle, seoDescription,
        priority, frequency, request.imagemCapaId(), request.imagemOgId());
  }

  private void atualizarEntity(
      BlogPostEntity entity, Dados dados, UUID atorId, OffsetDateTime agora) {
    BlogImagemEntity capa = imagemService.validarVinculo(
        dados.imagemCapaId(), "CAPA", atorId, entity.getImagemCapaId());
    BlogImagemEntity og = imagemService.validarVinculo(
        dados.imagemOgId(), "OG", atorId, entity.getImagemOgId());
    entity.atualizar(
        dados.categoria().getId(),
        dados.titulo(),
        dados.slug(),
        dados.resumo(),
        dados.conteudo(),
        dados.autorNome(),
        dados.seoTitle(),
        dados.seoDescription(),
        dados.sitemapPriority(),
        dados.changeFrequency(),
        capa == null ? null : capa.getId(),
        og == null ? null : og.getId(),
        atorId,
        agora);
  }

  private void validarPublicacao(BlogPostEntity entity, BlogCategoriaEntity categoria) {
    if (!Boolean.TRUE.equals(categoria.getAtiva())) {
      throw conflito("categoria editorial inativa");
    }
    texto(entity.getResumo(), 20, 320, "resumo");
    conteudoValidator.validar(entity.getConteudo());
    texto(entity.getSeoTitle(), 3, 180, "SEO title");
    texto(entity.getSeoDescription(), 20, 320, "SEO description");
  }

  private void promoverImagens(BlogPostEntity entity, OffsetDateTime agora) {
    imagemService.promover(imagemService.buscar(entity.getImagemCapaId()), agora);
    imagemService.promover(imagemService.buscar(entity.getImagemOgId()), agora);
  }

  private void retirarImagemSubstituida(
      UUID imagemAnteriorId, UUID imagemNovaId, OffsetDateTime agora) {
    if (imagemAnteriorId != null && !Objects.equals(imagemAnteriorId, imagemNovaId)) {
      imagemService.retirarDoPublico(imagemService.buscar(imagemAnteriorId), agora);
    }
  }

  private List<BlogPostDto> toDtos(List<BlogPostEntity> posts, boolean publico) {
    if (posts.isEmpty()) {
      return List.of();
    }
    Map<UUID, BlogCategoriaEntity> categorias = categoriaRepository.findAllById(
            posts.stream().map(BlogPostEntity::getCategoriaId).collect(Collectors.toSet()))
        .stream()
        .collect(Collectors.toMap(BlogCategoriaEntity::getId, Function.identity()));
    Set<UUID> imageIds = posts.stream()
        .flatMap(post -> java.util.stream.Stream.of(post.getImagemCapaId(), post.getImagemOgId()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<UUID, BlogImagemEntity> imagens = imagemRepository.findAllById(imageIds).stream()
        .collect(Collectors.toMap(BlogImagemEntity::getId, Function.identity()));
    return posts.stream()
        .map(post -> toDto(post, categorias.get(post.getCategoriaId()), imagens, publico))
        .toList();
  }

  private BlogPostDto toDto(BlogPostEntity post, boolean publico) {
    BlogCategoriaEntity categoria = categoriaRepository.findById(post.getCategoriaId())
        .orElseThrow(() -> new IllegalStateException("post sem categoria editorial"));
    Map<UUID, BlogImagemEntity> imagens = new HashMap<>();
    if (post.getImagemCapaId() != null) {
      imagens.put(post.getImagemCapaId(), imagemService.buscar(post.getImagemCapaId()));
    }
    if (post.getImagemOgId() != null) {
      imagens.put(post.getImagemOgId(), imagemService.buscar(post.getImagemOgId()));
    }
    return toDto(post, categoria, imagens, publico);
  }

  private BlogPostDto toDto(
      BlogPostEntity post,
      BlogCategoriaEntity categoria,
      Map<UUID, BlogImagemEntity> imagens,
      boolean publico) {
    BlogImagemEntity capa = imagens.get(post.getImagemCapaId());
    BlogImagemEntity og = imagens.get(post.getImagemOgId());
    String capaUrl = publico ? imagemService.urlPublica(capa) : imagemService.urlAdmin(capa);
    String ogUrl = publico ? imagemService.urlPublica(og) : imagemService.urlAdmin(og);
    return new BlogPostDto(
        post.getId(),
        post.getTitulo(),
        post.getSlug(),
        post.getResumo(),
        post.getConteudo(),
        categoria.getNome(),
        categoria.getId(),
        categoria.getSlug(),
        capaUrl,
        post.getImagemCapaId(),
        post.getAutorNome(),
        post.getStatus(),
        post.getSeoTitle(),
        post.getSeoDescription(),
        ogUrl,
        post.getImagemOgId(),
        post.getSitemapPriority(),
        post.getChangeFrequency(),
        post.getPublicadoEm(),
        post.getCriadoEm(),
        post.getAtualizadoEm(),
        post.getVersao());
  }

  private void validarVersao(BlogPostRequest request, BlogPostEntity entity) {
    if (request == null || request.versao() == null || request.versao() != entity.getVersao()) {
      throw conflito("post do Blog foi alterado por outra sessao");
    }
  }

  private void validarSlugUnico(String slug, UUID id) {
    if (repository.existsBySlugAndIdNot(slug, id)) {
      throw conflito("slug do Blog ja utilizado");
    }
  }

  private String slugify(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
  }

  private String slugSeguro(String value) {
    String slug = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (slug.length() < 1
        || slug.length() > 180
        || !slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
      throw badRequest("slug do Blog invalido");
    }
    return slug;
  }

  private String texto(String value, int min, int max, String field) {
    String normalized = textoOpcional(value, max, field);
    if (normalized.length() < min) {
      throw badRequest(field + " fora do tamanho permitido");
    }
    return normalized;
  }

  private String textoOpcional(String value, int max, String field) {
    String normalized = value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    if (normalized.length() > max || normalized.contains("<") || normalized.contains(">")) {
      throw badRequest(field + " invalido");
    }
    return normalized;
  }

  private void exigirAtor(UUID atorId) {
    if (atorId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
    }
  }

  private String requestId(String requestId) {
    String value = requestId == null ? "" : requestId.trim();
    if (value.length() < 8 || value.length() > 128 || !value.matches("^[A-Za-z0-9._:-]+$")) {
      throw badRequest("requestId administrativo invalido");
    }
    return value;
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException conflito(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private record Dados(
      BlogCategoriaEntity categoria,
      String titulo,
      String slug,
      String resumo,
      String conteudo,
      String autorNome,
      String seoTitle,
      String seoDescription,
      BigDecimal sitemapPriority,
      String changeFrequency,
      UUID imagemCapaId,
      UUID imagemOgId) {
  }
}
