package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.admin.stories.AdminStoryConfiguracaoService.DURACAO_HORAS;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDireitoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryOfertaDto;
import br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeuAnuncioStoryOfertaService {
  private final MeusAnunciosConsultaService anunciosService;
  private final MeuAnuncioStoryConsultaService storyConsultaService;
  private final CreditoLedgerOperacaoService ledgerService;
  private final StoryConfiguracaoComercialRepository configuracaoRepository;
  private final BeneficioPremiumRepository beneficioRepository;
  private final AtivacaoBeneficioRepository ativacaoRepository;
  private final StoryAnuncioRepository storyRepository;

  public MeuAnuncioStoryOfertaService(
      MeusAnunciosConsultaService anunciosService,
      MeuAnuncioStoryConsultaService storyConsultaService,
      CreditoLedgerOperacaoService ledgerService,
      StoryConfiguracaoComercialRepository configuracaoRepository,
      BeneficioPremiumRepository beneficioRepository,
      AtivacaoBeneficioRepository ativacaoRepository,
      StoryAnuncioRepository storyRepository) {
    this.anunciosService = anunciosService;
    this.storyConsultaService = storyConsultaService;
    this.ledgerService = ledgerService;
    this.configuracaoRepository = configuracaoRepository;
    this.beneficioRepository = beneficioRepository;
    this.ativacaoRepository = ativacaoRepository;
    this.storyRepository = storyRepository;
  }

  @Transactional(readOnly = true)
  public MeuAnuncioStoryOfertaDto consultar(String slug, Authentication authentication) {
    var anuncio = anunciosService.anuncioDoUsuario(slug, authentication);
    UUID usuarioId = anunciosService.usuarioAutenticado(authentication).getId();
    var storyAtivo = storyConsultaService.consultarAtivos(List.of(anuncio.getId())).get(anuncio.getId());
    if (storyAtivo != null) {
      return new MeuAnuncioStoryOfertaDto(
          "STORY_ATIVO", null, null, DURACAO_HORAS, null,
          null, null, null, storyAtivo, null, null);
    }

    AtivacaoBeneficioEntity direito = localizarDireitoDisponivel(anuncio.getId(), usuarioId);
    if (direito != null) {
      return new MeuAnuncioStoryOfertaDto(
          "DIREITO_DISPONIVEL", null, null, DURACAO_HORAS, null,
          null, null, null, null,
          new MeuAnuncioStoryDireitoDto(
              direito.getId(), statusPublico(direito),
              direito.getCustoCreditosSnapshot(), direito.getInicioEm(), direito.getFimEm()),
          null);
    }

    var configuracao = configuracaoRepository
        .findById(StoryConfiguracaoComercialEntity.SINGLETON_ID)
        .orElse(null);
    if (configuracao == null) {
      return indisponivel(false, null);
    }
    if (!Boolean.TRUE.equals(configuracao.getAtivo())) {
      return indisponivel(true, configuracao.getVersao());
    }

    int saldo = ledgerService.consultarSaldo(usuarioId);
    int custo = configuracao.getCustoCreditos();
    return new MeuAnuncioStoryOfertaDto(
        "OFERTA_DISPONIVEL", true, true, DURACAO_HORAS, custo,
        saldo, Math.max(0, saldo - custo), Math.max(0, custo - saldo),
        null, null, configuracao.getVersao());
  }

  private MeuAnuncioStoryOfertaDto indisponivel(boolean configurada, Long versao) {
    return new MeuAnuncioStoryOfertaDto(
        "NOVAS_ATIVACOES_INDISPONIVEIS", configurada, false, DURACAO_HORAS,
        null, null, null, null, null, null, versao);
  }

  private AtivacaoBeneficioEntity localizarDireitoDisponivel(UUID anuncioId, UUID usuarioId) {
    var beneficio = beneficioRepository.findByCodigo(STORIES).orElse(null);
    if (beneficio == null) return null;
    Set<UUID> consumidas = storyRepository.findByAnuncioIds(List.of(anuncioId)).stream()
        .map(item -> item.getAtivacaoBeneficioId())
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    List<AtivacaoBeneficioEntity> candidatas = ativacaoRepository.findByAnuncioId(anuncioId).stream()
        .filter(item -> Objects.equals(item.getUsuarioId(), usuarioId))
        .filter(item -> Objects.equals(item.getBeneficioId(), beneficio.getId()))
        .filter(item -> item.getRevogadaEm() == null)
        .filter(item -> !consumidas.contains(item.getId()))
        .toList();
    AtivacaoBeneficioEntity ativa = candidatas.stream()
        .filter(item -> ativaDisponivel(item, agora))
        .sorted(Comparator.comparing(AtivacaoBeneficioEntity::getFimEm)
            .thenComparing(AtivacaoBeneficioEntity::getCriadoEm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AtivacaoBeneficioEntity::getId))
        .findFirst().orElse(null);
    if (ativa != null) return ativa;
    return candidatas.stream()
        .filter(this::aguardandoUso)
        .sorted(Comparator.comparing(AtivacaoBeneficioEntity::getCriadoEm,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(AtivacaoBeneficioEntity::getId))
        .findFirst().orElse(null);
  }

  private String statusPublico(AtivacaoBeneficioEntity ativacao) {
    return aguardandoUso(ativacao) ? "DISPONIVEL_PARA_PUBLICAR" : ativacao.getStatus().name();
  }

  private boolean aguardandoUso(AtivacaoBeneficioEntity ativacao) {
    return ativacao.getStatus() == StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO
        && ativacao.getInicioEm() == null && ativacao.getFimEm() == null;
  }

  private boolean ativaDisponivel(AtivacaoBeneficioEntity ativacao, OffsetDateTime agora) {
    return ativacao.getStatus() == StatusAtivacaoBeneficio.ATIVA
        && ativacao.getInicioEm() != null && ativacao.getFimEm() != null
        && !ativacao.getInicioEm().isAfter(agora) && ativacao.getFimEm().isAfter(agora);
  }
}