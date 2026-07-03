package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.SolicitarAnuncioValidationErrorResponseDto;
import br.com.topsdojob.v3.application.publico.service.SolicitarAnuncioPublicoService;
import br.com.topsdojob.v3.application.publico.service.SolicitarAnuncioValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/anunciar")
public class PublicAnunciarController {

    private final SolicitarAnuncioPublicoService service;

    public PublicAnunciarController(SolicitarAnuncioPublicoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> solicitar(@RequestBody(required = false) JsonNode payload) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.solicitar(payload));
        } catch (SolicitarAnuncioValidationException exception) {
            return ResponseEntity.badRequest().body(SolicitarAnuncioValidationErrorResponseDto.from(exception.errors()));
        }
    }
}
