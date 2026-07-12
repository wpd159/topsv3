package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
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
    private MidiaPublicaUrlService urlService;
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
        urlService = mock(MidiaPublicaUrlService.class);
        service = new MeusAnunciosConsultaService(
                usuarioRepository,
                anuncioRepository,
                localizacaoRepository,
                anuncioMidiaRepository,
                arquivoMidiaRepository,
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                new MidiaPublicaMapper(urlService),
                new MidiaPublicaSeguraPolicy());
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
        assertThat(resultado.get(1).status()).isEqualTo("PAUSADO");
        assertThat(resultado.get(1).capa()).isNull();
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

        var capa = service.listar(authentication()).get(0).capa();

        assertThat(capa.restrita()).isTrue();
        assertThat(capa.urlPublica()).isNull();
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
        return AnuncioMidiaEntity.criarFixtureHomologacao(
                id,
                anuncioId,
                arquivoId,
                TipoAnuncioMidia.FOTO,
                FinalidadeAnuncioMidia.CAPA,
                1,
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
