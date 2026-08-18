package br.com.topsdojob.v3.application.publico.favorito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.application.publico.service.PoliticaContatoPublicoService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.FavoritoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.FavoritoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class FavoritosPublicosServiceTest {

    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000001001");
    private static final UUID ANUNCIO_ID = UUID.fromString("00000000-0000-4000-8000-000000001011");
    private static final UUID INATIVO_ID = UUID.fromString("00000000-0000-4000-8000-000000001012");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 7, 14, 12, 0, 0, 0, ZoneOffset.UTC);

    private MeusAnunciosConsultaService usuarioService;
    private FavoritoAnuncioRepository favoritoRepository;
    private FavoritoGravacaoService gravacaoService;
    private AnuncioRepository anuncioRepository;
    private AnuncioLocalizacaoRepository localizacaoRepository;
    private AnuncioMidiaRepository anuncioMidiaRepository;
    private ArquivoMidiaRepository arquivoMidiaRepository;
    private EstadoRepository estadoRepository;
    private CidadeRepository cidadeRepository;
    private BairroRepository bairroRepository;
    private FavoritosPublicosService service;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        usuarioService = mock(MeusAnunciosConsultaService.class);
        favoritoRepository = mock(FavoritoAnuncioRepository.class);
        gravacaoService = mock(FavoritoGravacaoService.class);
        anuncioRepository = mock(AnuncioRepository.class);
        localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
        arquivoMidiaRepository = mock(ArquivoMidiaRepository.class);
        estadoRepository = mock(EstadoRepository.class);
        cidadeRepository = mock(CidadeRepository.class);
        bairroRepository = mock(BairroRepository.class);
        authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        service = new FavoritosPublicosService(
                usuarioService,
                favoritoRepository,
                gravacaoService,
                anuncioRepository,
                localizacaoRepository,
                anuncioMidiaRepository,
                arquivoMidiaRepository,
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                new MidiaPublicaMapper(mock(MidiaPublicaUrlService.class)),
                new MidiaPublicaSeguraPolicy(),
                mock(PoliticaContatoPublicoService.class));
    }

    @Test
    void incluirEIdempotenteEPertenceAoUsuarioDaSessao() {
        AnuncioEntity anuncio = anuncio(ANUNCIO_ID, "perfil-publico", StatusAnuncio.PUBLICADO);
        when(anuncioRepository.findPublicoPublicadoComProprietarioAtivoPorSlug("perfil-publico"))
                .thenReturn(Optional.of(anuncio));

        assertThat(service.incluir("perfil-publico", authentication).favorito()).isTrue();
        assertThat(service.incluir("perfil-publico", authentication).favorito()).isTrue();

        verify(gravacaoService, times(2))
                .incluirSeAusente(org.mockito.ArgumentMatchers.eq(USUARIO_ID),
                        org.mockito.ArgumentMatchers.eq(ANUNCIO_ID), any(OffsetDateTime.class));
    }

    @Test
    void removerEIdempotenteESoUsaRelacaoDoUsuarioDaSessao() {
        AnuncioEntity anuncio = anuncio(ANUNCIO_ID, "perfil-publico", StatusAnuncio.PUBLICADO);
        when(anuncioRepository.findBySlugAndRemovidoEmIsNull("perfil-publico"))
                .thenReturn(Optional.of(anuncio));

        assertThat(service.remover("perfil-publico", authentication).favorito()).isFalse();
        assertThat(service.remover("perfil-publico", authentication).favorito()).isFalse();

        verify(gravacaoService, times(2)).removerSeExistente(USUARIO_ID, ANUNCIO_ID);
    }

    @Test
    void anuncioInexistenteOuNaoPublicoNaoPodeSerIncluido() {
        when(anuncioRepository.findPublicoPublicadoComProprietarioAtivoPorSlug("perfil-inativo"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.incluir("perfil-inativo", authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void listagemExcluiFavoritoInativoEFazCargasEmLote() {
        FavoritoAnuncioEntity favoritoPublico = favorito(ANUNCIO_ID, AGORA);
        FavoritoAnuncioEntity favoritoInativo = favorito(INATIVO_ID, AGORA.minusMinutes(1));
        when(favoritoRepository.findByUsuarioIdOrderByCriadoEmDesc(USUARIO_ID))
                .thenReturn(List.of(favoritoPublico, favoritoInativo));
        when(anuncioRepository.findPublicosPublicadosComProprietarioAtivoPorIds(anyList()))
                .thenReturn(List.of(anuncio(ANUNCIO_ID, "perfil-publico", StatusAnuncio.PUBLICADO)));
        stubCargaEmLoteVazia();

        var resultado = service.listar(authentication);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).slug()).isEqualTo("perfil-publico");
        verify(localizacaoRepository).findByAnuncioIdIn(List.of(ANUNCIO_ID));
        verify(anuncioMidiaRepository).findByAnuncioIdIn(List.of(ANUNCIO_ID));
        verify(anuncioRepository).findPrimeiraPublicacaoByUsuarioIdIn(List.of(USUARIO_ID));
    }

    @Test
    void falhaDeSessaoPermanece401ENaoViraListaVazia() {
        ResponseStatusException unauthorized = new ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "sessao publica obrigatoria");
        when(usuarioService.usuarioAutenticado(null)).thenThrow(unauthorized);

        assertThatThrownBy(() -> service.listar(null))
                .isSameAs(unauthorized)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
    }

    private void stubCargaEmLoteVazia() {
        when(localizacaoRepository.findByAnuncioIdIn(anyList())).thenReturn(List.of());
        when(estadoRepository.findAllById(anyList())).thenReturn(List.of());
        when(cidadeRepository.findAllById(anyList())).thenReturn(List.of());
        when(bairroRepository.findAllById(anyList())).thenReturn(List.of());
        when(anuncioMidiaRepository.findByAnuncioIdIn(anyList())).thenReturn(List.of());
        when(arquivoMidiaRepository.findByIdIn(anyList())).thenReturn(List.of());
        when(anuncioRepository.findPrimeiraPublicacaoByUsuarioIdIn(anyList())).thenReturn(List.of());
    }

    private AnuncioEntity anuncio(UUID id, String slug, StatusAnuncio status) {
        return AnuncioEntity.criarFixtureHomologacao(
                id,
                USUARIO_ID,
                slug,
                "Titulo " + slug,
                "Descricao publica",
                status,
                StatusModeracaoAnuncio.APROVADO,
                AGORA);
    }

    private FavoritoAnuncioEntity favorito(UUID anuncioId, OffsetDateTime criadoEm) {
        FavoritoAnuncioEntity favorito = mock(FavoritoAnuncioEntity.class);
        when(favorito.getAnuncioId()).thenReturn(anuncioId);
        when(favorito.getCriadoEm()).thenReturn(criadoEm);
        return favorito;
    }
}
