package br.com.topsdojob.v3.application.publico.pagamento;

import br.com.topsdojob.v3.platform.error.ApiErrorCode;

public class PagamentoPixException extends RuntimeException {

    private final ApiErrorCode code;

    public PagamentoPixException(ApiErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public ApiErrorCode code() {
        return code;
    }
}
