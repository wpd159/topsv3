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
                false), "+5562999999999");

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
                false), "+5562999999999");

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
                true), "+5562999999999");

        assertThat(resultado.atendimentoExclusivamenteVirtual()).isTrue();
    }

    @Test
    void rejeitaExclusividadeSemSexoVirtual() {
        assertThatThrownBy(() -> validator.validar(request(
                "ACOMPANHANTE_FEMININA",
                List.of("ORAL"),
                true), "+5562999999999"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason())
                            .isEqualTo("atendimento exclusivamente virtual exige o servico VIDEOCHAMADA");
                });
    }

    @Test
    void rejeitaHtmlOuJavascriptNoComplemento() {
        assertThatThrownBy(() -> validator.validar(request(
                "ACOMPANHANTE_FEMININA",
                List.of("ORAL"),
                false,
                "<b>Recepcao</b>"), "+5562999999999"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getReason()).contains("HTML ou JavaScript"));

        assertThatThrownBy(() -> validator.validar(request(
                "ACOMPANHANTE_FEMININA",
                List.of("ORAL"),
                false,
                "javascript:alert('x')"), "+5562999999999"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getReason()).contains("HTML ou JavaScript"));
    }

    private MeuAnuncioAtualizacaoRequestDto request(
            String categoria,
            List<String> servicos,
            boolean exclusivamenteVirtual) {
        return request(categoria, servicos, exclusivamenteVirtual, null);
    }

    private MeuAnuncioAtualizacaoRequestDto request(
            String categoria,
            List<String> servicos,
            boolean exclusivamenteVirtual,
            String enderecoResumido) {
        return new MeuAnuncioAtualizacaoRequestDto(
                "Titulo valido para anuncio",
                "Descricao suficientemente longa para o anuncio",
                categoria,
                new BigDecimal("200.00"),
                "GO",
                "Goiania",
                "Setor Bueno",
                enderecoResumido,
                List.of("A_COMBINAR"),
                servicos,
                exclusivamenteVirtual,
                "https://example.invalid/conteudo");
    }
}
