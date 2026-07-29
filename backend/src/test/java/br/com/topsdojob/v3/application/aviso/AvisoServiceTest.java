package br.com.topsdojob.v3.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.aviso.AvisoDtos.Edicao;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Versao;
import br.com.topsdojob.v3.persistence.entity.aviso.AvisoEntity;
import br.com.topsdojob.v3.persistence.repository.aviso.AvisoRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class AvisoServiceTest {

  private AvisoRepository repository;
  private AvisoAuditoriaService auditoria;
  private AvisoService service;

  @BeforeEach
  void setUp() {
    repository = mock(AvisoRepository.class);
    auditoria = mock(AvisoAuditoriaService.class);
    service = new AvisoService(repository, auditoria);
    when(repository.saveAndFlush(any(AvisoEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void criaRascunhoIdempotenteSanitizadoEAuditado() {
    UUID ator = UUID.randomUUID();
    var criado = service.criar(
        edicao(null, null, null),
        ator,
        "Operador QA",
        "idempotency-aviso-create",
        "request-aviso-create");

    assertThat(criado.status()).isEqualTo("RASCUNHO");
    assertThat(criado.localExibicao()).isEqualTo("SITE");
    verify(auditoria).registrar(
        eq(ator), eq("AVISO_CRIADO"), any(AvisoEntity.class),
        eq("request-aviso-create"), any(OffsetDateTime.class), eq(null));

    AvisoEntity existente = aviso("RASCUNHO", null, null);
    when(repository.findByCriadoPorUsuarioIdAndCriadoRequestId(
        ator, "request-aviso-retry"))
        .thenReturn(Optional.of(existente));

    var retry = service.criar(
        edicaoRodape(null),
        ator,
        "Operador QA",
        "request-aviso-retry",
        "request-aviso-retry");
    assertThat(retry.id()).isEqualTo(existente.getId());
    verify(auditoria, times(1)).registrar(
        eq(ator), eq("AVISO_CRIADO"), any(AvisoEntity.class),
        eq("request-aviso-create"), any(OffsetDateTime.class), eq(null));

    assertThatThrownBy(() -> service.criar(
        new Edicao(
            "Outro aviso QA",
            "Mensagem segura para homologacao.",
            "ANUNCIO_RODAPE",
            "DIARIO",
            true,
            null,
            null,
            null),
        ator,
        "Operador QA",
        "request-aviso-retry",
        "request-aviso-divergent"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("idempotencia reutilizada");
  }

  @Test
  void publicoUsaLocalEJanelaNoServidorSemFallbackFrontend() {
    AvisoEntity vigente = aviso(
        "PUBLICADO",
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
        OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
    when(repository.buscarVigentes(
        eq("ANUNCIO_RODAPE"), any(OffsetDateTime.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(vigente)));

    var pagina = service.listarPublicos("ANUNCIO_RODAPE", 0, 5);

    assertThat(pagina.itens()).singleElement().satisfies(item -> {
      assertThat(item.localExibicao()).isEqualTo("ANUNCIO_RODAPE");
      assertThat(item.titulo()).isEqualTo("Aviso QA");
    });
  }

  @Test
  void identificaVigenteAgendadoEExpiradoEmAmericaSaoPauloPeloInstante() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    var itens = List.of(
        aviso("PUBLICADO", now.minusHours(1), now.plusHours(1)),
        aviso("PUBLICADO", now.plusHours(1), now.plusHours(2)),
        aviso("PUBLICADO", now.minusHours(2), now.minusHours(1)));
    when(repository.buscarAdmin(
        eq(null), eq(null), eq(null), any(Pageable.class)))
        .thenReturn(new PageImpl<>(itens));

    assertThat(service.listarAdmin(null, null, null, 0, 10).itens())
        .extracting(item -> item.situacao())
        .containsExactly("VIGENTE", "AGENDADO", "EXPIRADO");
  }

  @Test
  void publicaRetiraEArquivaComLockVersaoEAuditoria() {
    UUID ator = UUID.randomUUID();
    AvisoEntity aviso = aviso("RASCUNHO", null, null);
    when(repository.findByIdForUpdate(aviso.getId())).thenReturn(Optional.of(aviso));

    assertThat(service.publicar(
        aviso.getId(), new Versao(0L), ator, "request-aviso-publish").status())
        .isEqualTo("PUBLICADO");
    assertThat(service.retirar(
        aviso.getId(), new Versao(0L), ator, "request-aviso-withdraw").status())
        .isEqualTo("RASCUNHO");
    assertThat(service.arquivar(
        aviso.getId(), new Versao(0L), ator, "request-aviso-archive").status())
        .isEqualTo("ARQUIVADO");
  }

  @Test
  void rejeitaHtmlScriptJanelaInvalidaEConflitoDeVersao() {
    UUID ator = UUID.randomUUID();
    assertThatThrownBy(() -> service.criar(
        new Edicao(
            "Aviso <script>",
            "Mensagem segura para QA.",
            "SITE",
            "SEMPRE",
            true,
            null,
            null,
            null),
        ator,
        "Operador QA",
        "idempotency-aviso-html",
        "request-aviso-html"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("titulo invalida");

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    assertThatThrownBy(() -> service.criar(
        edicao(now.plusHours(2), now.plusHours(1), null),
        ator,
        "Operador QA",
        "idempotency-aviso-window",
        "request-aviso-window"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("fim da vigencia");

    AvisoEntity aviso = aviso("RASCUNHO", null, null);
    when(repository.findByIdForUpdate(aviso.getId())).thenReturn(Optional.of(aviso));
    assertThatThrownBy(() -> service.atualizar(
        aviso.getId(),
        edicao(null, null, 9L),
        ator,
        "request-aviso-update"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("outra sessao");
  }

  private Edicao edicao(
      OffsetDateTime inicio, OffsetDateTime fim, Long versao) {
    return new Edicao(
        "Aviso QA",
        "Mensagem segura para homologacao.",
        "SITE",
        "DIARIO",
        true,
        inicio,
        fim,
        versao);
  }

  private Edicao edicaoRodape(Long versao) {
    return new Edicao(
        "Aviso QA",
        "Mensagem segura para homologacao.",
        "ANUNCIO_RODAPE",
        "DIARIO",
        true,
        null,
        null,
        versao);
  }

  private AvisoEntity aviso(
      String status, OffsetDateTime inicio, OffsetDateTime fim) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID ator = UUID.randomUUID();
    AvisoEntity aviso = AvisoEntity.criarRascunho(
        UUID.randomUUID(),
        "Aviso QA",
        "Mensagem segura para homologacao.",
        "ANUNCIO_RODAPE",
        "DIARIO",
        true,
        inicio,
        fim,
        ator,
        "Operador QA",
        "request-aviso-fixture",
        now);
    if ("PUBLICADO".equals(status)) {
      aviso.publicar(ator, now);
    } else if ("ARQUIVADO".equals(status)) {
      aviso.arquivar(ator, now);
    }
    return aviso;
  }
}
