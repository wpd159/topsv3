package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class MetricaPublicaServiceTest {

    @Test
    void registraVisualizacaoComHashesSemPersistirIpUserAgentOuRefererBrutos() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncioLivre(anuncioId, null);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        EventoVisualizacaoRepository eventoRepository = mock(EventoVisualizacaoRepository.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq("anuncio-local"),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(Optional.of(anuncio));
        when(eventoRepository.save(any(EventoVisualizacaoEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MetricaPublicaService service = new MetricaPublicaService(
                anuncioRepository,
                eventoRepository,
                mock(CliqueWhatsappRepository.class),
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
                http);

        ArgumentCaptor<EventoVisualizacaoEntity> captor = ArgumentCaptor.forClass(EventoVisualizacaoEntity.class);
        verify(eventoRepository).save(captor.capture());
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
        CliqueWhatsappRepository cliqueRepository = mock(CliqueWhatsappRepository.class);
        when(anuncioRepository.findBySlugAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq("anuncio-local"),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(Optional.of(anuncio));
        when(cliqueRepository.save(any(CliqueWhatsappEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ComplianceVisitorAccessService visitorAccessService =
                mock(ComplianceVisitorAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(visitorAccessService.autorizado(
                request,
                EscopoConteudoVisitante.WHATSAPP)).thenReturn(true);

        MetricaPublicaService service = new MetricaPublicaService(
                anuncioRepository,
                mock(EventoVisualizacaoRepository.class),
                cliqueRepository,
                new MetricaPublicaHashService("valor_local_ficticio", "local"),
                new PoliticaContatoPublicoService(),
                visitorAccessService);

        CliqueWhatsappPublicoResponseDto response = service.registrarCliqueWhatsapp(
                "anuncio-local",
                new CliqueWhatsappPublicoRequestDto("visitante-local", "BR", "SP", "Cidade Local", "MOBILE"),
                request);

        ArgumentCaptor<CliqueWhatsappEntity> captor = ArgumentCaptor.forClass(CliqueWhatsappEntity.class);
        verify(cliqueRepository).save(captor.capture());
        assertThat(response.disponivel()).isTrue();
        assertThat(response.whatsappUrl()).isEqualTo("https://wa.me/5500000000000");
        assertThat(captor.getValue().getPermitido()).isTrue();
        assertThat(response.toString()).doesNotContain("whatsapp_normalizado");
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
