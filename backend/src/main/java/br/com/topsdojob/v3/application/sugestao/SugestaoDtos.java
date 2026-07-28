package br.com.topsdojob.v3.application.sugestao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SugestaoDtos {

    private SugestaoDtos() {
    }

    public record CriarRequest(String tipo, String titulo, String descricao) {
    }

    public record Criacao(
            UUID id,
            String protocolo,
            String status,
            String statusRotulo,
            OffsetDateTime criadoEm,
            boolean repetida) {
    }

    public record AlterarStatusRequest(String status, String providencia) {
    }

    public record Resumo(
            UUID id,
            String protocolo,
            String titulo,
            String tipo,
            String tipoRotulo,
            String status,
            String statusRotulo,
            String usuarioNome,
            String usuarioEmail,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm) {
    }

    public record Historico(
            UUID id,
            String acao,
            String acaoRotulo,
            String status,
            String providencia,
            String atorNome,
            OffsetDateTime criadoEm,
            String requestId) {
    }

    public record Detalhe(
            Resumo sugestao,
            String descricao,
            String providencia,
            String responsavelNome,
            OffsetDateTime decididoEm,
            List<Historico> historico) {
    }

    public record Indicadores(
            long total,
            long novas,
            long emAnalise,
            long resolvidas,
            long recusadas) {
    }

    public record Pagina<T>(
            List<T> itens,
            int pagina,
            int tamanho,
            long totalElementos,
            int totalPaginas) {
    }
}
