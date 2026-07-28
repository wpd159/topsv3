package br.com.topsdojob.v3.application.denuncia;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DenunciaDtos {
    private DenunciaDtos() {
    }

    public record CriarDenunciaRequest(
            UUID anuncioId,
            String motivo,
            String descricao) {
    }

    public record CriarDenunciaResponse(
            UUID id,
            String protocolo,
            String status,
            OffsetDateTime criadoEm,
            boolean repetida) {
    }

    public record AlterarStatusRequest(
            String status,
            String providencia) {
    }

    public record Pagina<T>(
            List<T> itens,
            int pagina,
            int tamanho,
            long totalElementos,
            int totalPaginas) {
    }

    public record Resumo(
            UUID id,
            String protocolo,
            UUID anuncioId,
            String anuncioTitulo,
            String anuncioSlug,
            String motivo,
            String motivoRotulo,
            String status,
            String statusRotulo,
            String denuncianteNome,
            String denuncianteEmail,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm) {
    }

    public record Anuncio(
            UUID id,
            String titulo,
            String slug,
            String status,
            String statusModeracao) {
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
            Resumo denuncia,
            String descricao,
            String providencia,
            String responsavelNome,
            OffsetDateTime decididaEm,
            Anuncio anuncio,
            List<Historico> historico) {
    }

    public record Indicadores(
            long total,
            long pendentes,
            long punidas,
            long ignoradas) {
    }
}
