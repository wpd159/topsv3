package br.com.topsdojob.v3.application.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

  @Test
  void detalhePublicoRespeitaRascunhoRetiradaESlugAtualSemTruncarConteudo() {
    String conteudo = "<h2>Inicio sintetico</h2>\n"
        + "<p>Paragrafo editorial completo, com acentuação.</p>\n".repeat(1000)
        + "<p>Marcador final sintetico.</p>";
    var criado = service.criar(request("slug-original", conteudo, null), ator, "request-blog-ciclo");
    var captor = ArgumentCaptor.forClass(BlogPostEntity.class);
    verify(repository).saveAndFlush(captor.capture());
    BlogPostEntity entity = captor.getValue();
    assertThat(entity.getConteudo()).isEqualTo(conteudo);
    assertThat(criado.conteudo()).isEqualTo(conteudo);

    // Fronteira do repository em memoria; nao substitui uma prova PostgreSQL.
    when(repository.findByIdForUpdate(entity.getId())).thenReturn(Optional.of(entity));
    when(repository.findBySlugAndStatus(anyString(), anyString())).thenAnswer(invocation ->
        entity.getSlug().equals(invocation.getArgument(0))
                && entity.getStatus().equals(invocation.getArgument(1))
            ? Optional.of(entity)
            : Optional.empty());

    assertNaoPublicado("slug-original");
    assertNaoPublicado("inexistente");
    service.publicar(entity.getId(), ator, "request-blog-publicar");
    assertThat(service.buscarPublicado("slug-original").conteudo()).isEqualTo(conteudo);

    service.retirar(entity.getId(), ator, "request-blog-retirar");
    assertThat(entity.getStatus()).isEqualTo("RASCUNHO");
    assertNaoPublicado("slug-original");

    service.publicar(entity.getId(), ator, "request-blog-republicar");
    service.atualizar(entity.getId(), request("slug-alterado", conteudo, entity.getVersao()),
        ator, "request-blog-alterar-slug");
    assertNaoPublicado("slug-original");
    assertThat(service.buscarPublicado("slug-alterado").conteudo()).isEqualTo(conteudo);
  }

  @Test
  void listagemAdminSemTermoNaoEnviaParametroNuloParaLower() {
    when(repository.findAllByOrderByAtualizadoEmDescIdAsc()).thenReturn(List.of());

    assertThat(service.listarAdmin(null, null)).isEmpty();

    verify(repository).findAllByOrderByAtualizadoEmDescIdAsc();
    verify(repository, never()).buscarAdmin(any(), any());
  }

  @Test
  void listagemAdminSemTermoFiltraStatusPorMetodoTipado() {
    when(repository.findAllByStatusOrderByAtualizadoEmDescIdAsc("RASCUNHO"))
        .thenReturn(List.of());

    assertThat(service.listarAdmin("", "rascunho")).isEmpty();

    verify(repository).findAllByStatusOrderByAtualizadoEmDescIdAsc("RASCUNHO");
    verify(repository, never()).buscarAdmin(any(), any());
  }

  @Test
  void listagemAdminComTermoUsaConsultaTextual() {
    when(repository.buscarAdmin("homologacao", null)).thenReturn(List.of());

    assertThat(service.listarAdmin(" homologacao ", "TODOS")).isEmpty();

    verify(repository).buscarAdmin("homologacao", null);
  }

  private BlogPostRequest request() {
    return request("homologacao-blog-v3",
        "<h2>Conteudo de homologacao</h2><p>Texto editorial seguro e completo.</p>", null);
  }

  private BlogPostRequest request(String slug, String conteudo, Long versao) {
    return new BlogPostRequest(
        categoriaId,
        "Homologacao Blog V3",
        slug,
        "Resumo editorial completo para publicacao segura.",
        conteudo,
        "Equipe Tops do Job",
        "Homologacao Blog V3",
        "Descricao editorial completa para mecanismos de busca.",
        new BigDecimal("0.7"),
        "weekly",
        null,
        null,
        versao);
  }

  private void assertNaoPublicado(String slug) {
    assertThatThrownBy(() -> service.buscarPublicado(slug))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
