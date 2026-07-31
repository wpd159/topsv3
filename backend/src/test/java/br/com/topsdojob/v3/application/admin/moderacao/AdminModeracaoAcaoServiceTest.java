package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminReclassificarMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.premium.BeneficioFotosExtrasModeracaoService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AnuncioBloqueioJuridicoRepository bloqueioJuridicoRepository =
            mock(AnuncioBloqueioJuridicoRepository.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
    private final DecisaoModeracaoRepository decisaoRepository = mock(DecisaoModeracaoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final OutboxEventoRepository outboxRepository = mock(OutboxEventoRepository.class);
    private final MidiaStorageAprovacaoService storageAprovacaoService = mock(MidiaStorageAprovacaoService.class);
    private final BeneficioFotosExtrasModeracaoService fotosExtrasService =
            mock(BeneficioFotosExtrasModeracaoService.class);
    private final Map<UUID, UUID> anuncioIdPorMidia = new HashMap<>();
    private AdminModeracaoAcaoService service;

    @BeforeEach
    void setUp() {
        anuncioIdPorMidia.clear();
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
                storageAprovacaoService,
                fotosExtrasService,
                "https://v3.example.invalid");
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void fotoAprovadaExigeVisibilidadeExplicita() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        assertThatThrownBy(() -> decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("visibilidade obrigatoria");
    }

    @Test
    void fotoPodeSerLivreOuRestritaIndividualmente() {
        Fixture livre = fixture(TipoAnuncioMidia.FOTO, null);
        var responseLivre = decidir(livre.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null);

        Fixture restrita = fixture(TipoAnuncioMidia.FOTO, null);
        var responseRestrita = decidir(restrita.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null);

        assertThat(responseLivre.visibilidadeMidia()).isEqualTo("LIVRE");
        assertThat(responseRestrita.visibilidadeMidia()).isEqualTo("RESTRITA_18");
        assertThat(livre.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.LIVRE);
        assertThat(restrita.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
        verify(fotosExtrasService, org.mockito.Mockito.times(2))
                .iniciarSeCapacidadeAdicionalAprovada(any(), any(), any(), any(), any());
    }

    @Test
    void fotoLivreDescartaObservacaoResidual() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        decidir(
                fixture.id(),
                AdminDecisaoModeracaoAcao.APROVAR,
                VisibilidadeMidia.LIVRE,
                null,
                "observacao residual restrita");

        ArgumentCaptor<AuditoriaEventoEntity> auditoria = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(auditoria.capture());
        assertThat(auditoria.getValue().getDepoisJson())
                .contains("\"motivoSanitizado\":null")
                .doesNotContain("observacao residual restrita");
    }

    @Test
    void fotoRestritaPreservaObservacaoInformada() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        decidir(
                fixture.id(),
                AdminDecisaoModeracaoAcao.APROVAR,
                VisibilidadeMidia.RESTRITA_18,
                null,
                "conteudo sensivel confirmado");

        ArgumentCaptor<AuditoriaEventoEntity> auditoria = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(auditoria.capture());
        assertThat(auditoria.getValue().getDepoisJson())
                .contains("\"motivoSanitizado\":\"conteudo sensivel confirmado\"");
    }

    @Test
    void videoNuncaAceitaLivre() {
        Fixture fixture = fixture(TipoAnuncioMidia.VIDEO, null);

        assertThatThrownBy(() -> decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        assertThat(decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, null, null).visibilidadeMidia())
                .isEqualTo("RESTRITA_18");
        verify(storageAprovacaoService, never()).prepararAprovacao(any(), any());
    }

    @Test
    void storyNaoParticipaDaModeracao() {
        Fixture fixture = fixture(TipoAnuncioMidia.STORY, null);

        assertThatThrownBy(() -> decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("story nao participa");

        verify(arquivoRepository, never()).findByIdForUpdate(any());
        verify(storageAprovacaoService, never()).prepararAprovacao(any(), any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void solicitarAjusteMantemArquivoPendenteERegistraStatusReal() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        decidir(fixture.id(), AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE, null, "ajustar enquadramento");

        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.AJUSTE_SOLICITADO);
        assertThat(fixture.arquivo().getStatusArquivo()).isEqualTo(StatusArquivoMidia.PENDENTE);
    }

    @Test
    void alterarUmaMidiaNaoAlteraOutra() {
        Fixture primeira = fixture(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE);
        AnuncioMidiaEntity segunda = entity(AnuncioMidiaEntity.class);
        ReflectionTestUtils.setField(segunda, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);

        decidir(primeira.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null);

        assertThat(primeira.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
        assertThat(segunda.getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
    }

    @Test
    void rejeicaoExigeMotivoENaoExecutaPromocaoOuExclusao() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        assertThatThrownBy(() -> decidir(fixture.id(), AdminDecisaoModeracaoAcao.REPROVAR, null, " "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("motivo obrigatorio");

        var response = decidir(fixture.id(), AdminDecisaoModeracaoAcao.REPROVAR, null, "conteudo incompativel");

        assertThat(response.hardDeleteExecutado()).isFalse();
        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.REJEITADA);
        assertThat(fixture.arquivo().getStatusArquivo()).isEqualTo(StatusArquivoMidia.REJEITADO);
        verify(storageAprovacaoService, never()).prepararAprovacao(any(), any());
        verify(fotosExtrasService, never())
                .iniciarSeCapacidadeAdicionalAprovada(any(), any(), any(), any(), any());
        verify(auditoriaRepository).save(any());
    }

    @Test
    void videoRejeitadoPermaneceRestritoSemOperacaoDeStorage() {
        Fixture fixture = fixture(TipoAnuncioMidia.VIDEO, null);

        var response = decidir(fixture.id(), AdminDecisaoModeracaoAcao.REPROVAR, null, "video incompativel");

        assertThat(response.visibilidadeMidia()).isEqualTo("RESTRITA_18");
        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.REJEITADA);
        assertThat(fixture.arquivo().getStatusArquivo()).isEqualTo(StatusArquivoMidia.REJEITADO);
        verify(storageAprovacaoService, never()).prepararAprovacao(any(), any());
    }

    @Test
    void midiaInexistenteRetorna404() {
        UUID id = UUID.randomUUID();
        when(midiaRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> decidir(id, AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void decisaoRepetidaRetornaConflito() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);
        decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null);

        assertThatThrownBy(() -> decidir(
                fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("ja finalizada");
    }

    @Test
    void fotoR2LivreExigeDerivadoMarcadoDoPipeline() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);
        ReflectionTestUtils.setField(fixture.arquivo(), "storageProvider", "R2");

        assertThatThrownBy(() -> decidir(
                fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("derivado marcado");

        fixture.arquivo().registrarProcessamento(
                1,
                "watermark-v1",
                OffsetDateTime.parse("2026-07-22T12:00:00Z"),
                "a".repeat(64));
        decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null);

        verify(storageAprovacaoService).prepararAprovacao(fixture.arquivo(), VisibilidadeMidia.LIVRE);
    }

    @Test
    void fotoImportadaPendenteLivreUsaProvenienciaEChecksumDoStaging() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);
        String sha256 = "c".repeat(64);
        ReflectionTestUtils.setField(fixture.arquivo(), "storageProvider", "R2");
        ReflectionTestUtils.setField(fixture.arquivo(), "sha256", sha256);
        when(midiaRepository.existsFotoImportadaProcessadaComChecksum(fixture.id(), sha256)).thenReturn(true);

        var response = decidir(
                fixture.id(),
                AdminDecisaoModeracaoAcao.APROVAR,
                VisibilidadeMidia.LIVRE,
                null);

        assertThat(response.visibilidadeMidia()).isEqualTo("LIVRE");
        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
        assertThat(fixture.arquivo().getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
        verify(storageAprovacaoService).prepararAprovacao(fixture.arquivo(), VisibilidadeMidia.LIVRE);
    }

    @Test
    void fotoImportadaPendenteRestritaGeraPreviewSemMoverOriginalPrivado() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);
        ReflectionTestUtils.setField(fixture.arquivo(), "storageProvider", "R2");

        var response = decidir(
                fixture.id(),
                AdminDecisaoModeracaoAcao.APROVAR,
                VisibilidadeMidia.RESTRITA_18,
                null);

        assertThat(response.visibilidadeMidia()).isEqualTo("RESTRITA_18");
        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
        assertThat(fixture.arquivo().getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
        verify(storageAprovacaoService)
                .prepararAprovacao(fixture.arquivo(), VisibilidadeMidia.RESTRITA_18);
    }

    @Test
    void classificacaoImportadaJaAplicadaPodeSerAprovadaQuandoDecisaoPermanecePendente() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18);
        ReflectionTestUtils.setField(fixture.arquivo(), "storageProvider", "R2");

        decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null);

        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
        assertThat(fixture.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
    }

    @Test
    void midiaDeOutroAnuncioRetornaConflitoSemAlterarArquivo() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        assertThatThrownBy(() -> service.decidirMidia(
                fixture.id(),
                new AdminDecidirMidiaRequestDto(
                        UUID.randomUUID(),
                        AdminDecisaoModeracaoAcao.APROVAR,
                        VisibilidadeMidia.RESTRITA_18,
                        null,
                        null,
                        null),
                principal(),
                "req-outro-anuncio"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("nao pertence");

        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.PENDENTE);
        assertThat(fixture.arquivo().getStatusArquivo()).isEqualTo(StatusArquivoMidia.PENDENTE);
        verify(arquivoRepository, never()).findByIdForUpdate(fixture.midia().getArquivoMidiaId());
    }

    @Test
    void anuncioBloqueadoImpedeAprovacaoDaFoto() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);
        ReflectionTestUtils.setField(fixture.anuncio(), "status", StatusAnuncio.BLOQUEADO);

        assertThatThrownBy(() -> decidir(
                fixture.id(),
                AdminDecisaoModeracaoAcao.APROVAR,
                VisibilidadeMidia.RESTRITA_18,
                null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("estado do anuncio");

        verify(arquivoRepository, never()).findByIdForUpdate(fixture.midia().getArquivoMidiaId());
    }

    @Test
    void fotoFinalizadaPodeSerReclassificadaSemCriarOutraMidiaLogica() {
        Fixture fixture = fixtureFinalizada(VisibilidadeMidia.LIVRE);

        var response = reclassificar(fixture.id(), VisibilidadeMidia.RESTRITA_18, "classificacao administrativa");

        assertThat(response.visibilidadeMidia()).isEqualTo("RESTRITA_18");
        assertThat(response.auditoriaRegistrada()).isTrue();
        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.PUBLICAVEL);
        assertThat(fixture.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
        verify(storageAprovacaoService).prepararReclassificacao(
                fixture.arquivo(), VisibilidadeMidia.LIVRE, VisibilidadeMidia.RESTRITA_18);
        verify(auditoriaRepository).save(any());
    }

    @Test
    void promocaoDeFotoFinalizadaExigeDerivadoMarcado() {
        Fixture fixture = fixtureFinalizada(VisibilidadeMidia.RESTRITA_18);

        assertThatThrownBy(() -> reclassificar(fixture.id(), VisibilidadeMidia.LIVRE, "liberacao administrativa"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("derivado marcado");

        fixture.arquivo().registrarProcessamento(
                1,
                "watermark-v1",
                OffsetDateTime.parse("2026-07-22T12:00:00Z"),
                "b".repeat(64));
        reclassificar(fixture.id(), VisibilidadeMidia.LIVRE, "liberacao administrativa");

        verify(storageAprovacaoService).prepararReclassificacao(
                fixture.arquivo(), VisibilidadeMidia.RESTRITA_18, VisibilidadeMidia.LIVRE);
    }

    @Test
    void retryDoMesmoEstadoEhIdempotenteSemNovaAuditoriaOuStorage() {
        Fixture fixture = fixtureFinalizada(VisibilidadeMidia.LIVRE);

        var response = reclassificar(fixture.id(), VisibilidadeMidia.LIVRE, "retry da classificacao");

        assertThat(response.auditoriaRegistrada()).isFalse();
        assertThat(response.mensagem()).contains("ja estava aplicada");
        verify(storageAprovacaoService, never()).prepararReclassificacao(any(), any(), any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void videoNuncaParticipaDaReclassificacao() {
        Fixture fixture = fixture(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.RESTRITA_18);
        ReflectionTestUtils.setField(fixture.midia(), "status", StatusAnuncioMidia.PUBLICAVEL);

        assertThatThrownBy(() -> reclassificar(fixture.id(), VisibilidadeMidia.LIVRE, "tentativa invalida"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("video exige");
    }

    private br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto decidir(
            UUID id,
            AdminDecisaoModeracaoAcao decisao,
            VisibilidadeMidia visibilidade,
            String motivo) {
        return decidir(id, decisao, visibilidade, motivo, null);
    }

    private br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto decidir(
            UUID id,
            AdminDecisaoModeracaoAcao decisao,
            VisibilidadeMidia visibilidade,
            String motivo,
            String observacao) {
        return service.decidirMidia(
                id,
                new AdminDecidirMidiaRequestDto(
                        anuncioIdPorMidia.get(id),
                        decisao,
                        visibilidade,
                        motivo,
                        observacao,
                        null),
                principal(),
                "req-local-123456");
    }

    private br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto reclassificar(
            UUID id,
            VisibilidadeMidia visibilidade,
            String motivo) {
        return service.reclassificarMidia(
                id,
                new AdminReclassificarMidiaRequestDto(visibilidade, motivo),
                principal(),
                "req-reclassificar-123");
    }

    private Fixture fixture(TipoAnuncioMidia tipo, VisibilidadeMidia visibilidade) {
        UUID id = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        AnuncioMidiaEntity midia = entity(AnuncioMidiaEntity.class);
        ReflectionTestUtils.setField(midia, "id", id);
        ReflectionTestUtils.setField(midia, "anuncioId", anuncioId);
        ReflectionTestUtils.setField(midia, "arquivoMidiaId", arquivoId);
        ReflectionTestUtils.setField(midia, "tipo", tipo);
        ReflectionTestUtils.setField(midia, "status", StatusAnuncioMidia.PENDENTE);
        ReflectionTestUtils.setField(midia, "visibilidadeMidia", visibilidade);
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        ReflectionTestUtils.setField(arquivo, "id", arquivoId);
        ReflectionTestUtils.setField(arquivo, "statusArquivo", StatusArquivoMidia.PENDENTE);
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        ReflectionTestUtils.setField(anuncio, "id", anuncioId);
        ReflectionTestUtils.setField(anuncio, "status", StatusAnuncio.PENDENTE_REVISAO);
        anuncioIdPorMidia.put(id, anuncioId);
        when(midiaRepository.findByIdForUpdate(id)).thenReturn(Optional.of(midia));
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(arquivoRepository.findByIdForUpdate(arquivoId)).thenReturn(Optional.of(arquivo));
        when(documentoRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivoId)).thenReturn(false);
        return new Fixture(id, midia, arquivo, anuncio);
    }

    private Fixture fixtureFinalizada(VisibilidadeMidia visibilidade) {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, visibilidade);
        ReflectionTestUtils.setField(fixture.midia(), "status", StatusAnuncioMidia.PUBLICAVEL);
        ReflectionTestUtils.setField(fixture.arquivo(), "statusArquivo", StatusArquivoMidia.VALIDADO);
        ReflectionTestUtils.setField(fixture.arquivo(), "storageProvider", "R2");
        return fixture;
    }

    private AdminUserPrincipal principal() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin Local",
                "admin.local@example.invalid",
                "hash-local",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MIDIA_REVISAR")),
                true);
    }

    private <T> T entity(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
            UUID id,
            AnuncioMidiaEntity midia,
            ArquivoMidiaEntity arquivo,
            AnuncioEntity anuncio) {
    }
}
