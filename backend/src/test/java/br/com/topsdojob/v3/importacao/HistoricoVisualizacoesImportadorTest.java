package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HistoricoVisualizacoesImportadorTest {

  private static final Path IMPORTADOR = Path.of(
      "..", "scripts", "local", "importacao", "reconciliar-historico-visualizacoes.sql");

  @Test
  void separaContadorEventosReaisESaldoLegadoSemFabricarDatas() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("a.visualizacoes::bigint AS contador_total")
        .contains("coalesce(e.total_eventos, 0)::bigint AS eventos_detalhados")
        .contains("AS saldo_historico_legado")
        .contains("FROM legacy_metricas.anuncio_view_log v")
        .contains("v.visto_em AT TIME ZONE 'America/Sao_Paulo'")
        .contains("INSERT INTO evento_visualizacao")
        .contains("'import:anuncio_view_log:' || v.id::text")
        .contains("i.total_visualizacoes + (")
        .contains("e.request_id LIKE 'import:anuncio_view_log:%'")
        .doesNotContain("INSERT INTO agregado_visualizacao_diaria")
        .doesNotContain("generate_series")
        .doesNotContain("GA4");
  }

  @Test
  void saldoNegativoBloqueiaApplySemTruncarEvento() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("count(*) FILTER (WHERE saldo_historico_legado < 0)")
        .contains("OR saldos_negativos > 0")
        .contains("'SALDO_NEGATIVO' AS codigo")
        .contains("contador_total")
        .contains("eventos_detalhados")
        .contains("saldo_historico_legado")
        .doesNotContain("greatest(saldo_historico_legado, 0)")
        .doesNotContain("DELETE FROM evento_visualizacao");
  }

  @Test
  void importaCliquesSanitizadosComInstanteEDiaLocalOriginais() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("FROM legacy_metricas.cliques_whatsapp w")
        .contains("w.data_clique AT TIME ZONE 'America/Sao_Paulo' AS criado_em")
        .contains("w.data_clique::date AS dia_local")
        .contains("'import:cliques_whatsapp:' || w.id::text")
        .contains("INSERT INTO clique_whatsapp")
        .contains("NULL,\n    NULL,\n    NULL,")
        .doesNotContain("w.ip")
        .doesNotContain("w.user_agent");
  }

  @Test
  void dryRunNaoEscreveEAplicacaoExigeEscopoIntegroEAutorizacao() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("c.modo = 'APLICAR'")
        .contains("historicoVisualizacoesApplyAutorizado")
        .contains("eventos_nao_mapeados > 0")
        .contains("cliques_nao_mapeados > 0")
        .contains("ON CONFLICT (anuncio_id) DO NOTHING")
        .contains("ON CONFLICT (id) DO NOTHING")
        .contains("agregados_inseridos")
        .contains("eventos_inseridos")
        .contains("cliques_inseridos");
  }

  @Test
  void retryDivergenteFalhaFechadoSemAtualizacaoSilenciosa() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("i.total_visualizacoes <> o.saldo_historico_legado")
        .contains("i.snapshot_fingerprint <> contexto.snapshot_fingerprint")
        .contains("i.origem_hash <> o.origem_hash")
        .contains("evento de visualizacao ja importado diverge da origem sanitizada")
        .contains("clique WhatsApp ja importado diverge da origem sanitizada")
        .doesNotContain("UPDATE agregado_visualizacao_inicial")
        .doesNotContain("ON CONFLICT (anuncio_id) DO UPDATE");
  }

  @Test
  void relatorioReconciliaSemContagensHardcoded() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("contador_explicado_integralmente")
        .contains("saldo_historico_positivo")
        .contains("saldo_historico_negativo")
        .contains("soma_saldos_positivos")
        .contains("visualizacoes_canonicas")
        .contains("eventos_detalhados")
        .contains("cliques_whatsapp")
        .contains("menor_evento_em")
        .contains("maior_clique_em")
        .doesNotContain("30154 AS")
        .doesNotContain("19234 AS")
        .doesNotContain("10920 AS")
        .doesNotContain("2788 AS");
  }
}
