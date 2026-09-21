package br.com.topsdojob.v3.application.admin.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceDocumentoDecisaoRequestDto;
import br.com.topsdojob.v3.application.publico.compliance.ComplianceAgeGateProperties;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.DecisaoRiscoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.NivelAcessoVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusChallengeVisitante;
import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.StatusDocumentoVisitante;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorChallengeEntity;
import br.com.topsdojob.v3.persistence.entity.compliance.ComplianceVisitorDocumentoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorChallengeRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorDocumentoRepository;
import br.com.topsdojob.v3.persistence.repository.ComplianceVisitorRiskProfileRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminComplianceVisitorServiceTest {

  @Test
  void leituraPrivadaRegistraAtorReferenciaEEtapasSemAfirmarVisualizacao() {
    Fixture fixture = fixture();
    when(fixture.storage.get(StorageArea.PRIVATE_DOCUMENT, fixture.documento.getChaveObjeto()))
        .thenReturn(new StoredObject(new byte[] {1, 2, 3}, "application/pdf"));

    var result = fixture.service.carregarDocumento(fixture.documento.getId(), fixture.admin, "leitura-permitida");

    assertThat(result.bytes()).containsExactly(1, 2, 3);
    assertThat(fixture.trilha).extracting(AuditoriaEventoEntity::getDepoisJson).containsExactly(
        etapa("TENTATIVA"), etapa("AUTORIZADO"), etapa("BYTES_PREPARADOS"));
    assertThat(fixture.trilha).allSatisfy(evento -> {
      assertThat(evento.getAtorUsuarioId()).isEqualTo(fixture.admin.usuarioId());
      assertThat(evento.getRecursoId()).isEqualTo(fixture.documento.getId());
      assertThat(evento.getRequestId()).isEqualTo("leitura-permitida");
      assertThat(evento.getCriadoEm()).isNotNull();
      assertThat(evento.getDepoisJson()).doesNotContain("qa.pdf", "bucket-documental", "VISUALIZADO", "http");
    });
  }

  @Test
  void documentoInexistenteOuForaDaAreaCanonicaNaoLeStorageERegistraNegativa() {
    Fixture fixture = fixture();
    fixture.storageProperties.setDocumentBucket("outro-bucket");

    assertThatThrownBy(() -> fixture.service.carregarDocumento(
        fixture.documento.getId(), fixture.admin, "leitura-negada"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    assertThat(fixture.trilha).extracting(AuditoriaEventoEntity::getDepoisJson)
        .containsExactly(etapa("TENTATIVA"), etapa("NEGADO"));
    verifyNoInteractions(fixture.storage);
  }

  @Test
  void falhaDoStorageMantemRespostaSanitizadaESemEventoDeBytes() {
    Fixture fixture = fixture();
    when(fixture.storage.get(StorageArea.PRIVATE_DOCUMENT, fixture.documento.getChaveObjeto()))
        .thenThrow(new IllegalStateException("storage interno https://private.invalid/segredo"));

    assertThatThrownBy(() -> fixture.service.carregarDocumento(
        fixture.documento.getId(), fixture.admin, "leitura-falhou"))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
          assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(exception.getReason()).isEqualTo("documento de visitante nao encontrado");
        });

    assertThat(fixture.trilha).extracting(AuditoriaEventoEntity::getDepoisJson)
        .containsExactly(etapa("TENTATIVA"), etapa("AUTORIZADO"), etapa("FALHA"));
  }

  @Test
  void ausenciaDeAtorRecusaLeituraSemConsultarDocumentoOuStorage() {
    Fixture fixture = fixture();
    assertThatThrownBy(() -> fixture.service.carregarDocumento(
        fixture.documento.getId(), null, "sem-ator"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    assertThat(fixture.trilha).extracting(AuditoriaEventoEntity::getDepoisJson)
        .containsExactly(etapa("TENTATIVA"), etapa("NEGADO"));
    verifyNoInteractions(fixture.storage);
  }

  private String etapa(String etapa) {
    return "{\"etapa\":\"" + etapa + "\",\"dadosPrivadosOcultos\":true}";
  }

  @Test
  void aprovarDocumentoMudaSomenteEstadoParaRetomadaSemEmitirToken() {
    Fixture fixture = fixture();
    OffsetDateTime expiracaoAnterior = fixture.challenge.getExpiraEm();

    var result = fixture.service.decidir(
        fixture.documento.getId(),
        new AdminComplianceDocumentoDecisaoRequestDto("APPROVE", null),
        fixture.admin,
        "request-approve");

    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(fixture.documento.getStatus())
        .isEqualTo(StatusDocumentoVisitante.APPROVED);
    assertThat(fixture.challenge.getStatus())
        .isEqualTo(StatusChallengeVisitante.DOCUMENT_APPROVED);
    assertThat(fixture.challenge.getExpiraEm())
        .isAfterOrEqualTo(expiracaoAnterior)
        .isAfter(OffsetDateTime.now(ZoneOffset.UTC));
    assertThat(fixture.audit.get().getDepoisJson())
        .contains("\"tokenEmitido\":false")
        .doesNotContain("session_hash")
        .doesNotContain("chave_objeto");
  }

  @Test
  void rejeicaoSanitizaMotivoPublicoERecusaDecisaoRepetida() {
    Fixture fixture = fixture();
    String sensitiveReason =
        "Imagem ilegivel 123.456.789-09; contato qa@example.invalid";

    fixture.service.decidir(
        fixture.documento.getId(),
        new AdminComplianceDocumentoDecisaoRequestDto(
            "REJECT",
            sensitiveReason),
        fixture.admin,
        "request-reject");

    assertThat(fixture.documento.getMotivoPublicoSanitizado())
        .isEqualTo("Imagem ilegivel [dado omitido]; contato [dado omitido]")
        .doesNotContain("123.456")
        .doesNotContain("@");
    assertThat(fixture.challenge.getStatus())
        .isEqualTo(StatusChallengeVisitante.DOCUMENT_REJECTED);
    assertThat(fixture.audit.get().getDepoisJson())
        .doesNotContain("123.456")
        .doesNotContain("@");

    assertThatThrownBy(() -> fixture.service.decidir(
        fixture.documento.getId(),
        new AdminComplianceDocumentoDecisaoRequestDto(
            "REJECT",
            "Nova tentativa"),
        fixture.admin,
        "request-repeat"))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @SuppressWarnings("unchecked")
  private Fixture fixture() {
    ComplianceVisitorDocumentoRepository documentoRepository =
        mock(ComplianceVisitorDocumentoRepository.class);
    ComplianceVisitorChallengeRepository challengeRepository =
        mock(ComplianceVisitorChallengeRepository.class);
    ComplianceVisitorRiskProfileRepository riskRepository =
        mock(ComplianceVisitorRiskProfileRepository.class);
    AuditoriaEventoRepository auditoriaRepository =
        mock(AuditoriaEventoRepository.class);
    ObjectProvider<ObjectStorage> storageProvider = mock(ObjectProvider.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    when(storageProvider.getIfAvailable()).thenReturn(storage);
    R2StorageProperties storageProperties = new R2StorageProperties();
    storageProperties.setDocumentBucket("bucket-documental");
    storageProperties.setDocumentPrefix("hml/preprod/documentos/");
    ComplianceAgeGateProperties ageGateProperties = new ComplianceAgeGateProperties();
    ageGateProperties.setChallengeTtlMinutes(60);
    AtomicReference<AuditoriaEventoEntity> audit = new AtomicReference<>();
    List<AuditoriaEventoEntity> trilha = new ArrayList<>();
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
            "2".repeat(64),
            2,
            now.plusMinutes(10),
            now);
    challenge.marcarDocumentoPendente("3".repeat(64), now);
    ComplianceVisitorDocumentoEntity documento =
        ComplianceVisitorDocumentoEntity.criarPendente(
            UUID.randomUUID(),
            challenge.getSessionHash(),
            challenge.getId(),
            challenge.getAnuncioId(),
            "bucket-documental",
            "hml/preprod/documentos/compliance/visitor/qa.pdf",
            "application/pdf",
            100,
            "4".repeat(64),
            "5".repeat(64),
            now);
    when(documentoRepository.findByIdForUpdate(documento.getId()))
        .thenReturn(Optional.of(documento));
    when(documentoRepository.findById(documento.getId())).thenReturn(Optional.of(documento));
    when(challengeRepository.findByIdForUpdate(challenge.getId()))
        .thenReturn(Optional.of(challenge));
    when(auditoriaRepository.save(any(AuditoriaEventoEntity.class)))
        .thenAnswer(invocation -> {
          AuditoriaEventoEntity entity = invocation.getArgument(0);
          audit.set(entity);
          trilha.add(entity);
          return entity;
        });
    AdminComplianceVisitorService service = new AdminComplianceVisitorService(
        documentoRepository,
        challengeRepository,
        riskRepository,
        auditoriaRepository,
        storageProvider,
        storageProperties,
        ageGateProperties,
        new AdminComplianceDocumentoAuditService(auditoriaRepository));
    AdminUserPrincipal admin = new AdminUserPrincipal(
        UUID.randomUUID(),
        "Admin QA",
        "admin@example.invalid",
        "n/a",
        List.of(PapelUsuario.ADMIN),
        List.of(),
        List.of(),
        true);
    return new Fixture(service, challenge, documento, admin, audit, storage, storageProperties, trilha);
  }

  private record Fixture(
      AdminComplianceVisitorService service,
      ComplianceVisitorChallengeEntity challenge,
      ComplianceVisitorDocumentoEntity documento,
      AdminUserPrincipal admin,
      AtomicReference<AuditoriaEventoEntity> audit,
      ObjectStorage storage,
      R2StorageProperties storageProperties,
      List<AuditoriaEventoEntity> trilha) {
  }
}
