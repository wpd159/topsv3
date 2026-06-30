package br.com.topsdojob.v3.importacao.saneamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.validacao.ResultadoValidacaoImportacao;

class SaneamentoImportacaoTest {

    @Test
    void catalogoContemRegrasParaEscoposPrincipais() {
        assertThat(CatalogoRegrasSaneamentoImportacao.porEscopo().keySet())
                .containsAll(EnumSet.allOf(EscopoRegraSaneamentoImportacao.class));
    }

    @Test
    void pagamentoExigeClassificacaoPorEvidencia() {
        List<RegraSaneamentoImportacaoDto> regrasPagamento =
                CatalogoRegrasSaneamentoImportacao.porEscopo().get(EscopoRegraSaneamentoImportacao.PAGAMENTO);

        assertThat(regrasPagamento)
                .anySatisfy(regra -> {
                    assertThat(regra.tipo())
                            .isEqualTo(TipoRegraSaneamentoImportacao.MAPEAR_PROVEDOR_PAGAMENTO_POR_EVIDENCIA);
                    assertThat(regra.mencionaEvidencia()).isTrue();
                    assertThat(regra.descricaoSanitizada()).contains("Mercado Pago");
                    assertThat(regra.descricaoSanitizada()).contains("Efi");
                });
    }

    @Test
    void documentoPrivadoNaoEPublico() {
        List<RegraSaneamentoImportacaoDto> regrasDocumento =
                CatalogoRegrasSaneamentoImportacao.porEscopo().get(EscopoRegraSaneamentoImportacao.DOCUMENTO);

        assertThat(regrasDocumento)
                .filteredOn(RegraSaneamentoImportacaoDto::documentoPrivado)
                .allSatisfy(regra -> assertThat(regra.publicaDado()).isFalse());
    }

    @Test
    void gratuitoNaoTemRegraDeLimiteArtificial() {
        assertThat(CatalogoRegrasSaneamentoImportacao.catalogoPadrao())
                .allSatisfy(regra -> assertThat(regra.gratuitoComLimiteArtificial()).isFalse());
    }

    @Test
    void premiumApareceComoPreservado() {
        List<RegraSaneamentoImportacaoDto> regrasPremium =
                CatalogoRegrasSaneamentoImportacao.porEscopo().get(EscopoRegraSaneamentoImportacao.PREMIUM);

        assertThat(regrasPremium)
                .anySatisfy(regra -> {
                    assertThat(regra.tipo()).isEqualTo(TipoRegraSaneamentoImportacao.PRESERVAR_PREMIUM_EXISTENTE);
                    assertThat(regra.preservaExistente()).isTrue();
                });
    }

    @Test
    void validadorNaoTentaAcessarFilesystem() {
        RegraSaneamentoImportacaoDto regra = new RegraSaneamentoImportacaoDto(
                "PACOTE_CAMINHO_LOGICO_NAO_ABRIR",
                TipoRegraSaneamentoImportacao.BLOQUEAR_DADO_REAL_EM_EXEMPLO,
                EscopoRegraSaneamentoImportacao.PACOTE_IMPORTACAO,
                SeveridadeRegraSaneamentoImportacao.ALERTA,
                "Texto declarativo com caminho-logico-nao-abrir/dump-ficticio.sql, sem I/O.",
                true,
                false,
                false,
                false,
                false,
                "PACOTE",
                List.of());

        assertThatCode(() -> new ValidadorRegrasSaneamentoImportacao().validarRegra(regra))
                .doesNotThrowAnyException();
    }

    @Test
    void regraDePagamentoSemEvidenciaGeraPendencia() {
        RegraSaneamentoImportacaoDto regra = new RegraSaneamentoImportacaoDto(
                "PAGAMENTO_INVALIDO",
                TipoRegraSaneamentoImportacao.MAPEAR_PROVEDOR_PAGAMENTO_POR_EVIDENCIA,
                EscopoRegraSaneamentoImportacao.PAGAMENTO,
                SeveridadeRegraSaneamentoImportacao.BLOQUEANTE,
                "Mapear provedor por nome de tabela.",
                false,
                false,
                false,
                false,
                false,
                "PROVEDOR",
                List.of());

        ResultadoValidacaoImportacao resultado = new ValidadorRegrasSaneamentoImportacao().validar(List.of(regra));

        assertThat(codigos(resultado)).contains(CodigoPendenciaImportacao.SANEAMENTO_PAGAMENTO_SEM_EVIDENCIA);
    }

    private static List<CodigoPendenciaImportacao> codigos(ResultadoValidacaoImportacao resultado) {
        return resultado.pendencias().stream()
                .map(pendencia -> pendencia.codigo())
                .toList();
    }
}
