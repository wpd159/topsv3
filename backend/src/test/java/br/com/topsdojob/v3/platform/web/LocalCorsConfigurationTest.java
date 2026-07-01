package br.com.topsdojob.v3.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalCorsConfigurationTest {

    @Test
    void corsComCredentialsFicaAtivoSomenteEmLocalComOrigemConfigurada() {
        LocalCorsConfiguration local = new LocalCorsConfiguration("http://localhost:3000", "local");
        LocalCorsConfiguration producao = new LocalCorsConfiguration("https://topsdojob.com", "producao");
        LocalCorsConfiguration semOrigem = new LocalCorsConfiguration("", "local");

        assertThat(local.localCredentialsEnabled()).isTrue();
        assertThat(producao.localCredentialsEnabled()).isFalse();
        assertThat(semOrigem.localCredentialsEnabled()).isFalse();
    }
}
