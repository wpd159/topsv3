package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class MeuAnuncioCicloVidaServiceTest {

    private static final UUID USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000001101");
    private static final UUID OUTRO_USUARIO_ID = UUID.fromString("00000000-0000-4000-8000-000000001102");
    private static final UUID ANUNCIO_ID = UUID.fromString("00000000-0000-4000-8000-000000001111");
    private static final OffsetDateTime CRIADO_EM = OffsetDateTime.of(
            2026, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private MeusAnunciosConsultaService consultaService;
    private AnuncioRepository anuncioRepository;
    private AuditoriaEventoRepository auditoriaRepository;
    private FotoElegivelAnuncioPolicy fotoElegivelPolicy;
    private Authentication authentication;
    private MeuAnuncioCicloVidaService service;

    @BeforeEach
    void setUp() {
        consultaService = mock(MeusAnunciosConsultaService.class);
        anuncioRepository = mock(AnuncioRepository.class);
        auditoriaRepository = mock(AuditoriaEventoRepository.class);
        fotoElegivelPolicy = mock(FotoElegivelAnuncioPolicy.class);
        authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(consultaService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(consultaService.slugSeguro(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(consultaService.acoesPermitidas(any())).thenAnswer(invocation -> {
            AnuncioEntity anuncio = invocation.getArgument(0);
            return new br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAcoesDto(
                    anuncio.podePausarPeloProprietario(),
                    anuncio.podeReativarPeloProprietario(),
                    anuncio.podeRemoverPeloProprietario(),
                    false);
        });
        service = new MeuAnuncioCicloVidaService(
                consultaService,
                anuncioRepository,
                auditoriaRepository,
                new ObjectMapper(),
                mock(AnuncioStatusHistoricoRepository.class),
                mock(DocumentoBuscaAnuncioRepository.class),
                mock(RevisaoAnuncioRepository.class),
                fotoElegivelPolicy);
    }

    @Test
    void proprietarioPausaSemAlterarPublicacaoERegistraAuditoria() {
        AnuncioEntity anuncio = anuncio(USUARIO_ID, "perfil-publicado", StatusAnuncio.PUBLICADO);
        OffsetDateTime publicadoEm = anuncio.getPublicadoEm();
        OffsetDateTime ultimaPublicacaoEm = anuncio.getUltimaPublicacaoEm();
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-publicado", authentication)).thenReturn(anuncio);

        var resultado = service.pausar("perfil-publicado", authentication, "request-pausa");

        assertThat(resultado.status()).isEqualTo("PAUSADO");
        assertThat(resultado.acoesPermitidas().reativar()).isTrue();
        assertThat(anuncio.getPublicadoEm()).isEqualTo(publicadoEm);
        assertThat(anuncio.getUltimaPublicacaoEm()).isEqualTo(ultimaPublicacaoEm);
        assertThat(anuncio.getRemovidoEm()).isNull();
        AuditoriaEventoEntity auditoria = auditoriaSalva();
        assertThat(auditoria.getAtorUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(auditoria.getAcao()).isEqualTo("ANUNCIO_PAUSADO_PELO_USUARIO");
        assertThat(auditoria.getRecursoId()).isEqualTo(ANUNCIO_ID);
        assertThat(auditoria.getRequestId()).isEqualTo("request-pausa");
        assertThat(auditoria.getCriadoEm()).isNotNull();
    }

    @Test
    void proprietarioReativaSemRestaurarDataOuPrioridade() {
        AnuncioEntity anuncio = anuncio(USUARIO_ID, "perfil-pausado", StatusAnuncio.PUBLICADO);
        anuncio.pausarPeloProprietario(CRIADO_EM.plusDays(1));
        OffsetDateTime publicadoEm = anuncio.getPublicadoEm();
        OffsetDateTime ultimaPublicacaoEm = anuncio.getUltimaPublicacaoEm();
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-pausado", authentication)).thenReturn(anuncio);

        var resultado = service.reativar("perfil-pausado", authentication, "request-reativar");

        assertThat(resultado.status()).isEqualTo("PUBLICADO");
        assertThat(resultado.acoesPermitidas().pausar()).isTrue();
        assertThat(anuncio.getPublicadoEm()).isEqualTo(publicadoEm);
        assertThat(anuncio.getUltimaPublicacaoEm()).isEqualTo(ultimaPublicacaoEm);
        assertThat(auditoriaSalva().getAcao()).isEqualTo("ANUNCIO_REATIVADO_PELO_USUARIO");
        var ordem = inOrder(consultaService, fotoElegivelPolicy, anuncioRepository);
        ordem.verify(consultaService).anuncioDoUsuarioParaAtualizacao("perfil-pausado", authentication);
        ordem.verify(fotoElegivelPolicy).validarParaReativacao(ANUNCIO_ID);
        ordem.verify(anuncioRepository).save(anuncio);
        verify(fotoElegivelPolicy, never()).validarParaAprovacao(any());
    }

    @Test
    void reativacaoSemAprovadaElegivelRecusaSemAlterarAnuncioOuAuditoria() {
        AnuncioEntity anuncio = anuncio(USUARIO_ID, "perfil-sem-aprovada", StatusAnuncio.PAUSADO);
        OffsetDateTime atualizadoEm = anuncio.getAtualizadoEm();
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-sem-aprovada", authentication))
                .thenReturn(anuncio);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                FotoElegivelAnuncioPolicy.MENSAGEM_SEM_FOTO_APROVADA_REATIVACAO))
                .when(fotoElegivelPolicy).validarParaReativacao(ANUNCIO_ID);

        assertThatThrownBy(() -> service.reativar("perfil-sem-aprovada", authentication, "req-sem-aprovada"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(409);
                    assertThat(error.getReason()).isEqualTo(
                            FotoElegivelAnuncioPolicy.MENSAGEM_SEM_FOTO_APROVADA_REATIVACAO);
                });

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PAUSADO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(anuncio.getAtualizadoEm()).isEqualTo(atualizadoEm);
        assertThat(anuncio.getRemovidoEm()).isNull();
        verify(anuncioRepository, never()).save(any());
        verify(auditoriaRepository, never()).save(any());
        verify(fotoElegivelPolicy, never()).validarParaAprovacao(any());
    }

    @Test
    void proprietarioRemoveSomenteLogicamenteSemDeleteFisico() {
        AnuncioEntity anuncio = anuncio(USUARIO_ID, "perfil-removido", StatusAnuncio.PAUSADO);
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-removido", authentication)).thenReturn(anuncio);

        var resultado = service.remover("perfil-removido", authentication, "request-remover");

        assertThat(resultado.status()).isEqualTo("REMOVIDO");
        assertThat(resultado.acoesPermitidas().pausar()).isFalse();
        assertThat(resultado.acoesPermitidas().reativar()).isFalse();
        assertThat(resultado.acoesPermitidas().remover()).isFalse();
        assertThat(anuncio.getRemovidoEm()).isNotNull();
        verify(anuncioRepository).save(anuncio);
        verify(anuncioRepository, never()).delete(any(AnuncioEntity.class));
        verify(anuncioRepository, never()).deleteById(any(UUID.class));
        assertThat(auditoriaSalva().getAcao()).isEqualTo("ANUNCIO_REMOVIDO_PELO_USUARIO");
    }

    @Test
    void anuncioDeOutroProprietarioRetorna403() {
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-terceiro", authentication))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "anuncio pertence a outro usuario"));

        assertStatus(403, () -> service.pausar("perfil-terceiro", authentication, "request-403"));

        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void slugInexistenteRetorna404() {
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-ausente", authentication))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "anuncio nao encontrado"));

        assertStatus(404, () -> service.remover("perfil-ausente", authentication, "request-404"));

        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void transicaoInvalidaERetryRepetidoRetornam409SemAuditoriaDuplicada() {
        AnuncioEntity anuncio = anuncio(USUARIO_ID, "perfil-retry", StatusAnuncio.PUBLICADO);
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-retry", authentication)).thenReturn(anuncio);

        service.pausar("perfil-retry", authentication, "request-primeiro");
        assertStatus(409, () -> service.pausar("perfil-retry", authentication, "request-repetido"));

        verify(auditoriaRepository, times(1)).save(any());
        verify(anuncioRepository, atMostOnce()).save(anuncio);
    }

    @Test
    void reativacaoSemModeracaoAprovadaRetorna409() {
        AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID,
                USUARIO_ID,
                "perfil-pendente",
                "Titulo perfil pendente",
                "Descricao suficiente",
                StatusAnuncio.PAUSADO,
                StatusModeracaoAnuncio.PENDENTE,
                CRIADO_EM);
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-pendente", authentication)).thenReturn(anuncio);

        assertStatus(409, () -> service.reativar("perfil-pendente", authentication, "request-409"));

        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void anuncioBloqueadoJuridicamenteNaoPodeSerMutadoPeloProprietario() {
        AnuncioEntity anuncio = anuncio(USUARIO_ID, "perfil-bloqueado", StatusAnuncio.BLOQUEADO);
        when(consultaService.anuncioDoUsuarioParaAtualizacao("perfil-bloqueado", authentication)).thenReturn(anuncio);

        assertStatus(409, () -> service.pausar("perfil-bloqueado", authentication, "request-pausa"));
        assertStatus(409, () -> service.reativar("perfil-bloqueado", authentication, "request-reativar"));
        assertStatus(409, () -> service.remover("perfil-bloqueado", authentication, "request-remover"));

        verify(anuncioRepository, never()).save(anuncio);
        verify(anuncioRepository, never()).delete(any(AnuncioEntity.class));
        verify(auditoriaRepository, never()).save(any());
    }

    private AnuncioEntity anuncio(UUID usuarioId, String slug, StatusAnuncio status) {
        return AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID,
                usuarioId,
                slug,
                "Titulo " + slug,
                "Descricao suficiente",
                status,
                StatusModeracaoAnuncio.APROVADO,
                CRIADO_EM);
    }

    private AuditoriaEventoEntity auditoriaSalva() {
        ArgumentCaptor<AuditoriaEventoEntity> captor = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertStatus(int status, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(status));
    }
}
