package br.com.topsdojob.v3.application.publico.anunciante.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record MinhaContaStoryAtivacaoRequest(
    String modoConteudo,
    UUID anuncioId,
    Integer custoCreditosEsperado,
    Long versaoConfiguracao) {
}
