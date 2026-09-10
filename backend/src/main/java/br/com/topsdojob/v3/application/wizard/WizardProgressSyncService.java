package br.com.topsdojob.v3.application.wizard;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncRequest;
import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncResponse;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.wizard.WizardProgressJdbcRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WizardProgressSyncService {

  private static final Pattern SESSION_ID =
      Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$");
  private static final List<String> MODOS = List.of("CREATE", "EDIT");
  private static final List<String> STEPS = List.of(
      "PERFIL", "LOCALIZACAO", "SERVICOS", "FOTOS",
      "REVISAO", "PREMIUM", "KYC", "CONCLUIDO");
  private static final List<String> STATUS_PERMITIDOS =
      List.of("EM_PREENCHIMENTO", "AGUARDANDO_MODERACAO");

  private final MeusAnunciosConsultaService meusAnunciosService;
  private final AnuncioRepository anuncioRepository;
  private final WizardProgressJdbcRepository repository;
  private final Clock clock;

  @Autowired
  public WizardProgressSyncService(
      MeusAnunciosConsultaService meusAnunciosService,
      AnuncioRepository anuncioRepository,
      WizardProgressJdbcRepository repository) {
    this(meusAnunciosService, anuncioRepository, repository, Clock.systemUTC());
  }

  WizardProgressSyncService(
      MeusAnunciosConsultaService meusAnunciosService,
      AnuncioRepository anuncioRepository,
      WizardProgressJdbcRepository repository,
      Clock clock) {
    this.meusAnunciosService = meusAnunciosService;
    this.anuncioRepository = anuncioRepository;
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public SyncResponse sincronizar(SyncRequest request, Authentication authentication) {
    if (request == null) {
      throw invalido("progresso do wizard obrigatorio");
    }
    UsuarioEntity usuario = meusAnunciosService.usuarioAutenticadoParaAtualizacao(authentication);
    String sessaoId = texto(request.sessionId());
    if (!SESSION_ID.matcher(sessaoId).matches()) {
      throw invalido("identificador de sessao invalido");
    }
    String modo = codigo(request.mode());
    String step = codigo(request.ultimoStep());
    String status = codigo(request.status());
    if (!MODOS.contains(modo)) throw invalido("modo do wizard invalido");
    if (!STEPS.contains(step)) throw invalido("etapa do wizard invalida");
    if (!STATUS_PERMITIDOS.contains(status)) throw invalido("status do wizard invalido");

    UUID solicitadoId = parseAnuncioId(request.anuncioId());
    UUID persistidoId = repository.findAnuncioIdPorSessao(usuario.getId(), sessaoId).orElse(null);
    if (persistidoId != null && solicitadoId != null && !persistidoId.equals(solicitadoId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "sessao do wizard pertence a outro anuncio");
    }
    UUID anuncioId = anuncioDoUsuario(persistidoId != null ? persistidoId : solicitadoId, usuario.getId());
    if ("AGUARDANDO_MODERACAO".equals(status)
        && (!"CONCLUIDO".equals(step) || anuncioId == null)) {
      throw invalido("conclusao do wizard exige anuncio vinculado");
    }

    OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    var row = repository.sincronizar(
        UUID.randomUUID(),
        sessaoId,
        usuario.getId(),
        anuncioId,
        modo,
        step,
        STEPS.indexOf(step),
        status,
        agora);
    if (!modo.equals(row.modo())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "sessao do wizard pertence a outro modo");
    }
    return new SyncResponse(row.id(), row.status(), row.ultimoStep(), row.atualizadoEm());
  }

  private UUID parseAnuncioId(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException exception) {
      throw invalido("anuncio do wizard invalido");
    }
  }

  private UUID anuncioDoUsuario(UUID id, UUID usuarioId) {
    if (id == null) return null;
    AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(id)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "anuncio do wizard nao encontrado"));
    if (!usuarioId.equals(anuncio.getUsuarioId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "anuncio do wizard pertence a outro usuario");
    }
    if (anuncio.getRemovidoEm() != null
        || anuncio.getStatus() == br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio.REMOVIDO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio encerrado nao pode concluir o wizard");
    }
    return id;
  }

  private String texto(String value) {
    return value == null ? "" : value.trim();
  }

  private String codigo(String value) {
    return texto(value).toUpperCase(Locale.ROOT);
  }

  private ResponseStatusException invalido(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
