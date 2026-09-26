package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
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
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class MeuAnuncioAtualizacaoServiceTest {

    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000000701");
    private static final UUID ANUNCIO_ID = UUID.fromString("00000000-0000-4000-8000-000000000702");
    private static final UUID ESTADO_ID = UUID.fromString("00000000-0000-4000-8000-000000000703");
    private static final UUID CIDADE_ID = UUID.fromString("00000000-0000-4000-8000-000000000704");
    private static final UUID BAIRRO_ID = UUID.fromString("00000000-0000-4000-8000-000000000705");
    private static final OffsetDateTime PUBLICADO_EM =
            OffsetDateTime.of(2025, 5, 10, 12, 0, 0, 0, ZoneOffset.UTC);

    private MeusAnunciosConsultaService consultaService;
    private KycPublicoService kycService;
    private AnuncioRepository anuncioRepository;
    private AnuncioLocalizacaoRepository localizacaoRepository;
    private DocumentoBuscaAnuncioRepository documentoBuscaRepository;
    private RevisaoAnuncioRepository revisaoRepository;
    private EstadoRepository estadoRepository;
    private CidadeRepository cidadeRepository;
    private BairroRepository bairroRepository;
    private Authentication authentication;
    private MeuAnuncioAtualizacaoService service;

    @BeforeEach
    void setUp() {
        consultaService = mock(MeusAnunciosConsultaService.class);
        kycService = mock(KycPublicoService.class);
        anuncioRepository = mock(AnuncioRepository.class);
        localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        documentoBuscaRepository = mock(DocumentoBuscaAnuncioRepository.class);
        revisaoRepository = mock(RevisaoAnuncioRepository.class);
        estadoRepository = mock(EstadoRepository.class);
        cidadeRepository = mock(CidadeRepository.class);
        bairroRepository = mock(BairroRepository.class);
        authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(usuario.getTelefoneNormalizado()).thenReturn("+5562999999999");
        when(consultaService.usuarioAutenticado(authentication)).thenReturn(usuario);
        service = new MeuAnuncioAtualizacaoService(
                consultaService,
                kycService,
                anuncioRepository,
                localizacaoRepository,
                documentoBuscaRepository,
                revisaoRepository,
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                new ObjectMapper(),
                new AnuncioAtualizacaoCanonicaValidator(),
                mock(ArquivoPublicidadeRegistroService.class));
    }

    @Test
    void atualizaCamposPersistidosPreservaSlugEPublicacaoERemeteParaRevisao() {
        AnuncioEntity anuncio = anuncio();
        OffsetDateTime primeiraPublicacao = anuncio.getPublicadoEm();
        OffsetDateTime ultimaPublicacao = anuncio.getUltimaPublicacaoEm();
        EstadoEntity estado = EstadoEntity.criarFixtureHomologacao(
                ESTADO_ID, "GO", "Goias", "goias", PUBLICADO_EM);
        CidadeEntity cidade = CidadeEntity.criarFixtureHomologacao(
                CIDADE_ID, ESTADO_ID, "Goiania", "goiania", "goiania", PUBLICADO_EM);
        BairroEntity bairro = BairroEntity.criarFixtureHomologacao(
                BAIRRO_ID, CIDADE_ID, "Setor Bueno", "setor bueno", "setor-bueno", PUBLICADO_EM);
        AnuncioLocalizacaoEntity localizacao = AnuncioLocalizacaoEntity.criarSolicitacaoLocal(
                ANUNCIO_ID, ESTADO_ID, CIDADE_ID, null, PUBLICADO_EM);
        MeuAnuncioDto resposta = mock(MeuAnuncioDto.class);

        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication)).thenReturn(anuncio);
        when(estadoRepository.findByUfIgnoreCase("GO")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(ESTADO_ID, "goiania")).thenReturn(Optional.of(cidade));
        when(bairroRepository.findByCidadeIdAndSlug(CIDADE_ID, "setor-bueno")).thenReturn(Optional.of(bairro));
        when(localizacaoRepository.findByAnuncioId(ANUNCIO_ID)).thenReturn(Optional.of(localizacao));
        when(documentoBuscaRepository.findById(ANUNCIO_ID)).thenReturn(Optional.empty());
        when(revisaoRepository.findFirstByAnuncioIdAndStatusOrderByCriadoEmDesc(
                ANUNCIO_ID, StatusRevisaoAnuncio.ABERTA)).thenReturn(Optional.empty());
        when(consultaService.detalhar("slug-preservado", authentication)).thenReturn(resposta);

        MeuAnuncioDto resultado = service.atualizar(
                "slug-preservado", requestValido(), authentication);

        assertThat(resultado).isSameAs(resposta);
        assertThat(anuncio.getSlug()).isEqualTo("slug-preservado");
        assertThat(anuncio.getTitulo()).isEqualTo("Novo titulo publico");
        assertThat(anuncio.getDescricao()).isEqualTo("Descricao atualizada e suficientemente completa.");
        assertThat(anuncio.getCategoria()).isEqualTo("ACOMPANHANTE_FEMININA");
        assertThat(anuncio.getPreco()).isEqualByComparingTo("250.00");
        assertThat(anuncio.getWhatsappNormalizado()).isEqualTo("+5562999999999");
        assertThat(anuncio.getLocaisAtendimento())
                .containsExactlyInAnyOrder(LocalAtendimentoAnuncio.MEU_LOCAL, LocalAtendimentoAnuncio.HOTEL_MOTEL);
        assertThat(anuncio.getServicos()).containsExactlyInAnyOrder(ServicoAnuncio.ANAL, ServicoAnuncio.ORAL);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.PENDENTE);
        assertThat(anuncio.getPublicadoEm()).isEqualTo(primeiraPublicacao);
        assertThat(anuncio.getUltimaPublicacaoEm()).isEqualTo(ultimaPublicacao);
        assertThat(localizacao.getBairroId()).isEqualTo(BAIRRO_ID);
        assertThat(localizacao.getEnderecoResumido()).isEqualTo("Regiao central");

        ArgumentCaptor<RevisaoAnuncioEntity> revisaoCaptor = ArgumentCaptor.forClass(RevisaoAnuncioEntity.class);
        verify(revisaoRepository).save(revisaoCaptor.capture());
        assertThat(revisaoCaptor.getValue().getTipo()).isEqualTo(TipoRevisaoAnuncio.EDICAO);
        assertThat(revisaoCaptor.getValue().getCriadoPor()).isEqualTo(USUARIO_ID);
        assertThat(revisaoCaptor.getValue().getPayloadSolicitado())
                .contains("Novo titulo publico", "contatoCanonicoDaConta")
                .doesNotContain("+5562999999999");
        verify(documentoBuscaRepository).save(any(DocumentoBuscaAnuncioEntity.class));
    }

    @Test
    void payloadInvalidoRetorna400SemPersistir() {
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication)).thenReturn(anuncio());
        MeuAnuncioAtualizacaoRequestDto invalido = new MeuAnuncioAtualizacaoRequestDto(
                "curto",
                "descricao curta",
                "INVALIDA!",
                BigDecimal.ZERO,
                "G",
                "",
                null,
                null,
                List.of("FORA_DO_ENUM"),
                List.of(),
                false,
                "url-invalida");

        assertStatus(400, () -> service.atualizar("slug-preservado", invalido, authentication));

        verify(anuncioRepository, never()).save(any());
        verify(revisaoRepository, never()).save(any());
    }

    @Test
    void tituloComConteudoAtivoNaoAlteraAnuncio() {
        AnuncioEntity anuncio = anuncio();
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication)).thenReturn(anuncio);

        assertThatThrownBy(() -> service.atualizar(
                "slug-preservado",
                requestComTitulo("Atendimento <script>alert('x')</script>"),
                authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(400);
                    assertThat(exception.getReason())
                            .isEqualTo("titulo nao pode conter HTML ou JavaScript");
                });

        assertThat(anuncio.getTitulo()).isEqualTo("Titulo original");
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        verify(anuncioRepository, never()).saveAndFlush(any());
        verify(localizacaoRepository, never()).save(any());
        verify(documentoBuscaRepository, never()).save(any());
        verify(revisaoRepository, never()).save(any());
    }

    @Test
    void preservaStatus401DaSessao() {
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        assertStatus(401, () -> service.atualizar("slug-preservado", requestValido(), authentication));
    }

    @Test
    void preservaStatus403ParaAnuncioAlheio() {
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertStatus(403, () -> service.atualizar("slug-preservado", requestValido(), authentication));
    }

    @Test
    void preservaStatus404ParaSlugInexistente() {
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-inexistente", authentication))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertStatus(404, () -> service.atualizar("slug-inexistente", requestValido(), authentication));
    }

    @Test
    void atualizaRevisaoAbertaSemCriarFluxoConcorrente() {
        AnuncioEntity anuncio = anuncio();
        RevisaoAnuncioEntity revisao = RevisaoAnuncioEntity.abrir(
                UUID.randomUUID(),
                ANUNCIO_ID,
                TipoRevisaoAnuncio.CRIACAO,
                "{}",
                USUARIO_ID,
                PUBLICADO_EM);
        AnuncioLocalizacaoEntity localizacao = prepararLocalidadeSemBairro(anuncio);
        when(revisaoRepository.findFirstByAnuncioIdAndStatusOrderByCriadoEmDesc(
                ANUNCIO_ID, StatusRevisaoAnuncio.ABERTA)).thenReturn(Optional.of(revisao));
        when(consultaService.detalhar("slug-preservado", authentication)).thenReturn(mock(MeuAnuncioDto.class));

        service.atualizar("slug-preservado", requestSemBairro(), authentication);

        verify(revisaoRepository).save(revisao);
        assertThat(revisao.getTipo()).isEqualTo(TipoRevisaoAnuncio.CRIACAO);
        assertThat(revisao.getPayloadSolicitado()).contains("Novo titulo publico");
        assertThat(localizacao.getEnderecoResumido()).isNull();
    }

    @Test
    void anuncioRejeitadoCorrigidoVoltaParaPendenteComNovaRevisaoSemPublicar() {
        AnuncioEntity anuncio = anuncio();
        anuncio.aplicarModeracao(
                StatusAnuncio.REJEITADO,
                StatusModeracaoAnuncio.REJEITADO,
                PUBLICADO_EM.plusDays(1));
        prepararLocalidadeSemBairro(anuncio);
        when(revisaoRepository.findFirstByAnuncioIdAndStatusOrderByCriadoEmDesc(
                ANUNCIO_ID, StatusRevisaoAnuncio.ABERTA)).thenReturn(Optional.empty());
        when(consultaService.detalhar("slug-preservado", authentication))
                .thenReturn(mock(MeuAnuncioDto.class));

        service.atualizar("slug-preservado", requestSemBairro(), authentication);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.PENDENTE);
        ArgumentCaptor<RevisaoAnuncioEntity> revisaoCaptor =
                ArgumentCaptor.forClass(RevisaoAnuncioEntity.class);
        verify(revisaoRepository).save(revisaoCaptor.capture());
        assertThat(revisaoCaptor.getValue().getTipo()).isEqualTo(TipoRevisaoAnuncio.EDICAO);
        assertThat(revisaoCaptor.getValue().getStatus()).isEqualTo(StatusRevisaoAnuncio.ABERTA);
        verify(revisaoRepository, never()).delete(any(RevisaoAnuncioEntity.class));
    }

    @Test
    void revisaoEmAnaliseBloqueiaCorridaCom409() {
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication)).thenReturn(anuncio());
        when(revisaoRepository.existsByAnuncioIdAndStatusIn(eq(ANUNCIO_ID), any())).thenReturn(true);

        assertStatus(409, () -> service.atualizar("slug-preservado", requestValido(), authentication));

        verify(anuncioRepository, never()).save(any());
    }

    @Test
    void kycAusenteBloqueiaEdicaoAntesDePersistir() {
        AnuncioEntity anuncio = anuncio();
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication)).thenReturn(anuncio);
        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT))
                .when(kycService).garantirProntoParaAnuncio(USUARIO_ID);

        assertStatus(409, () -> service.atualizar("slug-preservado", requestValido(), authentication));

        verify(anuncioRepository, never()).save(any());
        verify(revisaoRepository, never()).save(any());
    }

    private AnuncioLocalizacaoEntity prepararLocalidadeSemBairro(AnuncioEntity anuncio) {
        EstadoEntity estado = EstadoEntity.criarFixtureHomologacao(
                ESTADO_ID, "GO", "Goias", "goias", PUBLICADO_EM);
        CidadeEntity cidade = CidadeEntity.criarFixtureHomologacao(
                CIDADE_ID, ESTADO_ID, "Goiania", "goiania", "goiania", PUBLICADO_EM);
        AnuncioLocalizacaoEntity localizacao = AnuncioLocalizacaoEntity.criarSolicitacaoLocal(
                ANUNCIO_ID,
                ESTADO_ID,
                CIDADE_ID,
                null,
                "Endereço sintético local",
                PUBLICADO_EM);
        when(consultaService.anuncioDoUsuarioParaAtualizacao("slug-preservado", authentication)).thenReturn(anuncio);
        when(estadoRepository.findByUfIgnoreCase("GO")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(ESTADO_ID, "goiania")).thenReturn(Optional.of(cidade));
        when(localizacaoRepository.findByAnuncioId(ANUNCIO_ID)).thenReturn(Optional.of(localizacao));
        when(documentoBuscaRepository.findById(ANUNCIO_ID)).thenReturn(Optional.empty());
        return localizacao;
    }

    private AnuncioEntity anuncio() {
        return AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID,
                USUARIO_ID,
                "slug-preservado",
                "Titulo original",
                "Descricao original suficientemente completa.",
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                PUBLICADO_EM);
    }

    private MeuAnuncioAtualizacaoRequestDto requestValido() {
        return new MeuAnuncioAtualizacaoRequestDto(
                "Novo titulo publico",
                "Descricao atualizada e suficientemente completa.",
                "ACOMPANHANTE_FEMININA",
                new BigDecimal("250"),
                "GO",
                "Goiania",
                "Setor Bueno",
                "Regiao central",
                List.of("MEU_LOCAL", "HOTEL_MOTEL"),
                List.of("ANAL", "ORAL"),
                false,
                "https://example.invalid/conteudo");
    }

    private MeuAnuncioAtualizacaoRequestDto requestComTitulo(String titulo) {
        MeuAnuncioAtualizacaoRequestDto request = requestValido();
        return new MeuAnuncioAtualizacaoRequestDto(
                titulo,
                request.descricao(),
                request.categoria(),
                request.preco(),
                request.uf(),
                request.cidade(),
                request.bairro(),
                request.enderecoResumido(),
                request.locaisAtendimento(),
                request.servicos(),
                request.atendimentoExclusivamenteVirtual(),
                request.linkConteudo());
    }

    private MeuAnuncioAtualizacaoRequestDto requestSemBairro() {
        MeuAnuncioAtualizacaoRequestDto request = requestValido();
        return new MeuAnuncioAtualizacaoRequestDto(
                request.titulo(),
                request.descricao(),
                request.categoria(),
                request.preco(),
                request.uf(),
                request.cidade(),
                null,
                null,
                request.locaisAtendimento(),
                request.servicos(),
                request.atendimentoExclusivamenteVirtual(),
                request.linkConteudo());
    }

    private void assertStatus(int status, org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(status));
    }
}
