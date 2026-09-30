package br.com.topsdojob.v3.application.admin.arquivo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Projecoes privadas: nenhuma chave de storage ou documento KYC e exposta. */
public final class AdminArquivoPublicidadeDtos {
  private AdminArquivoPublicidadeDtos() {
  }

  public record Item(
      UUID id,
      UUID anuncioId,
      String titulo,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      String status,
      String natureza,
      String relacaoMaterial,
      String cobertura,
      String tipo,
      String slug,
      UUID anuncianteId,
      String anuncianteNome,
      String beneficioCodigo,
      long totalVersoes,
      String encerramentoMotivo,
      OffsetDateTime retencaoAte,
      boolean preservacaoAtiva,
      String fimTipo) {
  }

  public record Midia(
      UUID id,
      String variante,
      String mimeType,
      long tamanhoBytes,
      String sha256,
      int ordem,
      String arquivoUrl) {
  }

  public record Versao(
      UUID id,
      int numero,
      OffsetDateTime capturadoEm,
      OffsetDateTime vigenteDesde,
      OffsetDateTime vigenteAte,
      String motivo,
      JsonNode conteudo,
      JsonNode contratante,
      JsonNode comercial,
      JsonNode segmentacao,
      JsonNode alcance,
      String conteudoSha256,
      List<Midia> midias) {
  }

  public record Detalhe(
      UUID id,
      UUID anuncioId,
      UUID contratanteUsuarioId,
      UUID ativacaoBeneficioId,
      UUID grupoAtivacaoId,
      UUID movimentoCreditoId,
      UUID pagamentoId,
      String natureza,
      String relacaoMaterial,
      String cobertura,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      OffsetDateTime retencaoAte,
      String encerramentoMotivo,
      List<Versao> versoes,
      String fimTipo,
      List<Preservacao> preservacoes) {
    public Detalhe(UUID id, UUID anuncioId, UUID contratanteUsuarioId,
        UUID ativacaoBeneficioId, UUID grupoAtivacaoId, UUID movimentoCreditoId,
        UUID pagamentoId, String natureza, String relacaoMaterial, String cobertura,
        OffsetDateTime inicioEm, OffsetDateTime fimEm, OffsetDateTime retencaoAte,
        String encerramentoMotivo, List<Versao> versoes) {
      this(id, anuncioId, contratanteUsuarioId, ativacaoBeneficioId, grupoAtivacaoId,
          movimentoCreditoId, pagamentoId, natureza, relacaoMaterial, cobertura,
          inicioEm, fimEm, retencaoAte, encerramentoMotivo, versoes,
          AdminArquivoPublicidadeConsulta.fimTipo(fimEm, encerramentoMotivo), List.of());
    }
  }

  public record Preservacao(UUID id, String fundamento, UUID responsavelUsuarioId,
      OffsetDateTime inicioEm, OffsetDateTime revisarEm) { }

  public record Relatorio<T>(String tipo, OffsetDateTime geradoEm, String fusoHorario,
      UUID responsavelId, FinalidadeAcessoArquivoPublicidade finalidade,
      AdminArquivoPublicidadeConsulta.Filtros filtros, List<UUID> idsSelecionados,
      int quantidade, int limiteRegistros, List<String> lacunas, List<T> registros) { }

  public record Arquivo(byte[] bytes, String mimeType) {
  }
}
