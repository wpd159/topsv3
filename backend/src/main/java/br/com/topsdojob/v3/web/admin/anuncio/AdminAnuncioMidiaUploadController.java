package br.com.topsdojob.v3.web.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaUploadService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioMidiaUploadDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/anuncios")
public class AdminAnuncioMidiaUploadController {

    private final AdminAnuncioMidiaUploadService service;

    public AdminAnuncioMidiaUploadController(AdminAnuncioMidiaUploadService service) {
        this.service = service;
    }

    @PostMapping(path = "/{anuncioId}/midias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR') and hasAuthority('MIDIA_REVISAR')")
    public ResponseEntity<AdminAnuncioMidiaUploadDto> enviar(
            @PathVariable UUID anuncioId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            MultipartHttpServletRequest multipartRequest) {
        MultipartFile arquivo = arquivoUnico(multipartRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.enviar(
                        anuncioId,
                        arquivo,
                        idempotencyKey,
                        administrador,
                        RequestIdContext.current(multipartRequest)));
    }

    private MultipartFile arquivoUnico(MultipartHttpServletRequest request) {
        List<MultipartFile> arquivos = request.getFiles("arquivo");
        if (request.getMultiFileMap().size() != 1
                || !request.getMultiFileMap().containsKey("arquivo")
                || arquivos.size() != 1
                || !request.getParameterMap().isEmpty()) {
            throw multipartInvalido();
        }
        try {
            Collection<Part> partes = request.getParts();
            if (!partes.isEmpty()) {
                if (partes.size() != 1) throw multipartInvalido();
                Part parte = partes.iterator().next();
                if (!"arquivo".equals(parte.getName()) || parte.getSubmittedFileName() == null) {
                    throw multipartInvalido();
                }
            }
        } catch (IOException | ServletException exception) {
            throw multipartInvalido();
        }
        return arquivos.get(0);
    }

    private ResponseStatusException multipartInvalido() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "multipart deve conter exatamente uma parte arquivo");
    }
}
