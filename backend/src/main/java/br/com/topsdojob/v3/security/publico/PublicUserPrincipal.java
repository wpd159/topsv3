package br.com.topsdojob.v3.security.publico;

import java.io.Serializable;
import java.util.UUID;

public record PublicUserPrincipal(
        UUID usuarioId,
        String username,
        String email) implements Serializable {
}
