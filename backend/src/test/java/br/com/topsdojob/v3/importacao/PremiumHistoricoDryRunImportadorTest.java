package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PremiumHistoricoDryRunImportadorTest {

    private static final Path IMPORTACAO = Path.of("..", "scripts", "local", "importacao");

    @Test
    void classificaSomenteBeneficiosCanonicosComOrigemComprovada() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));
        String premium = sql.substring(
                sql.indexOf("CREATE TEMP TABLE dryrun_premium_classificado"),
                sql.indexOf("-- Favoritos preservados sem duplicidade."));

        assertThat(premium)
                .contains("OCULTAR_IDADE")
                .contains("FOTOS_EXTRA_5")
                .contains("ANUNCIO_TOPO")
                .contains("WHATSAPP_CARD")
                .contains("CARROSSEL_FOTOS")
                .contains("VIDEO_1")
                .contains("STORIES_FLUXO_PROPRIO")
                .contains("ORIGEM_NAO_COMPROVADA_QUARENTENA")
                .contains("ATIVACAO_DUPLICADA_QUARENTENA")
                .contains("ATIVACAO_SOBREPOSTA_QUARENTENA")
                .contains("'CREDITO'::text AS origem_v3")
                .doesNotContain("INSERT INTO ativacao_beneficio")
                .doesNotContain("INSERT INTO grupo_ativacao_beneficio")
                .doesNotContain("INSERT INTO movimento_credito");
    }

    @Test
    void reconciliadorEhIdempotenteENaoDebitaCreditos() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("reconciliar-premium-historico.sql"));

        assertThat(sql)
                .contains("premium-historico-v1")
                .contains("import:grupo-premium:")
                .contains("import:ativacao-premium:")
                .contains("ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING")
                .contains("movimentos_ledger_antes")
                .contains("saldo_antes")
                .contains("reconciliacao Premium alterou o ledger")
                .doesNotContain("INSERT INTO movimento_credito")
                .doesNotContain("UPDATE movimento_credito")
                .doesNotContain("DELETE FROM movimento_credito");
    }

    @Test
    void validadorExigeZeroDuplicidadeFkInvalidaEAlteracaoNoLedger() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("validar-dryrun-producao-v3-saneado.sql"));

        assertThat(sql)
                .contains("ativacao Premium promovida fora do contrato conservador")
                .contains("ativacoes Premium importadas duplicadas ou sobrepostas")
                .contains("importacao Premium historica criou movimento de credito")
                .contains("PREMIUM_VIGENTE|")
                .contains("PREMIUM_EXPIRADO|")
                .contains("PREMIUM_QUARENTENA|");
    }
}
