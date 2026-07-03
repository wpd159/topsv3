package br.com.topsdojob.v3.web.admin.pagamentos;

import br.com.topsdojob.v3.application.admin.pagamentos.PagamentoConsistenciaService;
import br.com.topsdojob.v3.application.admin.pagamentos.PagamentoConsultaService;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoConsistenciaResumoDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoDetalheDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoListaItemDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoPaginaDto;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pagamentos")
public class AdminPagamentosController {

    private final PagamentoConsultaService consultaService;
    private final PagamentoConsistenciaService consistenciaService;

    public AdminPagamentosController(
            PagamentoConsultaService consultaService,
            PagamentoConsistenciaService consistenciaService) {
        this.consultaService = consultaService;
        this.consistenciaService = consistenciaService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminPagamentoPaginaDto<AdminPagamentoListaItemDto> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return consultaService.listar(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminPagamentoDetalheDto detalhar(@PathVariable UUID id) {
        return consultaService.detalhar(id);
    }

    @GetMapping("/consistencia")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminPagamentoConsistenciaResumoDto consistencia() {
        return consistenciaService.consultarConsistencia();
    }

    @GetMapping("/inconsistencias")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public AdminPagamentoConsistenciaResumoDto inconsistencias() {
        return consistenciaService.consultarInconsistencias();
    }
}
