package br.com.topsdojob.v3.application.denuncia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.CriarDenunciaRequest;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DenunciaPublicaServiceTest {

    private static final UUID ANUNCIO = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID DENUNCIA = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final String KEY = "denuncia-test-0001";
    private static final String REQUEST_ID = "request-denuncia-001";
    private static final String CONTEXTO = "a".repeat(64);

    private AnuncioRepository anuncioRepository;
    private UsuarioRepository usuarioRepository;
    private DenunciaJdbcRepository repository;
    private PublicAuthRateLimiter rateLimiter;
    private MetricaPublicaHashService hashService;
    private AuditoriaEventoRepository auditoriaRepository;
    private HttpServletRequest request;
    private AnuncioEntity anuncio;
    private DenunciaPublicaService service;

    @BeforeEach
    void setUp() {
        anuncioRepository = org.mockito.Mockito.mock(AnuncioRepository.class);
        usuarioRepository = org.mockito.Mockito.mock(UsuarioRepository.class);
        repository = org.mockito.Mockito.mock(DenunciaJdbcRepository.class);
        rateLimiter = org.mockito.Mockito.mock(PublicAuthRateLimiter.class);
        hashService = org.mockito.Mockito.mock(MetricaPublicaHashService.class);
        auditoriaRepository = org.mockito.Mockito.mock(AuditoriaEventoRepository.class);
        request = org.mockito.Mockito.mock(HttpServletRequest.class);
        anuncio = org.mockito.Mockito.mock(AnuncioEntity.class);
        when(anuncio.getId()).thenReturn(ANUNCIO);
        when(anuncio.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
        when(anuncio.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.APROVADO);
        when(anuncio.getRemovidoEm()).thenReturn(null);
        when(anuncioRepository.findById(ANUNCIO)).thenReturn(Optional.of(anuncio));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("QA Browser");
        when(hashService.hash(eq("denuncia-ip"), anyString())).thenReturn("b".repeat(64));
        when(hashService.hash(eq("denuncia-user-agent"), anyString())).thenReturn("c".repeat(64));
        when(hashService.hash(eq("denuncia-contexto"), anyString())).thenReturn(CONTEXTO);
        service = new DenunciaPublicaService(
                anuncioRepository,
                usuarioRepository,
                repository,
                rateLimiter,
                hashService,
                auditoriaRepository);
    }

    @Test
    void visitanteCriaUmaDenunciaSanitizadaSemAlterarAnuncio() {
        DenunciaJdbcRepository.DenunciaRow row = row("CONTEUDO_INADEQUADO", "Relato seguro");
        when(repository.porIdempotencia(ANUNCIO, CONTEXTO, KEY))
                .thenReturn(Optional.empty(), Optional.of(row));
        when(repository.inserir(
                any(), eq(ANUNCIO), eq(null), eq(CONTEXTO), eq("CONTEUDO_INADEQUADO"),
                any(), eq(KEY), eq(REQUEST_ID), any(), any(), any()))
                .thenReturn(1);
        when(auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(
                "DENUNCIA_CRIADA", DENUNCIA, REQUEST_ID))
                .thenReturn(false);

        var response = service.criar(
                new CriarDenunciaRequest(
                        ANUNCIO,
                        "CONTEUDO_INADEQUADO",
                        "<script>alert(1)</script>Relato seguro"),
                KEY,
                null,
                request,
                REQUEST_ID);

        ArgumentCaptor<String> descricao = ArgumentCaptor.forClass(String.class);
        verify(repository).inserir(
                any(), eq(ANUNCIO), eq(null), eq(CONTEXTO), eq("CONTEUDO_INADEQUADO"),
                descricao.capture(), eq(KEY), eq(REQUEST_ID), any(), any(), any());
        assertThat(descricao.getValue()).isEqualTo("Relato seguro");
        assertThat(response.status()).isEqualTo("PENDENTE");
        assertThat(response.repetida()).isFalse();
        verify(rateLimiter).require(eq("denuncia-criar"), eq(CONTEXTO), eq(5), any());
        verify(anuncioRepository, never()).save(any());
    }

    @Test
    void retryIdenticoNaoDuplicaDenuncia() {
        when(repository.porIdempotencia(ANUNCIO, CONTEXTO, KEY))
                .thenReturn(Optional.of(row("SPAM", null)));

        var response = service.criar(
                new CriarDenunciaRequest(ANUNCIO, "SPAM", null),
                KEY,
                null,
                request,
                REQUEST_ID);

        assertThat(response.repetida()).isTrue();
        verify(repository, never()).inserir(
                any(), any(), any(), anyString(), anyString(), any(), anyString(),
                anyString(), any(), any(), any());
        verify(rateLimiter, never()).require(anyString(), anyString(), any(Integer.class), any());
    }

    @Test
    void chaveReutilizadaComOutroRelatoRetorna409() {
        when(repository.porIdempotencia(ANUNCIO, CONTEXTO, KEY))
                .thenReturn(Optional.of(row("SPAM", "Original")));

        assertThatThrownBy(() -> service.criar(
                new CriarDenunciaRequest(ANUNCIO, "OUTROS", "Outro"),
                KEY,
                null,
                request,
                REQUEST_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void anuncioForaDoCatalogoNaoPodeSerDenunciado() {
        when(anuncio.getStatus()).thenReturn(StatusAnuncio.PAUSADO);

        assertThatThrownBy(() -> service.criar(
                new CriarDenunciaRequest(ANUNCIO, "SPAM", null),
                KEY,
                null,
                request,
                REQUEST_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repository, never()).inserir(
                any(), any(), any(), anyString(), anyString(), any(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    void motivoVazioEhRecusadoSemCriarDenuncia() {
        assertThatThrownBy(() -> service.criar(
                new CriarDenunciaRequest(ANUNCIO, "", "texto"),
                KEY,
                null,
                request,
                REQUEST_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(repository, never()).inserir(
                any(), any(), any(), anyString(), anyString(), any(), anyString(),
                anyString(), any(), any(), any());
    }

    private DenunciaJdbcRepository.DenunciaRow row(String motivo, String descricao) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        return new DenunciaJdbcRepository.DenunciaRow(
                DENUNCIA,
                ANUNCIO,
                null,
                CONTEXTO,
                motivo,
                descricao,
                "PENDENTE",
                null,
                null,
                KEY,
                REQUEST_ID,
                "b".repeat(64),
                "c".repeat(64),
                now,
                now,
                null,
                0,
                "Anuncio QA",
                "anuncio-qa",
                "PUBLICADO",
                "APROVADO",
                null,
                null,
                null);
    }
}
