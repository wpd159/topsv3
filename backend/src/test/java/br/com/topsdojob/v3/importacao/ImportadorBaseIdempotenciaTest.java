package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImportadorBaseIdempotenciaTest {

  private static final Path IMPORTADOR = Path.of(
      "..", "scripts", "local", "importacao", "dryrun-producao-v3-saneado.sql");

  @Test
  void reutilizaSomenteExecucaoConcluidaDoMesmoSnapshot() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("e.status NOT IN ('CONCLUIDA', 'CONCLUIDA_COM_PENDENCIAS')")
        .contains("e.iniciado_em <> c.snapshot_at")
        .contains("(e.resumo_json ->> 'snapshotId') IS DISTINCT FROM c.snapshot_id")
        .contains("(e.resumo_json ->> 'snapshotFingerprint') IS DISTINCT FROM c.snapshot_fingerprint")
        .contains("(e.resumo_json ->> 'storageDestinationFingerprint')")
        .contains("IS DISTINCT FROM c.storage_destination_fingerprint")
        .contains("AS dryrun_snapshot_novo")
        .contains("\\gset");
    assertThat(sql)
        .contains("\\if :dryrun_snapshot_novo")
        .contains("\\endif\n\nCOMMIT;");
  }

  @Test
  void mantemIdDeterministicoSemApagarHistoricoOuSortearNovaExecucao() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("md5('dryrun:snapshot:' || :'snapshot_id')::uuid AS execucao_id")
        .doesNotContain("gen_random_uuid()")
        .doesNotContain("DELETE FROM importacao_execucao")
        .doesNotContain("TRUNCATE importacao_execucao")
        .doesNotContain("ON CONFLICT (id) DO UPDATE");
  }

  @Test
  void snapshotIncluiMetricasEManifestoPrivadoSemPublicacaoAutomatica() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("OPTIONS (user %L)")
        .contains("current_user,\n    current_user")
        .contains("anuncio_view_log")
        .contains("cliques_whatsapp")
        .contains("/tmp/dryrun-r2-private-media.tsv")
        .contains("'visualizacoesCanonicasOrigem'")
        .contains("'eventosVisualizacaoOrigem'")
        .contains("'cliquesWhatsappOrigem'")
        .contains("'anunciosPublicados'")
        .contains("'midiasR2PrivadasLogicasOrigem'")
        .contains("'midiasR2PrivadasLogicasImportadas'")
        .contains("'midiasR2PrivadasLogicasQuarentena'")
        .contains("'midiasR2PrivadasLogicasDivergentes'")
        .contains("'MIDIA_ORIGEM_AUSENTE'")
        .contains("ON CONFLICT (id) DO NOTHING")
        .doesNotContain("OPTIONS (user 'topsv3dry')")
        .doesNotContain("WHEN 'ATIVO' THEN 'PUBLICADO'");
  }

  @Test
  void transportaDadosCadastraisNormalizadosSemIncluiLosNoStagingSanitizado() throws Exception {
    String sql = Files.readString(IMPORTADOR);

    assertThat(sql)
        .contains("nullif(trim(r.nome_completo), '') AS nome_civil_candidato")
        .contains("regexp_replace(coalesce(r.cpf, ''), '[^0-9]', '', 'g') AS cpf_digitos")
        .contains("CREATE OR REPLACE FUNCTION pg_temp.cpf_valido(value text)")
        .contains("pg_temp.cpf_valido(n.cpf_digitos) AS cpf_origem_valido")
        .contains("WHEN r.cpf_origem_valido THEN r.cpf_digitos")
        .contains("'cpfOrigemInvalido', u.cpf_origem_invalido")
        .contains("'USUARIO_CPF_INVALIDO_NAO_PROMOVIDO'")
        .contains(
            "regexp_replace(coalesce(r.telefone, ''), '[^0-9]', '', 'g') AS telefone_digitos")
        .contains("THEN '+55' || r.telefone_digitos")
        .contains("THEN '+' || r.telefone_digitos")
        .contains(
            "id, nome, email_normalizado, telefone_normalizado, status, tipo_conta,")
        .contains(
            "criado_em, criado_em, NULL, 0, data_nascimento, nome_civil, cpf_normalizado")
        .contains("u.telefone_normalizado AS whatsapp_normalizado")
        .contains(
            "a.status_moderacao, a.categoria, false, a.preco, a.whatsapp_normalizado, a.publicado_em")
        .contains("a.categoria AS categoria_origem")
        .contains("WHEN a.categoria = 'VENDA_DE_CONTEUDO' THEN 'ACOMPANHANTE_FEMININA'")
        .contains("WHERE a.categoria_origem = 'VENDA_DE_CONTEUDO'")
        .contains("'usuariosNomeCivilOrigem'")
        .contains("'usuariosCpfOrigem'")
        .contains("'usuariosCpfInvalidosOrigem'")
        .contains("'usuariosTelefoneOrigem'")
        .contains("'anunciosWhatsappOrigem'");

    assertThat(sql.substring(sql.indexOf("INSERT INTO stg_usuario")))
        .doesNotContain("'nomeCivil', u.nome_civil")
        .doesNotContain("'cpfNormalizado', u.cpf_normalizado")
        .doesNotContain("'cpfOrigem', u.cpf_digitos")
        .doesNotContain("'telefoneNormalizado', u.telefone_normalizado")
        .doesNotContain("'whatsappNormalizado', a.whatsapp_normalizado");
  }
}
