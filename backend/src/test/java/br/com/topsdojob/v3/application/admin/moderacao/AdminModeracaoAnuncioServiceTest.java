package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.admin.premium.BeneficioFotosExtrasModeracaoService;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioBloqueioJuridicoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ParteDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminModeracaoAnuncioServiceTest {

    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository =
            mock(AnuncioBloqueioJuridicoRepository.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
    private final DecisaoModeracaoRepository decisaoRepository = mock(DecisaoModeracaoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final OutboxEventoRepository outboxRepository = mock(OutboxEventoRepository.class);
    private final MidiaStorageAprovacaoService storageService = mock(MidiaStorageAprovacaoService.class);
    private final BeneficioFotosExtrasModeracaoService fotosExtrasService =
            mock(BeneficioFotosExtrasModeracaoService.class);
    private final FotoElegivelAnuncioPolicy fotoElegivelAnuncioPolicy =
            mock(FotoElegivelAnuncioPolicy.class);
    private AdminModeracaoAcaoService service;

    @BeforeEach
    void setUp() {
        service = new AdminModeracaoAcaoService(
                revisaoRepository,
                anuncioRepository,
                usuarioRepository,
                bloqueioJuridicoRepository,
                midiaRepository,
                arquivoRepository,
                documentoRepository,
                decisaoRepository,
                auditoriaRepository,
                outboxRepository,
                new ObjectMapper(),
                storageService,
                fotosExtrasService,
                fotoElegivelAnuncioPolicy,
                "https://v3.example.invalid");
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aprovaEPublicaAnuncioSemAlterarMidiasERegistraAtorRequestId() {
        Fixture fixture = fixtureComKycValidado();

        var response = decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null);

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(fixture.anuncio().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(fixture.anuncio().getPublicadoEm()).isNotNull();
        assertThat(fixture.anuncio().getUltimaPublicacaoEm()).isEqualTo(fixture.anuncio().getPublicadoEm());
        assertThat(fixture.revisao().getStatus()).isEqualTo(StatusRevisaoAnuncio.APROVADA);
        assertThat(response.mensagem()).isEqualTo("anuncio aprovado e publicado");
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
    void guardDeFotosFalhaAntesDeQualquerMutacaoDaRevisaoOuAnuncio() {
        Fixture fixture = fixtureComKycValidado();
        doThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                FotoElegivelAnuncioPolicy.MENSAGEM_FOTO_AGUARDANDO_DECISAO))
                .when(fotoElegivelAnuncioPolicy)
                .validarParaAprovacao(fixture.anuncio().getId());

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining(FotoElegivelAnuncioPolicy.MENSAGEM_FOTO_AGUARDANDO_DECISAO);

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(fixture.revisao().getStatus()).isEqualTo(StatusRevisaoAnuncio.ABERTA);
        verify(decisaoRepository, never()).save(any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void operacaoUnicaAprovaRevisaoAbertaEPublicaNaMesmaChamada() {
        Fixture fixture = fixtureComKycValidado();
        when(revisaoRepository.findFirstByAnuncioIdAndStatusInOrderByCriadoEmDesc(
                fixture.anuncio().getId(),
                List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE)))
                .thenReturn(Optional.of(fixture.revisao()));

        var response = service.aprovarEPublicarAnuncio(
                fixture.anuncio().getId(),
                fixture.actor(),
                "req-operacao-unica");

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(fixture.anuncio().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(fixture.revisao().getStatus()).isEqualTo(StatusRevisaoAnuncio.APROVADA);
        assertThat(response.mensagem()).isEqualTo("anuncio aprovado e publicado");
        verify(decisaoRepository).save(any());
        verify(auditoriaRepository).save(any());
        verifyNoInteractions(midiaRepository, arquivoRepository, storageService);
    }

    @Test
    void operacaoUnicaCriaEFinalizaRevisaoSemEstadoIntermediarioExterno() {
        Fixture fixture = fixtureComKycValidado();
        when(revisaoRepository.findFirstByAnuncioIdAndStatusInOrderByCriadoEmDesc(
                fixture.anuncio().getId(),
                List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE)))
                .thenReturn(Optional.empty());
        when(revisaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.aprovarEPublicarAnuncio(
                fixture.anuncio().getId(),
                fixture.actor(),
                "req-operacao-unica-sem-revisao");

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(fixture.anuncio().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(response.mensagem()).isEqualTo("anuncio aprovado e publicado");
        ArgumentCaptor<RevisaoAnuncioEntity> revisaoCriada =
                ArgumentCaptor.forClass(RevisaoAnuncioEntity.class);
        verify(revisaoRepository).save(revisaoCriada.capture());
        assertThat(revisaoCriada.getValue().getStatus()).isEqualTo(StatusRevisaoAnuncio.APROVADA);
        assertThat(revisaoCriada.getValue().getFinalizadoEm()).isNotNull();
    }

    @Test
    void retryDaOperacaoUnicaNaoDuplicaRevisaoDecisaoOuAuditoria() {
        Fixture fixture = fixtureComKycValidado();
        when(revisaoRepository.findFirstByAnuncioIdAndStatusInOrderByCriadoEmDesc(
                fixture.anuncio().getId(),
                List.of(StatusRevisaoAnuncio.ABERTA, StatusRevisaoAnuncio.EM_ANALISE)))
                .thenReturn(Optional.of(fixture.revisao()));

        service.aprovarEPublicarAnuncio(
                fixture.anuncio().getId(),
                fixture.actor(),
                "req-operacao-unica");
        var retry = service.aprovarEPublicarAnuncio(
                fixture.anuncio().getId(),
                fixture.actor(),
                "req-operacao-unica");

        assertThat(retry.auditoriaRegistrada()).isFalse();
        assertThat(retry.mensagem()).contains("ja estava aprovado e publicado");
        verify(decisaoRepository, times(1)).save(any());
        verify(auditoriaRepository, times(1)).save(any());
        verify(fotoElegivelAnuncioPolicy, times(2))
                .validarParaAprovacao(fixture.anuncio().getId());
        verify(documentoRepository, times(1))
                .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(
                        fixture.usuario().getId());
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
    void reprovacaoRegistraUmaNotificacaoIdempotenteComMotivoTituloELinkSanitizados() {
        Fixture fixture = fixture();
        String motivo = """
                Corrigir a descricao <script>alert('x')</script>, completar o documento ausente
                e remover o contato ana@example.invalid +5511999999999 123.456.789-09.
                """;

        var response = decidir(fixture, AdminDecisaoModeracaoAcao.REPROVAR, motivo);

        assertThat(response.mensagem()).contains("notificacao registrada na outbox");
        ArgumentCaptor<OutboxEventoEntity> outbox = ArgumentCaptor.forClass(OutboxEventoEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getTipoEvento()).isEqualTo("MODERACAO_REPROVADA");
        assertThat(outbox.getValue().getIdempotencyKey())
                .isEqualTo("MODERACAO_REPROVADA:" + fixture.revisao().getId());
        assertThat(outbox.getValue().getPayloadJson())
                .contains(
                        "\"communicationVersion\":1",
                        "\"anuncioTitulo\":\"Anúncio sintético\"",
                        "\"destinatarioUsuarioId\":\"" + fixture.usuario().getId() + "\"",
                        "\"destinatarioLogico\":\"ANUNCIANTE_VINCULADA_AO_ANUNCIO\"",
                        "\"linkEdicao\":\"https://v3.example.invalid/meus-anuncios/anuncio-moderacao-teste/editar\"",
                        "[email-mascarado]",
                        "[contato-mascarado]",
                        "[documento-mascarado]")
                .doesNotContain("<script>", "ana@example.invalid", "+5511999999999", "123.456.789-09");

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.REPROVAR, motivo))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(outboxRepository, times(1)).save(any());
    }

    @Test
    void estadoAprovadoOuProprietarioBloqueadoNaoReprovaNemCriaNotificacao() {
        Fixture aprovado = fixture();
        aprovado.anuncio().aplicarModeracao(
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                OffsetDateTime.parse("2026-07-22T12:05:00Z"));

        assertThatThrownBy(() -> decidir(aprovado, AdminDecisaoModeracaoAcao.REPROVAR, "corrigir dados"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("estado do anuncio");

        Fixture bloqueado = fixture();
        bloqueado.usuario().bloquearJuridicamente(OffsetDateTime.parse("2026-07-22T12:05:00Z"));
        assertThatThrownBy(() -> decidir(bloqueado, AdminDecisaoModeracaoAcao.REPROVAR, "corrigir dados"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("estado do proprietario");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void aprovacaoRepetidaEhIdempotenteSemNovaDecisaoOuAuditoria() {
        Fixture fixture = fixtureComKycValidado();
        var primeira = decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null);
        OffsetDateTime primeiraPublicacao = fixture.anuncio().getUltimaPublicacaoEm();

        var segunda = decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null);

        assertThat(primeira.auditoriaRegistrada()).isTrue();
        assertThat(segunda.auditoriaRegistrada()).isFalse();
        assertThat(segunda.mensagem()).contains("ja estava aprovado e publicado");
        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(fixture.anuncio().getUltimaPublicacaoEm()).isEqualTo(primeiraPublicacao);
        verify(decisaoRepository, times(1)).save(any());
        verify(auditoriaRepository, times(1)).save(any());
        verify(fotoElegivelAnuncioPolicy, times(2))
                .validarParaAprovacao(fixture.anuncio().getId());
        verify(documentoRepository, times(1))
                .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(
                        fixture.usuario().getId());
    }

    @Test
    void anuncioElegivelSemEnvioDocumentalRetornaConflitoKyc() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("KYC ainda nao foi enviada");

        verify(decisaoRepository, never()).save(any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void documentoKycReprovadoNaoPermiteNovaAprovacao() {
        Fixture fixture = fixture();
        configurarKyc(fixture, StatusDocumentoUsuario.REJEITADO);

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("KYC ainda nao foi aprovada");

        verify(decisaoRepository, never()).save(any());
        verify(auditoriaRepository, never()).save(any());
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

    @Test
    void anuncioBloqueadoNaoPodeSerAprovado() {
        Fixture fixture = fixture();
        fixture.anuncio().bloquearJuridicamente(OffsetDateTime.parse("2026-07-22T12:05:00Z"));

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("estado do anuncio impede");

        verify(decisaoRepository, never()).save(any());
        verifyNoInteractions(documentoRepository);
        verifyNoInteractions(midiaRepository, arquivoRepository, storageService);
    }

    @Test
    void anuncioRemovidoNaoPodeSerAprovado() {
        Fixture fixture = fixture();
        fixture.anuncio().removerLogicamente(OffsetDateTime.parse("2026-07-22T12:05:00Z"));

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("estado do anuncio impede");

        verify(decisaoRepository, never()).save(any());
        verifyNoInteractions(documentoRepository);
        verifyNoInteractions(midiaRepository, arquivoRepository, storageService);
    }

    @Test
    void telaAntigaNaoAprovaDepoisQueUsuarioFoiSuspenso() {
        Fixture fixture = fixture();
        fixture.usuario().bloquearJuridicamente(OffsetDateTime.parse("2026-07-22T12:05:00Z"));

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("proprietario esta suspenso");

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(fixture.revisao().getStatus()).isEqualTo(StatusRevisaoAnuncio.ABERTA);
        verify(decisaoRepository, never()).save(any());
        verify(anuncioRepository, never()).save(any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void bloqueioJuridicoAtivoImpedeAprovacaoEPublicacao() {
        Fixture fixture = fixture();
        when(bloqueioJuridicoRepository.findAtivoPorAnuncioForUpdate(fixture.anuncio().getId()))
                .thenReturn(Optional.of(mock(AnuncioBloqueioJuridicoEntity.class)));

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("bloqueio juridico");

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        verify(decisaoRepository, never()).save(any());
    }

    @Test
    void anuncioRejeitadoSemNovaRevisaoValidaNaoPodeSerAprovado() {
        Fixture fixture = fixture();
        fixture.anuncio().aplicarModeracao(
                StatusAnuncio.REJEITADO,
                StatusModeracaoAnuncio.REJEITADO,
                OffsetDateTime.parse("2026-07-22T12:05:00Z"));

        assertThatThrownBy(() -> decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("estado do anuncio");

        verify(decisaoRepository, never()).save(any());
        verifyNoInteractions(documentoRepository);
    }

    @Test
    void regularizaAprovacaoLegadaSemDuplicarDecisao() {
        Fixture fixture = fixture();
        org.springframework.test.util.ReflectionTestUtils.setField(
                fixture.anuncio(), "status", StatusAnuncio.APROVADO);
        org.springframework.test.util.ReflectionTestUtils.setField(
                fixture.anuncio(), "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        fixture.revisao().finalizar(
                StatusRevisaoAnuncio.APROVADA,
                OffsetDateTime.parse("2026-07-22T12:05:00Z"));
        fixture.decisaoRegistrada().set(true);

        var response = decidir(fixture, AdminDecisaoModeracaoAcao.APROVAR, null);

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(fixture.anuncio().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(fixture.anuncio().getPublicadoEm()).isNotNull();
        assertThat(response.auditoriaRegistrada()).isTrue();
        assertThat(response.mensagem()).contains("publicado agora");
        verify(fotoElegivelAnuncioPolicy).validarParaAprovacao(fixture.anuncio().getId());
        verify(decisaoRepository, never()).save(any());
        verifyNoInteractions(documentoRepository);
        ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertThat(audit.getValue().getAcao())
                .isEqualTo("MODERACAO_REVISAO_PUBLICACAO_REGULARIZAR");
    }

    @Test
    void operacaoUnicaRegularizaLegadoMesmoSemRevisaoAberta() {
        Fixture fixture = fixture();
        org.springframework.test.util.ReflectionTestUtils.setField(
                fixture.anuncio(), "status", StatusAnuncio.APROVADO);
        org.springframework.test.util.ReflectionTestUtils.setField(
                fixture.anuncio(), "statusModeracao", StatusModeracaoAnuncio.APROVADO);

        var response = service.aprovarEPublicarAnuncio(
                fixture.anuncio().getId(),
                fixture.actor(),
                "req-regularizacao-operacao-unica");

        assertThat(fixture.anuncio().getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(fixture.anuncio().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(response.mensagem()).contains("publicado agora");
        verify(fotoElegivelAnuncioPolicy).validarParaAprovacao(fixture.anuncio().getId());
        verify(decisaoRepository, never()).save(any());
        verify(revisaoRepository, never()).save(any());
        ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertThat(audit.getValue().getAcao())
                .isEqualTo("MODERACAO_ANUNCIO_PUBLICACAO_REGULARIZAR");
    }

    @Test
    void dominioRecusaCriarNovoEstadoAprovadoSemPublicacao() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.anuncio().aplicarModeracao(
                StatusAnuncio.APROVADO,
                StatusModeracaoAnuncio.APROVADO,
                OffsetDateTime.parse("2026-07-22T12:05:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estado legado invalido");
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

    private Fixture fixtureComKycValidado() {
        Fixture fixture = fixture();
        configurarKyc(fixture, StatusDocumentoUsuario.VALIDADO);
        return fixture;
    }

    private void configurarKyc(Fixture fixture, StatusDocumentoUsuario status) {
        UUID envioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.parse("2026-07-22T12:01:00Z");
        DocumentoUsuarioEntity documento = DocumentoUsuarioEntity.criarPendente(
                UUID.randomUUID(),
                fixture.usuario().getId(),
                UUID.randomUUID(),
                envioId,
                ParteDocumentoUsuario.FRENTE,
                agora.minusMinutes(1));
        documento.aplicarDecisao(
                status,
                fixture.actor().usuarioId(),
                status == StatusDocumentoUsuario.REJEITADO ? "documento rejeitado no teste" : null,
                agora);
        when(documentoRepository
                .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(
                        fixture.usuario().getId()))
                .thenReturn(List.of(documento));
        when(documentoRepository
                .findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(envioId))
                .thenReturn(List.of(documento));
    }

    private Fixture fixture() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID revisaoId = UUID.randomUUID();
        AdminUserPrincipal actor = principal();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-22T12:00:00Z");
        UsuarioEntity usuario = UsuarioEntity.criarSolicitacaoLocal(
                usuarioId,
                "Anunciante Local",
                "anunciante.local@example.invalid",
                null,
                now);
        usuario.confirmarEmail(now);
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
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
        AtomicBoolean decisaoRegistrada = new AtomicBoolean(false);
        when(revisaoRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
        when(revisaoRepository.findByIdForUpdate(revisaoId)).thenReturn(Optional.of(revisao));
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(bloqueioJuridicoRepository.findAtivoPorAnuncioForUpdate(anuncioId))
                .thenReturn(Optional.empty());
        when(bloqueioJuridicoRepository.findAtivoPorUsuarioForUpdate(any(), any()))
                .thenReturn(Optional.empty());
        when(decisaoRepository.existsByRevisaoAnuncioId(revisaoId))
                .thenAnswer(invocation -> decisaoRegistrada.get());
        when(decisaoRepository.save(any())).thenAnswer(invocation -> {
            decisaoRegistrada.set(true);
            return invocation.getArgument(0);
        });
        return new Fixture(anuncio, usuario, revisao, actor, decisaoRegistrada);
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
            UsuarioEntity usuario,
            RevisaoAnuncioEntity revisao,
            AdminUserPrincipal actor,
            AtomicBoolean decisaoRegistrada) {
    }
}
