package br.com.topsdojob.v3.web.admin.wizard;

import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressDtos.Dashboard;
import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressService;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/wizard-progress")
@PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
public class AdminWizardProgressController {

  private final AdminWizardProgressService service;

  public AdminWizardProgressController(AdminWizardProgressService service) {
    this.service = service;
  }

  @GetMapping("/dashboard")
  public ResponseEntity<Dashboard> dashboard(
      @RequestParam(defaultValue = "30_DIAS") String periodo,
      @RequestParam(required = false) LocalDate inicio,
      @RequestParam(required = false) LocalDate fim,
      @RequestParam(required = false) String termo,
      @RequestParam(defaultValue = "TODOS") String modo,
      @RequestParam(defaultValue = "TODOS") String status,
      @RequestParam(required = false) String uf,
      @RequestParam(required = false) String cidade,
      @RequestParam(defaultValue = "TODOS") String kyc,
      @RequestParam(defaultValue = "TODOS") String anuncioStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.consultar(
            periodo,
            inicio,
            fim,
            termo,
            modo,
            status,
            uf,
            cidade,
            kyc,
            anuncioStatus,
            page,
            size));
  }
}
