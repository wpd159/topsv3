package br.com.topsdojob.v3.application.admin.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.outbox.dto.AdminOutboxSimularProcessamentoRequestDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminOutboxSimulacaoServiceTest {

    private final OutboxEventoRepository outboxRepository = mock(OutboxEventoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final AdminOutboxSimulacaoService service = service("local");

    @Test
    void adminSimulaOutboxPendenteComAuditoriaSanitizada() {
        UUID id = UUID.randomUUID();
        OutboxEventoEntity entity = outbox(id);
        when(outboxRepository.findById(id)).thenReturn(Optional.of(entity));
        when(outboxRepository.save(any(OutboxEventoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditoriaRepository.save(any(AuditoriaEventoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.simularProcessamentoLocal(
                id,
                new AdminOutboxSimularProcessamentoRequestDto(
                        "validar localmente ana@example.invalid +5511999999999 123.456.789-09",
                        "cliente-local-123"),
                principal(PapelUsuario.ADMIN),
                "req-local-123456");

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.statusAntes()).isEqualTo("PENDENTE");
        assertThat(response.statusDepois()).isEqualTo("PROCESSADO");
        assertThat(response.statusAlterado()).isTrue();
        assertThat(response.envioExternoExecutado()).isFalse();
        assertThat(response.auditoriaRegistrada()).isTrue();
        assertThat(entity.getStatus().name()).isEqualTo("PROCESSADO");
        assertThat(entity.getProcessadoEm()).isNotNull();
        verify(outboxRepository).save(entity);

        ArgumentCaptor<AuditoriaEventoEntity> auditoria = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(auditoria.capture());
        assertThat(auditoria.getValue().getAcao()).isEqualTo("OUTBOX_SIMULACAO_LOCAL");
        assertThat(auditoria.getValue().getRecursoTipo()).isEqualTo("OUTBOX_EVENTO");
        assertThat(auditoria.getValue().getDepoisJson())
                .contains("\"envioExternoExecutado\":false")
                .contains("\"payloadBrutoExposto\":false")
                .contains("[email-mascarado]", "[contato-mascarado]", "[documento-mascarado]")
                .doesNotContain("ana@example.invalid", "+5511999999999", "123.456.789-09", "bucket-privado");
    }

    @Test
    void moderadorNaoSimulaProcessamento() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.simularProcessamentoLocal(id, null, principal(PapelUsuario.MODERADOR), "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void appEnvNaoConfiguradoBloqueiaSemAlterarOutbox() {
        UUID id = UUID.randomUUID();
        OutboxEventoEntity entity = outbox(id);
        AdminOutboxSimulacaoService naoConfigurado = service("nao_configurado");

        assertThatThrownBy(() -> naoConfigurado.simularProcessamentoLocal(id, null, principal(PapelUsuario.ADMIN), "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("APP_ENV=local");

        assertThat(entity.getStatus().name()).isEqualTo("PENDENTE");
        assertThat(entity.getProcessadoEm()).isNull();
        verifyNoInteractions(outboxRepository, auditoriaRepository);
    }

    @Test
    void appEnvStagingBloqueiaSemRegistrarProcessado() {
        UUID id = UUID.randomUUID();
        OutboxEventoEntity entity = outbox(id);
        when(outboxRepository.findById(id)).thenReturn(Optional.of(entity));
        AdminOutboxSimulacaoService staging = service("staging");

        assertThatThrownBy(() -> staging.simularProcessamentoLocal(id, null, principal(PapelUsuario.ADMIN), "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        assertThat(entity.getStatus().name()).isEqualTo("PENDENTE");
        assertThat(entity.getProcessadoEm()).isNull();
        verify(outboxRepository, never()).save(any(OutboxEventoEntity.class));
        verify(auditoriaRepository, never()).save(any(AuditoriaEventoEntity.class));
    }

    @Test
    void outboxInexistenteRetorna404() {
        UUID id = UUID.randomUUID();
        when(outboxRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simularProcessamentoLocal(id, null, principal(PapelUsuario.ADMIN), "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void outboxNaoPendenteRetorna409() {
        UUID id = UUID.randomUUID();
        OutboxEventoEntity entity = outbox(id);
        entity.marcarProcessadoPorSimulacaoLocal(OffsetDateTime.now());
        when(outboxRepository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.simularProcessamentoLocal(id, null, principal(PapelUsuario.ADMIN), "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    private OutboxEventoEntity outbox(UUID id) {
        return OutboxEventoEntity.registrarPendente(
                id,
                "REVISAO_ANUNCIO",
                UUID.randomUUID(),
                "MODERACAO_SOLICITAR_AJUSTE",
                payloadSensivel(),
                "MODERACAO_SOLICITAR_AJUSTE:" + id,
                OffsetDateTime.now());
    }

    private String payloadSensivel() {
        return """
                {
                  "anuncioId": "00000000-0000-4000-8000-000000000501",
                  "revisaoId": "00000000-0000-4000-8000-000000000801",
                  "decisao": "SOLICITAR_AJUSTE",
                  "statusAnuncio": "PENDENTE_REVISAO",
                  "statusModeracao": "PENDENTE",
                  "motivoSanitizado": "ajuste local ana@example.invalid +5511999999999 123.456.789-09",
                  "hardDeleteExecutado": false,
                  "envioExternoPendente": true,
                  "bucket": "bucket-privado"
                }
                """;
    }

    private AdminUserPrincipal principal(PapelUsuario papel) {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin Local",
                "admin.local@example.invalid",
                "hash-local",
                List.of(papel),
                List.of(),
                List.of(new SimpleGrantedAuthority("ROLE_" + papel.name()), new SimpleGrantedAuthority("ANUNCIO_MODERAR")),
                true);
    }

    private AdminOutboxSimulacaoService service(String appEnv) {
        return new AdminOutboxSimulacaoService(
                outboxRepository,
                auditoriaRepository,
                new ObjectMapper(),
                appEnv);
    }
}
