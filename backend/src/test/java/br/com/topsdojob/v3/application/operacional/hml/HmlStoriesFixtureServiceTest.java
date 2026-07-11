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
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
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

        assertThat(result.anunciosCriados()).isEqualTo(3);
        assertThat(result.arquivosCriados()).isEqualTo(6);
        assertThat(result.vinculosCriados()).isEqualTo(7);
        assertThat(result.storyCriado()).isTrue();
        verify(anuncioRepository, times(3)).save(any(AnuncioEntity.class));
        verify(arquivoRepository, times(6)).save(any(ArquivoMidiaEntity.class));
        verify(anuncioMidiaRepository, times(7)).save(any(AnuncioMidiaEntity.class));
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
        verify(arquivoRepository, times(6)).save(arquivoCaptor.capture());
        assertThat(arquivoCaptor.getAllValues())
                .filteredOn(item -> item.getStatusArquivo() == StatusArquivoMidia.PENDENTE)
                .hasSize(1);

        ArgumentCaptor<AnuncioMidiaEntity> midiaCaptor = ArgumentCaptor.forClass(AnuncioMidiaEntity.class);
        verify(anuncioMidiaRepository, times(7)).save(midiaCaptor.capture());
        assertThat(midiaCaptor.getAllValues())
                .filteredOn(item -> item.getTipo() == TipoAnuncioMidia.VIDEO
                        && item.getStatus() == StatusAnuncioMidia.PUBLICAVEL)
                .hasSize(1);
        assertThat(midiaCaptor.getAllValues())
                .filteredOn(item -> item.getStatus() == StatusAnuncioMidia.PENDENTE)
                .hasSize(1);
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
        when(anuncioRepository.existsById(any())).thenReturn(true);
        when(arquivoRepository.existsById(any())).thenReturn(true);
        when(anuncioMidiaRepository.existsById(any())).thenReturn(true);
        when(storyRepository.existsById(any())).thenReturn(true);

        var result = service("homologacao").provisionar("Runtime-Novo-456!");

        assertThat(result.anunciosCriados()).isZero();
        assertThat(result.arquivosCriados()).isZero();
        assertThat(result.vinculosCriados()).isZero();
        assertThat(result.storyCriado()).isFalse();
        verify(anuncioRepository, never()).save(any());
        verify(arquivoRepository, never()).save(any());
        verify(anuncioMidiaRepository, never()).save(any());
        verify(storyRepository, never()).save(any());
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
                arquivoRepository,
                anuncioMidiaRepository,
                storyRepository,
                passwordEncoder);
    }
}
