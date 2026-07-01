package br.com.topsdojob.v3.application.publico.dto;

import java.time.LocalDate;

public record ConfirmarIdadePublicaRequestDto(
        LocalDate dataNascimento,
        Boolean declaracaoMaioridade) {
}
