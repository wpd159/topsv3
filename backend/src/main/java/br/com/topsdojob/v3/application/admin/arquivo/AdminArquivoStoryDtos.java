package br.com.topsdojob.v3.application.admin.arquivo;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Versao;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Projecoes privadas do arquivo prospectivo de Stories, sem chaves de storage. */
public final class AdminArquivoStoryDtos {
  private AdminArquivoStoryDtos() {
  }

  public record Item(UUID id, UUID storyId, UUID anuncioId, String titulo,
      String modoConteudo, String status, String natureza, String cobertura,
      OffsetDateTime inicioEm, OffsetDateTime fimEm) {
  }

  public record Detalhe(UUID id, UUID storyId, UUID anuncioId,
      UUID contratanteUsuarioId, UUID ativacaoBeneficioId, UUID grupoAtivacaoId,
      UUID movimentoCreditoId, UUID pagamentoId, String modoConteudo,
      String natureza, String relacaoMaterial, String cobertura,
      OffsetDateTime inicioEm, OffsetDateTime fimEm, OffsetDateTime retencaoAte,
      String encerramentoMotivo, List<Versao> versoes) {
  }
}
