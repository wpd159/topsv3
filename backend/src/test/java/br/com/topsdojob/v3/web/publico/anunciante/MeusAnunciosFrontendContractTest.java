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
                .contains("recoverWizardAnuncio")
                .contains("saveWizardCreationOperation")
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
                "export async function reordenarMinhasMidias");
        String fotos = Files.readString(FRONTEND.resolve(Path.of(
                "features", "anuncio-wizard", "components", "wizard-step-fotos.tsx")));
        String uploadPersistido = recorte(
                fotos,
                "const uploadPersisted = async (entries: EditPendingMedia[], kind: 'photo' | 'video' = 'photo', automatic = false) => {",
                "const pendingPersistedPhotos");
        String atualizarSelecao = recorte(fotos, "function updatePendingFiles(files: EditPendingMedia[]) {",
                "function selectPersistedFiles(");
        String selecionarERemover = recorte(fotos, "function selectPersistedFiles(", "const move = async");
        String validarSelecao = recorte(fotos, "useEffect(() => {", "const refresh = useCallback");
        String aceitarResposta = recorte(fotos, "function acceptResponse(", "function handleUnconfirmedState(");
        String assinaturaLote = recorte(adapter,
                "function mediaBatchSignature(files: File[], slug: string, accountScope: string) {",
                "export async function enviarMinhasMidiasEmLote(");
        String wizard = Files.readString(FRONTEND.resolve(Path.of(
                "features", "anuncio-wizard", "anuncio-wizard.tsx")));
        String fluxoFinal = recorte(wizard, "const runFinalFlow = async () => {", "const requestPublish = () => {");
        String criarAnuncio = recorte(wizard, "const submitAnuncio = async (generation: number) => {", "const ensureKycReady = async () => {");
        String validador = Files.readString(FRONTEND.resolve(Path.of("lib", "photo-upload-validation.ts")));

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
                .contains("await validateMediaUploadPhotos([arquivo])")
                .contains("parseErrorEnvelope(xhr.responseText)")
                .contains("const unsupportedPhotoUpload = containsOnlyPhotoUploads([arquivo])")
                .contains("mediaUploadIdempotencyKey(arquivo)")
                .contains("mediaUploadIdempotencyKeys.delete(arquivo)")
                .contains("form.append('arquivo', arquivo)")
                .doesNotContain("setRequestHeader('Content-Type'");
        assertThat(unitario.indexOf("mediaUploadIdempotencyKeys.delete(arquivo)"))
                .isGreaterThan(unitario.indexOf("if (xhr.status < 200 || xhr.status >= 300)"));
        apareceAntes(unitario, "await validateMediaUploadPhotos([arquivo])", "const csrfValue =");
        apareceAntes(unitario, "await validateMediaUploadPhotos([arquivo])", "const xhr = new XMLHttpRequest()");
        apareceAntes(unitario, "const result = mapMinhasMidias(body, slug)", "mediaUploadIdempotencyKeys.delete(arquivo)");
        assertThat(lote)
                .contains("await validateMediaUploadPhotos(arquivos)")
                .contains("const selectedVideos = arquivos.filter(isSupportedUploadVideo)")
                .contains("if (context && !context.isCurrent()) throw contextChanged()")
                .contains("[session, current] = await Promise.all([getPublicSession(), listarMinhasMidias(slug)])")
                .contains("String(session.id) !== context.actorId")
                .contains("current.anuncio.id !== expectedAnuncioId")
                .contains("selectedVideos.some((file) => file.size === 0 || file.size > maxVideoBytes)")
                .contains("!context.allowUnconfirmedRetry && (!current.limites.videoAtivo || current.limites.videosDisponiveis < selectedVideos.length)")
                .contains("parseErrorEnvelope(xhr.responseText)")
                .contains("const unsupportedPhotoUpload = containsOnlyPhotoUploads(arquivos)")
                .contains("const signature = mediaBatchSignature(arquivos, slug, accountScope)")
                .contains("mediaBatchIdempotencyKeys.set(signature, idempotencyKey)")
                .contains("mediaBatchIdempotencyKeys.delete(signature)")
                .contains("const idempotencyKey = mediaBatchIdempotencyKeys.get(signature) || crypto.randomUUID()")
                .contains("xhr.setRequestHeader('Idempotency-Key', idempotencyKey)")
                .contains("if (context && selectedVideos.length && !result.midias.some((item) => item.tipo === 'VIDEO'))")
                .contains("if (context && !context.isCurrent()) {")
                .contains("reject(contextChanged())")
                .contains("arquivos.forEach((arquivo) => form.append('arquivos', arquivo))")
                .doesNotContain("setRequestHeader('Content-Type'");
        assertThat(lote.indexOf("mediaBatchIdempotencyKeys.delete(signature)"))
                .isGreaterThan(lote.indexOf("if (xhr.status < 200 || xhr.status >= 300)"));
        apareceAntes(lote, "await validateMediaUploadPhotos(arquivos)", "csrfValue = readCsrfValue()");
        apareceAntes(lote, "await validateMediaUploadPhotos(arquivos)", "const xhr = new XMLHttpRequest()");
        apareceAntes(lote, "if (context && !context.isCurrent()) throw contextChanged()", "const signature =");
        apareceAntes(lote, "[session, current] = await Promise.all", "const signature =");
        apareceAntes(lote, "const result = mapMinhasMidias(body, slug)", "mediaBatchIdempotencyKeys.delete(signature)");
        apareceAntes(lote, "expectedAnuncioId && result.anuncio.id !== expectedAnuncioId", "mediaBatchIdempotencyKeys.delete(signature)");
        apareceAntes(lote, "expectedAnuncioId && result.anuncio.id !== expectedAnuncioId", "onProgress?.(100)");
        apareceAntes(lote, "if (context && selectedVideos.length && !result.midias.some", "onProgress?.(100)");
        apareceAntes(lote, "onProgress?.(100)", "mediaBatchIdempotencyKeys.delete(signature)");
        assertThat(lote).contains("Math.min(99, Math.round((event.loaded / event.total) * 100))");
        String respostaAindaNaoConfirmada = recorte(lote, "xhr.onerror = () => reject(uploadErrorFromXhr(",
                "const result = mapMinhasMidias(body, slug)");
        assertThat(respostaAindaNaoConfirmada)
                .doesNotContain("onProgress?.(100)", "mediaBatchIdempotencyKeys.delete(signature)");
        String antesDoEnvio = recorte(lote, "const form = new FormData()", "xhr.send(form)");
        assertThat(antesDoEnvio).contains(
                "arquivos.forEach((arquivo) => form.append('arquivos', arquivo))",
                "if (context && !context.isCurrent()) {",
                "reject(contextChanged())",
                "return");
        apareceAntes(antesDoEnvio, "if (context && !context.isCurrent()) {", "reject(contextChanged())");
        assertThat(adapter).contains("const mediaBatchFileIds = new WeakMap<File, string>()");
        assertThat(assinaturaLote)
                .contains("return [accountScope, slug, ...files")
                .contains("mediaBatchFileIds.get(file)")
                .contains("identity = crypto.randomUUID()")
                .contains("mediaBatchFileIds.set(file, identity)")
                .contains("return identity")
                .contains(".join('|')")
                .doesNotContain("file.name", "file.size", "file.lastModified", "file.type");

        assertThat(uploadPersistido)
                .contains("const files = entries.map((entry) => entry.file)")
                .contains("!validationReady")
                .contains("entries.some((entry) => entry.kind !== kind || !pendingFilesRef.current.includes(entry))")
                .contains("entries.some((entry) => !persistedValidation.results[pendingEntries.indexOf(entry)]?.valid)")
                .contains("(kind === 'photo' && errors.lote && !retryable)")
                .contains("(kind === 'video' && errors.video && !videoRetryable)")
                .contains("const version = selectionVersionRef.current")
                .contains("const results = await Promise.all(entries.map((entry) => validateSelectedMedia(entry.file, entry.kind === 'photo', persisted?.limites.maxVideoBytes)))")
                .contains("if (version !== selectionVersionRef.current || entries.some((entry) => !pendingFilesRef.current.includes(entry))")
                .contains("const isCurrent = () => mountedRef.current && generation === operationGenerationRef.current")
                .contains("kind === 'video' ? { actorId: actorId!, isCurrent, allowUnconfirmedRetry: uploadUnconfirmed } : undefined")
                .contains("if (isCurrent() && acceptResponse(latest, generation)) {")
                .contains("updatePendingFiles(pendingFilesRef.current.filter((entry) => !entries.includes(entry)))")
                .contains("meusAnunciosErrorMessage(error, 'Falha ao enviar os arquivos.')");
        apareceAntes(uploadPersistido, "const results = await Promise.all", "if (version !== selectionVersionRef.current");
        apareceAntes(uploadPersistido, "if (version !== selectionVersionRef.current", "await enviarMinhasMidiasEmLote(");
        apareceAntes(uploadPersistido, "await enviarMinhasMidiasEmLote(", "acceptResponse(latest, generation)");
        apareceAntes(uploadPersistido, "if (isCurrent() && acceptResponse(latest, generation)) {",
                "updatePendingFiles(pendingFilesRef.current.filter((entry) => !entries.includes(entry)))");
        assertThat(uploadPersistido).doesNotContain("updatePendingFiles([])");
        assertThat(aceitarResposta)
                .contains("if (!mountedRef.current || generation !== operationGenerationRef.current || terminalRef.current) return false")
                .contains("response.anuncio.slug !== slug")
                .contains("if (response.anuncio.status === 'REMOVIDO') terminalRef.current = true")
                .contains("setPersisted(response)")
                .contains("onPersistedChange?.(response)")
                .contains("return true");
        apareceAntes(aceitarResposta, "generation !== operationGenerationRef.current", "setPersisted(response)");
        apareceAntes(aceitarResposta, "setPersisted(response)", "onPersistedChange?.(response)");
        apareceAntes(aceitarResposta, "onPersistedChange?.(response)", "return true");
        String falha = recorte(uploadPersistido, "} catch (error) {", "} finally {");
        assertThat(falha)
                .contains("const mayRetry = error instanceof TypeError || (error instanceof MeusAnunciosApiError")
                .contains("error.status === 0 || error.status === 408 || error.status === 429 || error.status >= 500")
                .contains("if (kind === 'video') setVideoRetryable(mayRetry)")
                .contains("else setRetryable(mayRetry)")
                .contains("setErrors((current) => ({ ...current, [kind === 'video' ? 'video' : 'lote']: message }))")
                .doesNotContain("setPendingPersistedFiles([])", "updatePendingFiles(", "error.status === 415");
        assertThat(atualizarSelecao)
                .contains("selectionVersionRef.current += 1")
                .contains("pendingFilesRef.current = files")
                .contains("if (onPendingFilesChange) onPendingFilesChange(files)")
                .contains("else setUncontrolledPendingFiles(files)")
                .contains("if (keepVideoError && current.video) next.video = current.video")
                .contains("if (keepPhotoError && current.lote) next.lote = current.lote")
                .contains("setProgress({})")
                .contains("if (!keepPhotoError) setRetryable(false)")
                .contains("if (!keepVideoError) setVideoRetryable(false)")
                .doesNotContain("uploadPersisted(", "enviarMinhasMidiasEmLote(");
        assertThat(selecionarERemover)
                .contains("updatePendingFiles([...pendingFilesRef.current, ...files.map((file) => ({ file, kind: 'photo' as const }))])")
                .contains("pendingFilesRef.current.filter((entry) => entry.kind === 'photo')")
                .contains("files.map((file) => ({ file, kind: 'video' as const }))")
                .contains("function removePendingPersistedFile(entry: EditPendingMedia)")
                .contains("updatePendingFiles(pendingFilesRef.current.filter((item) => item !== entry))")
                .doesNotContain("uploadPersisted(", "enviarMinhasMidiasEmLote(");
        assertThat(validarSelecao)
                .contains("const files = pendingEntries")
                .contains("files.map((entry) => validateSelectedMedia(entry.file, entry.kind === 'photo', persisted?.limites.maxVideoBytes))")
                .contains("if (current) setPersistedValidation({ selection: files, results })")
                .contains("return () => { current = false }");
        assertThat(fotos)
                .contains("pendingFiles?: EditPendingMedia[]")
                .contains("useState<EditPendingMedia[]>([])")
                .contains("const pendingEntries = controlledPendingFiles ?? uncontrolledPendingFiles")
                .contains("const pendingPersistedPhotos = pendingEntries.filter((entry) => entry.kind === 'photo')")
                .contains("const pendingPersistedVideos = pendingEntries.filter((entry) => entry.kind === 'video')")
                .contains("const validationReady = persistedValidation.selection === pendingEntries")
                .contains("pendingPhotoValidation || invalidPhotos || Boolean(errors.lote && !retryable)")
                .contains("onClick={() => void uploadPersisted(pendingPersistedPhotos, 'photo')}")
                .contains("void uploadPersisted([entry], 'video', true)")
                .contains("onClick={() => void uploadPersisted([pendingPersistedVideos[0]], 'video')}")
                .contains("errors.lote && retryable ? 'Tentar enviar fotos novamente' : 'Enviar fotos'")
                .contains("const url = URL.createObjectURL(selectedVideoFile)")
                .contains("return () => URL.revokeObjectURL(url)");
        assertThat(fluxoFinal)
                .contains("if (!isEdit) {")
                .contains("const files = state.fotos")
                .contains("const results = await Promise.all(files.map(validatePhotoUpload))")
                .contains("files.length !== photoSelectionRef.current.length")
                .contains("files.some((file, index) => file !== photoSelectionRef.current[index])")
                .contains("if (changed || files.length > 4 || invalidIndex >= 0 || files.some((file) => serverRejectedPhotos.includes(file)))")
                .contains("throw new Error(result && !result.valid");
        apareceAntes(fluxoFinal, "const results = await Promise.all", "const changed =");
        apareceAntes(fluxoFinal, "throw new Error(result && !result.valid", "await ensureKycReady()");
        apareceAntes(fluxoFinal, "await ensureKycReady()", "else await submitAnuncio(generation)");
        assertThat(criarAnuncio)
                .contains("created = await submitWizardAnuncio(state, operation.sessionId)")
                .contains("recoverWizardAnuncio(operation.sessionId)")
                .contains("updated.id !== createdAnuncioIdRef.current || updated.slug !== targetSlug")
                .contains("progressScope, createdAnuncioIdRef.current ?? undefined")
                .contains("setFotos([])")
                .contains("setVideos([])")
                .doesNotContain("consultarLimitesMinhasMidias");
        apareceAntes(criarAnuncio, "saveWizardCreationOperation(progressScope, operation)",
                "await submitWizardAnuncio(state, operation.sessionId)");
        apareceAntes(criarAnuncio, "await enviarMinhasMidiasEmLote(", "setFotos([])");
        assertThat(wizard)
                .contains("state.fotos.map((file) => ({ file, url: URL.createObjectURL(file) }))")
                .contains("fotoPreviewEntries[index]?.file === file ? fotoPreviewEntries[index].url : undefined")
                .contains("return () => entries.forEach(({ url }) => URL.revokeObjectURL(url))");
        assertThat(contarOcorrencias(fotos, "URL.createObjectURL(")).isEqualTo(1);
        assertThat(contarOcorrencias(fotos, "URL.revokeObjectURL(")).isEqualTo(1);
        assertThat(validador)
                .contains("new WeakMap<File, Promise<PhotoUploadValidationResult>>()")
                .contains("bitmap.close()")
                .contains("URL.revokeObjectURL(url)");
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
        String classificarVideo = recorte(classificacao, "function isVideoUploadFile(", "function isPhotoUploadFile(");
        String classificarFoto = recorte(classificacao, "function isPhotoUploadFile(", "function containsOnlyPhotoUploads(");
        String validarFotos = recorte(classificacao, "async function validateMediaUploadPhotos(", "const failures = results.filter");
        String validador = Files.readString(FRONTEND.resolve(Path.of("lib", "photo-upload-validation.ts")));

        assertThat(contrato)
                .contains("unsupportedPhotoUpload?: boolean")
                .contains("options.unsupportedPhotoUpload")
                .contains("? resolveUnsupportedPhotoUploadMessage(serverMessage)")
                .contains(": message('Não foi possível processar o arquivo enviado.')");
        assertThat(classificacao)
                .contains("new Set(['jpg', 'jpeg', 'png', 'webp'])")
                .contains("file.type.trim().toLowerCase()")
                .contains("file.name.trim().toLowerCase()")
                .contains("files.length > 0 && files.every(isPhotoUploadFile)");
        assertThat(classificarVideo)
                .contains("if (extension && PHOTO_UPLOAD_EXTENSIONS.has(extension)) return false")
                .contains("return isSupportedUploadVideo(file)")
                .contains("OTHER_VIDEO_EXTENSIONS.has(extension)")
                .doesNotContain("mimeType.startsWith('video/')");
        assertThat(classificarFoto)
                .contains("if (isVideoUploadFile(file)) return false")
                .contains("return Boolean(extension && PHOTO_UPLOAD_EXTENSIONS.has(extension)) || mimeType.startsWith('image/')")
                .doesNotContain("if (mimeType) return");
        assertThat(validarFotos)
                .contains("const results = await Promise.all(files.map(async (file) => {")
                .contains("if (isVideoUploadFile(file)) return null")
                .contains("file.type.trim().toLowerCase().startsWith('video/')")
                .contains("(!extension || !PHOTO_UPLOAD_EXTENSIONS.has(extension))")
                .contains("const result = await validatePhotoUpload(file)")
                .contains("return result.valid ? null : `${file.name}: ${result.message}`");
        assertThat(validador)
                .contains("if (!['jpg', 'jpeg', 'png', 'webp'].includes(ext)) return invalid(PHOTO_FORMAT_MESSAGE)")
                .contains("const bytes = new Uint8Array(await file.arrayBuffer())")
                .contains("jpeg && (ext === 'jpg' || ext === 'jpeg')")
                .contains("bytes[bytes.length - 2] !== 0xff || bytes[bytes.length - 1] !== 0xd9")
                .contains("ext === 'png' && pngSignature.every")
                .contains("ext === 'webp'")
                .contains("await imageDimensions(file.slice(0, file.size, mime))")
                .doesNotContain("file.type");
        assertThat(adapter)
                .contains("const message = xhr.status === 415 && unsupportedPhotoUpload")
                .contains("candidateMessage || fallback");
        assertThat(documentos).doesNotContain("unsupportedPhotoUpload");
        assertThat(kyc).doesNotContain("unsupportedPhotoUpload");
        assertThat(documentos).doesNotContain("photo-upload-validation", "validatePhotoUpload");
        assertThat(kyc).doesNotContain("photo-upload-validation", "validatePhotoUpload");
    }

    @Test
    void remocaoDeMidiaExibeMensagemCodigoERequestIdDoEnvelope() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "meus-anuncios-api.ts")));
        String fotos = Files.readString(FRONTEND.resolve(Path.of(
                "features", "anuncio-wizard", "components", "wizard-step-fotos.tsx")));
        String remocao = recorte(
                fotos,
                "const confirmRemoval = async () => {",
                "if (!slug) {");
        String pedirConfirmacao = recorte(fotos, "const requestRemoval = (midia: MinhaMidiaGestao) => {", "const cancelRemoval = () => {");
        String cancelar = recorte(fotos, "const cancelRemoval = () => {", "const confirmRemoval = async () => {");

        assertThat(adapter)
                .contains("error.code ? `Código: ${error.code}` : null")
                .contains("error.requestId ? `Request ID: ${error.requestId}` : null");
        assertThat(remocao)
                .contains("meusAnunciosErrorMessage(error, 'Não foi possível remover a mídia.')")
                .contains("handleUnconfirmedState(error, true)")
                .contains("const midia = removalIntentRef.current")
                .contains("!midia || busy || disabled || terminalRef.current || !uploadLockRef.current")
                .contains("acceptResponse(await removerMinhaMidia(slug, midia.id), generation)")
                .doesNotContain("error instanceof Error ? error.message");
        apareceAntes(remocao, "removalIntentRef.current = null", "await removerMinhaMidia(slug, midia.id)");
        assertThat(contarOcorrencias(fotos, "await removerMinhaMidia(")).isEqualTo(1);
        assertThat(pedirConfirmacao)
                .contains("!beginInteraction()")
                .contains("setRemovalIntent(midia)")
                .doesNotContain("removerMinhaMidia(");
        assertThat(cancelar)
                .contains("setRemovalIntent(null)")
                .contains("endInteraction()")
                .doesNotContain("removerMinhaMidia(");
        assertThat(fotos)
                .contains("Excluir a última foto?", "Excluir foto?")
                .contains("Ao excluir esta foto, seu anúncio será encerrado. Deseja continuar?")
                .contains("Ao excluir a última foto, seu anúncio será encerrado. Deseja continuar?")
                .contains("Excluir foto e encerrar anúncio", "'Excluir foto'")
                .contains("Para trocar a foto, envie a nova antes de excluir a atual.")
                .contains("Se o anúncio já estiver aprovado, aguarde a aprovação da nova foto antes de excluir a última foto aprovada.")
                .contains("onOpenAutoFocus", "cancelRemovalButtonRef.current?.focus()")
                .contains("persisted?.fotosValidasAtivasTotal === 1")
                .contains("persisted.midias.every((midia) => midia.tipo !== 'FOTO' || midia.id === removalIntent.id)")
                .doesNotContain("substituta persistida", "Se esta for a última foto");
        String wizard = Files.readString(FRONTEND.resolve(Path.of(
                "features", "anuncio-wizard", "anuncio-wizard.tsx")));
        for (String encerramento : new String[] { fotos, wizard }) {
            assertThat(encerramento)
                    .contains("Anúncio encerrado")
                    .contains("Seu anúncio foi encerrado e não está mais disponível.")
                    .contains("Voltar para Meus anúncios")
                    .doesNotContain("O servidor confirmou o encerramento");
        }
    }

    private static String recorte(String conteudo, String inicio, String fim) {
        int inicioIndex = conteudo.indexOf(inicio);
        int fimIndex = inicioIndex < 0 ? -1 : conteudo.indexOf(fim, inicioIndex + inicio.length());
        assertThat(inicioIndex).as("inicio do contrato").isGreaterThanOrEqualTo(0);
        assertThat(fimIndex).as("fim do contrato").isGreaterThan(inicioIndex);
        return conteudo.substring(inicioIndex, fimIndex);
    }

    private static void apareceAntes(String conteudo, String primeiro, String segundo) {
        assertThat(conteudo).contains(primeiro, segundo);
        assertThat(conteudo.indexOf(primeiro))
                .as("%s deve ocorrer antes de %s", primeiro, segundo)
                .isLessThan(conteudo.indexOf(segundo));
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
