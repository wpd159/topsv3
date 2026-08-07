package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdadeAnunciantePublicaServiceTest {

  @Test
  void calculaCorretamenteNaViradaDoAniversario() {
    LocalDate nascimento = LocalDate.of(1990, 7, 12);

    assertThat(IdadeAnunciantePublicaService.calcular(nascimento, LocalDate.of(2026, 7, 11)))
        .isEqualTo(35);
    assertThat(IdadeAnunciantePublicaService.calcular(nascimento, LocalDate.of(2026, 7, 12)))
        .isEqualTo(36);
  }

  @Test
  void anuncioComNascimentoEsemBeneficioMostraIdade() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioRepository repository = mock(UsuarioRepository.class);
    UsuarioEntity usuario = usuario(usuarioId, LocalDate.of(1990, 1, 1));
    when(repository.findById(usuarioId)).thenReturn(Optional.of(usuario));

    var resultado = new IdadeAnunciantePublicaService(repository).resolver(usuarioId, false);

    assertThat(resultado.username()).isEqualTo("perfil-publico");
    assertThat(resultado.idade()).isNotNull().isGreaterThanOrEqualTo(36);
    assertThat(resultado.idadeOculta()).isFalse();
  }

  @Test
  void backendRemoveIdadeQuandoBeneficioPagoAtivoDecidiuOcultar() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioRepository repository = mock(UsuarioRepository.class);
    UsuarioEntity usuario = usuario(usuarioId, LocalDate.of(1990, 1, 1));
    when(repository.findById(usuarioId)).thenReturn(Optional.of(usuario));

    var resultado = new IdadeAnunciantePublicaService(repository).resolver(usuarioId, true);

    assertThat(resultado.username()).isEqualTo("perfil-publico");
    assertThat(resultado.idade()).isNull();
    assertThat(resultado.idadeOculta()).isTrue();
  }

  @Test
  void dtoPublicoNuncaExpoeDataDeNascimento() {
    assertThat(Arrays.stream(AnuncioDetalhePublicoDto.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("dataNascimento")
        .contains("idade", "idadeOculta");
  }

  @Test
  void resolveIdadesEmLoteEOmiteComBeneficioVigente() {
    UUID usuarioVisivelId = UUID.randomUUID();
    UUID usuarioOcultoId = UUID.randomUUID();
    UUID anuncioVisivelId = UUID.randomUUID();
    UUID anuncioOcultoId = UUID.randomUUID();
    UsuarioRepository repository = mock(UsuarioRepository.class);
    UsuarioEntity visivel = usuario(usuarioVisivelId, LocalDate.of(1990, 1, 1));
    UsuarioEntity oculto = usuario(usuarioOcultoId, LocalDate.of(1992, 2, 2));
    when(repository.findAllById(List.of(usuarioVisivelId, usuarioOcultoId)))
        .thenReturn(List.of(visivel, oculto));
    AnuncioEntity anuncioVisivel = anuncio(anuncioVisivelId, usuarioVisivelId);
    AnuncioEntity anuncioOculto = anuncio(anuncioOcultoId, usuarioOcultoId);
    PremiumPublicoFlagsDto flagsOcultos = new PremiumPublicoFlagsDto(
        false, false, true, false, false, true,
        false, false, false, false, List.of());

    Map<UUID, IdadeAnunciantePublicaService.Resultado> resultado =
        new IdadeAnunciantePublicaService(repository).resolverPorAnuncios(
            List.of(anuncioVisivel, anuncioOculto),
            Map.of(anuncioOcultoId, flagsOcultos));

    assertThat(resultado.get(anuncioVisivelId).idade()).isNotNull();
    assertThat(resultado.get(anuncioVisivelId).idadeOculta()).isFalse();
    assertThat(resultado.get(anuncioOcultoId).idade()).isNull();
    assertThat(resultado.get(anuncioOcultoId).idadeOculta()).isTrue();
    verify(repository).findAllById(List.of(usuarioVisivelId, usuarioOcultoId));
  }

  @Test
  void resolveIdentidadeDeStoriesPorContaSemNovaConsultaDeUsuario() {
    UUID usuarioVisivelId = UUID.randomUUID();
    UUID usuarioOcultoId = UUID.randomUUID();
    UsuarioRepository repository = mock(UsuarioRepository.class);
    UsuarioEntity visivel = usuario(usuarioVisivelId, LocalDate.of(1990, 1, 1));
    UsuarioEntity oculto = usuario(usuarioOcultoId, LocalDate.of(1992, 2, 2));

    Map<UUID, IdadeAnunciantePublicaService.Resultado> resultado =
        new IdadeAnunciantePublicaService(repository).resolverPorUsuarios(
            List.of(visivel, oculto),
            java.util.Set.of(usuarioOcultoId));

    assertThat(resultado.get(usuarioVisivelId).idade()).isNotNull();
    assertThat(resultado.get(usuarioVisivelId).idadeOculta()).isFalse();
    assertThat(resultado.get(usuarioOcultoId).idade()).isNull();
    assertThat(resultado.get(usuarioOcultoId).idadeOculta()).isTrue();
    org.mockito.Mockito.verifyNoInteractions(repository);
  }

  private UsuarioEntity usuario(UUID id, LocalDate nascimento) {
    UsuarioEntity usuario = org.mockito.Mockito.mock(UsuarioEntity.class);
    when(usuario.getId()).thenReturn(id);
    when(usuario.getNome()).thenReturn("perfil-publico");
    when(usuario.getDataNascimento()).thenReturn(nascimento);
    return usuario;
  }

  private AnuncioEntity anuncio(UUID id, UUID usuarioId) {
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    when(anuncio.getId()).thenReturn(id);
    when(anuncio.getUsuarioId()).thenReturn(usuarioId);
    return anuncio;
  }
}
