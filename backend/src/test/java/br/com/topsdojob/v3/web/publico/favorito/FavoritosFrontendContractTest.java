package br.com.topsdojob.v3.web.publico.favorito;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FavoritosFrontendContractTest {

    private static final Path FRONTEND = Path.of("..", "frontend", "src");

    @Test
    void usaUmUnicoAdapterEContratoPorSlugSemEstadoLegado() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "favoritos-api.ts")));
        String provider = Files.readString(FRONTEND.resolve(Path.of("context", "FavoritosContext.tsx")));
        String card = Files.readString(FRONTEND.resolve(Path.of("components", "anuncios", "anuncio-card.tsx")));
        String pagina = Files.readString(FRONTEND.resolve(
                Path.of("app", "(private-routes)", "favoritos", "page.tsx")));

        assertThat(adapter)
                .contains("'/minha-conta/favoritos'")
                .contains("method: 'PUT'")
                .contains("method: 'DELETE'")
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("usuarioId");
        assertThat(provider)
                .contains("new Set(favoritos.map((item) => item.slug))")
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage");
        assertThat(card)
                .contains("<FavoritoButton slug={slugRota}")
                .doesNotContain("/favoritar")
                .doesNotContain("favoritoInicial")
                .doesNotContain("usuarioId");
        assertThat(pagina)
                .contains("useFavoritos()")
                .doesNotContain("/anuncios/favoritos")
                .doesNotContain("catch (err) {}")
                .doesNotContain("useState<Anuncio");
    }
}
