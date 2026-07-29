package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminOutboxConsultaServiceTest {

    private final OutboxEventoRepository repository = mock(OutboxEventoRepository.class);
    private final AdminOutboxConsultaService service = new AdminOutboxConsultaService(repository, new ObjectMapper());

    @Test
    void adminListaOutboxComPreviaSanitizada() {
        OutboxEventoEntity entity = outbox(
                "REVISAO_ANUNCIO",
                "MODERACAO_SOLICITAR_AJUSTE",
                payloadSensivel());
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        var page = service.listar(0, 20, StatusOutbox.PENDENTE, "moderacao_solicitar_ajuste", null, null, null, principal(PapelUsuario.ADMIN));

        assertThat(page.itens()).hasSize(1);
        var item = page.itens().get(0);
        assertThat(item.tipoEvento()).isEqualTo("MODERACAO_SOLICITAR_AJUSTE");
        assertThat(item.status()).isEqualTo("PENDENTE");
        assertThat(item.envioExternoExecutado()).isFalse();
        assertThat(item.previa().envioExternoExecutado()).isFalse();
        assertThat(item.previa().corpoSanitizado())
                .contains("Esta visualizacao nao envia mensagens")
                .contains("o estado de entrega pertence ao registro da outbox")
                .doesNotContain("@example.invalid", "123.456.789-09", "+5511999999999");
    }

    @Test
    void detalheNuncaExpoeDadosBrutosSensiveis() {
        UUID id = UUID.randomUUID();
        OutboxEventoEntity entity = outbox(id, "REVISAO_ANUNCIO", "MODERACAO_REPROVADA", payloadSensivel());
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        var detalhe = service.detalhar(id, principal(PapelUsuario.ADMIN));

        assertThat(detalhe.somenteLeitura()).isTrue();
        assertThat(detalhe.envioExternoExecutado()).isFalse();
        assertThat(detalhe.dadosSanitizados())
                .containsKeys("anuncioId", "revisaoId", "motivoSanitizado", "jsonBrutoExposto")
                .doesNotContainKeys("bucket", "emailRealEnviado", "whatsappRealEnviado");
        String serialized = detalhe.toString();
        assertThat(serialized)
                .doesNotContain("@example.invalid", "+5511999999999", "123.456.789-09", "bucket-privado");
    }

    @Test
    void moderadorNaoConsultaDetalheForaDoEscopoDeModeracao() {
        UUID id = UUID.randomUUID();
        OutboxEventoEntity entity = outbox(id, "PAGAMENTO", "PAGAMENTO_EVENTO_LOCAL", "{}");
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.detalhar(id, principal(PapelUsuario.MODERADOR)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    private OutboxEventoEntity outbox(String aggregateTipo, String tipoEvento, String payloadJson) {
        return outbox(UUID.randomUUID(), aggregateTipo, tipoEvento, payloadJson);
    }

    private OutboxEventoEntity outbox(UUID id, String aggregateTipo, String tipoEvento, String payloadJson) {
        return OutboxEventoEntity.registrarPendente(
                id,
                aggregateTipo,
                UUID.randomUUID(),
                tipoEvento,
                payloadJson,
                tipoEvento + ":" + id,
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
                  "emailRealEnviado": false,
                  "whatsappRealEnviado": false,
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
}
