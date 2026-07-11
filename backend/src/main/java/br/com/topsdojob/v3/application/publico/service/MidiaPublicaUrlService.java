package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MidiaPublicaUrlService {

    public static final String PENDENTE_URL_PUBLICA_MIDIA_CDN = "PENDENTE_URL_PUBLICA_MIDIA_CDN";

    private static final String HML_FIXTURE_PROVIDER = "LOCAL_MOCK";
    private static final String HML_FIXTURE_BUCKET = "topsv3-hml-fixture";
    private static final String HML_FIXTURE_PREFIX = "fixture/stories/";
    private static final String HML_FIXTURE_PUBLIC_ASSET = "/demo-safe-public.svg";

    private final String appEnv;
    private final String canonicalDomain;

    public MidiaPublicaUrlService() {
        this("nao_configurado", "");
    }

    @Autowired
    public MidiaPublicaUrlService(
            @Value("${app.env:nao_configurado}") String appEnv,
            @Value("${app.canonical-domain:}") String canonicalDomain) {
        this.appEnv = appEnv;
        this.canonicalDomain = canonicalDomain;
    }

    public ResultadoUrlPublica resolver(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo) {
        if (vinculo == null || arquivo == null) {
            return new ResultadoUrlPublica(null, PENDENTE_URL_PUBLICA_MIDIA_CDN);
        }
        if (fixtureHomologacaoSeguro(vinculo, arquivo)) {
            return new ResultadoUrlPublica(
                    canonicalDomain.replaceAll("/+$", "") + HML_FIXTURE_PUBLIC_ASSET,
                    null);
        }
        return new ResultadoUrlPublica(null, PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    private boolean fixtureHomologacaoSeguro(
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo) {
        return "homologacao".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim())
                && canonicalDomain != null
                && canonicalDomain.startsWith("https://")
                && vinculo.getVisibilidadeMidia() == VisibilidadeMidia.LIVRE
                && HML_FIXTURE_PROVIDER.equals(arquivo.getStorageProvider())
                && HML_FIXTURE_BUCKET.equals(arquivo.getBucket())
                && arquivo.getChaveObjeto() != null
                && arquivo.getChaveObjeto().startsWith(HML_FIXTURE_PREFIX);
    }

    public record ResultadoUrlPublica(
            String urlPublica,
            String pendenciaMidia) {
    }
}
