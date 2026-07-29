package br.com.topsdojob.v3.application.admin.creditos;

import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.AtualizarRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.CriarRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.Resumo;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.StatusRequest;
import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminPlanoCreditoConsultaRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPlanoCreditoService {

    private static final Pattern CODIGO = Pattern.compile("^[A-Z0-9_]{3,50}$");
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999.99");

    private final PlanoCreditoRepository planos;
    private final AdminPlanoCreditoConsultaRepository consultas;
    private final AdminCreditoOperacaoService auditoria;

    public AdminPlanoCreditoService(
            PlanoCreditoRepository planos,
            AdminPlanoCreditoConsultaRepository consultas,
            AdminCreditoOperacaoService auditoria) {
        this.planos = planos;
        this.consultas = consultas;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar(String busca, String status) {
        String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
        if (termo.length() > 120) {
            throw badRequest("busca de planos invalida");
        }
        FiltroStatus filtro = FiltroStatus.from(status);
        List<PlanoCreditoEntity> encontrados = planos.findAllByOrderByOrdemExibicaoAscCodigoAsc().stream()
                .filter(plano -> termo.isEmpty()
                        || plano.getNome().toLowerCase(Locale.ROOT).contains(termo)
                        || plano.getCodigo().toLowerCase(Locale.ROOT).contains(termo))
                .filter(plano -> filtro.aceita(Boolean.TRUE.equals(plano.getAtivo())))
                .toList();
        Map<UUID, Long> compras = consultas.comprasConfirmadas(
                encontrados.stream().map(PlanoCreditoEntity::getId).toList());
        return encontrados.stream().map(plano -> toDto(plano, compras.getOrDefault(plano.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public Resumo detalhar(UUID id) {
        PlanoCreditoEntity plano = planos.findById(id)
                .orElseThrow(() -> notFound());
        return toDto(plano, comprasConfirmadas(id));
    }

    @Transactional
    public Resumo criar(
            CriarRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        if (request == null || request.ativo() == null) {
            throw badRequest("dados do plano obrigatorios");
        }
        String codigo = codigo(request.codigo());
        if (planos.existsByCodigoIgnoreCase(codigo)) {
            throw conflito("codigo do plano ja existe");
        }
        DadosPlano dados = validarDados(
                request.nome(),
                request.descricao(),
                request.quantidadeCreditos(),
                request.valor(),
                request.ordemExibicao());
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        PlanoCreditoEntity plano = PlanoCreditoEntity.criar(
                UUID.randomUUID(),
                codigo,
                dados.nome(),
                dados.descricao(),
                dados.quantidadeCreditos(),
                dados.valor(),
                request.ativo(),
                dados.ordemExibicao(),
                agora);
        try {
            planos.saveAndFlush(plano);
        } catch (DataIntegrityViolationException exception) {
            throw conflito("codigo do plano ja existe");
        }
        auditoria.auditar(
                administrador.usuarioId(),
                "CREDITO_PACOTE_CRIAR",
                "PLANO_CREDITO",
                plano.getId(),
                Map.of(),
                estado(plano),
                requestId);
        return toDto(plano, 0L);
    }

    @Transactional
    public Resumo atualizar(
            UUID id,
            AtualizarRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        if (request == null) {
            throw badRequest("dados do plano obrigatorios");
        }
        PlanoCreditoEntity plano = bloquear(id);
        validarVersao(plano, request.atualizadoEm());
        DadosPlano dados = validarDados(
                request.nome(),
                request.descricao(),
                request.quantidadeCreditos(),
                request.valor(),
                request.ordemExibicao());
        Map<String, Object> antes = estado(plano);
        plano.atualizar(
                dados.nome(),
                dados.descricao(),
                dados.quantidadeCreditos(),
                dados.valor(),
                dados.ordemExibicao(),
                OffsetDateTime.now(ZoneOffset.UTC));
        planos.saveAndFlush(plano);
        auditoria.auditar(
                administrador.usuarioId(),
                "CREDITO_PACOTE_ATUALIZAR",
                "PLANO_CREDITO",
                plano.getId(),
                antes,
                estado(plano),
                requestId);
        return toDto(plano, comprasConfirmadas(id));
    }

    @Transactional
    public Resumo ativar(
            UUID id,
            StatusRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        return alterarStatus(id, request, true, administrador, requestId);
    }

    @Transactional
    public Resumo desativar(
            UUID id,
            StatusRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        return alterarStatus(id, request, false, administrador, requestId);
    }

    private Resumo alterarStatus(
            UUID id,
            StatusRequest request,
            boolean ativo,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAdministrador(administrador);
        PlanoCreditoEntity plano = bloquear(id);
        if (Boolean.TRUE.equals(plano.getAtivo()) == ativo) {
            return toDto(plano, comprasConfirmadas(id));
        }
        validarVersao(plano, request == null ? null : request.atualizadoEm());
        if (ativo && (plano.getQuantidadeCreditos() == null
                || plano.getQuantidadeCreditos() <= 0
                || plano.getValor() == null
                || plano.getValor().signum() <= 0)) {
            throw conflito("plano invalido nao pode ser ativado");
        }
        Map<String, Object> antes = estado(plano);
        plano.alterarAtivo(ativo, OffsetDateTime.now(ZoneOffset.UTC));
        planos.saveAndFlush(plano);
        auditoria.auditar(
                administrador.usuarioId(),
                ativo ? "CREDITO_PACOTE_ATIVAR" : "CREDITO_PACOTE_DESATIVAR",
                "PLANO_CREDITO",
                plano.getId(),
                antes,
                estado(plano),
                requestId);
        return toDto(plano, comprasConfirmadas(id));
    }

    private PlanoCreditoEntity bloquear(UUID id) {
        return planos.findByIdForUpdate(id).orElseThrow(this::notFound);
    }

    private DadosPlano validarDados(
            String nome,
            String descricao,
            Integer quantidadeCreditos,
            BigDecimal valor,
            Integer ordemExibicao) {
        return new DadosPlano(
                texto(nome, 2, 120, "nome invalido"),
                texto(descricao, 0, 500, "descricao invalida"),
                inteiroPositivo(quantidadeCreditos, "quantidade de creditos invalida"),
                valorMonetario(valor),
                inteiroNaoNegativo(ordemExibicao, "ordem invalida"));
    }

    private String codigo(String value) {
        String codigo = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CODIGO.matcher(codigo).matches()) {
            throw badRequest("codigo deve conter apenas letras maiusculas, numeros e sublinhado");
        }
        return codigo;
    }

    private BigDecimal valorMonetario(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(VALOR_MAXIMO) > 0) {
            throw badRequest("preco deve ser positivo");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw badRequest("preco deve possuir no maximo duas casas decimais");
        }
    }

    private String texto(String value, int min, int max, String error) {
        String texto = value == null ? "" : value.trim();
        if (texto.length() < min || texto.length() > max) {
            throw badRequest(error);
        }
        return texto;
    }

    private int inteiroPositivo(Integer value, String error) {
        if (value == null || value <= 0 || value > 1_000_000) {
            throw badRequest(error);
        }
        return value;
    }

    private int inteiroNaoNegativo(Integer value, String error) {
        if (value == null || value < 0 || value > 1_000_000) {
            throw badRequest(error);
        }
        return value;
    }

    private void validarVersao(PlanoCreditoEntity plano, OffsetDateTime versao) {
        if (versao == null || plano.getAtualizadoEm() == null
                || !plano.getAtualizadoEm().toInstant().equals(versao.toInstant())) {
            throw conflito("plano foi alterado por outra operacao; atualize os dados e tente novamente");
        }
    }

    private void validarAdministrador(AdminUserPrincipal administrador) {
        if (administrador == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
        boolean autorizado = administrador.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch("ROLE_ADMIN"::equals)
                && administrador.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .anyMatch("FINANCEIRO_GERENCIAR"::equals);
        if (!autorizado) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "permissao financeira obrigatoria");
        }
    }

    private long comprasConfirmadas(UUID id) {
        return consultas.comprasConfirmadas(List.of(id)).getOrDefault(id, 0L);
    }

    private Resumo toDto(PlanoCreditoEntity plano, long comprasConfirmadas) {
        return new Resumo(
                plano.getId(),
                plano.getCodigo(),
                plano.getNome(),
                plano.getDescricao(),
                plano.getQuantidadeCreditos(),
                plano.getValor(),
                plano.getMoeda(),
                Boolean.TRUE.equals(plano.getAtivo()),
                plano.getOrdemExibicao(),
                comprasConfirmadas,
                plano.getCriadoEm(),
                plano.getAtualizadoEm());
    }

    private Map<String, Object> estado(PlanoCreditoEntity plano) {
        return Map.of(
                "codigo", plano.getCodigo(),
                "nome", plano.getNome(),
                "quantidadeCreditos", plano.getQuantidadeCreditos(),
                "valor", plano.getValor(),
                "ativo", Boolean.TRUE.equals(plano.getAtivo()),
                "ordemExibicao", plano.getOrdemExibicao());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "plano de creditos nao encontrado");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflito(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record DadosPlano(
            String nome,
            String descricao,
            int quantidadeCreditos,
            BigDecimal valor,
            int ordemExibicao) {
    }

    private enum FiltroStatus {
        TODOS,
        ATIVOS,
        INATIVOS;

        private static FiltroStatus from(String value) {
            try {
                return value == null || value.isBlank()
                        ? TODOS
                        : valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filtro de status invalido");
            }
        }

        private boolean aceita(boolean ativo) {
            return this == TODOS || (this == ATIVOS && ativo) || (this == INATIVOS && !ativo);
        }
    }
}
