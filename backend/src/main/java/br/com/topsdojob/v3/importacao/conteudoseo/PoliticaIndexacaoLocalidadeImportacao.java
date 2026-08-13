package br.com.topsdojob.v3.importacao.conteudoseo;

import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada;
import org.springframework.stereotype.Component;

@Component
public class PoliticaIndexacaoLocalidadeImportacao {

  public Decisao avaliar(LocalidadeSeoLegada localidade) {
    if (!localidade.localidadeExiste()) {
      return Decisao.noindex(Motivo.LOCALIDADE_INEXISTENTE);
    }
    if (!localidade.rotaCanonica()) {
      return Decisao.noindex(Motivo.ROTA_NAO_CANONICA);
    }
    if (!localidade.conteudoValido()) {
      return Decisao.noindex(Motivo.CONTEUDO_INVALIDO);
    }
    if (localidade.duplicada()) {
      return Decisao.noindex(Motivo.CONTEUDO_DUPLICADO);
    }
    if (localidade.filtroTemporario()) {
      return Decisao.noindex(Motivo.FILTRO_TEMPORARIO);
    }
    if (localidade.vazia() || localidade.anunciosPublicosIndexaveis() <= 0) {
      return Decisao.noindex(Motivo.SEM_INVENTARIO_INDEXAVEL);
    }
    if (!localidade.inventarioPublicoSuficiente()
        && !localidade.relevanciaRealComprovada()) {
      return Decisao.noindex(Motivo.RELEVANCIA_INSUFICIENTE);
    }
    return new Decisao(true, true, null);
  }

  public enum Motivo {
    LOCALIDADE_INEXISTENTE,
    ROTA_NAO_CANONICA,
    CONTEUDO_INVALIDO,
    CONTEUDO_DUPLICADO,
    FILTRO_TEMPORARIO,
    SEM_INVENTARIO_INDEXAVEL,
    RELEVANCIA_INSUFICIENTE
  }

  public record Decisao(boolean indexavel, boolean incluirSitemap, Motivo motivo) {
    private static Decisao noindex(Motivo motivo) {
      return new Decisao(false, false, motivo);
    }
  }
}
