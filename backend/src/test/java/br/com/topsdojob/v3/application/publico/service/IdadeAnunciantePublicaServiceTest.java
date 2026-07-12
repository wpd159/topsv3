package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import java.time.LocalDate;
import java.util.Arrays;
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

  private UsuarioEntity usuario(UUID id, LocalDate nascimento) {
    UsuarioEntity usuario = org.mockito.Mockito.mock(UsuarioEntity.class);
    when(usuario.getId()).thenReturn(id);
    when(usuario.getNome()).thenReturn("perfil-publico");
    when(usuario.getDataNascimento()).thenReturn(nascimento);
    return usuario;
  }
}
