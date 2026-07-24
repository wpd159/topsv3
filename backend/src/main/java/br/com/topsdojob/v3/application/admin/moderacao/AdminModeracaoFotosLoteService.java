package br.com.topsdojob.v3.application.admin.moderacao;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService.CleanupException;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLotePrevalidacaoService.ItemValidado;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminResultadoFotoLoteItemDto;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminModeracaoFotosLoteService {

    private final AdminModeracaoFotosLotePrevalidacaoService prevalidacaoService;
    private final AdminModeracaoFotosLoteItemService itemService;
    private final AdminModeracaoFotosLoteAuditoriaService auditoriaService;

    public AdminModeracaoFotosLoteService(
            AdminModeracaoFotosLotePrevalidacaoService prevalidacaoService,
            AdminModeracaoFotosLoteItemService itemService,
            AdminModeracaoFotosLoteAuditoriaService auditoriaService) {
        this.prevalidacaoService = prevalidacaoService;
        this.itemService = itemService;
        this.auditoriaService = auditoriaService;
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
        var prevalidacao = prevalidacaoService.validar(anuncioId, request, ator);
        List<AdminResultadoFotoLoteItemDto> resultados = new ArrayList<>();

        for (ItemValidado item : prevalidacao.itens()) {
            if (item.jaProcessada()) {
                resultados.add(new AdminResultadoFotoLoteItemDto(
                        item.mediaId(),
                        item.decisao().name(),
                        item.classificacao() == null ? null : item.classificacao().name(),
                        "JA_PROCESSADA",
                        item.decisao() == AdminDecisaoFotoLoteAcao.EXCLUIR
                                ? "REMOVIDA"
                                : "PUBLICAVEL",
                        null));
                continue;
            }
            try {
                resultados.add(itemService.executar(
                        anuncioId,
                        item,
                        ator,
                        requestId));
            } catch (RuntimeException exception) {
                String codigo = codigoSanitizado(exception);
                auditoriaService.registrarFalha(
                        ator.usuarioId(),
                        item.mediaId(),
                        requestId,
                        codigo);
                resultados.add(new AdminResultadoFotoLoteItemDto(
                        item.mediaId(),
                        item.decisao().name(),
                        item.classificacao() == null ? null : item.classificacao().name(),
                        "FALHA",
                        null,
                        mensagemSanitizada(codigo)));
            }
        }

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
        return response;
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
            case "FALHA_OPERACIONAL_R2", "OBJETO_PERMANECE_NO_R2", "STORAGE_INDISPONIVEL" ->
                    "Nao foi possivel remover a foto do armazenamento. Tente novamente.";
            case "CONFLITO_DE_ESTADO" ->
                    "O estado da foto mudou. Atualize os dados e tente novamente.";
            case "MIDIA_NAO_ENCONTRADA" -> "A foto nao foi encontrada.";
            default -> "Nao foi possivel concluir a decisao desta foto.";
        };
    }
}
