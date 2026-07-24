package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminResultadoFotoLoteItemDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ResultadoAuditoria;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminModeracaoFotosLoteAuditoriaServiceTest {

    private final AuditoriaEventoRepository repository = mock(AuditoriaEventoRepository.class);
    private final AdminModeracaoFotosLoteAuditoriaService service =
            new AdminModeracaoFotosLoteAuditoriaService(repository, new ObjectMapper());

    @Test
    void loteRegistraResultadoIndividualSanitizadoUmaUnicaVez() {
        UUID anuncioId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID atorId = UUID.randomUUID();
        var response = new AdminDecidirFotosLoteResponseDto(
                anuncioId,
                List.of(new AdminResultadoFotoLoteItemDto(
                        mediaId,
                        "EXCLUIR",
                        null,
                        "FALHA",
                        null,
                        "Nao foi possivel remover a foto do armazenamento. Tente novamente.")),
                0,
                0,
                0,
                1,
                false,
                "request-lote",
                OffsetDateTime.now(ZoneOffset.UTC));

        service.registrarLote(atorId, response);

        ArgumentCaptor<AuditoriaEventoEntity> evento =
                ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(repository).save(evento.capture());
        assertThat(evento.getValue().getAcao()).isEqualTo("MODERACAO_FOTOS_LOTE");
        assertThat(evento.getValue().getRecursoId()).isEqualTo(anuncioId);
        assertThat(evento.getValue().getRequestId()).isEqualTo("request-lote");
        assertThat(evento.getValue().getDepoisJson())
                .contains(mediaId.toString())
                .contains("\"decisao\":\"EXCLUIR\"")
                .contains("\"resultado\":\"FALHA\"")
                .contains("\"motivoSanitizado\"");
    }

    @Test
    void auditoriaExistenteNaoEhDuplicada() {
        UUID anuncioId = UUID.randomUUID();
        when(repository.existsByAcaoAndRecursoIdAndRequestId(
                "MODERACAO_FOTOS_LOTE",
                anuncioId,
                "request-retry")).thenReturn(true);
        var response = new AdminDecidirFotosLoteResponseDto(
                anuncioId,
                List.of(),
                0,
                0,
                1,
                0,
                true,
                "request-retry",
                OffsetDateTime.now(ZoneOffset.UTC));

        service.registrarLote(UUID.randomUUID(), response);

        verify(repository, never()).save(any());
    }

    @Test
    void falhaIndividualEhAuditadaComoErroSemDetalheDeStorage() {
        UUID mediaId = UUID.randomUUID();

        service.registrarFalha(
                UUID.randomUUID(),
                mediaId,
                "request-falha",
                "FALHA_OPERACIONAL_R2");

        ArgumentCaptor<AuditoriaEventoEntity> evento =
                ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(repository).save(evento.capture());
        assertThat(evento.getValue().getResultado()).isEqualTo(ResultadoAuditoria.ERRO);
        assertThat(evento.getValue().getDepoisJson())
                .contains("FALHA_OPERACIONAL_R2")
                .contains("\"storageOculto\":true")
                .doesNotContain("bucket")
                .doesNotContain("objectKey");
    }
}
