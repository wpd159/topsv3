package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import br.com.topsdojob.v3.persistence.repository.CategoriaHomeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CategoriaHomePublicaServiceTest {

  @Test
  void retornaSomenteFonteAtivaOrdenadaDoRepositorio() {
    CategoriaHomeRepository repository = mock(CategoriaHomeRepository.class);
    when(repository.findByAtivoTrueOrderByOrdemAscIdAsc()).thenReturn(List.of(
        categoria("MASSAGENS", "Massagens", "/anuncios?categoria=MASSAGENS", "/cards/massagem.jpg", 1)));

    var categorias = new CategoriaHomePublicaService(repository).listarAtivas();

    assertThat(categorias).singleElement().satisfies(categoria -> {
      assertThat(categoria.identificador()).isEqualTo("MASSAGENS");
      assertThat(categoria.titulo()).isEqualTo("Massagens");
      assertThat(categoria.destino()).isEqualTo("/anuncios?categoria=MASSAGENS");
      assertThat(categoria.imagemPublicaUrl()).isEqualTo("/cards/massagem.jpg");
      assertThat(categoria.ativo()).isTrue();
    });
  }

  @Test
  void rejeitaUrlDeImagemNaoPublica() {
    CategoriaHomeRepository repository = mock(CategoriaHomeRepository.class);
    when(repository.findByAtivoTrueOrderByOrdemAscIdAsc()).thenReturn(List.of(
        categoria("MASSAGENS", "Massagens", "/anuncios?categoria=MASSAGENS", "https://storage.invalid/private", 1)));

    assertThatThrownBy(() -> new CategoriaHomePublicaService(repository).listarAtivas())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("imagem publica");
  }

  private CategoriaHomeEntity categoria(
      String identificador,
      String titulo,
      String destino,
      String imagem,
      int ordem) {
    CategoriaHomeEntity entity = instantiate();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(entity, "categoriaEnum", identificador);
    ReflectionTestUtils.setField(entity, "nome", titulo);
    ReflectionTestUtils.setField(entity, "descricao", "Descricao publica");
    ReflectionTestUtils.setField(entity, "destino", destino);
    ReflectionTestUtils.setField(entity, "imagemPublicaUrl", imagem);
    ReflectionTestUtils.setField(entity, "ordem", ordem);
    ReflectionTestUtils.setField(entity, "ativo", true);
    return entity;
  }

  private CategoriaHomeEntity instantiate() {
    try {
      var constructor = CategoriaHomeEntity.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
