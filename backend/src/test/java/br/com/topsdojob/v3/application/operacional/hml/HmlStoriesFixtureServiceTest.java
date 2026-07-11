package br.com.topsdojob.v3.application.operacional.hml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class HmlStoriesFixtureServiceTest {

    private UsuarioRepository usuarioRepository;
    private CredencialUsuarioRepository credencialRepository;
    private PapelUsuarioRepository papelRepository;
    private AnuncioRepository anuncioRepository;
    private EstadoRepository estadoRepository;
    private CidadeRepository cidadeRepository;
    private BairroRepository bairroRepository;
    private AnuncioLocalizacaoRepository localizacaoRepository;
    private ArquivoMidiaRepository arquivoRepository;
    private AnuncioMidiaRepository anuncioMidiaRepository;
    private StoryAnuncioRepository storyRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        credencialRepository = mock(CredencialUsuarioRepository.class);
        papelRepository = mock(PapelUsuarioRepository.class);
        anuncioRepository = mock(AnuncioRepository.class);
        estadoRepository = mock(EstadoRepository.class);
        cidadeRepository = mock(CidadeRepository.class);
        bairroRepository = mock(BairroRepository.class);
        localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        arquivoRepository = mock(ArquivoMidiaRepository.class);
        anuncioMidiaRepository = mock(AnuncioMidiaRepository.class);
        storyRepository = mock(StoryAnuncioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash-bcrypt-fixture");
        when(usuarioRepository.findByEmailNormalizado(HmlStoriesFixtureService.USUARIO_EMAIL))
                .thenReturn(Optional.empty());
        when(papelRepository.findByUsuarioId(any())).thenReturn(List.of());
    }

    @Test
    void criaCenarioCompletoSemQualquerDependenciaDeCredito() {
        HmlStoriesFixtureService service = service("homologacao");

        var result = service.provisionar("Runtime-Seguro-123!");

        assertThat(result.localidadesCriadas()).isEqualTo(3);
        assertThat(result.localizacoesCriadas()).isEqualTo(3);
        assertThat(result.anunciosCriados()).isEqualTo(3);
        assertThat(result.arquivosCriados()).isEqualTo(12);
        assertThat(result.vinculosCriados()).isEqualTo(13);
        assertThat(result.storyCriado()).isTrue();
        verify(anuncioRepository, times(3)).save(any(AnuncioEntity.class));
        verify(estadoRepository).save(any());
        verify(cidadeRepository).save(any());
        verify(bairroRepository).save(any());
        verify(localizacaoRepository, times(3)).save(any(AnuncioLocalizacaoEntity.class));
        verify(arquivoRepository, times(12)).save(any(ArquivoMidiaEntity.class));
        verify(anuncioMidiaRepository, times(13)).save(any(AnuncioMidiaEntity.class));
        verify(storyRepository).save(any(StoryAnuncioEntity.class));

        ArgumentCaptor<AnuncioEntity> anuncioCaptor = ArgumentCaptor.forClass(AnuncioEntity.class);
        verify(anuncioRepository, times(3)).save(anuncioCaptor.capture());
        assertThat(anuncioCaptor.getAllValues())
                .filteredOn(item -> item.getStatus() == StatusAnuncio.PUBLICADO)
                .hasSize(2);
        assertThat(anuncioCaptor.getAllValues())
                .filteredOn(item -> item.getStatus() == StatusAnuncio.PAUSADO)
                .hasSize(1);

        ArgumentCaptor<ArquivoMidiaEntity> arquivoCaptor = ArgumentCaptor.forClass(ArquivoMidiaEntity.class);
        verify(arquivoRepository, times(12)).save(arquivoCaptor.capture());
        assertThat(arquivoCaptor.getAllValues())
                .filteredOn(item -> item.getStatusArquivo() == StatusArquivoMidia.PENDENTE)
                .hasSize(1);

        ArgumentCaptor<AnuncioMidiaEntity> midiaCaptor = ArgumentCaptor.forClass(AnuncioMidiaEntity.class);
        verify(anuncioMidiaRepository, times(13)).save(midiaCaptor.capture());
        assertThat(midiaCaptor.getAllValues())
                .filteredOn(item -> item.getTipo() == TipoAnuncioMidia.VIDEO
                        && item.getStatus() == StatusAnuncioMidia.PUBLICAVEL)
                .hasSize(1);
        assertThat(midiaCaptor.getAllValues())
                .filteredOn(item -> item.getStatus() == StatusAnuncioMidia.PENDENTE)
                .hasSize(1);
        assertThat(midiaCaptor.getAllValues())
                .filteredOn(item -> item.getTipo() == TipoAnuncioMidia.FOTO
                        && item.getStatus() == StatusAnuncioMidia.PUBLICAVEL
                        && item.getVisibilidadeMidia() == br.com.topsdojob.v3.domain.shared.VisibilidadeMidia.LIVRE)
                .hasSize(9);
        UUID arquivoCompartilhado = midiaCaptor.getAllValues().stream()
                .filter(item -> item.getTipo() == TipoAnuncioMidia.STORY)
                .findFirst()
                .orElseThrow()
                .getArquivoMidiaId();
        assertThat(midiaCaptor.getAllValues())
                .filteredOn(item -> arquivoCompartilhado.equals(item.getArquivoMidiaId()))
                .extracting(AnuncioMidiaEntity::getTipo)
                .containsExactlyInAnyOrder(TipoAnuncioMidia.FOTO, TipoAnuncioMidia.STORY);

        assertThat(Arrays.stream(HmlStoriesFixtureService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName))
                .noneMatch(name -> name.toLowerCase().contains("credito"));
    }

    @Test
    void reexecucaoPreservaAnunciosMidiasEStoryExistentes() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
                UUID.randomUUID(),
                "Usuario HML",
                HmlStoriesFixtureService.USUARIO_EMAIL,
                null,
                agora);
        CredencialUsuarioEntity credencial = CredencialUsuarioEntity.criar(
                UUID.randomUUID(), usuario.getId(), "hash-anterior", agora);
        when(usuarioRepository.findByEmailNormalizado(HmlStoriesFixtureService.USUARIO_EMAIL))
                .thenReturn(Optional.of(usuario));
        when(credencialRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(credencial));
        AnuncioEntity anuncioExistente = mock(AnuncioEntity.class);
        when(anuncioRepository.findById(any())).thenReturn(Optional.of(anuncioExistente));
        when(estadoRepository.existsById(any())).thenReturn(true);
        when(cidadeRepository.existsById(any())).thenReturn(true);
        when(bairroRepository.existsById(any())).thenReturn(true);
        AnuncioLocalizacaoEntity localizacaoExistente = mock(AnuncioLocalizacaoEntity.class);
        when(localizacaoRepository.findByAnuncioId(any())).thenReturn(Optional.of(localizacaoExistente));
        when(arquivoRepository.existsById(any())).thenReturn(true);
        AnuncioMidiaEntity midiaExistente = mock(AnuncioMidiaEntity.class);
        when(midiaExistente.getTipo()).thenReturn(TipoAnuncioMidia.FOTO);
        AnuncioMidiaEntity storyMidiaExistente = mock(AnuncioMidiaEntity.class);
        when(storyMidiaExistente.getTipo()).thenReturn(TipoAnuncioMidia.STORY);
        UUID storyMidiaId = UUID.fromString("f1000000-0000-4000-8000-000000000307");
        when(anuncioMidiaRepository.findById(any())).thenAnswer(invocation ->
                Optional.of(storyMidiaId.equals(invocation.getArgument(0))
                        ? storyMidiaExistente
                        : midiaExistente));
        when(storyRepository.existsById(any())).thenReturn(true);

        var result = service("homologacao").provisionar("Runtime-Novo-456!");

        assertThat(result.localidadesCriadas()).isZero();
        assertThat(result.localizacoesCriadas()).isZero();
        assertThat(result.anunciosCriados()).isZero();
        assertThat(result.arquivosCriados()).isZero();
        assertThat(result.vinculosCriados()).isZero();
        assertThat(result.storyCriado()).isFalse();
        verify(anuncioRepository, times(3)).save(anuncioExistente);
        verify(localizacaoRepository, times(3)).save(localizacaoExistente);
        verify(arquivoRepository, never()).save(any());
        verify(anuncioMidiaRepository, times(12)).save(midiaExistente);
        verify(anuncioMidiaRepository, never()).save(storyMidiaExistente);
        verify(storyRepository, never()).save(any());
    }

    @Test
    void escolheProximaOrdemLivreQuandoGaleriaDoisJaEstaOcupada() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID anuncioAId = UUID.fromString("f1000000-0000-4000-8000-000000000101");
        UUID midiaCanonicaOrdemDoisId = UUID.fromString("f1000000-0000-4000-8000-000000000308");
        AnuncioMidiaEntity ocupanteExistente = AnuncioMidiaEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                anuncioAId,
                UUID.randomUUID(),
                TipoAnuncioMidia.FOTO,
                FinalidadeAnuncioMidia.GALERIA,
                2,
                StatusAnuncioMidia.PUBLICAVEL,
                br.com.topsdojob.v3.domain.shared.VisibilidadeMidia.LIVRE,
                agora);
        when(anuncioMidiaRepository.findByAnuncioIdIn(any())).thenReturn(List.of(ocupanteExistente));

        service("homologacao").provisionar("Runtime-Seguro-123!");

        ArgumentCaptor<AnuncioMidiaEntity> captor = ArgumentCaptor.forClass(AnuncioMidiaEntity.class);
        verify(anuncioMidiaRepository, times(13)).save(captor.capture());
        AnuncioMidiaEntity reconciliada = captor.getAllValues().stream()
                .filter(item -> midiaCanonicaOrdemDoisId.equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(reconciliada.getOrdem()).isEqualTo(7);
        assertThat(captor.getAllValues())
                .filteredOn(item -> anuncioAId.equals(item.getAnuncioId())
                        && item.getFinalidade() == FinalidadeAnuncioMidia.GALERIA
                        && item.getStatus() != StatusAnuncioMidia.REMOVIDA)
                .extracting(AnuncioMidiaEntity::getOrdem)
                .doesNotHaveDuplicates();
        verify(anuncioMidiaRepository, never()).save(ocupanteExistente);
    }

    @Test
    void recusaFixtureForaDeHomologacao() {
        assertThatThrownBy(() -> service("producao").provisionar("Runtime-Seguro-123!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora de homologacao");

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(anuncioRepository, never()).save(any());
    }

    private HmlStoriesFixtureService service(String appEnv) {
        return new HmlStoriesFixtureService(
                appEnv,
                usuarioRepository,
                credencialRepository,
                papelRepository,
                anuncioRepository,
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                localizacaoRepository,
                arquivoRepository,
                anuncioMidiaRepository,
                storyRepository,
                passwordEncoder);
    }
}
