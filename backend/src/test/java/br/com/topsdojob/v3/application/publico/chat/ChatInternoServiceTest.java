package br.com.topsdojob.v3.application.publico.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.persistence.entity.chat.ChatConversaEntity;
import br.com.topsdojob.v3.persistence.entity.chat.ChatMensagemEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.chat.ChatConversaRepository;
import br.com.topsdojob.v3.persistence.repository.chat.ChatConversaResumoProjection;
import br.com.topsdojob.v3.persistence.repository.chat.ChatMensagemRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
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

class ChatInternoServiceTest {

    private static final UUID USUARIO_A = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID USUARIO_B = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID USUARIO_C = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID CONVERSA_ID = UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final UUID MENSAGEM_ID = UUID.fromString("50000000-0000-4000-8000-000000000005");
    private static final String IDEMPOTENCY_KEY = "chat-test-0001";

    private MeusAnunciosConsultaService autenticacaoService;
    private UsuarioRepository usuarioRepository;
    private ChatConversaRepository conversaRepository;
    private ChatMensagemRepository mensagemRepository;
    private PublicAuthRateLimiter rateLimiter;
    private Authentication authentication;
    private UsuarioEntity ator;
    private UsuarioEntity participante;
    private ChatInternoService service;

    @BeforeEach
    void setUp() {
        autenticacaoService = mock(MeusAnunciosConsultaService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        conversaRepository = mock(ChatConversaRepository.class);
        mensagemRepository = mock(ChatMensagemRepository.class);
        rateLimiter = mock(PublicAuthRateLimiter.class);
        authentication = mock(Authentication.class);
        ator = usuario(USUARIO_A, "qa-a");
        participante = usuario(USUARIO_B, "qa-b");
        when(autenticacaoService.usuarioAutenticado(authentication)).thenReturn(ator);
        service = new ChatInternoService(
                autenticacaoService,
                usuarioRepository,
                conversaRepository,
                mensagemRepository,
                rateLimiter);
    }

    @Test
    void criaOuReutilizaUmaUnicaConversaPeloUsername() {
        ChatConversaEntity conversa = conversa(USUARIO_A, USUARIO_B);
        when(usuarioRepository.findByNomeIgnoreCase(anyString())).thenReturn(Optional.of(participante));
        when(conversaRepository.inserirSeAusente(any(), eq(USUARIO_A), eq(USUARIO_B), anyString(), any()))
                .thenReturn(1);
        when(conversaRepository.findByParticipanteAIdAndParticipanteBId(USUARIO_A, USUARIO_B))
                .thenReturn(Optional.of(conversa));
        ChatConversaResumoProjection resumo = resumo();
        when(conversaRepository.listarResumos(USUARIO_A)).thenReturn(List.of(resumo));

        var primeira = service.iniciar("qa-b", authentication, "request-chat-0001");
        var repetida = service.iniciar("QA-B", authentication, "request-chat-0002");

        assertThat(primeira.id()).isEqualTo(CONVERSA_ID);
        assertThat(repetida.id()).isEqualTo(CONVERSA_ID);
        verify(conversaRepository, times(2)).inserirSeAusente(
                any(),
                eq(USUARIO_A),
                eq(USUARIO_B),
                anyString(),
                any());
    }

    @Test
    void impedeConversaComOProprioUsuario() {
        when(usuarioRepository.findByNomeIgnoreCase("qa-a")).thenReturn(Optional.of(ator));

        assertThatThrownBy(() -> service.iniciar(
                "qa-a",
                authentication,
                "request-chat-0003"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(conversaRepository, never()).inserirSeAusente(
                any(), any(), any(), anyString(), any());
    }

    @Test
    void terceiroNaoAcessaConversaAlheia() {
        ChatConversaEntity conversa = conversa(USUARIO_B, USUARIO_C);
        when(conversaRepository.findById(CONVERSA_ID)).thenReturn(Optional.of(conversa));

        assertThatThrownBy(() -> service.detalhar(CONVERSA_ID, authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(mensagemRepository, never()).findByConversaIdOrderByCriadoEmAscIdAsc(any());
    }

    @Test
    void enviaTextoSanitizadoUmaUnicaVez() {
        ChatConversaEntity conversa = conversa(USUARIO_A, USUARIO_B);
        ChatMensagemEntity mensagem = mensagem(
                "<script>alert(1)</script>Ola qa-b",
                "Ola qa-b",
                USUARIO_A,
                USUARIO_B);
        when(conversaRepository.findByIdForUpdate(CONVERSA_ID)).thenReturn(Optional.of(conversa));
        when(mensagemRepository.findByRemetenteUsuarioIdAndIdempotencyKey(
                USUARIO_A,
                IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.of(mensagem));
        when(mensagemRepository.inserirSeAusente(
                any(),
                eq(CONVERSA_ID),
                eq(USUARIO_A),
                eq(USUARIO_B),
                anyString(),
                eq(IDEMPOTENCY_KEY),
                anyString(),
                any()))
                .thenReturn(1);
        when(usuarioRepository.findById(USUARIO_B)).thenReturn(Optional.of(participante));

        var response = service.enviar(
                CONVERSA_ID,
                "<script>alert(1)</script>Ola qa-b",
                IDEMPOTENCY_KEY,
                authentication,
                "request-chat-0004");

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(mensagemRepository).inserirSeAusente(
                any(),
                eq(CONVERSA_ID),
                eq(USUARIO_A),
                eq(USUARIO_B),
                corpo.capture(),
                eq(IDEMPOTENCY_KEY),
                anyString(),
                any());
        assertThat(corpo.getValue()).isEqualTo("Ola qa-b");
        assertThat(response.corpo()).isEqualTo("Ola qa-b");
        assertThat(response.repetida()).isFalse();
        verify(conversa).registrarMensagem(any());
        verify(rateLimiter).require(eq("chat-send"), eq(USUARIO_A.toString()), eq(30), any());
    }

    @Test
    void retryIdenticoNaoInsereSegundaMensagem() {
        ChatMensagemEntity mensagem = mensagem("Mensagem QA", "Mensagem QA", USUARIO_A, USUARIO_B);
        when(mensagemRepository.findByRemetenteUsuarioIdAndIdempotencyKey(
                USUARIO_A,
                IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(mensagem));
        when(usuarioRepository.findById(USUARIO_A)).thenReturn(Optional.of(ator));
        when(usuarioRepository.findById(USUARIO_B)).thenReturn(Optional.of(participante));

        var response = service.enviar(
                CONVERSA_ID,
                "Mensagem QA",
                IDEMPOTENCY_KEY,
                authentication,
                "request-chat-0005");

        assertThat(response.repetida()).isTrue();
        assertThat(response.id()).isEqualTo(MENSAGEM_ID);
        verify(mensagemRepository, never()).inserirSeAusente(
                any(), any(), any(), any(), anyString(), anyString(), anyString(), any());
        verify(rateLimiter, never()).require(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void chaveReutilizadaComOutroConteudoRetornaConflito() {
        ChatMensagemEntity mensagem = mensagem("Mensagem original", "Mensagem original", USUARIO_A, USUARIO_B);
        when(mensagemRepository.findByRemetenteUsuarioIdAndIdempotencyKey(
                USUARIO_A,
                IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(mensagem));

        assertThatThrownBy(() -> service.enviar(
                CONVERSA_ID,
                "Mensagem diferente",
                IDEMPOTENCY_KEY,
                authentication,
                "request-chat-0006"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void abrirConversaZeraSomenteAsNaoLidasDoParticipante() {
        ChatConversaEntity conversa = conversa(USUARIO_A, USUARIO_B);
        when(conversaRepository.findByIdForUpdate(CONVERSA_ID)).thenReturn(Optional.of(conversa));
        when(mensagemRepository.countByDestinatarioUsuarioIdAndLidoEmIsNull(USUARIO_A))
                .thenReturn(0L);

        var response = service.marcarComoLida(CONVERSA_ID, authentication);

        verify(mensagemRepository).marcarComoLidas(eq(CONVERSA_ID), eq(USUARIO_A), any());
        assertThat(response.total()).isZero();
    }

    @Test
    void historicoMantemOrdemDoRepositorioESinalizaRemetente() {
        ChatConversaEntity conversa = conversa(USUARIO_A, USUARIO_B);
        ChatMensagemEntity primeira = mensagem("Primeira", "Primeira", USUARIO_B, USUARIO_A);
        ChatMensagemEntity segunda = mensagem("Segunda", "Segunda", USUARIO_A, USUARIO_B);
        when(conversaRepository.findById(CONVERSA_ID)).thenReturn(Optional.of(conversa));
        when(usuarioRepository.findById(USUARIO_B)).thenReturn(Optional.of(participante));
        when(mensagemRepository.findByConversaIdOrderByCriadoEmAscIdAsc(CONVERSA_ID))
                .thenReturn(List.of(primeira, segunda));
        ChatConversaResumoProjection resumo = resumo();
        when(conversaRepository.listarResumos(USUARIO_A)).thenReturn(List.of(resumo));

        var response = service.detalhar(CONVERSA_ID, authentication);

        assertThat(response.mensagens()).extracting(item -> item.corpo())
                .containsExactly("Primeira", "Segunda");
        assertThat(response.mensagens()).extracting(item -> item.minha())
                .containsExactly(false, true);
    }

    private UsuarioEntity usuario(UUID id, String nome) {
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getNome()).thenReturn(nome);
        when(usuario.getStatus()).thenReturn(StatusUsuario.ATIVO);
        when(usuario.getTipoConta()).thenReturn(TipoContaUsuario.ANUNCIANTE);
        when(usuario.getDesativadoEm()).thenReturn(null);
        return usuario;
    }

    private ChatConversaEntity conversa(UUID a, UUID b) {
        ChatConversaEntity conversa = mock(ChatConversaEntity.class);
        when(conversa.getId()).thenReturn(CONVERSA_ID);
        when(conversa.getParticipanteAId()).thenReturn(a);
        when(conversa.getParticipanteBId()).thenReturn(b);
        when(conversa.contem(any())).thenAnswer(invocation -> {
            UUID usuarioId = invocation.getArgument(0);
            return a.equals(usuarioId) || b.equals(usuarioId);
        });
        when(conversa.outroParticipante(a)).thenReturn(b);
        when(conversa.outroParticipante(b)).thenReturn(a);
        return conversa;
    }

    private ChatMensagemEntity mensagem(
            String entrada,
            String armazenada,
            UUID remetente,
            UUID destinatario) {
        ChatMensagemEntity mensagem = mock(ChatMensagemEntity.class);
        when(mensagem.getId()).thenReturn(MENSAGEM_ID);
        when(mensagem.getConversaId()).thenReturn(CONVERSA_ID);
        when(mensagem.getRemetenteUsuarioId()).thenReturn(remetente);
        when(mensagem.getDestinatarioUsuarioId()).thenReturn(destinatario);
        when(mensagem.getCorpo()).thenReturn(armazenada);
        when(mensagem.getIdempotencyKey()).thenReturn(IDEMPOTENCY_KEY);
        when(mensagem.getCriadoEm()).thenReturn(OffsetDateTime.parse("2026-07-27T12:00:00Z"));
        return mensagem;
    }

    private ChatConversaResumoProjection resumo() {
        ChatConversaResumoProjection resumo = mock(ChatConversaResumoProjection.class);
        when(resumo.getId()).thenReturn(CONVERSA_ID);
        when(resumo.getParticipanteUsername()).thenReturn("qa-b");
        when(resumo.getUltimaMensagem()).thenReturn("Mensagem QA");
        when(resumo.getUltimaMensagemEm()).thenReturn(OffsetDateTime.parse("2026-07-27T12:00:00Z"));
        when(resumo.getNaoLidas()).thenReturn(1L);
        return resumo;
    }
}
