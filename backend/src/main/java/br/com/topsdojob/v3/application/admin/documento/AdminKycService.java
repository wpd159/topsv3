package br.com.topsdojob.v3.application.admin.documento;

import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoRequestDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoResponseDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDocumentoDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycUrlTemporariaDto;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoSanitizer;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioAcessoEntity;
import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioAcessoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminKycService {

  private static final Duration URL_TTL = Duration.ofMinutes(5);

  private final DocumentoUsuarioRepository documentoRepository;
  private final DocumentoUsuarioAcessoRepository acessoRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final UsuarioRepository usuarioRepository;
  private final AuditoriaEventoRepository auditoriaRepository;
  private final R2StorageProperties storageProperties;
  private final ObjectProvider<ObjectStorage> storageProvider;

  public AdminKycService(
      DocumentoUsuarioRepository documentoRepository,
      DocumentoUsuarioAcessoRepository acessoRepository,
      ArquivoMidiaRepository arquivoRepository,
      UsuarioRepository usuarioRepository,
      AuditoriaEventoRepository auditoriaRepository,
      R2StorageProperties storageProperties,
      ObjectProvider<ObjectStorage> storageProvider) {
    this.documentoRepository = documentoRepository;
    this.acessoRepository = acessoRepository;
    this.arquivoRepository = arquivoRepository;
    this.usuarioRepository = usuarioRepository;
    this.auditoriaRepository = auditoriaRepository;
    this.storageProperties = storageProperties;
    this.storageProvider = storageProvider;
  }

  @Transactional(readOnly = true)
  public List<AdminKycEnvioDto> listarPendentes() {
    List<DocumentoUsuarioEntity> documentos = documentoRepository
        .findByStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmAsc(
            List.of(StatusDocumentoUsuario.PENDENTE, StatusDocumentoUsuario.EM_ANALISE));
    return documentos.stream()
        .collect(Collectors.groupingBy(
            DocumentoUsuarioEntity::getEnvioId,
            LinkedHashMap::new,
            Collectors.toList()))
        .values().stream()
        .map(this::mapear)
        .sorted(Comparator.comparing(AdminKycEnvioDto::enviadoEm))
        .toList();
  }

  @Transactional(readOnly = true)
  public AdminKycEnvioDto detalhar(UUID envioId) {
    return mapear(documentosDoEnvio(envioId));
  }

  @Transactional(readOnly = true)
  public List<AdminKycEnvioDto> listarPorUsuario(UUID usuarioId) {
    return documentoRepository
        .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(usuarioId)
        .stream()
        .collect(Collectors.groupingBy(
            DocumentoUsuarioEntity::getEnvioId,
            LinkedHashMap::new,
            Collectors.toList()))
        .values().stream()
        .map(this::mapear)
        .sorted(Comparator.comparing(AdminKycEnvioDto::enviadoEm).reversed())
        .toList();
  }

  @Transactional
  public AdminKycUrlTemporariaDto urlTemporaria(
      UUID documentoId,
      AdminUserPrincipal ator,
      String requestId) {
    DocumentoUsuarioEntity documento = documentoRepository.findById(documentoId)
        .filter(item -> item.getRemovidoEm() == null && item.getExpurgadoEm() == null)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "documento nao encontrado"));
    ArquivoMidiaEntity arquivo = arquivoRepository.findById(documento.getArquivoMidiaId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "arquivo do documento nao encontrado"));
    validarLocalPrivado(arquivo);
    ObjectStorage storage = storageObrigatorio();
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    documentosDoEnvio(documento.getEnvioId()).forEach(item -> item.marcarEmAnalise(agora));
    acessoRepository.save(DocumentoUsuarioAcessoEntity.registrarPermitido(
        UUID.randomUUID(),
        documento.getId(),
        ator.usuarioId(),
        requestId,
        agora));
    return new AdminKycUrlTemporariaDto(
        storage.temporaryGetUrl(StorageArea.PRIVATE_DOCUMENT, arquivo.getChaveObjeto(), URL_TTL).toString(),
        agora.plus(URL_TTL));
  }

  @Transactional
  public AdminKycDecisaoResponseDto decidir(
      UUID envioId,
      AdminKycDecisaoRequestDto request,
      AdminUserPrincipal ator,
      String requestId) {
    AdminDecisaoModeracaoAcao decisao = request == null ? null : request.decisao();
    if (decisao == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decisao obrigatoria");
    }
    String motivo = AdminModeracaoSanitizer.texto(request.motivo(), 240);
    if ((decisao == AdminDecisaoModeracaoAcao.REPROVAR
        || decisao == AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE)
        && (motivo == null || motivo.length() < 3)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo deve ter ao menos 3 caracteres");
    }
    List<DocumentoUsuarioEntity> documentos = documentosDoEnvio(envioId);
    if (documentos.stream().anyMatch(item -> item.getStatus() != StatusDocumentoUsuario.PENDENTE
        && item.getStatus() != StatusDocumentoUsuario.EM_ANALISE)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "envio documental ja decidido");
    }
    String statusAnterior = statusPublico(documentos);
    StatusDocumentoUsuario novoStatus = switch (decisao) {
      case APROVAR -> StatusDocumentoUsuario.VALIDADO;
      case REPROVAR -> StatusDocumentoUsuario.REJEITADO;
      case SOLICITAR_AJUSTE -> StatusDocumentoUsuario.AJUSTE_SOLICITADO;
    };
    StatusArquivoMidia statusArquivo = switch (decisao) {
      case APROVAR -> StatusArquivoMidia.VALIDADO;
      case REPROVAR -> StatusArquivoMidia.REJEITADO;
      case SOLICITAR_AJUSTE -> StatusArquivoMidia.PENDENTE;
    };
    OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
    documentos.forEach(item -> item.aplicarDecisao(novoStatus, ator.usuarioId(), motivo, agora));
    arquivoRepository.findByIdIn(documentos.stream().map(DocumentoUsuarioEntity::getArquivoMidiaId).toList())
        .forEach(arquivo -> arquivo.aplicarDecisao(statusArquivo));
    auditoriaRepository.save(AuditoriaEventoEntity.registrar(
        UUID.randomUUID(),
        ator.usuarioId(),
        "KYC_DOCUMENTOS_DECIDIR",
        "KYC_ENVIO",
        envioId,
        "{\"status\":\"" + statusAnterior + "\",\"dadosPrivadosOcultos\":true}",
        "{\"status\":\"" + novoStatus.name() + "\",\"motivoSanitizado\":"
            + (motivo == null ? "null" : "true") + ",\"dadosPrivadosOcultos\":true}",
        requestId,
        agora));
    return new AdminKycDecisaoResponseDto(envioId, statusPublico(documentos), requestId, agora);
  }

  private AdminKycEnvioDto mapear(List<DocumentoUsuarioEntity> documentos) {
    DocumentoUsuarioEntity primeiro = documentos.get(0);
    UsuarioEntity usuario = usuarioRepository.findById(primeiro.getUsuarioId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario do KYC nao encontrado"));
    Map<UUID, ArquivoMidiaEntity> arquivos = arquivoRepository
        .findByIdIn(documentos.stream().map(DocumentoUsuarioEntity::getArquivoMidiaId).toList()).stream()
        .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
    return new AdminKycEnvioDto(
        primeiro.getEnvioId(),
        usuario.getId(),
        usuario.getNomeCivil(),
        mascararCpf(usuario.getCpfNormalizado()),
        usuario.getDataNascimento() == null ? null : usuario.getDataNascimento().toString(),
        statusPublico(documentos),
        documentos.stream().map(DocumentoUsuarioEntity::getMotivoModeracao)
            .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null),
        documentos.stream().map(DocumentoUsuarioEntity::getCriadoEm).min(Comparator.naturalOrder()).orElse(null),
        documentos.stream().map(DocumentoUsuarioEntity::getRevisadoEm).filter(value -> value != null)
            .max(Comparator.naturalOrder()).orElse(null),
        documentos.stream().sorted(Comparator.comparing(DocumentoUsuarioEntity::getParte))
            .map(documento -> {
              ArquivoMidiaEntity arquivo = arquivos.get(documento.getArquivoMidiaId());
              return new AdminKycDocumentoDto(
                  documento.getId(),
                  documento.getParte().name(),
                  documento.getStatus().name(),
                  arquivo == null ? null : arquivo.getMimeType(),
                  arquivo == null || arquivo.getTamanhoBytes() == null ? 0L : arquivo.getTamanhoBytes());
            }).toList());
  }

  private List<DocumentoUsuarioEntity> documentosDoEnvio(UUID envioId) {
    List<DocumentoUsuarioEntity> documentos = documentoRepository
        .findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(envioId);
    if (documentos.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "envio documental nao encontrado");
    }
    return documentos;
  }

  private String statusPublico(List<DocumentoUsuarioEntity> documentos) {
    if (documentos.stream().allMatch(item -> item.getStatus() == StatusDocumentoUsuario.VALIDADO)) return "APROVADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.AJUSTE_SOLICITADO)) return "AJUSTE_SOLICITADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.REJEITADO)) return "REJEITADO";
    if (documentos.stream().anyMatch(item -> item.getStatus() == StatusDocumentoUsuario.EM_ANALISE)) return "EM_ANALISE";
    return "PENDENTE";
  }

  private void validarLocalPrivado(ArquivoMidiaEntity arquivo) {
    if (!storageProperties.getDocumentBucket().equals(arquivo.getBucket())
        || arquivo.getChaveObjeto() == null
        || !arquivo.getChaveObjeto().startsWith(storageProperties.getDocumentPrefix())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "documento fora do storage privado canonico");
    }
  }

  private ObjectStorage storageObrigatorio() {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null || !storageProperties.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "storage de documentos indisponivel");
    }
    return storage;
  }

  private String mascararCpf(String cpf) {
    return cpf == null || cpf.length() != 11 ? null : "***.***.***-" + cpf.substring(9);
  }
}
