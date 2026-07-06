package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.persistence.entity.seo.SeoUrlEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.RobotsSeo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoSeoUrl;
import org.springframework.stereotype.Component;

@Component
public class SeoPublicoMapper {

    public SeoRotaPublicaDto fromEntity(SeoUrlEntity seoUrl) {
        String canonicalPath = pathLocalSeguro(coalesce(seoUrl.getCanonicalPath(), seoUrl.getCaminhoPublico()));
        boolean indexavel = Boolean.TRUE.equals(seoUrl.getIndexavel());
        return new SeoRotaPublicaDto(
                titleFor(canonicalPath, seoUrl.getTipo()),
                descriptionFor(canonicalPath, seoUrl.getTipo()),
                canonicalPath,
                robots(indexavel),
                tipoRota(seoUrl.getTipo(), canonicalPath),
                indexavel);
    }

    public SeoRotaPublicaDto fallback(String caminho) {
        String canonicalPath = pathLocalSeguro(caminho);
        TipoSeoUrl tipo = inferirTipo(canonicalPath);
        return new SeoRotaPublicaDto(
                titleFor(canonicalPath, tipo),
                descriptionFor(canonicalPath, tipo),
                canonicalPath,
                robots(false),
                tipoRota(tipo, canonicalPath),
                false);
    }

    private String titleFor(String canonicalPath, TipoSeoUrl tipo) {
        return "Tops do Job - " + rotuloRotaPublica(tipo, canonicalPath);
    }

    private String descriptionFor(String canonicalPath, TipoSeoUrl tipo) {
        return switch (tipoEfetivo(tipo, canonicalPath)) {
            case ANUNCIO -> "Informações públicas do anúncio no Tops do Job.";
            case CIDADE -> "Acompanhantes disponíveis por cidade no Tops do Job.";
            case BAIRRO -> "Acompanhantes disponíveis por bairro no Tops do Job.";
            default -> "Informações públicas do Tops do Job.";
        };
    }

    private String tipoRota(TipoSeoUrl tipo, String canonicalPath) {
        return tipoEfetivo(tipo, canonicalPath).name();
    }

    private String rotuloRotaPublica(TipoSeoUrl tipo, String canonicalPath) {
        return switch (tipoEfetivo(tipo, canonicalPath)) {
            case ANUNCIO -> "Anúncio";
            case CIDADE -> "Cidade";
            case BAIRRO -> "Bairro";
            default -> "Página";
        };
    }

    private TipoSeoUrl tipoEfetivo(TipoSeoUrl tipo, String canonicalPath) {
        if (tipo != null) {
            return tipo;
        }
        return inferirTipo(canonicalPath);
    }

    private TipoSeoUrl inferirTipo(String canonicalPath) {
        if (canonicalPath.startsWith("/anuncios/")) {
            return TipoSeoUrl.ANUNCIO;
        }
        if (canonicalPath.startsWith("/acompanhantes/")) {
            String[] partes = canonicalPath.split("/");
            return partes.length >= 5 ? TipoSeoUrl.BAIRRO : TipoSeoUrl.CIDADE;
        }
        return TipoSeoUrl.OUTRO;
    }

    private String robots(boolean indexavel) {
        return indexavel ? RobotsSeo.INDEX_FOLLOW.name() : RobotsSeo.NOINDEX_FOLLOW.name();
    }

    private String pathLocalSeguro(String path) {
        String normalized = coalesce(path, "/").trim();
        if (normalized.contains("://")) {
            return "/";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
