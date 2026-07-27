package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoRequestDto;
import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaResponseDto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorAccessService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class MetricaPublicaServiceTest {

    @Test
    void registraVisualizacaoComHashesSemPersistirIpUserAgentOuRefererBrutos() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncioLivre(anuncioId, null);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        MetricaPublicaPersistenceService persistenceService = mock(MetricaPublicaPersistenceService.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq("anuncio-local"),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(Optional.of(anuncio));
        when(persistenceService.registrarVisualizacao(any(EventoVisualizacaoEntity.class))).thenReturn(true);

        MetricaPublicaService service = new MetricaPublicaService(
                anuncioRepository,
                persistenceService,
                new MetricaPublicaHashService("valor_local_ficticio", "local"),
                new PoliticaContatoPublicoService(),
                mock(ComplianceVisitorAccessService.class));
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("127.0.0.1");
        http.addHeader("User-Agent", "Mozilla local");
        http.addHeader("Referer", "http://localhost/anuncios/anuncio-local");

        RegistrarVisualizacaoPublicaResponseDto response = service.registrarVisualizacao(
                "anuncio-local",
                new RegistrarVisualizacaoPublicaRequestDto("visitante-local", "BR", "SP", "Cidade Local", "DESKTOP"),
                "view-test-1",
                http);

        ArgumentCaptor<EventoVisualizacaoEntity> captor = ArgumentCaptor.forClass(EventoVisualizacaoEntity.class);
        verify(persistenceService).registrarVisualizacao(captor.capture());
        EventoVisualizacaoEntity salvo = captor.getValue();
        assertThat(response.registrado()).isTrue();
        assertThat(response.pendenciaStories()).isEqualTo(MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
        assertThat(salvo.getIpHash()).isNotBlank().isNotEqualTo("127.0.0.1");
        assertThat(salvo.getUserAgentHash()).isNotEqualTo("Mozilla local");
        assertThat(salvo.getRefererHash()).isNotEqualTo("http://localhost/anuncios/anuncio-local");
    }

    @Test
    void cliqueWhatsappRetornaUrlApenasQuandoPoliticaPermite() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncioLivre(anuncioId, "+5500000000000");
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        MetricaPublicaPersistenceService persistenceService = mock(MetricaPublicaPersistenceService.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq("anuncio-local"),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(Optional.of(anuncio));
        when(persistenceService.registrarClique(any(CliqueWhatsappEntity.class))).thenReturn(true);
        ComplianceVisitorAccessService visitorAccessService =
                mock(ComplianceVisitorAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.WHATSAPP)).thenReturn(true);

        MetricaPublicaService service = new MetricaPublicaService(
                anuncioRepository,
                persistenceService,
                new MetricaPublicaHashService("valor_local_ficticio", "local"),
                new PoliticaContatoPublicoService(),
                visitorAccessService);

        CliqueWhatsappPublicoResponseDto response = service.registrarCliqueWhatsapp(
                "anuncio-local",
                new CliqueWhatsappPublicoRequestDto("visitante-local", "BR", "SP", "Cidade Local", "MOBILE"),
                "click-test-1",
                request);

        ArgumentCaptor<CliqueWhatsappEntity> captor = ArgumentCaptor.forClass(CliqueWhatsappEntity.class);
        verify(persistenceService).registrarClique(captor.capture());
        assertThat(response.disponivel()).isTrue();
        assertThat(response.whatsappUrl()).isEqualTo("https://wa.me/5500000000000");
        assertThat(captor.getValue().getPermitido()).isTrue();
        assertThat(response.toString()).doesNotContain("whatsapp_normalizado");
    }

    @Test
    void retryComMesmaChaveReutilizaIdDoEvento() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioRepository anuncioRepository = anuncioRepository(anuncioLivre(anuncioId, null));
        MetricaPublicaPersistenceService persistenceService = mock(MetricaPublicaPersistenceService.class);
        when(persistenceService.registrarVisualizacao(any(EventoVisualizacaoEntity.class)))
                .thenReturn(true, false);
        MetricaPublicaService service = service(
                anuncioRepository,
                persistenceService,
                mock(ComplianceVisitorAccessService.class));

        RegistrarVisualizacaoPublicaResponseDto primeira =
                service.registrarVisualizacao("anuncio-local", null, "retry-view-1", new MockHttpServletRequest());
        RegistrarVisualizacaoPublicaResponseDto segunda =
                service.registrarVisualizacao("anuncio-local", null, "retry-view-1", new MockHttpServletRequest());

        ArgumentCaptor<EventoVisualizacaoEntity> captor = ArgumentCaptor.forClass(EventoVisualizacaoEntity.class);
        verify(persistenceService, org.mockito.Mockito.times(2)).registrarVisualizacao(captor.capture());
        List<EventoVisualizacaoEntity> eventos = captor.getAllValues();
        assertThat(eventos.get(0).getId()).isEqualTo(eventos.get(1).getId());
        assertThat(primeira.eventoId()).isEqualTo(segunda.eventoId());
    }

    @Test
    void falhaDaMetricaNaoBloqueiaUrlDoWhatsapp() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioRepository anuncioRepository = anuncioRepository(
                anuncioLivre(anuncioId, "+5500000000000"));
        MetricaPublicaPersistenceService persistenceService = mock(MetricaPublicaPersistenceService.class);
        when(persistenceService.registrarClique(any(CliqueWhatsappEntity.class)))
                .thenThrow(new IllegalStateException("falha sintetica"));
        ComplianceVisitorAccessService visitorAccessService = mock(ComplianceVisitorAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(request, EscopoConteudoVisitante.WHATSAPP)).thenReturn(true);
        MetricaPublicaService service = service(anuncioRepository, persistenceService, visitorAccessService);

        CliqueWhatsappPublicoResponseDto response = service.registrarCliqueWhatsapp(
                "anuncio-local", null, "click-failure-1", request);

        assertThat(response.registrado()).isFalse();
        assertThat(response.disponivel()).isTrue();
        assertThat(response.whatsappUrl()).isEqualTo("https://wa.me/5500000000000");
        assertThat(response.status()).isEqualTo("METRICA_INDISPONIVEL");
    }

    @Test
    void cliqueAntesDaVerificacaoNaoPersisteEvento() {
        AnuncioRepository anuncioRepository = anuncioRepository(
                anuncioLivre(UUID.randomUUID(), "+5500000000000"));
        MetricaPublicaPersistenceService persistenceService = mock(MetricaPublicaPersistenceService.class);
        ComplianceVisitorAccessService visitorAccessService = mock(ComplianceVisitorAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(request, EscopoConteudoVisitante.WHATSAPP)).thenReturn(false);
        MetricaPublicaService service = service(anuncioRepository, persistenceService, visitorAccessService);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.registrarCliqueWhatsapp("anuncio-local", null, "click-denied-1", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(persistenceService, never()).registrarClique(any());
    }

    @Test
    void falhaDaVisualizacaoRetornaServicoIndisponivel() {
        AnuncioRepository anuncioRepository = anuncioRepository(anuncioLivre(UUID.randomUUID(), null));
        MetricaPublicaPersistenceService persistenceService = mock(MetricaPublicaPersistenceService.class);
        when(persistenceService.registrarVisualizacao(any()))
                .thenThrow(new IllegalStateException("falha sintetica"));
        MetricaPublicaService service = service(
                anuncioRepository,
                persistenceService,
                mock(ComplianceVisitorAccessService.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.registrarVisualizacao(
                        "anuncio-local", null, "view-failure-1", new MockHttpServletRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private MetricaPublicaService service(
            AnuncioRepository anuncioRepository,
            MetricaPublicaPersistenceService persistenceService,
            ComplianceVisitorAccessService visitorAccessService) {
        return new MetricaPublicaService(
                anuncioRepository,
                persistenceService,
                new MetricaPublicaHashService("valor_local_ficticio", "local"),
                new PoliticaContatoPublicoService(),
                visitorAccessService);
    }

    private AnuncioRepository anuncioRepository(AnuncioEntity anuncio) {
        AnuncioRepository repository = mock(AnuncioRepository.class);
        when(repository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq("anuncio-local"),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(Optional.of(anuncio));
        return repository;
    }

    private AnuncioEntity anuncioLivre(UUID id, String whatsapp) {
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", id);
        set(anuncio, "slug", "anuncio-local");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncio, "whatsappNormalizado", whatsapp);
        return anuncio;
    }
}
