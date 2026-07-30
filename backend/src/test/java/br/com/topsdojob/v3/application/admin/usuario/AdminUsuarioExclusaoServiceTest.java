package br.com.topsdojob.v3.application.admin.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioExclusaoJdbcRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
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
    private AdminUsuarioExclusaoService service;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        exclusaoRepository = mock(AdminUsuarioExclusaoJdbcRepository.class);
        auditorias = mock(AuditoriaEventoRepository.class);
        service = new AdminUsuarioExclusaoService(usuarios, exclusaoRepository, auditorias);
    }

    @Test
    void adminExcluiUsuarioElegivelComAuditoriaSanitizadaEVinculosTecnicos() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEntity usuario = usuario(usuarioId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.PENDENTE);
        when(usuarios.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(exclusaoRepository.bloqueios(usuarioId)).thenReturn(List.of());

        var resultado = service.excluir(
                usuarioId,
                new AdminUsuarioExclusaoRequestDto("EXCLUIR"),
                "delete-user-test-0001",
                admin(),
                "request-delete-user-0001");

        assertThat(resultado.id()).isEqualTo(usuarioId);
        assertThat(resultado.excluido()).isTrue();
        verify(exclusaoRepository).deleteTechnicalLinks(usuarioId);
        verify(usuarios).delete(usuario);
        verify(usuarios).flush();

        ArgumentCaptor<AuditoriaEventoEntity> audit = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditorias).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getAcao()).isEqualTo("USUARIO_EXCLUIDO_FISICAMENTE");
        assertThat(audit.getValue().getRecursoId()).isEqualTo(usuarioId);
        assertThat(audit.getValue().getDepoisJson())
                .contains("\"status\":\"EXCLUIDO\"")
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
    void retryComMesmaChaveRetornaMesmoSucessoSemNovaExclusao() {
        UUID usuarioId = UUID.randomUUID();
        AdminUserPrincipal ator = admin();
        when(exclusaoRepository.exclusaoConcluida(
                org.mockito.ArgumentMatchers.eq(usuarioId),
                org.mockito.ArgumentMatchers.eq(ator.usuarioId()),
                any()))
                .thenReturn(true);

        var resultado = service.excluir(
                usuarioId,
                new AdminUsuarioExclusaoRequestDto("EXCLUIR"),
                "delete-user-retry-0001",
                ator,
                "request-delete-user-retry");

        assertThat(resultado).isEqualTo(
                new AdminUsuarioExclusaoResultadoDto(usuarioId, true));
        verify(usuarios, never()).findByIdForUpdate(any());
        verify(exclusaoRepository, never()).deleteTechnicalLinks(any());
        verify(auditorias, never()).saveAndFlush(any());
    }

    @Test
    void qualquerDependenciaRetorna409SemRemocaoParcial() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioEntity usuario = usuario(usuarioId, TipoContaUsuario.ANUNCIANTE, StatusUsuario.ATIVO);
        when(usuarios.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(exclusaoRepository.bloqueios(usuarioId))
                .thenReturn(List.of("POSSUI_ANUNCIOS", "POSSUI_DOCUMENTOS_KYC"));

        assertThatThrownBy(() -> service.excluir(
                usuarioId,
                new AdminUsuarioExclusaoRequestDto("EXCLUIR"),
                "delete-user-test-0002",
                admin(),
                "request-delete-user-0002"))
                .isInstanceOf(AdminUsuarioExclusaoBloqueadaException.class)
                .satisfies(exception -> assertThat(
                        ((AdminUsuarioExclusaoBloqueadaException) exception).bloqueios())
                        .containsExactly("POSSUI_ANUNCIOS", "POSSUI_DOCUMENTOS_KYC"));

        verify(exclusaoRepository, never()).deleteTechnicalLinks(any());
        verify(auditorias, never()).saveAndFlush(any());
        verify(usuarios, never()).delete(any());
    }

    @Test
    void contaStaffEUsuarioImportadoSaoBloqueados() {
        UUID staffId = UUID.randomUUID();
        UsuarioEntity staff = usuario(staffId, TipoContaUsuario.STAFF, StatusUsuario.ATIVO);
        when(usuarios.findById(staffId))
                .thenReturn(Optional.of(staff));
        when(exclusaoRepository.bloqueios(staffId)).thenReturn(List.of("CONTA_STAFF"));
        assertThat(service.elegibilidade(staffId).bloqueios()).containsExactly("CONTA_STAFF");

        UUID importedId = UUID.randomUUID();
        UsuarioEntity imported = usuario(
                importedId,
                TipoContaUsuario.ANUNCIANTE,
                StatusUsuario.IMPORTADO);
        when(usuarios.findById(importedId))
                .thenReturn(Optional.of(imported));
        when(exclusaoRepository.bloqueios(importedId)).thenReturn(List.of("USUARIO_IMPORTADO"));
        assertThat(service.elegibilidade(importedId).bloqueios())
                .containsExactly("USUARIO_IMPORTADO");
    }

    @Test
    void confirmacaoIdempotenciaEAtorSaoObrigatorios() {
        assertThatThrownBy(() -> service.excluir(
                UUID.randomUUID(),
                new AdminUsuarioExclusaoRequestDto("excluir"),
                "delete-user-test-0003",
                admin(),
                "request-delete-user-0003"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.excluir(
                UUID.randomUUID(),
                new AdminUsuarioExclusaoRequestDto("EXCLUIR"),
                "curta",
                admin(),
                "request-delete-user-0004"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.excluir(
                UUID.randomUUID(),
                new AdminUsuarioExclusaoRequestDto("EXCLUIR"),
                "delete-user-test-0005",
                moderator(),
                "request-delete-user-0005"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
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
