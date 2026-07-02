package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoMetricasDto;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMetricaResumoConsultaService {

    private final EventoVisualizacaoRepository visualizacaoRepository;
    private final CliqueWhatsappRepository cliqueRepository;

    public AdminMetricaResumoConsultaService(
            EventoVisualizacaoRepository visualizacaoRepository,
            CliqueWhatsappRepository cliqueRepository) {
        this.visualizacaoRepository = visualizacaoRepository;
        this.cliqueRepository = cliqueRepository;
    }

    @Transactional(readOnly = true)
    public AdminResumoMetricasDto consultar() {
        long permitidos = cliqueRepository.countByPermitidoTrue();
        long bloqueados = cliqueRepository.countByPermitidoFalse();
        return new AdminResumoMetricasDto(
                visualizacaoRepository.count(),
                permitidos + bloqueados,
                permitidos,
                bloqueados);
    }
}
