package br.com.topsdojob.v3.application.admin.anuncio.dto;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.CategoriaBloqueioJuridico;

public record AdminBloqueioJuridicoRequest(
    CategoriaBloqueioJuridico categoria,
    String motivo,
    String observacaoInterna) {
}
