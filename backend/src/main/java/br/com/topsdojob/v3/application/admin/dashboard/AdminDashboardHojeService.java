package br.com.topsdojob.v3.application.admin.dashboard;

import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardHojeDto;
import br.com.topsdojob.v3.persistence.repository.AgregadoCliqueWhatsappDiarioRepository;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoDiariaRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardHojeService {

    static final ZoneId FUSO_CANONICO = ZoneId.of("America/Sao_Paulo");

    private final AgregadoVisualizacaoDiariaRepository visualizacaoRepository;
    private final AgregadoCliqueWhatsappDiarioRepository cliqueRepository;
    private final AtivacaoBeneficioRepository beneficioRepository;
    private final Clock clock;

    @Autowired
    public AdminDashboardHojeService(
            AgregadoVisualizacaoDiariaRepository visualizacaoRepository,
            AgregadoCliqueWhatsappDiarioRepository cliqueRepository,
            AtivacaoBeneficioRepository beneficioRepository) {
        this(visualizacaoRepository, cliqueRepository, beneficioRepository, Clock.systemUTC());
    }

    AdminDashboardHojeService(
            AgregadoVisualizacaoDiariaRepository visualizacaoRepository,
            AgregadoCliqueWhatsappDiarioRepository cliqueRepository,
            AtivacaoBeneficioRepository beneficioRepository,
            Clock clock) {
        this.visualizacaoRepository = visualizacaoRepository;
        this.cliqueRepository = cliqueRepository;
        this.beneficioRepository = beneficioRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardHojeDto consultar() {
        OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        LocalDate hoje = agora.atZoneSameInstant(FUSO_CANONICO).toLocalDate();
        return new AdminDashboardHojeDto(
                hoje,
                FUSO_CANONICO.getId(),
                visualizacaoRepository.somarPorData(hoje),
                cliqueRepository.somarPorData(hoje),
                beneficioRepository.countVigentes(agora),
                agora);
    }
}
