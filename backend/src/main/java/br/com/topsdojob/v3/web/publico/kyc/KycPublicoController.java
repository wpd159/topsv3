package br.com.topsdojob.v3.web.publico.kyc;

import br.com.topsdojob.v3.application.publico.kyc.KycPublicoException;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.application.publico.kyc.dto.KycPublicoErroDto;
import br.com.topsdojob.v3.application.publico.kyc.dto.KycStatusDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/public/minha-conta/kyc")
public class KycPublicoController {

  private final KycPublicoService service;

  public KycPublicoController(KycPublicoService service) {
    this.service = service;
  }

  @GetMapping
  public KycStatusDto consultar(Authentication authentication) {
    return service.consultar(authentication);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public KycStatusDto enviar(
      @RequestParam(required = false) String nomeCivil,
      @RequestParam(required = false) String cpf,
      @RequestParam(required = false) String dataNascimento,
      @RequestParam String modoDocumento,
      @RequestPart(value = "documentoUnico", required = false) MultipartFile documentoUnico,
      @RequestPart(value = "documentoFrente", required = false) MultipartFile documentoFrente,
      @RequestPart(value = "documentoVerso", required = false) MultipartFile documentoVerso,
      Authentication authentication,
      HttpServletRequest request) {
    return service.enviar(
        authentication,
        nomeCivil,
        cpf,
        dataNascimento,
        modoDocumento,
        documentoUnico,
        documentoFrente,
        documentoVerso,
        RequestIdContext.current(request));
  }

  @ExceptionHandler(KycPublicoException.class)
  public ResponseEntity<KycPublicoErroDto> handleKycError(
      KycPublicoException exception,
      HttpServletRequest request) {
    return ResponseEntity.status(exception.status())
        .cacheControl(CacheControl.noStore())
        .body(new KycPublicoErroDto(
            exception.code(),
            exception.getMessage(),
            RequestIdContext.current(request)));
  }
}
