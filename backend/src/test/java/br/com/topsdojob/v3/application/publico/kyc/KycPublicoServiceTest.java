package br.com.topsdojob.v3.application.publico.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class KycPublicoServiceTest {

  private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
  private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
  private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
  private final Authentication authentication = mock(Authentication.class);
  private final List<DocumentoUsuarioEntity> documentos = new ArrayList<>();
  private final Map<UUID, ArquivoMidiaEntity> arquivos = new LinkedHashMap<>();
  private final UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
      UUID.randomUUID(), "Pessoa de teste", "pessoa@example.invalid", null,
      LocalDate.of(1990, 5, 10), OffsetDateTime.now(ZoneOffset.UTC));
  private final KycPublicoService service = new KycPublicoService(
      usuarioService,
      usuarioRepository,
      documentoRepository,
      arquivoRepository,
      auditoriaRepository,
      new DocumentoUploadValidator(new DocumentoUploadProperties()),
      new DocumentoUploadProperties(),
      storageProperties(),
      storageProvider);

  @BeforeEach
  void setUp() {
    when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(storageProvider.getIfAvailable()).thenReturn(storage);
    when(usuarioRepository.findByCpfNormalizado(any())).thenReturn(Optional.empty());
    when(usuarioRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(documentoRepository
        .findByUsuarioIdAndRemovidoEmIsNullAndExpurgadoEmIsNullOrderByCriadoEmDescIdDesc(usuario.getId()))
        .thenAnswer(ignored -> List.copyOf(documentos));
    when(documentoRepository.save(any())).thenAnswer(invocation -> {
      DocumentoUsuarioEntity value = invocation.getArgument(0);
      documentos.add(0, value);
      return value;
    });
    when(arquivoRepository.save(any())).thenAnswer(invocation -> {
      ArquivoMidiaEntity value = invocation.getArgument(0);
      arquivos.put(value.getId(), value);
      return value;
    });
    when(arquivoRepository.findByIdIn(any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      List<UUID> ids = (List<UUID>) invocation.getArgument(0);
      return ids.stream().map(arquivos::get).toList();
    });
  }

  @Test
  void enviaPdfSomenteAoStoragePrivadoSemExporLocalInterno() {
    var response = service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "529.982.247-25",
        "1990-05-10",
        "PDF",
        pdf(),
        null,
        null,
        "req-kyc-1");

    assertThat(response.status()).isEqualTo("PENDENTE");
    assertThat(response.prontoParaEnviarAnuncio()).isTrue();
    assertThat(response.cpfMascarado()).isEqualTo("***.***.***-25");
    assertThat(response.toString())
        .doesNotContain("topsdojob-hml-documentos")
        .doesNotContain("hml/documentos/")
        .doesNotContain("52998224725");
    assertThat(documentos).singleElement();
    ArquivoMidiaEntity arquivo = arquivos.values().iterator().next();
    assertThat(arquivo.getBucket()).isEqualTo("topsdojob-hml-documentos");
    assertThat(arquivo.getChaveObjeto()).startsWith("hml/documentos/usuarios/");
    assertThat(arquivo.getNomeOriginal()).isNull();
    verify(storage).put(eq(StorageArea.PRIVATE_DOCUMENT), eq(arquivo.getChaveObjeto()), any(), eq("application/pdf"));
  }

  @Test
  void recusaCpfDuplicadoEMenorDeIdadeAntesDeGravar() {
    UsuarioEntity outro = UsuarioEntity.criarCadastroPublico(
        UUID.randomUUID(), "Outro", "outro@example.invalid", null,
        LocalDate.of(1985, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));
    outro.aplicarDadosKyc("Outro Civil", "52998224725", LocalDate.of(1985, 1, 1), OffsetDateTime.now(ZoneOffset.UTC));
    when(usuarioRepository.findByCpfNormalizado("52998224725")).thenReturn(Optional.of(outro));

    assertStatus(() -> service.enviar(
        authentication, "Pessoa Civil", "52998224725", "1990-05-10", "PDF",
        pdf(), null, null, "req-duplicate"), HttpStatus.CONFLICT);

    when(usuarioRepository.findByCpfNormalizado("52998224725")).thenReturn(Optional.empty());
    assertStatus(() -> service.enviar(
        authentication, "Pessoa Civil", "52998224725", LocalDate.now(ZoneOffset.UTC).minusYears(17).toString(), "PDF",
        pdf(), null, null, "req-minor"), HttpStatus.BAD_REQUEST);

    verify(storage, never()).put(any(), any(), any(), any());
  }

  @Test
  void exigeKycAntesDoAnuncioEAceitaEnvioPendente() {
    assertStatus(() -> service.garantirProntoParaAnuncio(usuario.getId()), HttpStatus.CONFLICT);

    service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "PDF",
        pdf(),
        null,
        null,
        "req-kyc-gate");

    service.garantirProntoParaAnuncio(usuario.getId());
  }

  @Test
  void enviaFrenteEversoComoPartesDoMesmoEnvioPrivado() {
    MockMultipartFile frente = imagemPng("documentoFrente", "frente.png");
    MockMultipartFile verso = imagemPng("documentoVerso", "verso.png");

    var response = service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "FRENTE_VERSO",
        null,
        frente,
        verso,
        "req-kyc-images");

    assertThat(response.status()).isEqualTo("PENDENTE");
    assertThat(documentos).hasSize(2);
    assertThat(documentos).extracting(DocumentoUsuarioEntity::getParte)
        .containsExactlyInAnyOrder(ParteDocumentoUsuario.FRENTE, ParteDocumentoUsuario.VERSO);
    assertThat(documentos).extracting(DocumentoUsuarioEntity::getEnvioId).doesNotContainNull();
    assertThat(documentos.get(0).getEnvioId()).isEqualTo(documentos.get(1).getEnvioId());
    verify(storage, org.mockito.Mockito.times(2)).put(eq(StorageArea.PRIVATE_DOCUMENT), any(), any(), eq("image/png"));
  }

  @Test
  void frenteSemVersoEhAceitaEOsModosNaoPodemSerMisturados() {
    MockMultipartFile frente = imagemPng("documentoFrente", "frente.png");

    service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "FRENTE_VERSO",
        null,
        frente,
        null,
        "req-kyc-front-only");

    assertThat(documentos).singleElement()
        .satisfies(item -> assertThat(item.getParte()).isEqualTo(ParteDocumentoUsuario.FRENTE));
  }

  @Test
  void pdfImagemEModoAusenteRetornamMensagensEspecificasSemGravar() {
    assertReason(() -> service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "PDF",
        imagemPng("documentoUnico", "identidade.png"),
        null,
        null,
        "req-kyc-wrong-pdf"), "O arquivo deve estar em PDF.");

    assertReason(() -> service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "FRENTE_VERSO",
        null,
        null,
        null,
        "req-kyc-no-front"), "Selecione a imagem da frente do documento.");

    assertReason(() -> service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "",
        null,
        null,
        null,
        "req-kyc-no-mode"), "Preencha os dados obrigatórios.");

    verify(storage, never()).put(any(), any(), any(), any());
  }

  @Test
  void permiteReenvioAposRejeicaoSemAlterarEnvioAnterior() {
    service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "PDF",
        pdf(),
        null,
        null,
        "req-kyc-original");
    DocumentoUsuarioEntity original = documentos.get(0);
    original.aplicarDecisao(
        StatusDocumentoUsuario.REJEITADO,
        UUID.randomUUID(),
        "Documento ilegivel",
        OffsetDateTime.now(ZoneOffset.UTC));

    var reenviado = service.enviar(
        authentication,
        "",
        "",
        "",
        "PDF",
        pdf(),
        null,
        null,
        "req-kyc-retry");

    assertThat(reenviado.status()).isEqualTo("PENDENTE");
    assertThat(documentos).hasSize(2);
    assertThat(documentos.get(0).getEnvioId()).isNotEqualTo(original.getEnvioId());
    assertThat(original.getStatus()).isEqualTo(StatusDocumentoUsuario.REJEITADO);
  }

  @Test
  void preservaDadosAprovadosEBloqueiaNovoEnvio() {
    service.enviar(
        authentication,
        "Pessoa Civil de Teste",
        "52998224725",
        "1990-05-10",
        "PDF",
        pdf(),
        null,
        null,
        "req-kyc-approved");
    documentos.get(0).aplicarDecisao(
        StatusDocumentoUsuario.VALIDADO,
        UUID.randomUUID(),
        null,
        OffsetDateTime.now(ZoneOffset.UTC));

    var aprovado = service.consultar(authentication);

    assertThat(aprovado.status()).isEqualTo("APROVADO");
    assertThat(aprovado.nomeCivil()).isEqualTo("Pessoa Civil de Teste");
    assertThat(aprovado.dataNascimento()).isEqualTo("1990-05-10");
    assertThatThrownBy(() -> service.enviar(
        authentication, "", "", "", "PDF", pdf(), null, null, "req-kyc-forbidden"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  private void assertStatus(Runnable operation, HttpStatus expected) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(expected));
  }

  private void assertReason(Runnable operation, String reason) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getReason()).isEqualTo(reason));
  }

  private MockMultipartFile pdf() {
    return new MockMultipartFile(
        "documentoUnico", "identidade.pdf", "application/pdf",
        "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes());
  }

  private MockMultipartFile imagemPng(String fieldName, String fileName) {
    byte[] png = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    return new MockMultipartFile(fieldName, fileName, "image/png", png);
  }

  private R2StorageProperties storageProperties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setDocumentBucket("topsdojob-hml-documentos");
    properties.setDocumentPrefix("hml/documentos/");
    return properties;
  }
}
