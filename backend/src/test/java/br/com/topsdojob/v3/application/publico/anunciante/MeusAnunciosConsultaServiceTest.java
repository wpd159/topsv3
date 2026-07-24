package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class MeusAnunciosConsultaServiceTest {

    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000000901");
    private static final UUID OUTRO_USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000000902");
    private static final UUID ANUNCIO_A_ID = UUID.fromString("00000000-0000-4000-8000-000000000911");
    private static final UUID ANUNCIO_B_ID = UUID.fromString("00000000-0000-4000-8000-000000000912");
    private static final UUID ESTADO_ID = UUID.fromString("00000000-0000-4000-8000-000000000921");
    private static final UUID CIDADE_ID = UUID.fromString("00000000-0000-4000-8000-000000000922");
    private static final UUID BAIRRO_ID = UUID.fromString("00000000-0000-4000-8000-000000000923");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 7, 11, 20, 0, 0, 0, ZoneOffset.UTC);

    private UsuarioRepository usuarioRepository;
    private AnuncioRepository anuncioRepository;
    private AnuncioLocalizacaoRepository localizacaoRepository;
    private AnuncioMidiaRepository anuncioMidiaRepository;
    private ArquivoMidiaRepository arquivoMidiaRepository;
    private EstadoRepository estadoRepository;
    private CidadeRepository cidadeRepository;
    private BairroRepository bairroRepository;
    private DecisaoModeracaoRepository decisaoModeracaoRepository;
    private MidiaPublicaUrlService urlService;
    private VisualizacaoTotalCanonicaService visualizacaoService;
    private MeusAnunciosConsultaService service;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        anuncioRepository = mock(AnuncioRepository.class);
        localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
        arquivoMidiaRepository = mock(ArquivoMidiaRepository.class);
        estadoRepository = mock(EstadoRepository.class);
        cidadeRepository = mock(CidadeRepository.class);
        bairroRepository = mock(BairroRepository.class);
        decisaoModeracaoRepository = mock(DecisaoModeracaoRepository.class);
        urlService = mock(MidiaPublicaUrlService.class);
        visualizacaoService = mock(VisualizacaoTotalCanonicaService.class);
        when(visualizacaoService.calcularEmLote(any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            Map<UUID, VisualizacoesCanonicasDto> totais = new LinkedHashMap<>();
            ids.forEach(id -> totais.put(id, VisualizacoesCanonicasDto.total(0)));
            return totais;
        });
        service = new MeusAnunciosConsultaService(
                usuarioRepository,
                anuncioRepository,
                localizacaoRepository,
                anuncioMidiaRepository,
                arquivoMidiaRepository,
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                decisaoModeracaoRepository,
                new MidiaPublicaMapper(urlService),
                new MidiaPublicaSeguraPolicy(),
                visualizacaoService);
    }

    @Test
    void semSessaoRetorna401() {
        assertThatThrownBy(() -> service.listar(null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void usuarioSemAnunciosRecebeColecaoVaziaReal() {
        stubUsuarioAtivo();
        when(anuncioRepository.findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(USUARIO_ID))
                .thenReturn(List.of());

        assertThat(service.listar(authentication())).isEmpty();

        verify(anuncioRepository).findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(USUARIO_ID);
    }

    @Test
    void listaMultiplosAnunciosComStatusLocalizacaoECapaSegura() {
        stubUsuarioAtivo();
        AnuncioEntity publicado = anuncio(ANUNCIO_A_ID, USUARIO_ID, "perfil-publicado", StatusAnuncio.PUBLICADO);
        AnuncioEntity pausado = anuncio(ANUNCIO_B_ID, USUARIO_ID, "perfil-pausado", StatusAnuncio.PAUSADO);
        when(anuncioRepository.findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(USUARIO_ID))
                .thenReturn(List.of(publicado, pausado));

        AnuncioLocalizacaoEntity localizacaoA = AnuncioLocalizacaoEntity.criarFixtureHomologacao(
                ANUNCIO_A_ID, ESTADO_ID, CIDADE_ID, BAIRRO_ID, AGORA);
        AnuncioLocalizacaoEntity localizacaoB = AnuncioLocalizacaoEntity.criarFixtureHomologacao(
                ANUNCIO_B_ID, ESTADO_ID, CIDADE_ID, BAIRRO_ID, AGORA);
        when(localizacaoRepository.findByAnuncioIdIn(any())).thenReturn(List.of(localizacaoA, localizacaoB));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(
                EstadoEntity.criarFixtureHomologacao(ESTADO_ID, "GO", "Goias", "goias", AGORA)));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(
                CidadeEntity.criarFixtureHomologacao(CIDADE_ID, ESTADO_ID, "Goiania", "goiania", "goiania", AGORA)));
        when(bairroRepository.findAllById(any())).thenReturn(List.of(
                BairroEntity.criarFixtureHomologacao(BAIRRO_ID, CIDADE_ID, "Setor Bueno", "setor bueno", "setor-bueno", AGORA)));

        UUID vinculoId = UUID.fromString("00000000-0000-4000-8000-000000000931");
        UUID arquivoId = UUID.fromString("00000000-0000-4000-8000-000000000932");
        AnuncioMidiaEntity vinculo = vinculo(
                vinculoId, ANUNCIO_A_ID, arquivoId, VisibilidadeMidia.LIVRE);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(anuncioMidiaRepository.findByAnuncioIdIn(any())).thenReturn(List.of(vinculo));
        when(arquivoMidiaRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
        when(urlService.resolver(vinculo, arquivo))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/capa-segura.svg", null));

        var resultado = service.listar(authentication());

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).status()).isEqualTo("PUBLICADO");
        assertThat(resultado.get(0).localizacao().uf()).isEqualTo("GO");
        assertThat(resultado.get(0).localizacao().cidade()).isEqualTo("Goiania");
        assertThat(resultado.get(0).localizacao().bairro()).isEqualTo("Setor Bueno");
        assertThat(resultado.get(0).capa().urlPublica()).isEqualTo("/capa-segura.svg");
        assertThat(resultado.get(0).capa().restrita()).isFalse();
        assertThat(resultado.get(0).visualizacoes().total()).isZero();
        assertThat(resultado.get(0).acoesPermitidas().pausar()).isTrue();
        assertThat(resultado.get(0).acoesPermitidas().reativar()).isFalse();
        assertThat(resultado.get(0).acoesPermitidas().remover()).isTrue();
        assertThat(resultado.get(1).status()).isEqualTo("PAUSADO");
        assertThat(resultado.get(1).acoesPermitidas().pausar()).isFalse();
        assertThat(resultado.get(1).acoesPermitidas().reativar()).isTrue();
        assertThat(resultado.get(1).capa()).isNull();
    }

    @Test
    void anuncioRejeitadoExibeMotivoIntegralEAcaoDeCorrecaoEmConsultaLote() {
        stubUsuarioAtivo();
        AnuncioEntity rejeitado = anuncio(
                ANUNCIO_A_ID,
                USUARIO_ID,
                "perfil-rejeitado",
                StatusAnuncio.REJEITADO);
        rejeitado.aplicarModeracao(
                StatusAnuncio.REJEITADO,
                StatusModeracaoAnuncio.REJEITADO,
                AGORA);
        when(anuncioRepository.findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(USUARIO_ID))
                .thenReturn(List.of(rejeitado));
        when(localizacaoRepository.findByAnuncioIdIn(any())).thenReturn(List.of());
        when(anuncioMidiaRepository.findByAnuncioIdIn(any())).thenReturn(List.of());

        DecisaoModeracaoRepository.ReprovacaoPorAnuncioProjection decisao =
                mock(DecisaoModeracaoRepository.ReprovacaoPorAnuncioProjection.class);
        when(decisao.getAnuncioId()).thenReturn(ANUNCIO_A_ID);
        when(decisao.getMotivo()).thenReturn("Corrigir a descricao e reenviar.");
        when(decisao.getDecididoEm()).thenReturn(AGORA);
        when(decisaoModeracaoRepository.findReprovacoesByAnuncioIdIn(List.of(ANUNCIO_A_ID)))
                .thenReturn(List.of(decisao));

        var resultado = service.listar(authentication()).get(0);

        assertThat(resultado.status()).isEqualTo("REJEITADO");
        assertThat(resultado.statusModeracao()).isEqualTo("REJEITADO");
        assertThat(resultado.reprovacao().motivo()).isEqualTo("Corrigir a descricao e reenviar.");
        assertThat(resultado.reprovacao().decididoEm()).isEqualTo(AGORA);
        assertThat(resultado.acoesPermitidas().corrigirEReenviar()).isTrue();
        verify(decisaoModeracaoRepository).findReprovacoesByAnuncioIdIn(List.of(ANUNCIO_A_ID));
    }

    @Test
    void capaRestritaNuncaExpoeUrlOriginal() {
        stubUsuarioAtivo();
        AnuncioEntity anuncio = anuncio(ANUNCIO_A_ID, USUARIO_ID, "perfil-restrito", StatusAnuncio.PUBLICADO);
        when(anuncioRepository.findByUsuarioIdAndRemovidoEmIsNullOrderByAtualizadoEmDesc(USUARIO_ID))
                .thenReturn(List.of(anuncio));
        when(localizacaoRepository.findByAnuncioIdIn(any())).thenReturn(List.of());
        when(estadoRepository.findAllById(any())).thenReturn(List.of());
        when(cidadeRepository.findAllById(any())).thenReturn(List.of());
        when(bairroRepository.findAllById(any())).thenReturn(List.of());

        UUID vinculoId = UUID.fromString("00000000-0000-4000-8000-000000000941");
        UUID arquivoId = UUID.fromString("00000000-0000-4000-8000-000000000942");
        AnuncioMidiaEntity vinculo = vinculo(
                vinculoId, ANUNCIO_A_ID, arquivoId, VisibilidadeMidia.RESTRITA_18);
        when(anuncioMidiaRepository.findByAnuncioIdIn(any())).thenReturn(List.of(vinculo));
        when(arquivoMidiaRepository.findByIdIn(any())).thenReturn(List.of(arquivo(arquivoId)));

        var resultado = service.listar(authentication()).get(0);
        var capa = resultado.capa();

        assertThat(capa.restrita()).isTrue();
        assertThat(capa.urlPublica()).isNull();
        assertThat(resultado.midias()).hasSize(1);
        assertThat(resultado.midias().get(0).restrita()).isTrue();
        assertThat(resultado.midias().get(0).urlPublica()).isNull();
        assertThat(resultado.midias().get(0).ordem()).isEqualTo(1);
    }

    @Test
    void detalhePreservaOrdemDasMidiasSemExporAsNaoPublicaveis() {
        stubUsuarioAtivo();
        AnuncioEntity anuncio = anuncio(ANUNCIO_A_ID, USUARIO_ID, "perfil-midias", StatusAnuncio.PUBLICADO);
        when(anuncioRepository.findBySlugAndRemovidoEmIsNull("perfil-midias")).thenReturn(Optional.of(anuncio));
        when(localizacaoRepository.findByAnuncioIdIn(any())).thenReturn(List.of());
        when(estadoRepository.findAllById(any())).thenReturn(List.of());
        when(cidadeRepository.findAllById(any())).thenReturn(List.of());
        when(bairroRepository.findAllById(any())).thenReturn(List.of());

        UUID vinculoPrimeiroId = UUID.fromString("00000000-0000-4000-8000-000000000951");
        UUID vinculoSegundoId = UUID.fromString("00000000-0000-4000-8000-000000000952");
        UUID arquivoPrimeiroId = UUID.fromString("00000000-0000-4000-8000-000000000953");
        UUID arquivoSegundoId = UUID.fromString("00000000-0000-4000-8000-000000000954");
        AnuncioMidiaEntity primeiro = vinculo(
                vinculoPrimeiroId, ANUNCIO_A_ID, arquivoPrimeiroId, VisibilidadeMidia.LIVRE, 1);
        AnuncioMidiaEntity segundo = vinculo(
                vinculoSegundoId, ANUNCIO_A_ID, arquivoSegundoId, VisibilidadeMidia.LIVRE, 2);
        ArquivoMidiaEntity arquivoPrimeiro = arquivo(arquivoPrimeiroId);
        ArquivoMidiaEntity arquivoSegundo = arquivo(arquivoSegundoId);
        when(anuncioMidiaRepository.findByAnuncioIdIn(any())).thenReturn(List.of(segundo, primeiro));
        when(arquivoMidiaRepository.findByIdIn(any())).thenReturn(List.of(arquivoPrimeiro, arquivoSegundo));
        when(urlService.resolver(primeiro, arquivoPrimeiro))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/primeira.svg", null));
        when(urlService.resolver(segundo, arquivoSegundo))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica(
                        null, MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN));

        var midias = service.detalhar("perfil-midias", authentication()).midias();

        assertThat(midias).extracting(item -> item.ordem()).containsExactly(1, 2);
        assertThat(midias.get(0).urlPublica()).isEqualTo("/primeira.svg");
        assertThat(midias.get(1).urlPublica()).isNull();
    }

    @Test
    void anuncioDeOutroUsuarioRetorna403() {
        stubUsuarioAtivo();
        when(anuncioRepository.findBySlugAndRemovidoEmIsNull("perfil-terceiro"))
                .thenReturn(Optional.of(anuncio(
                        ANUNCIO_A_ID, OUTRO_USUARIO_ID, "perfil-terceiro", StatusAnuncio.PUBLICADO)));

        assertThatThrownBy(() -> service.detalhar("perfil-terceiro", authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void anuncioInexistenteRetorna404() {
        stubUsuarioAtivo();
        when(anuncioRepository.findBySlugAndRemovidoEmIsNull("perfil-inexistente"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhar("perfil-inexistente", authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
    }

    private void stubUsuarioAtivo() {
        UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
                USUARIO_ID,
                "Perfil Teste",
                "perfil.teste@example.invalid",
                "+5562999999999",
                null,
                AGORA);
        usuario.confirmarEmail(AGORA);
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new PublicUserPrincipal(USUARIO_ID, "Perfil Teste", "perfil.teste@example.invalid"),
                null,
                List.of());
    }

    private AnuncioEntity anuncio(UUID id, UUID usuarioId, String slug, StatusAnuncio status) {
        return AnuncioEntity.criarFixtureHomologacao(
                id,
                usuarioId,
                slug,
                "Titulo " + slug,
                "Descricao suficiente",
                status,
                StatusModeracaoAnuncio.APROVADO,
                AGORA);
    }

    private AnuncioMidiaEntity vinculo(
            UUID id,
            UUID anuncioId,
            UUID arquivoId,
            VisibilidadeMidia visibilidade) {
        return vinculo(id, anuncioId, arquivoId, visibilidade, 1);
    }

    private AnuncioMidiaEntity vinculo(
            UUID id,
            UUID anuncioId,
            UUID arquivoId,
            VisibilidadeMidia visibilidade,
            int ordem) {
        return AnuncioMidiaEntity.criarFixtureHomologacao(
                id,
                anuncioId,
                arquivoId,
                TipoAnuncioMidia.FOTO,
                FinalidadeAnuncioMidia.CAPA,
                ordem,
                StatusAnuncioMidia.PUBLICAVEL,
                visibilidade,
                AGORA);
    }

    private ArquivoMidiaEntity arquivo(UUID id) {
        return ArquivoMidiaEntity.criarFixtureHomologacao(
                id,
                "fixture/stories/capa-segura.svg",
                "image/svg+xml",
                StatusArquivoMidia.VALIDADO,
                AGORA);
    }
}
