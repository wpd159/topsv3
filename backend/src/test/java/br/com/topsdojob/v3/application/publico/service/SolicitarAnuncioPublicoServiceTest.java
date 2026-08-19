package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusPublicacaoBusca;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
            objectMapper);

    @BeforeEach
    void setUp() {
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuarioAutenticado);
        when(usuarioAutenticado.getId()).thenReturn(usuarioId);
        when(usuarioAutenticado.getTelefoneNormalizado()).thenReturn("+5562999999999");
    }

    @Test
    void payloadValidoCriaAnuncioNaoPublicoComRevisaoAberta() {
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
        assertThat(response.statusAnuncio()).isEqualTo("PENDENTE_REVISAO");
        assertThat(response.statusModeracao()).isEqualTo("PENDENTE");
        assertThat(response.uploadRealExecutado()).isFalse();
        assertThat(response.pagamentoCriado()).isFalse();
        assertThat(response.creditoCriado()).isFalse();
        assertThat(response.premiumObrigatorio()).isFalse();

        ArgumentCaptor<AnuncioEntity> anuncio = ArgumentCaptor.forClass(AnuncioEntity.class);
        ArgumentCaptor<AnuncioLocalizacaoEntity> localizacao = ArgumentCaptor.forClass(AnuncioLocalizacaoEntity.class);
        ArgumentCaptor<DocumentoBuscaAnuncioEntity> documentoBusca = ArgumentCaptor.forClass(DocumentoBuscaAnuncioEntity.class);
        ArgumentCaptor<RevisaoAnuncioEntity> revisao = ArgumentCaptor.forClass(RevisaoAnuncioEntity.class);
        verify(anuncioRepository).save(anuncio.capture());
        verify(localizacaoRepository).save(localizacao.capture());
        verify(documentoBuscaRepository).save(documentoBusca.capture());
        verify(revisaoRepository).save(revisao.capture());

        assertThat(anuncio.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(anuncio.getValue().getWhatsappNormalizado()).isEqualTo("+5562999999999");
        assertThat(anuncio.getValue().getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getValue().getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.PENDENTE);
        assertThat(anuncio.getValue().getPublicadoEm()).isNull();
        assertThat(localizacao.getValue().getEstadoId()).isEqualTo(estadoId);
        assertThat(localizacao.getValue().getEnderecoResumido()).isNull();
        assertThat(documentoBusca.getValue().getStatusPublicacao()).isEqualTo(StatusPublicacaoBusca.NAO_PUBLICAVEL);
        assertThat(documentoBusca.getValue().getTemMidiaValida()).isFalse();
        assertThat(revisao.getValue().getStatus()).isEqualTo(StatusRevisaoAnuncio.ABERTA);
        assertThat(revisao.getValue().getPayloadSolicitado())
                .contains("ANUNCIE_GRATIS_LOCAL")
                .contains("\"pagamentoCriado\":false")
                .doesNotContain("+5562999999999")
                .doesNotContain("example.invalid");
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
