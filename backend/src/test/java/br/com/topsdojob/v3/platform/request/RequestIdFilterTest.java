package br.com.topsdojob.v3.platform.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, captureRequestId(requestIdInsideChain));

        String responseRequestId = response.getHeader(RequestIdContext.HEADER_NAME);
        assertThat(responseRequestId).isNotBlank();
        assertThat(requestIdInsideChain.get()).isEqualTo(responseRequestId);
    }

    @Test
    void propagatesValidRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String suppliedRequestId = "req-local-123456";
        request.addHeader(RequestIdContext.HEADER_NAME, suppliedRequestId);

        filter.doFilter(request, response, captureRequestId(new AtomicReference<>()));

        assertThat(response.getHeader(RequestIdContext.HEADER_NAME)).isEqualTo(suppliedRequestId);
    }

    @Test
    void replacesInvalidRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestIdContext.HEADER_NAME, "token com espaço e tamanho inseguro");

        filter.doFilter(request, response, captureRequestId(new AtomicReference<>()));

        assertThat(response.getHeader(RequestIdContext.HEADER_NAME))
                .isNotBlank()
                .isNotEqualTo("token com espaço e tamanho inseguro");
    }

    private FilterChain captureRequestId(AtomicReference<String> requestIdInsideChain) {
        return (request, response) -> requestIdInsideChain.set(
                (String) request.getAttribute(RequestIdContext.ATTRIBUTE_NAME));
    }
}
