package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HistoricoVisualizacoesImportadorTest {

  private static final Path IMPORTADOR = Path.of(
      "..", "scripts", "local", "importacao", "reconciliar-historico-visualizacoes.sql");

  @Test
  void usaFonteEMapeamentoCanonicosSemCriarEventosFicticios() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("FROM legacy.anuncios a")
        .contains("a.visualizacoes::bigint")
        .contains("m.tabela_origem = 'anuncios'")
        .contains("m.entidade_tipo = 'ANUNCIO'")
        .contains("m.status = 'MAPEADO'")
        .contains("a.criado_em AT TIME ZONE 'America/Sao_Paulo'")
        .contains("'FORA_DO_SNAPSHOT'")
        .contains("'NAO_MAPEADO_BLOQUEANTE'")
        .contains("classificacao AS codigo")
        .contains("anuncio_origem_id")
        .contains("anuncios_fora_do_snapshot")
        .contains("anuncios_nao_mapeados_bloqueantes")
        .doesNotContain("a.titulo =")
        .doesNotContain("similarity(")
        .doesNotContain("INSERT INTO evento_visualizacao")
        .doesNotContain("agregado_visualizacao_diaria")
        .doesNotContain("clique_whatsapp")
        .doesNotContain("GA4")
        .doesNotContain("impressao");
  }

  @Test
  void dryRunNaoEscreveEAplicacaoExigeEscopoCompletoEAutorizacao() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("c.modo = 'APLICAR'")
        .contains("historicoVisualizacoesApplyAutorizado")
        .contains("fora_do_snapshot > 0 OR nao_mapeados_bloqueantes > 0 OR NOT apply_autorizado")
        .contains("APPLY proibido: fora do snapshot %, nao mapeados bloqueantes %, autorizacao %")
        .contains("ON CONFLICT (anuncio_id) DO NOTHING")
        .contains("inseridos bigint NOT NULL")
        .contains("preservados");
  }

  @Test
  void retryDivergenteFalhaFechadoSemAtualizacaoSilenciosa() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("i.total_visualizacoes <> o.total_visualizacoes")
        .contains("i.snapshot_fingerprint <> contexto.snapshot_fingerprint")
        .contains("i.origem_hash <> o.origem_hash")
        .contains("i.snapshot_corte_em <> e.iniciado_em")
        .contains("historico ja importado diverge em total, fingerprint, hash ou corte temporal")
        .doesNotContain("UPDATE agregado_visualizacao_inicial")
        .doesNotContain("ON CONFLICT (anuncio_id) DO UPDATE");
  }

  @Test
  void relatorioReconciliaAnunciosETotaisSemContagensHardcoded() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("anuncios_origem")
        .contains("anuncios_escopo")
        .contains("anuncios_mapeados")
        .contains("visualizacoes_escopo")
        .contains("visualizacoes_reconciliaveis")
        .contains("visualizacoes_fora_do_snapshot")
        .contains("visualizacoes_origem")
        .contains("reconciliacao total divergente")
        .contains("total_reconciliado <> total_esperado")
        .doesNotContain("648 AS")
        .doesNotContain("29807 AS")
        .doesNotContain("29805 AS");
  }
}
