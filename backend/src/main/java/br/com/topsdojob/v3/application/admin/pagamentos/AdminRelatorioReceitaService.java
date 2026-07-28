package br.com.topsdojob.v3.application.admin.pagamentos;

import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.AlertaConciliacao;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.DistribuicaoProduto;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.PaginaTransacoes;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.PontoDiario;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.Resumo;
import br.com.topsdojob.v3.application.admin.pagamentos.dto.AdminRelatorioReceitaDtos.Transacao;
import br.com.topsdojob.v3.persistence.repository.admin.AdminRelatorioReceitaJdbcRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminRelatorioReceitaService {

    static final ZoneId FUSO_CANONICO = ZoneId.of("America/Sao_Paulo");

    private final AdminRelatorioReceitaJdbcRepository repository;
    private final PagamentoSanitizer sanitizer;
    private final Clock clock;

    @Autowired
    public AdminRelatorioReceitaService(
            AdminRelatorioReceitaJdbcRepository repository,
            PagamentoSanitizer sanitizer) {
        this(repository, sanitizer, Clock.systemUTC());
    }

    AdminRelatorioReceitaService(
            AdminRelatorioReceitaJdbcRepository repository,
            PagamentoSanitizer sanitizer,
            Clock clock) {
        this.repository = repository;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Resumo resumo(
            String periodo,
            LocalDate inicio,
            LocalDate fim,
            String status,
            String metodo,
            String usuario,
            String produto) {
        OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        RelatorioReceitaFiltro filtro = filtro(
                periodo, inicio, fim, status, metodo, usuario, produto, agora);
        var metricas = repository.metricas(filtro);
        List<PontoDiario> serie = repository.evolucaoDiaria(filtro).stream()
                .map(item -> new PontoDiario(
                        item.data(),
                        item.receitaConfirmada(),
                        item.pagamentosConfirmados()))
                .toList();
        List<DistribuicaoProduto> distribuicao = repository.distribuicaoPorProduto(filtro).stream()
                .map(item -> new DistribuicaoProduto(
                        item.codigo(),
                        item.nome(),
                        item.receitaConfirmada(),
                        item.pagamentosConfirmados(),
                        item.creditosVendidos()))
                .toList();
        return new Resumo(
                metricas.receitaConfirmada(),
                metricas.pagamentosConfirmados(),
                ticketMedio(metricas.receitaConfirmada(), metricas.pagamentosConfirmados()),
                metricas.creditosVendidos(),
                metricas.pagamentosPendentes(),
                metricas.pagamentosFalhos(),
                metricas.pagamentosCancelados(),
                metricas.pagamentosEstornados(),
                serie,
                distribuicao,
                alertas(repository.conciliacao(filtro)),
                filtro.dataInicio(),
                filtro.dataFim(),
                FUSO_CANONICO.getId(),
                agora,
                true);
    }

    @Transactional(readOnly = true)
    public PaginaTransacoes transacoes(
            String periodo,
            LocalDate inicio,
            LocalDate fim,
            String status,
            String metodo,
            String usuario,
            String produto,
            String ordenacao,
            int page,
            int size) {
        OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        RelatorioReceitaFiltro filtro = filtro(
                periodo, inicio, fim, status, metodo, usuario, produto, agora);
        int pagina = Math.max(0, page);
        int tamanho = Math.max(1, Math.min(size, 100));
        String ordem = enumValue(
                ordenacao,
                "MAIS_RECENTES",
                List.of("MAIS_RECENTES", "MAIS_ANTIGOS", "MAIOR_VALOR", "MENOR_VALOR"));
        var resultado = repository.transacoes(filtro, pagina, tamanho, ordem);
        List<Transacao> itens = resultado.itens().stream()
                .map(item -> new Transacao(
                        item.id(),
                        item.data(),
                        texto(item.usuarioNome(), "Usuario"),
                        sanitizer.mascararEmail(item.usuarioEmail()),
                        "LEGADO".equals(item.status()) ? "REGISTRO_LEGADO" : "COMPRA_DE_CREDITOS",
                        item.produtoNome(),
                        item.valor(),
                        item.moeda(),
                        item.creditos(),
                        statusHumano(item.status()),
                        metodoHumano(item.metodo()),
                        sanitizer.mascararEvidenciaTransacao(item.identificadorExterno()),
                        "APROVADO".equals(item.status()),
                        true))
                .toList();
        return new PaginaTransacoes(
                itens,
                resultado.total(),
                pagina,
                tamanho,
                resultado.receitaConfirmada(),
                resultado.pagamentosConfirmados(),
                filtro.dataInicio(),
                filtro.dataFim(),
                FUSO_CANONICO.getId(),
                true);
    }

    private RelatorioReceitaFiltro filtro(
            String periodoSolicitado,
            LocalDate inicioSolicitado,
            LocalDate fimSolicitado,
            String statusSolicitado,
            String metodoSolicitado,
            String usuario,
            String produto,
            OffsetDateTime agora) {
        LocalDate hoje = agora.atZoneSameInstant(FUSO_CANONICO).toLocalDate();
        String periodo = enumValue(
                periodoSolicitado,
                "30_DIAS",
                List.of("HOJE", "7_DIAS", "30_DIAS", "PERSONALIZADO"));
        LocalDate inicio = switch (periodo) {
            case "HOJE" -> hoje;
            case "7_DIAS" -> hoje.minusDays(6);
            case "PERSONALIZADO" -> exigirData(inicioSolicitado, "inicio");
            default -> hoje.minusDays(29);
        };
        LocalDate fim = "PERSONALIZADO".equals(periodo)
                ? exigirData(fimSolicitado, "fim")
                : hoje;
        if (fim.isBefore(inicio)) {
            throw badRequest("fim deve ser igual ou posterior ao inicio");
        }
        if (inicio.plusYears(2).isBefore(fim)) {
            throw badRequest("periodo personalizado deve ter no maximo dois anos");
        }
        RelatorioReceitaFiltro.Status status = RelatorioReceitaFiltro.Status.valueOf(enumValue(
                statusSolicitado,
                "TODOS",
                List.of(
                        "TODOS",
                        "CONFIRMADO",
                        "PENDENTE",
                        "FALHO",
                        "CANCELADO",
                        "EXPIRADO",
                        "ESTORNADO",
                        "LEGADO")));
        RelatorioReceitaFiltro.Metodo metodo = RelatorioReceitaFiltro.Metodo.valueOf(enumValue(
                metodoSolicitado,
                "TODOS",
                List.of("TODOS", "PIX", "LEGADO", "DESCONHECIDO")));
        return new RelatorioReceitaFiltro(
                inicio,
                fim,
                inicio.atStartOfDay(FUSO_CANONICO).toOffsetDateTime(),
                fim.plusDays(1).atStartOfDay(FUSO_CANONICO).toOffsetDateTime(),
                status,
                metodo,
                normalizarBusca(usuario),
                normalizarBusca(produto));
    }

    private List<AlertaConciliacao> alertas(
            AdminRelatorioReceitaJdbcRepository.ConciliacaoRow conciliacao) {
        List<AlertaConciliacao> alertas = new ArrayList<>();
        adicionarAlerta(
                alertas,
                "PAGAMENTO_SEM_CONCILIACAO",
                "Pagamentos confirmados sem conciliacao concluida.",
                conciliacao.semConciliacao());
        adicionarAlerta(
                alertas,
                "CONCILIACAO_DIVERGENTE",
                "Valor ou creditos divergem entre pagamento e conciliacao.",
                conciliacao.divergentes());
        adicionarAlerta(
                alertas,
                "CONCILIACAO_SEM_MOVIMENTO",
                "Conciliacoes concluidas sem movimento de credito vinculado.",
                conciliacao.semMovimento());
        return List.copyOf(alertas);
    }

    private static void adicionarAlerta(
            List<AlertaConciliacao> alertas,
            String codigo,
            String mensagem,
            long quantidade) {
        if (quantidade > 0) {
            alertas.add(new AlertaConciliacao(codigo, mensagem, quantidade));
        }
    }

    private static BigDecimal ticketMedio(BigDecimal receita, long quantidade) {
        if (quantidade == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return receita.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    private static String statusHumano(String status) {
        return switch (status) {
            case "APROVADO" -> "CONFIRMADO";
            case "CRIADO", "AGUARDANDO_PAGAMENTO" -> "PENDENTE";
            case "ERRO" -> "FALHO";
            case "CANCELADO" -> "CANCELADO";
            case "EXPIRADO" -> "EXPIRADO";
            case "ESTORNADO" -> "ESTORNADO";
            case "LEGADO" -> "LEGADO";
            default -> "DESCONHECIDO";
        };
    }

    private static String metodoHumano(String metodo) {
        return switch (metodo) {
            case "PIX" -> "Pix";
            case "LEGADO" -> "Legado";
            default -> "Desconhecido";
        };
    }

    private static String enumValue(
            String value,
            String padrao,
            List<String> permitidos) {
        String normalizado = value == null || value.isBlank()
                ? padrao
                : value.trim().toUpperCase();
        if (!permitidos.contains(normalizado)) {
            throw badRequest("filtro invalido: " + normalizado);
        }
        return normalizado;
    }

    private static LocalDate exigirData(LocalDate valor, String campo) {
        if (valor == null) {
            throw badRequest(campo + " e obrigatorio no periodo personalizado");
        }
        return valor;
    }

    private static String normalizarBusca(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim();
        if (normalizado.length() > 120) {
            throw badRequest("filtro de busca excede 120 caracteres");
        }
        return normalizado;
    }

    private static ResponseStatusException badRequest(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }

    private static String texto(String valor, String fallback) {
        return valor == null || valor.isBlank() ? fallback : valor;
    }
}
