package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioOperacaoJuridicaDto;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminBloqueioJuridicoRequest;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminDesbloqueioJuridicoRequest;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioBloqueioJuridicoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioStatusHistoricoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StorySelecaoAdministrativaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StorySelecaoAdministrativaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioJuridicoService {

  private static final int MOTIVO_MAX = 1000;
  private static final int OBSERVACAO_MAX = 2000;

  private final AnuncioRepository anuncioRepository;
  private final UsuarioRepository usuarioRepository;
  private final AnuncioBloqueioJuridicoRepository bloqueioRepository;
  private final AnuncioStatusHistoricoRepository statusHistoricoRepository;
  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final StoryAnuncioRepository storyRepository;
  private final StorySelecaoAdministrativaRepository storyAdminRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final PublicSessionRegistry sessionRegistry;
  private final ObjectMapper objectMapper;

  public AdminAnuncioJuridicoService(
      AnuncioRepository anuncioRepository,
      UsuarioRepository usuarioRepository,
      AnuncioBloqueioJuridicoRepository bloqueioRepository,
      AnuncioStatusHistoricoRepository statusHistoricoRepository,
      AnuncioMidiaRepository anuncioMidiaRepository,
      StoryAnuncioRepository storyRepository,
      StorySelecaoAdministrativaRepository storyAdminRepository,
      AuditoriaEventoRepository auditoriaRepository,
      PublicSessionRegistry sessionRegistry,
      ObjectMapper objectMapper) {
    this.anuncioRepository = anuncioRepository;
    this.usuarioRepository = usuarioRepository;
    this.bloqueioRepository = bloqueioRepository;
    this.statusHistoricoRepository = statusHistoricoRepository;
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.storyRepository = storyRepository;
    this.storyAdminRepository = storyAdminRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.sessionRegistry = sessionRegistry;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AdminAnuncioOperacaoJuridicaDto reativar(
      UUID anuncioId,
      AdminUserPrincipal administrador,
      String requestId) {
    validarAdministrador(administrador);
    Contexto contexto = bloquearContexto(anuncioId);
    AnuncioEntity anuncio = contexto.anuncio();
    if (contexto.usuario().getStatus() != StatusUsuario.ATIVO
        || bloqueioRepository.existsByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNull(
            contexto.usuario().getId(), EscopoBloqueioJuridico.ANUNCIO_E_USUARIO)
        || anuncio.getStatus() != StatusAnuncio.PAUSADO
        || anuncio.getStatusModeracao() != StatusModeracaoAnuncio.APROVADO
        || anuncio.getRemovidoEm() != null) {
      throw conflito("anuncio nao pode ser reativado");
    }

    OffsetDateTime agora = agora();
    StatusAnuncio statusAnterior = anuncio.getStatus();
    anuncio.reativarAdministrativamente(agora);
    anuncioRepository.save(anuncio);
    registrarStatus(anuncio, statusAnterior, "REATIVACAO_ADMINISTRATIVA", administrador.usuarioId(), agora);
    auditarAnuncio(
        anuncio,
        administrador,
        "ANUNCIO_REATIVADO_ADMINISTRATIVAMENTE",
        requestId,
        estado(statusAnterior, contexto.usuario().getStatus()),
        estado(anuncio.getStatus(), contexto.usuario().getStatus()),
        agora);
    return resultado(anuncio, contexto.usuario(), "REATIVAR", 0, StorySuspension.vazia(), agora);
  }

  @Transactional
  public AdminAnuncioOperacaoJuridicaDto bloquearAnuncio(
      UUID anuncioId,
      AdminBloqueioJuridicoRequest request,
      AdminUserPrincipal administrador,
      String requestId) {
    return bloquear(
        anuncioId,
        request,
        EscopoBloqueioJuridico.ANUNCIO,
        administrador,
        requestId);
  }

  @Transactional
  public AdminAnuncioOperacaoJuridicaDto bloquearAnuncioEUsuario(
      UUID anuncioId,
      AdminBloqueioJuridicoRequest request,
      AdminUserPrincipal administrador,
      String requestId) {
    return bloquear(
        anuncioId,
        request,
        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO,
        administrador,
        requestId);
  }

  @Transactional
  public AdminAnuncioOperacaoJuridicaDto desbloquearAnuncio(
      UUID anuncioId,
      AdminDesbloqueioJuridicoRequest request,
      AdminUserPrincipal administrador,
      String requestId) {
    validarAdministrador(administrador);
    String motivo = textoOpcional(request == null ? null : request.motivo(), MOTIVO_MAX, "motivo");
    Contexto contexto = bloquearContexto(anuncioId);
    AnuncioEntity anuncio = contexto.anuncio();
    AnuncioBloqueioJuridicoEntity bloqueio = bloqueioRepository.findAtivoPorAnuncioForUpdate(anuncioId)
        .orElseThrow(() -> conflito("anuncio nao possui bloqueio juridico ativo"));
    if (anuncio.getStatus() != StatusAnuncio.BLOQUEADO || !bloqueio.anuncioBloqueado()) {
      throw conflito("estado juridico do anuncio incompativel");
    }

    OffsetDateTime agora = agora();
    StatusAnuncio statusAnterior = anuncio.getStatus();
    anuncio.desbloquearJuridicamente(agora);
    bloqueio.desbloquearAnuncio(administrador.usuarioId(), requestIdSeguro(requestId), agora);
    anuncioRepository.save(anuncio);
    bloqueioRepository.save(bloqueio);
    registrarStatus(anuncio, statusAnterior, "DESBLOQUEIO_JURIDICO", administrador.usuarioId(), agora);

    Map<String, Object> depois = estado(anuncio.getStatus(), contexto.usuario().getStatus());
    depois.put("motivo", motivo);
    depois.put("bloqueioId", bloqueio.getId());
    auditarAnuncio(
        anuncio,
        administrador,
        "ANUNCIO_DESBLOQUEIO_JURIDICO",
        requestId,
        estado(statusAnterior, contexto.usuario().getStatus()),
        depois,
        agora);
    return resultado(anuncio, contexto.usuario(), "DESBLOQUEAR_ANUNCIO", 0, StorySuspension.vazia(), agora);
  }

  @Transactional
  public AdminAnuncioOperacaoJuridicaDto desbloquearUsuario(
      UUID anuncioId,
      AdminDesbloqueioJuridicoRequest request,
      AdminUserPrincipal administrador,
      String requestId) {
    validarAdministrador(administrador);
    String motivo = textoOpcional(request == null ? null : request.motivo(), MOTIVO_MAX, "motivo");
    Contexto contexto = bloquearContexto(anuncioId);
    UsuarioEntity usuario = contexto.usuario();
    AnuncioBloqueioJuridicoEntity bloqueio = bloqueioRepository.findAtivoPorUsuarioForUpdate(
            usuario.getId(), EscopoBloqueioJuridico.ANUNCIO_E_USUARIO)
        .orElseThrow(() -> conflito("usuario nao possui bloqueio juridico ativo"));
    if (!bloqueio.usuarioBloqueado() || usuario.getStatus() != StatusUsuario.SUSPENSO) {
      throw conflito("estado juridico do usuario incompativel");
    }

    OffsetDateTime agora = agora();
    StatusUsuario statusAnterior = usuario.getStatus();
    try {
      usuario.desbloquearJuridicamente(bloqueio.getStatusUsuarioAnterior(), agora);
      bloqueio.desbloquearUsuario(administrador.usuarioId(), requestIdSeguro(requestId), agora);
    } catch (IllegalStateException exception) {
      throw conflito("usuario nao pode ser desbloqueado");
    }
    usuarioRepository.save(usuario);
    bloqueioRepository.save(bloqueio);

    Map<String, Object> antes = estado(contexto.anuncio().getStatus(), statusAnterior);
    Map<String, Object> depois = estado(contexto.anuncio().getStatus(), usuario.getStatus());
    depois.put("motivo", motivo);
    depois.put("bloqueioId", bloqueio.getId());
    auditar(
        administrador,
        "USUARIO_DESBLOQUEIO_JURIDICO",
        "USUARIO",
        usuario.getId(),
        requestId,
        antes,
        depois,
        agora);
    return resultado(
        contexto.anuncio(), usuario, "DESBLOQUEAR_USUARIO", 0, StorySuspension.vazia(), agora);
  }

  private AdminAnuncioOperacaoJuridicaDto bloquear(
      UUID anuncioId,
      AdminBloqueioJuridicoRequest request,
      EscopoBloqueioJuridico escopo,
      AdminUserPrincipal administrador,
      String requestId) {
    validarAdministrador(administrador);
    if (request == null || request.categoria() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoria do bloqueio obrigatoria");
    }
    String motivo = textoObrigatorio(request.motivo(), 5, MOTIVO_MAX, "motivo obrigatorio");
    String observacao = textoOpcional(request.observacaoInterna(), OBSERVACAO_MAX, "observacao interna");
    String requestIdValidado = requestIdSeguro(requestId);
    Contexto contexto = bloquearContexto(anuncioId);
    AnuncioEntity anuncio = contexto.anuncio();
    UsuarioEntity usuario = contexto.usuario();

    if (bloqueioRepository.findAtivoPorAnuncioForUpdate(anuncioId).isPresent()) {
      throw conflito("anuncio ja possui bloqueio juridico ativo");
    }
    if (bloqueioRepository.findAtivoPorUsuarioForUpdate(
            usuario.getId(), EscopoBloqueioJuridico.ANUNCIO_E_USUARIO).isPresent()) {
      throw conflito("usuario ja possui bloqueio juridico ativo");
    }

    OffsetDateTime agora = agora();
    StatusAnuncio statusAnterior = anuncio.getStatus();
    StatusUsuario statusUsuarioAnterior = null;
    try {
      anuncio.bloquearJuridicamente(agora);
      if (escopo == EscopoBloqueioJuridico.ANUNCIO_E_USUARIO) {
        statusUsuarioAnterior = usuario.bloquearJuridicamente(agora);
      }
    } catch (IllegalStateException exception) {
      throw conflito("bloqueio juridico incompativel com o estado atual");
    }

    int anunciosPausados = 0;
    List<AnuncioEntity> alterados = new ArrayList<>();
    alterados.add(anuncio);
    if (escopo == EscopoBloqueioJuridico.ANUNCIO_E_USUARIO) {
      for (AnuncioEntity outro : contexto.anunciosUsuario()) {
        if (outro.getId().equals(anuncio.getId())) {
          continue;
        }
        StatusAnuncio statusOutro = outro.getStatus();
        if (outro.pausarPorBloqueioUsuario(agora)) {
          anunciosPausados++;
          alterados.add(outro);
          registrarStatus(
              outro,
              statusOutro,
              "PAUSA_POR_BLOQUEIO_JURIDICO_USUARIO",
              administrador.usuarioId(),
              agora);
        }
      }
    }

    anuncioRepository.saveAll(alterados);
    if (statusUsuarioAnterior != null) {
      usuarioRepository.save(usuario);
    }
    registrarStatus(
        anuncio,
        statusAnterior,
        "BLOQUEIO_JURIDICO:" + request.categoria().name(),
        administrador.usuarioId(),
        agora);

    AnuncioBloqueioJuridicoEntity bloqueio = bloqueioRepository.save(
        AnuncioBloqueioJuridicoEntity.registrar(
            UUID.randomUUID(),
            anuncio.getId(),
            usuario.getId(),
            escopo,
            request.categoria(),
            statusUsuarioAnterior,
            motivo,
            observacao,
            administrador.usuarioId(),
            requestIdValidado,
            agora));

    Set<UUID> anunciosAfetados = escopo == EscopoBloqueioJuridico.ANUNCIO_E_USUARIO
        ? contexto.anunciosUsuario().stream().map(AnuncioEntity::getId).collect(Collectors.toSet())
        : Set.of(anuncio.getId());
    StorySuspension stories = suspenderStories(anunciosAfetados, agora);

    Map<String, Object> antes = estado(statusAnterior, statusUsuarioAnterior == null
        ? usuario.getStatus()
        : statusUsuarioAnterior);
    Map<String, Object> depois = estado(anuncio.getStatus(), usuario.getStatus());
    depois.put("bloqueioId", bloqueio.getId());
    depois.put("escopo", escopo.name());
    depois.put("categoria", request.categoria().name());
    depois.put("motivoSanitizado", motivo);
    depois.put("observacaoInterna", observacao);
    depois.put("anunciosPausados", anunciosPausados);
    depois.put("storiesSuspensos", stories.quantidade());
    depois.put("storyAdministrativoSuspenso", stories.administrativo());
    auditarAnuncio(
        anuncio,
        administrador,
        "ANUNCIO_BLOQUEIO_JURIDICO",
        requestId,
        antes,
        depois,
        agora);
    if (escopo == EscopoBloqueioJuridico.ANUNCIO_E_USUARIO) {
      auditar(
          administrador,
          "USUARIO_BLOQUEIO_JURIDICO",
          "USUARIO",
          usuario.getId(),
          requestId,
          antes,
          depois,
          agora);
      invalidarSessoesAposCommit(usuario.getId());
    }
    return resultado(anuncio, usuario, "BLOQUEAR_" + escopo.name(), anunciosPausados, stories, agora);
  }

  private Contexto bloquearContexto(UUID anuncioId) {
    AnuncioEntity referencia = anuncioRepository.findById(Objects.requireNonNull(anuncioId, "anuncioId obrigatorio"))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(referencia.getUsuarioId())
        .orElseThrow(() -> conflito("proprietario do anuncio nao encontrado"));
    List<AnuncioEntity> anuncios = anuncioRepository.findByUsuarioIdForLegalBlock(usuario.getId());
    AnuncioEntity anuncio = anuncios.stream()
        .filter(item -> item.getId().equals(anuncioId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
    return new Contexto(anuncio, usuario, anuncios);
  }

  private StorySuspension suspenderStories(Set<UUID> anuncioIds, OffsetDateTime agora) {
    List<UUID> midiaIds = anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds).stream()
        .map(item -> item.getId())
        .toList();
    List<StoryAnuncioEntity> stories = midiaIds.isEmpty()
        ? List.of()
        : storyRepository.findByAnuncioMidiaIdInForUpdate(midiaIds);
    List<StoryAnuncioEntity> alterados = stories.stream()
        .filter(item -> item.suspenderPorBloqueio(agora))
        .toList();
    if (!alterados.isEmpty()) {
      storyRepository.saveAll(alterados);
    }

    storyAdminRepository.bloquearOperacao();
    StorySelecaoAdministrativaEntity selecao = storyAdminRepository.bloquearSingleton().orElse(null);
    boolean administrativo = selecao != null
        && selecao.isAtiva()
        && anuncioIds.contains(selecao.getAnuncioId());
    if (administrativo) {
      selecao.desativar(agora);
      storyAdminRepository.save(selecao);
    }
    return new StorySuspension(alterados.size(), administrativo);
  }

  private void registrarStatus(
      AnuncioEntity anuncio,
      StatusAnuncio statusAnterior,
      String motivo,
      UUID atorId,
      OffsetDateTime agora) {
    statusHistoricoRepository.save(AnuncioStatusHistoricoEntity.registrar(
        UUID.randomUUID(),
        anuncio.getId(),
        statusAnterior,
        anuncio.getStatus(),
        motivo,
        atorId,
        agora));
  }

  private void auditarAnuncio(
      AnuncioEntity anuncio,
      AdminUserPrincipal administrador,
      String acao,
      String requestId,
      Map<String, Object> antes,
      Map<String, Object> depois,
      OffsetDateTime agora) {
    auditar(
        administrador,
        acao,
        "ANUNCIO",
        anuncio.getId(),
        requestId,
        antes,
        depois,
        agora);
  }

  private void auditar(
      AdminUserPrincipal administrador,
      String acao,
      String recursoTipo,
      UUID recursoId,
      String requestId,
      Map<String, Object> antes,
      Map<String, Object> depois,
      OffsetDateTime agora) {
    auditoriaRepository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        administrador.usuarioId(),
        acao,
        recursoTipo,
        recursoId,
        json(antes),
        json(depois),
        requestIdSeguro(requestId),
        agora));
  }

  private Map<String, Object> estado(StatusAnuncio statusAnuncio, StatusUsuario statusUsuario) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("statusAnuncio", statusAnuncio == null ? null : statusAnuncio.name());
    estado.put("statusUsuario", statusUsuario == null ? null : statusUsuario.name());
    return estado;
  }

  private AdminAnuncioOperacaoJuridicaDto resultado(
      AnuncioEntity anuncio,
      UsuarioEntity usuario,
      String acao,
      int anunciosPausados,
      StorySuspension stories,
      OffsetDateTime agora) {
    return new AdminAnuncioOperacaoJuridicaDto(
        anuncio.getId(),
        anuncio.getStatus().name(),
        usuario.getId(),
        usuario.getStatus().name(),
        acao,
        anunciosPausados,
        stories.quantidade(),
        stories.administrativo(),
        agora);
  }

  private String textoObrigatorio(String value, int min, int max, String mensagem) {
    String seguro = value == null ? "" : value.trim();
    if (seguro.length() < min || seguro.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
    return seguro;
  }

  private String textoOpcional(String value, int max, String campo) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String seguro = value.trim();
    if (seguro.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " excede o limite permitido");
    }
    return seguro;
  }

  private String requestIdSeguro(String requestId) {
    return textoObrigatorio(requestId, 1, 200, "requestId obrigatorio");
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar auditoria juridica", exception);
    }
  }

  private void validarAdministrador(AdminUserPrincipal administrador) {
    if (administrador == null
        || !administrador.isEnabled()
        || !administrador.papeis().contains(PapelUsuario.ADMIN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "administrador obrigatorio");
    }
  }

  private void invalidarSessoesAposCommit(UUID usuarioId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      sessionRegistry.invalidateAll(usuarioId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        sessionRegistry.invalidateAll(usuarioId);
      }
    });
  }

  private ResponseStatusException conflito(String mensagem) {
    return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private record Contexto(
      AnuncioEntity anuncio,
      UsuarioEntity usuario,
      List<AnuncioEntity> anunciosUsuario) {
  }

  private record StorySuspension(int quantidade, boolean administrativo) {
    private static StorySuspension vazia() {
      return new StorySuspension(0, false);
    }
  }
}
