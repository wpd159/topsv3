package br.com.topsdojob.v3.application.publico.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

public record StoryPublicoDto(
        String storyId,
        String modoConteudo,
        Integer ordem,
        String tipo,
        String finalidade,
        String visibilidadeMidia,
        String urlPublica,
        Integer largura,
        Integer altura,
        Integer duracaoMs,
        String mimeType,
        String pendenciaMidia,
        @JsonInclude(JsonInclude.Include.NON_NULL) String titulo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String cidade,
        @JsonInclude(JsonInclude.Include.NON_NULL) String uf,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal preco,
        @JsonInclude(JsonInclude.Include.NON_NULL) String resumo) {

    public StoryPublicoDto(
            Integer ordem,
            String tipo,
            String finalidade,
            String visibilidadeMidia,
            String urlPublica,
            Integer largura,
            Integer altura,
            Integer duracaoMs,
            String mimeType,
            String pendenciaMidia) {
        this(
                null, null, ordem, tipo, finalidade, visibilidadeMidia, urlPublica,
                largura, altura, duracaoMs, mimeType, pendenciaMidia,
                null, null, null, null, null);
    }
}
