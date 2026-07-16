package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import br.com.topsdojob.v3.persistence.repository.CategoriaHomeRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CategoriaHomePublicaServiceTest {

  @Test
  void retornaSomenteFonteAtivaOrdenadaComDestinoDerivadoDaCategoria() {
    CategoriaHomeRepository repository = mock(CategoriaHomeRepository.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    CategoriaHomeEntity entity = categoria("MASSAGENS", "/cards/massagem.jpg", null, 1);
    ReflectionTestUtils.setField(entity, "destino", "/destino-livre-proibido");
    when(repository.findByAtivoTrueOrderByOrdemAscIdAsc()).thenReturn(List.of(entity));

    var categorias = new CategoriaHomePublicaService(repository, storage).listarAtivas();

    assertThat(categorias).singleElement().satisfies(categoria -> {
      assertThat(categoria.identificador()).isEqualTo("MASSAGENS");
      assertThat(categoria.titulo()).isEqualTo("Massagens");
      assertThat(categoria.destino()).isEqualTo("/anuncios?categoria=MASSAGENS");
      assertThat(categoria.imagemPublicaUrl()).isEqualTo("/cards/massagem.jpg");
      assertThat(categoria.ativo()).isTrue();
    });
  }

  @Test
  void resolveImagemR2SemExporObjectKeyNoContrato() {
    CategoriaHomeRepository repository = mock(CategoriaHomeRepository.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    String chave = "hml/midias-aprovadas/categorias-home/id/imagem.webp";
    when(repository.findByAtivoTrueOrderByOrdemAscIdAsc())
        .thenReturn(List.of(categoria("MASSAGENS", null, chave, 1)));
    when(storage.publicUrl(StorageArea.PUBLIC_MEDIA, chave))
        .thenReturn(Optional.of(URI.create("https://public.example.invalid/imagem.webp")));

    var categoria = new CategoriaHomePublicaService(repository, storage).listarAtivas().get(0);

    assertThat(categoria.imagemPublicaUrl()).isEqualTo("https://public.example.invalid/imagem.webp");
    assertThat(categoria.toString()).doesNotContain(chave);
  }

  @Test
  void rejeitaImagemR2SemDominioPublico() {
    CategoriaHomeRepository repository = mock(CategoriaHomeRepository.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    String chave = "hml/midias-aprovadas/categorias-home/id/imagem.webp";
    when(repository.findByAtivoTrueOrderByOrdemAscIdAsc())
        .thenReturn(List.of(categoria("MASSAGENS", null, chave, 1)));
    when(storage.publicUrl(StorageArea.PUBLIC_MEDIA, chave)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new CategoriaHomePublicaService(repository, storage).listarAtivas())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dominio publico");
  }

  private CategoriaHomeEntity categoria(
      String identificador,
      String imagemPublica,
      String imagemObjectKey,
      int ordem) {
    CategoriaHomeEntity entity = instantiate();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(entity, "categoriaEnum", identificador);
    ReflectionTestUtils.setField(entity, "nome", "Massagens");
    ReflectionTestUtils.setField(entity, "descricao", "Descricao publica");
    ReflectionTestUtils.setField(entity, "destino", "/anuncios?categoria=" + identificador);
    ReflectionTestUtils.setField(entity, "imagemPublicaUrl", imagemPublica);
    ReflectionTestUtils.setField(entity, "imagemObjectKey", imagemObjectKey);
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
