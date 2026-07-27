package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;

@Service
public class MidiaPublicaUrlService {

    public static final String PENDENTE_URL_PUBLICA_MIDIA_CDN = "PENDENTE_URL_PUBLICA_MIDIA_CDN";

    private static final String HML_FIXTURE_PROVIDER = "LOCAL_MOCK";
    private static final String HML_FIXTURE_BUCKET = "topsv3-hml-fixture";
    private static final String HML_FIXTURE_PREFIX = "fixture/stories/";
    private static final String HML_FIXTURE_PUBLIC_ASSET = "/demo-safe-public.svg";
    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final String appEnv;
    private final String canonicalDomain;
    private final ObjectProvider<ObjectStorage> storageProvider;
    private final R2StorageProperties storageProperties;
    private final MidiaRestritaDerivacaoService derivacaoService;

    public MidiaPublicaUrlService() {
        this("nao_configurado", "", null, null, null);
    }

    MidiaPublicaUrlService(String appEnv, String canonicalDomain) {
        this(appEnv, canonicalDomain, null, null, null);
    }

    MidiaPublicaUrlService(
            String appEnv,
            String canonicalDomain,
            ObjectProvider<ObjectStorage> storageProvider,
            R2StorageProperties storageProperties) {
        this(appEnv, canonicalDomain, storageProvider, storageProperties, null);
    }

    @Autowired
    public MidiaPublicaUrlService(
            @Value("${app.env:nao_configurado}") String appEnv,
            @Value("${app.canonical-domain:}") String canonicalDomain,
            ObjectProvider<ObjectStorage> storageProvider,
            R2StorageProperties storageProperties,
            MidiaRestritaDerivacaoService derivacaoService) {
        this.appEnv = appEnv;
        this.canonicalDomain = canonicalDomain;
        this.storageProvider = storageProvider;
        this.storageProperties = storageProperties;
        this.derivacaoService = derivacaoService;
    }

    public ResultadoUrlPublica resolverPreviewRestrita(ArquivoMidiaEntity arquivo) {
        if (arquivo == null || derivacaoService == null) {
            return new ResultadoUrlPublica(
                    null,
                    MidiaRestritaDerivacaoService.PENDENTE_DERIVACAO_RESTRITA);
        }
        MidiaRestritaDerivacaoService.ResultadoPreview preview =
                derivacaoService.resolverPreviewPublica(arquivo);
        return new ResultadoUrlPublica(preview.previewUrl(), preview.pendencia());
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
        ResultadoUrlPublica preservada = resolverOrigemPublicaPreservada(vinculo, arquivo);
        if (preservada != null) {
            return preservada;
        }
        ResultadoUrlPublica r2 = resolverR2(vinculo, arquivo);
        if (r2 != null) return r2;
        return new ResultadoUrlPublica(null, PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    private ResultadoUrlPublica resolverR2(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo) {
        if (!"R2".equals(arquivo.getStorageProvider()) || storageProvider == null || storageProperties == null) {
            return null;
        }
        ObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) return null;
        try {
            if (vinculo.getVisibilidadeMidia() == VisibilidadeMidia.LIVRE
                    && storageProperties.getPublicMediaBucket().equals(arquivo.getBucket())
                    && arquivo.getChaveObjeto().startsWith(storageProperties.getPublicMediaPrefix())) {
                return storage.publicUrl(StorageArea.PUBLIC_MEDIA, arquivo.getChaveObjeto())
                        .map(uri -> new ResultadoUrlPublica(uri.toString(), null))
                        .orElseGet(() -> new ResultadoUrlPublica(null, PENDENTE_URL_PUBLICA_MIDIA_CDN));
            }
            if (vinculo.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18
                    && storageProperties.getPrivateMediaBucket().equals(arquivo.getBucket())
                    && arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix())) {
                return new ResultadoUrlPublica(
                        "/api/public/compliance/visitor/media/" + vinculo.getId(),
                        null);
            }
        } catch (RuntimeException ignored) {
            return new ResultadoUrlPublica(null, PENDENTE_URL_PUBLICA_MIDIA_CDN);
        }
        return null;
    }

    private ResultadoUrlPublica resolverOrigemPublicaPreservada(
            AnuncioMidiaEntity vinculo,
            ArquivoMidiaEntity arquivo) {
        if (storageProperties == null
                || vinculo.getVisibilidadeMidia() != VisibilidadeMidia.LIVRE
                || !"R2".equals(arquivo.getStorageProvider())
                || !Objects.equals(storageProperties.getPreservedPublicMediaBucket(), arquivo.getBucket())
                || arquivo.getChaveObjeto() == null
                || arquivo.getMimeType() == null
                || !IMAGE_MIME_TYPES.contains(arquivo.getMimeType().toLowerCase(Locale.ROOT))) {
            return null;
        }
        String prefix = storageProperties.getPreservedPublicMediaPrefix();
        String base = storageProperties.getPreservedPublicBaseUrl();
        if (prefix == null || prefix.isBlank() || base == null || base.isBlank()
                || !arquivo.getChaveObjeto().startsWith(prefix)) {
            return null;
        }
        String nome = arquivo.getChaveObjeto().substring(prefix.length());
        if (!nome.matches("[0-9a-f]{32}\\.(jpg|jpeg|png|webp)")) {
            return null;
        }
        try {
            URI origem = URI.create(base);
            if (!"https".equalsIgnoreCase(origem.getScheme())
                    || origem.getHost() == null
                    || origem.getUserInfo() != null
                    || origem.getQuery() != null
                    || origem.getFragment() != null
                    || !(origem.getPath() == null
                        || origem.getPath().isBlank()
                        || "/".equals(origem.getPath()))) {
                return null;
            }
            String normalizedBase = base.replaceAll("/+$", "");
            return new ResultadoUrlPublica(
                    normalizedBase + "/" + arquivo.getChaveObjeto(),
                    null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
