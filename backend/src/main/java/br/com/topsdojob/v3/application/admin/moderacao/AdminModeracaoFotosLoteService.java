package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService.CleanupException;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLotePrevalidacaoService.ItemValidado;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminResultadoFotoLoteItemDto;
import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminModeracaoFotosLoteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminModeracaoFotosLoteService.class);
    private static final int MAX_CONCORRENCIA_ITENS = 3;

    private final AdminModeracaoFotosLotePrevalidacaoService prevalidacaoService;
    private final AdminModeracaoFotosLoteItemService itemService;
    private final AdminModeracaoFotosLoteAuditoriaService auditoriaService;
    private final TaskExecutor taskExecutor;

    public AdminModeracaoFotosLoteService(
            AdminModeracaoFotosLotePrevalidacaoService prevalidacaoService,
            AdminModeracaoFotosLoteItemService itemService,
            AdminModeracaoFotosLoteAuditoriaService auditoriaService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.prevalidacaoService = prevalidacaoService;
        this.itemService = itemService;
        this.auditoriaService = auditoriaService;
        this.taskExecutor = taskExecutor;
    }

    public AdminDecidirFotosLoteResponseDto decidir(
            UUID anuncioId,
            AdminDecidirFotosLoteRequestDto request,
            AdminUserPrincipal ator,
            String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "identificador da requisicao indisponivel");
        }
        long inicio = System.nanoTime();
        var prevalidacao = prevalidacaoService.validar(anuncioId, request, ator);
        long fimPrevalidacao = System.nanoTime();
        List<AdminResultadoFotoLoteItemDto> resultados = new ArrayList<>(
                Collections.nCopies(prevalidacao.itens().size(), null));
        boolean possuiExclusao = prevalidacao.itens().stream()
                .anyMatch(item -> item.decisao() == AdminDecisaoFotoLoteAcao.EXCLUIR);

        int concorrenciaAplicada;
        if (possuiExclusao || prevalidacao.itens().size() < 2) {
            processarSequencialmente(
                    anuncioId,
                    prevalidacao.itens(),
                    resultados,
                    ator,
                    requestId);
            concorrenciaAplicada = 1;
        } else {
            concorrenciaAplicada = processarAprovacoesComConcorrenciaLimitada(
                    anuncioId,
                    prevalidacao,
                    resultados,
                    ator,
                    requestId);
        }
        long fimProcessamento = System.nanoTime();

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        int aprovadas = contar(resultados, "APROVADA");
        int excluidas = contar(resultados, "EXCLUIDA");
        int jaProcessadas = contar(resultados, "JA_PROCESSADA");
        int falhas = contar(resultados, "FALHA");
        var response = new AdminDecidirFotosLoteResponseDto(
                anuncioId,
                List.copyOf(resultados),
                aprovadas,
                excluidas,
                jaProcessadas,
                falhas,
                falhas == 0,
                requestId,
                agora);
        if (aprovadas > 0 || excluidas > 0 || falhas > 0) {
            auditoriaService.registrarLote(ator.usuarioId(), response);
        }
        long fimAuditoria = System.nanoTime();
        LOGGER.info(
                "Moderacao de fotos concluida: itens={}, prevalidacaoMs={}, processamentoMs={}, auditoriaMs={}, totalMs={}, concorrenciaMax={}",
                resultados.size(),
                millis(inicio, fimPrevalidacao),
                millis(fimPrevalidacao, fimProcessamento),
                millis(fimProcessamento, fimAuditoria),
                millis(inicio, fimAuditoria),
                concorrenciaAplicada);
        return response;
    }

    private void processarSequencialmente(
            UUID anuncioId,
            List<ItemValidado> itens,
            List<AdminResultadoFotoLoteItemDto> resultados,
            AdminUserPrincipal ator,
            String requestId) {
        for (int index = 0; index < itens.size(); index++) {
            resultados.set(index, processarItem(anuncioId, itens.get(index), ator, requestId));
        }
    }

    private int processarAprovacoesComConcorrenciaLimitada(
            UUID anuncioId,
            AdminModeracaoFotosLotePrevalidacaoService.Prevalidacao prevalidacao,
            List<AdminResultadoFotoLoteItemDto> resultados,
            AdminUserPrincipal ator,
            String requestId) {
        long fotosPublicaveis = prevalidacao.fotosPublicaveisAntes();
        int cursor = 0;
        int concorrenciaAplicada = 1;

        while (cursor < prevalidacao.itens().size()
                && fotosPublicaveis < LimiteMidiasAnuncioService.FOTOS_BASE) {
            int vagasBase = Math.toIntExact(
                    LimiteMidiasAnuncioService.FOTOS_BASE - fotosPublicaveis);
            int fimOnda = Math.min(
                    cursor + Math.min(MAX_CONCORRENCIA_ITENS, vagasBase),
                    prevalidacao.itens().size());
            concorrenciaAplicada = Math.max(
                    concorrenciaAplicada,
                    processarOnda(
                            anuncioId,
                            prevalidacao.itens(),
                            resultados,
                            cursor,
                            fimOnda,
                            ator,
                            requestId));
            fotosPublicaveis += contar(resultados.subList(cursor, fimOnda), "APROVADA");
            cursor = fimOnda;
        }

        while (cursor < prevalidacao.itens().size()
                && fotosPublicaveis <= LimiteMidiasAnuncioService.FOTOS_BASE) {
            var resultado = processarItem(
                    anuncioId,
                    prevalidacao.itens().get(cursor),
                    ator,
                    requestId);
            resultados.set(cursor, resultado);
            if ("APROVADA".equals(resultado.resultado())) {
                fotosPublicaveis++;
            }
            cursor++;
        }

        for (int inicioOnda = cursor;
                inicioOnda < prevalidacao.itens().size();
                inicioOnda += MAX_CONCORRENCIA_ITENS) {
            int fimOnda = Math.min(
                    inicioOnda + MAX_CONCORRENCIA_ITENS,
                    prevalidacao.itens().size());
            concorrenciaAplicada = Math.max(
                    concorrenciaAplicada,
                    processarOnda(
                            anuncioId,
                            prevalidacao.itens(),
                            resultados,
                            inicioOnda,
                            fimOnda,
                            ator,
                            requestId));
        }
        return concorrenciaAplicada;
    }

    private int processarOnda(
            UUID anuncioId,
            List<ItemValidado> itens,
            List<AdminResultadoFotoLoteItemDto> resultados,
            int inicio,
            int fim,
            AdminUserPrincipal ator,
            String requestId) {
        List<CompletableFuture<AdminResultadoFotoLoteItemDto>> futuros = new ArrayList<>();
        for (int index = inicio; index < fim; index++) {
            ItemValidado item = itens.get(index);
            try {
                futuros.add(CompletableFuture.supplyAsync(
                        () -> processarItem(anuncioId, item, ator, requestId),
                        taskExecutor));
            } catch (RuntimeException exception) {
                futuros.add(CompletableFuture.completedFuture(
                        processarItem(anuncioId, item, ator, requestId)));
            }
        }
        for (int offset = 0; offset < futuros.size(); offset++) {
            resultados.set(inicio + offset, futuros.get(offset).join());
        }
        return futuros.size();
    }

    private AdminResultadoFotoLoteItemDto processarItem(
            UUID anuncioId,
            ItemValidado item,
            AdminUserPrincipal ator,
            String requestId) {
        if (item.jaProcessada()) {
            return new AdminResultadoFotoLoteItemDto(
                    item.mediaId(),
                    item.decisao().name(),
                    item.classificacao() == null ? null : item.classificacao().name(),
                    "JA_PROCESSADA",
                    item.decisao() == AdminDecisaoFotoLoteAcao.EXCLUIR
                            ? "REMOVIDA"
                            : "PUBLICAVEL",
                    null,
                    null);
        }
        try {
            return itemService.executar(anuncioId, item, ator, requestId);
        } catch (RuntimeException exception) {
            String codigo = codigoSanitizado(exception);
            auditoriaService.registrarFalha(
                    ator.usuarioId(),
                    item.mediaId(),
                    requestId,
                    codigo);
            return new AdminResultadoFotoLoteItemDto(
                    item.mediaId(),
                    item.decisao().name(),
                    item.classificacao() == null ? null : item.classificacao().name(),
                    "FALHA",
                    null,
                    codigo,
                    mensagemSanitizada(codigo));
        }
    }

    private long millis(long inicio, long fim) {
        return Math.max(0L, (fim - inicio) / 1_000_000L);
    }

    private int contar(List<AdminResultadoFotoLoteItemDto> itens, String resultado) {
        return (int) itens.stream().filter(item -> resultado.equals(item.resultado())).count();
    }

    private String codigoSanitizado(RuntimeException exception) {
        if (exception instanceof CleanupException cleanupException) {
            return cleanupException.codigo();
        }
        if (exception instanceof ResponseStatusException statusException) {
            return switch (statusException.getStatusCode().value()) {
                case 404 -> "MIDIA_NAO_ENCONTRADA";
                case 409 -> "CONFLITO_DE_ESTADO";
                case 503 -> "FALHA_OPERACIONAL_R2";
                default -> "FALHA_DE_VALIDACAO";
            };
        }
        return "FALHA_TECNICA";
    }

    private String mensagemSanitizada(String codigo) {
        return switch (codigo) {
            case FotoElegivelAnuncioPolicy.CODIGO_ULTIMA_FOTO_APROVADA ->
                    FotoElegivelAnuncioPolicy.MENSAGEM_ULTIMA_FOTO_APROVADA;
            case "FALHA_OPERACIONAL_R2", "OBJETO_PERMANECE_NO_R2", "STORAGE_INDISPONIVEL" ->
                    "Nao foi possivel remover a foto do armazenamento. Tente novamente.";
            case "CONFLITO_DE_ESTADO" ->
                    "O estado da foto mudou. Atualize os dados e tente novamente.";
            case "MIDIA_NAO_ENCONTRADA" -> "A foto nao foi encontrada.";
            default -> "Nao foi possivel concluir a decisao desta foto.";
        };
    }
}
