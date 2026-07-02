package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminRemeterRevisaoRequestDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.DecisaoModeracaoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DecisaoModeracao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminModeracaoAcaoServiceTest {

    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioMidiaRepository anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoMidiaRepository = mock(ArquivoMidiaRepository.class);
    private final DocumentoUsuarioRepository documentoUsuarioRepository = mock(DocumentoUsuarioRepository.class);
    private final DecisaoModeracaoRepository decisaoRepository = mock(DecisaoModeracaoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final OutboxEventoRepository outboxRepository = mock(OutboxEventoRepository.class);
    private AdminModeracaoAcaoService service;

    @BeforeEach
    void setUp() {
        service = new AdminModeracaoAcaoService(
                revisaoRepository,
                anuncioRepository,
                anuncioMidiaRepository,
                arquivoMidiaRepository,
                documentoUsuarioRepository,
                decisaoRepository,
                auditoriaRepository,
                outboxRepository,
                new ObjectMapper());
    }

    @Test
    void revisaoAbertaPodeSerAprovadaComAuditoria() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.ABERTA);
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId)).thenReturn(false);

        var response = service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(AdminDecisaoModeracaoAcao.APROVAR, ClassificacaoConteudo.LIVRE, "ok", null, null),
                principal(),
                "req-local-123456");

        assertThat(response.status()).isEqualTo("APROVADA");
        assertThat(revisao.getStatus()).isEqualTo(StatusRevisaoAnuncio.APROVADA);
        assertThat(revisao.getFinalizadoEm()).isNotNull();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.APROVADO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        verify(decisaoRepository).save(any(DecisaoModeracaoEntity.class));
        verify(auditoriaRepository).save(any(AuditoriaEventoEntity.class));
    }

    @Test
    void revisaoAbertaPodeSerReprovadaComoBloqueada() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        String emailSintetico = "ana" + "@example.invalid";
        String telefoneSintetico = "+" + "5511999999999";
        String cpfSintetico = "123" + ".456" + ".789" + "-09";
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.EM_ANALISE);
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));

        var response = service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(
                        AdminDecisaoModeracaoAcao.REPROVAR,
                        ClassificacaoConteudo.BLOQUEADO,
                        "bloquear " + emailSintetico,
                        telefoneSintetico + " " + cpfSintetico,
                        null),
                principal(),
                "req-local-123456");

        assertThat(response.status()).isEqualTo("REJEITADA");
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.REJEITADO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.BLOQUEADO);
        assertThat(anuncio.getClassificacaoConteudo()).isEqualTo(ClassificacaoConteudo.BLOQUEADO);
        ArgumentCaptor<AuditoriaEventoEntity> auditoria = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(auditoria.capture());
        assertThat(auditoria.getValue().getDepoisJson())
                .contains("[email-mascarado]", "[contato-mascarado]", "[documento-mascarado]")
                .doesNotContain(emailSintetico, telefoneSintetico, cpfSintetico);
    }

    @Test
    void reprovarRevisaoSemMotivoRetornaBadRequest() {
        assertThatThrownBy(() -> service.decidirRevisao(
                UUID.randomUUID(),
                new AdminDecidirRevisaoRequestDto(AdminDecisaoModeracaoAcao.REPROVAR, ClassificacaoConteudo.BLOQUEADO, "   ", null, null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("motivo obrigatorio para reprovar");
    }

    @Test
    void revisaoAbertaPodeSolicitarAjusteComOutboxLocal() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        String emailSintetico = "ajuste" + "@example.invalid";
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.ABERTA);
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId)).thenReturn(false);
        when(outboxRepository.existsByTipoEventoAndIdempotencyKeyAndStatus(
                "MODERACAO_SOLICITAR_AJUSTE",
                "MODERACAO_SOLICITAR_AJUSTE:" + revisaoId,
                StatusOutbox.PENDENTE)).thenReturn(false);
        when(outboxRepository.existsByIdempotencyKey("MODERACAO_SOLICITAR_AJUSTE:" + revisaoId)).thenReturn(false);

        var response = service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(
                        AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE,
                        ClassificacaoConteudo.LIVRE,
                        "ajustar texto " + emailSintetico,
                        null,
                        "reservado-local"),
                principal(),
                "req-local-123456");

        assertThat(response.status()).isEqualTo("ABERTA");
        assertThat(revisao.getStatus()).isEqualTo(StatusRevisaoAnuncio.ABERTA);
        assertThat(revisao.getFinalizadoEm()).isNull();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        verify(decisaoRepository, never()).save(any(DecisaoModeracaoEntity.class));
        ArgumentCaptor<OutboxEventoEntity> outbox = ArgumentCaptor.forClass(OutboxEventoEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getTipoEvento()).isEqualTo("MODERACAO_SOLICITAR_AJUSTE");
        assertThat(outbox.getValue().getPayloadJson())
                .contains("[email-mascarado]")
                .doesNotContain(emailSintetico);
        verify(auditoriaRepository).save(any(AuditoriaEventoEntity.class));
    }

    @Test
    void solicitarAjusteNaoImpedeAprovacaoPosteriorDaMesmaRevisao() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.ABERTA);
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId)).thenReturn(false);

        service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(
                        AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE,
                        ClassificacaoConteudo.LIVRE,
                        "ajuste intermediario",
                        null,
                        null),
                principal(),
                "req-local-123456");

        verify(decisaoRepository, never()).save(any(DecisaoModeracaoEntity.class));

        var response = service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(AdminDecisaoModeracaoAcao.APROVAR, ClassificacaoConteudo.LIVRE, "ok", null, null),
                principal(),
                "req-local-123456");

        assertThat(response.status()).isEqualTo("APROVADA");
        assertThat(revisao.getStatus()).isEqualTo(StatusRevisaoAnuncio.APROVADA);
        assertThat(revisao.getFinalizadoEm()).isNotNull();
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.APROVADO);
        ArgumentCaptor<DecisaoModeracaoEntity> decisao = ArgumentCaptor.forClass(DecisaoModeracaoEntity.class);
        verify(decisaoRepository).save(decisao.capture());
        assertThat(decisao.getValue().getDecisao()).isEqualTo(DecisaoModeracao.APROVAR);
    }

    @Test
    void solicitarAjusteNaoImpedeReprovacaoPosteriorDaMesmaRevisao() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.ABERTA);
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId)).thenReturn(false);

        service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(
                        AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE,
                        ClassificacaoConteudo.LIVRE,
                        "ajuste intermediario",
                        null,
                        null),
                principal(),
                "req-local-123456");

        verify(decisaoRepository, never()).save(any(DecisaoModeracaoEntity.class));

        var response = service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(
                        AdminDecisaoModeracaoAcao.REPROVAR,
                        ClassificacaoConteudo.BLOQUEADO,
                        "motivo de reprovacao final",
                        null,
                        null),
                principal(),
                "req-local-123456");

        assertThat(response.status()).isEqualTo("REJEITADA");
        assertThat(revisao.getStatus()).isEqualTo(StatusRevisaoAnuncio.REJEITADA);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.REJEITADO);
        ArgumentCaptor<DecisaoModeracaoEntity> decisao = ArgumentCaptor.forClass(DecisaoModeracaoEntity.class);
        verify(decisaoRepository).save(decisao.capture());
        assertThat(decisao.getValue().getDecisao()).isEqualTo(DecisaoModeracao.REJEITAR);
    }

    @Test
    void solicitarAjusteComDecisaoFinalAnteriorRetornaConflito() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.ABERTA);
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId)).thenReturn(true);

        assertThatThrownBy(() -> service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(
                        AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE,
                        ClassificacaoConteudo.LIVRE,
                        "ajuste local",
                        null,
                        null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void solicitarAjusteDuplicadoRetornaConflitoSemNovoOutbox() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.ABERTA);
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId)).thenReturn(false);
        when(outboxRepository.existsByTipoEventoAndIdempotencyKeyAndStatus(
                "MODERACAO_SOLICITAR_AJUSTE",
                "MODERACAO_SOLICITAR_AJUSTE:" + revisaoId,
                StatusOutbox.PENDENTE)).thenReturn(true);

        assertThatThrownBy(() -> service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(
                        AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE,
                        ClassificacaoConteudo.LIVRE,
                        "ajuste local",
                        null,
                        null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(decisaoRepository, never()).save(any(DecisaoModeracaoEntity.class));
        verify(outboxRepository, never()).save(any(OutboxEventoEntity.class));
    }

    @Test
    void solicitarAjusteSemMotivoRetornaBadRequest() {
        assertThatThrownBy(() -> service.decidirRevisao(
                UUID.randomUUID(),
                new AdminDecidirRevisaoRequestDto(AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE, ClassificacaoConteudo.LIVRE, " ", null, null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("motivo obrigatorio");
    }

    @Test
    void revisaoFinalizadaRetornaConflito() {
        UUID revisaoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        RevisaoAnuncioEntity revisao = revisao(revisaoId, anuncioId, StatusRevisaoAnuncio.APROVADA);
        ReflectionTestUtils.setField(revisao, "finalizadoEm", OffsetDateTime.now());
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));

        assertThatThrownBy(() -> service.decidirRevisao(
                revisaoId,
                new AdminDecidirRevisaoRequestDto(AdminDecisaoModeracaoAcao.APROVAR, ClassificacaoConteudo.LIVRE, null, null, null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void midiaPendentePodeSerAprovada() {
        UUID midiaId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(midiaId, arquivoId, StatusAnuncioMidia.PENDENTE, ClassificacaoConteudo.LIVRE);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId, StatusArquivoMidia.PENDENTE, ClassificacaoConteudo.LIVRE);
        when(anuncioMidiaRepository.findById(midiaId)).thenReturn(Optional.of(midia));
        when(arquivoMidiaRepository.findById(arquivoId)).thenReturn(Optional.of(arquivo));
        when(documentoUsuarioRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivoId)).thenReturn(false);

        var response = service.decidirMidia(
                midiaId,
                new AdminDecidirMidiaRequestDto(AdminDecisaoModeracaoAcao.APROVAR, ClassificacaoConteudo.LIVRE, "ok", null, null),
                principal(),
                "req-local-123456");

        assertThat(response.status()).isEqualTo("PUBLICAVEL");
        assertThat(midia.getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
        assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
        verify(auditoriaRepository).save(any(AuditoriaEventoEntity.class));
    }

    @Test
    void midiaFinalizadaRetornaConflito() {
        UUID midiaId = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        when(anuncioMidiaRepository.findById(midiaId))
                .thenReturn(Optional.of(midia(midiaId, arquivoId, StatusAnuncioMidia.PUBLICAVEL, ClassificacaoConteudo.LIVRE)));

        assertThatThrownBy(() -> service.decidirMidia(
                midiaId,
                new AdminDecidirMidiaRequestDto(AdminDecisaoModeracaoAcao.REPROVAR, ClassificacaoConteudo.BLOQUEADO, "motivo local", null, null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void reprovarMidiaSemMotivoRetornaBadRequest() {
        assertThatThrownBy(() -> service.decidirMidia(
                UUID.randomUUID(),
                new AdminDecidirMidiaRequestDto(AdminDecisaoModeracaoAcao.REPROVAR, ClassificacaoConteudo.BLOQUEADO, null, "   ", null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("motivo obrigatorio para reprovar");
    }

    @Test
    void solicitarAjusteMidiaPermanecePendenteSemStatusCompativel() {
        assertThatThrownBy(() -> service.decidirMidia(
                UUID.randomUUID(),
                new AdminDecidirMidiaRequestDto(
                        AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE,
                        ClassificacaoConteudo.LIVRE,
                        "ajuste local",
                        null,
                        null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("sem status compativel");
    }

    @Test
    void decisaoInvalidaRetornaBadRequest() {
        assertThatThrownBy(() -> service.decidirRevisao(
                UUID.randomUUID(),
                new AdminDecidirRevisaoRequestDto(null, null, null, null, null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void remeterAnuncioParaRevisaoCriaRevisaoAuditoriaEOutbox() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.APROVADO, StatusModeracaoAnuncio.APROVADO, ClassificacaoConteudo.LIVRE);
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(revisaoRepository.existsByAnuncioIdAndStatusIn(anuncioId, List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE)))
                .thenReturn(false);

        var response = service.remeterAnuncioParaRevisao(
                anuncioId,
                new AdminRemeterRevisaoRequestDto("revisar dados sinteticos", null, "reservado-local"),
                principal(),
                "req-local-123456");

        assertThat(response.decisao()).isEqualTo("REMETER_REVISAO");
        assertThat(response.status()).isEqualTo("PENDENTE_REVISAO");
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.PENDENTE);
        ArgumentCaptor<RevisaoAnuncioEntity> revisao = ArgumentCaptor.forClass(RevisaoAnuncioEntity.class);
        verify(revisaoRepository).save(revisao.capture());
        assertThat(revisao.getValue().getTipo()).isEqualTo(TipoRevisaoAnuncio.EDICAO);
        assertThat(revisao.getValue().getStatus()).isEqualTo(StatusRevisaoAnuncio.ABERTA);
        verify(outboxRepository).save(any(OutboxEventoEntity.class));
        verify(auditoriaRepository).save(any(AuditoriaEventoEntity.class));
    }

    @Test
    void remeterAnuncioComRevisaoAbertaRetornaConflito() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.APROVADO, StatusModeracaoAnuncio.APROVADO, ClassificacaoConteudo.LIVRE);
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(revisaoRepository.existsByAnuncioIdAndStatusIn(anuncioId, List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.remeterAnuncioParaRevisao(
                anuncioId,
                new AdminRemeterRevisaoRequestDto("revisar", null, null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void remeterAnuncioSemMotivoRetornaBadRequest() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId, StatusAnuncio.APROVADO, StatusModeracaoAnuncio.APROVADO, ClassificacaoConteudo.LIVRE);
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> service.remeterAnuncioParaRevisao(
                anuncioId,
                new AdminRemeterRevisaoRequestDto("   ", "observacao nao substitui motivo", null),
                principal(),
                "req-local-123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("motivo obrigatorio para remeter revisao");
    }

    private RevisaoAnuncioEntity revisao(UUID id, UUID anuncioId, StatusRevisaoAnuncio status) {
        RevisaoAnuncioEntity entity = instantiate(RevisaoAnuncioEntity.class);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "anuncioId", anuncioId);
        ReflectionTestUtils.setField(entity, "status", status);
        ReflectionTestUtils.setField(entity, "criadoEm", OffsetDateTime.now().minusMinutes(5));
        return entity;
    }

    private AnuncioEntity anuncio(
            UUID id,
            StatusAnuncio status,
            StatusModeracaoAnuncio statusModeracao,
            ClassificacaoConteudo classificacao) {
        AnuncioEntity entity = instantiate(AnuncioEntity.class);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "status", status);
        ReflectionTestUtils.setField(entity, "statusModeracao", statusModeracao);
        ReflectionTestUtils.setField(entity, "classificacaoConteudo", classificacao);
        return entity;
    }

    private AnuncioMidiaEntity midia(
            UUID id,
            UUID arquivoId,
            StatusAnuncioMidia status,
            ClassificacaoConteudo classificacao) {
        AnuncioMidiaEntity entity = instantiate(AnuncioMidiaEntity.class);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "anuncioId", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "arquivoMidiaId", arquivoId);
        ReflectionTestUtils.setField(entity, "tipo", TipoAnuncioMidia.FOTO);
        ReflectionTestUtils.setField(entity, "status", status);
        ReflectionTestUtils.setField(entity, "classificacaoConteudo", classificacao);
        return entity;
    }

    private ArquivoMidiaEntity arquivo(
            UUID id,
            StatusArquivoMidia status,
            ClassificacaoConteudo classificacao) {
        ArquivoMidiaEntity entity = instantiate(ArquivoMidiaEntity.class);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "statusArquivo", status);
        ReflectionTestUtils.setField(entity, "classificacaoConteudo", classificacao);
        return entity;
    }

    private AdminUserPrincipal principal() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin Local",
                "admin.local@example.invalid",
                "hash-local",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ANUNCIO_MODERAR")),
                true);
    }

    private <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("falha ao instanciar entidade de teste", exception);
        }
    }
}
