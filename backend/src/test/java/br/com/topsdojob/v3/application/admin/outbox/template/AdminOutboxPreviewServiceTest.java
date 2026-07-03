package br.com.topsdojob.v3.application.admin.outbox.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminOutboxPreviewServiceTest {

    private final OutboxEventoRepository repository = mock(OutboxEventoRepository.class);
    private final AdminOutboxTemplateCatalog catalog = new AdminOutboxTemplateCatalog();
    private final AdminOutboxPreviewService service = new AdminOutboxPreviewService(
            repository,
            new ObjectMapper(),
            new AdminOutboxTemplateRenderer(catalog));

    @Test
    void catalogoContemTemplatesLocaisDeModeracao() {
        assertThat(catalog.todos())
                .extracting(AdminOutboxTemplateDto::tipo)
                .contains(
                        AdminOutboxTemplateTipo.MODERACAO_SOLICITAR_AJUSTE,
                        AdminOutboxTemplateTipo.MODERACAO_REPROVADA,
                        AdminOutboxTemplateTipo.ANUNCIO_REMETIDO_REVISAO,
                        AdminOutboxTemplateTipo.MODERACAO_MIDIA_REPROVADA,
                        AdminOutboxTemplateTipo.GENERICO_OUTBOX_MODERACAO);
    }

    @Test
    void adminAcessaPreviewSanitizadaSemAlterarOutbox() {
        UUID id = UUID.randomUUID();
        OutboxEventoEntity entity = outbox(id, "MODERACAO_SOLICITAR_AJUSTE", payloadSensivel());
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        var preview = service.preview(id, principal(PapelUsuario.ADMIN));

        assertThat(preview.id()).isEqualTo(id);
        assertThat(preview.tipoEvento()).isEqualTo("MODERACAO_SOLICITAR_AJUSTE");
        assertThat(preview.status()).isEqualTo("PENDENTE");
        assertThat(preview.envioExternoExecutado()).isFalse();
        assertThat(preview.somentePreview()).isTrue();
        assertThat(preview.canalPrevisto()).isEqualTo("CANAL_LOCAL_PREVIEW");
        assertThat(preview.camposMascarados())
                .contains("email", "telefone_ou_whatsapp", "cpf_ou_documento", "storage_bucket_hash", "segredo", "pix_financeiro");
        assertThat(preview.assuntoSanitizado()).contains("[anuncio]");
        assertThat(preview.corpoSanitizado())
                .contains("nenhuma comunicacao foi enviada")
                .contains("[email-mascarado]", "[contato-mascarado]", "[documento-mascarado]")
                .doesNotContain(
                        "ana@example.invalid",
                        "+5511999999999",
                        "123.456.789-09",
                        "bucket-privado",
                        "marcador-ficticio",
                        "Pix copia",
                        "<script>");
        assertThat(entity.getStatus().name()).isEqualTo("PENDENTE");
        assertThat(entity.getProcessadoEm()).isNull();
        verify(repository, never()).save(any(OutboxEventoEntity.class));
    }

    @Test
    void moderadorAcessaPreviewDeModeracao() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(outbox(id, "MODERACAO_REPROVADA", payloadSensivel())));

        var preview = service.preview(id, principal(PapelUsuario.MODERADOR));

        assertThat(preview.somentePreview()).isTrue();
        assertThat(preview.envioExternoExecutado()).isFalse();
    }

    @Test
    void moderadorNaoAcessaPreviewForaDeModeracao() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(outbox(id, "PAGAMENTO_EVENTO_LOCAL", "{}")));

        assertThatThrownBy(() -> service.preview(id, principal(PapelUsuario.MODERADOR)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void comercialEUsuarioNaoAcessamPreview() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(outbox(id, "MODERACAO_REPROVADA", payloadSensivel())));

        assertThatThrownBy(() -> service.preview(id, principal(PapelUsuario.COMERCIAL)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThatThrownBy(() -> service.preview(id, principal(PapelUsuario.USUARIO)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void semSessaoRetorna401() {
        assertThatThrownBy(() -> service.preview(UUID.randomUUID(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void outboxInexistenteRetorna404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(id, principal(PapelUsuario.ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private OutboxEventoEntity outbox(UUID id, String tipoEvento, String payloadJson) {
        return OutboxEventoEntity.registrarPendente(
                id,
                "REVISAO_ANUNCIO",
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
                  "motivoSanitizado": "ajuste local ana@example.invalid +5511999999999 123.456.789-09 <script>x</script>",
                  "emailReal": "ana@example.invalid",
                  "whatsappReal": "+5511999999999",
                  "documento": "123.456.789-09",
                  "bucket": "bucket-privado",
                  "hash": "sha256-privado",
                  "credencialPrivada": "marcador-ficticio",
                  "pix": "Pix copia e cola local",
                  "valor": "1000"
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
