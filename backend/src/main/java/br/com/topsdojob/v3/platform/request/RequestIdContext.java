package br.com.topsdojob.v3.platform.request;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIdContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestIdContext.class.getName() + ".requestId";

    private RequestIdContext() {
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE_NAME);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        return "";
    }
}
