package br.com.topsdojob.v3.web.admin.creditos;

import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.creditos.AdminPlanoCreditoService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoConsistenciaService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoLedgerConsultaService;
import br.com.topsdojob.v3.application.admin.creditos.CreditoSaldoConsultaService;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminAuditoriaFinanceiraDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoAjusteRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoConsistenciaResumoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoEstornoRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoMovimentoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoOperacaoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoPaginaDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoSaldoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoUsuarioDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.AtualizarRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.CriarRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.Resumo;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.StatusRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/creditos")
public class AdminCreditosController {

    private final CreditoSaldoConsultaService saldoService;
    private final CreditoLedgerConsultaService ledgerService;
    private final CreditoConsistenciaService consistenciaService;
    private final AdminCreditoOperacaoService operacaoService;
    private final AdminPlanoCreditoService planoService;

    public AdminCreditosController(
            CreditoSaldoConsultaService saldoService,
            CreditoLedgerConsultaService ledgerService,
            CreditoConsistenciaService consistenciaService,
            AdminCreditoOperacaoService operacaoService,
            AdminPlanoCreditoService planoService) {
        this.saldoService = saldoService;
        this.ledgerService = ledgerService;
        this.consistenciaService = consistenciaService;
        this.operacaoService = operacaoService;
        this.planoService = planoService;
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

    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public List<AdminCreditoUsuarioDto> usuarios(@RequestParam String query) {
        return operacaoService.buscarUsuarios(query);
    }

    @PostMapping("/usuarios/{id}/ajustes")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_GERENCIAR')")
    public AdminCreditoOperacaoDto ajustar(
            @PathVariable UUID id,
            @RequestBody AdminCreditoAjusteRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return operacaoService.ajustar(
                id,
                body,
                idempotencyKey,
                administrador,
                RequestIdContext.current(request));
    }

    @PostMapping("/movimentos/{id}/estornos")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_GERENCIAR')")
    public AdminCreditoOperacaoDto estornar(
            @PathVariable UUID id,
            @RequestBody AdminCreditoEstornoRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return operacaoService.estornar(
                id,
                body == null ? null : body.motivo(),
                idempotencyKey,
                administrador,
                RequestIdContext.current(request));
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public List<AdminAuditoriaFinanceiraDto> auditoria(@RequestParam(defaultValue = "50") int limit) {
        return operacaoService.auditoria(limit);
    }

    @GetMapping("/pacotes")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public List<Resumo> pacotes(
            @RequestParam(defaultValue = "") String busca,
            @RequestParam(defaultValue = "TODOS") String status) {
        return planoService.listar(busca, status);
    }

    @GetMapping("/pacotes/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public Resumo detalharPacote(@PathVariable UUID id) {
        return planoService.detalhar(id);
    }

    @PostMapping("/pacotes")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_GERENCIAR')")
    public Resumo criarPacote(
            @RequestBody CriarRequest body,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return planoService.criar(body, administrador, RequestIdContext.current(request));
    }

    @PutMapping("/pacotes/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_GERENCIAR')")
    public Resumo atualizarPacote(
            @PathVariable UUID id,
            @RequestBody AtualizarRequest body,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return planoService.atualizar(
                id,
                body,
                administrador,
                RequestIdContext.current(request));
    }

    @PostMapping("/pacotes/{id}/ativacao")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_GERENCIAR')")
    public Resumo ativarPacote(
            @PathVariable UUID id,
            @RequestBody StatusRequest body,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return planoService.ativar(id, body, administrador, RequestIdContext.current(request));
    }

    @PostMapping("/pacotes/{id}/desativacao")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_GERENCIAR')")
    public Resumo desativarPacote(
            @PathVariable UUID id,
            @RequestBody StatusRequest body,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return planoService.desativar(id, body, administrador, RequestIdContext.current(request));
    }
}
