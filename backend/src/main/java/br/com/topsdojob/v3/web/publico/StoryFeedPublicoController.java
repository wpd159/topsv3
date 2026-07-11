package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.StoryFeedBundleDto;
import br.com.topsdojob.v3.application.publico.dto.StoryViewerPublicoDto;
import br.com.topsdojob.v3.application.publico.service.StoryFeedPublicoService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/stories")
public class StoryFeedPublicoController {

    private final StoryFeedPublicoService service;

    public StoryFeedPublicoController(StoryFeedPublicoService service) {
        this.service = service;
    }

    @GetMapping("/ativos")
    public List<StoryFeedBundleDto> listar(HttpServletRequest request) {
        return service.listar(request);
    }

    @GetMapping("/{storyId}")
    public StoryViewerPublicoDto buscar(@PathVariable String storyId, HttpServletRequest request) {
        return service.buscar(storyId, request);
    }
}
