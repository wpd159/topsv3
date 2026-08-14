package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class FonteMidiaRestritaAoManifesto implements FonteMidiaMigracao {

  private final FonteMidiaMigracao delegate;
  private final Set<ChavePermitida> permitidas;

  FonteMidiaRestritaAoManifesto(
      FonteMidiaMigracao delegate,
      ManifestoMidiaFaseCinco manifesto) {
    this.delegate = Objects.requireNonNull(delegate, "fonte obrigatoria");
    this.permitidas = Objects.requireNonNull(manifesto, "manifesto obrigatorio").itens().stream()
        .map(ManifestoMidiaFaseCinco.Item::origem)
        .filter(origem -> origem.tipo() == TipoOrigem.OBJECT_STORAGE)
        .map(origem -> new ChavePermitida(origem.area(), origem.localizador()))
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public br.com.topsdojob.v3.infrastructure.storage.StoredObject carregar(Origem origem) {
    ChavePermitida solicitada = new ChavePermitida(origem.area(), origem.localizador());
    if (!permitidas.contains(solicitada)) {
      throw new FonteMidiaMigracao.OrigemMidiaInvalidaException(
          "chave nao pertence ao manifest validado");
    }
    return delegate.carregar(origem);
  }

  private record ChavePermitida(
      br.com.topsdojob.v3.infrastructure.storage.StorageArea area,
      String chave) {
  }
}
