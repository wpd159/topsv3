package br.com.topsdojob.v3.application.suporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminSuporteServiceTest {

    private static final UUID TICKET = UUID.fromString("50000000-0000-4000-8000-000000000005");
    private static final UUID USUARIO = UUID.fromString("60000000-0000-4000-8000-000000000006");
    private static final UUID ADMIN = UUID.fromString("70000000-0000-4000-8000-000000000007");
    private static final UUID MENSAGEM = UUID.fromString("80000000-0000-4000-8000-000000000008");
    private static final String KEY = "suporte-admin-0001";

    private SuporteTicketJdbcRepository repository;
    private AuditoriaEventoRepository auditoriaRepository;
    private AdminSuporteService service;
    private AdminUserPrincipal ator;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(SuporteTicketJdbcRepository.class);
        auditoriaRepository = org.mockito.Mockito.mock(AuditoriaEventoRepository.class);
        service = new AdminSuporteService(repository, auditoriaRepository);
        ator = new AdminUserPrincipal(
                ADMIN,
                "Admin QA",
                "admin@example.invalid",
                "hash",
                List.of(PapelUsuario.ADMIN),
                List.of(new AdminPermissionDto("SUPORTE_ATENDER", "Suporte")),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("SUPORTE_ATENDER")),
                true);
    }

    @Test
    void respostaDaEquipeEhIdempotenteEAtualizaContador() {
        when(repository.mensagemPorIdempotencia(ADMIN, KEY))
                .thenReturn(Optional.empty(), Optional.of(mensagem("Resposta QA")));
        when(repository.porIdComLock(TICKET)).thenReturn(Optional.of(ticket("ABERTO")));
        when(repository.inserirMensagem(
                any(), eq(TICKET), eq(ADMIN), eq("STAFF"), eq("Resposta QA"),
                eq(false), eq(KEY), anyString(), any()))
                .thenReturn(1);

        var response = service.responder(
                TICKET,
                "Resposta QA",
                KEY,
                ator,
                "request-suporte-admin-001");

        assertThat(response.repetida()).isFalse();
        verify(repository).atualizarStatus(
                eq(TICKET), eq("AGUARDANDO_USUARIO"), eq(ADMIN), eq(null), any());
    }

    @Test
    void retryDaRespostaNaoInsereOutraMensagem() {
        when(repository.mensagemPorIdempotencia(ADMIN, KEY))
                .thenReturn(Optional.of(mensagem("Resposta QA")));

        var response = service.responder(
                TICKET,
                "Resposta QA",
                KEY,
                ator,
                "request-suporte-admin-002");

        assertThat(response.repetida()).isTrue();
        verify(repository, never()).inserirMensagem(
                any(), any(), any(), anyString(), anyString(), any(Boolean.class),
                anyString(), anyString(), any());
    }

    @Test
    void ticketFinalizadoNaoPodeSerReaberto() {
        when(repository.porIdComLock(TICKET)).thenReturn(Optional.of(ticket("ENCERRADO")));

        assertThatThrownBy(() -> service.alterarStatus(
                TICKET,
                "ABERTO",
                ator,
                "request-suporte-admin-003"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).atualizarStatus(any(), anyString(), any(), any(), any());
    }

    @Test
    void conteudoDaMensagemNaoEntraNaAuditoria() {
        when(repository.mensagemPorIdempotencia(ADMIN, KEY))
                .thenReturn(Optional.empty(), Optional.of(mensagem("Segredo QA")));
        when(repository.porIdComLock(TICKET)).thenReturn(Optional.of(ticket("EM_ATENDIMENTO")));
        when(repository.inserirMensagem(
                any(), any(), any(), anyString(), anyString(), any(Boolean.class),
                anyString(), anyString(), any()))
                .thenReturn(1);

        service.responder(
                TICKET,
                "Segredo QA",
                KEY,
                ator,
                "request-suporte-admin-004");

        var captor = org.mockito.ArgumentCaptor.forClass(
                br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(captor.capture());
        assertThat(captor.getValue().getAntesJson()).doesNotContain("Segredo QA");
        assertThat(captor.getValue().getDepoisJson()).doesNotContain("Segredo QA");
        assertThat(captor.getValue().getOrigem().name()).isEqualTo("SUPORTE");
    }

    private SuporteTicketJdbcRepository.TicketRow ticket(String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-27T12:00:00Z");
        return new SuporteTicketJdbcRepository.TicketRow(
                TICKET,
                "Assunto QA",
                "OUTROS",
                status,
                "MEDIA",
                USUARIO,
                null,
                now,
                now,
                "ENCERRADO".equals(status) ? now : null,
                1);
    }

    private SuporteTicketJdbcRepository.MensagemRow mensagem(String corpo) {
        return new SuporteTicketJdbcRepository.MensagemRow(
                MENSAGEM,
                TICKET,
                ADMIN,
                "STAFF",
                corpo,
                false,
                OffsetDateTime.parse("2026-07-27T12:00:00Z"),
                "Admin QA");
    }
}
