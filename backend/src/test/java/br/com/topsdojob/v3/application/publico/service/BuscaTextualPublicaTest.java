package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BuscaTextualPublicaTest {

    @Test
    void normalizaEspacosCaixaEAcentosSemRemoverPontuacaoLegitima() {
        assertThat(BuscaTextualPublica.normalizarParaLike("  MARÇÃO\tD'Avila \"VIP\"  "))
                .isEqualTo("marcao d'avila \"vip\"");
    }

    @Test
    void trataAusenciaOuEspacosComoBuscaVazia() {
        assertThat(BuscaTextualPublica.normalizarParaLike(null)).isNull();
        assertThat(BuscaTextualPublica.normalizarParaLike(" \t\r\n ")).isNull();
    }

    @Test
    void escapaWildcardsEBarraParaBuscaLiteralParametrizada() {
        assertThat(BuscaTextualPublica.normalizarParaLike("100%_vip\\foto"))
                .isEqualTo("100\\%\\_vip\\\\foto");
    }

    @Test
    void preservaEmojiETextoDeInjecaoComoConteudoLiteral() {
        assertThat(BuscaTextualPublica.normalizarParaLike("💖 ' OR 1=1 --"))
                .isEqualTo("💖 ' or 1=1 --");
    }

    @Test
    void aceitaLimiteExatoERejeitaExcessoComRespostaControlada() {
        String limite = "a".repeat(BuscaTextualPublica.LIMITE_CARACTERES);
        assertThat(BuscaTextualPublica.normalizarParaLike(limite)).isEqualTo(limite);

        assertThatThrownBy(() -> BuscaTextualPublica.normalizarParaLike(limite + "b"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("busca deve ter no maximo 80 caracteres");
                });
    }
}
