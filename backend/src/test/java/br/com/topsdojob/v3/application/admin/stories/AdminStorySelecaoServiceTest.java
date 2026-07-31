package br.com.topsdojob.v3.application.admin.stories;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService;
import br.com.topsdojob.v3.application.stories.StoryMidiaElegibilidadeService.MidiaElegivel;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminStorySelecaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-07-31T13:00:00Z");

    private final StorySelecaoAdministrativaRepository selecaoRepository =
            mock(StorySelecaoAdministrativaRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final StoryMidiaElegibilidadeService elegibilidadeService = mock(StoryMidiaElegibilidadeService.class);
    private final List<StorySelecaoAdministrativaEntity> persistidas = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private AdminStorySelecaoService service;

    @BeforeEach
    void setUp() {
        service = new AdminStorySelecaoService(
                selecaoRepository,
                anuncioRepository,
                usuarioRepository,
                auditoriaRepository,
                elegibilidadeService,
                new ObjectMapper(),
                Clock.fixed(AGORA, ZoneOffset.UTC));
        when(anuncioRepository.findById(any())).thenAnswer(invocation ->
                Optional.of(anuncio(invocation.getArgument(0))));
        when(elegibilidadeService.listar(any())).thenReturn(List.of(midia(TipoAnuncioMidia.FOTO)));
        when(selecaoRepository.findByIdempotencyKey(any())).thenAnswer(invocation -> persistidas.stream()
                .filter(item -> invocation.getArgument(0).equals(item.getIdempotencyKey()))
                .findFirst());
        when(selecaoRepository.bloquearAtivasDoAnuncio(any())).thenAnswer(invocation -> persistidas.stream()
                .filter(StorySelecaoAdministrativaEntity::isAtiva)
                .filter(item -> invocation.getArgument(0).equals(item.getAnuncioId()))
                .toList());
        when(selecaoRepository.findByAtivaTrueOrderByAtivadoEmAscIdAsc()).thenAnswer(invocation ->
                persistidas.stream().filter(StorySelecaoAdministrativaEntity::isAtiva).toList());
        when(selecaoRepository.save(any())).thenAnswer(invocation -> {
            StorySelecaoAdministrativaEntity item = invocation.getArgument(0);
            if (item.getId() == null) set(item, "id", ids.getAndIncrement());
            if (!persistidas.contains(item)) persistidas.add(item);
            return item;
        });
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ativarABCmantemTodasAsSelecoesComVigenciasIndependentes() {
        UUID anuncioA = UUID.randomUUID();
        UUID anuncioB = UUID.randomUUID();
        UUID anuncioC = UUID.randomUUID();

        service.ativar(anuncioA, "story-chave-a", ator(), "req-a");
        service.ativar(anuncioB, "story-chave-b", ator(), "req-b");
        service.ativar(anuncioC, "story-chave-c", ator(), "req-c");

        assertThat(service.consultar())
                .extracting(item -> item.anuncioId())
                .containsExactly(anuncioA, anuncioB, anuncioC);
        assertThat(persistidas).allSatisfy(item -> {
            assertThat(item.isAtiva()).isTrue();
            assertThat(item.getExpiraEm()).isEqualTo(item.getAtivadoEm().plusHours(24));
        });
        verify(auditoriaRepository, times(3)).save(any());
    }

    @Test
    void retryComMesmaChaveNaoRevalidaEstadoAtualNemDuplicaAuditoria() {
        UUID anuncioId = UUID.randomUUID();
        AdminUserPrincipal actor = ator();

        var primeira = service.ativar(anuncioId, "story-retry-001", actor, "req-1");
        AnuncioEntity pausadoDepoisDaConclusao = anuncio(anuncioId);
        set(pausadoDepoisDaConclusao, "status", StatusAnuncio.PAUSADO);
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(pausadoDepoisDaConclusao));
        when(elegibilidadeService.listar(anuncioId)).thenReturn(List.of());
        var repetida = service.ativar(anuncioId, "story-retry-001", actor, "req-2");

        assertThat(persistidas).hasSize(1);
        assertThat(repetida.id()).isEqualTo(primeira.id());
        assertThat(repetida.ativadoEm()).isEqualTo(primeira.ativadoEm());
        assertThat(repetida.expiraEm()).isEqualTo(primeira.expiraEm());
        verify(auditoriaRepository).save(any());
    }

    @Test
    void desativarUmAnuncioPreservaOsDemais() {
        UUID anuncioA = UUID.randomUUID();
        UUID anuncioB = UUID.randomUUID();
        service.ativar(anuncioA, "story-remove-a", ator(), "req-a");
        service.ativar(anuncioB, "story-remove-b", ator(), "req-b");

        var removida = service.desativar(anuncioA, ator(), "req-remove");

        assertThat(removida.ativa()).isFalse();
        assertThat(persistidas.stream().filter(StorySelecaoAdministrativaEntity::isAtiva))
                .extracting(StorySelecaoAdministrativaEntity::getAnuncioId)
                .containsExactly(anuncioB);
        assertThat(persistidas.get(0).getAnuncioId()).isEqualTo(anuncioA);
    }

    @Test
    void selecaoExpiradaNaoEConsultada() {
        StorySelecaoAdministrativaEntity expirada = StorySelecaoAdministrativaEntity.nova(
                UUID.randomUUID(),
                ator().usuarioId(),
                OffsetDateTime.ofInstant(AGORA.minusSeconds(90_000), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(AGORA.minusSeconds(3_600), ZoneOffset.UTC),
                "story-expirada");
        set(expirada, "id", ids.getAndIncrement());
        persistidas.add(expirada);

        assertThat(service.consultar()).isEmpty();
    }

    @Test
    void rejeitaAnuncioSemMidiaAprovadaOuNaoPublico() {
        UUID semMidia = UUID.randomUUID();
        when(elegibilidadeService.listar(semMidia)).thenReturn(List.of());
        assertThatThrownBy(() -> service.ativar(
                semMidia, "story-sem-midia", ator(), "req-sem-midia"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("422");

        UUID pausado = UUID.randomUUID();
        AnuncioEntity anuncioPausado = anuncio(pausado);
        set(anuncioPausado, "status", StatusAnuncio.PAUSADO);
        when(anuncioRepository.findById(pausado)).thenReturn(Optional.of(anuncioPausado));
        assertThatThrownBy(() -> service.ativar(
                pausado, "story-pausado", ator(), "req-pausado"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("422");
    }

    private AnuncioEntity anuncio(UUID id) {
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", id);
        set(anuncio, "slug", "anuncio-" + id);
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
