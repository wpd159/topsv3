package br.com.topsdojob.v3.application.sugestao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.AlterarStatusRequest;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.CriarRequest;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class SugestaoServiceTest {

    private final MeusAnunciosConsultaService usuarioService =
            mock(MeusAnunciosConsultaService.class);
    private final SugestaoJdbcRepository repository =
            mock(SugestaoJdbcRepository.class);
    private final PublicAuthRateLimiter rateLimiter =
            mock(PublicAuthRateLimiter.class);
    private final AuditoriaEventoRepository auditoriaRepository =
            mock(AuditoriaEventoRepository.class);
    private final Authentication authentication = mock(Authentication.class);
    private final UsuarioEntity usuario = mock(UsuarioEntity.class);
    private final UUID usuarioId = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final SugestaoService service = new SugestaoService(
            usuarioService,
            repository,
            rateLimiter,
            auditoriaRepository);

    @BeforeEach
    void setUp() {
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuario.getId()).thenReturn(usuarioId);
    }

    @Test
    void criaUmaSugestaoSemRegistrarConteudoNaAuditoria() {
        String chave = "sugestao-test-0001";
        SugestaoJdbcRepository.SugestaoRow row = row(
                "PENDENTE", "FEATURE", "Uma melhoria segura", "Descricao suficiente", null);
        when(repository.porIdempotencia(usuarioId, chave))
                .thenReturn(Optional.empty(), Optional.of(row));
        when(repository.inserir(
                any(), eq(usuarioId), anyString(), anyString(), anyString(),
                eq(chave), anyString(), any()))
                .thenReturn(1);

        var criada = service.criar(
                new CriarRequest("FEATURE", "Uma melhoria segura", "Descricao suficiente"),
                chave,
                authentication,
                "request-sugestao-0001");

        assertThat(criada.status()).isEqualTo("PENDENTE");
        assertThat(criada.repetida()).isFalse();
        verify(rateLimiter).require(eq("sugestao-criar"), eq(usuarioId.toString()), eq(5), any());
        verify(auditoriaRepository).save(any());
    }

    @Test
    void retryIdenticoNaoCriaSegundaLinhaNemAuditoria() {
        String chave = "sugestao-test-0002";
        SugestaoJdbcRepository.SugestaoRow row = row(
                "PENDENTE", "BUG", "Erro visual no painel", "Detalhes do erro visual", null);
        when(repository.porIdempotencia(usuarioId, chave)).thenReturn(Optional.of(row));

        var repetida = service.criar(
                new CriarRequest("BUG", "Erro visual no painel", "Detalhes do erro visual"),
                chave,
                authentication,
                "request-sugestao-0002");

        assertThat(repetida.repetida()).isTrue();
        verify(repository, never()).inserir(
                any(), any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void adminAlteraEstadoComLockEHistoricoSemRespostaPublica() {
        UUID id = UUID.fromString("20000000-0000-4000-8000-000000000002");
        UUID adminId = UUID.fromString("30000000-0000-4000-8000-000000000003");
        AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
        when(ator.isEnabled()).thenReturn(true);
        when(ator.usuarioId()).thenReturn(adminId);
        when(repository.porIdComLock(id)).thenReturn(Optional.of(row(
                "PENDENTE", "FEATURE", "Melhoria de busca", "Detalhes da melhoria", null)));
        when(repository.porId(id)).thenReturn(Optional.of(row(
                "EM_ANALISE", "FEATURE", "Melhoria de busca", "Detalhes da melhoria",
                "Analise iniciada")));
        when(repository.historico(id)).thenReturn(List.of());
        when(auditoriaRepository.existsByAcaoAndRecursoIdAndRequestId(
                anyString(), eq(id), anyString())).thenReturn(false);

        var detalhe = service.alterarStatus(
                id,
                new AlterarStatusRequest("EM_ANALISE", "Analise iniciada"),
                ator,
                "request-sugestao-0003");

        assertThat(detalhe.sugestao().status()).isEqualTo("EM_ANALISE");
        verify(repository).atualizarStatus(
                eq(id),
                eq("EM_ANALISE"),
                eq("Analise iniciada"),
                eq(adminId),
                eq(null),
                any());
        verify(auditoriaRepository).save(any());
    }

    @Test
    void decisaoFinalNaoPodeSerSubstituida() {
        UUID id = UUID.fromString("20000000-0000-4000-8000-000000000004");
        AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
        when(ator.isEnabled()).thenReturn(true);
        when(repository.porIdComLock(id)).thenReturn(Optional.of(row(
                "RESOLVIDO", "FEATURE", "Melhoria concluida", "Detalhes da melhoria",
                "Implementada")));

        assertThatThrownBy(() -> service.alterarStatus(
                id,
                new AlterarStatusRequest("RECUSADO", "Decisao substituta"),
                ator,
                "request-sugestao-0004"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(repository, never()).atualizarStatus(
                any(), anyString(), anyString(), any(), any(), any());
    }

    private SugestaoJdbcRepository.SugestaoRow row(
            String status,
            String tipo,
            String titulo,
            String descricao,
            String providencia) {
        OffsetDateTime agora = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        return new SugestaoJdbcRepository.SugestaoRow(
                UUID.fromString("20000000-0000-4000-8000-000000000001"),
                usuarioId,
                tipo,
                titulo,
                descricao,
                status,
                providencia,
                null,
                "sugestao-test-0001",
                "request-sugestao-0001",
                agora,
                agora,
                Set.of("RESOLVIDO", "RECUSADO").contains(status) ? agora : null,
                0,
                "Usuario QA",
                "qa@example.invalid",
                null);
    }
}
