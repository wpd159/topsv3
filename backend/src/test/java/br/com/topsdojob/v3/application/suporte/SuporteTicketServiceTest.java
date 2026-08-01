package br.com.topsdojob.v3.application.suporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class SuporteTicketServiceTest {

    private static final UUID USUARIO = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID OUTRO_USUARIO = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID TICKET = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID MENSAGEM = UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final String KEY = "suporte-test-0001";

    private MeusAnunciosConsultaService usuarioService;
    private SuporteTicketJdbcRepository repository;
    private PublicAuthRateLimiter rateLimiter;
    private AuditoriaEventoRepository auditoriaRepository;
    private Authentication authentication;
    private UsuarioEntity usuario;
    private SuporteTicketService service;

    @BeforeEach
    void setUp() {
        usuarioService = org.mockito.Mockito.mock(MeusAnunciosConsultaService.class);
        repository = org.mockito.Mockito.mock(SuporteTicketJdbcRepository.class);
        rateLimiter = org.mockito.Mockito.mock(PublicAuthRateLimiter.class);
        auditoriaRepository = org.mockito.Mockito.mock(AuditoriaEventoRepository.class);
        authentication = org.mockito.Mockito.mock(Authentication.class);
        usuario = org.mockito.Mockito.mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        service = new SuporteTicketService(
                usuarioService,
                repository,
                rateLimiter,
                auditoriaRepository);
    }

    @Test
    void terceiroNaoAcessaTicketAlheio() {
        when(repository.porId(TICKET)).thenReturn(Optional.of(ticket("ABERTO", OUTRO_USUARIO)));

        assertThatThrownBy(() -> service.detalhar(TICKET, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).mensagens(any(), anyBoolean());
    }

    @Test
    void mensagemEhSanitizadaEPersistidaUmaVez() {
        SuporteTicketJdbcRepository.TicketRow ticket = ticket("ABERTO", USUARIO);
        SuporteTicketJdbcRepository.MensagemRow armazenada = mensagem("Ola suporte");
        when(repository.mensagemPorIdempotencia(USUARIO, KEY))
                .thenReturn(Optional.empty(), Optional.of(armazenada));
        when(repository.porIdComLock(TICKET)).thenReturn(Optional.of(ticket));
        when(repository.inserirMensagem(
                any(), eq(TICKET), eq(USUARIO), eq("USUARIO"), anyString(),
                eq(false), eq(false), eq(KEY), anyString(), any()))
                .thenReturn(1);
        when(auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(anyString(), any(), anyString()))
                .thenReturn(false);

        var response = service.responder(
                TICKET,
                "<script>alert(1)</script>Ola suporte",
                KEY,
                authentication,
                "request-suporte-001");

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(repository).inserirMensagem(
                any(), eq(TICKET), eq(USUARIO), eq("USUARIO"), corpo.capture(),
                eq(false), eq(false), eq(KEY), anyString(), any());
        assertThat(corpo.getValue()).isEqualTo("Ola suporte");
        assertThat(response.corpo()).isEqualTo("Ola suporte");
        verify(repository).tocar(eq(TICKET), any());
        verify(rateLimiter).require(eq("suporte-responder"), eq(USUARIO.toString()), eq(30), any());
    }

    @Test
    void retryIdenticoNaoDuplicaMensagem() {
        SuporteTicketJdbcRepository.MensagemRow armazenada = mensagem("Mensagem QA");
        when(repository.mensagemPorIdempotencia(USUARIO, KEY)).thenReturn(Optional.of(armazenada));

        var response = service.responder(
                TICKET,
                "Mensagem QA",
                KEY,
                authentication,
                "request-suporte-002");

        assertThat(response.repetida()).isTrue();
        verify(repository, never()).inserirMensagem(
                any(), any(), any(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyString(), any());
        verify(rateLimiter, never()).require(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void retryConcorrenteNaoRepeteEstadoNemAuditoria() {
        SuporteTicketJdbcRepository.MensagemRow armazenada = mensagem("Mensagem QA");
        when(repository.mensagemPorIdempotencia(USUARIO, KEY))
                .thenReturn(Optional.empty(), Optional.of(armazenada));
        when(repository.porIdComLock(TICKET)).thenReturn(Optional.of(ticket("ABERTO", USUARIO)));
        when(repository.inserirMensagem(
                any(), eq(TICKET), eq(USUARIO), eq("USUARIO"), eq("Mensagem QA"),
                eq(false), eq(false), eq(KEY), anyString(), any()))
                .thenReturn(0);

        var response = service.responder(
                TICKET,
                "Mensagem QA",
                KEY,
                authentication,
                "request-suporte-concorrente-001");

        assertThat(response.repetida()).isTrue();
        verify(repository, never()).tocar(any(), any());
        verify(repository, never()).atualizarStatus(any(), anyString(), any(), any(), any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void chaveReutilizadaComOutroConteudoRetorna409() {
        when(repository.mensagemPorIdempotencia(USUARIO, KEY))
                .thenReturn(Optional.of(mensagem("Mensagem original")));

        assertThatThrownBy(() -> service.responder(
                TICKET,
                "Mensagem diferente",
                KEY,
                authentication,
                "request-suporte-003"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void ticketEncerradoNaoAceitaNovaResposta() {
        when(repository.mensagemPorIdempotencia(USUARIO, KEY)).thenReturn(Optional.empty());
        when(repository.porIdComLock(TICKET)).thenReturn(Optional.of(ticket("ENCERRADO", USUARIO)));

        assertThatThrownBy(() -> service.responder(
                TICKET,
                "Nova resposta",
                KEY,
                authentication,
                "request-suporte-004"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).inserirMensagem(
                any(), any(), any(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyString(), any());
    }

    @Test
    void respostaDoUsuarioRetomaAtendimentoSemFabricarEstadoVazio() {
        when(repository.mensagemPorIdempotencia(USUARIO, KEY))
                .thenReturn(Optional.empty(), Optional.of(mensagem("Retorno QA")));
        when(repository.porIdComLock(TICKET))
                .thenReturn(Optional.of(ticket("AGUARDANDO_USUARIO", USUARIO)));
        when(repository.inserirMensagem(
                any(), any(), any(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyString(), anyString(), any()))
                .thenReturn(1);

        service.responder(
                TICKET,
                "Retorno QA",
                KEY,
                authentication,
                "request-suporte-005");

        verify(repository).atualizarStatus(eq(TICKET), eq("EM_ATENDIMENTO"), eq(null), eq(null), any());
    }

    @Test
    void listaVazia200PermaneceEstadoLegitimo() {
        when(repository.contarDoUsuario(USUARIO, "TODOS")).thenReturn(0L);
        when(repository.listarDoUsuario(eq(USUARIO), eq("TODOS"), eq(20), anyLong()))
                .thenReturn(List.of());

        var pagina = service.listar("TODOS", 0, 20, authentication);

        assertThat(pagina.itens()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }

    @Test
    void abrirTicketDoProprioUsuarioMarcaSomenteRespostasDaEquipeComoLidas() {
        SuporteTicketJdbcRepository.TicketRow antes = ticket("ABERTO", USUARIO, 2);
        SuporteTicketJdbcRepository.TicketRow depois = ticket("ABERTO", USUARIO, 0);
        when(repository.porId(TICKET)).thenReturn(Optional.of(antes), Optional.of(depois));
        when(repository.mensagens(TICKET, false)).thenReturn(List.of());

        var detalhe = service.detalhar(TICKET, authentication);

        verify(repository).marcarRespostasComoLidas(TICKET, USUARIO);
        assertThat(detalhe.ticket().naoLidas()).isZero();
    }

    @Test
    void contadorDeNaoLidasEhSempreDerivadoDaSessao() {
        when(repository.contarNaoLidasDoUsuario(USUARIO)).thenReturn(3L);

        assertThat(service.naoLidas(authentication).total()).isEqualTo(3);
        verify(repository).contarNaoLidasDoUsuario(USUARIO);
    }

    private SuporteTicketJdbcRepository.TicketRow ticket(String status, UUID usuarioId) {
        return ticket(status, usuarioId, 0);
    }

    private SuporteTicketJdbcRepository.TicketRow ticket(String status, UUID usuarioId, long naoLidas) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T12:00:00Z");
        return new SuporteTicketJdbcRepository.TicketRow(
                TICKET,
                "Assunto de suporte",
                "OUTROS",
                status,
                "MEDIA",
                usuarioId,
                null,
                now,
                now,
                "ENCERRADO".equals(status) ? now : null,
                1,
                naoLidas);
    }

    private SuporteTicketJdbcRepository.MensagemRow mensagem(String corpo) {
        return new SuporteTicketJdbcRepository.MensagemRow(
                MENSAGEM,
                TICKET,
                USUARIO,
                "USUARIO",
                corpo,
                false,
                false,
                OffsetDateTime.parse("2026-07-27T12:00:00Z"),
                "QA");
    }
}
