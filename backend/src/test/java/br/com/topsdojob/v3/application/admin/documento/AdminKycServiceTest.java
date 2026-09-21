package br.com.topsdojob.v3.application.admin.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator.DocumentoValidado;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioAcessoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ParteDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AdminKycServiceTest {

  private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
  private final DocumentoUsuarioAcessoRepository acessoRepository = mock(DocumentoUsuarioAcessoRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final AdminKycThumbnailProcessor thumbnailProcessor = mock(AdminKycThumbnailProcessor.class);
  private final DocumentoUploadValidator uploadValidator = mock(DocumentoUploadValidator.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
  private final UUID envioId = UUID.randomUUID();
  private final UUID usuarioId = UUID.randomUUID();
  private final UUID arquivoId = UUID.randomUUID();
  private final DocumentoUsuarioEntity documento = DocumentoUsuarioEntity.criarPendente(
      UUID.randomUUID(), usuarioId, arquivoId, envioId, ParteDocumentoUsuario.UNICO,
      OffsetDateTime.now(ZoneOffset.UTC));
  private final ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarUploadPendente(
      arquivoId,
      "R2",
      "topsdojob-hml-documentos",
      "hml/documentos/usuarios/teste/envios/teste/unico.pdf",
      null,
      "application/pdf",
      1200,
      null,
      null,
      null,
      "a".repeat(64),
      OffsetDateTime.now(ZoneOffset.UTC));
  private final UsuarioEntity usuario = usuario();
  private final AdminUserPrincipal admin = new AdminUserPrincipal(
      UUID.randomUUID(), "Admin HML", "admin@example.invalid", "n/a",
      List.of(PapelUsuario.ADMIN), List.of(), List.of(), true);
  private final AdminKycService service = new AdminKycService(
      documentoRepository,
      acessoRepository,
      arquivoRepository,
      usuarioRepository,
      auditoriaRepository,
      properties(),
      storageProvider,
      thumbnailProcessor,
      uploadValidator);

  @BeforeEach
  void setUp() {
    when(documentoRepository.findById(documento.getId())).thenReturn(Optional.of(documento));
    when(documentoRepository.findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(envioId))
        .thenReturn(List.of(documento));
    when(documentoRepository.findEnvioIdAtivoByDocumentoId(documento.getId())).thenReturn(Optional.of(envioId));
    when(documentoRepository.findAtivosDoEnvioForUpdate(envioId)).thenReturn(List.of(documento));
    when(documentoRepository.findByStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmAsc(any()))
        .thenReturn(List.of(documento));
    when(arquivoRepository.findById(arquivoId)).thenReturn(Optional.of(arquivo));
    when(arquivoRepository.findByIdIn(any())).thenReturn(List.of(arquivo));
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
    when(storageProvider.getIfAvailable()).thenReturn(storage);
    when(storage.temporaryGetUrl(StorageArea.PRIVATE_DOCUMENT, arquivo.getChaveObjeto(), java.time.Duration.ofMinutes(5)))
        .thenReturn(URI.create("https://private.invalid/temporary-document"));
  }

  @Test
  void geraUrlPrivadaCurtaEMarcaAcessoAuditavel() {
    var response = service.urlTemporaria(documento.getId(), admin, "req-view");

    assertThat(response.url()).startsWith("https://private.invalid/");
    assertThat(response.expiraEm()).isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    assertThat(documento.getStatus()).isEqualTo(StatusDocumentoUsuario.EM_ANALISE);
    verify(acessoRepository).save(any());
  }

  @Test
  void geraMiniaturaPeloMesmoStoragePrivadoSemExporUrlAssinada() {
    byte[] source = {1, 2, 3};
    byte[] rendered = {4, 5, 6};
    when(storage.get(StorageArea.PRIVATE_DOCUMENT, arquivo.getChaveObjeto()))
        .thenReturn(new StoredObject(source, "application/pdf"));
    when(thumbnailProcessor.processar(
        eq(arquivoId),
        eq(arquivo.getSha256()),
        eq("application/pdf"),
        any()))
        .thenAnswer(invocation -> {
          @SuppressWarnings("unchecked")
          Supplier<byte[]> loader = invocation.getArgument(3, Supplier.class);
          assertThat(loader.get()).containsExactly(source);
          return new AdminKycThumbnailProcessor.Thumbnail(rendered, "image/jpeg", "\"etag\"");
        });

    var response = service.miniatura(documento.getId(), admin, "req-thumb");

    assertThat(response.content()).containsExactly(rendered);
    assertThat(response.contentType()).isEqualTo("image/jpeg");
    verify(storage).get(StorageArea.PRIVATE_DOCUMENT, arquivo.getChaveObjeto());
    verify(acessoRepository).save(any());
  }

  @Test
  void exigeMotivoParaRejeicaoEAprovaSemPublicarDocumento() {
    assertThatThrownBy(() -> service.decidir(
        envioId,
        new AdminKycDecisaoRequestDto(AdminDecisaoModeracaoAcao.REPROVAR, "x"),
        admin,
        "req-reject"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

    var response = service.decidir(
        envioId,
        new AdminKycDecisaoRequestDto(AdminDecisaoModeracaoAcao.APROVAR, null),
        admin,
        "req-approve");

    assertThat(response.status()).isEqualTo("APROVADO");
    assertThat(documento.getStatus()).isEqualTo(StatusDocumentoUsuario.VALIDADO);
    assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.VALIDADO);
    assertThat(arquivo.getBucket()).isEqualTo("topsdojob-hml-documentos");
    assertThat(arquivo.getChaveObjeto()).startsWith("hml/documentos/");
    verify(auditoriaRepository).save(any());
  }

  @Test
  void retryDaDecisaoKycNaoDuplicaAuditoria() {
    var request = new AdminKycDecisaoRequestDto(
        AdminDecisaoModeracaoAcao.APROVAR,
        null);

    service.decidir(
        envioId,
        request,
        admin,
        "req-approve-retry");

    assertThatThrownBy(() -> service.decidir(
        envioId,
        request,
        admin,
        "req-approve-retry"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(auditoriaRepository).save(any());
  }
  @Test
  void rejeitaComMotivoObrigatorio() {
    var rejeitado = service.decidir(
        envioId,
        new AdminKycDecisaoRequestDto(AdminDecisaoModeracaoAcao.REPROVAR, "Documento ilegivel"),
        admin,
        "req-reject-valid");

    assertThat(rejeitado.status()).isEqualTo("REJEITADO");
    assertThat(documento.getStatus()).isEqualTo(StatusDocumentoUsuario.REJEITADO);
    assertThat(documento.getMotivoModeracao()).isEqualTo("Documento ilegivel");
    assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.REJEITADO);
  }

  @Test
  void solicitaAjusteMantendoDocumentoPrivado() {
    var ajuste = service.decidir(
        envioId,
        new AdminKycDecisaoRequestDto(
            AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE,
            "Envie uma imagem mais nitida"),
        admin,
        "req-adjustment");

    assertThat(ajuste.status()).isEqualTo("AJUSTE_SOLICITADO");
    assertThat(documento.getStatus()).isEqualTo(StatusDocumentoUsuario.AJUSTE_SOLICITADO);
    assertThat(arquivo.getStatusArquivo()).isEqualTo(StatusArquivoMidia.PENDENTE);
    assertThat(arquivo.getBucket()).isEqualTo("topsdojob-hml-documentos");
    assertThat(arquivo.getChaveObjeto()).startsWith("hml/documentos/");
  }

  @Test
  void adminAdicionaImagemPrivadaPendenteEORetryNaoDuplica() {
    byte[] bytes = {10, 20, 30, 40};
    var upload = new MockMultipartFile("documentoUnico", "qa.png", "image/png", bytes);
    var validado = new DocumentoValidado(
        bytes,
        "image/png",
        "png",
        20,
        10,
        "5f53c0ff07ba5d9a330e68c95dabb1a9bc49e29f9ed53f6fa7c6d99abb000050");
    List<ArquivoMidiaEntity> arquivosSalvos = new ArrayList<>();
    List<DocumentoUsuarioEntity> documentosSalvos = new ArrayList<>();
    when(uploadValidator.validar(upload)).thenReturn(validado);
    when(storage.putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        eq(bytes),
        eq("image/png"))).thenReturn(ObjectWriteResult.CREATED);
    when(storage.get(eq(StorageArea.PRIVATE_DOCUMENT), any()))
        .thenReturn(new StoredObject(bytes, "image/png"));
    when(arquivoRepository.save(any())).thenAnswer(invocation -> {
      ArquivoMidiaEntity salvo = invocation.getArgument(0);
      arquivosSalvos.add(salvo);
      return salvo;
    });
    when(documentoRepository.save(any())).thenAnswer(invocation -> {
      DocumentoUsuarioEntity salvo = invocation.getArgument(0);
      documentosSalvos.add(salvo);
      return salvo;
    });
    when(arquivoRepository.findByIdIn(any())).thenAnswer(invocation -> List.copyOf(arquivosSalvos));
    when(documentoRepository
        .findByEnvioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByParteAsc(any()))
        .thenAnswer(invocation -> {
          UUID consulta = invocation.getArgument(0);
          if (consulta.equals(envioId)) return List.of(documento);
          return documentosSalvos.stream()
              .filter(item -> consulta.equals(item.getEnvioId()) && item.getRemovidoEm() == null)
              .toList();
        });

    var primeiro = service.enviarAdministrativamente(
        usuarioId,
        "IDENTIDADE",
        "UNICO",
        null,
        upload,
        null,
        null,
        "qa-image-once",
        admin,
        "req-upload");
    var repetido = service.enviarAdministrativamente(
        usuarioId,
        "IDENTIDADE",
        "UNICO",
        null,
        upload,
        null,
        null,
        "qa-image-once",
        admin,
        "req-upload-retry");

    assertThat(primeiro.envioId()).isEqualTo(repetido.envioId());
    assertThat(primeiro.status()).isEqualTo("PENDENTE");
    assertThat(documentosSalvos).hasSize(1);
    assertThat(arquivosSalvos).hasSize(1);
    assertThat(arquivosSalvos.get(0).getBucket()).isEqualTo("topsdojob-hml-documentos");
    assertThat(arquivosSalvos.get(0).getChaveObjeto()).startsWith("hml/documentos/usuarios/");
    verify(storage, times(1)).putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        eq(bytes),
        eq("image/png"));
  }

  @Test
  void substituicaoSoInativaDocumentoAnteriorDepoisDeConfirmarNovoObjeto() {
    byte[] bytes = {1, 3, 5, 7};
    var upload = new MockMultipartFile("documentoUnico", "qa.pdf", "application/pdf", bytes);
    var validado = new DocumentoValidado(
        bytes,
        "application/pdf",
        "pdf",
        null,
        null,
        "e6e8cb429864b8e8d7fe95e53360dbd00756316813fb1e4a03a778d4632dbde6");
    List<ArquivoMidiaEntity> arquivosSalvos = new ArrayList<>();
    when(uploadValidator.validar(upload)).thenReturn(validado);
    when(storage.putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        eq(bytes),
        eq("application/pdf"))).thenReturn(ObjectWriteResult.CREATED);
    when(storage.get(eq(StorageArea.PRIVATE_DOCUMENT), any()))
        .thenReturn(new StoredObject(bytes, "application/pdf"));
    when(arquivoRepository.save(any())).thenAnswer(invocation -> {
      ArquivoMidiaEntity salvo = invocation.getArgument(0);
      arquivosSalvos.add(salvo);
      return salvo;
    });
    when(documentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(arquivoRepository.findByIdIn(any())).thenAnswer(invocation -> List.copyOf(arquivosSalvos));

    var novo = service.enviarAdministrativamente(
        usuarioId,
        "IDENTIDADE",
        "UNICO",
        envioId,
        upload,
        null,
        null,
        "qa-replacement",
        admin,
        "req-replace");

    assertThat(novo.status()).isEqualTo("PENDENTE");
    assertThat(documento.getRemovidoEm()).isNotNull();
    assertThat(documento.getStatus()).isEqualTo(StatusDocumentoUsuario.REMOVIDO);
    verify(documentoRepository).saveAll(List.of(documento));
    verify(storage, never()).delete(StorageArea.PRIVATE_DOCUMENT, arquivo.getChaveObjeto());
  }

  @Test
  void falhaNaConfirmacaoDoStoragePreservaDocumentoAnteriorEPermiteRetry() {
    byte[] bytes = {9, 8, 7, 6};
    var upload = new MockMultipartFile("documentoUnico", "qa.png", "image/png", bytes);
    var validado = new DocumentoValidado(
        bytes,
        "image/png",
        "png",
        10,
        10,
        "63d987d1c6d69751e48e4b5a8f16d1e20ff80c151b33a05dd68f01e34b7f5a63");
    when(uploadValidator.validar(upload)).thenReturn(validado);
    when(storage.putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        eq(bytes),
        eq("image/png"))).thenReturn(ObjectWriteResult.CREATED);
    when(storage.get(eq(StorageArea.PRIVATE_DOCUMENT), any()))
        .thenReturn(new StoredObject(new byte[] {1}, "image/png"));

    assertThatThrownBy(() -> service.enviarAdministrativamente(
        usuarioId,
        "IDENTIDADE",
        "UNICO",
        envioId,
        upload,
        null,
        null,
        "qa-storage-failure",
        admin,
        "req-storage-failure"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

    assertThat(documento.getRemovidoEm()).isNull();
    assertThat(documento.getStatus()).isEqualTo(StatusDocumentoUsuario.PENDENTE);
    verify(documentoRepository, never()).saveAll(any());
    verify(storage).delete(eq(StorageArea.PRIVATE_DOCUMENT), any());
  }

  private UsuarioEntity usuario() {
    UsuarioEntity value = UsuarioEntity.criarCadastroPublico(
        usuarioId,
        "Pessoa teste",
        "pessoa@example.invalid",
        null,
        LocalDate.of(1990, 5, 10),
        OffsetDateTime.now(ZoneOffset.UTC));
    value.aplicarDadosKyc(
        "Pessoa Civil Teste",
        "52998224725",
        LocalDate.of(1990, 5, 10),
        OffsetDateTime.now(ZoneOffset.UTC));
    return value;
  }

  private R2StorageProperties properties() {
    R2StorageProperties value = new R2StorageProperties();
    value.setEnabled(true);
    value.setDocumentBucket("topsdojob-hml-documentos");
    value.setDocumentPrefix("hml/documentos/");
    return value;
  }
}
