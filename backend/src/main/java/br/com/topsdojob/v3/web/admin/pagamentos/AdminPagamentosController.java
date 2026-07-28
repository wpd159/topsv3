package br.com.topsdojob.v3.web.admin.pagamentos;

import br.com.topsdojob.v3.application.admin.pagamentos.PagamentoConsistenciaService;
import br.com.topsdojob.v3.application.admin.pagamentos.PagamentoConsultaService;
import br.com.topsdojob.v3.application.admin.pagamentos.AdminRelatorioReceitaService;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.PaginaTransacoes;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.Resumo;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoConsistenciaResumoDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoDetalheDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoListaItemDto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminPagamentoPaginaDto;
import java.util.UUID;
import java.time.LocalDate;
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
    private final AdminRelatorioReceitaService relatorioReceitaService;

    public AdminPagamentosController(
            PagamentoConsultaService consultaService,
            PagamentoConsistenciaService consistenciaService,
            AdminRelatorioReceitaService relatorioReceitaService) {
        this.consultaService = consultaService;
        this.consistenciaService = consistenciaService;
        this.relatorioReceitaService = relatorioReceitaService;
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

    @GetMapping("/relatorio/resumo")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public Resumo relatorioResumo(
            @RequestParam(defaultValue = "30_DIAS") String periodo,
            @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fim,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "TODOS") String metodo,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String produto) {
        return relatorioReceitaService.resumo(
                periodo, inicio, fim, status, metodo, usuario, produto);
    }

    @GetMapping("/relatorio/transacoes")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public PaginaTransacoes relatorioTransacoes(
            @RequestParam(defaultValue = "30_DIAS") String periodo,
            @RequestParam(required = false) LocalDate inicio,
            @RequestParam(required = false) LocalDate fim,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "TODOS") String metodo,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String produto,
            @RequestParam(defaultValue = "MAIS_RECENTES") String ordenacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return relatorioReceitaService.transacoes(
                periodo, inicio, fim, status, metodo, usuario, produto, ordenacao, page, size);
    }
}
