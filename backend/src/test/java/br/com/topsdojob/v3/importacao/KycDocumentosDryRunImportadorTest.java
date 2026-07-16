package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KycDocumentosDryRunImportadorTest {

  @Test
  void mapeiaStatusKycPelaRegraExibidaNaProducaoSemConfundirConfirmacaoDeEmail() throws Exception {
    String sql = Files.readString(Path.of(
        "..", "scripts", "local", "importacao", "dryrun-producao-v3-saneado.sql"));
    String usuarios = sql.substring(sql.indexOf("-- Usuarios:"), sql.indexOf("-- Localidades:"));
    String kyc = sql.substring(sql.indexOf("-- KYC:"), sql.indexOf("-- Creditos:"));

    assertThat(usuarios)
        .contains("r.advertiser_verification_status::text = 'APROVADO'")
        .contains("r.advertiser_verification_status::text IN ('PENDENTE', 'EM_REVISAO')")
        .contains("r.advertiser_verification_status::text IN ('REPROVADO', 'SUSPENSO')")
        .contains("FROM legacy.usuario_documentos d")
        .contains("WHERE d.usuario_id = r.id")
        .contains("'Importacao KYC'")
        .contains("'SISTEMA'")
        .doesNotContain("is_verificado");
    assertThat(kyc)
        .contains("CASE u.kyc_status_origem")
        .contains("WHEN 'APROVADO' THEN 'VALIDADO'")
        .contains("WHEN 'REPROVADO' THEN 'REJEITADO'")
        .contains("coalesce(revisor.id, c.kyc_migration_actor_id)")
        .contains("coalesce(revisao.reviewed_at AT TIME ZONE 'America/Sao_Paulo', c.snapshot_at)")
        .doesNotContain("coalesce(nullif(r.kyc_status, ''), canonico.kyc_status)");
  }

  @Test
  void promoveDocumentoPrivadoTipadoPreservaStatusEConsolidaDuplicatas() throws Exception {
    String sql = Files.readString(Path.of(
        "..", "scripts", "local", "importacao", "dryrun-producao-v3-saneado.sql"));
    String kyc = sql.substring(sql.indexOf("-- KYC:"), sql.indexOf("-- Creditos:"));

    assertThat(sql)
        .contains("dryrun-r2-kyc-documents.tsv")
        .contains(":'r2_document_bucket'")
        .contains("kycAprovadoAutomaticamente', 0");
    assertThat(kyc)
        .contains("hml/documentos/importacao/%/sha256/%")
        .contains("r.mime_type = 'application/pdf'")
        .contains("r.mime_type = 'image/jpeg'")
        .contains("r.mime_type = 'image/png'")
        .contains("r.parte IN ('FRENTE', 'VERSO')")
        .contains("r.kyc_status IN ('PENDENTE', 'VALIDADO', 'REJEITADO')")
        .contains("k.kyc_status,\n  'ENQUANTO_HOUVER_ANUNCIO'")
        .contains("r.reference_hash = r.canonical_reference_hash")
        .contains("conteudoDuplicadoConsolidado")
        .contains("KYC_REFERENCIA_HTTP_QUARENTENA")
        .contains("KYC_PARTE_DOCUMENTAL_NAO_COMPROVADA")
        .contains("KYC_TIPO_OU_CONTEUDO_INVALIDO")
        .contains("KYC_REFERENCIA_DUPLICADA")
        .contains("'urlPublicaGerada', false");
  }

  @Test
  void validadorExigeStoragePrivadoSemOrfaoNemReaplicacao() throws Exception {
    String sql = Files.readString(Path.of(
        "..", "scripts", "local", "importacao", "validar-dryrun-producao-v3-saneado.sql"));

    assertThat(sql)
        .contains("documento KYC promovido fora do contrato privado, historico e tipado")
        .contains("documento KYC orfao no fluxo operacional")
        .contains("referencia KYC reaplicada na mesma execucao")
        .contains("documento KYC recebeu indicacao de URL publica")
        .contains("mapeamento KYC de usuarios possui status invalido ou contagem duplicada")
        .contains("ator tecnico da migracao KYC recebeu credencial")
        .contains("usuario KYC aprovado na origem ficaria sem aprovacao operacional")
        .contains("a.bucket <> (SELECT r2_document_bucket FROM validar_context)")
        .contains("a.chave_objeto NOT LIKE 'hml/documentos/importacao/%/sha256/%'");
  }
}
