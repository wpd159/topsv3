package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminModeracaoAnuncioServiceTest {

    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
    private final DecisaoModeracaoRepository decisaoRepository = mock(DecisaoModeracaoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final OutboxEventoRepository outboxRepository = mock(OutboxEventoRepository.class);
    private final MidiaStorageAprovacaoService storageService = mock(MidiaStorageAprovacaoService.class);
    private AdminModeracaoAcaoService service;

    @BeforeEach
    void setUp() {
        service = new AdminModeracaoAcaoService(
                revisaoRepository,
                anuncioRepository,
                midiaRepository,
                arquivoRepository,
                documentoRepository,
                decisaoRepository,
                auditoriaRepository,
                outboxRepository,
                new ObjectMapper(),
                storageService);
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aprovaAnuncioSemAlterarMidiasERegistraAtorRequestId() {
        Fixture fixture = fixture();

        var response = decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null);

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.APROVADO);
        assertThat(fixture.anuncio().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(fixture.revisao().getStatus()).isEqualTo(StatusRevisaoAnuncio.APROVADA);
        assertThat(response.hardDeleteExecutado()).isFalse();
        verify(decisaoRepository).save(any());
        verifyNoInteractions(midiaRepository, arquivoRepository, storageService);

        ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertThat(audit.getValue().getAtorUsuarioId()).isEqualTo(fixture.actor().usuarioId());
        assertThat(audit.getValue().getRequestId()).isEqualTo("req-moderacao-anuncio");
        assertThat(audit.getValue().getAcao()).isEqualTo("MODERACAO_REVISAO_DECIDIR");
    }

    @Test
    void rejeicaoExigeMotivoENaoApagaAnuncioOuMidias() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.REPROVAR, " "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("motivo obrigatorio");

        var response = decidir(fixture, AdminDecisaoModeracaoAcao.REPROVAR, "dados comerciais incompatíveis");

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.REJEITADO);
        assertThat(fixture.anuncio().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.REJEITADO);
        assertThat(fixture.revisao().getStatus()).isEqualTo(StatusRevisaoAnuncio.REJEITADA);
        assertThat(response.hardDeleteExecutado()).isFalse();
        verify(anuncioRepository, never()).delete(any(AnuncioEntity.class));
        verifyNoInteractions(midiaRepository, arquivoRepository, storageService);
    }

    @Test
    void decisaoRepetidaRetornaConflitoSobMesmoRegistro() {
        Fixture fixture = fixture();
        decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null);

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("ja finalizada");
    }

    @Test
    void revisaoInexistenteRetorna404() {
        UUID id = UUID.randomUUID();
        when(revisaoRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decidirRevisao(
                id,
                new AdminDecidirRevisaoRequestDto(AdminDecisaoModeracaoAcao.APROVAR, null, null, null),
                principal(),
                "req-moderacao-anuncio"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void atorAusenteRetorna403AntesDeConsultarDados() {
        assertThatThrownBy(() -> service.decidirRevisao(
                UUID.randomUUID(),
                new AdminDecidirRevisaoRequestDto(AdminDecisaoModeracaoAcao.APROVAR, null, null, null),
                null,
                "req-moderacao-anuncio"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        verifyNoInteractions(revisaoRepository, anuncioRepository);
    }

    private br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto decidir(
            Fixture fixture,
            AdminDecisaoModeracaoAcao decisao,
            String motivo) {
        return service.decidirRevisao(
                fixture.revisao().getId(),
                new AdminDecidirRevisaoRequestDto(decisao, motivo, null, null),
                fixture.actor(),
                "req-moderacao-anuncio");
    }

    private Fixture fixture() {
        UUID anuncioId = UUID.randomUUID();
        UUID revisaoId = UUID.randomUUID();
        AdminUserPrincipal actor = principal();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-22T12:00:00Z");
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                UUID.randomUUID(),
                "anuncio-moderacao-teste",
                "Anúncio sintético",
                "Descrição sintética",
                "MASSAGENS",
                null,
                null,
                now);
        RevisaoAnuncioEntity revisao = RevisaoAnuncioEntity.abrir(
                revisaoId,
                anuncioId,
                TipoRevisaoAnuncio.CRIACAO,
                "{}",
                actor.usuarioId(),
                now);
        when(revisaoRepository.findByIdForUpdate(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId)).thenReturn(false);
        return new Fixture(anuncio, revisao, actor);
    }

    private AdminUserPrincipal principal() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin Local",
                "admin.local@example.invalid",
                "hash-local",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ANUNCIO_MODERAR")),
                true);
    }

    private record Fixture(
            AnuncioEntity anuncio,
            RevisaoAnuncioEntity revisao,
            AdminUserPrincipal actor) {
    }
}
