package br.com.topsdojob.v3.importacao.pacote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;

class PacoteEntradaImportacaoTest {

    private static final String CHECKSUM_FICTICIO =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void pacoteSemIdentificadorEInvalido() {
        PacoteEntradaImportacaoDto pacote = pacoteCompleto(null, "2B");

        ResultadoValidacaoPacoteImportacao resultado = new ValidadorPacoteEntradaImportacao().validar(pacote);

        assertThat(resultado.valido()).isFalse();
        assertThat(codigos(resultado)).contains(CodigoPendenciaImportacao.PACOTE_SEM_IDENTIFICADOR);
    }

    @Test
    void pacoteSemVersaoEInvalido() {
        PacoteEntradaImportacaoDto pacote = pacoteCompleto("pacote-ficticio-2b", " ");

        ResultadoValidacaoPacoteImportacao resultado = new ValidadorPacoteEntradaImportacao().validar(pacote);

        assertThat(resultado.valido()).isFalse();
        assertThat(codigos(resultado)).contains(CodigoPendenciaImportacao.PACOTE_SEM_VERSAO);
    }

    @Test
    void pacoteSemArquivoObrigatorioGeraPendencia() {
        PacoteEntradaImportacaoDto pacote = new PacoteEntradaImportacaoDto(
                "pacote-ficticio-2b",
                "2B",
                Instant.parse("2026-01-01T00:00:00Z"),
                "origem-legado-ficticia",
                arquivosSem(TipoArquivoPacoteImportacao.DUMP_BANCO_LEGADO),
                "exemplo sanitizado");

        ResultadoValidacaoPacoteImportacao resultado = new ValidadorPacoteEntradaImportacao().validar(pacote);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.pendencias())
                .anySatisfy(pendencia -> {
                    assertThat(pendencia.codigo())
                            .isEqualTo(CodigoPendenciaImportacao.PACOTE_ARQUIVO_OBRIGATORIO_AUSENTE);
                    assertThat(pendencia.idLegado()).isEqualTo("DUMP_BANCO_LEGADO");
                });
    }

    @Test
    void validadorNaoTentaLerFilesystem() {
        ArquivoPacoteImportacaoDto arquivo = new ArquivoPacoteImportacaoDto(
                TipoArquivoPacoteImportacao.DUMP_BANCO_LEGADO,
                "dump-ficticio",
                StatusArquivoPacoteImportacao.DECLARADO,
                "entrada-logica-nao-abrir/dump-ficticio.sql",
                "sql-dump-sanitizado",
                null,
                CHECKSUM_FICTICIO,
                "2B");
        PacoteEntradaImportacaoDto pacote = new PacoteEntradaImportacaoDto(
                "pacote-ficticio-2b",
                "2B",
                Instant.parse("2026-01-01T00:00:00Z"),
                "origem-legado-ficticia",
                List.of(arquivo),
                "o caminho e apenas texto logico");

        ResultadoValidacaoPacoteImportacao resultado = new ValidadorPacoteEntradaImportacao().validar(pacote);

        assertThat(resultado.pendencias()).isNotEmpty();
        assertThat(codigos(resultado)).doesNotContain(CodigoPendenciaImportacao.PACOTE_DESCRITOR_AUSENTE);
    }

    @Test
    void exemploSanitizadoNaoContemUrlTelefoneCpfOuEmailReal() throws IOException {
        String conteudo = Files.readString(repoRoot().resolve(
                "docs/v3/exemplos/importacao/pacote-entrada-exemplo-sanitizado.json"));

        assertThat(Pattern.compile("https?://").matcher(conteudo).find()).isFalse();
        assertThat(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+").matcher(conteudo).find()).isFalse();
        assertThat(Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b").matcher(conteudo).find()).isFalse();
        assertThat(Pattern.compile("\\b\\d{10,13}\\b").matcher(conteudo).find()).isFalse();
    }

    private static PacoteEntradaImportacaoDto pacoteCompleto(String identificador, String versao) {
        return new PacoteEntradaImportacaoDto(
                identificador,
                versao,
                Instant.parse("2026-01-01T00:00:00Z"),
                "origem-legado-ficticia",
                arquivosSem(),
                "exemplo sanitizado");
    }

    private static List<ArquivoPacoteImportacaoDto> arquivosSem(TipoArquivoPacoteImportacao... ausentes) {
        List<TipoArquivoPacoteImportacao> ignorados = Arrays.asList(ausentes);
        return Arrays.stream(TipoArquivoPacoteImportacao.values())
                .filter(tipo -> !ignorados.contains(tipo))
                .map(tipo -> new ArquivoPacoteImportacaoDto(
                        tipo,
                        tipo.nomeLogicoPadrao(),
                        StatusArquivoPacoteImportacao.DECLARADO,
                        "entrada-logica-nao-abrir/" + tipo.nomeLogicoPadrao(),
                        "jsonl-sanitizado",
                        0L,
                        CHECKSUM_FICTICIO,
                        "2B"))
                .toList();
    }

    private static List<CodigoPendenciaImportacao> codigos(ResultadoValidacaoPacoteImportacao resultado) {
        return resultado.pendencias().stream()
                .map(pendencia -> pendencia.codigo())
                .toList();
    }

    private static Path repoRoot() {
        Path atual = Path.of("").toAbsolutePath();
        if (Files.exists(atual.resolve("docs/v3"))) {
            return atual;
        }
        return atual.getParent();
    }
}
