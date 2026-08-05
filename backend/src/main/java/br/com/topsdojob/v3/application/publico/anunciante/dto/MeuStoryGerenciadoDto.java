package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeuStoryGerenciadoDto(
    UUID id,
    String modoConteudo,
    String status,
    OffsetDateTime publicadoEm,
    OffsetDateTime expiraEm,
    String anuncioSlug,
    String anuncioTitulo,
    String estadoMidia,
    boolean falhaTecnica,
    boolean podeExcluir,
    boolean podeDescartar,
    OffsetDateTime encerradoEm,
    boolean direitoPreservado) {
}
