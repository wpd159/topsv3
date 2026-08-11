package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryRemocaoRequest;
import br.com.topsdojob.v3.application.publico.anunciante.dto.StoryEncerramentoDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.MotivoRemocaoStoryAdmin;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemEncerramentoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoryEncerramentoService {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoryEncerramentoService.class);

  private final MeusAnunciosConsultaService usuarioService;
  private final StoryAnuncioRepository storyRepository;
  private final StoryFalhaTecnicaService falhaTecnicaService;
  private final AtivacaoBeneficioRepository ativacaoRepository;
  private final GrupoAtivacaoBeneficioRepository grupoRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final StoryMidiaCleanupService cleanupService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public StoryEncerramentoService(
      MeusAnunciosConsultaService usuarioService,
      StoryAnuncioRepository storyRepository,
      StoryFalhaTecnicaService falhaTecnicaService,
      AtivacaoBeneficioRepository ativacaoRepository,
      GrupoAtivacaoBeneficioRepository grupoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      StoryMidiaCleanupService cleanupService,
      ObjectMapper objectMapper) {
    this(
        usuarioService,
        storyRepository,
        falhaTecnicaService,
        ativacaoRepository,
        grupoRepository,
        auditoriaRepository,
        cleanupService,
        objectMapper,
        Clock.systemUTC());
  }

  StoryEncerramentoService(
      MeusAnunciosConsultaService usuarioService,
      StoryAnuncioRepository storyRepository,
      StoryFalhaTecnicaService falhaTecnicaService,
      AtivacaoBeneficioRepository ativacaoRepository,
      GrupoAtivacaoBeneficioRepository grupoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      StoryMidiaCleanupService cleanupService,
      ObjectMapper objectMapper,
      Clock clock) {
    this.usuarioService = usuarioService;
    this.storyRepository = storyRepository;
    this.falhaTecnicaService = falhaTecnicaService;
    this.ativacaoRepository = ativacaoRepository;
    this.grupoRepository = grupoRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.cleanupService = cleanupService;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public StoryEncerramentoDto encerrarProprio(
      UUID storyId,
      Authentication authentication,
      String requestId) {
    UUID usuarioId = usuarioService.usuarioAutenticado(authentication).getId();
    StoryAnuncioEntity story = story(storyId);
    if (!usuarioId.equals(story.getCriadoPor())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Story pertence a outra conta");
    }
    boolean falhaTecnica = falhaTecnicaService.comprovada(story);
    String motivo = falhaTecnica ? "FALHA_TECNICA" : "EXCLUSAO_VOLUNTARIA";
    return encerrar(
        story,
        usuarioId,
        OrigemEncerramentoStory.USUARIO,
        motivo,
        null,
        falhaTecnica,
        requestId);
  }

  @Transactional
  public StoryEncerramentoDto removerComoAdmin(
      UUID storyId,
      AdminStoryRemocaoRequest request,
      AdminUserPrincipal administrador,
      String requestId) {
    if (administrador == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "administrador nao autenticado");
    }
    MotivoRemocaoStoryAdmin motivo = motivoAdmin(request);
    String descricao = descricaoSegura(request == null ? null : request.descricao(), motivo);
    StoryAnuncioEntity story = story(storyId);
    if (remocaoAdministrativaJaConcluida(story)) {
      LOGGER.info("story_encerramento_idempotente requestId={}", requestIdSeguro(requestId));
      return resposta(story, true);
    }
    if (story.getEncerradoEm() != null || story.getStatus() == StatusStoryAnuncio.REMOVIDO) {
      throw storyNaoRemovivel();
    }
    OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    boolean falhaTecnica = falhaTecnicaService.comprovada(story);
    if (!story.estaPublicamenteAtivo(agora) || falhaTecnica) {
      throw storyNaoRemovivel();
    }
    if (motivo == MotivoRemocaoStoryAdmin.ERRO_TECNICO && !falhaTecnica) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "falha tecnica nao comprovada pelo estado do Story");
    }
    return encerrar(
        story,
        administrador.usuarioId(),
        OrigemEncerramentoStory.ADMIN,
        motivo.name(),
        descricao,
        motivo == MotivoRemocaoStoryAdmin.ERRO_TECNICO,
        requestId);
  }

  private boolean remocaoAdministrativaJaConcluida(StoryAnuncioEntity story) {
    return story.getStatus() == StatusStoryAnuncio.REMOVIDO
        && story.getEncerradoEm() != null
        && story.getOrigemEncerramento() == OrigemEncerramentoStory.ADMIN;
  }

  private ResponseStatusException storyNaoRemovivel() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Story nao esta mais ativo para remocao administrativa");
  }

  private StoryEncerramentoDto encerrar(
      StoryAnuncioEntity story,
      UUID atorId,
      OrigemEncerramentoStory origem,
      String motivo,
      String descricao,
      boolean preservarDireito,
      String requestId) {
    String requestSeguro = requestIdSeguro(requestId);
    LOGGER.info("story_encerramento_iniciado requestId={}", requestSeguro);
    boolean jaEncerrado = story.getEncerradoEm() != null;
    if (jaEncerrado) {
      LOGGER.info("story_encerramento_idempotente requestId={}", requestSeguro);
      agendarCleanup(story, atorId, requestId);
      return resposta(story, true);
    }
    OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    String antes = snapshot(story);
    if (preservarDireito) {
      restaurarDireitoDaConta(story, agora, requestId);
    }
    if (!story.encerrar(atorId, origem, motivo, descricao, preservarDireito, agora)) {
      return resposta(story, true);
    }
    storyRepository.save(story);
    auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
        UUID.randomUUID(),
        atorId,
        origem == OrigemEncerramentoStory.ADMIN
            ? "STORY_REMOVIDO_PELO_ADMIN"
            : preservarDireito
                ? "STORY_COM_FALHA_DESCARTADO_PELO_USUARIO"
                : "STORY_EXCLUIDO_PELO_USUARIO",
        "STORY_ANUNCIO",
        story.getId(),
        antes,
        snapshot(story),
        requestId,
        agora));
    agendarCleanup(story, atorId, requestId);
    return resposta(story, false);
  }

  private void restaurarDireitoDaConta(
      StoryAnuncioEntity story,
      OffsetDateTime agora,
      String requestId) {
    if (story.getModoConteudoEfetivo() != ModoConteudoStory.MIDIA_UPLOAD
        || story.getAtivacaoBeneficioId() == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Story nao possui direito tecnico restauravel");
    }
    AtivacaoBeneficioEntity ativacao = ativacaoRepository
        .findByIdForUpdate(story.getAtivacaoBeneficioId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "direito do Story nao encontrado"));
    if (!Objects.equals(story.getCriadoPor(), ativacao.getUsuarioId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "direito do Story pertence a outra conta");
    }
    GrupoAtivacaoBeneficioEntity grupo = grupoRepository
        .findByIdForUpdate(ativacao.getGrupoAtivacaoId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "grupo do direito do Story nao encontrado"));
    if (!Objects.equals(story.getCriadoPor(), grupo.getUsuarioId())
        || grupo.getOrigem() != ativacao.getOrigem()
        || ativacaoRepository.findByGrupoAtivacaoId(grupo.getId()).size() != 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "direito tecnico nao pode ser isolado com seguranca");
    }

    UUID anuncioAtivacao = ativacao.getAnuncioId();
    UUID anuncioGrupo = grupo.getAnuncioId();
    boolean vinculoHistorico = anuncioAtivacao != null || anuncioGrupo != null;
    if (vinculoHistorico) {
      boolean storyHistoricoAtual = story.getArquivoMidiaId() == null
          && story.getAnuncioMidiaId() != null
          && Objects.equals(story.getAnuncioId(), anuncioAtivacao);
      boolean direitoHistoricoPreservado = storyRepository
          .existsByAtivacaoBeneficioIdAndDireitoPreservadoTrueAndEncerradoEmIsNotNull(
              ativacao.getId());
      if (anuncioAtivacao == null
          || !Objects.equals(anuncioAtivacao, anuncioGrupo)
          || !storyHistoricoAtual && !direitoHistoricoPreservado) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "vinculo historico do direito de Story inconsistente");
      }
    }

    try {
      if (vinculoHistorico) {
        ativacao.restaurarAposFalhaTecnicaPreservandoVinculoHistorico();
      } else {
        ativacao.restaurarAposFalhaTecnicaDaConta();
      }
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "direito tecnico revogado nao pode ser restaurado");
    }
    if (!vinculoHistorico) {
      grupo.desvincularAnuncioAposFalhaTecnica(agora);
      grupoRepository.save(grupo);
    }
    ativacaoRepository.save(ativacao);
    LOGGER.info("story_direito_preservado requestId={}", requestIdSeguro(requestId));
  }

  private void agendarCleanup(
      StoryAnuncioEntity story,
      UUID atorId,
      String requestId) {
    if (story.getModoConteudoEfetivo() != ModoConteudoStory.MIDIA_UPLOAD) {
      return;
    }
    Runnable cleanup = () -> executarCleanupSeguro(story.getId(), atorId, requestId);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      LOGGER.info(
          "story_encerramento_confirmado requestId={}",
          requestIdSeguro(requestId));
      cleanup.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        LOGGER.info(
            "story_encerramento_confirmado requestId={}",
            requestIdSeguro(requestId));
        cleanup.run();
      }
    });
  }

  private void executarCleanupSeguro(UUID storyId, UUID atorId, String requestId) {
    String requestSeguro = requestIdSeguro(requestId);
    LOGGER.info("story_cleanup_iniciado requestId={}", requestSeguro);
    try {
      StoryMidiaCleanupService.Resultado resultado =
          cleanupService.limpar(storyId, atorId, requestId);
      if (resultado == StoryMidiaCleanupService.Resultado.FALHOU) {
        LOGGER.warn("story_cleanup_falhou requestId={} resultado=FALHOU", requestSeguro);
      } else {
        LOGGER.info(
            "story_cleanup_concluido requestId={} resultado={}",
            requestSeguro,
            resultado == null ? "SEM_RESULTADO" : resultado.name());
      }
    } catch (RuntimeException exception) {
      LOGGER.error(
          "story_cleanup_falhou requestId={} tipo={}",
          requestSeguro,
          exception.getClass().getSimpleName());
    }
  }

  private StoryAnuncioEntity story(UUID storyId) {
    if (storyId == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Story nao encontrado");
    }
    return storyRepository.findByIdForUpdate(storyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Story nao encontrado"));
  }

  private MotivoRemocaoStoryAdmin motivoAdmin(AdminStoryRemocaoRequest request) {
    String valor = request == null || request.motivo() == null
        ? ""
        : request.motivo().trim();
    try {
      return MotivoRemocaoStoryAdmin.valueOf(valor);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "motivo administrativo obrigatorio e invalido");
    }
  }

  private String descricaoSegura(String value, MotivoRemocaoStoryAdmin motivo) {
    String descricao = value == null ? null : value.trim();
    if (descricao != null && descricao.isBlank()) {
      descricao = null;
    }
    if (motivo == MotivoRemocaoStoryAdmin.OUTRO && descricao == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "descricao obrigatoria para o motivo OUTRO");
    }
    if (descricao != null
        && (descricao.length() > 500
            || descricao.indexOf('<') >= 0
            || descricao.indexOf('>') >= 0
            || descricao.chars().anyMatch(Character::isISOControl))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "descricao administrativa invalida");
    }
    return descricao;
  }

  private StoryEncerramentoDto resposta(StoryAnuncioEntity story, boolean repetido) {
    return new StoryEncerramentoDto(
        story.getId(),
        story.getStatus().name(),
        story.getEncerradoEm(),
        story.getOrigemEncerramento() == null ? null : story.getOrigemEncerramento().name(),
        story.getMotivoEncerramento(),
        story.isDireitoPreservado(),
        repetido);
  }

  private String snapshot(StoryAnuncioEntity story) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("status", story.getStatus().name());
    estado.put("modoConteudo", story.getModoConteudoEfetivo().name());
    estado.put("encerradoEm", story.getEncerradoEm());
    estado.put("origemEncerramento", story.getOrigemEncerramento());
    estado.put("motivoEncerramento", story.getMotivoEncerramento());
    estado.put("descricaoEncerramento", story.getDescricaoEncerramento());
    estado.put("direitoPreservado", story.isDireitoPreservado());
    try {
      return objectMapper.writeValueAsString(estado);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "falha ao registrar auditoria do Story");
    }
  }

  private String requestIdSeguro(String requestId) {
    if (requestId == null
        || !requestId.matches("[A-Za-z0-9._:-]{1,128}")) {
      return "ausente";
    }
    return requestId;
  }
}
