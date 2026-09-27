package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.DocumentoBuscaAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.wizard.WizardProgressJdbcRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusPublicacaoBusca;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class SolicitarAnuncioPublicoServiceTest {

    private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
    private final KycPublicoService kycService = mock(KycPublicoService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final UUID usuarioId = UUID.randomUUID();
    private final UsuarioEntity usuarioAutenticado = mock(UsuarioEntity.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
    private final DocumentoBuscaAnuncioRepository documentoBuscaRepository = mock(DocumentoBuscaAnuncioRepository.class);
    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final EstadoRepository estadoRepository = mock(EstadoRepository.class);
    private final CidadeRepository cidadeRepository = mock(CidadeRepository.class);
    private final BairroRepository bairroRepository = mock(BairroRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WizardProgressJdbcRepository wizardRepository = mock(WizardProgressJdbcRepository.class);
    private final SolicitarAnuncioPublicoService service = new SolicitarAnuncioPublicoService(
            usuarioService,
            kycService,
            anuncioRepository,
            localizacaoRepository,
            documentoBuscaRepository,
            revisaoRepository,
            estadoRepository,
            cidadeRepository,
            bairroRepository,
            objectMapper,
            mock(AnuncioMidiaRepository.class), wizardRepository);

    @BeforeEach
    void setUp() {
        when(usuarioService.usuarioAutenticadoParaAtualizacao(authentication)).thenReturn(usuarioAutenticado);
        when(usuarioAutenticado.getId()).thenReturn(usuarioId);
        when(usuarioAutenticado.getTelefoneNormalizado()).thenReturn("+5562999999999");
    }

    @Test
    void sessaoNovaEhVinculadaDepoisDoFlushSobLockDoProprietario() {
        mockLocalidadeValida();
        when(wizardRepository.sincronizar(any(), eq("wizard-create-new"), eq(usuarioId), any(),
                eq("CREATE"), eq("FOTOS"), eq(3), eq("EM_PREENCHIMENTO"), any()))
                .thenReturn(new WizardProgressJdbcRepository.SyncRow(UUID.randomUUID(), "CREATE",
                        "EM_PREENCHIMENTO", "FOTOS", OffsetDateTime.now()));

        var response = service.solicitar(validPayload(), authentication, "wizard-create-new");

        assertThat(response.criado()).isTrue();
        InOrder order = Mockito.inOrder(usuarioService, wizardRepository, anuncioRepository);
        order.verify(usuarioService).usuarioAutenticadoParaAtualizacao(authentication);
        order.verify(wizardRepository).findSessaoPorUsuario(usuarioId, "wizard-create-new");
        order.verify(anuncioRepository).save(any(AnuncioEntity.class));
        order.verify(anuncioRepository).flush();
        order.verify(wizardRepository).sincronizar(any(), eq("wizard-create-new"), eq(usuarioId),
                eq(response.anuncioId()), eq("CREATE"), eq("FOTOS"), eq(3), eq("EM_PREENCHIMENTO"), any());
    }

    @Test
    void replayRecuperaIdentidadeEEstadoAtualSemRevalidarPayloadNemCriarEfeitos() {
        AnuncioEntity existente = anuncioDaSessao(StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE);

        var response = service.solicitar(null, authentication, "wizard-create-replay");

        assertThat(response.criado()).isFalse();
        assertThat(response.anuncioId()).isEqualTo(existente.getId());
        assertThat(response.slugLocal()).isEqualTo(existente.getSlug());
        assertThat(response.statusAnuncio()).isEqualTo("PENDENTE_REVISAO");
        assertThat(response.statusModeracao()).isEqualTo("PENDENTE");
        assertThat(response.revisaoCriada()).isFalse();
        verifyNoInteractions(kycService, localizacaoRepository, documentoBuscaRepository, revisaoRepository);
        verify(anuncioRepository, never()).save(any());
        verify(wizardRepository, never()).sincronizar(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void sessaoEditMesmoSemAnuncioNaoAutorizaCriacao() {
        when(wizardRepository.findSessaoPorUsuario(usuarioId, "wizard-session-edit"))
                .thenReturn(Optional.of(new WizardProgressJdbcRepository.SessionRow("EDIT", null)));
        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication, "wizard-session-edit"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
        verifyNoInteractions(anuncioRepository, kycService, revisaoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REMOVIDO", "BLOQUEADO"})
    void sessaoDeAnuncioEncerradoOuBloqueadoNuncaCriaSubstituto(String status) {
        anuncioDaSessao(StatusAnuncio.valueOf(status), StatusModeracaoAnuncio.PENDENTE);
        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication, "wizard-create-replay"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
        verify(anuncioRepository, never()).save(any());
        verifyNoInteractions(kycService, revisaoRepository);
    }

    @Test
    void vinculoDeOutroProprietarioEhRecusadoSemCriacao() {
        AnuncioEntity anuncio = anuncioDaSessao(StatusAnuncio.RASCUNHO, StatusModeracaoAnuncio.NAO_ENVIADO);
        when(anuncio.getUsuarioId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication, "wizard-create-replay"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        verify(anuncioRepository, never()).save(any());
    }

    @Test
    void vinculoAusenteNoBancoNuncaEhTratadoComoNovaCriacao() {
        when(wizardRepository.findSessaoPorUsuario(usuarioId, "wizard-create-missing"))
                .thenReturn(Optional.of(new WizardProgressJdbcRepository.SessionRow("CREATE", UUID.randomUUID())));
        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication, "wizard-create-missing"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
        verify(anuncioRepository, never()).save(any());
        verifyNoInteractions(kycService, revisaoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "wizard/session-invalid"})
    void headerPresenteInvalidoNaoCaiNoFluxoLegado(String sessionId) {
        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication, sessionId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(wizardRepository, anuncioRepository, kycService);
    }

    private AnuncioEntity anuncioDaSessao(StatusAnuncio status, StatusModeracaoAnuncio moderation) {
        UUID id = UUID.randomUUID();
        AnuncioEntity anuncio = mock(AnuncioEntity.class);
        when(anuncio.getId()).thenReturn(id);
        when(anuncio.getUsuarioId()).thenReturn(usuarioId);
        when(anuncio.getSlug()).thenReturn("anuncio-sintetico-da-sessao");
        when(anuncio.getStatus()).thenReturn(status);
        when(anuncio.getStatusModeracao()).thenReturn(moderation);
        when(wizardRepository.findSessaoPorUsuario(usuarioId, "wizard-create-replay"))
                .thenReturn(Optional.of(new WizardProgressJdbcRepository.SessionRow("CREATE", id)));
        when(anuncioRepository.findByIdForModeration(id)).thenReturn(Optional.of(anuncio));
        return anuncio;
    }

    @Test
    void payloadValidoCriaRascunhoSemRevisaoAntesDoUpload() {
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID bairroId = UUID.randomUUID();
        EstadoEntity estado = mock(EstadoEntity.class);
        CidadeEntity cidade = mock(CidadeEntity.class);
        BairroEntity bairro = mock(BairroEntity.class);
        when(estado.getId()).thenReturn(estadoId);
        when(cidade.getId()).thenReturn(cidadeId);
        when(bairro.getId()).thenReturn(bairroId);
        when(anuncioRepository.existsBySlug(anyString())).thenReturn(false);
        when(estadoRepository.findByUfIgnoreCase("ZZ")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "cidade-sintetica")).thenReturn(Optional.of(cidade));
        when(bairroRepository.findByCidadeIdAndSlug(cidadeId, "bairro-sintetico")).thenReturn(Optional.of(bairro));

        var response = service.solicitar(validPayload(), authentication);

        assertThat(response.criado()).isTrue();
        assertThat(response.publicado()).isFalse();
        assertThat(response.statusAnuncio()).isEqualTo("RASCUNHO");
        assertThat(response.statusModeracao()).isEqualTo("NAO_ENVIADO");
        assertThat(response.revisaoCriada()).isFalse();
        assertThat(response.revisaoId()).isNull();
        assertThat(response.uploadRealExecutado()).isFalse();
        assertThat(response.pagamentoCriado()).isFalse();
        assertThat(response.creditoCriado()).isFalse();
        assertThat(response.premiumObrigatorio()).isFalse();

        InOrder ordem = Mockito.inOrder(usuarioService, kycService, anuncioRepository);
        ordem.verify(usuarioService).usuarioAutenticadoParaAtualizacao(authentication);
        ordem.verify(kycService).garantirProntoParaAnuncio(usuarioId);
        ordem.verify(anuncioRepository).save(any(AnuncioEntity.class));
        verify(usuarioService, never()).usuarioAutenticado(authentication);

        ArgumentCaptor<AnuncioEntity> anuncio = ArgumentCaptor.forClass(AnuncioEntity.class);
        ArgumentCaptor<AnuncioLocalizacaoEntity> localizacao = ArgumentCaptor.forClass(AnuncioLocalizacaoEntity.class);
        ArgumentCaptor<DocumentoBuscaAnuncioEntity> documentoBusca = ArgumentCaptor.forClass(DocumentoBuscaAnuncioEntity.class);
        verify(anuncioRepository).save(anuncio.capture());
        verify(localizacaoRepository).save(localizacao.capture());
        verify(documentoBuscaRepository).save(documentoBusca.capture());
        verifyNoInteractions(revisaoRepository);

        assertThat(anuncio.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(anuncio.getValue().getWhatsappNormalizado()).isEqualTo("+5562999999999");
        assertThat(anuncio.getValue().getStatus()).isEqualTo(StatusAnuncio.RASCUNHO);
        assertThat(anuncio.getValue().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.NAO_ENVIADO);
        assertThat(anuncio.getValue().getPublicadoEm()).isNull();
        assertThat(localizacao.getValue().getEstadoId()).isEqualTo(estadoId);
        assertThat(localizacao.getValue().getEnderecoResumido()).isNull();
        assertThat(documentoBusca.getValue().getStatusPublicacao()).isEqualTo(StatusPublicacaoBusca.NAO_PUBLICAVEL);
        assertThat(documentoBusca.getValue().getTemMidiaValida()).isFalse();
    }

    @Test
    void complementoPublicoRealEhPersistidoSemMarcadorTecnico() {
        mockLocalidadeValida();
        ObjectNode payload = validPayload();
        payload.put("enderecoResumido", "  Proximo   a recepcao  ");

        service.solicitar(payload, authentication);

        ArgumentCaptor<AnuncioLocalizacaoEntity> localizacao =
                ArgumentCaptor.forClass(AnuncioLocalizacaoEntity.class);
        ArgumentCaptor<DocumentoBuscaAnuncioEntity> documentoBusca =
                ArgumentCaptor.forClass(DocumentoBuscaAnuncioEntity.class);
        verify(localizacaoRepository).save(localizacao.capture());
        verify(documentoBuscaRepository).save(documentoBusca.capture());
        assertThat(localizacao.getValue().getEnderecoResumido()).isEqualTo("Proximo a recepcao");
        assertThat(documentoBusca.getValue().getTextoBusca()).contains("proximo a recepcao");
    }

    @Test
    void complementoMaiorQueLimiteEhRejeitadoSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("enderecoResumido", "x".repeat(121));

        assertValidationCode(payload, "TAMANHO_INVALIDO");
    }

    @Test
    void complementoComHtmlOuJavascriptEhRejeitadoSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("enderecoResumido", "<script>alert('x')</script> Recepcao");

        assertValidationCode(payload, "CONTEUDO_NAO_PERMITIDO");

        payload.put("enderecoResumido", "javascript:alert('x')");
        assertValidationCode(payload, "CONTEUDO_NAO_PERMITIDO");
    }

    @Test
    void sexoVirtualAdicionalPreservaCategoriaBaseEUmUnicoAnuncio() {
        mockLocalidadeValida();
        ObjectNode payload = validPayload();
        payload.putArray("servicos").add("VIDEOCHAMADA");
        payload.put("atendimentoExclusivamenteVirtual", false);

        service.solicitar(payload, authentication);

        ArgumentCaptor<AnuncioEntity> anuncio = ArgumentCaptor.forClass(AnuncioEntity.class);
        verify(anuncioRepository).save(anuncio.capture());
        assertThat(anuncio.getValue().getCategoria()).isEqualTo("ACOMPANHANTE_FEMININA");
        assertThat(anuncio.getValue().getServicos()).containsExactly(ServicoAnuncio.VIDEOCHAMADA);
        assertThat(anuncio.getValue().isAtendimentoExclusivamenteVirtual()).isFalse();
    }

    @Test
    void clienteLegadoComCategoriaVirtualENormalizadoSemExclusividade() {
        mockLocalidadeValida();
        ObjectNode payload = validPayload();
        payload.put("categoria", "VENDA_DE_CONTEUDO");

        service.solicitar(payload, authentication);

        ArgumentCaptor<AnuncioEntity> anuncio = ArgumentCaptor.forClass(AnuncioEntity.class);
        verify(anuncioRepository).save(anuncio.capture());
        assertThat(anuncio.getValue().getCategoria()).isEqualTo("ACOMPANHANTE_FEMININA");
        assertThat(anuncio.getValue().getServicos()).containsExactly(ServicoAnuncio.VIDEOCHAMADA);
        assertThat(anuncio.getValue().isAtendimentoExclusivamenteVirtual()).isFalse();
    }

    @Test
    void exclusividadeSemSexoVirtualRetornaErroSemPersistir() {
        mockLocalidadeValida();
        ObjectNode payload = validPayload();
        payload.put("atendimentoExclusivamenteVirtual", true);

        assertValidationCode(payload, "EXCLUSIVIDADE_VIRTUAL_INVALIDA");
    }

    @Test
    void aceiteTermosAusenteRetornaErroSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("aceiteTermos", false);

        assertValidationCode(payload, "ACEITE_TERMOS_OBRIGATORIO");
    }

    @Test
    void precoZeroRetornaErroSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("preco", 0);

        assertValidationCode(payload, "PRECO_INVALIDO");
    }

    @Test
    void telefoneNoTituloRetornaErroSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("titulo", "Contato +5511999999999 agora");

        assertValidationCode(payload, "TITULO_CONTATO_OU_REDE_SOCIAL");
    }

    @Test
    void redeSocialNoTituloRetornaErroSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("titulo", "Perfil instagram local");

        assertValidationCode(payload, "TITULO_CONTATO_OU_REDE_SOCIAL");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Atendimento <script>alert('x')</script>",
            "Atendimento seguro </script>",
            "Atendimento <b>especial</b>",
            "Atendimento javascript:alert('x')"
    })
    void conteudoAtivoNoTituloRetornaErroEspecificoSemPersistir(String titulo) {
        ObjectNode payload = validPayload();
        payload.put("titulo", titulo);

        assertThatThrownBy(() -> service.solicitar(payload, authentication))
                .isInstanceOfSatisfying(SolicitarAnuncioValidationException.class, exception ->
                        assertThat(exception.errors()).anySatisfy(error -> {
                            assertThat(error.campo()).isEqualTo("titulo");
                            assertThat(error.codigo()).isEqualTo("CONTEUDO_NAO_PERMITIDO");
                            assertThat(error.mensagem())
                                    .isEqualTo("titulo nao pode conter HTML ou JavaScript");
                        }));
        verify(anuncioRepository, never()).save(any());
        verify(localizacaoRepository, never()).save(any());
        verify(documentoBuscaRepository, never()).save(any());
        verify(revisaoRepository, never()).save(any());
    }

    @Test
    void cidadeAusenteRetornaErroSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("cidade", "");

        assertValidationCode(payload, "CAMPO_OBRIGATORIO");
    }

    @Test
    void campoPerigosoRetornaErroSemPersistir() {
        ObjectNode payload = validPayload();
        payload.put("pagamentoId", UUID.randomUUID().toString());

        assertValidationCode(payload, "CAMPO_PERIGOSO");
    }

    @Test
    void whatsappEnviadoPeloClienteEhRecusadoEOCanonicoVemDaConta() {
        ObjectNode payload = validPayload();
        payload.put("whatsapp", "+5511999999999");

        assertValidationCode(payload, "CAMPO_NAO_PERMITIDO");
    }

    @Test
    void telefoneAusenteNaContaDirecionaCorrecaoParaMinhaConta() {
        when(usuarioAutenticado.getTelefoneNormalizado()).thenReturn(null);

        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication))
                .isInstanceOfSatisfying(SolicitarAnuncioValidationException.class, exception ->
                        assertThat(exception.errors()).anySatisfy(error -> {
                            assertThat(error.campo()).isEqualTo("telefone");
                            assertThat(error.codigo()).isEqualTo("TELEFONE_DA_CONTA_OBRIGATORIO");
                            assertThat(error.mensagem()).contains("Minha Conta");
                        }));
        verify(anuncioRepository, never()).save(any());
    }

    @Test
    void kycAusenteBloqueiaCriacaoAntesDePersistir() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "KYC pendente"))
                .when(kycService).garantirProntoParaAnuncio(usuarioId);

        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(anuncioRepository, never()).save(any());
    }

    @Test
    void falhaAoTravarContaImpedeValidacaoKycECriacao() {
        when(usuarioService.usuarioAutenticadoParaAtualizacao(authentication))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "conta indisponivel"));

        assertThatThrownBy(() -> service.solicitar(validPayload(), authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(kycService, anuncioRepository, localizacaoRepository,
                documentoBuscaRepository, revisaoRepository);
    }

    private void assertValidationCode(ObjectNode payload, String code) {
        assertThatThrownBy(() -> service.solicitar(payload, authentication))
                .isInstanceOfSatisfying(SolicitarAnuncioValidationException.class, exception ->
                        assertThat(exception.errors()).anyMatch(error -> code.equals(error.codigo())));
        verify(anuncioRepository, never()).save(any());
        verify(revisaoRepository, never()).save(any());
    }

    private ObjectNode validPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("uf", "ZZ");
        payload.put("cidade", "Cidade Sintetica");
        payload.put("bairro", "Bairro Sintetico");
        payload.put("titulo", "Anuncio sintetico para revisao");
        payload.put("descricao", "Texto sintetico neutro para validar criacao local sem dado real.");
        payload.put("preco", new BigDecimal("120.00"));
        payload.put("categoria", "ACOMPANHANTE_FEMININA");
        payload.put("linkConteudo", "https://example.invalid/conteudo");
        payload.put("aceiteTermos", true);
        payload.put("confirmacaoIdade", true);
        return payload;
    }

    private void mockLocalidadeValida() {
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        EstadoEntity estado = mock(EstadoEntity.class);
        CidadeEntity cidade = mock(CidadeEntity.class);
        BairroEntity bairro = mock(BairroEntity.class);
        when(estado.getId()).thenReturn(estadoId);
        when(cidade.getId()).thenReturn(cidadeId);
        when(anuncioRepository.existsBySlug(anyString())).thenReturn(false);
        when(estadoRepository.findByUfIgnoreCase("ZZ")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "cidade-sintetica"))
                .thenReturn(Optional.of(cidade));
        when(bairroRepository.findByCidadeIdAndSlug(cidadeId, "bairro-sintetico"))
                .thenReturn(Optional.of(bairro));
    }
}
