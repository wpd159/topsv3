package br.com.topsdojob.v3.application.admin.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioProprietarioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminAnuncioProprietarioAtualizacaoServiceTest {

    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AdminUsuarioAtualizacaoService usuarioAtualizacaoService =
            mock(AdminUsuarioAtualizacaoService.class);
    private final AdminAnuncioProprietarioAtualizacaoService service =
            new AdminAnuncioProprietarioAtualizacaoService(
                    anuncioRepository,
                    usuarioRepository,
                    usuarioAtualizacaoService);

    @Test
    void derivaProprietarioDoAnuncioEDelegaSomenteNomeCivilECpf() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AnuncioEntity anuncio = AnuncioEntity.criarFixtureHomologacao(
                anuncioId,
                usuarioId,
                "anuncio-qa",
                "Anuncio QA",
                "Descricao QA",
                StatusAnuncio.PENDENTE_REVISAO,
                StatusModeracaoAnuncio.PENDENTE,
                agora);
        UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
                usuarioId,
                "Nome de exibicao preservado",
                "usuario-qa@example.invalid",
                "+5562999999999",
                LocalDate.of(1990, 1, 1),
                agora);
        usuario.aplicarDadosKyc(
                "Nome Civil Anterior",
                "12345678909",
                LocalDate.of(1990, 1, 1),
                agora);
        AdminUserPrincipal administrador = mock(AdminUserPrincipal.class);
        AdminUsuarioDetalheDto resposta = mock(AdminUsuarioDetalheDto.class);
        when(anuncioRepository.findUsuarioIdById(anuncioId)).thenReturn(Optional.of(usuarioId));
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(usuarioAtualizacaoService.atualizar(
                eq(usuarioId),
                org.mockito.ArgumentMatchers.any(),
                eq(administrador),
                eq("req-owner"))).thenReturn(resposta);

        var resultado = service.atualizar(
                anuncioId,
                new AdminAnuncioProprietarioAtualizacaoRequest(
                        " Nome Civil Corrigido ",
                        "529.982.247-25"),
                administrador,
                "req-owner");

        assertThat(resultado).isSameAs(resposta);
        InOrder ordem = Mockito.inOrder(anuncioRepository, usuarioRepository, usuarioAtualizacaoService);
        ordem.verify(anuncioRepository).findUsuarioIdById(anuncioId);
        ordem.verify(usuarioRepository).findByIdForUpdate(usuarioId);
        ordem.verify(anuncioRepository).findByIdForModeration(anuncioId);
        ordem.verify(usuarioAtualizacaoService).atualizar(
                eq(usuarioId), any(AdminUsuarioAtualizacaoRequestDto.class),
                eq(administrador), eq("req-owner"));
        ArgumentCaptor<AdminUsuarioAtualizacaoRequestDto> request =
                ArgumentCaptor.forClass(AdminUsuarioAtualizacaoRequestDto.class);
        verify(usuarioAtualizacaoService).atualizar(
                eq(usuarioId),
                request.capture(),
                eq(administrador),
                eq("req-owner"));
        assertThat(request.getValue().getVersao()).isEqualTo(usuario.getVersao());
        assertThat(request.getValue().getNomeCivil()).isEqualTo(" Nome Civil Corrigido ");
        assertThat(request.getValue().getCpf()).isEqualTo("529.982.247-25");
        assertThat(request.getValue().isNomeCivilInformado()).isTrue();
        assertThat(request.getValue().isCpfInformado()).isTrue();
        assertThat(request.getValue().isNomeInformado()).isFalse();
        assertThat(request.getValue().isEmailInformado()).isFalse();
        assertThat(request.getValue().isTelefoneInformado()).isFalse();
        assertThat(request.getValue().isDataNascimentoInformada()).isFalse();
        assertThat(usuario.getNome()).isEqualTo("Nome de exibicao preservado");
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.PENDENTE);
    }

    @Test
    void mudancaDeProprietarioEntreConsultaELockNaoAtualizaOutraConta() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioOriginal = UUID.randomUUID();
        UUID outroUsuario = UUID.randomUUID();
        AnuncioEntity anuncioAlterado = AnuncioEntity.criarFixtureHomologacao(
                anuncioId, outroUsuario, "anuncio-trocado", "Anuncio QA",
                "Descricao QA", StatusAnuncio.PENDENTE_REVISAO,
                StatusModeracaoAnuncio.PENDENTE, OffsetDateTime.now(ZoneOffset.UTC));
        UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
                usuarioOriginal, "Usuario original", "original@example.invalid",
                "+5562999999999", LocalDate.of(1990, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));
        when(anuncioRepository.findUsuarioIdById(anuncioId)).thenReturn(Optional.of(usuarioOriginal));
        when(usuarioRepository.findByIdForUpdate(usuarioOriginal)).thenReturn(Optional.of(usuario));
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncioAlterado));

        assertThatThrownBy(() -> service.atualizar(
                anuncioId,
                new AdminAnuncioProprietarioAtualizacaoRequest("Nome Civil", "529.982.247-25"),
                mock(AdminUserPrincipal.class), "req-owner-changed"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(usuarioRepository).findByIdForUpdate(usuarioOriginal);
        verify(anuncioRepository).findByIdForModeration(anuncioId);
        verifyNoInteractions(usuarioAtualizacaoService);
        verify(usuarioRepository, never()).findByIdForUpdate(outroUsuario);
    }

    @Test
    void usuarioInexistenteImpedeLockDoAnuncioEAtualizacao() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(anuncioRepository.findUsuarioIdById(anuncioId)).thenReturn(Optional.of(usuarioId));

        assertThatThrownBy(() -> service.atualizar(
                anuncioId,
                new AdminAnuncioProprietarioAtualizacaoRequest("Nome Civil", "529.982.247-25"),
                mock(AdminUserPrincipal.class), "req-owner-missing"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(anuncioRepository, never()).findByIdForModeration(anuncioId);
        verifyNoInteractions(usuarioAtualizacaoService);
    }

    @Test
    void anuncioSemProprietarioRetornaConflitoSemTravarUsuarioOuAnuncio() {
        UUID anuncioId = UUID.randomUUID();
        when(anuncioRepository.existsById(anuncioId)).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(
                anuncioId,
                new AdminAnuncioProprietarioAtualizacaoRequest("Nome Civil", "529.982.247-25"),
                mock(AdminUserPrincipal.class), "req-owner-null"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(anuncioRepository).findUsuarioIdById(anuncioId);
        verify(anuncioRepository).existsById(anuncioId);
        verify(anuncioRepository, never()).findByIdForModeration(anuncioId);
        verifyNoInteractions(usuarioRepository, usuarioAtualizacaoService);
    }

    @Test
    void anuncioInexistenteRetornaNaoEncontradoSemTravarUsuario() {
        UUID anuncioId = UUID.randomUUID();

        assertThatThrownBy(() -> service.atualizar(
                anuncioId,
                new AdminAnuncioProprietarioAtualizacaoRequest("Nome Civil", "529.982.247-25"),
                mock(AdminUserPrincipal.class), "req-owner-absent"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(anuncioRepository).findUsuarioIdById(anuncioId);
        verify(anuncioRepository).existsById(anuncioId);
        verify(anuncioRepository, never()).findByIdForModeration(anuncioId);
        verifyNoInteractions(usuarioRepository, usuarioAtualizacaoService);
    }
}
