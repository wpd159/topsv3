package br.com.topsdojob.v3.application.wizard;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validation shared by creation replay and exact-session recovery. */
public final class WizardCreationSessionPolicy {
  private static final Pattern SESSION_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$");

  private WizardCreationSessionPolicy() { }

  public static String sessionId(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (!SESSION_ID.matcher(value).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "identificador de sessao invalido");
    }
    return value;
  }

  public static void exigirCreate(String modo) {
    if (!"CREATE".equals(modo)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "sessao do wizard pertence a outro modo");
    }
  }

  public static AnuncioEntity anuncioRecuperavel(AnuncioEntity anuncio, UUID usuarioId) {
    if (!usuarioId.equals(anuncio.getUsuarioId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "anuncio do wizard pertence a outro usuario");
    }
    if (anuncio.getRemovidoEm() != null || anuncio.getStatus() == StatusAnuncio.REMOVIDO
        || anuncio.getStatus() == StatusAnuncio.BLOQUEADO || anuncio.getStatus() == null
        || anuncio.getStatusModeracao() == null || anuncio.getSlug() == null || anuncio.getSlug().isBlank()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio da sessao nao pode ser retomado");
    }
    return anuncio;
  }
}
