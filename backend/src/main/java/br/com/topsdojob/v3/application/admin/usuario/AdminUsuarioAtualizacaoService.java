package br.com.topsdojob.v3.application.admin.usuario;

import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUsuarioAtualizacaoService {

  private final UsuarioRepository usuarioRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final AdminUsuarioConsultaService consultaService;

  public AdminUsuarioAtualizacaoService(
      UsuarioRepository usuarioRepository,
      AuditoriaEventoRepository auditoriaRepository,
      AdminUsuarioConsultaService consultaService) {
    this.usuarioRepository = usuarioRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.consultaService = consultaService;
  }

  @Transactional
  public AdminUsuarioDetalheDto atualizarTelefone(
      UUID usuarioId,
      AdminUsuarioAtualizacaoRequestDto request,
      AdminUserPrincipal ator,
      String requestId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dados de atualizacao obrigatorios");
    }
    String telefone = normalizarTelefone(request.telefone());
    UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
    usuarioRepository.findByTelefoneNormalizado(telefone)
        .filter(existente -> !existente.getId().equals(usuarioId))
        .ifPresent(existente -> {
          throw new ResponseStatusException(HttpStatus.CONFLICT, "telefone ja cadastrado");
        });

    if (!telefone.equals(usuario.getTelefoneNormalizado())) {
      OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
      boolean configuradoAntes = usuario.getTelefoneNormalizado() != null
          && !usuario.getTelefoneNormalizado().isBlank();
      usuario.atualizarPerfilPublico(usuario.getNome(), telefone, agora);
      usuarioRepository.save(usuario);
      auditoriaRepository.save(AuditoriaEventoEntity.registrar(
          UUID.randomUUID(),
          ator.usuarioId(),
          "USUARIO_TELEFONE_ATUALIZAR",
          "USUARIO",
          usuarioId,
          "{\"telefoneConfigurado\":" + configuradoAntes + ",\"dadosPrivadosOcultos\":true}",
          "{\"telefoneConfigurado\":true,\"dadosPrivadosOcultos\":true}",
          requestId,
          agora));
    }
    return consultaService.detalhar(usuarioId, ator);
  }

  private String normalizarTelefone(String value) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "telefone completo obrigatorio");
    }
    String trimmed = value.trim();
    if (trimmed.startsWith("+") && !trimmed.startsWith("+55")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "telefone brasileiro invalido");
    }
    String digits = trimmed.replaceAll("\\D", "");
    if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
      digits = digits.substring(2);
    }
    if (digits.length() != 10 && digits.length() != 11) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "telefone deve conter DDD e numero completo");
    }
    return "+55" + digits;
  }
}
