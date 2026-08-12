package br.com.topsdojob.v3.application.publico.pagamento;

public final class EfiWebhookAutenticacaoException extends RuntimeException {

    public EfiWebhookAutenticacaoException() {
        super("N\u00e3o foi poss\u00edvel validar a notifica\u00e7\u00e3o.");
    }
}