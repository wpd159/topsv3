package br.com.topsdojob.v3.application.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AnuncioAtualizacaoCanonicaValidatorTest {

    private final AnuncioAtualizacaoCanonicaValidator validator =
            new AnuncioAtualizacaoCanonicaValidator();

    @Test
    void preservaCategoriaBaseQuandoSexoVirtualTambemFoiSelecionado() {
        var resultado = validator.validar(request(
                "ACOMPANHANTE_FEMININA",
                List.of("ORAL", "VIDEOCHAMADA"),
                false));

        assertThat(resultado.categoria()).isEqualTo("ACOMPANHANTE_FEMININA");
        assertThat(resultado.servicos())
                .containsExactlyInAnyOrder(ServicoAnuncio.ORAL, ServicoAnuncio.VIDEOCHAMADA);
        assertThat(resultado.atendimentoExclusivamenteVirtual()).isFalse();
    }

    @Test
    void normalizaCategoriaVirtualLegadaSemMarcarExclusividade() {
        var resultado = validator.validar(request(
                "VENDA_DE_CONTEUDO",
                List.of("ORAL"),
                false));

        assertThat(resultado.categoria()).isEqualTo("ACOMPANHANTE_FEMININA");
        assertThat(resultado.servicos())
                .containsExactlyInAnyOrder(ServicoAnuncio.ORAL, ServicoAnuncio.VIDEOCHAMADA);
        assertThat(resultado.atendimentoExclusivamenteVirtual()).isFalse();
    }

    @Test
    void aceitaExclusividadeSomenteComSexoVirtualSelecionado() {
        var resultado = validator.validar(request(
                "ACOMPANHANTE_FEMININA",
                List.of("VIDEOCHAMADA"),
                true));

        assertThat(resultado.atendimentoExclusivamenteVirtual()).isTrue();
    }

    @Test
    void rejeitaExclusividadeSemSexoVirtual() {
        assertThatThrownBy(() -> validator.validar(request(
                "ACOMPANHANTE_FEMININA",
                List.of("ORAL"),
                true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason())
                            .isEqualTo("atendimento exclusivamente virtual exige o servico VIDEOCHAMADA");
                });
    }

    private MeuAnuncioAtualizacaoRequestDto request(
            String categoria,
            List<String> servicos,
            boolean exclusivamenteVirtual) {
        return new MeuAnuncioAtualizacaoRequestDto(
                "Titulo valido para anuncio",
                "Descricao suficientemente longa para o anuncio",
                categoria,
                new BigDecimal("200.00"),
                "GO",
                "Goiania",
                "Setor Bueno",
                List.of("A_COMBINAR"),
                servicos,
                "+5562999999999",
                exclusivamenteVirtual);
    }
}
