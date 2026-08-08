package br.com.topsdojob.v3.application.publico.anunciante;

import static br.com.topsdojob.v3.application.admin.stories.AdminStoryConfiguracaoService.DURACAO_HORAS;
import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.STORIES;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryAtivacaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryAtivacaoRequest;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDireitoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryOfertaDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MinhaContaStoriesDireitoService {

  private final MeusAnunciosConsultaService usuarioService;
  private final MeuAnuncioStoryConsultaService storyConsultaService;
  private final AnuncioRepository anuncioRepository;
  private final CreditoLedgerOperacaoService ledgerService;
  private final MovimentoCreditoRepository movimentoRepository;
  private final StoryConfiguracaoComercialRepository configuracaoRepository;
  private final BeneficioPremiumRepository beneficioRepository;
  private final GrupoAtivacaoBeneficioRepository grupoRepository;
  private final AtivacaoBeneficioRepository ativacaoRepository;
  private final StoryAnuncioRepository storyRepository;
  private final AuditoriaEventoRepository auditoriaRepository;

  public MinhaContaStoriesDireitoService(
      MeusAnunciosConsultaService usuarioService,
      MeuAnuncioStoryConsultaService storyConsultaService,
      AnuncioRepository anuncioRepository,
      CreditoLedgerOperacaoService ledgerService,
      MovimentoCreditoRepository movimentoRepository,
      StoryConfiguracaoComercialRepository configuracaoRepository,
      BeneficioPremiumRepository beneficioRepository,
      GrupoAtivacaoBeneficioRepository grupoRepository,
      AtivacaoBeneficioRepository ativacaoRepository,
      StoryAnuncioRepository storyRepository,
      AuditoriaEventoRepository auditoriaRepository) {
    this.usuarioService = usuarioService;
    this.storyConsultaService = storyConsultaService;
    this.anuncioRepository = anuncioRepository;
    this.ledgerService = ledgerService;
    this.movimentoRepository = movimentoRepository;
    this.configuracaoRepository = configuracaoRepository;
    this.beneficioRepository = beneficioRepository;
    this.grupoRepository = grupoRepository;
    this.ativacaoRepository = ativacaoRepository;
    this.storyRepository = storyRepository;
    this.auditoriaRepository = auditoriaRepository;
  }

  @Transactional(readOnly = true)
  public MinhaContaStoryOfertaDto consultar(
      String modoConteudo,
      UUID anuncioId,
      Authentication authentication) {
    UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
    ModoConteudoStory modo = modo(modoConteudo);
    AnuncioEntity anuncio = anuncio(modo, anuncioId, usuarioId, false);
    if (modo == ModoConteudoStory.ANUNCIO) {
      var ativo = storyConsultaService.consultarAtivos(List.of(anuncio.getId())).get(anuncio.getId());
      if (ativo != null) {
        return oferta(
            modo, anuncio.getId(), "STORY_ATIVO", null, null, null, null, null,
            ativo, null, null);
      }
    }

    AtivacaoBeneficioEntity direito = localizarDireitoDisponivel(modo, anuncio, usuarioId);
    if (direito != null) {
      return oferta(
          modo, anuncioId, "DIREITO_DISPONIVEL", null, null, null, null, null,
          null,
          new MinhaContaStoryDireitoDto(
              direito.getId(),
              statusPublico(direito),
              direito.getCustoCreditosSnapshot(),
              direito.getInicioEm(),
              direito.getFimEm()),
          null);
    }

    var configuracao = configuracaoRepository.findById(
        br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity.SINGLETON_ID)
        .orElse(null);
    if (configuracao == null) {
      return indisponivel(modo, anuncioId, false, null);
    }
    if (!Boolean.TRUE.equals(configuracao.getAtivo())) {
      return indisponivel(modo, anuncioId, true, configuracao.getVersao());
    }
    int saldo = ledgerService.consultarSaldo(usuarioId);
    int custo = configuracao.getCustoCreditos();
    return oferta(
        modo, anuncioId, "OFERTA_DISPONIVEL", true, true, custo,
        saldo, Math.max(0, saldo - custo), null, null, configuracao.getVersao(),
        Math.max(0, custo - saldo));
  }

  @Transactional
  public MinhaContaStoryAtivacaoDto ativar(
      MinhaContaStoryAtivacaoRequest request,
      String idempotencyKey,
      Authentication authentication,
      String requestId) {
    validarConfirmacao(request);
    UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
    ModoConteudoStory modo = modo(request.modoConteudo());
    AnuncioEntity anuncio = anuncio(modo, request.anuncioId(), usuarioId, true);
    UUID anuncioId = anuncio == null ? null : anuncio.getId();
    String chaveCliente = CreditoLedgerOperacaoService.chaveObrigatoria(idempotencyKey);
    String chave = "story-compra:" + usuarioId + ":" + modo.name() + ":"
        + (anuncioId == null ? "conta" : anuncioId) + ":" + chaveCliente;
    int saldoAtual = ledgerService.bloquearEConsultarSaldo(usuarioId);

    GrupoAtivacaoBeneficioEntity repetido = grupoRepository.findByIdempotencyKey(chave).orElse(null);
    if (repetido != null) {
      return resultadoRepetido(repetido, modo, anuncioId, usuarioId, request, saldoAtual);
    }

    var configuracao = configuracaoRepository.findForUpdate()
        .orElseThrow(this::ofertaIndisponivel);
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
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    if (modo == ModoConteudoStory.ANUNCIO) {
      var stories = storyRepository.findByAnuncioIdForUpdate(anuncioId);
      if (stories.stream().anyMatch(item -> storyAtivo(item, agora))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "o anuncio ja possui Story ativo");
      }
    }
    if (localizarDireitoDisponivel(modo, anuncio, usuarioId) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "direito de Story ja disponivel");
    }

    int custo = configuracao.getCustoCreditos();
    if (saldoAtual < custo) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "saldo de creditos insuficiente");
    }
    GrupoAtivacaoBeneficioEntity grupo = grupoRepository.save(
        GrupoAtivacaoBeneficioEntity.criarCompraComCreditos(
            UUID.randomUUID(), usuarioId, anuncioId, agora,
            agora.plusHours(DURACAO_HORAS), chave, agora));
    String chaveAtivacao = chave + ":ativacao:v" + request.versaoConfiguracao()
        + ":c" + request.custoCreditosEsperado();
    AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(
        AtivacaoBeneficioEntity.criarCompraAguardandoModeracao(
            UUID.randomUUID(), beneficio.getId(), null, usuarioId, anuncioId,
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
        UUID.randomUUID(),
        usuarioId,
        "STORY_DIREITO_ATIVADO",
        "ATIVACAO_BENEFICIO",
        ativacao.getId(),
        null,
        "{\"modoConteudo\":\"" + modo.name()
            + "\",\"custoCreditos\":" + custo + ",\"duracaoHoras\":24}",
        requestId,
        agora));
    return new MinhaContaStoryAtivacaoDto(
        ativacao.getId(), modo.name(), anuncioId, custo, saldoAtual, saldoPosterior,
        DURACAO_HORAS, false);
  }

  public DireitoPublicacao reservarParaPublicacao(
      ModoConteudoStory modo,
      AnuncioEntity anuncio,
      UUID usuarioId,
      OffsetDateTime agora) {
    AtivacaoBeneficioEntity ativacao = localizarDireitoDisponivel(modo, anuncio, usuarioId);
    if (ativacao == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "direito de Story obrigatorio");
    }
    GrupoAtivacaoBeneficioEntity grupo = grupoRepository
        .findByIdForUpdate(ativacao.getGrupoAtivacaoId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT, "grupo do direito de Story ausente"));
    UUID anuncioId = anuncio == null ? null : anuncio.getId();
    if (!Objects.equals(usuarioId, ativacao.getUsuarioId())
        || !Objects.equals(usuarioId, grupo.getUsuarioId())
        || ativacao.getOrigem() != grupo.getOrigem()
        || !vinculoCompativel(modo, anuncioId, ativacao, grupo)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "direito de Story inconsistente");
    }
    if (storyRepository.existsByAtivacaoBeneficioIdAndDireitoPreservadoFalse(ativacao.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "direito de Story ja consumido");
    }
    return new DireitoPublicacao(ativacao, grupo);
  }

  public DireitoPublicacao criarDireitoAdministrativoParaPublicacao(
      AnuncioEntity anuncio,
      UUID administradorId,
      String chavePublicacao,
      OffsetDateTime agora) {
    if (anuncio == null
        || anuncio.getId() == null
        || anuncio.getUsuarioId() == null
        || administradorId == null
        || chavePublicacao == null
        || agora == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "contexto administrativo de Story invalido");
    }
    var beneficio = beneficioRepository.findByCodigo(STORIES)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT, "identidade tecnica de Stories ausente"));
    String chaveGrupo = "story-admin-direito:" + chavePublicacao;
    String chaveAtivacao = chaveGrupo + ":ativacao";
    GrupoAtivacaoBeneficioEntity repetido = grupoRepository
        .findByIdempotencyKey(chaveGrupo)
        .orElse(null);
    if (repetido != null) {
      return direitoAdministrativoRepetido(
          repetido,
          beneficio.getId(),
          anuncio,
          administradorId,
          chaveAtivacao);
    }
    GrupoAtivacaoBeneficioEntity grupo = grupoRepository.save(
        GrupoAtivacaoBeneficioEntity.criarAdministrativa(
            UUID.randomUUID(),
            anuncio.getUsuarioId(),
            anuncio.getId(),
            administradorId,
            agora,
            agora.plusHours(DURACAO_HORAS),
            chaveGrupo,
            "Publicacao administrativa de Story",
            agora));
    AtivacaoBeneficioEntity ativacao = ativacaoRepository.save(
        AtivacaoBeneficioEntity.criarAdministrativaAguardandoModeracao(
            UUID.randomUUID(),
            beneficio.getId(),
            null,
            anuncio.getUsuarioId(),
            anuncio.getId(),
            grupo.getId(),
            administradorId,
            chaveAtivacao,
            agora));
    return new DireitoPublicacao(ativacao, grupo);
  }

  private DireitoPublicacao direitoAdministrativoRepetido(
      GrupoAtivacaoBeneficioEntity grupo,
      UUID beneficioId,
      AnuncioEntity anuncio,
      UUID administradorId,
      String chaveAtivacao) {
    List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
    if (grupo.getOrigem() != OrigemBeneficio.ADMIN
        || !Objects.equals(grupo.getUsuarioId(), anuncio.getUsuarioId())
        || !Objects.equals(grupo.getAnuncioId(), anuncio.getId())
        || !Objects.equals(grupo.getAtorUsuarioId(), administradorId)
        || ativacoes.size() != 1) {
      throw idempotenciaDivergente();
    }
    AtivacaoBeneficioEntity ativacao = ativacoes.get(0);
    if (ativacao.getOrigem() != OrigemBeneficio.ADMIN
        || !Objects.equals(ativacao.getBeneficioId(), beneficioId)
        || !Objects.equals(ativacao.getUsuarioId(), anuncio.getUsuarioId())
        || !Objects.equals(ativacao.getAnuncioId(), anuncio.getId())
        || !Objects.equals(ativacao.getAtorUsuarioId(), administradorId)
        || !Objects.equals(ativacao.getCustoCreditosSnapshot(), 0)
        || !Objects.equals(ativacao.getIdempotencyKey(), chaveAtivacao)) {
      throw idempotenciaDivergente();
    }
    return new DireitoPublicacao(ativacao, grupo);
  }

  public OffsetDateTime iniciarVigencia(
      DireitoPublicacao direito,
      OffsetDateTime publicadoEm) {
    OffsetDateTime fimEm = publicadoEm.plusHours(DURACAO_HORAS);
    try {
      direito.ativacao().iniciarVigenciaExclusiva(publicadoEm, fimEm);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "direito de Story indisponivel");
    }
    direito.grupo().estenderValidadeAte(fimEm, publicadoEm);
    ativacaoRepository.save(direito.ativacao());
    grupoRepository.save(direito.grupo());
    return fimEm;
  }

  private MinhaContaStoryAtivacaoDto resultadoRepetido(
      GrupoAtivacaoBeneficioEntity grupo,
      ModoConteudoStory modo,
      UUID anuncioId,
      UUID usuarioId,
      MinhaContaStoryAtivacaoRequest request,
      int saldoAtual) {
    if (!Objects.equals(anuncioId, grupo.getAnuncioId())
        || !usuarioId.equals(grupo.getUsuarioId())) {
      throw idempotenciaDivergente();
    }
    List<AtivacaoBeneficioEntity> ativacoes = ativacaoRepository.findByGrupoAtivacaoId(grupo.getId());
    if (ativacoes.size() != 1) {
      throw idempotenciaDivergente();
    }
    AtivacaoBeneficioEntity ativacao = ativacoes.get(0);
    var beneficio = beneficioRepository.findById(ativacao.getBeneficioId()).orElse(null);
    String esperada = grupo.getIdempotencyKey() + ":ativacao:v"
        + request.versaoConfiguracao() + ":c" + request.custoCreditosEsperado();
    if (beneficio == null
        || !STORIES.equals(beneficio.getCodigo())
        || !Objects.equals(ativacao.getCustoCreditosSnapshot(), request.custoCreditosEsperado())
        || !Objects.equals(ativacao.getIdempotencyKey(), esperada)) {
      throw idempotenciaDivergente();
    }
    int custo = ativacao.getCustoCreditosSnapshot();
    if (custo == 0) {
      return new MinhaContaStoryAtivacaoDto(
          ativacao.getId(), modo.name(), anuncioId, 0, saldoAtual, saldoAtual,
          DURACAO_HORAS, true);
    }
    var movimentos = movimentoRepository.findByReferenciaTipoAndReferenciaIdIn(
        "ATIVACAO_BENEFICIO", List.of(ativacao.getId())).stream()
        .filter(item -> item.getDirecao() == DirecaoMovimentoCredito.DEBITO)
        .toList();
    if (movimentos.size() != 1 || !Objects.equals(movimentos.get(0).getQuantidade(), custo)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "operacao idempotente possui ledger inconsistente");
    }
    return new MinhaContaStoryAtivacaoDto(
        ativacao.getId(), modo.name(), anuncioId, custo,
        movimentos.get(0).getSaldoAntes(), movimentos.get(0).getSaldoDepois(),
        DURACAO_HORAS, true);
  }

  private AtivacaoBeneficioEntity localizarDireitoDisponivel(
      ModoConteudoStory modo,
      AnuncioEntity anuncio,
      UUID usuarioId) {
    var beneficio = beneficioRepository.findByCodigo(STORIES).orElse(null);
    if (beneficio == null) {
      return null;
    }
    UUID anuncioId = anuncio == null ? null : anuncio.getId();
    List<AtivacaoBeneficioEntity> candidatas = (modo == ModoConteudoStory.ANUNCIO
        ? ativacaoRepository.findByAnuncioId(anuncioId)
        : ativacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)).stream()
        .filter(item -> Objects.equals(item.getUsuarioId(), usuarioId))
        .filter(item -> Objects.equals(item.getBeneficioId(), beneficio.getId()))
        .filter(item -> item.getRevogadaEm() == null)
        .filter(item -> modo != ModoConteudoStory.MIDIA_UPLOAD
            || direitoMidiaUploadCompativel(item))
        .filter(item -> !storyRepository
            .existsByAtivacaoBeneficioIdAndDireitoPreservadoFalse(item.getId()))
        .toList();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    AtivacaoBeneficioEntity ativa = candidatas.stream()
        .filter(item -> ativaDisponivel(item, agora))
        .sorted(Comparator.comparing(AtivacaoBeneficioEntity::getFimEm)
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
        .sorted(Comparator.comparing(
            AtivacaoBeneficioEntity::getCriadoEm,
            Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AtivacaoBeneficioEntity::getId))
        .findFirst()
        .orElse(null);
  }

  private boolean direitoMidiaUploadCompativel(AtivacaoBeneficioEntity ativacao) {
    return ativacao.getAnuncioId() == null
        || storyRepository
            .existsByAtivacaoBeneficioIdAndDireitoPreservadoTrueAndEncerradoEmIsNotNull(
                ativacao.getId());
  }

  private boolean vinculoCompativel(
      ModoConteudoStory modo,
      UUID anuncioId,
      AtivacaoBeneficioEntity ativacao,
      GrupoAtivacaoBeneficioEntity grupo) {
    if (modo == ModoConteudoStory.ANUNCIO) {
      return Objects.equals(anuncioId, ativacao.getAnuncioId())
          && Objects.equals(anuncioId, grupo.getAnuncioId());
    }
    if (ativacao.getAnuncioId() == null && grupo.getAnuncioId() == null) {
      return true;
    }
    return ativacao.getAnuncioId() != null
        && Objects.equals(ativacao.getAnuncioId(), grupo.getAnuncioId())
        && storyRepository
            .existsByAtivacaoBeneficioIdAndDireitoPreservadoTrueAndEncerradoEmIsNotNull(
                ativacao.getId());
  }

  private AnuncioEntity anuncio(
      ModoConteudoStory modo,
      UUID anuncioId,
      UUID usuarioId,
      boolean bloquear) {
    if (modo == ModoConteudoStory.MIDIA_UPLOAD) {
      if (anuncioId != null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "MIDIA_UPLOAD nao aceita anuncioId");
      }
      return null;
    }
    if (anuncioId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ANUNCIO exige anuncioId");
    }
    AnuncioEntity anuncio = (bloquear
        ? anuncioRepository.findByIdForModeration(anuncioId)
        : anuncioRepository.findById(anuncioId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    if (!usuarioId.equals(anuncio.getUsuarioId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "anuncio pertence a outra conta");
    }
    validarAnuncio(anuncio);
    return anuncio;
  }

  private MinhaContaStoryOfertaDto indisponivel(
      ModoConteudoStory modo,
      UUID anuncioId,
      boolean configurada,
      Long versao) {
    return oferta(
        modo, anuncioId, "NOVAS_ATIVACOES_INDISPONIVEIS", configurada, false,
        null, null, null, null, null, versao);
  }

  private MinhaContaStoryOfertaDto oferta(
      ModoConteudoStory modo,
      UUID anuncioId,
      String estado,
      Boolean configurada,
      Boolean ativo,
      Integer custo,
      Integer saldo,
      Integer saldoProjetado,
      br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto storyAtivo,
      MinhaContaStoryDireitoDto direito,
      Long versao) {
    return oferta(
        modo, anuncioId, estado, configurada, ativo, custo, saldo, saldoProjetado,
        storyAtivo, direito, versao, null);
  }

  private MinhaContaStoryOfertaDto oferta(
      ModoConteudoStory modo,
      UUID anuncioId,
      String estado,
      Boolean configurada,
      Boolean ativo,
      Integer custo,
      Integer saldo,
      Integer saldoProjetado,
      br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto storyAtivo,
      MinhaContaStoryDireitoDto direito,
      Long versao,
      Integer deficit) {
    return new MinhaContaStoryOfertaDto(
        modo.name(), anuncioId, estado, configurada, ativo, DURACAO_HORAS,
        custo, saldo, saldoProjetado, deficit, storyAtivo, direito, versao);
  }

  private void validarConfirmacao(MinhaContaStoryAtivacaoRequest request) {
    if (request == null
        || request.custoCreditosEsperado() == null
        || request.versaoConfiguracao() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirmacao da oferta obrigatoria");
    }
  }

  private void validarAnuncio(AnuncioEntity anuncio) {
    if (anuncio.getStatus() != StatusAnuncio.PUBLICADO
        || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.APROVADO
        || anuncio.getRemovidoEm() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio nao esta publicavel para Story");
    }
  }

  private ModoConteudoStory modo(String value) {
    try {
      return ModoConteudoStory.valueOf(
          value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modoConteudo invalido");
    }
  }

  private String statusPublico(AtivacaoBeneficioEntity ativacao) {
    return aguardandoUso(ativacao) ? "DISPONIVEL_PARA_PUBLICAR" : ativacao.getStatus().name();
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

  private boolean storyAtivo(
      br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity story,
      OffsetDateTime agora) {
    return story.getModoConteudoEfetivo() == ModoConteudoStory.ANUNCIO
        && story.getStatus() == StatusStoryAnuncio.PUBLICADO
        && story.getEncerradoEm() == null
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

  public record DireitoPublicacao(
      AtivacaoBeneficioEntity ativacao,
      GrupoAtivacaoBeneficioEntity grupo) {
  }
}
