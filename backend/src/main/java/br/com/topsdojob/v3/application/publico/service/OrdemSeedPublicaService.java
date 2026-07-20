package br.com.topsdojob.v3.application.publico.service;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrdemSeedPublicaService {

    private final LongSupplier gerador;

    public OrdemSeedPublicaService() {
        SecureRandom secureRandom = new SecureRandom();
        this.gerador = secureRandom::nextLong;
    }

    OrdemSeedPublicaService(LongSupplier gerador) {
        this.gerador = Objects.requireNonNull(gerador);
    }

    public long resolver(String ordemSeed) {
        if (ordemSeed == null) {
            return gerador.getAsLong();
        }
        if (ordemSeed.isBlank() || !ordemSeed.matches("-?[0-9]+")) {
            throw invalida();
        }
        try {
            return Long.parseLong(ordemSeed);
        } catch (NumberFormatException exception) {
            throw invalida();
        }
    }

    private ResponseStatusException invalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "ordemSeed invalida");
    }
}
