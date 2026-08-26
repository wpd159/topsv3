package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationReadinessStateTest {

    @Test
    void acompanhaInicializacaoEEncerramentoDaAplicacao() {
        var state = new ApplicationReadinessState();

        assertThat(state.isReady()).isFalse();
        state.applicationReady();
        assertThat(state.isReady()).isTrue();
        state.applicationClosing();
        assertThat(state.isReady()).isFalse();
    }
}
