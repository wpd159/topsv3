package br.com.topsdojob.v3.security.config;

import br.com.topsdojob.v3.platform.error.ApiErrorCode;
import br.com.topsdojob.v3.platform.error.ApiErrorResponse;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class AdminSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public AdminSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ApiErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                code.status().value(),
                code.status().getReasonPhrase(),
                code,
                code.defaultMessage(),
                request.getRequestURI(),
                RequestIdContext.current(request));
        objectMapper.writeValue(response.getWriter(), body);
        response.flushBuffer();
    }
}
