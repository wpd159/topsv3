package br.com.topsdojob.v3.web.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioCicloVidaService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.MinhasMidiasService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioStoryService;
import br.com.topsdojob.v3.application.publico.anunciante.MeuAnuncioStoryOfertaService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioCicloVidaDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaLimitesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiasResponseDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.ReordenarMinhasMidiasRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryOfertaDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/minha-conta/anuncios")
public class MeusAnunciosController {

    private final MeusAnunciosConsultaService consultaService;
    private final MeuAnuncioAtualizacaoService atualizacaoService;
    private final MeuAnuncioCicloVidaService cicloVidaService;
    private final MinhasMidiasService midiasService;
    private final MeuAnuncioStoryService storyService;
    private final MeuAnuncioStoryOfertaService storyOfertaService;

    public MeusAnunciosController(
            MeusAnunciosConsultaService consultaService,
            MeuAnuncioAtualizacaoService atualizacaoService,
            MeuAnuncioCicloVidaService cicloVidaService,
            MinhasMidiasService midiasService,
            MeuAnuncioStoryService storyService,
            MeuAnuncioStoryOfertaService storyOfertaService) {
        this.consultaService = consultaService;
        this.atualizacaoService = atualizacaoService;
        this.cicloVidaService = cicloVidaService;
        this.midiasService = midiasService;
        this.storyService = storyService;
        this.storyOfertaService = storyOfertaService;
    }

    @GetMapping
    public List<MeuAnuncioDto> listar(Authentication authentication) {
        return consultaService.listar(authentication);
    }

    @GetMapping("/{slug}")
    public MeuAnuncioDto detalhar(@PathVariable String slug, Authentication authentication) {
        return consultaService.detalhar(slug, authentication);
    }

    @PatchMapping("/{slug}")
    public MeuAnuncioDto atualizar(
            @PathVariable String slug,
            @RequestBody MeuAnuncioAtualizacaoRequestDto request,
            Authentication authentication) {
        return atualizacaoService.atualizar(slug, request, authentication);
    }

    @PostMapping("/{slug}/pausar")
    public MeuAnuncioCicloVidaDto pausar(
            @PathVariable String slug,
            Authentication authentication,
            HttpServletRequest request) {
        return cicloVidaService.pausar(slug, authentication, RequestIdContext.current(request));
    }

    @PostMapping("/{slug}/reativar")
    public MeuAnuncioCicloVidaDto reativar(
            @PathVariable String slug,
            Authentication authentication,
            HttpServletRequest request) {
        return cicloVidaService.reativar(slug, authentication, RequestIdContext.current(request));
    }

    @DeleteMapping("/{slug}")
    public MeuAnuncioCicloVidaDto remover(
            @PathVariable String slug,
            Authentication authentication,
            HttpServletRequest request) {
        return cicloVidaService.remover(slug, authentication, RequestIdContext.current(request));
    }

    @GetMapping("/{slug}/midias")
    public MeuAnuncioMidiasResponseDto listarMidias(
            @PathVariable String slug,
            Authentication authentication) {
        return midiasService.listar(slug, authentication);
    }

    @GetMapping("/{slug}/midias/limites")
    public MeuAnuncioMidiaLimitesDto consultarLimitesMidias(
            @PathVariable String slug,
            Authentication authentication) {
        return midiasService.limites(slug, authentication);
    }

    @PostMapping(path = "/{slug}/midias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeuAnuncioMidiasResponseDto enviarMidia(
            @PathVariable String slug,
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return midiasService.enviar(slug, arquivo, idempotencyKey, authentication);
    }

    @PostMapping(path = "/{slug}/midias/lote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeuAnuncioMidiasResponseDto enviarMidiasEmLote(
            @PathVariable String slug,
            @RequestPart("arquivos") List<MultipartFile> arquivos,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        return midiasService.enviarLote(slug, arquivos, idempotencyKey, authentication);
    }

    @PatchMapping("/{slug}/midias/ordem")
    public MeuAnuncioMidiasResponseDto reordenarMidias(
            @PathVariable String slug,
            @RequestBody ReordenarMinhasMidiasRequestDto request,
            Authentication authentication) {
        return midiasService.reordenar(slug, request, authentication);
    }

    @DeleteMapping("/{slug}/midias/{midiaId}")
    public MeuAnuncioMidiasResponseDto removerMidia(
            @PathVariable String slug,
            @PathVariable UUID midiaId,
            Authentication authentication) {
        return midiasService.remover(slug, midiaId, authentication);
    }

    @PostMapping(path = "/{slug}/stories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeuAnuncioStoryDto publicarStory(
            @PathVariable String slug,
            @RequestPart("modoConteudo") String modoConteudo,
            @RequestPart(value = "arquivo", required = false) List<MultipartFile> arquivos,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        validarContratoMultipartStory(request);
        return storyService.publicar(
                slug,
                modoConteudo,
                arquivos,
                idempotencyKey,
                authentication,
                RequestIdContext.current(request));
    }

    @GetMapping("/{slug}/stories/oferta")
    public ResponseEntity<MeuAnuncioStoryOfertaDto> consultarOfertaStory(
            @PathVariable String slug,
            Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(storyOfertaService.consultar(slug, authentication));
    }

    private void validarContratoMultipartStory(HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parametros de Story nao permitidos");
        }
        Set<String> permitidos = Set.of("modoConteudo", "arquivo");
        try {
            boolean desconhecido = request.getParts().stream()
                    .map(part -> part.getName())
                    .anyMatch(nome -> !permitidos.contains(nome));
            if (desconhecido) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "campo nao permitido no contrato de Story");
            }
        } catch (IOException | ServletException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "multipart de Story invalido");
        }
    }
}
