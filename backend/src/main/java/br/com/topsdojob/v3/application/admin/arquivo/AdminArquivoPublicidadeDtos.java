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
      String cobertura) {
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
      List<Versao> versoes) {
  }

  public record Arquivo(byte[] bytes, String mimeType) {
  }
}
