package br.com.topsdojob.v3.application.publico.pagamento.dto;

public record EfiWebhookResultadoDto(int recebidos, int processados, int repetidos, int ignorados) {
}
