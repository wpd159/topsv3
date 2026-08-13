package br.com.topsdojob.v3.importacao.midia;

import static org.assertj.core.api.Assertions.assertThat;

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
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.RelatorioExecucao;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.ResultadoItem;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.StatusResultado;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliadorMidiaFaseCincoTest {

  @Test
  void fechaEquacaoComImportadasDescartadasEQuarentena() {
    Item imported = item("importada", Decisao.IMPORTAR, null);
    Item discarded = item("descartada", Decisao.DESCARTAR, "ESCOPO_EXCLUIDO");
    Item quarantine = item("quarentena", Decisao.QUARENTENA, "OWNERSHIP_NAO_COMPROVADO");
    ManifestoMidiaFaseCinco manifest = new ManifestoMidiaFaseCinco(
        List.of(imported, discarded, quarantine));
    RelatorioExecucao report = new RelatorioExecucao(
        manifest.sha256(),
        false,
        List.of(
            ResultadoItem.sucesso(imported, StatusResultado.COPIADA),
            ResultadoItem.decisao(discarded, StatusResultado.DESCARTADA),
            ResultadoItem.decisao(quarantine, StatusResultado.QUARENTENA)));

    var reconciliation = new ReconciliadorMidiaFaseCinco().reconciliar(manifest, report);

    assertThat(reconciliation.equacaoFechada()).isTrue();
    assertThat(reconciliation.aprovada()).isTrue();
    assertThat(reconciliation.totalManifesto()).isEqualTo(3);
    assertThat(reconciliation.concluidas()).isEqualTo(1);
    assertThat(reconciliation.bytesValidados()).isEqualTo(8);
    assertThat(reconciliation.equacaoClassificacao()).isTrue();
    assertThat(reconciliation.equacaoExecucao()).isTrue();
    assertThat(reconciliation.equacaoBytes()).isTrue();
    assertThat(reconciliation.porClasse()).containsKeys("RESTRITO", "DESCARTAVEL", "ORFAO_OU_AMBIGUO");
  }

  @Test
  void bloqueioOuItemSemResultadoReprovaReconciliacao() {
    Item imported = item("importada", Decisao.IMPORTAR, null);
    ManifestoMidiaFaseCinco manifest = new ManifestoMidiaFaseCinco(List.of(imported));

    var missing = new ReconciliadorMidiaFaseCinco().reconciliar(
        manifest, new RelatorioExecucao(manifest.sha256(), false, List.of()));
    var blocked = new ReconciliadorMidiaFaseCinco().reconciliar(
        manifest,
        new RelatorioExecucao(
            manifest.sha256(),
            false,
            List.of(ResultadoItem.falha(imported, StatusResultado.BLOQUEADA, "DIVERGENTE"))));

    assertThat(missing.equacaoFechada()).isFalse();
    assertThat(missing.aprovada()).isFalse();
    assertThat(blocked.equacaoExecucao()).isTrue();
    assertThat(blocked.equacaoBytes()).isFalse();
    assertThat(blocked.equacaoFechada()).isFalse();
    assertThat(blocked.aprovada()).isFalse();
  }

  @Test
  void referenciasDuplicadasContamUmObjetoEUmVolumeFisico() {
    Item first = item("ref-1", Decisao.IMPORTAR, null);
    Item second = withDestination(item("ref-2", Decisao.IMPORTAR, null), first.destino());
    ManifestoMidiaFaseCinco manifest = new ManifestoMidiaFaseCinco(List.of(first, second));
    RelatorioExecucao report = new RelatorioExecucao(
        manifest.sha256(),
        false,
        List.of(
            ResultadoItem.sucesso(first, StatusResultado.COPIADA),
            ResultadoItem.sucesso(second, StatusResultado.PRESERVADA)));

    var reconciliation = new ReconciliadorMidiaFaseCinco().reconciliar(manifest, report);

    assertThat(reconciliation.importaveis()).isEqualTo(2);
    assertThat(reconciliation.objetosImportaveis()).isEqualTo(1);
    assertThat(reconciliation.objetosConcluidos()).isEqualTo(1);
    assertThat(reconciliation.bytesImportaveis()).isEqualTo(8);
    assertThat(reconciliation.bytesValidados()).isEqualTo(8);
    assertThat(reconciliation.aprovada()).isTrue();
  }

  private Item item(String id, Decisao decision, String reason) {
    return new Item(
        id,
        EntidadeTipo.ANUNCIO,
        "anuncio-origem",
        "anuncio-v3",
        "usuario-origem",
        "usuario-v3",
        "referencia-" + id,
        Finalidade.GALERIA,
        TipoMidia.FOTO,
        Visibilidade.RESTRITA_18,
        EstadoModeracao.APROVADA,
        true,
        false,
        1,
        "image/jpeg",
        8,
        "a".repeat(64),
        new Origem(TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_MEDIA, "origem/" + id),
        decision == Decisao.IMPORTAR
            ? new Destino(StorageArea.PRIVATE_MEDIA, "bucket", "destino/" + id)
            : null,
        decision,
        reason);
  }

  private Item withDestination(Item item, Destino destination) {
    return new Item(
        item.idOrigem(), item.entidadeTipo(), item.entidadeOrigemId(), item.entidadeV3Id(),
        item.proprietarioOrigemId(), item.proprietarioV3Id(), item.referenciaOrigemId(),
        item.finalidade(), item.tipoMidia(), item.visibilidade(), item.estadoModeracao(),
        item.originalExiste(), item.capaValida(), item.ordem(), item.mimeType(),
        item.tamanhoBytes(), item.sha256(), item.origem(), destination, item.decisao(), item.motivo());
  }
}
