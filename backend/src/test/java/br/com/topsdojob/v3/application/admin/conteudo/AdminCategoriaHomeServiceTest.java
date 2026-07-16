package br.com.topsdojob.v3.application.admin.conteudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.conteudo.dto.CategoriaHomeAdminRequest;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.service.CategoriaHomePublicaService;
import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import br.com.topsdojob.v3.persistence.repository.CategoriaHomeRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AdminCategoriaHomeServiceTest {

  @Test
  void criaCardVendaDeConteudoComVinculoCanonicoEImagemNoObjectStorage() throws Exception {
    Fixture fixture = fixture();
    when(fixture.repository.saveAndFlush(any(CategoriaHomeEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var resposta = fixture.service.criar(
        new CategoriaHomeAdminRequest(
            "VENDA_DE_CONTEUDO",
            "Venda de conteudo e Videochamadas",
            "Videochamadas e conteudo exclusivo.",
            3,
            true),
        imagem());

    assertThat(resposta.categoriaCodigo()).isEqualTo("VENDA_DE_CONTEUDO");
    assertThat(resposta.categoriaNome()).isEqualTo("Sexo Virtual");
    assertThat(resposta.nome()).isEqualTo("Venda de conteudo e Videochamadas");
    assertThat(resposta.imagemUrl()).startsWith("https://public.example.invalid/");
    verify(fixture.storage).put(
        any(StorageArea.class),
        anyString(),
        any(byte[].class),
        anyString());
  }

  @Test
  void recusaSegundoCardParaAMesmaCategoriaCanonica() throws Exception {
    Fixture fixture = fixture();
    when(fixture.repository.existsByCategoriaEnumAndAtivoTrue("MASSAGENS")).thenReturn(true);

    assertThatThrownBy(() -> fixture.service.criar(request("MASSAGENS", 3, true), imagem()))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(fixture.storage, never()).put(any(), anyString(), any(), anyString());
  }

  @Test
  void permiteCardInativoSemViolarAUnicidadeDosCardsPublicos() throws Exception {
    Fixture fixture = fixture();
    when(fixture.repository.saveAndFlush(any(CategoriaHomeEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var resposta = fixture.service.criar(request("MASSAGENS", 8, false), imagem());

    assertThat(resposta.ativo()).isFalse();
    verify(fixture.repository, never()).existsByCategoriaEnumAndAtivoTrue("MASSAGENS");
  }

  @Test
  void editaVinculoParaVendaDeConteudoETrocaImagemSemManterObjetoAnterior() throws Exception {
    Fixture fixture = fixture();
    UUID id = UUID.randomUUID();
    String chaveAnterior = "hml/midias-aprovadas/categorias-home/antiga.webp";
    CategoriaHomeEntity entity = CategoriaHomeEntity.criarAdministrativa(
        id,
        CategoriaAnuncio.MASSAGENS,
        "Massagens",
        "Descricao antiga",
        chaveAnterior,
        3,
        true);
    when(fixture.repository.findById(id)).thenReturn(Optional.of(entity));
    when(fixture.repository.saveAndFlush(entity)).thenReturn(entity);

    var resposta = fixture.service.atualizar(
        id,
        new CategoriaHomeAdminRequest(
            "VENDA_DE_CONTEUDO", "Conteudo online", "Descricao nova", 4, false),
        imagem());

    assertThat(resposta.categoriaCodigo()).isEqualTo("VENDA_DE_CONTEUDO");
    assertThat(resposta.ordem()).isEqualTo(4);
    assertThat(resposta.ativo()).isFalse();
    verify(fixture.storage).delete(StorageArea.PUBLIC_MEDIA, chaveAnterior);
  }

  @Test
  void edicaoReversivelApareceNaHomeERestauraValorOriginal() {
    Fixture fixture = fixture();
    UUID id = UUID.randomUUID();
    CategoriaHomeEntity entity = CategoriaHomeEntity.criarAdministrativa(
        id,
        CategoriaAnuncio.MASSAGENS,
        "Massagens",
        "Descricao publica original.",
        "hml/midias-aprovadas/categorias-home/massagens.webp",
        3,
        true);
    when(fixture.repository.findById(id)).thenReturn(Optional.of(entity));
    when(fixture.repository.saveAndFlush(entity)).thenReturn(entity);
    when(fixture.repository.findByAtivoTrueOrderByOrdemAscIdAsc()).thenReturn(List.of(entity));
    CategoriaHomePublicaService publica = new CategoriaHomePublicaService(
        fixture.repository, fixture.storage);

    fixture.service.atualizar(
        id,
        new CategoriaHomeAdminRequest(
            "MASSAGENS", "Massagens em destaque", "Descricao publica temporaria.", 3, true),
        null);

    assertThat(publica.listarAtivas())
        .singleElement()
        .satisfies(item -> assertThat(item.titulo()).isEqualTo("Massagens em destaque"));

    fixture.service.atualizar(
        id,
        new CategoriaHomeAdminRequest(
            "MASSAGENS", "Massagens", "Descricao publica original.", 3, true),
        null);

    assertThat(publica.listarAtivas())
        .singleElement()
        .satisfies(item -> assertThat(item.titulo()).isEqualTo("Massagens"));
  }

  @Test
  void listaSomenteTaxonomiaCanonicaDoWizard() {
    Fixture fixture = fixture();

    assertThat(fixture.service.listarCategoriasCanonicas())
        .extracting(item -> item.codigo())
        .containsExactly(
            "ACOMPANHANTE_FEMININA",
            "ACOMPANHANTE_MASCULINO",
            "TRANSEX_TRAVESTIS",
            "MASSAGENS",
            "VENDA_DE_CONTEUDO");
  }

  private Fixture fixture() {
    CategoriaHomeRepository repository = mock(CategoriaHomeRepository.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    R2StorageProperties storageProperties = new R2StorageProperties();
    storageProperties.setPublicMediaPrefix("hml/midias-aprovadas/");
    MidiaUploadProperties uploadProperties = new MidiaUploadProperties();
    MidiaUploadValidator validator = new MidiaUploadValidator(uploadProperties);
    when(storage.publicUrl(any(), anyString())).thenAnswer(invocation -> Optional.of(URI.create(
        "https://public.example.invalid/" + invocation.getArgument(1, String.class))));
    return new Fixture(
        repository,
        storage,
        new AdminCategoriaHomeService(repository, validator, storage, storageProperties));
  }

  private CategoriaHomeAdminRequest request(String categoria, int ordem, boolean ativo) {
    return new CategoriaHomeAdminRequest(
        categoria,
        "Massagens",
        "Atendimento de massagem anunciado publicamente.",
        ordem,
        ativo);
  }

  private MockMultipartFile imagem() throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return new MockMultipartFile("imagem", "categoria.png", "image/png", output.toByteArray());
  }

  private record Fixture(
      CategoriaHomeRepository repository,
      ObjectStorage storage,
      AdminCategoriaHomeService service) {
  }
}
