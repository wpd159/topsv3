package br.com.topsdojob.v3.application.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PublicPasswordPolicyTest {

    @Test
    void aceitaSenhaQueCumpreTodosOsRequisitos() {
        assertThatCode(() -> PublicPasswordPolicy.validate("Nova@Forte9", "Nova@Forte9"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Curta@1",
            "semmaiuscula@1",
            "SEMMINUSCULA@1",
            "SemNumero@",
            "SemSimbolo9",
            "Senha@1234"
    })
    void recusaCadaViolacaoDaPolitica(String candidate) {
        assertThatThrownBy(() -> PublicPasswordPolicy.validate(candidate, candidate))
                .isInstanceOfSatisfying(PublicAuthException.class, error ->
                        assertThat(error.getMessage())
                                .isEqualTo("Senha não atende aos requisitos de segurança."));
    }

    @Test
    void diferenciaConfirmacaoDivergente() {
        assertThatThrownBy(() -> PublicPasswordPolicy.validate("Nova@Forte9", "Outra@Forte9"))
                .isInstanceOfSatisfying(PublicAuthException.class, error ->
                        assertThat(error.getMessage()).isEqualTo("As senhas não coincidem."));
    }
}
