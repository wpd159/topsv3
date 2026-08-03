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
                .contains("pausarMeuAnuncio")
                .contains("reativarMeuAnuncio")
                .contains("removerMeuAnuncio")
                .contains("acoesPermitidas")
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
        String cicloVida = Files.readString(FRONTEND.resolve(
                Path.of("components", "anuncios", "meu-anuncio-acoes-ciclo-vida.tsx")));
        String localidades = Files.readString(FRONTEND.resolve(
                Path.of("hooks", "useLocalidades.ts")));
        String storage = Files.readString(FRONTEND.resolve(
                Path.of("features", "anuncio-wizard", "wizard-storage.ts")));
        String wizardStore = Files.readString(FRONTEND.resolve(
                Path.of("features", "anuncio-wizard", "use-anuncio-wizard-store.ts")));
        String storyDialog = Files.readString(FRONTEND.resolve(
                Path.of("components", "stories", "story-create-dialog.tsx")));
        String storySelector = Files.readString(FRONTEND.resolve(
                Path.of("components", "stories", "story-anuncio-selector-dialog.tsx")));

        assertThat(listagem)
                .contains("listarMeusAnuncios")
                .contains("Você ainda não possui anúncios")
                .contains("import { StoryCreateDialog } from '@/components/stories/story-create-dialog'")
                .contains("import { StoryAnuncioSelectorDialog } from '@/components/stories/story-anuncio-selector-dialog'")
                .contains("const [storyDialogOpen, setStoryDialogOpen] = useState(false)")
                .contains("const [storyTargetId, setStoryTargetId] = useState<string | null>(null)")
                .contains("onClick={(event) => abrirStoryGlobal(event.currentTarget)}")
                .contains("onStoryOpen={abrirStory}")
                .contains("<StoryAnuncioSelectorDialog")
                .contains("<StoryCreateDialog")
                .doesNotContain("fetch(")
                .doesNotContain("/anuncios/meus")
                .doesNotContain("/monetizar")
                .doesNotContain("features/catalogo");
        assertThat(contarOcorrencias(listagem, "<StoryCreateDialog")).isEqualTo(1);
        assertThat(contarOcorrencias(listagem, "<StoryAnuncioSelectorDialog")).isEqualTo(1);
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
                .contains("enviarMinhasMidiasEmLote")
                .contains("<WizardStepFotos")
                .contains("readOnly={isEdit}")
                .contains("disabled={publishing}")
                .doesNotContain("fetch(")
                .doesNotContain("document.body.style.overflow");
        assertThat(localidades)
                .contains("listarCatalogoCompletoLocalidades")
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
                .contains("onStoryOpen: (anuncioId: string, trigger: HTMLButtonElement) => void")
                .contains("onStoryOpen(anuncio.id, event.currentTarget)")
                .contains("getStoryEntryState(anuncio)")
                .doesNotContain("ImpulsionarModal")
                .doesNotContain("Adicionar story")
                .doesNotContain("StoryCreateDialog")
                .doesNotContain("StoryAnuncioSelectorDialog")
                .doesNotContain("publicarMeuAnuncioStory")
                .doesNotContain("comprarBeneficios")
                .doesNotContain("storyDialogOpen")
                .doesNotContain("fetch(")
                .doesNotContain("\"/stories")
                .doesNotContain("'/stories")
                .doesNotContain("/minha-conta/anuncios/");
        assertThat(storySelector)
                .contains("onSelect: (anuncio: MeuAnuncio) => void")
                .contains("getStoryEntryState(anuncio)")
                .doesNotContain("StoryCreateDialog")
                .doesNotContain("publicarMeuAnuncioStory")
                .doesNotContain("comprarBeneficios")
                .doesNotContain("fetch(");
        assertThat(storyDialog)
                .contains("from '@/lib/meus-anuncios-api'")
                .contains("ativarMeuAnuncioStory")
                .contains("publicarMeuAnuncioStory")
                .doesNotContain("from '@/features/monetizacao-wizard/api'")
                .doesNotContain("comprarBeneficios")
                .doesNotContain("fetch(")
                .doesNotContain("XMLHttpRequest")
                .doesNotContain("'/api/")
                .doesNotContain("\"/api/");
        assertThat(cicloVida)
                .contains("acoesPermitidas.pausar")
                .contains("acoesPermitidas.reativar")
                .contains("acoesPermitidas.remover")
                .contains("setConfirmacao('PAUSAR')")
                .contains("setConfirmacao('REMOVER')")
                .contains("emExecucao.current")
                .contains("disabled={processando !== null}")
                .contains("onSuccess(resultado, acao)")
                .doesNotContain("usuarioId")
                .doesNotContain("email");
    }

    private static int contarOcorrencias(String conteudo, String trecho) {
        int quantidade = 0;
        int inicio = 0;
        while ((inicio = conteudo.indexOf(trecho, inicio)) >= 0) {
            quantidade++;
            inicio += trecho.length();
        }
        return quantidade;
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
