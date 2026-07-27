package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.domain.metrica.MetricaTipos.MetodoVerificacaoEtaria;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ComplianceVisitorAuditService {

  private static final Pattern EMAIL = Pattern.compile(
      "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
  private static final Pattern CPF = Pattern.compile(
      "\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
  private static final Pattern BIRTH_DATE = Pattern.compile(
      "\\b\\d{2}/\\d{2}/\\d{4}\\b");
  private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{8,}\\b");

  private final EventoVerificacaoEtariaRepository repository;
  private final br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService hashService;

  public ComplianceVisitorAuditService(
      EventoVerificacaoEtariaRepository repository,
      br.com.topsdojob.v3.application.publico.service.MetricaPublicaHashService hashService) {
    this.repository = repository;
    this.hashService = hashService;
  }

  public void registrar(
      ResultadoVerificacaoEtaria resultado,
      MetodoVerificacaoEtaria metodo,
      UUID anuncioId,
      UUID challengeId,
      String sessionHash,
      String estado,
      String escopo,
      String motivo,
      String documentoStatus,
      HttpServletRequest request) {
    repository.save(EventoVerificacaoEtariaEntity.registrarCompliance(
        resultado,
        metodo,
        anuncioId,
        challengeId,
        sessionHash,
        codigo(estado, 48),
        codigo(escopo, 48),
        codigo(motivo, 96),
        codigo(documentoStatus, 32),
        hashService.hash("ip", request == null ? null : request.getRemoteAddr()),
        hashService.hash(
            "user-agent",
            request == null ? null : request.getHeader("User-Agent")),
        request == null ? "" : RequestIdContext.current(request),
        OffsetDateTime.now(ZoneOffset.UTC)));
  }

  private String codigo(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String redacted = EMAIL.matcher(value).replaceAll("DADO_OMITIDO");
    redacted = CPF.matcher(redacted).replaceAll("DADO_OMITIDO");
    redacted = BIRTH_DATE.matcher(redacted).replaceAll("DADO_OMITIDO");
    redacted = LONG_NUMBER.matcher(redacted).replaceAll("DADO_OMITIDO");
    String sanitized = redacted.trim()
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9_:-]", "_");
    return sanitized.substring(0, Math.min(max, sanitized.length()));
  }
}
