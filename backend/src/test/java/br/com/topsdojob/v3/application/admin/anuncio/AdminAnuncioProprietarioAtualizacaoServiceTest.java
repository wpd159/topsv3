package br.com.topsdojob.v3.application.admin.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
}
