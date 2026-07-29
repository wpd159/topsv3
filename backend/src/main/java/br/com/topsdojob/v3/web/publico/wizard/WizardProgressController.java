package br.com.topsdojob.v3.web.publico.wizard;

import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncRequest;
import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncResponse;
import br.com.topsdojob.v3.application.wizard.WizardProgressSyncService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/wizard-progress")
public class WizardProgressController {

  private final WizardProgressSyncService service;

  public WizardProgressController(WizardProgressSyncService service) {
    this.service = service;
  }

  @PostMapping("/sync")
  public ResponseEntity<SyncResponse> sincronizar(
      @RequestBody(required = false) SyncRequest request,
      Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.sincronizar(request, authentication));
  }
}
