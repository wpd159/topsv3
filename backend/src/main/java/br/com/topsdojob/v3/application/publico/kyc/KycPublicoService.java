package br.com.topsdojob.v3.application.publico.kyc;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator.DocumentoValidado;
import br.com.topsdojob.v3.application.publico.kyc.dto.KycDocumentoDto;
import br.com.topsdojob.v3.application.publico.kyc.dto.KycStatusDto;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ParteDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KycPublicoService {

  private static final List<String> FORMATOS = List.of("JPG", "PNG", "PDF");

  private final MeusAnunciosConsultaService usuarioService;
  private final UsuarioRepository usuarioRepository;
  private final DocumentoUsuarioRepository documentoRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final DocumentoUploadValidator uploadValidator;
  private final DocumentoUploadProperties uploadProperties;
  private final R2StorageProperties storageProperties;
  private final ObjectProvider<ObjectStorage> storageProvider;

  public KycPublicoService(
      MeusAnunciosConsultaService usuarioService,
      UsuarioRepository usuarioRepository,
      DocumentoUsuarioRepository documentoRepository,
      ArquivoMidiaRepository arquivoRepository,
      AuditoriaEventoRepository auditoriaRepository,
      DocumentoUploadValidator uploadValidator,
      DocumentoUploadProperties uploadProperties,
      R2StorageProperties storageProperties,
      ObjectProvider<ObjectStorage> storageProvider) {
    this.usuarioService = usuarioService;
    this.usuarioRepository = usuarioRepository;
    this.documentoRepository = documentoRepository;
    this.arquivoRepository = arquivoRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.uploadValidator = uploadValidator;
    this.uploadProperties = uploadProperties;
    this.storageProperties = storageProperties;
    this.storageProvider = storageProvider;
  }

  @Transactional(readOnly = true)
  public KycStatusDto consultar(Authentication authentication) {
    UsuarioEntity usuario = usuarioService.usuarioAutenticado(authentication);
    return resposta(usuario);
  }

  @Transactional(readOnly = true)
  public void garantirProntoParaAnuncio(UUID usuarioId) {
    String statusAtual = status(documentosUltimoEnvio(usuarioId));
    if (!List.of("PENDENTE", "EM_ANALISE", "APROVADO").contains(statusAtual)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "complete o KYC antes de concluir o anuncio");
    }
  }

  @Transactional
  public KycStatusDto enviar(
      Authentication authentication,
      String nomeCivil,
      String cpf,
      String dataNascimento,
      String modoDocumento,
      MultipartFile documentoUnico,
      MultipartFile documentoFrente,
      MultipartFile documentoVerso,
      String requestId) {
    UsuarioEntity usuario = usuarioService.usuarioAutenticado(authentication);
    String statusAtual = status(documentosUltimoEnvio(usuario.getId()));
    if ("PENDENTE".equals(statusAtual) || "EM_ANALISE".equals(statusAtual) || "APROVADO".equals(statusAtual)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "KYC atual nao permite novo envio");
    }

    String nomeCivilFinal = validarNomeCivil(valorOuExistente(nomeCivil, usuario.getNomeCivil()));
    String cpfFinal = validarCpf(valorOuExistente(cpf, usuario.getCpfNormalizado()), usuario.getId());
    LocalDate nascimentoFinal = validarNascimento(valorOuExistente(dataNascimento, iso(usuario.getDataNascimento())));
    List<ParteArquivo> arquivos = validarArquivos(modoDocumento, documentoUnico, documentoFrente, documentoVerso);
    ObjectStorage storage = storageObrigatorio();
    UUID envioId = UUID.randomUUID();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    List<String> chavesCriadas = new ArrayList<>();

    try {
      for (ParteArquivo item : arquivos) {
        UUID arquivoId = UUID.randomUUID();
        DocumentoValidado validado = uploadValidator.validar(item.arquivo());
        String key = storageProperties.getDocumentPrefix()
            + "usuarios/" + usuario.getId()
            + "/envios/" + envioId
            + "/" + item.parte().name().toLowerCase(Locale.ROOT)
            + "-" + arquivoId + "." + validado.extensao();
        storage.put(StorageArea.PRIVATE_DOCUMENT, key, validado.bytes(), validado.mimeType());
        chavesCriadas.add(key);
        arquivoRepository.save(ArquivoMidiaEntity.criarUploadPendente(
            arquivoId,
            "R2",
            storageProperties.getDocumentBucket(),
            key,
            null,
            validado.mimeType(),
            validado.bytes().length,
            validado.largura(),
            validado.altura(),
            null,
            validado.sha256(),
            agora));
        documentoRepository.save(DocumentoUsuarioEntity.criarPendente(
            UUID.randomUUID(),
            usuario.getId(),
            arquivoId,
            envioId,
            item.parte(),
            agora));
      }
      usuario.aplicarDadosKyc(nomeCivilFinal, cpfFinal, nascimentoFinal, agora);
      usuarioRepository.saveAndFlush(usuario);
      auditoriaRepository.save(AuditoriaEventoEntity.registrarSistema(
          UUID.randomUUID(),
          usuario.getId(),
          "KYC_DOCUMENTOS_ENVIAR",
          "KYC_ENVIO",
          envioId,
          null,
          "{\"documentos\":" + arquivos.size() + ",\"dadosPrivadosOcultos\":true}",
          requestId,
          agora));
      limparObjetosSeRollback(storage, chavesCriadas);
      return resposta(usuario);
    } catch (DataIntegrityViolationException exception) {
      limparObjetosAgora(storage, chavesCriadas);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF ja cadastrado em outra conta");
    } catch (RuntimeException exception) {
      limparObjetosAgora(storage, chavesCriadas);
      throw exception;
    }
  }

  private List<ParteArquivo> validarArquivos(
      String modo,
      MultipartFile unico,
      MultipartFile frente,
      MultipartFile verso) {
    if ("PDF".equalsIgnoreCase(modo)) {
      if (vazio(unico) || !vazio(frente) || !vazio(verso)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "envie somente o PDF unico");
      }
      return List.of(new ParteArquivo(ParteDocumentoUsuario.UNICO, unico));
    }
    if ("FRENTE_VERSO".equalsIgnoreCase(modo)) {
      if (vazio(frente) || !vazio(unico)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frente do documento obrigatoria");
      }
      List<ParteArquivo> resultado = new ArrayList<>();
      resultado.add(new ParteArquivo(ParteDocumentoUsuario.FRENTE, frente));
      if (!vazio(verso)) resultado.add(new ParteArquivo(ParteDocumentoUsuario.VERSO, verso));
      return List.copyOf(resultado);
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modo de documento invalido");
  }

  private boolean vazio(MultipartFile arquivo) {
    return arquivo == null || arquivo.isEmpty();
  }

  private String validarNomeCivil(String valor) {
    String normalizado = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
    if (normalizado.length() < 3 || normalizado.length() > 180) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nome civil invalido");
    }
    return normalizado;
  }

  private String validarCpf(String valor, UUID usuarioId) {
    String normalizado = valor == null ? "" : valor.replaceAll("\\D", "");
    if (!cpfValido(normalizado)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF invalido");
    }
    usuarioRepository.findByCpfNormalizado(normalizado)
        .filter(existente -> !existente.getId().equals(usuarioId))
        .ifPresent(existente -> {
          throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF ja cadastrado em outra conta");
        });
    return normalizado;
  }

  private boolean cpfValido(String valor) {
    if (valor == null || !valor.matches("[0-9]{11}") || valor.chars().distinct().count() == 1) return false;
    int primeiro = digitoCpf(valor, 9, 10);
    int segundo = digitoCpf(valor, 10, 11);
    return primeiro == Character.digit(valor.charAt(9), 10)
        && segundo == Character.digit(valor.charAt(10), 10);
  }

  private int digitoCpf(String valor, int tamanho, int pesoInicial) {
    int soma = 0;
    for (int index = 0; index < tamanho; index++) {
      soma += Character.digit(valor.charAt(index), 10) * (pesoInicial - index);
    }
    int resto = 11 - (soma % 11);
    return resto >= 10 ? 0 : resto;
  }

  private LocalDate validarNascimento(String valor) {
    try {
      LocalDate nascimento = LocalDate.parse(valor == null ? "" : valor.trim());
      LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
      if (nascimento.isAfter(hoje) || Period.between(nascimento, hoje).getYears() < 18) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "KYC permitido apenas para maiores de 18 anos");
      }
      return nascimento;
    } catch (DateTimeParseException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "data de nascimento invalida");
    }
  }

  private String valorOuExistente(String informado, String existente) {
    return informado == null || informado.isBlank() ? existente : informado;
  }

  private KycStatusDto resposta(UsuarioEntity usuario) {
    List<DocumentoUsuarioEntity> documentos = documentosUltimoEnvio(usuario.getId());
    String status = status(documentos);
    Map<UUID, ArquivoMidiaEntity> arquivos = arquivoRepository.findByIdIn(documentos.stream()
            .map(DocumentoUsuarioEntity::getArquivoMidiaId).toList()).stream()
        .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
    String motivo = documentos.stream()
        .map(DocumentoUsuarioEntity::getMotivoModeracao)
        .filter(value -> value != null && !value.isBlank())
        .findFirst().orElse(null);
    List<KycDocumentoDto> itens = documentos.stream()
        .sorted(Comparator.comparing(DocumentoUsuarioEntity::getParte))
        .map(documento -> {
          ArquivoMidiaEntity arquivo = arquivos.get(documento.getArquivoMidiaId());
          return new KycDocumentoDto(
              documento.getId(),
              documento.getParte().name(),
              documento.getStatus().name(),
              arquivo == null ? null : arquivo.getMimeType(),
              arquivo == null || arquivo.getTamanhoBytes() == null ? 0L : arquivo.getTamanhoBytes());
        }).toList();
    return new KycStatusDto(
        status,
        usuario.getNomeCivil(),
        usuario.getCpfNormalizado() != null,
        mascararCpf(usuario.getCpfNormalizado()),
        iso(usuario.getDataNascimento()),
        motivo,
        List.of("PENDENTE", "EM_ANALISE", "APROVADO").contains(status),
        List.of("NAO_INICIADO", "REJEITADO", "AJUSTE_SOLICITADO").contains(status),
        uploadProperties.getMaxBytes(),
        FORMATOS,
        List.copyOf(itens));
  }

  private List<DocumentoUsuarioEntity> documentosUltimoEnvio(UUID usuarioId) {
    List<DocumentoUsuarioEntity> todos = documentoRepository
        .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(usuarioId);
    if (todos.isEmpty()) return List.of();
    UUID envioId = todos.get(0).getEnvioId();
    return todos.stream().filter(item -> envioId.equals(item.getEnvioId())).toList();
  }

  private String status(List<DocumentoUsuarioEntity> documentos) {
    if (documentos.isEmpty()) return "NAO_INICIADO";
    if (documentos.stream().allMatch(item -> item.getStatus() == StatusDocumentoUsuario.VALIDADO)) return "APROVADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.AJUSTE_SOLICITADO)) return "AJUSTE_SOLICITADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.REJEITADO)) return "REJEITADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.EM_ANALISE)) return "EM_ANALISE";
    return "PENDENTE";
  }

  private String mascararCpf(String cpf) {
    return cpf == null || cpf.length() != 11 ? null : "***.***.***-" + cpf.substring(9);
  }

  private String iso(LocalDate value) {
    return value == null ? null : value.toString();
  }

  private ObjectStorage storageObrigatorio() {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de documentos indisponivel");
    }
    return storage;
  }

  private void limparObjetosSeRollback(ObjectStorage storage, List<String> keys) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    List<String> snapshot = List.copyOf(keys);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) limparObjetosAgora(storage, snapshot);
      }
    });
  }

  private void limparObjetosAgora(ObjectStorage storage, List<String> keys) {
    for (String key : keys) {
      try {
        storage.delete(StorageArea.PRIVATE_DOCUMENT, key);
      } catch (RuntimeException ignored) {
        // Objeto permanece privado e pode ser reconciliado operacionalmente.
      }
    }
  }

  private record ParteArquivo(ParteDocumentoUsuario parte, MultipartFile arquivo) {
  }
}
