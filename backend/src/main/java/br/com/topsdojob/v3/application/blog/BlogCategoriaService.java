package br.com.topsdojob.v3.application.blog;

import br.com.topsdojob.v3.application.blog.dto.BlogCategoriaDto;
import br.com.topsdojob.v3.application.blog.dto.BlogCategoriaRequest;
import br.com.topsdojob.v3.persistence.entity.blog.BlogCategoriaEntity;
import br.com.topsdojob.v3.persistence.repository.blog.BlogCategoriaRepository;
import br.com.topsdojob.v3.persistence.repository.blog.BlogPostRepository;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlogCategoriaService {

  private final BlogCategoriaRepository repository;
  private final BlogPostRepository postRepository;
  private final BlogAuditoriaService auditoria;

  public BlogCategoriaService(
      BlogCategoriaRepository repository,
      BlogPostRepository postRepository,
      BlogAuditoriaService auditoria) {
    this.repository = repository;
    this.postRepository = postRepository;
    this.auditoria = auditoria;
  }

  @Transactional(readOnly = true)
  public List<BlogCategoriaDto> listarAdmin() {
    return repository.findAllByOrderByOrdemAscNomeAsc().stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public List<BlogCategoriaDto> listarPublicas() {
    return repository.findAllByAtivaTrueOrderByOrdemAscNomeAsc().stream().map(this::toDto).toList();
  }

  @Transactional
  public BlogCategoriaDto criar(
      BlogCategoriaRequest request, UUID atorId, String requestId) {
    exigirAtor(atorId);
    Dados dados = validar(request);
    validarUnicidade(dados.nome(), dados.slug(), UUID.randomUUID());
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    BlogCategoriaEntity entity = repository.saveAndFlush(BlogCategoriaEntity.criar(
        UUID.randomUUID(), dados.nome(), dados.slug(), dados.ordem(), dados.ativa(), agora));
    auditoria.registrar(
        atorId, "BLOG_CATEGORIA_CRIADA", "BLOG_CATEGORIA", entity.getId(),
        requestId, agora, null, snapshot(entity));
    return toDto(entity);
  }

  @Transactional
  public BlogCategoriaDto atualizar(
      UUID id, BlogCategoriaRequest request, UUID atorId, String requestId) {
    exigirAtor(atorId);
    BlogCategoriaEntity entity = repository.findById(id)
        .orElseThrow(() -> notFound("categoria do Blog nao encontrada"));
    if (request == null || request.versao() == null || request.versao() != entity.getVersao()) {
      throw conflito("categoria do Blog foi alterada por outra sessao");
    }
    Dados dados = validar(request);
    validarUnicidade(dados.nome(), dados.slug(), id);
    var antes = snapshot(entity);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    entity.atualizar(dados.nome(), dados.slug(), dados.ordem(), dados.ativa(), agora);
    BlogCategoriaEntity salva = repository.saveAndFlush(entity);
    auditoria.registrar(
        atorId, "BLOG_CATEGORIA_ATUALIZADA", "BLOG_CATEGORIA", id,
        requestId, agora, antes, snapshot(salva));
    return toDto(salva);
  }

  @Transactional
  public BlogCategoriaDto desativar(UUID id, UUID atorId, String requestId) {
    exigirAtor(atorId);
    BlogCategoriaEntity entity = repository.findById(id)
        .orElseThrow(() -> notFound("categoria do Blog nao encontrada"));
    if (!Boolean.TRUE.equals(entity.getAtiva())) {
      return toDto(entity);
    }
    var antes = snapshot(entity);
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    entity.atualizar(entity.getNome(), entity.getSlug(), entity.getOrdem(), false, agora);
    BlogCategoriaEntity salva = repository.saveAndFlush(entity);
    auditoria.registrar(
        atorId, "BLOG_CATEGORIA_DESATIVADA", "BLOG_CATEGORIA", id,
        requestId, agora, antes, snapshot(salva));
    return toDto(salva);
  }

  private Dados validar(BlogCategoriaRequest request) {
    if (request == null) {
      throw badRequest("dados da categoria obrigatorios");
    }
    String nome = texto(request.nome(), 2, 120, "nome");
    String slug = request.slug() == null || request.slug().isBlank()
        ? slugify(nome)
        : slugify(request.slug());
    if (request.ordem() == null || request.ordem() < 0 || request.ativa() == null) {
      throw badRequest("ordem e estado da categoria sao obrigatorios");
    }
    return new Dados(nome, slug, request.ordem(), request.ativa());
  }

  private void validarUnicidade(String nome, String slug, UUID id) {
    if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)
        || repository.existsBySlugAndIdNot(slug, id)) {
      throw conflito("nome ou slug da categoria ja utilizado");
    }
  }

  private BlogCategoriaDto toDto(BlogCategoriaEntity entity) {
    return new BlogCategoriaDto(
        entity.getId(),
        entity.getNome(),
        entity.getSlug(),
        entity.getOrdem(),
        Boolean.TRUE.equals(entity.getAtiva()),
        postRepository.countByCategoriaIdAndStatus(entity.getId(), "PUBLICADO"),
        entity.getVersao(),
        entity.getAtualizadoEm());
  }

  private java.util.Map<String, Object> snapshot(BlogCategoriaEntity entity) {
    return java.util.Map.of(
        "slug", entity.getSlug(),
        "ativa", entity.getAtiva(),
        "ordem", entity.getOrdem());
  }

  private String slugify(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
    if (normalized.isBlank() || normalized.length() > 120) {
      throw badRequest("slug da categoria invalido");
    }
    return normalized;
  }

  private String texto(String value, int min, int max, String field) {
    String normalized = value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    if (normalized.length() < min || normalized.length() > max) {
      throw badRequest(field + " fora do tamanho permitido");
    }
    return normalized;
  }

  private void exigirAtor(UUID atorId) {
    if (atorId == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
    }
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

  private record Dados(String nome, String slug, int ordem, boolean ativa) {
  }
}
