package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDireitoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryOfertaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryOfertaOpcaoDto;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
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
  private final PremiumCatalogoService catalogoService;
  private final BeneficioPremiumRepository beneficioRepository;
  private final BeneficioPremiumOpcaoRepository opcaoRepository;
  private final AtivacaoBeneficioRepository ativacaoRepository;
  private final StoryAnuncioRepository storyRepository;

  public MeuAnuncioStoryOfertaService(
      MeusAnunciosConsultaService anunciosService,
      MeuAnuncioStoryConsultaService storyConsultaService,
      CreditoLedgerOperacaoService ledgerService,
      PremiumCatalogoService catalogoService,
      BeneficioPremiumRepository beneficioRepository,
      BeneficioPremiumOpcaoRepository opcaoRepository,
      AtivacaoBeneficioRepository ativacaoRepository,
      StoryAnuncioRepository storyRepository) {
    this.anunciosService = anunciosService;
    this.storyConsultaService = storyConsultaService;
    this.ledgerService = ledgerService;
    this.catalogoService = catalogoService;
    this.beneficioRepository = beneficioRepository;
    this.opcaoRepository = opcaoRepository;
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
          "STORY_ATIVO", null, storyAtivo, null, null, null, null, List.of());
    }

    AtivacaoBeneficioEntity direito = localizarDireitoDisponivel(anuncio.getId(), usuarioId);
    if (direito != null) {
      Integer duracao = direito.getOpcaoId() == null
          ? null
          : opcaoRepository.findById(direito.getOpcaoId())
              .map(item -> item.getDuracaoDias())
              .orElse(null);
      return new MeuAnuncioStoryOfertaDto(
          "DIREITO_DISPONIVEL",
          null,
          null,
          new MeuAnuncioStoryDireitoDto(
              direito.getId(),
              direito.getStatus().name(),
              duracao,
              direito.getCustoCreditosSnapshot(),
              direito.getInicioEm(),
              direito.getFimEm()),
          PremiumBeneficioCodigo.STORIES,
          null,
          null,
          List.of());
    }

    int saldo = ledgerService.consultarSaldo(usuarioId);
    var stories = catalogoService.catalogoAtivo().stream()
        .filter(item -> PremiumBeneficioCodigo.STORIES.equals(item.codigo()))
        .findFirst()
        .orElse(null);
    if (stories == null || stories.opcoes().isEmpty()) {
      return new MeuAnuncioStoryOfertaDto(
          "NOVAS_ATIVACOES_INDISPONIVEIS",
          saldo,
          null,
          null,
          PremiumBeneficioCodigo.STORIES,
          null,
          null,
          List.of());
    }

    List<MeuAnuncioStoryOfertaOpcaoDto> opcoes = stories.opcoes().stream()
        .filter(item -> item.ativo())
        .map(item -> new MeuAnuncioStoryOfertaOpcaoDto(
            item.id(),
            item.duracaoDias(),
            item.custoCreditos(),
            saldo,
            Math.max(0, saldo - item.custoCreditos()),
            Math.max(0, item.custoCreditos() - saldo),
            item.ordemExibicao()))
        .toList();
    if (opcoes.isEmpty()) {
      return new MeuAnuncioStoryOfertaDto(
          "NOVAS_ATIVACOES_INDISPONIVEIS",
          saldo,
          null,
          null,
          PremiumBeneficioCodigo.STORIES,
          stories.nome(),
          stories.descricao(),
          List.of());
    }
    return new MeuAnuncioStoryOfertaDto(
        "OPCOES_DISPONIVEIS",
        saldo,
        null,
        null,
        PremiumBeneficioCodigo.STORIES,
        stories.nome(),
        stories.descricao(),
        opcoes);
  }

  private AtivacaoBeneficioEntity localizarDireitoDisponivel(UUID anuncioId, UUID usuarioId) {
    var beneficio = beneficioRepository.findByCodigo(PremiumBeneficioCodigo.STORIES).orElse(null);
    if (beneficio == null) {
      return null;
    }
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
        .sorted(Comparator
            .comparing(AtivacaoBeneficioEntity::getFimEm)
            .thenComparing(
                AtivacaoBeneficioEntity::getCriadoEm,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AtivacaoBeneficioEntity::getId))
        .findFirst()
        .orElse(null);
    if (ativa != null) {
      return ativa;
    }
    return candidatas.stream()
        .filter(this::aguardandoUso)
        .sorted(Comparator
            .comparing(
                AtivacaoBeneficioEntity::getCriadoEm,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AtivacaoBeneficioEntity::getId))
        .findFirst()
        .orElse(null);
  }

  private boolean aguardandoUso(AtivacaoBeneficioEntity ativacao) {
    return ativacao.getStatus() == StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO
        && ativacao.getInicioEm() == null
        && ativacao.getFimEm() == null;
  }

  private boolean ativaDisponivel(AtivacaoBeneficioEntity ativacao, OffsetDateTime agora) {
    return ativacao.getStatus() == StatusAtivacaoBeneficio.ATIVA
        && ativacao.getInicioEm() != null
        && ativacao.getFimEm() != null
        && !ativacao.getInicioEm().isAfter(agora)
        && ativacao.getFimEm().isAfter(agora);
  }
}
