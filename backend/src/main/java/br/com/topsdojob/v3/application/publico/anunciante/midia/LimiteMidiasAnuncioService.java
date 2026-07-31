package br.com.topsdojob.v3.application.publico.anunciante.midia;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.FOTOS_EXTRA_5;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.VIDEO_1;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LimiteMidiasAnuncioService {

    public static final int FOTOS_BASE = 4;
    public static final int FOTOS_COM_EXTRA = 10;
    public static final int VIDEOS = 1;
    private final BeneficioAnuncioConsultaService beneficioService;

    public LimiteMidiasAnuncioService(BeneficioAnuncioConsultaService beneficioService) {
        this.beneficioService = beneficioService;
    }

    @Transactional(readOnly = true)
    public Resultado resolver(UUID anuncioId) {
        var beneficios = beneficioService.consultarCalculados(anuncioId);
        boolean fotosExtras = beneficios.stream()
                .anyMatch(item -> item.beneficio() != null
                        && FOTOS_EXTRA_5.equals(item.beneficio().getCodigo())
                        && (item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO
                        || (item.ativacao() != null
                        && item.ativacao().getStatus()
                        == StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO)));
        boolean videoAtivo = beneficios.stream()
                .anyMatch(item -> item.beneficio() != null
                        && VIDEO_1.equals(item.beneficio().getCodigo())
                        && (item.status() == PremiumBeneficioStatusCalculado.ATIVO
                        || item.status() == PremiumBeneficioStatusCalculado.VENCENDO));
        return new Resultado(
                fotosExtras ? FOTOS_COM_EXTRA : FOTOS_BASE,
                videoAtivo ? VIDEOS : 0,
                fotosExtras,
                videoAtivo);
    }

    public record Resultado(
            int maxFotos,
            int maxVideos,
            boolean fotosExtrasAtivo,
            boolean videoAtivo) {
    }
}
