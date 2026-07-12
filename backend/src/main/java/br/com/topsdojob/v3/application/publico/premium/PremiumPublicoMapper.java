package br.com.topsdojob.v3.application.publico.premium;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumPublicoMapper {

    private static final String OCULTAR_IDADE = "OCULTAR_IDADE";
    private static final Set<String> CODIGOS_PUBLICOS_VISIVEIS = Set.of(
            "DESTAQUE",
            "ANUNCIO_TOPO",
            "FOTOS_EXTRA",
            "VIDEO",
            "STORIES",
            "CARROSSEL");
    private static final Set<String> CODIGOS_PUBLICOS = new LinkedHashSet<>();
    private static final Set<String> CODIGOS_MIDIA_EXTRA = Set.of("FOTOS_EXTRA", "VIDEO", "CARROSSEL");

    static {
        CODIGOS_PUBLICOS.addAll(CODIGOS_PUBLICOS_VISIVEIS);
        CODIGOS_PUBLICOS.add(OCULTAR_IDADE);
    }

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
                calculados.stream().anyMatch(this::ocultaIdadeComBeneficioPago),
                codigos.stream()
                        .filter(CODIGOS_PUBLICOS_VISIVEIS::contains)
                        .map(this::rotuloPublico)
                        .distinct()
                        .toList());
    }

    private boolean publicavel(PremiumBeneficioCalculado item) {
        return item.beneficio() != null
                && CODIGOS_PUBLICOS.contains(item.beneficio().getCodigo())
                && (item.status() == PremiumBeneficioStatusCalculado.ATIVO
                || item.status() == PremiumBeneficioStatusCalculado.VENCENDO);
    }

    private boolean ocultaIdadeComBeneficioPago(PremiumBeneficioCalculado item) {
        if (item.beneficio() == null
                || !OCULTAR_IDADE.equals(item.beneficio().getCodigo())
                || item.ativacao() == null) {
            return false;
        }
        OrigemBeneficio origem = item.ativacao().getOrigem();
        if (origem == OrigemBeneficio.COMPRA) {
            return item.ativacao().getPrecoSnapshot() != null
                    && item.ativacao().getPrecoSnapshot().signum() > 0;
        }
        return origem == OrigemBeneficio.CREDITO
                && item.ativacao().getCustoCreditosSnapshot() != null
                && item.ativacao().getCustoCreditosSnapshot() > 0;
    }

    private String rotuloPublico(String codigo) {
        return switch (codigo) {
            case "DESTAQUE" -> "Destaque";
            case "ANUNCIO_TOPO" -> "Topo";
            case "FOTOS_EXTRA", "VIDEO", "CARROSSEL" -> "Mídia extra";
            case "STORIES" -> "Stories";
            default -> "Premium";
        };
    }
}
