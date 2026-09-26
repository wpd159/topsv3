package br.com.topsdojob.v3.web.admin.arquivo;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Item;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeService;
import br.com.topsdojob.v3.application.admin.arquivo.FinalidadeAcessoArquivoPublicidade;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/registros/publicidade")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ARQUIVO_PUBLICIDADE_LER')")
public class AdminArquivoPublicidadeController {
  private final AdminArquivoPublicidadeService service;
  private final ObjectMapper mapper;

  public AdminArquivoPublicidadeController(AdminArquivoPublicidadeService service, ObjectMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping
  public ResponseEntity<AdminPaginaDto<Item>> listar(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam FinalidadeAcessoArquivoPublicidade finalidade,
      @AuthenticationPrincipal AdminUserPrincipal actor,
      HttpServletRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.listar(page, size, actor.usuarioId(), RequestIdContext.current(request),
            finalidade));
  }

  @PostMapping("/{id}")
  public ResponseEntity<Detalhe> detalhar(
      @PathVariable UUID id,
      @RequestParam FinalidadeAcessoArquivoPublicidade finalidade,
      @AuthenticationPrincipal AdminUserPrincipal actor,
      HttpServletRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.detalhar(id, actor.usuarioId(), RequestIdContext.current(request),
            finalidade, false));
  }

  @PostMapping(value = "/{id}/exportacao", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ARQUIVO_PUBLICIDADE_LER') and hasAuthority('ARQUIVO_PUBLICIDADE_EXPORTAR')")
  public ResponseEntity<byte[]> exportar(
      @PathVariable UUID id,
      @RequestParam FinalidadeAcessoArquivoPublicidade finalidade,
      @AuthenticationPrincipal AdminUserPrincipal actor,
      HttpServletRequest request) throws JsonProcessingException {
    Detalhe detalhe = service.detalhar(id, actor.usuarioId(), RequestIdContext.current(request),
        finalidade, true);
    byte[] bytes = mapper.writeValueAsBytes(detalhe);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=publicidade-" + id + ".json")
        .contentType(MediaType.APPLICATION_JSON)
        .contentLength(bytes.length)
        .body(bytes);
  }

  @PostMapping("/{id}/midias/{midiaId}/arquivo")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ARQUIVO_PUBLICIDADE_LER') and hasAuthority('ARQUIVO_PUBLICIDADE_EXPORTAR')")
  public ResponseEntity<byte[]> arquivo(
      @PathVariable UUID id,
      @PathVariable UUID midiaId,
      @RequestParam FinalidadeAcessoArquivoPublicidade finalidade,
      @AuthenticationPrincipal AdminUserPrincipal actor,
      HttpServletRequest request) {
    var result = service.midia(id, midiaId, actor.usuarioId(), RequestIdContext.current(request),
        finalidade);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=publicidade-midia-" + midiaId)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(result.bytes().length)
        .body(result.bytes());
  }
}
