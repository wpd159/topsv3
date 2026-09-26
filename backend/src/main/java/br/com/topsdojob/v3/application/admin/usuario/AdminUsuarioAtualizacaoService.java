package br.com.topsdojob.v3.application.admin.usuario;

import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioErroCampoDto;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeStoryRegistroService;
import br.com.topsdojob.v3.domain.usuario.CpfValidator;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUsuarioAtualizacaoService {

  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final UsuarioRepository usuarioRepository;
  private final AnuncioRepository anuncioRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final AdminUsuarioConsultaService consultaService;
  private final ArquivoPublicidadeRegistroService arquivoPublicidade;
  private final ArquivoPublicidadeStoryRegistroService arquivoStories;

  public AdminUsuarioAtualizacaoService(
      UsuarioRepository usuarioRepository,
      AnuncioRepository anuncioRepository,
      AuditoriaEventoRepository auditoriaRepository,
      AdminUsuarioConsultaService consultaService,
      ArquivoPublicidadeRegistroService arquivoPublicidade,
      ArquivoPublicidadeStoryRegistroService arquivoStories) {
    this.usuarioRepository = usuarioRepository;
    this.anuncioRepository = anuncioRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.consultaService = consultaService;
    this.arquivoPublicidade = arquivoPublicidade;
    this.arquivoStories = arquivoStories;
  }

  @Transactional
  public AdminUsuarioDetalheDto atualizar(
      UUID usuarioId,
      AdminUsuarioAtualizacaoRequestDto request,
      AdminUserPrincipal ator,
      String requestId) {
    if (request == null || !request.possuiCampoInformado()) {
      throw erro("dados", "DADOS_OBRIGATORIOS", "Informe ao menos um campo para atualizar.");
    }
    UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(usuarioId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
    if (usuario.getStatus() == StatusUsuario.EXCLUIDO) {
      throw erro(
          "dados",
          "CONTA_EXCLUIDA",
          "A conta excluida nao pode ser alterada.",
          HttpStatus.CONFLICT);
    }
    if (request.getVersao() == null || !request.getVersao().equals(usuario.getVersao())) {
      throw erro(
          "versao",
          "CADASTRO_DESATUALIZADO",
          "Os dados foram alterados por outra operacao. Atualize a pagina e tente novamente.",
          HttpStatus.CONFLICT);
    }

    String nome = request.isNomeInformado()
        ? textoObrigatorio(request.getNome(), "nome", 2, 120)
        : usuario.getNome();
    String nomeCivil = request.isNomeCivilInformado()
        ? textoObrigatorio(request.getNomeCivil(), "nomeCivil", 3, 180)
        : usuario.getNomeCivil();
    String email = request.isEmailInformado()
        ? email(request.getEmail())
        : usuario.getEmailNormalizado();
    String cpf = request.isCpfInformado()
        ? cpf(request.getCpf())
        : usuario.getCpfNormalizado();
    String telefone = request.isTelefoneInformado()
        ? telefone(request.getTelefone())
        : usuario.getTelefoneNormalizado();
    LocalDate dataNascimento = request.isDataNascimentoInformada()
        ? nascimento(request.getDataNascimento())
        : usuario.getDataNascimento();

    validarUnicidade(usuarioId, email, cpf, telefone);
    List<String> camposAlterados = camposAlterados(
        usuario,
        nome,
        nomeCivil,
        email,
        cpf,
        telefone,
        dataNascimento);
    if (camposAlterados.isEmpty()) {
      return consultaService.detalhar(usuarioId, ator);
    }

    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    usuario.atualizarDadosCadastrais(
        nome,
        nomeCivil,
        email,
        cpf,
        telefone,
        dataNascimento,
        agora);
    try {
      usuarioRepository.saveAndFlush(usuario);
    } catch (DataIntegrityViolationException exception) {
      if (camposAlterados.contains("cpf")
          && !camposAlterados.contains("email")
          && !camposAlterados.contains("telefone")) {
        throw erro(
            "cpf",
            "CPF_JA_CADASTRADO",
            "CPF já vinculado a outro usuário.",
            HttpStatus.CONFLICT);
      }
      throw erro(
          "dados",
          "DADOS_DUPLICADOS",
          "Um dos dados unicos ja pertence a outra conta.",
          HttpStatus.CONFLICT);
    }
    if (camposAlterados.contains("telefone")) {
      anuncioRepository.sincronizarTelefoneDoProprietario(usuarioId, telefone, agora);
    }
    auditoriaRepository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        ator.usuarioId(),
        "USUARIO_DADOS_CADASTRAIS_ATUALIZAR",
        "USUARIO",
        usuarioId,
        auditoria(camposAlterados),
        auditoria(camposAlterados),
        requestId,
        agora));
    if (camposAlterados.stream().anyMatch(campo -> List.of(
        "nome", "nomeCivil", "email", "cpf", "telefone").contains(campo))) {
      anuncioRepository.findByUsuarioIdAndRemovidoEmIsNull(usuarioId).stream()
          .map(br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity::getId)
          .sorted()
          .forEach(anuncioId -> arquivoPublicidade.registrarEstado(anuncioId,
              "CONTRATANTE_ATUALIZADO_ADMINISTRATIVAMENTE", requestId, agora));
      arquivoStories.registrarEstadoPorUsuario(usuarioId,
          "CONTRATANTE_ATUALIZADO_ADMINISTRATIVAMENTE", requestId, agora);
    }
    return consultaService.detalhar(usuarioId, ator);
  }

  private void validarUnicidade(UUID usuarioId, String email, String cpf, String telefone) {
    if (email != null) {
      usuarioRepository.findByEmailNormalizado(email)
          .filter(existente -> !existente.getId().equals(usuarioId))
          .ifPresent(existente -> {
            throw erro(
                "email",
                "EMAIL_JA_CADASTRADO",
                "O e-mail informado ja esta cadastrado.",
                HttpStatus.CONFLICT);
          });
    }
    if (cpf != null) {
      usuarioRepository.findByCpfNormalizado(cpf)
          .filter(existente -> !existente.getId().equals(usuarioId))
          .ifPresent(existente -> {
            throw erro(
                "cpf",
                "CPF_JA_CADASTRADO",
                "CPF já vinculado a outro usuário.",
                HttpStatus.CONFLICT);
          });
    }
    if (telefone != null) {
      usuarioRepository.findByTelefoneNormalizado(telefone)
          .filter(existente -> !existente.getId().equals(usuarioId))
          .ifPresent(existente -> {
            throw erro(
                "telefone",
                "TELEFONE_JA_CADASTRADO",
                "O telefone informado ja esta cadastrado.",
                HttpStatus.CONFLICT);
          });
    }
  }

  private String textoObrigatorio(
      String value,
      String campo,
      int minimo,
      int maximo) {
    String normalizado = value == null ? "" : value.trim().replaceAll("\\s+", " ");
    if (normalizado.length() < minimo || normalizado.length() > maximo) {
      throw erro(campo, "CAMPO_INVALIDO", "Revise o valor informado.");
    }
    return normalizado;
  }

  private String email(String value) {
    String normalizado = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (normalizado.length() > 254 || !EMAIL.matcher(normalizado).matches()) {
      throw erro("email", "EMAIL_INVALIDO", "Informe um e-mail valido.");
    }
    return normalizado;
  }

  private String cpf(String value) {
    String normalizado = value == null ? "" : value.replaceAll("\\D", "");
    if (!CpfValidator.isValid(normalizado)) {
      throw erro("cpf", "CPF_INVALIDO", "Informe um CPF valido.");
    }
    return normalizado;
  }

  private String telefone(String value) {
    if (value == null || value.isBlank()) {
      throw erro("telefone", "TELEFONE_INVALIDO", "Informe DDD e numero completo.");
    }
    String trimmed = value.trim();
    if (trimmed.startsWith("+") && !trimmed.startsWith("+55")) {
      throw erro("telefone", "TELEFONE_INVALIDO", "Informe um telefone brasileiro valido.");
    }
    String digits = trimmed.replaceAll("\\D", "");
    if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
      digits = digits.substring(2);
    }
    if (digits.length() != 10 && digits.length() != 11) {
      throw erro("telefone", "TELEFONE_INVALIDO", "Informe DDD e numero completo.");
    }
    return "+55" + digits;
  }

  private LocalDate nascimento(String value) {
    try {
      LocalDate data = LocalDate.parse(value == null ? "" : value.trim());
      LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
      if (data.isAfter(hoje) || Period.between(data, hoje).getYears() < 18) {
        throw erro(
            "dataNascimento",
            "DATA_NASCIMENTO_INVALIDA",
            "A data deve corresponder a uma pessoa maior de 18 anos.");
      }
      return data;
    } catch (DateTimeParseException exception) {
      throw erro(
          "dataNascimento",
          "DATA_NASCIMENTO_INVALIDA",
          "Informe a data no formato dia, mes e ano.");
    }
  }

  private List<String> camposAlterados(
      UsuarioEntity usuario,
      String nome,
      String nomeCivil,
      String email,
      String cpf,
      String telefone,
      LocalDate nascimento) {
    List<String> campos = new ArrayList<>();
    if (!java.util.Objects.equals(usuario.getNome(), nome)) campos.add("nome");
    if (!java.util.Objects.equals(usuario.getNomeCivil(), nomeCivil)) campos.add("nomeCivil");
    if (!java.util.Objects.equals(usuario.getEmailNormalizado(), email)) campos.add("email");
    if (!java.util.Objects.equals(usuario.getCpfNormalizado(), cpf)) campos.add("cpf");
    if (!java.util.Objects.equals(usuario.getTelefoneNormalizado(), telefone)) campos.add("telefone");
    if (!java.util.Objects.equals(usuario.getDataNascimento(), nascimento)) campos.add("dataNascimento");
    return List.copyOf(campos);
  }

  private String auditoria(List<String> campos) {
    return "{\"campos\":[\""
        + String.join("\",\"", campos)
        + "\"],\"dadosPrivadosOcultos\":true}";
  }

  private AdminUsuarioAtualizacaoException erro(
      String campo,
      String codigo,
      String mensagem) {
    return erro(campo, codigo, mensagem, HttpStatus.BAD_REQUEST);
  }

  private AdminUsuarioAtualizacaoException erro(
      String campo,
      String codigo,
      String mensagem,
      HttpStatus status) {
    return new AdminUsuarioAtualizacaoException(
        status,
        mensagem,
        new AdminUsuarioErroCampoDto(campo, codigo, mensagem));
  }
}
