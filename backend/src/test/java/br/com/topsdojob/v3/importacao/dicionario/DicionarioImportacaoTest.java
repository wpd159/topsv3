package br.com.topsdojob.v3.importacao.dicionario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.importacao.pacote.TipoArquivoPacoteImportacao;
import br.com.topsdojob.v3.importacao.validacao.ResultadoValidacaoImportacao;

class DicionarioImportacaoTest {

    @Test
    void catalogoContemDicionarioParaPrincipaisTiposDeArquivo() {
        List<TipoArquivoPacoteImportacao> tiposCatalogados = CatalogoDicionarioImportacao.catalogoPadrao()
                .stream()
                .map(DicionarioArquivoImportacaoDto::tipoArquivo)
                .toList();

        assertThat(tiposCatalogados).containsAll(Arrays.asList(TipoArquivoPacoteImportacao.values()));
    }

    @Test
    void pagamentoPossuiCampoDeProvedorPorEvidencia() {
        DicionarioArquivoImportacaoDto pagamentos = CatalogoDicionarioImportacao
                .porTipo(TipoArquivoPacoteImportacao.EXPORT_PAGAMENTOS)
                .orElseThrow();

        assertThat(pagamentos.campos())
                .anySatisfy(campo -> {
                    assertThat(campo.nomeLogico()).isEqualTo("provedor_evidencia");
                    assertThat(campo.exigeEvidencia()).isTrue();
                    assertThat(campo.sensibilidade()).isEqualTo(SensibilidadeCampoImportacao.FINANCEIRO);
                });
    }

    @Test
    void creditoNaoAceitaDecimal() {
        DicionarioArquivoImportacaoDto invalido = new DicionarioArquivoImportacaoDto(
                TipoArquivoPacoteImportacao.EXPORT_CREDITOS,
                List.of(new CampoPacoteImportacaoDto(
                        "quantidade_creditos",
                        TipoCampoImportacao.NUMERO_DECIMAL,
                        ObrigatoriedadeCampoImportacao.OBRIGATORIO,
                        SensibilidadeCampoImportacao.FINANCEIRO,
                        false,
                        true,
                        true,
                        true,
                        "exemplo invalido")),
                "dicionario invalido para teste");

        ResultadoValidacaoImportacao resultado = new ValidadorDicionarioImportacao().validar(List.of(invalido));

        assertThat(codigos(resultado)).contains(CodigoPendenciaImportacao.DICIONARIO_CAMPO_CREDITO_TIPO_INCOMPATIVEL);
    }

    @Test
    void documentoPrivadoNaoPodeSerPublico() {
        DicionarioArquivoImportacaoDto invalido = new DicionarioArquivoImportacaoDto(
                TipoArquivoPacoteImportacao.EXPORT_USUARIOS,
                List.of(new CampoPacoteImportacaoDto(
                        "documento_privado",
                        TipoCampoImportacao.DOCUMENTO,
                        ObrigatoriedadeCampoImportacao.PENDENTE_EVIDENCIA,
                        SensibilidadeCampoImportacao.PUBLICO,
                        false,
                        false,
                        true,
                        true,
                        "exemplo invalido")),
                "dicionario invalido para teste");

        ResultadoValidacaoImportacao resultado = new ValidadorDicionarioImportacao().validar(List.of(invalido));

        assertThat(codigos(resultado)).contains(CodigoPendenciaImportacao.DICIONARIO_DOCUMENTO_PUBLICO);
    }

    @Test
    void validadorNaoTentaLerFilesystem() {
        DicionarioArquivoImportacaoDto dicionario = new DicionarioArquivoImportacaoDto(
                TipoArquivoPacoteImportacao.MANIFESTO_MIDIA,
                List.of(new CampoPacoteImportacaoDto(
                        "caminho_logico_que_nao_deve_ser_aberto",
                        TipoCampoImportacao.REFERENCIA_MIDIA,
                        ObrigatoriedadeCampoImportacao.OBRIGATORIO,
                        SensibilidadeCampoImportacao.OPERACIONAL,
                        false,
                        true,
                        true,
                        false,
                        "texto declarativo sem I/O")),
                "teste sem filesystem");

        assertThatCode(() -> new ValidadorDicionarioImportacao().validar(List.of(dicionario)))
                .doesNotThrowAnyException();
    }

    private static List<CodigoPendenciaImportacao> codigos(ResultadoValidacaoImportacao resultado) {
        return resultado.pendencias().stream()
                .map(pendencia -> pendencia.codigo())
                .toList();
    }
}
