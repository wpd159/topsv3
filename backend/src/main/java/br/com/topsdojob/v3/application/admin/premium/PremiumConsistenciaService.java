package br.com.topsdojob.v3.application.admin.premium;

import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumConsistenciaItemDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumConsistenciaResumoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumVencendoItemDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumVencendoResumoDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumConsistenciaService {

    private final AtivacaoBeneficioRepository ativacaoRepository;
    private final GrupoAtivacaoBeneficioRepository grupoRepository;
    private final AnuncioRepository anuncioRepository;
    private final BeneficioAnuncioConsultaService beneficioService;

    public PremiumConsistenciaService(
            AtivacaoBeneficioRepository ativacaoRepository,
            GrupoAtivacaoBeneficioRepository grupoRepository,
            AnuncioRepository anuncioRepository,
            BeneficioAnuncioConsultaService beneficioService) {
        this.ativacaoRepository = ativacaoRepository;
        this.grupoRepository = grupoRepository;
        this.anuncioRepository = anuncioRepository;
        this.beneficioService = beneficioService;
    }

    @Transactional(readOnly = true)
    public AdminPremiumConsistenciaResumoDto consultarConsistencia() {
        OffsetDateTime agora = OffsetDateTime.now();
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findAll();
        List<PremiumBeneficioCalculado> calculados = beneficioService.calcular(ativacoes, agora);
        Map<UUID, AnuncioEntity> anuncios = anunciosPorId(ativacoes.stream()
                .map(AtivacaoBeneficioEntity::getAnuncioId)
                .filter(java.util.Objects::nonNull)
                .toList());
        List<AdminPremiumConsistenciaItemDto> itens = calculados.stream()
                .filter(PremiumBeneficioCalculado::inconsistente)
                .flatMap(item -> item.codigos().stream()
                        .filter(PremiumConsistenciaCodigo::inconsistencia)
                        .map(codigo -> toConsistenciaItem(item, codigo, anuncios, agora)))
                .toList();
        List<AdminPremiumConsistenciaItemDto> gruposSemBeneficios = gruposSemBeneficios(ativacoes, anuncios, agora);
        List<AdminPremiumConsistenciaItemDto> todos = java.util.stream.Stream
                .concat(itens.stream(), gruposSemBeneficios.stream())
                .toList();
        return new AdminPremiumConsistenciaResumoDto(todos, todos.size(), agora, true);
    }

    @Transactional(readOnly = true)
    public AdminPremiumVencendoResumoDto consultarVencendo() {
        OffsetDateTime agora = OffsetDateTime.now();
        OffsetDateTime limite = agora.plusDays(PremiumExpiracaoPolicyService.JANELA_VENCENDO_DIAS);
        List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByFimEmBetween(agora, limite);
        List<PremiumBeneficioCalculado> calculados = beneficioService.calcular(ativacoes, agora);
        Map<UUID, AnuncioEntity> anuncios = anunciosPorId(ativacoes.stream()
                .map(AtivacaoBeneficioEntity::getAnuncioId)
                .filter(java.util.Objects::nonNull)
                .toList());
        List<AdminPremiumVencendoItemDto> itens = calculados.stream()
                .filter(PremiumBeneficioCalculado::venceEmBreve)
                .map(item -> toVencendoItem(item, anuncios, agora))
                .toList();
        return new AdminPremiumVencendoResumoDto(
                itens,
                itens.size(),
                PremiumExpiracaoPolicyService.JANELA_VENCENDO_DIAS,
                agora,
                true);
    }

    private AdminPremiumConsistenciaItemDto toConsistenciaItem(
            PremiumBeneficioCalculado item,
            PremiumConsistenciaCodigo codigo,
            Map<UUID, AnuncioEntity> anuncios,
            OffsetDateTime agora) {
        AtivacaoBeneficioEntity ativacao = item.ativacao();
        GrupoAtivacaoBeneficioEntity grupo = item.grupo();
        AnuncioEntity anuncio = ativacao.getAnuncioId() == null ? null : anuncios.get(ativacao.getAnuncioId());
        return new AdminPremiumConsistenciaItemDto(
                ativacao.getAnuncioId(),
                anuncio == null ? null : anuncio.getSlug(),
                ativacao.getId(),
                item.beneficio() == null ? null : item.beneficio().getCodigo(),
                codigo.name(),
                "ALERTA",
                mensagem(codigo),
                item.status().name(),
                ativacao.getFimEm(),
                grupo == null ? null : grupo.getId(),
                grupo == null || grupo.getStatus() == null ? null : grupo.getStatus().name(),
                grupo == null ? null : grupo.getValidadeFimEm(),
                agora);
    }

    private AdminPremiumVencendoItemDto toVencendoItem(
            PremiumBeneficioCalculado item,
            Map<UUID, AnuncioEntity> anuncios,
            OffsetDateTime agora) {
        AtivacaoBeneficioEntity ativacao = item.ativacao();
        AnuncioEntity anuncio = ativacao.getAnuncioId() == null ? null : anuncios.get(ativacao.getAnuncioId());
        return new AdminPremiumVencendoItemDto(
                ativacao.getAnuncioId(),
                anuncio == null ? null : anuncio.getSlug(),
                ativacao.getId(),
                item.beneficio() == null ? null : item.beneficio().getCodigo(),
                item.status().name(),
                ativacao.getFimEm(),
                Math.max(0, ChronoUnit.DAYS.between(agora, ativacao.getFimEm())),
                item.grupo() != null,
                true);
    }

    private List<AdminPremiumConsistenciaItemDto> gruposSemBeneficios(
            Collection<AtivacaoBeneficioEntity> ativacoes,
            Map<UUID, AnuncioEntity> anunciosConhecidos,
            OffsetDateTime agora) {
        List<GrupoAtivacaoBeneficioEntity> grupos = grupoRepository.findAll();
        List<UUID> gruposComBeneficio = ativacoes.stream()
                .map(AtivacaoBeneficioEntity::getGrupoAtivacaoId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, AnuncioEntity> anuncios = anunciosPorId(grupos.stream()
                .map(GrupoAtivacaoBeneficioEntity::getAnuncioId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !anunciosConhecidos.containsKey(id))
                .toList());
        return grupos.stream()
                .filter(grupo -> !gruposComBeneficio.contains(grupo.getId()))
                .map(grupo -> {
                    AnuncioEntity anuncio = grupo.getAnuncioId() == null
                            ? null
                            : anunciosConhecidos.getOrDefault(grupo.getAnuncioId(), anuncios.get(grupo.getAnuncioId()));
                    return new AdminPremiumConsistenciaItemDto(
                            grupo.getAnuncioId(),
                            anuncio == null ? null : anuncio.getSlug(),
                            null,
                            null,
                            PremiumConsistenciaCodigo.GRUPO_SEM_BENEFICIOS.name(),
                            "ALERTA",
                            mensagem(PremiumConsistenciaCodigo.GRUPO_SEM_BENEFICIOS),
                            "INCONSISTENTE",
                            null,
                            grupo.getId(),
                            grupo.getStatus() == null ? null : grupo.getStatus().name(),
                            grupo.getValidadeFimEm(),
                            agora);
                })
                .toList();
    }

    private Map<UUID, AnuncioEntity> anunciosPorId(Collection<UUID> ids) {
        List<UUID> unicos = ids.stream().distinct().toList();
        if (unicos.isEmpty()) {
            return Map.of();
        }
        return anuncioRepository.findAllById(unicos).stream()
                .collect(Collectors.toMap(AnuncioEntity::getId, Function.identity()));
    }

    private String mensagem(PremiumConsistenciaCodigo codigo) {
        return switch (codigo) {
            case GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO ->
                    "Grupo expirado trata todos os beneficios como expirados; ativacao ativa foi sinalizada.";
            case BENEFICIO_EXPIRADO_ANTES_DO_GRUPO ->
                    "Beneficio expirou antes do grupo/pacote sem justificativa local.";
            case GRUPO_SEM_BENEFICIOS ->
                    "Grupo/pacote nao possui ativacoes vinculadas.";
            case DATA_INVALIDA ->
                    "Janela de vigencia invalida ou incompleta.";
            case ORIGEM_DESCONHECIDA ->
                    "Origem do beneficio nao foi identificada.";
            default -> "Codigo operacional de consistencia premium.";
        };
    }
}
