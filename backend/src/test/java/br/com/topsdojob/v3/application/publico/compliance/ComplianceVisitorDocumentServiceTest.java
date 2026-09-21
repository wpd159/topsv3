package br.com.topsdojob.v3.application.publico.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceVisitorSessionService.SessionContext;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator.DocumentoValidado;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusDocumentoVisitante;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorDocumentoEntity;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorDocumentoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

class ComplianceVisitorDocumentServiceTest {

  private static final byte[] PDF =
      "%PDF-1.4\nSEM VALIDADE\n%%EOF".getBytes(StandardCharsets.US_ASCII);

  @Test
  void documentoPrivadoEIdempotenteSemCriarSegundoObjetoOuRegistro() {
    Fixture fixture = fixture(validado(PDF));
    MockMultipartFile file = arquivo(PDF);

    var first = fixture.service.submeter(
        fixture.challenge.getId(),
        "document-idempotency",
        file,
        new MockHttpServletRequest());
    var second = fixture.service.submeter(
        fixture.challenge.getId(),
        "document-idempotency",
        file,
        new MockHttpServletRequest());

    assertThat(first.response().submissionId())
        .isEqualTo(second.response().submissionId());
    assertThat(first.response().state()).isEqualTo("DOCUMENT_PENDING");
    assertThat(fixture.persisted.get().getChaveObjeto())
        .startsWith("hml/preprod/documentos/compliance/visitor/");
    assertThat(fixture.persisted.get().getBucket())
        .isEqualTo("topsdojob-hml-documentos");
    assertThat(fixture.challenge.getStatus())
        .isEqualTo(StatusChallengeVisitante.DOCUMENT_PENDING);
    verify(fixture.storage, times(1)).putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        eq(PDF),
        eq("application/pdf"));
    verify(fixture.documentoRepository, times(1)).save(any());
  }

  @Test
  void mesmaIdempotenciaComOutroDocumentoRetornaConflito() {
    DocumentoValidado first = validado(PDF);
    DocumentoValidado different = validado(
        "%PDF-1.4\nOUTRO\n%%EOF".getBytes(StandardCharsets.US_ASCII));
    Fixture fixture = fixture(first);
    when(fixture.validator.validar(any()))
        .thenReturn(first)
        .thenReturn(different);

    fixture.service.submeter(
        fixture.challenge.getId(),
        "document-idempotency",
        arquivo(PDF),
        new MockHttpServletRequest());

    assertThatThrownBy(() -> fixture.service.submeter(
        fixture.challenge.getId(),
        "document-idempotency",
        arquivo(different.bytes()),
        new MockHttpServletRequest()))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(fixture.storage, times(1)).putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        any(),
        eq("application/pdf"));
  }

  @Test
  void retryDeDocumentoJaDecididoPreservaOEstadoPublicoReal() {
    Fixture fixture = fixture(validado(PDF));
    MockMultipartFile file = arquivo(PDF);
    fixture.service.submeter(
        fixture.challenge.getId(),
        "document-idempotency",
        file,
        new MockHttpServletRequest());
    fixture.persisted.get().decidir(
        StatusDocumentoVisitante.REJECTED,
        UUID.randomUUID(),
        "Imagem sem nitidez suficiente.",
        OffsetDateTime.now(ZoneOffset.UTC));

    var retry = fixture.service.submeter(
        fixture.challenge.getId(),
        "document-idempotency",
        file,
        new MockHttpServletRequest());

    assertThat(retry.response().state()).isEqualTo("DOCUMENT_REJECTED");
    assertThat(retry.response().status()).isEqualTo("REJECTED");
    assertThat(retry.response().reasonPublic())
        .isEqualTo("Imagem sem nitidez suficiente.");
    verify(fixture.storage, times(1)).putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        any(),
        eq("application/pdf"));
  }

  @Test
  void novaChaveNaoDuplicaDocumentoAindaPendenteMasReenvioRejeitadoContinuaDisponivel() {
    var fixture = fixture(validado(PDF));
    fixture.service.submeter(fixture.challenge.getId(), "first-key", arquivo(PDF), new MockHttpServletRequest());
    when(fixture.documentoRepository.findBySessionHashAndIdempotenciaHash(any(), any())).thenReturn(Optional.empty());
    when(fixture.documentoRepository.findTopByChallengeIdOrderByCriadoEmDesc(any()))
        .thenAnswer(invocation -> Optional.of(fixture.persisted.get()));
    assertThatThrownBy(() -> fixture.service.submeter(fixture.challenge.getId(), "new-key", arquivo(PDF), new MockHttpServletRequest()))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(fixture.documentoRepository, times(1)).save(any());
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    fixture.persisted.get().decidir(StatusDocumentoVisitante.REJECTED, UUID.randomUUID(), "Ilegivel", now);
    fixture.challenge.marcarDocumentoRejeitado("Ilegivel", now);
    assertThat(fixture.service.submeter(fixture.challenge.getId(), "new-key", arquivo(PDF), new MockHttpServletRequest())
        .response().state()).isEqualTo("DOCUMENT_PENDING");
    verify(fixture.documentoRepository, times(2)).save(any());
  }

  @Test
  void bloqueioAtualEExpiracaoRecusamUploadAntesDeGravarObjeto() {
    for (boolean blocked : new boolean[] {false, true}) {
      var fixture = fixture(validado(PDF));
      if (blocked) when(fixture.riskService.bloqueadaEm(any(), any())).thenReturn(true);
      else ReflectionTestUtils.setField(fixture.challenge, "expiraEm", OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
      assertThatThrownBy(() -> fixture.service.submeter(fixture.challenge.getId(), "new-key", arquivo(PDF), new MockHttpServletRequest()))
          .isInstanceOfSatisfying(ResponseStatusException.class,
              exception -> assertThat(exception.getStatusCode()).isEqualTo(blocked ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.GONE));
      verify(fixture.documentoRepository, never()).save(any());
      verify(fixture.storage, never()).putIfAbsent(any(), any(), any(), any());
    }
  }

  @SuppressWarnings("unchecked")
  private Fixture fixture(DocumentoValidado validado) {
    ComplianceVisitorChallengeRepository challengeRepository =
        mock(ComplianceVisitorChallengeRepository.class);
    ComplianceVisitorDocumentoRepository documentoRepository =
        mock(ComplianceVisitorDocumentoRepository.class);
    ComplianceVisitorSessionService sessionService =
        mock(ComplianceVisitorSessionService.class);
    ComplianceVisitorAuditService auditService =
        mock(ComplianceVisitorAuditService.class);
    ComplianceVisitorRiskService riskService = mock(ComplianceVisitorRiskService.class);
    DocumentoUploadValidator validator = mock(DocumentoUploadValidator.class);
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    AtomicReference<ComplianceVisitorDocumentoEntity> persisted =
        new AtomicReference<>();
    ComplianceVisitorChallengeEntity challenge = documentPendingChallenge();
    SessionContext session = new SessionContext(
        UUID.randomUUID(),
        challenge.getSessionHash(),
        "2".repeat(64),
        ResponseCookie.from("visitor_session_id", "session").build(),
        false);
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setDocumentBucket("topsdojob-hml-documentos");
    properties.setDocumentPrefix("hml/preprod/documentos/");

    when(sessionService.obterOuCriar(any())).thenReturn(session);
    when(challengeRepository.findByIdForUpdate(challenge.getId()))
        .thenReturn(Optional.of(challenge));
    when(documentoRepository.findBySessionHashAndIdempotenciaHash(any(), any()))
        .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
    when(documentoRepository.save(any())).thenAnswer(invocation -> {
      ComplianceVisitorDocumentoEntity entity = invocation.getArgument(0);
      persisted.set(entity);
      return entity;
    });
    when(validator.validar(any())).thenReturn(validado);
    when(provider.getIfAvailable()).thenReturn(storage);
    when(storage.putIfAbsent(
        eq(StorageArea.PRIVATE_DOCUMENT),
        any(),
        any(),
        eq("application/pdf"))).thenReturn(ObjectWriteResult.CREATED);
    when(storage.get(eq(StorageArea.PRIVATE_DOCUMENT), any()))
        .thenReturn(new StoredObject(validado.bytes(), validado.mimeType()));

    ComplianceVisitorDocumentService service = new ComplianceVisitorDocumentService(
        challengeRepository,
        documentoRepository,
        sessionService,
        riskService,
        auditService,
        validator,
        new MetricaPublicaHashService(
            "document-test-hash-value",
            "homologacao"),
        provider,
        properties);
    return new Fixture(
        service,
        challenge,
        documentoRepository,
        validator,
        storage,
        riskService,
        persisted);
  }

  private ComplianceVisitorChallengeEntity documentPendingChallenge() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    ComplianceVisitorChallengeEntity challenge =
        ComplianceVisitorChallengeEntity.criar(
            UUID.randomUUID(),
            "1".repeat(64),
            NivelAcessoVisitante.STRONG,
            NivelAcessoVisitante.STRONG,
            EscopoConteudoVisitante.CONTEUDO_EXPLICITO,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "/anuncios/qa",
            100,
            DecisaoRiscoVisitante.REVIEW_FLAG,
            "RISCO_REVISAO_DOCUMENTAL",
            true,
            true,
            "3".repeat(64),
            2,
            now.plusDays(1),
            now);
    challenge.marcarDocumentoPendente("4".repeat(64), now);
    return challenge;
  }

  private DocumentoValidado validado(byte[] bytes) {
    return new DocumentoValidado(
        bytes,
        "application/pdf",
        "pdf",
        null,
        null,
        sha256(bytes));
  }

  private String sha256(byte[] bytes) {
    try {
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private MockMultipartFile arquivo(byte[] bytes) {
    return new MockMultipartFile(
        "document",
        "sem-validade.pdf",
        "application/pdf",
        bytes);
  }

  private record Fixture(
      ComplianceVisitorDocumentService service,
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorDocumentoRepository documentoRepository,
      DocumentoUploadValidator validator,
      ObjectStorage storage,
      ComplianceVisitorRiskService riskService,
      AtomicReference<ComplianceVisitorDocumentoEntity> persisted) {
  }
}
