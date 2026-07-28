package br.com.topsdojob.v3.web.admin.faq;

import br.com.topsdojob.v3.application.faq.FaqDtos.Edicao;
import br.com.topsdojob.v3.application.faq.FaqDtos.Item;
import br.com.topsdojob.v3.application.faq.FaqDtos.Ordem;
import br.com.topsdojob.v3.application.faq.FaqDtos.Versao;
import br.com.topsdojob.v3.application.faq.FaqService;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/faqs")
public class AdminFaqController {

  private static final String LEITURA =
      "hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ADMIN_CONFIGURAR')";
  private static final String MUTACAO =
      "hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')";

  private final FaqService service;

  public AdminFaqController(FaqService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(LEITURA)
  public List<Item> listar(
      @RequestParam(required = false) String termo,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String categoria) {
    return service.listarAdmin(termo, status, categoria);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(MUTACAO)
  public Item criar(
      @RequestBody Edicao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.criar(
        request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PutMapping("/{id}")
  @PreAuthorize(MUTACAO)
  public Item atualizar(
      @PathVariable UUID id,
      @RequestBody Edicao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.atualizar(
        id, request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/publicar")
  @PreAuthorize(MUTACAO)
  public Item publicar(
      @PathVariable UUID id,
      @RequestBody Versao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.publicar(
        id, request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/retirar")
  @PreAuthorize(MUTACAO)
  public Item retirar(
      @PathVariable UUID id,
      @RequestBody Versao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.retirar(
        id, request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/arquivar")
  @PreAuthorize(MUTACAO)
  public Item arquivar(
      @PathVariable UUID id,
      @RequestBody Versao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.arquivar(
        id, request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PatchMapping("/{id}/ordem")
  @PreAuthorize(MUTACAO)
  public Item reordenar(
      @PathVariable UUID id,
      @RequestBody Ordem request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.reordenar(
        id, request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  private UUID atorId(AdminUserPrincipal ator) {
    return ator == null ? null : ator.usuarioId();
  }
}
