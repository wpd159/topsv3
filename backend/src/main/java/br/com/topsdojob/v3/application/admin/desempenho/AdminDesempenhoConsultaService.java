package br.com.topsdojob.v3.application.admin.desempenho;

import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoAnuncianteDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoAnuncioDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoComparativoPremiumDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoDiarioDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoOrigemDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoResumoDto;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminBeneficioAnuncioDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoCliqueWhatsappDiarioEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoDiariaEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AgregadoCliqueWhatsappDiarioRepository;
import br.com.topsdojob.v3.persistence.repository.AgregadoVisualizacaoDiariaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDesempenhoConsultaService {

    private static final String AVISO_RESULTADO = "Prova de resultado local: visualizacoes e cliques indicam exposicao, sem promessa de contratacao.";

    private final AnuncioRepository anuncioRepository;
    private final AgregadoVisualizacaoDiariaRepository visualizacaoAgregadaRepository;
    private final AgregadoCliqueWhatsappDiarioRepository cliqueAgregadoRepository;
    private final EventoVisualizacaoRepository visualizacaoRepository;
    private final CliqueWhatsappRepository cliqueRepository;
    private final AtivacaoBeneficioRepository ativacaoRepository;
    private final BeneficioAnuncioConsultaService beneficioService;
    private final AdminDesempenhoOrigemService origemService;
    private final AdminDesempenhoPremiumComparativoService comparativoService;
    private final AdminDesempenhoSanitizer sanitizer;

    public AdminDesempenhoConsultaService(
            AnuncioRepository anuncioRepository,
            AgregadoVisualizacaoDiariaRepository visualizacaoAgregadaRepository,
            AgregadoCliqueWhatsappDiarioRepository cliqueAgregadoRepository,
            EventoVisualizacaoRepository visualizacaoRepository,
            CliqueWhatsappRepository cliqueRepository,
            AtivacaoBeneficioRepository ativacaoRepository,
            BeneficioAnuncioConsultaService beneficioService,
            AdminDesempenhoOrigemService origemService,
            AdminDesempenhoPremiumComparativoService comparativoService,
            AdminDesempenhoSanitizer sanitizer) {
        this.anuncioRepository = anuncioRepository;
        this.visualizacaoAgregadaRepository = visualizacaoAgregadaRepository;
        this.cliqueAgregadoRepository = cliqueAgregadoRepository;
        this.visualizacaoRepository = visualizacaoRepository;
        this.cliqueRepository = cliqueRepository;
        this.ativacaoRepository = ativacaoRepository;
        this.beneficioService = beneficioService;
        this.origemService = origemService;
        this.comparativoService = comparativoService;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public AdminDesempenhoAnuncioDto consultarAnuncio(UUID anuncioId) {
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        return desempenhoAnuncio(anuncio);
    }

    @Transactional(readOnly = true)
    public List<AdminDesempenhoDiarioDto> consultarDiario(UUID anuncioId) {
        return consultarAnuncio(anuncioId).diario();
    }

    @Transactional(readOnly = true)
    public List<AdminDesempenhoOrigemDto> consultarOrigens(UUID anuncioId) {
        return consultarAnuncio(anuncioId).origens();
    }

    @Transactional(readOnly = true)
    public AdminDesempenhoAnuncianteDto consultarAnunciante(UUID usuarioId) {
        List<AdminDesempenhoAnuncioDto> anuncios = anuncioRepository.findByUsuarioIdAndRemovidoEmIsNull(usuarioId).stream()
                .map(this::desempenhoAnuncio)
                .toList();
        long visualizacoes = anuncios.stream().mapToLong(AdminDesempenhoAnuncioDto::visualizacoesTotal).sum();
        long cliques = anuncios.stream().mapToLong(AdminDesempenhoAnuncioDto::cliquesWhatsappTotal).sum();
        return new AdminDesempenhoAnuncianteDto(
                usuarioId,
                anuncios.size(),
                visualizacoes,
                cliques,
                sanitizer.taxaCliqueView(cliques, visualizacoes),
                anuncios,
                AVISO_RESULTADO,
                false,
                true,
                true);
    }

    @Transactional(readOnly = true)
    public AdminDesempenhoResumoDto consultarResumo() {
        List<AgregadoVisualizacaoDiariaEntity> visualizacoes = visualizacaoAgregadaRepository.findAll();
        List<AgregadoCliqueWhatsappDiarioEntity> cliques = cliqueAgregadoRepository.findAll();
        long totalVisualizacoes = visualizacoes.stream().mapToLong(item -> seguro(item.getTotalVisualizacoes())).sum();
        long totalCliques = cliques.stream().mapToLong(item -> seguro(item.getTotalCliques())).sum();
        List<UUID> anuncioIds = new ArrayList<>();
        visualizacoes.stream().map(AgregadoVisualizacaoDiariaEntity::getAnuncioId).filter(Objects::nonNull).forEach(anuncioIds::add);
        cliques.stream().map(AgregadoCliqueWhatsappDiarioEntity::getAnuncioId).filter(Objects::nonNull).forEach(anuncioIds::add);
        Map<UUID, List<AtivacaoBeneficioEntity>> ativacoes = ativacaoRepository.findAll().stream()
                .filter(item -> item.getAnuncioId() != null)
                .collect(java.util.stream.Collectors.groupingBy(AtivacaoBeneficioEntity::getAnuncioId));
        long visualizacoesComPremium = visualizacoes.stream()
                .filter(item -> premiumAtivoNoDia(item.getDataReferencia(), ativacoes.getOrDefault(item.getAnuncioId(), List.of())))
                .mapToLong(item -> seguro(item.getTotalVisualizacoes()))
                .sum();
        long visualizacoesOrganicas = totalVisualizacoes - visualizacoesComPremium;
        return new AdminDesempenhoResumoDto(
                anuncioIds.stream().distinct().count(),
                totalVisualizacoes,
                totalCliques,
                sanitizer.taxaCliqueView(totalCliques, totalVisualizacoes),
                Math.max(0, visualizacoesOrganicas),
                visualizacoesComPremium,
                AdminDesempenhoPremiumComparativoService.MENSAGEM_SEGURA,
                OffsetDateTime.now(),
                false,
                false,
                true,
                true);
    }

    private AdminDesempenhoAnuncioDto desempenhoAnuncio(AnuncioEntity anuncio) {
        UUID anuncioId = anuncio.getId();
        List<AgregadoVisualizacaoDiariaEntity> visualizacoesAgregadas = visualizacaoAgregadaRepository.findByAnuncioId(anuncioId);
        List<AgregadoCliqueWhatsappDiarioEntity> cliquesAgregados = cliqueAgregadoRepository.findByAnuncioId(anuncioId);
        List<EventoVisualizacaoEntity> visualizacoesBrutas = visualizacoesAgregadas.isEmpty()
                ? visualizacaoRepository.findByAnuncioId(anuncioId)
                : List.of();
        List<CliqueWhatsappEntity> cliquesBrutos = cliquesAgregados.isEmpty()
                ? cliqueRepository.findByAnuncioId(anuncioId)
                : List.of();
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByAnuncioId(anuncioId);
        List<AdminDesempenhoDiarioDto> diario = diario(
                visualizacoesAgregadas,
                cliquesAgregados,
                visualizacoesBrutas,
                cliquesBrutos,
                ativacoes);
        List<AdminDesempenhoOrigemDto> origens = origemService.consolidar(
                anuncioId,
                visualizacoesAgregadas,
                cliquesAgregados,
                visualizacoesBrutas,
                cliquesBrutos);
        long visualizacoesTotal = diario.stream().mapToLong(AdminDesempenhoDiarioDto::visualizacoes).sum();
        long cliquesTotal = diario.stream().mapToLong(AdminDesempenhoDiarioDto::cliquesWhatsapp).sum();
        List<String> beneficiosAtivos = beneficioService.consultar(anuncioId).stream()
                .filter(item -> "ATIVO".equals(item.statusCalculado()) || "VENCENDO".equals(item.statusCalculado()))
                .map(AdminBeneficioAnuncioDto::beneficioCodigo)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        AdminDesempenhoComparativoPremiumDto comparativo = comparativoService.comparar(diario, beneficiosAtivos);
        return new AdminDesempenhoAnuncioDto(
                anuncioId,
                anuncio.getUsuarioId(),
                sanitizer.texto(anuncio.getSlug()),
                sanitizer.texto(anuncio.getTitulo()),
                visualizacoesTotal,
                cliquesTotal,
                sanitizer.taxaCliqueView(cliquesTotal, visualizacoesTotal),
                diario,
                origens,
                comparativo,
                AVISO_RESULTADO,
                true,
                false,
                true);
    }

    private List<AdminDesempenhoDiarioDto> diario(
            List<AgregadoVisualizacaoDiariaEntity> visualizacoesAgregadas,
            List<AgregadoCliqueWhatsappDiarioEntity> cliquesAgregados,
            List<EventoVisualizacaoEntity> visualizacoesBrutas,
            List<CliqueWhatsappEntity> cliquesBrutos,
            List<AtivacaoBeneficioEntity> ativacoes) {
        Map<LocalDate, long[]> porData = new LinkedHashMap<>();
        if (!visualizacoesAgregadas.isEmpty() || !cliquesAgregados.isEmpty()) {
            visualizacoesAgregadas.forEach(item -> {
                if (item.getDataReferencia() != null) {
                    porData.computeIfAbsent(item.getDataReferencia(), ignored -> new long[2])[0] += seguro(item.getTotalVisualizacoes());
                }
            });
            cliquesAgregados.forEach(item -> {
                if (item.getDataReferencia() != null) {
                    porData.computeIfAbsent(item.getDataReferencia(), ignored -> new long[2])[1] += seguro(item.getTotalCliques());
                }
            });
        } else {
            visualizacoesBrutas.stream()
                    .map(EventoVisualizacaoEntity::getCriadoEm)
                    .filter(Objects::nonNull)
                    .map(OffsetDateTime::toLocalDate)
                    .forEach(data -> porData.computeIfAbsent(data, ignored -> new long[2])[0]++);
            cliquesBrutos.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getPermitido()))
                    .map(CliqueWhatsappEntity::getCriadoEm)
                    .filter(Objects::nonNull)
                    .map(OffsetDateTime::toLocalDate)
                    .forEach(data -> porData.computeIfAbsent(data, ignored -> new long[2])[1]++);
        }
        return porData.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AdminDesempenhoDiarioDto(
                        entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[1],
                        sanitizer.taxaCliqueView(entry.getValue()[1], entry.getValue()[0]),
                        premiumAtivoNoDia(entry.getKey(), ativacoes),
                        true))
                .sorted(Comparator.comparing(AdminDesempenhoDiarioDto::dataReferencia))
                .toList();
    }

    private boolean premiumAtivoNoDia(LocalDate data, List<AtivacaoBeneficioEntity> ativacoes) {
        if (data == null || ativacoes == null || ativacoes.isEmpty()) {
            return false;
        }
        return ativacoes.stream().anyMatch(item -> {
            if (item.getStatus() != StatusAtivacaoBeneficio.ATIVA) {
                return false;
            }
            LocalDate inicio = item.getInicioEm() == null ? LocalDate.MIN : item.getInicioEm().toLocalDate();
            LocalDate fim = item.getFimEm() == null ? LocalDate.MAX : item.getFimEm().toLocalDate();
            return !data.isBefore(inicio) && !data.isAfter(fim);
        });
    }

    private long seguro(Long value) {
        return value == null || value < 0 ? 0 : value;
    }
}
