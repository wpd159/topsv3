package br.com.topsdojob.v3.web.publico.favorito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.favorito.FavoritosPublicosService;
import br.com.topsdojob.v3.application.publico.favorito.dto.FavoritoEstadoDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class FavoritosPublicosControllerTest {

    @Test
    void expoeSomenteGetPutDeleteCanonicosComSessaoDelegada() {
        FavoritosPublicosService service = mock(FavoritosPublicosService.class);
        Authentication authentication = mock(Authentication.class);
        when(service.listar(authentication)).thenReturn(List.of());
        when(service.incluir("perfil-publico", authentication))
                .thenReturn(new FavoritoEstadoDto("perfil-publico", true));
        when(service.remover("perfil-publico", authentication))
                .thenReturn(new FavoritoEstadoDto("perfil-publico", false));
        FavoritosPublicosController controller = new FavoritosPublicosController(service);

        assertThat(controller.listar(authentication)).isEmpty();
        assertThat(controller.incluir("perfil-publico", authentication).favorito()).isTrue();
        assertThat(controller.remover("perfil-publico", authentication).favorito()).isFalse();
        verify(service).listar(authentication);
        verify(service).incluir("perfil-publico", authentication);
        verify(service).remover("perfil-publico", authentication);
    }
}
