package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FonteMidiaRestritaAoManifestoTest {

  @Test
  void permiteSomenteParAreaChaveExatoDoManifestoValidado() {
    AtomicInteger leituras = new AtomicInteger();
    FonteMidiaMigracao delegate = ignored -> {
      leituras.incrementAndGet();
      return new StoredObject(new byte[] {1}, "image/jpeg");
    };
    Origem permitida = new Origem(
        TipoOrigem.OBJECT_STORAGE,
        StorageArea.PUBLIC_MEDIA,
        "uploads/legado/foto.jpg");
    FonteMidiaMigracao fonte = new FonteMidiaRestritaAoManifesto(
        delegate,
        new ManifestoMidiaFaseCinco(List.of(item(permitida))));

    assertThat(fonte.carregar(permitida).content()).containsExactly(1);
    assertThatThrownBy(() -> fonte.carregar(new Origem(
        TipoOrigem.OBJECT_STORAGE,
        StorageArea.PRIVATE_MEDIA,
        permitida.localizador())))
        .isInstanceOf(FonteMidiaMigracao.OrigemMidiaInvalidaException.class);
    assertThatThrownBy(() -> fonte.carregar(new Origem(
        TipoOrigem.OBJECT_STORAGE,
        StorageArea.PUBLIC_MEDIA,
        "uploads/legado/nao-listada.jpg")))
        .isInstanceOf(FonteMidiaMigracao.OrigemMidiaInvalidaException.class);
    assertThat(leituras).hasValue(1);
  }

  private static Item item(Origem origem) {
    return new Item(
        "midia-1",
        EntidadeTipo.ANUNCIO,
        "anuncio-1",
        null,
        "usuario-1",
        null,
        "foto-1",
        Finalidade.GALERIA,
        TipoMidia.FOTO,
        Visibilidade.LIVRE,
        EstadoModeracao.APROVADA,
        true,
        false,
        0,
        "image/jpeg",
        1,
        "a".repeat(64),
        origem,
        new Destino(StorageArea.PUBLIC_MEDIA, "public-target", "hml/publico/foto.jpg"),
        Decisao.IMPORTAR,
        null);
  }
}
