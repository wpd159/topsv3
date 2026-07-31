package br.com.topsdojob.v3.web.publico.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.kyc.KycPublicoException;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class KycPublicoControllerTest {

  @Test
  void retornaCodigoCpfSanitizadoERequestIdSemCache() {
    KycPublicoController controller = new KycPublicoController(mock(KycPublicoService.class));
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getAttribute(RequestIdContext.ATTRIBUTE_NAME)).thenReturn("req-kyc-cpf");

    var response = controller.handleKycError(
        new KycPublicoException(
            HttpStatus.CONFLICT,
            "CPF_JA_CADASTRADO",
            "Este CPF ja esta cadastrado em outra conta."),
        request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().codigo()).isEqualTo("CPF_JA_CADASTRADO");
    assertThat(response.getBody().requestId()).isEqualTo("req-kyc-cpf");
  }
}
