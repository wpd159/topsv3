package br.com.topsdojob.v3.application.suporte;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SuporteDtos {
    private SuporteDtos() {
    }

    public record Pagina<T>(
            List<T> itens,
            int pagina,
            int tamanho,
            long totalElementos,
            int totalPaginas) {
    }

    public record TicketResumo(
            UUID id,
            String protocolo,
            String assunto,
            String categoria,
            String categoriaRotulo,
            String status,
            String statusRotulo,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm,
            long totalMensagens,
            long naoLidas) {
    }

    public record Mensagem(
            UUID id,
            String origem,
            String remetente,
            String corpo,
            OffsetDateTime criadoEm,
            boolean minha,
            boolean repetida,
            boolean naoLida) {
    }

    public record TicketDetalhe(
            TicketResumo ticket,
            List<Mensagem> mensagens) {
    }

    public record CriarTicketRequest(
            String assunto,
            String categoria,
            String descricao) {
    }

    public record ResponderTicketRequest(String mensagem) {
    }

    public record AlterarStatusRequest(String status) {
    }

    public record AdminTicketResumo(
            UUID id,
            String protocolo,
            String assunto,
            String categoria,
            String categoriaRotulo,
            String status,
            String statusRotulo,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm,
            long totalMensagens,
            UUID usuarioId,
            String usuarioNome,
            String usuarioEmail,
            String mensagemInicial) {
    }

    public record AdminTicketDetalhe(
            AdminTicketResumo ticket,
            List<Mensagem> mensagens,
            UUID responsavelId,
            String responsavelNome,
            OffsetDateTime encerradoEm) {
    }

    public record Indicadores(
            long total,
            long abertos,
            long emAtendimento,
            long aguardandoUsuario,
            long resolvidos,
            long encerrados,
            long pendentesEquipe) {
    }

    public record NaoLidas(long total) {
    }
}
