package br.com.topsdojob.v3.application.admin.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioEncerramentoConteudoService.Resultado;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioExclusaoJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioExclusaoJdbcRepository.DependencyAnalysis;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminUsuarioExclusaoServiceTest {

    private UsuarioRepository usuarios;
    private AdminUsuarioExclusaoJdbcRepository exclusaoRepository;
    private AuditoriaEventoRepository auditorias;
    private AdminUsuarioEncerramentoConteudoService conteudo;
    private PublicSessionRegistry sessions;
    private AdminUsuarioExclusaoService service;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        exclusaoRepository = mock(AdminUsuarioExclusaoJdbcRepository.class);
        auditorias = mock(AuditoriaEventoRepository.class);
        conteudo = mock(AdminUsuarioEncerramentoConteudoService.class);
        sessions = mock(PublicSessionRegistry.class);
        service = new AdminUsuarioExclusaoService(
                usuarios,
                exclusaoRepository,
                auditorias,
                conteudo,
                sessions,
                new ObjectMapper());
    }

    @Test
    void contaVaziaUsaExclusaoFisicaComAuditoriaSanitizada() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEntity usuario = usuario(usuarioId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.PENDENTE);
        when(exclusaoRepository.exclusaoConcluida(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(usuarios.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(exclusaoRepository.analisar(usuarioId)).thenReturn(analise(false, false, 0, List.of()));

        var resultado = service.excluir(
                usuarioId,
                request(),
                "delete-user-test-0001",
                admin(),
                "request-delete-user-0001");

        assertThat(resultado).isEqualTo(new AdminUsuarioExclusaoResultadoDto(
                usuarioId,
                true,
                "EXCLUSAO_FISICA",
                false));
        verify(exclusaoRepository).deleteTechnicalLinks(usuarioId);
        verify(usuarios).delete(usuario);
        verify(usuarios).flush();
        verify(conteudo, never()).encerrar(any(), any(), any());
        verify(sessions).invalidateAll(usuarioId);

        ArgumentCaptor<AuditoriaEventoEntity> audit =
                ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditorias).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getAcao()).isEqualTo("USUARIO_EXCLUIDO_FISICAMENTE");
        assertThat(audit.getValue().getDepoisJson())
                .contains("\"estrategia\":\"EXCLUSAO_FISICA\"")
                .contains("\"idempotencyHash\"")
                .doesNotContain(
                        "delete-user-test-0001",
                        "email",
                        "cpf",
                        "telefone",
                        "senha",
                        "token");
    }

    @Test
    void contaImportadaOuComHistoricoUsaAnonimizacaoEPreservaVinculos() {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UsuarioEntity usuario = usuario(usuarioId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.IMPORTADO);
        Resultado encerramento = new Resultado(List.of(anuncioId), 1, 1, false);
        when(exclusaoRepository.exclusaoConcluida(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(usuarios.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(exclusaoRepository.analisar(usuarioId))
                .thenReturn(analise(true, false, 4, List.of(
                        "USUARIO_IMPORTADO",
                        "POSSUI_ANUNCIOS",
                        "POSSUI_DOCUMENTOS_KYC")));
        when(conteudo.encerrar(eq(usuarioId), any(), any())).thenReturn(encerramento);

        var resultado = service.excluir(
                usuarioId,
                request(),
                "delete-user-history-0001",
                admin(),
                "request-delete-user-history");

        assertThat(resultado.tipoExclusao()).isEqualTo("EXCLUSAO_COM_ANONIMIZACAO");
        assertThat(resultado.anonimizado()).isTrue();
        verify(conteudo).encerrar(eq(usuarioId), any(), any());
        verify(exclusaoRepository).deleteTechnicalLinks(usuarioId);
        verify(exclusaoRepository).anonymizeAuxiliaryData(eq(usuarioId), eq(List.of(anuncioId)), any());
        verify(usuario).anonimizarDefinitivamente(any(), any());
        verify(usuarios).saveAndFlush(usuario);
        verify(usuarios, never()).delete(usuario);
        verify(sessions).invalidateAll(usuarioId);
    }

    @Test
    void preValidacaoNaoBloqueiaHistoricoEInformaConsequencias() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEntity usuario = usuario(usuarioId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.ATIVO);
        when(usuarios.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(exclusaoRepository.analisar(usuarioId))
                .thenReturn(analise(false, false, 7, List.of("POSSUI_PAGAMENTOS")));

        var elegibilidade = service.elegibilidade(usuarioId);

        assertThat(elegibilidade.podeExcluir()).isTrue();
        assertThat(elegibilidade.tipoExclusao()).isEqualTo("EXCLUSAO_COM_ANONIMIZACAO");
        assertThat(elegibilidade.anonimizado()).isTrue();
        assertThat(elegibilidade.vinculosPreservados()).isEqualTo(7);
        assertThat(elegibilidade.bloqueios()).isEmpty();
        assertThat(elegibilidade.consequencias())
                .contains("HISTORICOS_FINANCEIROS_E_OPERACIONAIS_PRESERVADOS");
    }

    @Test
    void retryComMesmaChaveRetornaMesmoResultadoSemNovaOperacao() {
        UUID usuarioId = UUID.randomUUID();
        AdminUserPrincipal ator = admin();
        when(exclusaoRepository.exclusaoConcluida(
                eq(usuarioId),
                eq(ator.usuarioId()),
                any()))
                .thenReturn(Optional.of("EXCLUSAO_COM_ANONIMIZACAO"));

        var resultado = service.excluir(
                usuarioId,
                request(),
                "delete-user-retry-0001",
                ator,
                "request-delete-user-retry");

        assertThat(resultado).isEqualTo(new AdminUsuarioExclusaoResultadoDto(
                usuarioId,
                true,
                "EXCLUSAO_COM_ANONIMIZACAO",
                true));
        verify(usuarios, never()).findByIdForUpdate(any());
        verify(exclusaoRepository, never()).deleteTechnicalLinks(any());
        verify(auditorias, never()).saveAndFlush(any());
    }

    @Test
    void titularSemHistoricoUsaExclusaoFisicaCanonica() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEntity usuario = usuario(usuarioId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.ATIVO);
        when(exclusaoRepository.exclusaoConcluidaPorRecurso(eq(usuarioId), any()))
                .thenReturn(Optional.empty());
        when(usuarios.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(exclusaoRepository.analisar(usuarioId)).thenReturn(analise(false, false, 0, List.of()));

        var resultado = service.excluirPeloProprioUsuario(
                usuarioId,
                "delete-own-account-physical",
                "request-own-account-physical");

        assertThat(resultado).isEqualTo(new AdminUsuarioExclusaoResultadoDto(
                usuarioId,
                true,
                "EXCLUSAO_FISICA",
                false));
        verify(exclusaoRepository).deleteTechnicalLinks(usuarioId);
        verify(usuarios).delete(usuario);
        verify(conteudo, never()).encerrar(any(), any(), any());
        verify(sessions).invalidateAll(usuarioId);

        ArgumentCaptor<AuditoriaEventoEntity> audit =
                ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditorias).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getAcao()).isEqualTo("USUARIO_AUTOEXCLUIDO_FISICAMENTE");
        assertThat(audit.getValue().getDepoisJson())
                .contains("\"estrategia\":\"EXCLUSAO_FISICA\"")
                .doesNotContain("delete-own-account-physical");
    }

    @Test
    void titularComHistoricoUsaAnonimizacaoCanonicaEPreservaVinculos() {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UsuarioEntity usuario = usuario(usuarioId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.ATIVO);
        Resultado encerramento = new Resultado(List.of(anuncioId), 1, 1, false);
        when(exclusaoRepository.exclusaoConcluidaPorRecurso(eq(usuarioId), any()))
                .thenReturn(Optional.empty());
        when(usuarios.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(exclusaoRepository.analisar(usuarioId))
                .thenReturn(analise(false, false, 3, List.of(
                        "POSSUI_ANUNCIOS",
                        "POSSUI_LANCAMENTOS_FINANCEIROS")));
        when(conteudo.encerrar(eq(usuarioId), eq(usuarioId), any())).thenReturn(encerramento);

        var resultado = service.excluirPeloProprioUsuario(
                usuarioId,
                "delete-own-account-history",
                "request-own-account-history");

        assertThat(resultado.tipoExclusao()).isEqualTo("EXCLUSAO_COM_ANONIMIZACAO");
        assertThat(resultado.anonimizado()).isTrue();
        verify(conteudo).encerrar(eq(usuarioId), eq(usuarioId), any());
        verify(exclusaoRepository).deleteTechnicalLinks(usuarioId);
        verify(exclusaoRepository).anonymizeAuxiliaryData(eq(usuarioId), eq(List.of(anuncioId)), any());
        verify(usuario).anonimizarDefinitivamente(eq(usuarioId), any());
        verify(usuarios).saveAndFlush(usuario);
        verify(usuarios, never()).delete(usuario);
        verify(sessions).invalidateAll(usuarioId);
    }

    @Test
    void retryDoTitularRetornaResultadoSemRepetirExclusao() {
        UUID usuarioId = UUID.randomUUID();
        when(exclusaoRepository.exclusaoConcluidaPorRecurso(eq(usuarioId), any()))
                .thenReturn(Optional.of("EXCLUSAO_COM_ANONIMIZACAO"));

        var resultado = service.excluirPeloProprioUsuario(
                usuarioId,
                "delete-own-account-retry",
                "request-own-account-retry");

        assertThat(resultado).isEqualTo(new AdminUsuarioExclusaoResultadoDto(
                usuarioId,
                true,
                "EXCLUSAO_COM_ANONIMIZACAO",
                true));
        verify(usuarios, never()).findByIdForUpdate(any());
        verify(exclusaoRepository, never()).deleteTechnicalLinks(any());
        verify(auditorias, never()).saveAndFlush(any());
    }

    @Test
    void chamadaDiretaDeAutoexclusaoContinuaBloqueandoStaff() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEntity staff = usuario(usuarioId, TipoContaUsuario.STAFF, StatusUsuario.ATIVO);
        when(exclusaoRepository.exclusaoConcluidaPorRecurso(eq(usuarioId), any()))
                .thenReturn(Optional.empty());
        when(usuarios.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(staff));
        when(exclusaoRepository.analisar(usuarioId))
                .thenReturn(new DependencyAnalysis(true, false, false, 0, List.of()));

        assertThatThrownBy(() -> service.excluirPeloProprioUsuario(
                usuarioId,
                "delete-own-staff-blocked",
                "request-own-staff-blocked"))
                .isInstanceOf(AdminUsuarioExclusaoBloqueadaException.class);

        verify(exclusaoRepository, never()).deleteTechnicalLinks(any());
        verify(usuarios, never()).delete(any());
        verify(auditorias, never()).saveAndFlush(any());
    }

    @Test
    void staffEOperacaoConcorrenteSaoOsBloqueiosReais() {
        UUID staffId = UUID.randomUUID();
        UsuarioEntity staff = usuario(staffId, TipoContaUsuario.STAFF, StatusUsuario.ATIVO);
        when(usuarios.findById(staffId)).thenReturn(Optional.of(staff));
        when(exclusaoRepository.analisar(staffId))
                .thenReturn(new DependencyAnalysis(true, false, false, 0, List.of()));
        assertThat(service.elegibilidade(staffId).bloqueios()).containsExactly("CONTA_STAFF");

        UUID commonId = UUID.randomUUID();
        UsuarioEntity common = usuario(commonId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.ATIVO);
        when(usuarios.findById(commonId)).thenReturn(Optional.of(common));
        when(exclusaoRepository.analisar(commonId))
                .thenReturn(new DependencyAnalysis(false, false, true, 1, List.of()));
        assertThat(service.elegibilidade(commonId).bloqueios())
                .containsExactly("OPERACAO_CONCORRENTE");
    }

    @Test
    void confirmacaoMotivoIdempotenciaEAtorSaoObrigatorios() {
        assertBadRequest(new AdminUsuarioExclusaoRequestDto("excluir", "Motivo valido"),
                "delete-user-test-0003");
        assertBadRequest(new AdminUsuarioExclusaoRequestDto("EXCLUIR", "x"),
                "delete-user-test-0004");
        assertBadRequest(
                new AdminUsuarioExclusaoRequestDto(
                        "EXCLUIR",
                        "Contato qa@example.invalid deve ser removido"),
                "delete-user-test-0005");
        assertBadRequest(request(), "curta");

        assertThatThrownBy(() -> service.excluir(
                UUID.randomUUID(),
                request(),
                "delete-user-test-0006",
                moderator(),
                "request-delete-user-0006"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void assertBadRequest(AdminUsuarioExclusaoRequestDto request, String idempotencyKey) {
        assertThatThrownBy(() -> service.excluir(
                UUID.randomUUID(),
                request,
                idempotencyKey,
                admin(),
                "request-invalid"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private AdminUsuarioExclusaoRequestDto request() {
        return new AdminUsuarioExclusaoRequestDto("EXCLUIR", "Encerramento da conta QA");
    }

    private DependencyAnalysis analise(
            boolean importado,
            boolean concorrente,
            long vinculos,
            List<String> tipos) {
        return new DependencyAnalysis(false, importado, concorrente, vinculos, tipos);
    }

    private UsuarioEntity usuario(UUID id, TipoContaUsuario tipo, StatusUsuario status) {
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getTipoConta()).thenReturn(tipo);
        when(usuario.getStatus()).thenReturn(status);
        return usuario;
    }

    private AdminUserPrincipal admin() {
        return principal(PapelUsuario.ADMIN);
    }

    private AdminUserPrincipal moderator() {
        return principal(PapelUsuario.MODERADOR);
    }

    private AdminUserPrincipal principal(PapelUsuario papel) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + papel.name()),
                new SimpleGrantedAuthority("ANUNCIO_LER"),
                new SimpleGrantedAuthority("ANUNCIO_MODERAR"));
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Operador",
                "operador@example.invalid",
                "hash",
                List.of(papel),
                List.of(
                        new AdminPermissionDto("ANUNCIO_LER", "ANUNCIO_LER"),
                        new AdminPermissionDto("ANUNCIO_MODERAR", "ANUNCIO_MODERAR")),
                authorities,
                true);
    }
}
