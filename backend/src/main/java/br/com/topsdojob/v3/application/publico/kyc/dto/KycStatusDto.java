package br.com.topsdojob.v3.application.publico.kyc.dto;

import java.util.List;

public record KycStatusDto(
    String status,
    String nomeCivil,
    boolean cpfPreenchido,
    String cpfMascarado,
    String dataNascimento,
    String motivo,
    boolean prontoParaEnviarAnuncio,
    boolean podeReenviar,
    long tamanhoMaximoBytes,
    List<String> formatosAceitos,
    List<KycDocumentoDto> documentos) {
}
