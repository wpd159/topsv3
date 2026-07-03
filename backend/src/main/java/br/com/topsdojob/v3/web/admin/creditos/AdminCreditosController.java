package br.com.topsdojob.v3.web.admin.creditos;

import br.com.topsdojob.v3.application.admin.creditos.CreditoConsistenciaService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoLedgerConsultaService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoSaldoConsultaService;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoConsistenciaResumoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoMovimentoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoPaginaDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoSaldoDto;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/creditos")
public class AdminCreditosController {

    private final CreditoSaldoConsultaService saldoService;
    private final CreditoLedgerConsultaService ledgerService;
    private final CreditoConsistenciaService consistenciaService;

    public AdminCreditosController(
            CreditoSaldoConsultaService saldoService,
            CreditoLedgerConsultaService ledgerService,
            CreditoConsistenciaService consistenciaService) {
        this.saldoService = saldoService;
        this.ledgerService = ledgerService;
        this.consistenciaService = consistenciaService;
    }

    @GetMapping("/usuarios/{id}/saldo")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminCreditoSaldoDto saldo(@PathVariable UUID id) {
        return saldoService.consultar(id);
    }

    @GetMapping("/usuarios/{id}/movimentos")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminCreditoPaginaDto<AdminCreditoMovimentoDto> movimentos(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ledgerService.consultar(id, page, size);
    }

    @GetMapping("/consistencia")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminCreditoConsistenciaResumoDto consistencia() {
        return consistenciaService.consultarConsistencia();
    }

    @GetMapping("/inconsistencias")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminCreditoConsistenciaResumoDto inconsistencias() {
        return consistenciaService.consultarInconsistencias();
    }
}
