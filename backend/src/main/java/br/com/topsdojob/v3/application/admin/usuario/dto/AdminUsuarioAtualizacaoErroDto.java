package br.com.topsdojob.v3.application.admin.usuario.dto;

import java.util.List;

public record AdminUsuarioAtualizacaoErroDto(
    String codigo,
    String mensagem,
    List<AdminUsuarioErroCampoDto> erros,
    String requestId) {
}
