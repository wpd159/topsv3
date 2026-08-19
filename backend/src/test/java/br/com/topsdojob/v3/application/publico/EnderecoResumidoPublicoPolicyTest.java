package br.com.topsdojob.v3.application.publico;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import org.junit.jupiter.api.Test;

class EnderecoResumidoPublicoPolicyTest {

    @Test
    void omiteSomenteMarcadorTecnicoExatoComVariacoesDeAcentoEspacoECaixa() {
        assertThat(EnderecoResumidoPublicoPolicy.projetar("Endereco sintetico local")).isNull();
        assertThat(EnderecoResumidoPublicoPolicy.projetar("  ENDEREÇO   SINTÉTICO LOCAL  ")).isNull();
        assertThat(new LocalizacaoPublicaDto(
                "PB", "Paraiba", "Joao Pessoa", "joao-pessoa", "Centro", "centro",
                "Endereço sintético local").enderecoResumido()).isNull();
    }

    @Test
    void preservaComplementoPublicoReal() {
        assertThat(EnderecoResumidoPublicoPolicy.projetar("  Próximo à recepção  "))
                .isEqualTo("Próximo à recepção");
        assertThat(EnderecoResumidoPublicoPolicy.projetar("Endereço sintético local - fundos"))
                .isEqualTo("Endereço sintético local - fundos");
    }
}
