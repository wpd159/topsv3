package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioBloqueioJuridicoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class MeusAnunciosMutacaoLockTest {

    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000007301");
    private static final UUID OUTRO_USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000007302");
    private static final UUID ANUNCIO_ID = UUID.fromString("00000000-0000-4000-8000-000000007311");
    private static final String SLUG = "perfil-lock-sintetico";
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-08T20:00:00Z");

    private UsuarioRepository usuarios;
    private AnuncioRepository anuncios;
    private AnuncioBloqueioJuridicoRepository bloqueios;
    private Object[] consumidoresNaoUsados;
    private MeusAnunciosConsultaService service;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        anuncios = mock(AnuncioRepository.class);
        bloqueios = mock(AnuncioBloqueioJuridicoRepository.class);
        AnuncioLocalizacaoRepository localizacoes = mock(AnuncioLocalizacaoRepository.class);
        AnuncioMidiaRepository midias = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivos = mock(ArquivoMidiaRepository.class);
        EstadoRepository estados = mock(EstadoRepository.class);
        CidadeRepository cidades = mock(CidadeRepository.class);
        BairroRepository bairros = mock(BairroRepository.class);
        DecisaoModeracaoRepository decisoes = mock(DecisaoModeracaoRepository.class);
        MidiaPublicaMapper mapper = mock(MidiaPublicaMapper.class);
        MidiaPublicaSeguraPolicy midiaPolicy = mock(MidiaPublicaSeguraPolicy.class);
        VisualizacaoTotalCanonicaService visualizacoes = mock(VisualizacaoTotalCanonicaService.class);
        MeuAnuncioBeneficioConsultaService beneficios = mock(MeuAnuncioBeneficioConsultaService.class);
        MeuAnuncioStoryConsultaService stories = mock(MeuAnuncioStoryConsultaService.class);
        consumidoresNaoUsados = new Object[] {
                localizacoes, midias, arquivos, estados, cidades, bairros, decisoes,
                mapper, midiaPolicy, visualizacoes, beneficios, stories
        };
        service = new MeusAnunciosConsultaService(
                usuarios, anuncios, localizacoes, midias, arquivos, estados, cidades, bairros, decisoes,
                mapper, midiaPolicy, visualizacoes, beneficios, stories, bloqueios);
    }

    @AfterEach
    void nenhumaLeituraPreviaOuEscritaForaDoProtocolo() {
        verify(usuarios, never()).findById(any());
        verify(anuncios, never()).findById(any());
        verify(anuncios, never()).findBySlugAndRemovidoEmIsNull(any());
        verifyNoMoreInteractions(usuarios, anuncios, bloqueios);
        verifyNoInteractions(consumidoresNaoUsados);
    }

    @Test
    void carregaUsuarioProtegidoAntesDoAnuncioSemPreloadNemEscrita() {
        AnuncioEntity anuncio = anuncio(USUARIO_ID);
        stubAtivos(anuncio);

        assertThat(service.anuncioDoUsuarioParaAtualizacao("  PERFIL-LOCK-SINTETICO  ", authentication()))
                .isSameAs(anuncio);

        verificarOrdem(true, true);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getAtualizadoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getRemovidoEm()).isNull();
    }

    @Test
    void outroProprietarioRecebe403AntesDeConsultarBloqueiosOuEscrever() {
        AnuncioEntity anuncio = anuncio(OUTRO_USUARIO_ID);
        stubAtivos(anuncio);

        assertThatThrownBy(() -> service.anuncioDoUsuarioParaAtualizacao(SLUG, authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));

        verificarOrdem(false, false);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
    }

    @ParameterizedTest(name = "retirada por {0} impede mutacao normal com 404")
    @ValueSource(strings = {"STATUS_REMOVIDO", "DATA_REMOCAO"})
    void retiradaAtualImpedeMutacaoNormal(String estadoPersistido) {
        AnuncioEntity anuncio = retirado(estadoPersistido);
        stubAtivos(anuncio);

        assertThatThrownBy(() -> service.anuncioDoUsuarioParaAtualizacao(SLUG, authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));

        verificarOrdem(false, false);
        verificarRetiradaInalterada(anuncio, estadoPersistido);
    }

    @ParameterizedTest(name = "entry DELETE inclui {0} apenas para preservar verificacao de replay")
    @ValueSource(strings = {"STATUS_REMOVIDO", "DATA_REMOCAO"})
    void entradaExclusivaDeDeletePreservaEstadoRetiradoParaChecarReplay(String estadoPersistido) {
        AnuncioEntity anuncio = retirado(estadoPersistido);
        stubAtivos(anuncio);

        assertThat(service.anuncioDoUsuarioParaRemocaoMidia(SLUG, authentication())).isSameAs(anuncio);

        verificarOrdem(true, true);
        verificarRetiradaInalterada(anuncio, estadoPersistido);
    }

    @ParameterizedTest(name = "snapshot atual de usuario {0} impede carregar anuncio")
    @EnumSource(value = StatusUsuario.class, names = {"SUSPENSO", "EXCLUIDO"})
    void usuarioSuspensoOuExcluidoRecusadoNoPrimeiroLock(StatusUsuario estadoPersistido) {
        UsuarioEntity usuario = usuarioAtivo();
        if (estadoPersistido == StatusUsuario.SUSPENSO) usuario.bloquearJuridicamente(AGORA.plusSeconds(1));
        else usuario.anonimizarDefinitivamente(USUARIO_ID, AGORA.plusSeconds(1));
        when(usuarios.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.anuncioDoUsuarioParaAtualizacao(SLUG, authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));

        verify(usuarios).findByIdForUpdate(USUARIO_ID);
        assertThat(usuario.getStatus()).isEqualTo(estadoPersistido);
        assertThat(usuario.getAtualizadoEm()).isEqualTo(AGORA.plusSeconds(1));
        if (estadoPersistido == StatusUsuario.EXCLUIDO) assertThat(usuario.getExcluidoEm()).isNotNull();
    }

    @ParameterizedTest(name = "vinculo juridico ativo {0} impede anuncio pendente")
    @ValueSource(strings = {"ANUNCIO", "USUARIO"})
    void vinculoJuridicoAtivoPrevaleceSobreStatusPendente(String origem) {
        AnuncioEntity anuncio = anuncio(USUARIO_ID);
        stubAtivos(anuncio);
        AnuncioBloqueioJuridicoEntity bloqueio = mock(AnuncioBloqueioJuridicoEntity.class);
        if (origem.equals("ANUNCIO")) {
            when(bloqueios.findAtivoPorAnuncioForUpdate(ANUNCIO_ID)).thenReturn(Optional.of(bloqueio));
        } else {
            when(bloqueios.findAtivoPorUsuarioForUpdate(USUARIO_ID, EscopoBloqueioJuridico.ANUNCIO_E_USUARIO))
                    .thenReturn(Optional.of(bloqueio));
        }

        assertThatThrownBy(() -> service.anuncioDoUsuarioParaAtualizacao(SLUG, authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(409);
                    assertThat(error.getReason()).contains("bloqueio juridico");
                });

        verificarOrdem(true, origem.equals("USUARIO"));
        verifyNoInteractions(bloqueio);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getRemovidoEm()).isNull();
    }

    private void stubAtivos(AnuncioEntity anuncio) {
        when(usuarios.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuarioAtivo()));
        when(anuncios.findBySlugForLifecycle(SLUG)).thenReturn(Optional.of(anuncio));
    }

    private void verificarOrdem(boolean consultaBloqueioAnuncio, boolean consultaBloqueioUsuario) {
        InOrder ordem = inOrder(usuarios, anuncios, bloqueios);
        ordem.verify(usuarios).findByIdForUpdate(USUARIO_ID);
        ordem.verify(anuncios).findBySlugForLifecycle(SLUG);
        if (consultaBloqueioAnuncio) ordem.verify(bloqueios).findAtivoPorAnuncioForUpdate(ANUNCIO_ID);
        if (consultaBloqueioUsuario) {
            ordem.verify(bloqueios).findAtivoPorUsuarioForUpdate(USUARIO_ID, EscopoBloqueioJuridico.ANUNCIO_E_USUARIO);
        }
        ordem.verifyNoMoreInteractions();
    }

    private AnuncioEntity retirado(String estadoPersistido) {
        AnuncioEntity anuncio = anuncio(USUARIO_ID);
        // Testa cada indicador persistido isoladamente, inclusive dados historicos inconsistentes.
        if (estadoPersistido.equals("STATUS_REMOVIDO")) {
            ReflectionTestUtils.setField(anuncio, "status", StatusAnuncio.REMOVIDO);
        } else {
            ReflectionTestUtils.setField(anuncio, "removidoEm", AGORA.plusSeconds(1));
        }
        return anuncio;
    }

    private void verificarRetiradaInalterada(AnuncioEntity anuncio, String estadoPersistido) {
        assertThat(anuncio.getStatus()).isEqualTo(estadoPersistido.equals("STATUS_REMOVIDO")
                ? StatusAnuncio.REMOVIDO : StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getRemovidoEm()).isEqualTo(estadoPersistido.equals("DATA_REMOCAO")
                ? AGORA.plusSeconds(1) : null);
        assertThat(anuncio.getAtualizadoEm()).isEqualTo(AGORA);
    }

    private UsuarioEntity usuarioAtivo() {
        UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
                USUARIO_ID, "Usuario sintetico", "mutacao-lock@example.invalid", null, null, AGORA);
        usuario.confirmarEmail(AGORA);
        return usuario;
    }

    private AnuncioEntity anuncio(UUID proprietarioId) {
        return AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID, proprietarioId, SLUG, "Anuncio sintetico", "Descricao sintetica suficiente",
                StatusAnuncio.PENDENTE_REVISAO, StatusModeracaoAnuncio.PENDENTE, AGORA);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new PublicUserPrincipal(USUARIO_ID, "Usuario sintetico", "mutacao-lock@example.invalid"),
                null, List.of());
    }
}
