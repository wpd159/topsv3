package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.dto.CidadeLocalidadePublicaDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalidadeSeoIndexabilidadePolicyTest {

    private final LocalidadeSeoIndexabilidadePolicy policy = new LocalidadeSeoIndexabilidadePolicy();

    @Test
    void aplicaLimiteDeCincoAnunciosElegiveisParaCidade() {
        assertThat(policy.cidade(4).indexavel()).isFalse();
        assertThat(policy.cidade(4).motivo()).isEqualTo("INVENTARIO_INSUFICIENTE");
        assertThat(policy.cidade(5).indexavel()).isTrue();
        assertThat(policy.cidade(5).motivo()).isEqualTo("INVENTARIO_SUFICIENTE");
    }

    @Test
    void aplicaLimiteDeTresAnunciosElegiveisParaBairro() {
        assertThat(policy.bairro(2).indexavel()).isFalse();
        assertThat(policy.bairro(3).indexavel()).isTrue();
    }

    @Test
    void estadoExigeAoMenosUmaCidadeIndexavel() {
        CidadeLocalidadePublicaDto cidadeNaoIndexavel = cidade("cidade-a", 4);
        CidadeLocalidadePublicaDto cidadeIndexavel = cidade("cidade-b", 5);

        assertThat(policy.estado(4, List.of(cidadeNaoIndexavel)).indexavel()).isFalse();
        assertThat(policy.estado(4, List.of(cidadeNaoIndexavel)).motivo())
                .isEqualTo("SEM_CIDADE_INDEXAVEL");
        assertThat(policy.estado(9, List.of(cidadeNaoIndexavel, cidadeIndexavel)).indexavel()).isTrue();
        assertThat(policy.estado(9, List.of(cidadeNaoIndexavel, cidadeIndexavel)).motivo())
                .isEqualTo("CIDADE_INDEXAVEL");
    }

    private CidadeLocalidadePublicaDto cidade(String slug, long elegiveis) {
        return new CidadeLocalidadePublicaDto(
                slug,
                slug,
                elegiveis,
                null,
                policy.cidade(elegiveis),
                List.of());
    }
}
