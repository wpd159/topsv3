package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OrdemSeedPublicaServiceTest {

    @Test
    void geraNovaSeedParaCadaPesquisaSemValorInformado() {
        long[] valores = {1234567890123456789L, -987654321098765432L};
        AtomicInteger indice = new AtomicInteger();
        OrdemSeedPublicaService service = new OrdemSeedPublicaService(() -> valores[indice.getAndIncrement()]);

        assertThat(service.resolver(null)).isEqualTo(valores[0]);
        assertThat(service.resolver(null)).isEqualTo(valores[1]);
    }

    @Test
    void reutilizaTodaFaixaDeLongSemDependerDeDataOuFiltro() {
        OrdemSeedPublicaService service = new OrdemSeedPublicaService(() -> 7L);

        assertThat(service.resolver(Long.toString(Long.MIN_VALUE))).isEqualTo(Long.MIN_VALUE);
        assertThat(service.resolver(Long.toString(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
        assertThat(service.resolver("0")).isZero();
    }

    @Test
    void rejeitaSeedVaziaNaoDecimalOuForaDe64BitsCom400() {
        OrdemSeedPublicaService service = new OrdemSeedPublicaService(() -> 7L);

        for (String invalida : new String[] {"", " 1", "+1", "1.0", "9223372036854775808"}) {
            assertThatThrownBy(() -> service.resolver(invalida))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}
