package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceProtectedMediaService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/compliance/visitor/media")
public class ComplianceProtectedMediaController {

  private final ComplianceProtectedMediaService service;

  public ComplianceProtectedMediaController(ComplianceProtectedMediaService service) {
    this.service = service;
  }

  @GetMapping("/{midiaId}")
  public ResponseEntity<byte[]> carregar(
      @PathVariable UUID midiaId,
      HttpServletRequest request) {
    var result = service.carregar(midiaId, request);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(result.mimeType()))
        .contentLength(result.bytes().length)
        .body(result.bytes());
  }
}
