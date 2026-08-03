package br.com.topsdojob.v3.application.admin.stories.dto;

import java.time.OffsetDateTime;

public record AdminStoryConfiguracaoDto(
    boolean configurada,
    boolean ativo,
    Integer custoCreditos,
    int duracaoHoras,
    Long versao,
    OffsetDateTime atualizadoEm) {
}
