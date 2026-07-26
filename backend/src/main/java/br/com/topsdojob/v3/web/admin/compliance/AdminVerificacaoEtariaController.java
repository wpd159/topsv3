package br.com.topsdojob.v3.web.admin.compliance;

import br.com.topsdojob.v3.application.admin.compliance.AdminVerificacaoEtariaConsultaService;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminVerificacaoEtariaDto;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/compliance/verificacoes-etarias")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('SEGURANCA_GERENCIAR')")
public class AdminVerificacaoEtariaController {

  private final AdminVerificacaoEtariaConsultaService service;

  public AdminVerificacaoEtariaController(AdminVerificacaoEtariaConsultaService service) {
    this.service = service;
  }

  @GetMapping
  public List<AdminVerificacaoEtariaDto> listar(
      @RequestParam(defaultValue = "50") int limite) {
    return service.listar(limite);
  }
}
