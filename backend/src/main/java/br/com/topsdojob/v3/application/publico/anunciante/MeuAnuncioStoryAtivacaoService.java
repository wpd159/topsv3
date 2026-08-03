package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.admin.stories.AdminStoryConfiguracaoService.DURACAO_HORAS;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryAtivacaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryAtivacaoRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeuAnuncioStoryAtivacaoService {

  private final MeusAnunciosConsultaService anunciosService;
  private final CreditoLedgerOperacaoService ledgerService;
  private final MovimentoCreditoRepository movimentoRepository;
  private final StoryConfiguracaoComercialRepository configuracaoRepository;
  private final BeneficioPremiumRepository beneficioRepository;
  private final GrupoAtivacaoBeneficioRepository grupoRepository;
  private final AtivacaoBeneficioRepository ativacaoRepository;
  private final StoryAnuncioRepository storyRepository;
  private final AuditoriaEventoRepository auditoriaRepository;

  public MeuAnuncioStoryAtivacaoService(
      MeusAnunciosConsultaService anunciosService,
      CreditoLedgerOperacaoService ledgerService,
      MovimentoCreditoRepository movimentoRepository,
      StoryConfiguracaoComercialRepository configuracaoRepository,
      BeneficioPremiumRepository beneficioRepository,
      GrupoAtivacaoBeneficioRepository grupoRepository,
      AtivacaoBeneficioRepository ativacaoRepository,
      StoryAnuncioRepository storyRepository,
      AuditoriaEventoRepository auditoriaRepository) {
    this.anunciosService = anunciosService;
    this.ledgerService = ledgerService;
    this.movimentoRepository = movimentoRepository;
    this.configuracaoRepository = configuracaoRepository;
    this.beneficioRepository = beneficioRepository;
    this.grupoRepository = grupoRepository;
    this.ativacaoRepository = ativacaoRepository;
    this.storyRepository = storyRepository;
    this.auditoriaRepository = auditoriaRepository;
  }

  @Transactional
  public MeuAnuncioStoryAtivacaoDto ativar(
      String slug,
      MeuAnuncioStoryAtivacaoRequest request,
      String idempotencyKey,
      Authentication authentication,
      String requestId) {
    if (request == null
        || request.custoCreditosEsperado() == null
        || request.versaoConfiguracao() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirmacao da oferta obrigatoria");
    }
    UUID usuarioId = anunciosService.usuarioAutenticado(authentication).getId();
    AnuncioEntity anuncio = anunciosService.anuncioDoUsuario(slug, authentication);
    validarAnuncio(anuncio);
    String chave = "story-compra:" + usuarioId + ":"
        + CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
    int saldoAtual = ledgerService.bloquearEConsultarSaldo(usuarioId);

    GrupoAtivacaoBeneficioEntity repetido = grupoRepository.findByIdempotencyKey(chave).orElse(null);
    if (repetido != null) {
      return resultadoRepetido(repetido, anuncio, usuarioId, request, saldoAtual);
    }

    var configuracao = configuracaoRepository.findForUpdate()
        .orElseThrow(() -> ofertaIndisponivel());
    if (!Boolean.TRUE.equals(configuracao.getAtivo())) {
      throw ofertaIndisponivel();
    }
    if (!Objects.equals(configuracao.getVersao(), request.versaoConfiguracao())
        || !Objects.equals(configuracao.getCustoCreditos(), request.custoCreditosEsperado())) {
      throw ofertaAtualizada();
    }

    var beneficio = beneficioRepository.findByCodigo(STORIES)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT, "identidade tecnica de Stories ausente"));
    List<StoryAnuncioEntity> stories = storyRepository.findByAnuncioIdForUpdate(anuncio.getId());
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    if (stories.stream().anyMatch(item -> storyAtivo(item, agora))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "o anuncio ja possui Story ativo");
    }
    Set<UUID> consumidas = stories.stream()
        .map(StoryAnuncioEntity::getAtivacaoBeneficioId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    boolean direitoExistente = ativacaoRepository.findByAnuncioId(anuncio.getId()).stream()
        .filter(item -> usuarioId.equals(item.getUsuarioId()))
        .filter(item -> beneficio.getId().equals(item.getBeneficioId()))
        .filter(item -> item.getRevogadaEm() == null)
        .filter(item -> !consumidas.contains(item.getId()))
        .anyMatch(item -> item.getStatus() == StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO
            || (item.getStatus() == StatusAtivacaoBeneficio.ATIVA
                && item.getFimEm() != null
                && item.getFimEm().isAfter(agora)));
    if (direitoExistente) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "direito de Story ja disponivel");
    }

    int custo = configuracao.getCustoCreditos();
    if (saldoAtual < custo) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "saldo de creditos insuficiente");
    }
    GrupoAtivacaoBeneficioEntity grupo = grupoRepository.save(
        GrupoAtivacaoBeneficioEntity.criarCompraComCreditos(
            UUID.randomUUID(), usuarioId, anuncio.getId(), agora,
            agora.plusHours(DURACAO_HORAS), chave, agora));
    String chaveAtivacao = chave + ":ativacao:v" + request.versaoConfiguracao()
        + ":c" + request.custoCreditosEsperado();
    AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(
        AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
            UUID.randomUUID(), beneficio.getId(), null, usuarioId, anuncio.getId(),
            grupo.getId(), custo, chaveAtivacao, agora));

    int saldoPosterior = saldoAtual;
    if (custo > 0) {
      saldoPosterior = ledgerService.registrar(
          usuarioId,
          TipoMovimentoCredito.SAIDA,
          DirecaoMovimentoCredito.DEBITO,
          custo,
          saldoAtual,
          OrigemMovimentoCredito.BENEFICIO,
          "ATIVACAO_BENEFICIO",
          ativacao.getId(),
          chave + ":debito",
          usuarioId,
          "Ativacao de Story por 24 horas",
          requestId).movimento().getSaldoDepois();
    }
    auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
        UUID.randomUUID(), usuarioId, "STORY_ATIVACAO_CREDITOS", "ATIVACAO_BENEFICIO",
        ativacao.getId(), null,
        "{\"custoCreditos\":" + custo + ",\"duracaoHoras\":24}",
        requestId, agora));
    return new MeuAnuncioStoryAtivacaoDto(
        ativacao.getId(), custo, saldoAtual, saldoPosterior, DURACAO_HORAS, false);
  }

  private MeuAnuncioStoryAtivacaoDto resultadoRepetido(
      GrupoAtivacaoBeneficioEntity grupo,
      AnuncioEntity anuncio,
      UUID usuarioId,
      MeuAnuncioStoryAtivacaoRequest request,
      int saldoAtual) {
    if (!anuncio.getId().equals(grupo.getAnuncioId()) || !usuarioId.equals(grupo.getUsuarioId())) {
      throw idempotenciaDivergente();
    }
    List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
    if (ativacoes.size() != 1) {
      throw idempotenciaDivergente();
    }
    AtivacaoBeneficioEntity ativacao = ativacoes.get(0);
    var beneficio = beneficioRepository.findById(ativacao.getBeneficioId()).orElse(null);
    String chaveAtivacaoEsperada = grupo.getIdempotencyKey() + ":ativacao:v"
        + request.versaoConfiguracao() + ":c" + request.custoCreditosEsperado();
    if (beneficio == null
        || !STORIES.equals(beneficio.getCodigo())
        || !Objects.equals(ativacao.getCustoCreditosSnapshot(), request.custoCreditosEsperado())
        || !Objects.equals(ativacao.getIdempotencyKey(), chaveAtivacaoEsperada)) {
      throw idempotenciaDivergente();
    }
    int custo = ativacao.getCustoCreditosSnapshot();
    if (custo == 0) {
      return new MeuAnuncioStoryAtivacaoDto(
          ativacao.getId(), 0, saldoAtual, saldoAtual, DURACAO_HORAS, true);
    }
    var movimentos = movimentoRepository.findByReferenciaTipoAndReferenciaIdIn(
        "ATIVACAO_BENEFICIO", List.of(ativacao.getId())).stream()
        .filter(item -> item.getDirecao() == DirecaoMovimentoCredito.DEBITO)
        .toList();
    if (movimentos.size() != 1 || !Objects.equals(movimentos.get(0).getQuantidade(), custo)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "operacao idempotente possui ledger inconsistente");
    }
    return new MeuAnuncioStoryAtivacaoDto(
        ativacao.getId(), custo, movimentos.get(0).getSaldoAntes(),
        movimentos.get(0).getSaldoDepois(), DURACAO_HORAS, true);
  }

  private void validarAnuncio(AnuncioEntity anuncio) {
    if (anuncio.getStatus() != StatusAnuncio.PUBLICADO
        || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.APROVADO
        || anuncio.getRemovidoEm() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio nao esta publicavel para Story");
    }
  }

  private boolean storyAtivo(StoryAnuncioEntity story, OffsetDateTime agora) {
    return story.getStatus() == StatusStoryAnuncio.PUBLICADO
        && (story.getInicioEm() == null || !story.getInicioEm().isAfter(agora))
        && (story.getFimEm() == null || story.getFimEm().isAfter(agora));
  }

  private ResponseStatusException ofertaAtualizada() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "a oferta de Stories foi atualizada");
  }

  private ResponseStatusException ofertaIndisponivel() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "Stories esta temporariamente indisponivel para novas ativacoes");
  }

  private ResponseStatusException idempotenciaDivergente() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "Idempotency-Key reutilizada com outra intencao");
  }
}
