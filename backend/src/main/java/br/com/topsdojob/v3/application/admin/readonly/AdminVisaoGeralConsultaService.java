package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminContadorDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoAnunciosDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoMetricasDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoMidiasDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoModeracaoDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminStatusSistemaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminVisaoGeralDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminVisaoGeralConsultaService {

    private final AdminAnuncioResumoConsultaService anuncioService;
    private final AdminModeracaoResumoConsultaService moderacaoService;
    private final AdminMidiaResumoConsultaService midiaService;
    private final AdminMetricaResumoConsultaService metricaService;
    private final AdminSistemaStatusService sistemaService;

    public AdminVisaoGeralConsultaService(
            AdminAnuncioResumoConsultaService anuncioService,
            AdminModeracaoResumoConsultaService moderacaoService,
            AdminMidiaResumoConsultaService midiaService,
            AdminMetricaResumoConsultaService metricaService,
            AdminSistemaStatusService sistemaService) {
        this.anuncioService = anuncioService;
        this.moderacaoService = moderacaoService;
        this.midiaService = midiaService;
        this.metricaService = metricaService;
        this.sistemaService = sistemaService;
    }

    @Transactional(readOnly = true)
    public AdminVisaoGeralDto consultar(boolean admin, boolean moderador, boolean comercial) {
        AdminResumoAnunciosDto anuncios = anuncioService.consultar();
        AdminResumoModeracaoDto moderacao = (admin || moderador) ? moderacaoService.consultar() : null;
        AdminResumoMidiasDto midias = (admin || moderador) ? midiaService.consultar() : null;
        AdminResumoMetricasDto metricas = (admin || comercial) ? metricaService.consultar() : null;
        AdminStatusSistemaDto sistema = admin ? sistemaService.consultar() : null;
        List<AdminContadorDto> contadores = new ArrayList<>();
        contadores.add(new AdminContadorDto("ANUNCIOS_PUBLICADOS", "Anuncios publicados", anuncios.publicados()));
        if (moderacao != null) {
            contadores.add(new AdminContadorDto("REVISOES_ABERTAS", "Revisoes abertas", moderacao.revisoesAbertas()));
        }
        if (midias != null) {
            contadores.add(new AdminContadorDto("MIDIAS_PENDENTES", "Midias pendentes", midias.midiasPendentes()));
        }
        if (metricas != null) {
            contadores.add(new AdminContadorDto("VISUALIZACOES", "Visualizacoes", metricas.visualizacoesTotal()));
        }
        return new AdminVisaoGeralDto(
                List.copyOf(contadores),
                anuncios,
                moderacao,
                midias,
                metricas,
                sistema);
    }
}
