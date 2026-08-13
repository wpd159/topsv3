package br.com.topsdojob.v3.importacao.anuncio;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Matriz única entre o estado efetivo legado e o estado conservador da V3. */
public final class MatrizStatusAnuncioImportacao {

  private static final Map<String, DestinoStatus> MATRIZ = Map.ofEntries(
      Map.entry("RASCUNHO", destino(StatusAnuncio.RASCUNHO,
          StatusModeracaoAnuncio.NAO_ENVIADO, false)),
      Map.entry("PENDENTE", destino(StatusAnuncio.PENDENTE_REVISAO,
          StatusModeracaoAnuncio.PENDENTE, false)),
      Map.entry("EM_REVISAO", destino(StatusAnuncio.PENDENTE_REVISAO,
          StatusModeracaoAnuncio.PENDENTE, false)),
      Map.entry("ATIVO", destino(StatusAnuncio.PUBLICADO,
          StatusModeracaoAnuncio.APROVADO, true)),
      Map.entry("APROVADO", destino(StatusAnuncio.PUBLICADO,
          StatusModeracaoAnuncio.APROVADO, true)),
      Map.entry("PUBLICADO", destino(StatusAnuncio.PUBLICADO,
          StatusModeracaoAnuncio.APROVADO, true)),
      Map.entry("PAUSADO", destino(StatusAnuncio.PAUSADO,
          StatusModeracaoAnuncio.APROVADO, false)),
      Map.entry("INATIVO", destino(StatusAnuncio.PAUSADO,
          StatusModeracaoAnuncio.APROVADO, false)),
      Map.entry("REJEITADO", destino(StatusAnuncio.REJEITADO,
          StatusModeracaoAnuncio.REJEITADO, false)),
      Map.entry("BLOQUEADO", destino(StatusAnuncio.BLOQUEADO,
          StatusModeracaoAnuncio.APROVADO, false)),
      Map.entry("REMOVIDO", destino(StatusAnuncio.REMOVIDO,
          StatusModeracaoAnuncio.APROVADO, false)));

  public Optional<DestinoStatus> mapear(String statusLegado) {
    if (statusLegado == null || statusLegado.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(MATRIZ.get(statusLegado.trim().toUpperCase(Locale.ROOT)));
  }

  public Map<String, DestinoStatus> valores() {
    return MATRIZ;
  }

  private static DestinoStatus destino(
      StatusAnuncio status,
      StatusModeracaoAnuncio moderacao,
      boolean candidatoPublicacao) {
    return new DestinoStatus(status, moderacao, candidatoPublicacao);
  }

  public record DestinoStatus(
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao,
      boolean candidatoPublicacao) {
  }
}
