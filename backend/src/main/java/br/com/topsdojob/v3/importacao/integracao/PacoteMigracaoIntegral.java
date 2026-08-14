package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import java.util.Objects;

public record PacoteMigracaoIntegral(
    String pacoteId,
    String versao,
    SnapshotBaseMigracaoIntegral.Snapshot base,
    SnapshotAnunciosFaseUm.Snapshot faseUm,
    SnapshotConteudoSeoFaseDois.Snapshot faseDois,
    SnapshotConfiguracaoComercialFaseTres.Snapshot faseTres,
    SnapshotFinanceiroFaseQuatro.Snapshot faseQuatro,
    ManifestoMidiaFaseCinco faseCinco) {

  public PacoteMigracaoIntegral {
    pacoteId = obrigatorio(pacoteId, "pacoteId");
    versao = obrigatorio(versao, "versao");
    base = Objects.requireNonNull(base, "snapshot base obrigatorio");
    faseUm = Objects.requireNonNull(faseUm, "snapshot da Fase 1 obrigatorio");
    faseDois = Objects.requireNonNull(faseDois, "snapshot da Fase 2 obrigatorio");
    faseTres = Objects.requireNonNull(faseTres, "snapshot da Fase 3 obrigatorio");
    faseQuatro = Objects.requireNonNull(faseQuatro, "snapshot da Fase 4 obrigatorio");
    faseCinco = Objects.requireNonNull(faseCinco, "manifesto da Fase 5 obrigatorio");
    validarFotografia(base, faseUm, faseDois, faseTres, faseQuatro);
  }

  private static void validarFotografia(
      SnapshotBaseMigracaoIntegral.Snapshot base,
      SnapshotAnunciosFaseUm.Snapshot faseUm,
      SnapshotConteudoSeoFaseDois.Snapshot faseDois,
      SnapshotConfiguracaoComercialFaseTres.Snapshot faseTres,
      SnapshotFinanceiroFaseQuatro.Snapshot faseQuatro) {
    if (!base.capturadoEm().equals(faseUm.capturadoEm())
        || !base.capturadoEm().equals(faseDois.capturadoEm())
        || !base.capturadoEm().equals(faseTres.capturadoEm())
        || !base.capturadoEm().equals(faseQuatro.capturadoEm())) {
      throw new IllegalArgumentException("fases pertencem a fotografias diferentes");
    }
  }

  private static String obrigatorio(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return valor.trim();
  }
}
