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
                .contains("parseVisualizacoesCanonicas")
                .contains("/midias")
                .contains("XMLHttpRequest")
                .contains("Idempotency-Key")
                .contains("WeakMap<File, string>")
                .contains("method: 'PATCH'")
                .contains("XSRF")
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
        String wizard = Files.readString(FRONTEND.resolve(
                Path.of("features", "anuncio-wizard", "anuncio-wizard.tsx")));
        String criacao = Files.readString(FRONTEND.resolve(
                Path.of("app", "(private-routes)", "anunciar", "wizard", "page.tsx")));
        String card = Files.readString(FRONTEND.resolve(
                Path.of("components", "anuncios", "meu-anuncio-card.tsx")));
        String localidades = Files.readString(FRONTEND.resolve(
                Path.of("hooks", "useLocalidades.ts")));
        String storage = Files.readString(FRONTEND.resolve(
                Path.of("features", "anuncio-wizard", "wizard-storage.ts")));
        String wizardStore = Files.readString(FRONTEND.resolve(
                Path.of("features", "anuncio-wizard", "use-anuncio-wizard-store.ts")));

        assertThat(listagem)
                .contains("listarMeusAnuncios")
                .contains("Você ainda não possui anúncios")
                .doesNotContain("fetch(")
                .doesNotContain("StoryCreateDialog")
                .doesNotContain("features/catalogo");
        assertThat(detalhe)
                .contains("buscarMeuAnuncio")
                .contains("formatarVisualizacoesCanonicas(anuncio.visualizacoes)")
                .contains("error.status === 403")
                .contains("error.status === 404")
                .doesNotContain("fetch(");
        assertThat(edicao)
                .contains("AnuncioWizard")
                .contains("mode=\"edit\"")
                .contains("slug={slug}")
                .doesNotContain("MeuAnuncioEditor");
        assertThat(criacao)
                .contains("<AnuncioWizard />")
                .doesNotContain("mode=\"edit\"");
        assertThat(wizard)
                .contains("buscarMeuAnuncio")
                .contains("atualizarMeuAnuncio")
                .contains("mode?: 'create' | 'edit'")
                .contains("backendFirst: isEdit")
                .contains("hydrateFromBackend")
                .contains("clearCurrentCache")
                .contains("consultarLimitesMinhasMidias")
                .contains("enviarMinhaMidia")
                .contains("<WizardStepFotos")
                .contains("readOnly={isEdit}")
                .contains("disabled={publishing}")
                .doesNotContain("fetch(")
                .doesNotContain("document.body.style.overflow");
        assertThat(localidades)
                .contains("descobrirLocalidadesPublicas")
                .doesNotContain("fetch(")
                .doesNotContain("/localidades/estados")
                .doesNotContain("mode === 'create'");
        assertThat(storage)
                .contains("userId: string")
                .contains("mode: 'create' | 'edit'")
                .contains("scope.slug")
                .contains("sourceVersion")
                .contains("removeItem(UNSAFE_LEGACY_STORAGE_KEY)")
                .doesNotContain("getItem(UNSAFE_LEGACY_STORAGE_KEY)");
        assertThat(wizardStore)
                .contains("cached?.sourceVersion === sourceVersion")
                .contains("clearWizardCache(cacheScope)")
                .contains("backendFirst");
        assertThat(detalhe)
                .doesNotContain("Os campos de edição serão integrados em uma fase própria")
                .doesNotContain("modoEdicao");
        assertThat(card)
                .contains("/editar")
                .contains("Detalhes")
                .contains("formatarVisualizacoesCanonicas(anuncio.visualizacoes)")
                .doesNotContain("Excluir")
                .doesNotContain("ImpulsionarModal")
                .doesNotContain("Adicionar story");
    }

    @Test
    void editorHerdadoAtivoFoiRemovidoSemAfetarContratoDeTiposFuturo() {
        assertThat(FRONTEND.resolve(Path.of("components", "anuncios", "meu-anuncio-editor.tsx")))
                .doesNotExist();
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
