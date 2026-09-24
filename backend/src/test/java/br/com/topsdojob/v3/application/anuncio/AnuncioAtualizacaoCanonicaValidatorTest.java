package br.com.topsdojob.v3.application.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AnuncioAtualizacaoCanonicaValidatorTest {

    private final AnuncioAtualizacaoCanonicaValidator validator =
            new AnuncioAtualizacaoCanonicaValidator();

    @ParameterizedTest
    @ValueSource(ints = {492, 500})
    void descricaoValidaAteLimiteCanonico(int tamanho) {
        String descricao = "x".repeat(tamanho);

        var validado = validator.validar(requestComDescricao(descricao), "+5562999999999");

        assertThat(validado.descricao()).isEqualTo(descricao);
    }

    @Test
    void descricaoRestauradaCom501CaracteresEhRecusadaSemTruncamento() {
        String descricao = "x".repeat(501);

        assertThatThrownBy(() -> validator.validar(requestComDescricao(descricao), "+5562999999999"))
                .isInstanceOfSatisfying(AnuncioAtualizacaoValidationException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.field()).isEqualTo("descricao");
                    assertThat(exception.ruleCode()).isEqualTo("TAMANHO_INVALIDO");
                });
        assertThat(descricao).hasSize(501);
    }

    @Test
    void descricaoEhContadaDepoisDaNormalizacaoCanonica() {
        String descricaoComQuebras = "x".repeat(498) + "\r\n" + "x";

        var validado = validator.validar(requestComDescricao(descricaoComQuebras), "+5562999999999");

        assertThat(validado.descricao()).isEqualTo("x".repeat(498) + " x").hasSize(500);
    }

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

    @ParameterizedTest
    @ValueSource(strings = {
            "Atendimento <script>alert('x')</script>",
            "Atendimento seguro </script>",
            "Atendimento <b>especial</b>",
            "Atendimento javascript:alert('x')"
    })
    void rejeitaConteudoAtivoNoTituloComMensagemCanonica(String titulo) {
        assertThatThrownBy(() -> validator.validar(request(
                titulo,
                "ACOMPANHANTE_FEMININA",
                List.of("ORAL"),
                false,
                null), "+5562999999999"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason())
                            .isEqualTo("titulo nao pode conter HTML ou JavaScript");
                });
    }

    @Test
    void aceitaTituloNormalComPontuacaoLegitima() {
        var resultado = validator.validar(request(
                "Atendimento & companhia (centro) - d'ela",
                "ACOMPANHANTE_FEMININA",
                List.of("ORAL"),
                false,
                null), "+5562999999999");

        assertThat(resultado.titulo()).isEqualTo("Atendimento & companhia (centro) - d'ela");
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
        return request(
                "Titulo valido para anuncio",
                categoria,
                servicos,
                exclusivamenteVirtual,
                enderecoResumido);
    }

    private MeuAnuncioAtualizacaoRequestDto request(
            String titulo,
            String categoria,
            List<String> servicos,
            boolean exclusivamenteVirtual,
            String enderecoResumido) {
        return new MeuAnuncioAtualizacaoRequestDto(
                titulo,
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

    private MeuAnuncioAtualizacaoRequestDto requestComDescricao(String descricao) {
        MeuAnuncioAtualizacaoRequestDto base = request(
                "ACOMPANHANTE_FEMININA", List.of("ORAL"), false);
        return new MeuAnuncioAtualizacaoRequestDto(
                base.titulo(),
                descricao,
                base.categoria(),
                base.preco(),
                base.uf(),
                base.cidade(),
                base.bairro(),
                base.enderecoResumido(),
                base.locaisAtendimento(),
                base.servicos(),
                base.atendimentoExclusivamenteVirtual(),
                base.linkConteudo());
    }
}
