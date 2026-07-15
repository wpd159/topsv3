package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KycDocumentosDryRunImportadorTest {

  @Test
  void promoveSomentePdfPrivadoComoPendenteEQuarentenaAsDemaisReferencias() throws Exception {
    String sql = Files.readString(Path.of(
        "..", "scripts", "local", "importacao", "dryrun-producao-v3-saneado.sql"));
    String kyc = sql.substring(sql.indexOf("-- KYC:"), sql.indexOf("-- Creditos:"));

    assertThat(sql)
        .contains("dryrun-r2-kyc-documents.tsv")
        .contains(":'r2_document_bucket'")
        .contains("kycAprovadoAutomaticamente', 0");
    assertThat(kyc)
        .contains("hml/documentos/importacao/sha256/%")
        .contains("r.mime_type = 'application/pdf'")
        .contains("r.parte = 'UNICO'")
        .contains("r.kyc_status = 'PENDENTE'")
        .contains("'PENDENTE',\n  'ENQUANTO_HOUVER_ANUNCIO'")
        .contains("KYC_REFERENCIA_HTTP_QUARENTENA")
        .contains("KYC_PARTE_DOCUMENTAL_NAO_COMPROVADA")
        .contains("KYC_TIPO_OU_CONTEUDO_INVALIDO")
        .contains("KYC_REFERENCIA_DUPLICADA")
        .contains("'urlPublicaGerada', false")
        .doesNotContain("'VALIDADO'")
        .doesNotContain("'APROVADO'");
  }

  @Test
  void validadorExigeStoragePrivadoSemOrfaoNemReaplicacao() throws Exception {
    String sql = Files.readString(Path.of(
        "..", "scripts", "local", "importacao", "validar-dryrun-producao-v3-saneado.sql"));

    assertThat(sql)
        .contains("documento KYC promovido fora do contrato privado e pendente")
        .contains("documento KYC orfao no fluxo operacional")
        .contains("referencia KYC reaplicada na mesma execucao")
        .contains("documento KYC recebeu indicacao de URL publica")
        .contains("a.bucket <> (SELECT r2_document_bucket FROM validar_context)")
        .contains("a.chave_objeto NOT LIKE 'hml/documentos/importacao/sha256/%'");
  }
}
