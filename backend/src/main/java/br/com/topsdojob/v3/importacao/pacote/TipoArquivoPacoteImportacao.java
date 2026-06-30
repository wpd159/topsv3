package br.com.topsdojob.v3.importacao.pacote;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum TipoArquivoPacoteImportacao {
    DUMP_BANCO_LEGADO(true, true, "dump-banco-legado"),
    MANIFESTO_MIDIA(true, true, "manifesto-midia"),
    EXPORT_URLS_PUBLICAS(true, false, "export-urls-publicas"),
    EXPORT_SLUGS(true, false, "export-slugs"),
    EXPORT_METRICAS(true, false, "export-metricas"),
    EXPORT_PAGAMENTOS(true, true, "export-pagamentos"),
    EXPORT_CREDITOS(true, true, "export-creditos"),
    EXPORT_PREMIUM(true, true, "export-premium"),
    EXPORT_BENEFICIOS(true, false, "export-beneficios"),
    EXPORT_BANNERS(true, false, "export-banners"),
    EXPORT_SEO_CONTEUDOS(true, false, "export-seo-conteudos"),
    EXPORT_USUARIOS(true, false, "export-usuarios"),
    EXPORT_ANUNCIOS(true, false, "export-anuncios"),
    EXPORT_LOCALIDADES(true, false, "export-localidades"),
    RELATORIO_ORIGEM(true, false, "relatorio-origem"),
    CHECKSUMS(true, false, "checksums");

    private final boolean obrigatorio;
    private final boolean exigeChecksum;
    private final String nomeLogicoPadrao;

    TipoArquivoPacoteImportacao(boolean obrigatorio, boolean exigeChecksum, String nomeLogicoPadrao) {
        this.obrigatorio = obrigatorio;
        this.exigeChecksum = exigeChecksum;
        this.nomeLogicoPadrao = nomeLogicoPadrao;
    }

    public boolean obrigatorio() {
        return obrigatorio;
    }

    public boolean exigeChecksum() {
        return exigeChecksum;
    }

    public String nomeLogicoPadrao() {
        return nomeLogicoPadrao;
    }

    public static Set<TipoArquivoPacoteImportacao> obrigatorios() {
        return Arrays.stream(values())
                .filter(TipoArquivoPacoteImportacao::obrigatorio)
                .collect(Collectors.toUnmodifiableSet());
    }
}
