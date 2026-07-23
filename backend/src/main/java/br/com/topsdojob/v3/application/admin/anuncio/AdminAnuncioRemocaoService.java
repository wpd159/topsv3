package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService.CleanupException;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService.Resultado;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioRemocaoDto;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioRemocaoRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioStatusHistoricoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioStatusHistoricoRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioRemocaoService {

  private static final int MOTIVO_MIN = 5;
  private static final int MOTIVO_MAX = 1000;
  private static final int REQUEST_ID_MAX = 200;

  private final AnuncioRepository anuncioRepository;
  private final AnuncioStatusHistoricoRepository statusHistoricoRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final AdminAnuncioMidiaCleanupService midiaCleanupService;
  private final AdminAnuncioRemocaoFalhaAuditService falhaAuditService;
  private final ObjectMapper objectMapper;

  public AdminAnuncioRemocaoService(
      AnuncioRepository anuncioRepository,
      AnuncioStatusHistoricoRepository statusHistoricoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      AdminAnuncioMidiaCleanupService midiaCleanupService,
      AdminAnuncioRemocaoFalhaAuditService falhaAuditService,
      ObjectMapper objectMapper) {
    this.anuncioRepository = anuncioRepository;
    this.statusHistoricoRepository = statusHistoricoRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.midiaCleanupService = midiaCleanupService;
    this.falhaAuditService = falhaAuditService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public AdminAnuncioRemocaoDto remover(
      UUID anuncioId,
      AdminAnuncioRemocaoRequest request,
      AdminUserPrincipal administrador,
      String requestId) {
    validarAdministrador(administrador);
    String motivo = textoObrigatorio(
        request == null ? null : request.motivo(),
        MOTIVO_MIN,
        MOTIVO_MAX,
        "motivo obrigatorio");
    String requestIdValidado = textoObrigatorio(
        requestId,
        1,
        REQUEST_ID_MAX,
        "requestId obrigatorio");
    AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));

    StatusAnuncio statusAnterior = anuncio.getStatus();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    if (!anuncio.podeRemoverPeloProprietario()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "transicao para REMOVIDO nao permitida");
    }

    Resultado limpeza;
    try {
      limpeza = midiaCleanupService.limpar(anuncio.getId(), agora);
    } catch (CleanupException exception) {
      falhaAuditService.registrar(
          anuncio.getId(),
          administrador.usuarioId(),
          requestIdValidado,
          exception.codigo());
      throw new ResponseStatusException(
          exception.status(),
          "falha ao excluir midias do anuncio; tente novamente",
          exception);
    }

    anuncio.removerLogicamente(agora);
    anuncioRepository.save(anuncio);
    statusHistoricoRepository.save(AnuncioStatusHistoricoEntity.registrar(
        UUID.randomUUID(),
        anuncio.getId(),
        statusAnterior,
        anuncio.getStatus(),
        "REMOCAO_ADMINISTRATIVA: " + motivo,
        administrador.usuarioId(),
        agora));

    Map<String, Object> antes = new LinkedHashMap<>();
    antes.put("statusAnuncio", statusAnterior == null ? null : statusAnterior.name());
    antes.put("statusModeracao", anuncio.getStatusModeracao() == null
        ? null
        : anuncio.getStatusModeracao().name());
    antes.put("removidoEm", null);
    Map<String, Object> depois = new LinkedHashMap<>();
    depois.put("statusAnuncio", anuncio.getStatus().name());
    depois.put("statusModeracao", anuncio.getStatusModeracao() == null
        ? null
        : anuncio.getStatusModeracao().name());
    depois.put("decisao", "REMOVER");
    depois.put("motivoSanitizado", motivo);
    depois.put("removidoEm", anuncio.getRemovidoEm().toString());
    depois.put("midiasRemovidas", limpeza.midiasRemovidas());
    depois.put("objetosR2Excluidos", limpeza.objetosExcluidos());
    depois.put("objetosR2JaAusentes", limpeza.objetosJaAusentes());
    depois.put("objetosCompartilhadosPreservados", limpeza.objetosCompartilhadosPreservados());
    depois.put("storiesEncerrados", limpeza.storiesEncerrados());
    depois.put("storyAdministrativoEncerrado", limpeza.storyAdministrativoEncerrado());
    auditoriaRepository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        administrador.usuarioId(),
        "ANUNCIO_REMOVIDO_ADMINISTRATIVAMENTE",
        "ANUNCIO",
        anuncio.getId(),
        json(antes),
        json(depois),
        requestIdValidado,
        agora));

    Map<String, Object> limpezaAntes = new LinkedHashMap<>();
    limpezaAntes.put("midiasVinculadas", limpeza.midiasRemovidas());
    Map<String, Object> limpezaDepois = new LinkedHashMap<>();
    limpezaDepois.put("objetosR2Excluidos", limpeza.objetosExcluidos());
    limpezaDepois.put("objetosR2JaAusentes", limpeza.objetosJaAusentes());
    limpezaDepois.put(
        "objetosCompartilhadosPreservados",
        limpeza.objetosCompartilhadosPreservados());
    limpezaDepois.put("storiesEncerrados", limpeza.storiesEncerrados());
    limpezaDepois.put("storyAdministrativoEncerrado", limpeza.storyAdministrativoEncerrado());
    auditoriaRepository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        administrador.usuarioId(),
        "ANUNCIO_MIDIAS_EXCLUIDAS_R2",
        "ANUNCIO",
        anuncio.getId(),
        json(limpezaAntes),
        json(limpezaDepois),
        requestIdValidado,
        agora));

    return new AdminAnuncioRemocaoDto(
        anuncio.getId(),
        anuncio.getStatus().name(),
        anuncio.getStatusModeracao() == null ? null : anuncio.getStatusModeracao().name(),
        "REMOVER",
        limpeza.midiasRemovidas(),
        limpeza.objetosExcluidos(),
        limpeza.objetosJaAusentes(),
        limpeza.objetosCompartilhadosPreservados(),
        limpeza.storiesEncerrados(),
        limpeza.storyAdministrativoEncerrado(),
        anuncio.getRemovidoEm(),
        agora);
  }

  private void validarAdministrador(AdminUserPrincipal administrador) {
    boolean podeModerar = administrador != null
        && administrador.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ANUNCIO_MODERAR"::equals);
    if (administrador == null
        || !administrador.isEnabled()
        || !administrador.papeis().contains(PapelUsuario.ADMIN)
        || !podeModerar) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "administrador obrigatorio");
    }
  }

  private String textoObrigatorio(String value, int min, int max, String mensagem) {
    String seguro = value == null ? "" : value.trim();
    if (seguro.length() < min || seguro.length() > max) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
    return seguro;
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("falha ao serializar auditoria de remocao", exception);
    }
  }
}
