package br.com.topsdojob.v3.application.admin.staff;

import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.AtualizarRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.CriarRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Indicadores;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Pagina;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.application.operacional.outbox.OutboxEmailPayloadFactory;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminStaffJdbcRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminStaffService {
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final String RECURSO = "STAFF";

  private final AdminStaffJdbcRepository consulta;
  private final UsuarioRepository usuarios;
  private final PapelUsuarioRepository papeis;
  private final CredencialUsuarioRepository credenciais;
  private final AuditoriaEventoRepository auditorias;
  private final PasswordEncoder passwordEncoder;
  private final PublicSessionRegistry sessions;
  private final OutboxEventoRepository outbox;
  private final OutboxEmailPayloadFactory emailPayloads;

  public AdminStaffService(
      AdminStaffJdbcRepository consulta,
      UsuarioRepository usuarios,
      PapelUsuarioRepository papeis,
      CredencialUsuarioRepository credenciais,
      AuditoriaEventoRepository auditorias,
      PasswordEncoder passwordEncoder,
      PublicSessionRegistry sessions,
      OutboxEventoRepository outbox,
      OutboxEmailPayloadFactory emailPayloads) {
    this.consulta = consulta;
    this.usuarios = usuarios;
    this.papeis = papeis;
    this.credenciais = credenciais;
    this.auditorias = auditorias;
    this.passwordEncoder = passwordEncoder;
    this.sessions = sessions;
    this.outbox = outbox;
    this.emailPayloads = emailPayloads;
  }

  @Transactional(readOnly = true)
  public Pagina<Resumo> listar(
      String termo,
      String papel,
      String status,
      String ordenacao,
      int page,
      int size) {
    int pagina = Math.max(page, 0);
    int tamanho = Math.min(Math.max(size, 1), 100);
    String papelSeguro = filtroPapel(papel);
    Boolean ativo = filtroAtivo(status);
    String ordem = "ANTIGOS".equalsIgnoreCase(ordenacao) ? "ANTIGOS" : "RECENTES";
    List<Resumo> itens = consulta.listar(termo, papelSeguro, ativo, ordem, pagina, tamanho);
    long total = consulta.contar(termo, papelSeguro, ativo);
    int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / tamanho);
    return new Pagina<>(itens, pagina, tamanho, total, totalPaginas);
  }

  @Transactional(readOnly = true)
  public Indicadores indicadores() {
    return consulta.indicadores();
  }

  @Transactional(readOnly = true)
  public Detalhe detalhar(UUID id) {
    Resumo staff = consulta.detalhar(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "staff nao encontrado"));
    return new Detalhe(staff, consulta.permissoes(id), consulta.historico(id));
  }

  @Transactional
  public Detalhe criar(CriarRequest request, AdminUserPrincipal ator, String requestId) {
    validarAtor(ator);
    validarRequestId(requestId);
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dados obrigatorios");
    }
    String nome = nome(request.nome());
    String email = email(request.email());
    PapelUsuario papel = papel(request.papel());
    boolean ativo = request.ativo() == null || request.ativo();

    var existente = usuarios.findByEmailNormalizado(email);
    if (existente.isPresent()) {
      UUID id = existente.get().getId();
      if (auditorias.existsByAcaoAndRecursoIdAndRequestId("STAFF_CRIAR", id, requestId)
          && existente.get().getTipoConta() == TipoContaUsuario.STAFF) {
        return detalhar(id);
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "e-mail ja cadastrado");
    }

    OffsetDateTime agora = agora();
    UUID id = UUID.randomUUID();
    UsuarioEntity staff = UsuarioEntity.criarStaff(id, nome, email, ativo, agora);
    try {
      usuarios.saveAndFlush(staff);
      credenciais.save(CredencialUsuarioEntity.criarAcessoPendente(
          UUID.randomUUID(),
          id,
          passwordEncoder.encode(UUID.randomUUID() + ":" + UUID.randomUUID()),
          agora));
      papeis.save(PapelUsuarioEntity.criarStaff(id, papel, ator.usuarioId(), agora));
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "e-mail ja cadastrado");
    }

    auditorias.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        ator.usuarioId(),
        "STAFF_CRIAR",
        RECURSO,
        id,
        null,
        estadoJson(papel, ativo),
        requestId,
        agora));
    if (ativo) {
      outbox.save(OutboxEventoEntity.registrarPendente(
          UUID.randomUUID(),
          "USUARIO",
          id,
          "STAFF_CONVITE_CRIADO",
          emailPayloads.staff(id, papel.name()),
          "STAFF_CONVITE_CRIADO:" + id,
          agora));
    }
    return detalhar(id);
  }

  @Transactional
  public Detalhe atualizar(
      UUID id,
      AtualizarRequest request,
      AdminUserPrincipal ator,
      String requestId) {
    validarAtor(ator);
    validarRequestId(requestId);
    if (request == null || request.versao() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versao obrigatoria");
    }
    if (auditorias.existsByAcaoAndRecursoIdAndRequestId("STAFF_ATUALIZAR", id, requestId)) {
      return detalhar(id);
    }

    UsuarioEntity staff = usuarios.findByIdForUpdate(id)
        .filter(usuario -> usuario.getTipoConta() == TipoContaUsuario.STAFF)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "staff nao encontrado"));
    if (!Objects.equals(staff.getVersao(), request.versao())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "os dados mudaram; atualize a pagina e tente novamente");
    }

    PapelUsuario papelAnterior = papelAtual(id);
    PapelUsuario papelNovo = papel(request.papel());
    boolean ativoAnterior = "ATIVO".equals(staff.getStatus().name());
    boolean ativoNovo = request.ativo() == null ? ativoAnterior : request.ativo();
    String nomeNovo = nome(request.nome());
    boolean papelMudou = papelAnterior != papelNovo;
    boolean statusMudou = ativoAnterior != ativoNovo;
    boolean nomeMudou = !Objects.equals(staff.getNome(), nomeNovo);
    if (!papelMudou && !statusMudou && !nomeMudou) {
      return detalhar(id);
    }

    if (papelAnterior == PapelUsuario.ADMIN
        && ativoAnterior
        && (papelNovo != PapelUsuario.ADMIN || !ativoNovo)
        && consulta.bloquearAdministradoresAtivos().size() <= 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "o ultimo administrador ativo nao pode ser desativado ou rebaixado");
    }

    OffsetDateTime agora = agora();
    String antes = estadoJson(papelAnterior, ativoAnterior);
    staff.atualizarStaff(nomeNovo, ativoNovo, agora);
    usuarios.saveAndFlush(staff);
    if (papelMudou) {
      papeis.deleteByUsuarioId(id);
      papeis.save(PapelUsuarioEntity.criarStaff(id, papelNovo, ator.usuarioId(), agora));
    }
    auditorias.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        ator.usuarioId(),
        "STAFF_ATUALIZAR",
        RECURSO,
        id,
        antes,
        estadoJson(papelNovo, ativoNovo),
        requestId,
        agora));
    if (papelMudou || statusMudou) {
      invalidarSessoesAposCommit(id);
    }
    return detalhar(id);
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

  private PapelUsuario papelAtual(UUID usuarioId) {
    return papeis.findByUsuarioId(usuarioId).stream()
        .map(PapelUsuarioEntity::getPapel)
        .filter(papel -> papel == PapelUsuario.ADMIN || papel == PapelUsuario.MODERADOR)
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "staff sem papel administrativo gerenciavel"));
  }

  private void validarAtor(AdminUserPrincipal ator) {
    if (ator == null || !ator.papeis().contains(PapelUsuario.ADMIN)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "acesso negado");
    }
  }

  private String nome(String value) {
    String normalizado = value == null ? "" : value.trim().replaceAll("\\s+", " ");
    if (normalizado.length() < 2 || normalizado.length() > 120) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nome invalido");
    }
    return normalizado;
  }

  private String email(String value) {
    String normalizado = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (normalizado.length() > 254 || !EMAIL.matcher(normalizado).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "e-mail invalido");
    }
    return normalizado;
  }

  private PapelUsuario papel(String value) {
    try {
      PapelUsuario papel = PapelUsuario.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
      if (papel == PapelUsuario.ADMIN || papel == PapelUsuario.MODERADOR) {
        return papel;
      }
    } catch (IllegalArgumentException ignored) {
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "papel invalido");
  }

  private String filtroPapel(String value) {
    if (value == null || value.isBlank() || "TODOS".equalsIgnoreCase(value)) {
      return null;
    }
    return papel(value).name();
  }

  private Boolean filtroAtivo(String value) {
    if (value == null || value.isBlank() || "TODOS".equalsIgnoreCase(value)) {
      return null;
    }
    if ("ATIVO".equalsIgnoreCase(value)) return true;
    if ("INATIVO".equalsIgnoreCase(value)) return false;
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status invalido");
  }

  private void validarRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId obrigatorio");
    }
  }

  private String estadoJson(PapelUsuario papel, boolean ativo) {
    return "{\"papel\":\"" + papel.name() + "\",\"ativo\":" + ativo + "}";
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }
}
