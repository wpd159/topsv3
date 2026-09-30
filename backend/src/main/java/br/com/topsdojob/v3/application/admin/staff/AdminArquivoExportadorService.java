package br.com.topsdojob.v3.application.admin.staff;

import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.ExportadorRequest;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminArquivoExportadorService {
  private static final String CONCEDER = "STAFF_ARQUIVO_EXPORTADOR_CONCEDER";
  private static final String REVOGAR = "STAFF_ARQUIVO_EXPORTADOR_REVOGAR";

  private final UsuarioRepository usuarios;
  private final PapelUsuarioRepository papeis;
  private final AuditoriaEventoRepository auditorias;
  private final PublicSessionRegistry sessions;
  private final AdminStaffService staffService;
  private final JdbcTemplate jdbc;
  private final String approvedTargetId;

  public AdminArquivoExportadorService(
      UsuarioRepository usuarios,
      PapelUsuarioRepository papeis,
      AuditoriaEventoRepository auditorias,
      PublicSessionRegistry sessions,
      AdminStaffService staffService,
      JdbcTemplate jdbc,
      @Value("${app.admin.arquivo-exportador.approved-target-id:}") String approvedTargetId) {
    this.usuarios = usuarios;
    this.papeis = papeis;
    this.auditorias = auditorias;
    this.sessions = sessions;
    this.staffService = staffService;
    this.jdbc = jdbc;
    this.approvedTargetId = approvedTargetId == null ? "" : approvedTargetId.trim();
  }

  @Transactional
  public Detalhe alterar(
      UUID id,
      ExportadorRequest request,
      AdminUserPrincipal ator,
      String requestId) {
    validarAtor(ator);
    validarAtorAtual(ator.usuarioId());
    if (requestId == null || requestId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId obrigatorio");
    }
    if (request == null || request.conceder() == null || request.versao() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisao e versao obrigatorias");
    }

    boolean conceder = request.conceder();
    if (conceder && (id == null || !id.toString().equalsIgnoreCase(approvedTargetId))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "destinatario nao aprovado para exportacao");
    }

    UsuarioEntity staff = usuarios.findByIdForUpdate(id)
        .filter(usuario -> usuario.getTipoConta() == TipoContaUsuario.STAFF)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "staff nao encontrado"));
    List<PapelUsuarioEntity> papeisAtuais = papeis.findByUsuarioId(id);
    boolean possuiPapel = papeisAtuais.stream()
        .anyMatch(papel -> papel.getPapel() == PapelUsuario.ARQUIVO_EXPORTADOR);
    String acao = conceder ? CONCEDER : REVOGAR;
    if (auditorias.existsByAcaoAndRecursoIdAndRequestId(acao, id, requestId)) {
      if (possuiPapel != conceder) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "o estado mudou apos esta operacao; atualize a pagina e tente novamente");
      }
      return staffService.detalhar(id);
    }
    if (!Objects.equals(staff.getVersao(), request.versao())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "os dados mudaram; atualize a pagina e tente novamente");
    }
    if (conceder && (staff.getStatus() != StatusUsuario.ATIVO
        || papeisAtuais.stream().noneMatch(papel -> papel.getPapel() == PapelUsuario.ADMIN))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "exportacao exige administrador ativo");
    }

    if (possuiPapel == conceder) {
      return staffService.detalhar(id);
    }

    Integer versaoAnterior = staff.getVersao();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    if (conceder) {
      papeis.save(PapelUsuarioEntity.criarExportador(id, ator.usuarioId(), agora));
    } else {
      papeis.deleteByUsuarioIdAndPapel(id, PapelUsuario.ARQUIVO_EXPORTADOR);
    }
    staff.marcarAlteracaoPapeisStaff(agora);
    usuarios.saveAndFlush(staff);
    papeis.flush();
    auditorias.saveAndFlush(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        ator.usuarioId(),
        acao,
        "STAFF",
        id,
        estadoJson(possuiPapel, versaoAnterior),
        estadoJson(conceder, staff.getVersao()),
        requestId,
        agora));
    invalidarSessoesAposCommit(id);
    return staffService.detalhar(id);
  }

  private void validarAtor(AdminUserPrincipal ator) {
    if (ator == null || !ator.isEnabled() || !ator.papeis().contains(PapelUsuario.ADMIN)
        || ator.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .noneMatch("ADMIN_CONFIGURAR"::equals)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "acesso negado");
    }
  }

  private void validarAtorAtual(UUID atorId) {
    Boolean autorizado = jdbc.queryForObject("""
        SELECT EXISTS (
          SELECT 1
          FROM usuario u
          JOIN papel_usuario pu ON pu.usuario_id = u.id AND pu.papel = 'ADMIN'
          JOIN papel_permissao pp ON pp.papel = pu.papel
          JOIN permissao p ON p.id = pp.permissao_id AND p.codigo = 'ADMIN_CONFIGURAR'
          WHERE u.id = ?
            AND u.tipo_conta = 'STAFF'
            AND u.status = 'ATIVO'
            AND u.desativado_em IS NULL
        )
        """, Boolean.class, atorId);
    if (!Boolean.TRUE.equals(autorizado)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "acesso negado");
    }
  }

  private String estadoJson(boolean concedido, Integer versao) {
    return "{\"papel\":\"ARQUIVO_EXPORTADOR\",\"concedido\":" + concedido
        + ",\"versao\":" + versao + "}";
  }

  private void invalidarSessoesAposCommit(UUID usuarioId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      sessions.invalidateAll(usuarioId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        sessions.invalidateAll(usuarioId);
      }
    });
  }
}
