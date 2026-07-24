package br.com.topsdojob.v3.application.publico.premium;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.ANUNCIO_TOPO;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.CARROSSEL_FOTOS;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.OCULTAR_IDADE;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.VIDEO_1;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.WHATSAPP_CARD;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumPublicoMapper {

    private static final Set<String> CODIGOS_PUBLICOS_VISIVEIS = Set.of(
            ANUNCIO_TOPO,
            FOTOS_EXTRA_5,
            VIDEO_1,
            WHATSAPP_CARD,
            CARROSSEL_FOTOS);

    private final BeneficioAnuncioConsultaService beneficioService;

    public PremiumPublicoMapper(BeneficioAnuncioConsultaService beneficioService) {
        this.beneficioService = beneficioService;
    }

    @Transactional(readOnly = true)
    public PremiumPublicoFlagsDto flags(AnuncioEntity anuncio) {
        if (anuncio == null || anuncio.getId() == null) {
            return PremiumPublicoFlagsDto.vazio();
        }
        return flags(beneficioService.consultarCalculados(anuncio.getId()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, PremiumPublicoFlagsDto> flagsPorAnuncios(Collection<AnuncioEntity> anuncios) {
        if (anuncios == null || anuncios.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<PremiumBeneficioCalculado>> calculados = beneficioService.consultarCalculadosPorAnuncio(
                anuncios.stream().map(AnuncioEntity::getId).toList());
        return anuncios.stream().collect(Collectors.toMap(
                AnuncioEntity::getId,
                anuncio -> flags(calculados.getOrDefault(anuncio.getId(), List.of())),
                (primeiro, ignorado) -> primeiro));
    }

    private PremiumPublicoFlagsDto flags(List<PremiumBeneficioCalculado> todos) {
        List<PremiumBeneficioCalculado> calculados = todos.stream()
                .filter(this::publicavel)
                .toList();
        if (calculados.isEmpty()) {
            return PremiumPublicoFlagsDto.vazio();
        }
        Set<String> codigos = new LinkedHashSet<>();
        calculados.forEach(item -> codigos.add(item.beneficio().getCodigo()));
        boolean topoAtivo = codigos.contains(ANUNCIO_TOPO);
        boolean fotosExtrasAtivo = codigos.contains(FOTOS_EXTRA_5);
        boolean carrosselAtivo = codigos.contains(CARROSSEL_FOTOS);
        boolean videoAtivo = codigos.contains(VIDEO_1);
        boolean whatsappCardAtivo = codigos.contains(WHATSAPP_CARD);
        return new PremiumPublicoFlagsDto(
                topoAtivo,
                topoAtivo,
                true,
                false,
                codigos.stream().anyMatch(PremiumBeneficioCodigo.MIDIA_EXTRA::contains),
                codigos.contains(OCULTAR_IDADE),
                fotosExtrasAtivo,
                carrosselAtivo,
                videoAtivo,
                whatsappCardAtivo,
                codigos.stream()
                        .filter(CODIGOS_PUBLICOS_VISIVEIS::contains)
                        .map(this::rotuloPublico)
                        .distinct()
                        .toList());
    }

    private boolean publicavel(PremiumBeneficioCalculado item) {
        return item.beneficio() != null
                && PremiumBeneficioCodigo.TODOS.contains(item.beneficio().getCodigo())
                && (item.status() == PremiumBeneficioStatusCalculado.ATIVO
                || item.status() == PremiumBeneficioStatusCalculado.VENCENDO);
    }

    private String rotuloPublico(String codigo) {
        return switch (codigo) {
            case ANUNCIO_TOPO -> "Topo";
            case FOTOS_EXTRA_5 -> "Fotos extras";
            case VIDEO_1 -> "Video";
            case WHATSAPP_CARD -> "WhatsApp no card";
            case CARROSSEL_FOTOS -> "Carrossel de fotos";
            default -> "Premium";
        };
    }
}
