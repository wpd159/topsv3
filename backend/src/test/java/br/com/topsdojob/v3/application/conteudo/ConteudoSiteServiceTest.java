package br.com.topsdojob.v3.application.conteudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.conteudo.dto.ConteudoSiteRequest;
import br.com.topsdojob.v3.persistence.entity.seo.SeoConteudoPaginaEntity;
import br.com.topsdojob.v3.persistence.entity.seo.SeoUrlEntity;
import br.com.topsdojob.v3.persistence.repository.SeoConteudoPaginaRepository;
import br.com.topsdojob.v3.persistence.repository.SeoUrlRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ConteudoSiteServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-25T03:00:00Z"), ZoneOffset.UTC);

  @Test
  void construtorDeProducaoEhExplicitoParaInjecaoDoSpring() throws Exception {
    var constructor = ConteudoSiteService.class.getConstructor(
        SeoConteudoPaginaRepository.class,
        SeoUrlRepository.class);

    assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
  }

  @Test
  void catalogoAdministrativoIncluiChavesNaoPublicadasSemInventarConteudo() {
    Fixture fixture = fixture();
    when(fixture.conteudoRepository.findAllByStatusIn(any())).thenReturn(List.of());

    var entries = fixture.service.listarAdministracao();

    assertThat(entries).hasSize(12);
    assertThat(entries).allSatisfy(entry -> {
      assertThat(entry.titulo()).isNull();
      assertThat(entry.corpo()).isNull();
      assertThat(entry.contentHash()).isNull();
    });
    assertThat(fixture.service.listarPublicados()).isEmpty();
  }

  @Test
  void publicaNaEstruturaSeoExistenteComHashDeterministico() {
    Fixture fixture = fixture();
    UUID actor = UUID.randomUUID();
    when(fixture.seoUrlRepository.findByCaminhoPublico("/termos-de-uso"))
        .thenReturn(Optional.empty());
    when(fixture.seoUrlRepository.saveAndFlush(any(SeoUrlEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(fixture.conteudoRepository.findTopBySeoUrlIdAndChaveOrderByVersaoDesc(any(), any()))
        .thenReturn(Optional.empty());
    when(fixture.conteudoRepository.saveAndFlush(any(SeoConteudoPaginaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = fixture.service.publicar(
        "termos-de-uso",
        new ConteudoSiteRequest(
            "Termos de Uso",
            "# Condicoes\n\n- Regra um\n- [Ajuda](/faq)"),
        actor);

    assertThat(response.contentKey()).isEqualTo("termos-de-uso");
    assertThat(response.contentVersion()).isZero();
    assertThat(response.contentHash()).matches("[a-f0-9]{64}");
    assertThat(response.updatedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
    verify(fixture.seoUrlRepository).findByCaminhoPublico("/termos-de-uso");
    verify(fixture.conteudoRepository).saveAndFlush(any(SeoConteudoPaginaEntity.class));
  }

  @Test
  void atualizaLinhaPublicadaSemCriarFonteParalela() {
    Fixture fixture = fixture();
    UUID actor = UUID.randomUUID();
    UUID urlId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(CLOCK);
    SeoUrlEntity url = SeoUrlEntity.criarConteudoInstitucional(urlId, "/sobre", now);
    SeoConteudoPaginaEntity existing = SeoConteudoPaginaEntity.criarPublicada(
        UUID.randomUUID(),
        urlId,
        "quem_somos",
        "Anterior",
        "Conteudo anterior",
        actor,
        now.minusDays(1));
    when(fixture.seoUrlRepository.findByCaminhoPublico("/sobre")).thenReturn(Optional.of(url));
    when(fixture.conteudoRepository.findTopBySeoUrlIdAndChaveOrderByVersaoDesc(any(), any()))
        .thenReturn(Optional.of(existing));
    when(fixture.conteudoRepository.saveAndFlush(existing)).thenReturn(existing);

    var response = fixture.service.publicar(
        "quem-somos",
        new ConteudoSiteRequest("Quem somos", "Conteudo atual"),
        actor);

    assertThat(response.titulo()).isEqualTo("Quem somos");
    assertThat(response.corpo()).isEqualTo("Conteudo atual");
    assertThat(existing.getAtualizadoEm()).isEqualTo(now);
  }

  @Test
  void rejeitaHtmlScriptEProtocolosExecutaveis() {
    Fixture fixture = fixture();
    UUID actor = UUID.randomUUID();

    assertThatThrownBy(() -> fixture.service.publicar(
        "termos-de-uso",
        new ConteudoSiteRequest("Termos", "<script>alert(1)</script>"),
        actor))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> fixture.service.publicar(
        "termos-de-uso",
        new ConteudoSiteRequest("Termos", "[abrir](javascript:alert(1))"),
        actor))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void conteudoComVinculoDeRotaIncompativelNaoEhPublicado() {
    Fixture fixture = fixture();
    UUID actor = UUID.randomUUID();
    UUID urlId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(CLOCK);
    SeoUrlEntity wrongUrl = SeoUrlEntity.criarConteudoInstitucional(urlId, "/sobre", now);
    SeoConteudoPaginaEntity content = SeoConteudoPaginaEntity.criarPublicada(
        UUID.randomUUID(),
        urlId,
        "termos_de_uso",
        "Termos",
        "Corpo",
        actor,
        now);
    when(fixture.conteudoRepository.findAllByStatusIn(any())).thenReturn(List.of(content));
    when(fixture.seoUrlRepository.findAllById(any())).thenReturn(List.of(wrongUrl));

    assertThat(fixture.service.listarPublicados()).isEmpty();
  }

  private Fixture fixture() {
    SeoConteudoPaginaRepository conteudoRepository = mock(SeoConteudoPaginaRepository.class);
    SeoUrlRepository seoUrlRepository = mock(SeoUrlRepository.class);
    return new Fixture(
        new ConteudoSiteService(conteudoRepository, seoUrlRepository, CLOCK),
        conteudoRepository,
        seoUrlRepository);
  }

  private record Fixture(
      ConteudoSiteService service,
      SeoConteudoPaginaRepository conteudoRepository,
      SeoUrlRepository seoUrlRepository) {
  }
}
