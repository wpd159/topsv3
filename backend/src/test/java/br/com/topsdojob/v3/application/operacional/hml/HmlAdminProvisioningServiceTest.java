package br.com.topsdojob.v3.application.operacional.hml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class HmlAdminProvisioningServiceTest {

    private UsuarioRepository usuarioRepository;
    private CredencialUsuarioRepository credencialRepository;
    private PapelUsuarioRepository papelRepository;
    private PasswordEncoder passwordEncoder;
    private HmlAdminProvisioningService service;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        credencialRepository = mock(CredencialUsuarioRepository.class);
        papelRepository = mock(PapelUsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new HmlAdminProvisioningService(
                "homologacao",
                usuarioRepository,
                credencialRepository,
                papelRepository,
                passwordEncoder);
        when(passwordEncoder.encode(any())).thenReturn("hash-bcrypt-seguro");
        when(usuarioRepository.findByEmailNormalizado("admin.hml@example.invalid")).thenReturn(Optional.empty());
        when(papelRepository.findByUsuarioId(any())).thenReturn(List.of());
    }

    @Test
    void criaStaffCredencialComHashEPapelAdmin() {
        var result = service.provisionar("ADMIN.HML@EXAMPLE.INVALID", "Runtime-Seguro-123!");

        assertThat(result.usuarioCriado()).isTrue();
        verify(usuarioRepository).saveAndFlush(any(UsuarioEntity.class));
        verify(credencialRepository).save(any(CredencialUsuarioEntity.class));
        verify(papelRepository).save(any(PapelUsuarioEntity.class));
    }

    @Test
    void recusaEmailNaoFicticioSemPersistir() {
        assertThatThrownBy(() -> service.provisionar("pessoa@example.com", "Runtime-Seguro-123!"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(credencialRepository, never()).save(any());
        verify(papelRepository, never()).save(any());
    }

    @Test
    void reexecucaoAtualizaSomenteHashEReutilizaPapelAdmin() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UsuarioEntity usuario = UsuarioEntity.criarStaffHomologacao(
                java.util.UUID.randomUUID(),
                "Admin HML",
                "admin.hml@example.invalid",
                agora);
        CredencialUsuarioEntity credencial = CredencialUsuarioEntity.criar(
                java.util.UUID.randomUUID(),
                usuario.getId(),
                "hash-anterior",
                agora);
        PapelUsuarioEntity papel = PapelUsuarioEntity.criarAdminHomologacao(usuario.getId(), agora);
        when(usuarioRepository.findByEmailNormalizado("admin.hml@example.invalid"))
                .thenReturn(Optional.of(usuario));
        when(credencialRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(credencial));
        when(papelRepository.findByUsuarioId(usuario.getId())).thenReturn(List.of(papel));

        var result = service.provisionar("admin.hml@example.invalid", "Runtime-Novo-456!");

        assertThat(result.usuarioCriado()).isFalse();
        assertThat(result.papelAdminJaExistia()).isTrue();
        assertThat(credencial.getSenhaHash()).isEqualTo("hash-bcrypt-seguro");
        assertThat(papel.getPapel()).isEqualTo(PapelUsuario.ADMIN);
        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(papelRepository, never()).save(any());
        verify(credencialRepository).save(credencial);
    }

    @Test
    void recusaExecucaoForaDeHomologacao() {
        var producao = new HmlAdminProvisioningService(
                "producao",
                usuarioRepository,
                credencialRepository,
                papelRepository,
                passwordEncoder);

        assertThatThrownBy(() -> producao.provisionar("admin.hml@example.invalid", "Runtime-Seguro-123!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora de homologacao");
    }
}
