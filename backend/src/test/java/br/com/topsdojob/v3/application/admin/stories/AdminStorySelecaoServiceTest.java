package br.com.topsdojob.v3.application.admin.stories;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService.MidiaElegivel;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminStorySelecaoServiceTest {

    private final StorySelecaoAdministrativaRepository selecaoRepository = mock(StorySelecaoAdministrativaRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final StoryMidiaElegibilidadeService elegibilidadeService = mock(StoryMidiaElegibilidadeService.class);
    private final StorySelecaoAdministrativaEntity selecao = entity(StorySelecaoAdministrativaEntity.class);
    private AdminStorySelecaoService service;

    @BeforeEach
    void setUp() {
        set(selecao, "singletonId", (short) 1);
        set(selecao, "ativa", false);
        service = new AdminStorySelecaoService(
                selecaoRepository,
                anuncioRepository,
                usuarioRepository,
                auditoriaRepository,
                elegibilidadeService,
                new ObjectMapper());
        when(selecaoRepository.bloquearSingleton()).thenReturn(Optional.of(selecao));
        when(selecaoRepository.atual()).thenReturn(Optional.of(selecao));
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ativaSemCriarStoryPagoOuCredito() {
        UUID anuncioId = UUID.randomUUID();
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio(anuncioId)));
        when(elegibilidadeService.listar(anuncioId)).thenReturn(List.of(midia(TipoAnuncioMidia.FOTO)));

        var response = service.ativar(anuncioId, ator(), "req-ativar");

        assertThat(response.ativa()).isTrue();
        assertThat(response.anuncioId()).isEqualTo(anuncioId);
        assertThat(response.fotosAprovadas()).isEqualTo(1);
        assertThat(response.classificacao()).isEqualTo("RESTRITA_18");
        assertThat(response.expiraEm()).isEqualTo(response.ativadoEm().plusHours(24));
        ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertThat(audit.getValue().getAcao()).isEqualTo("STORY_ADMIN_ATIVAR");
        assertThat(audit.getValue().getRequestId()).isEqualTo("req-ativar");
    }

    @Test
    void primeiraAtivacaoCriaSomenteOLockSingleton() {
        UUID anuncioId = UUID.randomUUID();
        when(selecaoRepository.bloquearSingleton()).thenReturn(Optional.empty());
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio(anuncioId)));
        when(elegibilidadeService.listar(anuncioId)).thenReturn(List.of(midia(TipoAnuncioMidia.FOTO)));

        var response = service.ativar(anuncioId, ator(), "req-primeira");

        assertThat(response.ativa()).isTrue();
        ArgumentCaptor<StorySelecaoAdministrativaEntity> saved =
                ArgumentCaptor.forClass(StorySelecaoAdministrativaEntity.class);
        verify(selecaoRepository).save(saved.capture());
        assertThat(saved.getValue().getSingletonId()).isEqualTo((short) 1);
        assertThat(saved.getValue().getAnuncioId()).isEqualTo(anuncioId);
    }

    @Test
    void substituiNaMesmaLinhaComAuditoria() {
        UUID anterior = UUID.randomUUID();
        UUID proximo = UUID.randomUUID();
        selecao.ativar(anterior, UUID.randomUUID(), java.time.OffsetDateTime.now());
        when(anuncioRepository.findById(proximo)).thenReturn(Optional.of(anuncio(proximo)));
        when(elegibilidadeService.listar(proximo)).thenReturn(List.of(midia(TipoAnuncioMidia.VIDEO)));

        var response = service.ativar(proximo, ator(), "req-substituir");

        assertThat(response.anuncioId()).isEqualTo(proximo);
        ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertThat(audit.getValue().getAcao()).isEqualTo("STORY_ADMIN_SUBSTITUIR");
    }

    @Test
    void retryDoMesmoAnuncioNaoEstendeAsVinteEQuatroHoras() {
        UUID anuncioId = UUID.randomUUID();
        java.time.OffsetDateTime ativadoEm = java.time.OffsetDateTime.now().minusHours(2);
        selecao.ativar(anuncioId, UUID.randomUUID(), ativadoEm);
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio(anuncioId)));
        when(elegibilidadeService.listar(anuncioId)).thenReturn(List.of(midia(TipoAnuncioMidia.FOTO)));

        var response = service.ativar(anuncioId, ator(), "req-retry");

        assertThat(response.ativadoEm()).isEqualTo(ativadoEm);
        assertThat(response.expiraEm()).isEqualTo(ativadoEm.plusHours(24));
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void desativaLimpandoSelecaoERegistrandoAuditoria() {
        UUID anuncioId = UUID.randomUUID();
        selecao.ativar(anuncioId, UUID.randomUUID(), java.time.OffsetDateTime.now());

        var response = service.desativar(ator(), "req-desativar");

        assertThat(response.ativa()).isFalse();
        assertThat(selecao.getAnuncioId()).isNull();
        ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(audit.capture());
        assertThat(audit.getValue().getAcao()).isEqualTo("STORY_ADMIN_DESATIVAR");
    }

    @Test
    void rejeitaAnuncioSemMidiaAprovada() {
        UUID anuncioId = UUID.randomUUID();
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio(anuncioId)));
        when(elegibilidadeService.listar(anuncioId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.ativar(anuncioId, ator(), "req-sem-midia"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("422")
                .hasMessageContaining("sem foto ou video aprovado");
    }

    @Test
    void rejeitaAnuncioNaoPublico() {
        UUID anuncioId = UUID.randomUUID();
        AnuncioEntity anuncio = anuncio(anuncioId);
        set(anuncio, "status", StatusAnuncio.PAUSADO);
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));

        assertThatThrownBy(() -> service.ativar(anuncioId, ator(), "req-pausado"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("422");
    }

    private AnuncioEntity anuncio(UUID id) {
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", id);
        set(anuncio, "slug", "anuncio-publico");
        set(anuncio, "titulo", "Anuncio de demonstracao");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        return anuncio;
    }

    private MidiaElegivel midia(TipoAnuncioMidia tipo) {
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", UUID.randomUUID());
        set(vinculo, "tipo", tipo);
        return new MidiaElegivel(vinculo, entity(ArquivoMidiaEntity.class));
    }

    private AdminUserPrincipal ator() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin",
                "admin@example.invalid",
                "hash-sintetico",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true);
    }
}
