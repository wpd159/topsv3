package br.com.topsdojob.v3.web.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.MeusStoriesConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesDireitoService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhaContaStoriesPublicacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.StoryEncerramentoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeusStoriesPaginaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryAtivacaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryAtivacaoRequest;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryOfertaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.StoryEncerramentoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/minha-conta/stories")
public class MinhaContaStoriesController {

  private final MeusStoriesConsultaService consultaService;
  private final MinhaContaStoriesPublicacaoService publicacaoService;
  private final MinhaContaStoriesDireitoService direitoService;
  private final StoryEncerramentoService encerramentoService;

  public MinhaContaStoriesController(
      MeusStoriesConsultaService consultaService,
      MinhaContaStoriesPublicacaoService publicacaoService,
      MinhaContaStoriesDireitoService direitoService,
      StoryEncerramentoService encerramentoService) {
    this.consultaService = consultaService;
    this.publicacaoService = publicacaoService;
    this.direitoService = direitoService;
    this.encerramentoService = encerramentoService;
  }

  @GetMapping
  public ResponseEntity<MeusStoriesPaginaDto> listar(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return semCache(consultaService.listar(page, size, authentication));
  }

  @GetMapping("/oferta")
  public ResponseEntity<MinhaContaStoryOfertaDto> consultarOferta(
      @RequestParam String modoConteudo,
      @RequestParam(required = false) UUID anuncioId,
      Authentication authentication,
      HttpServletRequest request) {
    validarParametros(request, Set.of("modoConteudo", "anuncioId"));
    return semCache(direitoService.consultar(modoConteudo, anuncioId, authentication));
  }

  @PostMapping("/ativacoes")
  public ResponseEntity<MinhaContaStoryAtivacaoDto> ativar(
      @RequestBody MinhaContaStoryAtivacaoRequest body,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication,
      HttpServletRequest request) {
    return semCache(direitoService.ativar(
        body,
        idempotencyKey,
        authentication,
        RequestIdContext.current(request)));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<MinhaContaStoryDto> publicar(
      @RequestPart("modoConteudo") String modoConteudo,
      @RequestPart(value = "anuncioId", required = false) String anuncioId,
      @RequestPart(value = "arquivo", required = false) List<MultipartFile> arquivos,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication,
      HttpServletRequest request) {
    validarMultipart(request);
    return semCache(publicacaoService.publicar(
        modoConteudo,
        uuidOpcional(anuncioId),
        arquivos,
        idempotencyKey,
        authentication,
        RequestIdContext.current(request)));
  }

  @DeleteMapping("/{storyId}")
  public ResponseEntity<StoryEncerramentoDto> encerrar(
      @PathVariable UUID storyId,
      Authentication authentication,
      HttpServletRequest request) {
    return semCache(encerramentoService.encerrarProprio(
        storyId,
        authentication,
        RequestIdContext.current(request)));
  }

  private void validarMultipart(HttpServletRequest request) {
    if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parametros de Story nao permitidos");
    }
    Set<String> permitidos = Set.of("modoConteudo", "anuncioId", "arquivo");
    try {
      var partes = request.getParts();
      boolean desconhecido = partes.stream()
          .map(part -> part.getName())
          .anyMatch(nome -> !permitidos.contains(nome));
      long modos = partes.stream().filter(part -> "modoConteudo".equals(part.getName())).count();
      long anuncios = partes.stream().filter(part -> "anuncioId".equals(part.getName())).count();
      long arquivos = partes.stream().filter(part -> "arquivo".equals(part.getName())).count();
      if (desconhecido || modos != 1 || anuncios > 1 || arquivos > 1) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "multipart de Story invalido");
      }
    } catch (IOException | ServletException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "multipart de Story invalido");
    }
  }

  private void validarParametros(HttpServletRequest request, Set<String> permitidos) {
    boolean desconhecido = request.getParameterMap().keySet().stream()
        .anyMatch(nome -> !permitidos.contains(nome));
    if (desconhecido) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parametro de Story nao permitido");
    }
  }

  private UUID uuidOpcional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "anuncioId invalido");
    }
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
