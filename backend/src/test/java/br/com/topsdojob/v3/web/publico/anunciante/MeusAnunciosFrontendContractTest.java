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

        assertThat(listagem)
                .contains("listarMeusAnuncios")
                .contains("Você ainda não possui anúncios")
                .contains("import { StoryCreateDialog } from '@/components/stories/story-create-dialog'")
                .contains("const [storyTargetId, setStoryTargetId] = useState<string | null>(null)")
                .contains("onStoryOpen={abrirStory}")
                .contains("onClick={(event) => abrirStoryGlobal(event.currentTarget)}")
                .doesNotContain("fetch(")
                .doesNotContain("/anuncios/meus")
                .doesNotContain("/monetizar")
                .doesNotContain("features/catalogo");
        assertThat(contarOcorrencias(listagem, "<StoryCreateDialog")).isEqualTo(1);
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
                .doesNotContain("comprarBeneficios")
                .doesNotContain("storyDialogOpen")
                .doesNotContain("fetch(")
                .doesNotContain("\"/stories")
                .doesNotContain("'/stories")
                .doesNotContain("/minha-conta/anuncios/");
        assertThat(storyDialog)
                .contains("from '@/lib/meus-anuncios-api'")
                .contains("from '@/lib/minha-conta-stories-api'")
                .contains("consultarMinhaContaStoryOferta")
                .contains("ativarMinhaContaStory")
                .contains("publicarMinhaContaStory")
                .contains("chooseMode('MIDIA_UPLOAD')")
                .contains("chooseMode('ANUNCIO')")
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

    @Test
    void doisUploadsXhrPreservamEnvelopeIdempotenciaEArquivoAposFalha() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "meus-anuncios-api.ts")));
        String unitario = recorte(
                adapter,
                "export async function enviarMinhaMidia(",
                "const mediaBatchIdempotencyKeys");
        String lote = recorte(
                adapter,
                "export async function enviarMinhasMidiasEmLote(",
                "export function reordenarMinhasMidias");
        String fotos = Files.readString(FRONTEND.resolve(Path.of(
                "features", "anuncio-wizard", "components", "wizard-step-fotos.tsx")));
        String uploadPersistido = recorte(
                fotos,
                "const uploadPersisted = async (files: File[]) => {",
                "const pendingPersistedPhotos");

        assertThat(adapter)
                .contains("type MeusAnunciosErrorEnvelope")
                .contains("message?: unknown")
                .contains("code?: unknown")
                .contains("requestId?: unknown")
                .contains("nonBlankString(envelope?.code)")
                .contains("nonBlankString(envelope?.requestId) || xhr.getResponseHeader('X-Request-Id')")
                .contains("xhr.status === 415 && unsupportedPhotoUpload")
                .contains("resolveUnsupportedPhotoUploadMessage(candidateMessage)");
        assertThat(unitario)
                .contains("parseErrorEnvelope(xhr.responseText)")
                .contains("const unsupportedPhotoUpload = containsOnlyPhotoUploads([arquivo])")
                .contains("mediaUploadIdempotencyKey(arquivo)")
                .contains("mediaUploadIdempotencyKeys.delete(arquivo)")
                .contains("form.append('arquivo', arquivo)")
                .doesNotContain("setRequestHeader('Content-Type'");
        assertThat(unitario.indexOf("mediaUploadIdempotencyKeys.delete(arquivo)"))
                .isGreaterThan(unitario.indexOf("if (xhr.status < 200 || xhr.status >= 300)"));
        assertThat(lote)
                .contains("parseErrorEnvelope(xhr.responseText)")
                .contains("const unsupportedPhotoUpload = containsOnlyPhotoUploads(arquivos)")
                .contains("mediaBatchIdempotencyKeys.set(signature, idempotencyKey)")
                .contains("mediaBatchIdempotencyKeys.delete(signature)")
                .contains("arquivos.forEach((arquivo) => form.append('arquivos', arquivo))")
                .doesNotContain("setRequestHeader('Content-Type'");
        assertThat(lote.indexOf("mediaBatchIdempotencyKeys.delete(signature)"))
                .isGreaterThan(lote.indexOf("if (xhr.status < 200 || xhr.status >= 300)"));

        assertThat(uploadPersistido)
                .contains("setPendingPersistedFiles([])")
                .contains("meusAnunciosErrorMessage(error, 'Falha ao enviar os arquivos.')");
        String falha = recorte(uploadPersistido, "} catch (error) {", "} finally {");
        assertThat(falha).doesNotContain("setPendingPersistedFiles([])");
        assertThat(fotos)
                .contains("setPendingPersistedFiles(files)")
                .contains("pendingPersistedFiles.filter")
                .contains("errors.lote && pendingPersistedFiles.length")
                .contains("uploadPersisted(pendingPersistedFiles)")
                .contains("Tentar enviar novamente");
    }

    @Test
    void fallback415PublicoDistingueFotosDeVideoMistoEConteudoDesconhecido() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "meus-anuncios-api.ts")));
        String contrato = Files.readString(FRONTEND.resolve(Path.of("lib", "api-contract.ts")));
        String documentos = Files.readString(FRONTEND.resolve(Path.of(
                "features", "admin-documentos", "api.ts")));
        String kyc = Files.readString(FRONTEND.resolve(Path.of(
                "features", "anuncio-wizard", "api.ts")));
        String classificacao = recorte(
                adapter,
                "const PHOTO_UPLOAD_EXTENSIONS",
                "function uploadErrorFromXhr(");

        assertThat(contrato)
                .contains("unsupportedPhotoUpload?: boolean")
                .contains("options.unsupportedPhotoUpload")
                .contains("? resolveUnsupportedPhotoUploadMessage(serverMessage)")
                .contains(": message('Não foi possível processar o arquivo enviado.')");
        assertThat(classificacao)
                .contains("new Set(['jpg', 'jpeg', 'png', 'webp'])")
                .contains("file.type.trim().toLowerCase()")
                .contains("if (mimeType) return mimeType.startsWith('image/')")
                .contains("file.name.trim().toLowerCase()")
                .contains("files.length > 0 && files.every(isPhotoUploadFile)");
        assertThat(adapter)
                .contains("const message = xhr.status === 415 && unsupportedPhotoUpload")
                .contains("candidateMessage || fallback");
        assertThat(documentos).doesNotContain("unsupportedPhotoUpload");
        assertThat(kyc).doesNotContain("unsupportedPhotoUpload");
    }

    @Test
    void remocaoDeMidiaExibeMensagemCodigoERequestIdDoEnvelope() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "meus-anuncios-api.ts")));
        String fotos = Files.readString(FRONTEND.resolve(Path.of(
                "features", "anuncio-wizard", "components", "wizard-step-fotos.tsx")));
        String remocao = recorte(
                fotos,
                "const remove = async (midia: MinhaMidiaGestao) => {",
                "if (!slug) {");

        assertThat(adapter)
                .contains("error.code ? `Código: ${error.code}` : null")
                .contains("error.requestId ? `Request ID: ${error.requestId}` : null");
        assertThat(remocao)
                .contains("meusAnunciosErrorMessage(error, 'Não foi possível remover a mídia.')")
                .doesNotContain("error instanceof Error ? error.message");
    }

    private static String recorte(String conteudo, String inicio, String fim) {
        int inicioIndex = conteudo.indexOf(inicio);
        int fimIndex = inicioIndex < 0 ? -1 : conteudo.indexOf(fim, inicioIndex + inicio.length());
        assertThat(inicioIndex).as("inicio do contrato").isGreaterThanOrEqualTo(0);
        assertThat(fimIndex).as("fim do contrato").isGreaterThan(inicioIndex);
        return conteudo.substring(inicioIndex, fimIndex);
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
