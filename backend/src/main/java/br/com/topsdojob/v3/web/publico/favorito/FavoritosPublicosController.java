package br.com.topsdojob.v3.web.publico.favorito;

import br.com.topsdojob.v3.application.publico.favorito.FavoritosPublicosService;
import br.com.topsdojob.v3.application.publico.favorito.dto.FavoritoEstadoDto;
import br.com.topsdojob.v3.application.publico.favorito.dto.FavoritoPublicoDto;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/minha-conta/favoritos")
public class FavoritosPublicosController {

    private final FavoritosPublicosService service;

    public FavoritosPublicosController(FavoritosPublicosService service) {
        this.service = service;
    }

    @GetMapping
    public List<FavoritoPublicoDto> listar(Authentication authentication) {
        return service.listar(authentication);
    }

    @PutMapping("/{slug}")
    public FavoritoEstadoDto incluir(@PathVariable String slug, Authentication authentication) {
        return service.incluir(slug, authentication);
    }

    @DeleteMapping("/{slug}")
    public FavoritoEstadoDto remover(@PathVariable String slug, Authentication authentication) {
        return service.remover(slug, authentication);
    }
}
