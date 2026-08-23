package br.com.topsdojob.v3.web.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.compliance.ComplianceProtectedMediaService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ComplianceProtectedMediaControllerTest {

  @Test
  void entregaVideoProtegidoComMimeEHeadersFailClosed() {
    ComplianceProtectedMediaService service = mock(ComplianceProtectedMediaService.class);
    ComplianceProtectedMediaController controller =
        new ComplianceProtectedMediaController(service);
    UUID midiaId = UUID.randomUUID();
    HttpServletRequest request = new MockHttpServletRequest();
    byte[] bytes = new byte[] {0, 1, 2, 3};
    when(service.carregar(midiaId, request))
        .thenReturn(new ComplianceProtectedMediaService.Conteudo(bytes, "video/mp4"));

    var response = controller.carregar(midiaId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).hasToString("video/mp4");
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getHeaders().getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
    assertThat(response.getHeaders().getFirst("X-Content-Type-Options"))
        .isEqualTo("nosniff");
    assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
    assertThat(response.getBody()).containsExactly(bytes);
    assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.LOCATION);
    verify(service).carregar(midiaId, request);
  }
}
