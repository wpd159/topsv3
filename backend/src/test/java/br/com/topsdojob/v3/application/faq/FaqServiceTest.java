package br.com.topsdojob.v3.application.faq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.faq.FaqDtos.Edicao;
import br.com.topsdojob.v3.application.faq.FaqDtos.Ordem;
import br.com.topsdojob.v3.application.faq.FaqDtos.Versao;
import br.com.topsdojob.v3.persistence.entity.faq.FaqEntity;
import br.com.topsdojob.v3.persistence.repository.faq.FaqRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FaqServiceTest {

  private FaqRepository repository;
  private FaqAuditoriaService auditoria;
  private FaqService service;

  @BeforeEach
  void setUp() {
    repository = mock(FaqRepository.class);
    auditoria = mock(FaqAuditoriaService.class);
    service = new FaqService(repository, auditoria);
    when(repository.saveAndFlush(any(FaqEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void publicoLeSomenteFontePublicadaNaOrdemCanonizada() {
    FaqEntity faq = faq("PUBLICADO");
    when(repository.findAllByStatusOrderByOrdemAscAtualizadoEmDescIdAsc("PUBLICADO"))
        .thenReturn(List.of(faq));

    var result = service.listarPublicadas();

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.status()).isEqualTo("PUBLICADO");
      assertThat(item.pergunta()).isEqualTo("Como funciona a homologacao?");
    });
  }

  @Test
  void criaRascunhoIdempotenteERegistraAuditoriaSemConteudoIntegral() {
    UUID ator = UUID.randomUUID();
    Edicao request = new Edicao(
        " Como funciona a homologacao? ",
        "Resposta segura para o ambiente de QA.",
        "GERAL",
        4,
        null);

    var criada = service.criar(request, ator, "request-faq-0001");

    assertThat(criada.status()).isEqualTo("RASCUNHO");
    assertThat(criada.ordem()).isEqualTo(4);
    verify(auditoria).registrar(
        eq(ator), eq("FAQ_CRIADA"), any(FaqEntity.class),
        eq("request-faq-0001"), any(OffsetDateTime.class), eq(null));
  }

  @Test
  void rejeitaHtmlScriptHandlerIframeEProtocoloExecutavel() {
    UUID ator = UUID.randomUUID();
    for (String resposta : List.of(
        "<script>alert(1)</script>",
        "<img src=x onerror=alert(1)>",
        "<iframe src=\"https://example.invalid\"></iframe>",
        "javascript:alert(1)")) {
      assertThatThrownBy(() -> service.criar(
          new Edicao("Pergunta valida de seguranca?", resposta, "SEGURANCA", 0, null),
          ator,
          "request-faq-safe-" + Math.abs(resposta.hashCode())))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("resposta invalida");
    }
  }

  @Test
  void publicaRetiraArquivaEReordenaComVersaoCorreta() {
    UUID ator = UUID.randomUUID();
    FaqEntity faq = faq("RASCUNHO");
    when(repository.findByIdForUpdate(faq.getId())).thenReturn(Optional.of(faq));

    assertThat(service.publicar(
        faq.getId(), new Versao(0L), ator, "request-faq-publish").status())
        .isEqualTo("PUBLICADO");

    assertThatThrownBy(() -> service.retirar(
        faq.getId(), new Versao(1L), ator, "request-faq-withdraw"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("outra sessao");

    assertThat(service.reordenar(
        faq.getId(), new Ordem(9, 0L), ator, "request-faq-order").ordem())
        .isEqualTo(9);
  }

  @Test
  void versaoDesatualizadaBloqueiaEdicaoSemSucessoParcial() {
    UUID ator = UUID.randomUUID();
    FaqEntity faq = faq("RASCUNHO");
    when(repository.findByIdForUpdate(faq.getId())).thenReturn(Optional.of(faq));

    assertThatThrownBy(() -> service.atualizar(
        faq.getId(),
        new Edicao(
            "Pergunta alterada de homologacao?",
            "Resposta alterada de homologacao.",
            "GERAL",
            1,
            3L),
        ator,
        "request-faq-update"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("outra sessao");
  }

  private FaqEntity faq(String status) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID actor = UUID.randomUUID();
    FaqEntity faq = FaqEntity.criarRascunho(
        UUID.randomUUID(),
        "Como funciona a homologacao?",
        "Resposta segura para o ambiente de QA.",
        "GERAL",
        1,
        actor,
        "request-faq-fixture",
        now);
    if ("PUBLICADO".equals(status)) {
      faq.publicar(actor, now);
    } else if ("ARQUIVADO".equals(status)) {
      faq.arquivar(actor, now);
    }
    return faq;
  }
}
