package br.com.topsdojob.v3.application.admin.stories.dto;


public record AdminStoryConfiguracaoRequest(
    Boolean ativo,
    Integer custoCreditos,
    Long versao) {
}
