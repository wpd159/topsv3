package br.com.topsdojob.v3.application.publico.anunciante.midia;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LimiteMidiasAnuncioService {

    public static final int FOTOS_BASE = 4;
    public static final int FOTOS_COM_EXTRA = 10;
    public static final int VIDEOS = 1;
    private static final String FOTOS_EXTRA_5 = "FOTOS_EXTRA_5";

    private final BeneficioAnuncioConsultaService beneficioService;

    public LimiteMidiasAnuncioService(BeneficioAnuncioConsultaService beneficioService) {
        this.beneficioService = beneficioService;
    }

    @Transactional(readOnly = true)
    public Resultado resolver(UUID anuncioId) {
        boolean fotosExtras = beneficioService.consultarCalculados(anuncioId).stream()
                .anyMatch(item -> item.beneficio() != null
                        && FOTOS_EXTRA_5.equals(item.beneficio().getCodigo())
                        && (item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO));
        return new Resultado(fotosExtras ? FOTOS_COM_EXTRA : FOTOS_BASE, VIDEOS, fotosExtras);
    }

    public record Resultado(int maxFotos, int maxVideos, boolean fotosExtrasAtivo) {
    }
}
