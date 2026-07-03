package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioValidationErrorDto;
import java.util.List;

public class SolicitarAnuncioValidationException extends RuntimeException {
    private final List<SolicitarAnuncioValidationErrorDto> errors;

    SolicitarAnuncioValidationException(List<SolicitarAnuncioValidationErrorDto> errors) {
        super("solicitacao local invalida");
        this.errors = List.copyOf(errors);
    }

    public List<SolicitarAnuncioValidationErrorDto> errors() {
        return errors;
    }
}
