package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.List;
import java.util.UUID;

public record ReordenarMinhasMidiasRequestDto(List<UUID> midiaIds) {
}
