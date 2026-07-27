package br.com.topsdojob.v3.application.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.blog.dto.BlogPostRequest;
import br.com.topsdojob.v3.persistence.entity.blog.BlogCategoriaEntity;
import br.com.topsdojob.v3.persistence.entity.blog.BlogPostEntity;
import br.com.topsdojob.v3.persistence.repository.blog.BlogCategoriaRepository;
import br.com.topsdojob.v3.persistence.repository.blog.BlogImagemRepository;
import br.com.topsdojob.v3.persistence.repository.blog.BlogPostRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlogPostServiceTest {

  private final BlogPostRepository repository = mock(BlogPostRepository.class);
  private final BlogCategoriaRepository categoriaRepository = mock(BlogCategoriaRepository.class);
  private final BlogImagemRepository imagemRepository = mock(BlogImagemRepository.class);
  private final BlogImagemService imagemService = mock(BlogImagemService.class);
  private final BlogAuditoriaService auditoria = mock(BlogAuditoriaService.class);
  private final BlogConteudoValidator validator = new BlogConteudoValidator();
  private final UUID ator = UUID.randomUUID();
  private final UUID categoriaId = UUID.randomUUID();
  private BlogCategoriaEntity categoria;
  private BlogPostService service;

  @BeforeEach
  void setUp() {
    categoria = BlogCategoriaEntity.criar(
        categoriaId, "Guias", "guias", 0, true, OffsetDateTime.now(ZoneOffset.UTC));
    service = new BlogPostService(
        repository,
        categoriaRepository,
        imagemRepository,
        imagemService,
        validator,
        auditoria);
    when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoria));
    when(repository.saveAndFlush(any(BlogPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(imagemService.validarVinculo(any(), any(), any(), any())).thenReturn(null);
  }

  @Test
  void criaRascunhoIdempotentePorRequestId() {
    when(repository.findByCriadoPorUsuarioIdAndCriadoRequestId(ator, "request-blog-0001"))
        .thenReturn(Optional.empty());

    var created = service.criar(request(), ator, "request-blog-0001");

    assertThat(created.status()).isEqualTo("RASCUNHO");
    assertThat(created.publishedAt()).isNull();
    verify(repository).saveAndFlush(any(BlogPostEntity.class));
  }

  @Test
  void publicacaoConsolidaEstadoEDatasSemCriarOutroRegistro() {
    BlogPostEntity entity = BlogPostEntity.criarRascunho(
        UUID.randomUUID(), ator, "request-blog-0002", OffsetDateTime.now(ZoneOffset.UTC));
    entity.atualizar(
        categoriaId,
        "Homologacao Blog V3",
        "homologacao-blog-v3",
        "Resumo editorial completo para publicacao segura.",
        "<h2>Conteudo de homologacao</h2><p>Texto editorial seguro e completo.</p>",
        "Equipe Tops do Job",
        "Homologacao Blog V3",
        "Descricao editorial completa para mecanismos de busca.",
        new BigDecimal("0.7"),
        "weekly",
        null,
        null,
        ator,
        OffsetDateTime.now(ZoneOffset.UTC));
    when(repository.findByIdForUpdate(entity.getId())).thenReturn(Optional.of(entity));

    var published = service.publicar(entity.getId(), ator, "request-blog-0003");

    assertThat(published.status()).isEqualTo("PUBLICADO");
    assertThat(published.publishedAt()).isNotNull();
    verify(repository).saveAndFlush(entity);
  }

  @Test
  void contratosPublicosConsultamSomenteStatusPublicado() {
    when(repository.findAllByStatusOrderByPublicadoEmDescIdAsc("PUBLICADO"))
        .thenReturn(List.of());

    assertThat(service.listarPublicados()).isEmpty();

    verify(repository).findAllByStatusOrderByPublicadoEmDescIdAsc("PUBLICADO");
  }

  private BlogPostRequest request() {
    return new BlogPostRequest(
        categoriaId,
        "Homologacao Blog V3",
        "homologacao-blog-v3",
        "Resumo editorial completo para publicacao segura.",
        "<h2>Conteudo de homologacao</h2><p>Texto editorial seguro e completo.</p>",
        "Equipe Tops do Job",
        "Homologacao Blog V3",
        "Descricao editorial completa para mecanismos de busca.",
        new BigDecimal("0.7"),
        "weekly",
        null,
        null,
        null);
  }
}
