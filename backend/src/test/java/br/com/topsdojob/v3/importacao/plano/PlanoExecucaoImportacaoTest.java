package br.com.topsdojob.v3.importacao.plano;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;

class PlanoExecucaoImportacaoTest {

    @Test
    void catalogoContemEtapasMinimas() {
        assertThat(CatalogoPlanoExecucaoImportacao.etapasPadrao())
                .extracting(EtapaImportacaoDto::tipo)
                .containsAll(Arrays.asList(TipoEtapaImportacao.values()));
    }

    @Test
    void anunciosDependemDeUsuariosELocalidades() {
        EtapaImportacaoDto etapa = etapa(TipoEtapaImportacao.PREPARAR_ANUNCIOS);

        assertThat(tiposDependencia(etapa))
                .contains(TipoEtapaImportacao.PREPARAR_USUARIOS, TipoEtapaImportacao.PREPARAR_LOCALIDADES);
    }

    @Test
    void midiaDependeDeAnuncios() {
        EtapaImportacaoDto etapa = etapa(TipoEtapaImportacao.PREPARAR_MIDIAS);

        assertThat(tiposDependencia(etapa))
                .contains(TipoEtapaImportacao.PREPARAR_ANUNCIOS);
    }

    @Test
    void creditosDependemDePagamentosOuAlternativaAuditavel() {
        EtapaImportacaoDto etapa = etapa(TipoEtapaImportacao.PREPARAR_CREDITOS);

        assertThat(tiposDependencia(etapa))
                .contains(TipoEtapaImportacao.PREPARAR_PAGAMENTOS);
    }

    @Test
    void planoTemRelatorioDryRun() {
        assertThat(CatalogoPlanoExecucaoImportacao.etapasPadrao())
                .extracting(EtapaImportacaoDto::tipo)
                .contains(TipoEtapaImportacao.GERAR_RELATORIO_DRY_RUN);
    }

    @Test
    void planoTemBloqueioCriticoAntesDeImportacaoReal() {
        EtapaImportacaoDto etapa = etapa(TipoEtapaImportacao.BLOQUEAR_IMPORTACAO_COM_PENDENCIA_CRITICA);

        assertThat(etapa.bloqueiaImportacaoReal()).isTrue();
        assertThat(etapa.critica()).isTrue();
    }

    @Test
    void validadorNaoAcessaFilesystem() {
        EtapaImportacaoDto etapa = new EtapaImportacaoDto(
                TipoEtapaImportacao.VALIDAR_PACOTE_ENTRADA,
                1,
                StatusEtapaImportacao.APTO_DRY_RUN,
                CriticidadeEtapaImportacao.ALERTA,
                "Caminho logico C:/dump-ficticio/arquivo.sql usado apenas como texto.",
                List.of(),
                List.of(),
                List.of(),
                false);
        PlanoExecucaoImportacaoDto plano = new PlanoExecucaoImportacaoDto(
                "PLANO_TESTE",
                "2E",
                true,
                List.of(etapa),
                "Sem I/O.");

        assertThatCode(() -> new ValidadorPlanoExecucaoImportacao().validar(plano))
                .doesNotThrowAnyException();
    }

    @Test
    void validadorApontaRelatorioDryRunAusente() {
        EtapaImportacaoDto etapa = new EtapaImportacaoDto(
                TipoEtapaImportacao.VALIDAR_PACOTE_ENTRADA,
                1,
                StatusEtapaImportacao.APTO_DRY_RUN,
                CriticidadeEtapaImportacao.BLOQUEANTE,
                "Validar pacote estrutural.",
                List.of(),
                List.of(),
                List.of(),
                false);
        ResultadoPlanoImportacaoDto resultado = new ValidadorPlanoExecucaoImportacao().validar(
                new PlanoExecucaoImportacaoDto("PLANO_INVALIDO", "2E", true, List.of(etapa), null));

        assertThat(codigos(resultado))
                .contains(CodigoPendenciaImportacao.PLANO_SEM_RELATORIO_DRY_RUN);
    }

    private static EtapaImportacaoDto etapa(TipoEtapaImportacao tipo) {
        return CatalogoPlanoExecucaoImportacao.etapasPadrao().stream()
                .filter(etapa -> etapa.tipo() == tipo)
                .findFirst()
                .orElseThrow();
    }

    private static List<TipoEtapaImportacao> tiposDependencia(EtapaImportacaoDto etapa) {
        return etapa.dependencias().stream()
                .map(DependenciaEtapaImportacaoDto::tipoEtapa)
                .toList();
    }

    private static List<CodigoPendenciaImportacao> codigos(ResultadoPlanoImportacaoDto resultado) {
        return resultado.pendencias().stream()
                .map(pendencia -> pendencia.codigo())
                .toList();
    }
}
