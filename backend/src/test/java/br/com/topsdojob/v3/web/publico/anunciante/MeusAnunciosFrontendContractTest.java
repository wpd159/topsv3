package br.com.topsdojob.v3.web.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MeusAnunciosFrontendContractTest {

    private static final Path FRONTEND = Path.of("..", "frontend", "src");

    @Test
    void adapterUnicoUsaContratoV3SemFallbackSilencioso() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "meus-anuncios-api.ts")));

        assertThat(adapter)
                .contains("'/minha-conta/anuncios'")
                .contains("credentials: 'include'")
                .contains("throw new MeusAnunciosApiError")
                .doesNotContain("/anuncios/meus")
                .doesNotContain("return []");
    }

    @Test
    void listagemDetalheEEntradaDeEdicaoUsamSomenteAdapterV3() throws Exception {
        String listagem = Files.readString(FRONTEND.resolve(
                Path.of("app", "(private-routes)", "meus-anuncios", "page.tsx")));
        String detalhe = Files.readString(FRONTEND.resolve(
                Path.of("components", "anuncios", "meu-anuncio-detalhe-view.tsx")));
        String edicao = Files.readString(FRONTEND.resolve(
                Path.of("app", "(private-routes)", "meus-anuncios", "[slug]", "editar", "page.tsx")));
        String card = Files.readString(FRONTEND.resolve(
                Path.of("components", "anuncios", "meu-anuncio-card.tsx")));

        assertThat(listagem)
                .contains("listarMeusAnuncios")
                .contains("Você ainda não possui anúncios")
                .doesNotContain("fetch(")
                .doesNotContain("StoryCreateDialog")
                .doesNotContain("features/catalogo");
        assertThat(detalhe)
                .contains("buscarMeuAnuncio")
                .contains("error.status === 403")
                .contains("error.status === 404")
                .doesNotContain("fetch(");
        assertThat(edicao)
                .contains("MeuAnuncioDetalheView")
                .doesNotContain("EditarAnuncioWizard");
        assertThat(card)
                .contains("/editar")
                .contains("Detalhes")
                .doesNotContain("Excluir")
                .doesNotContain("ImpulsionarModal")
                .doesNotContain("Adicionar story");
    }

    @Test
    void editorHerdadoAtivoFoiRemovidoSemAfetarContratoDeTiposFuturo() {
        assertThat(FRONTEND.resolve(Path.of("features", "anuncio-wizard", "editar-anuncio-wizard.tsx")))
                .doesNotExist();
        assertThat(FRONTEND.resolve(Path.of("hooks", "useAnuncioEdit.ts")))
                .doesNotExist();
        assertThat(FRONTEND.resolve(Path.of("utils", "anuncio-formdata.ts")))
                .doesNotExist();
        assertThat(FRONTEND.resolve(Path.of("components", "anuncios", "editar", "types.ts")))
                .exists();
    }
}
