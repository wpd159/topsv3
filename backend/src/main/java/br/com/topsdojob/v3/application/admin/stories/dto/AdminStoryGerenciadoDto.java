package br.com.topsdojob.v3.application.admin.stories.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminStoryGerenciadoDto(
    UUID id,
    String usuarioUsername,
    String modoConteudo,
    String status,
    OffsetDateTime publicadoEm,
    OffsetDateTime expiraEm,
    String anuncioSlug,
    String anuncioTitulo,
    String estadoMidia,
    boolean falhaTecnica,
    OffsetDateTime encerradoEm,
    String origemEncerramento,
    String motivoEncerramento,
    boolean direitoPreservado) {
}
