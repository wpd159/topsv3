package br.com.topsdojob.v3.application.publico.premium;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumPublicoMapper {

    private static final Set<String> CODIGOS_PUBLICOS = Set.of(
            "DESTAQUE",
            "ANUNCIO_TOPO",
            "FOTOS_EXTRA",
            "VIDEO",
            "STORIES",
            "CARROSSEL");
    private static final Set<String> CODIGOS_MIDIA_EXTRA = Set.of("FOTOS_EXTRA", "VIDEO", "CARROSSEL");

    private final BeneficioAnuncioConsultaService beneficioService;

    public PremiumPublicoMapper(BeneficioAnuncioConsultaService beneficioService) {
        this.beneficioService = beneficioService;
    }

    @Transactional(readOnly = true)
    public PremiumPublicoFlagsDto flags(AnuncioEntity anuncio) {
        if (anuncio == null || anuncio.getId() == null) {
            return PremiumPublicoFlagsDto.vazio();
        }
        List<PremiumBeneficioCalculado> calculados = beneficioService.consultarCalculados(anuncio.getId()).stream()
                .filter(this::publicavel)
                .toList();
        if (calculados.isEmpty()) {
            return PremiumPublicoFlagsDto.vazio();
        }
        Set<String> codigos = new LinkedHashSet<>();
        calculados.forEach(item -> codigos.add(item.beneficio().getCodigo()));
        return new PremiumPublicoFlagsDto(
                codigos.contains("DESTAQUE"),
                codigos.contains("ANUNCIO_TOPO"),
                true,
                codigos.contains("STORIES"),
                codigos.stream().anyMatch(CODIGOS_MIDIA_EXTRA::contains),
                codigos.stream().map(this::rotuloPublico).distinct().toList());
    }

    private boolean publicavel(PremiumBeneficioCalculado item) {
        return item.beneficio() != null
                && CODIGOS_PUBLICOS.contains(item.beneficio().getCodigo())
                && (item.status() == PremiumBeneficioStatusCalculado.ATIVO
                || item.status() == PremiumBeneficioStatusCalculado.VENCENDO);
    }

    private String rotuloPublico(String codigo) {
        return switch (codigo) {
            case "DESTAQUE" -> "Destaque";
            case "ANUNCIO_TOPO" -> "Topo";
            case "FOTOS_EXTRA", "VIDEO", "CARROSSEL" -> "Midia extra";
            case "STORIES" -> "Stories";
            default -> "Premium";
        };
    }
}
