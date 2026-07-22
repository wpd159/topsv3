package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioResumoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.AdminMetricaResumoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.AdminMidiaResumoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.AdminModeracaoResumoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoAnunciosDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoMetricasDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoMidiasDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoModeracaoDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminResumoController {

    private final AdminAnuncioResumoConsultaService anuncioService;
    private final AdminModeracaoResumoConsultaService moderacaoService;
    private final AdminMidiaResumoConsultaService midiaService;
    private final AdminMetricaResumoConsultaService metricaService;

    public AdminResumoController(
            AdminAnuncioResumoConsultaService anuncioService,
            AdminModeracaoResumoConsultaService moderacaoService,
            AdminMidiaResumoConsultaService midiaService,
            AdminMetricaResumoConsultaService metricaService) {
        this.anuncioService = anuncioService;
        this.moderacaoService = moderacaoService;
        this.midiaService = midiaService;
        this.metricaService = metricaService;
    }

    @GetMapping("/anuncios/resumo")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public AdminResumoAnunciosDto anuncios() {
        return anuncioService.consultar();
    }

    @GetMapping("/moderacao/resumo")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
    public AdminResumoModeracaoDto moderacao() {
        return moderacaoService.consultar();
    }

    @GetMapping("/midias/resumo")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('MIDIA_REVISAR')")
    public AdminResumoMidiasDto midias() {
        return midiaService.consultar();
    }

    @GetMapping("/metricas/resumo")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminResumoMetricasDto metricas() {
        return metricaService.consultar();
    }
}
