package br.com.topsdojob.v3.web.admin.anuncio;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioMidiaUploadDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AdminAnuncioMidiaFrontendContractTest {

    private static final Path FRONTEND = Path.of("..", "frontend", "src");

    @Test
    void dtoDeUploadNaoExpoeLocalizadorNomeOriginalOuPii() {
        assertThat(Arrays.stream(AdminAnuncioMidiaUploadDto.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList())
                .containsExactly(
                        "midiaId", "anuncioId", "tipo", "finalidade", "ordem",
                        "status", "statusArquivo", "idempotente", "requestId")
                .doesNotContain(
                        "ownerId", "usuarioId", "bucket", "key", "chaveObjeto",
                        "url", "nomeOriginal", "storageProvider");
    }

    @Test
    void adapterEnviaUmaParteArquivoComIdempotenciaECsrfSemContentTypeManual() throws Exception {
        String api = Files.readString(FRONTEND.resolve(Path.of("features", "admin-anuncios", "api.ts")));
        String upload = recorte(api, "export async function uploadAdminAdMedia", "export async function listAdminAdHistory");
        String validacao = recorte(upload, "const validation = await validatePhotoUpload(arquivo)", "const form = new FormData()");

        assertOrdem(upload,
                "const validation = await validatePhotoUpload(arquivo)",
                "if (!validation.valid) {",
                "throw new ApiContractError(",
                "'INVALID_REQUEST',",
                "null,",
                "false,",
                "'PHOTO_UPLOAD_LOCAL_INVALID',",
                "const form = new FormData()",
                "form.append('arquivo', arquivo)",
                "return request<AdminAdMediaUploadResponse>(");
        assertThat(validacao)
                .contains("`${arquivo.name}: ${validation.message}`")
                .doesNotContain("new FormData(", "request<", "fetch(", "csrfHeaders(");

        assertThat(upload)
                .contains("const form = new FormData()")
                .contains("form.append('arquivo', arquivo)")
                .contains("`/anuncios/${encodeURIComponent(id)}/midias`")
                .contains("method: 'POST'")
                .contains("'Idempotency-Key': idempotencyKey")
                .contains("body: form")
                .contains("{ unsupportedPhotoUpload: true }")
                .doesNotContain("Content-Type", "ownerId", "bucket", "arquivoId", "midiaId", "url:");
        assertThat(contarOcorrencias(upload, "form.append(")).isEqualTo(1);
        assertThat(contarOcorrencias(api, "unsupportedPhotoUpload: true")).isEqualTo(1);
        assertThat(api)
                .contains("const multipart = typeof FormData !== 'undefined' && init.body instanceof FormData")
                .contains("csrfHeaders(multipart ? 'multipart' : 'json')")
                .contains(": { [csrfHeaderName()]: value }")
                .contains("unsupportedPhotoUpload: options.unsupportedPhotoUpload === true")
                .contains("throw normalizeApiError(error)");
    }

    @Test
    void paginacaoAdministrativaLimitaVintePaginasEPreservaValidacoes() throws Exception {
        String api = Files.readString(FRONTEND.resolve(Path.of("features", "admin-anuncios", "api.ts")));
        String paginacao = recorte(
                api,
                "const ADMIN_AD_MEDIA_PAGE_SIZE",
                "export async function listAdminAdMedia");

        assertThat(paginacao)
                .contains("const ADMIN_AD_MEDIA_PAGE_SIZE = 50")
                .contains("const ADMIN_AD_MEDIA_MAX_PAGES = 20")
                .contains("page.page === requestedPage")
                .contains("page.size === ADMIN_AD_MEDIA_PAGE_SIZE")
                .contains("page.totalPages <= ADMIN_AD_MEDIA_MAX_PAGES")
                .contains("page.totalElements !== baseline.totalElements")
                .contains("page.totalPages !== baseline.totalPages")
                .contains("itens.length !== baseline.totalElements")
                .contains("uniqueIds.size !== itens.length");
    }

    @Test
    void uploaderPreservaArquivoEChaveNaFalhaESoMostraSucessoDepoisDoReload() throws Exception {
        String uploader = Files.readString(FRONTEND.resolve(Path.of(
                "features", "admin-anuncios", "admin-anuncio-midia-uploader.tsx")));
        String moderacao = Files.readString(FRONTEND.resolve(Path.of(
                "features", "admin-anuncios", "admin-anuncio-moderacao.tsx")));
        String validacaoFotos = Files.readString(FRONTEND.resolve(Path.of("lib", "photo-upload-validation.ts")));
        String selecao = recorte(uploader, "function selectArquivo(files: File[]) {", "function removeArquivo() {");
        String remocao = recorte(uploader, "function removeArquivo() {", "async function submit() {");
        String submit = recorte(uploader, "async function submit() {", "  return (");
        String bloqueio = recorte(submit, "async function submit() {", "uploadLock.current = true");
        String catchUpload = recorte(uploader, "} catch (uploadError) {", "} finally {");

        assertOrdem(submit,
                "uploadLock.current = true",
                "await uploadAdminAdMedia(anuncioId, arquivo, idempotencyKey)",
                "await onReload()",
                "selectionVersion.current += 1",
                "selectedFile.current = null",
                "validatedFile.current = null",
                "setArquivo(null)",
                "setIdempotencyKey(null)",
                "setValidation(null)",
                "setSuccess('Foto enviada");
        assertThat(bloqueio)
                .contains("uploadLock.current || busy || disabled || !arquivo || !idempotencyKey")
                .contains("selectedFile.current !== arquivo")
                .contains("validatedFile.current !== arquivo")
                .contains("validation?.status !== 'valid'")
                .contains("(error && !error.retryable)) return");
        assertThat(submit).doesNotContain("crypto.randomUUID()");

        assertOrdem(selecao,
                "if (uploadLock.current || busy || disabled) return",
                "const version = ++selectionVersion.current",
                "selectedFile.current = selected",
                "validatedFile.current = null",
                "setArquivo(selected)",
                "setIdempotencyKey(selected ? crypto.randomUUID() : null)",
                "setError(null)",
                "setValidation(selected ? { status: 'checking' } : null)",
                "void validatePhotoUpload(selected).then((result) => {",
                "if (version !== selectionVersion.current || selectedFile.current !== selected) return",
                "validatedFile.current = result.valid ? selected : null",
                "setValidation(result.valid ? { status: 'valid' } : { status: 'invalid', message: result.message })");
        assertThat(selecao).doesNotContain("uploadAdminAdMedia(", "submit(", "fetch(", "new FormData(");
        assertOrdem(remocao,
                "if (uploadLock.current || busy || disabled) return",
                "selectionVersion.current += 1",
                "selectedFile.current = null",
                "validatedFile.current = null",
                "setArquivo(null)",
                "setIdempotencyKey(null)",
                "setValidation(null)",
                "setError(null)");
        assertThat(remocao).doesNotContain("uploadAdminAdMedia(", "submit(", "fetch(", "new FormData(");

        assertThat(catchUpload)
                .contains("setError(normalizeApiError(uploadError))")
                .doesNotContain("setArquivo(", "setIdempotencyKey(", "setSuccess(",
                        "selectedFile.current =", "validatedFile.current =", "setValidation(");
        assertThat(uploader)
                .contains("const [arquivo, setArquivo] = useState<File | null>(null)")
                .contains("const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null)")
                .contains("selected ? crypto.randomUUID() : null")
                .contains("error.message")
                .contains("error.code")
                .contains("error.requestId")
                .contains("error?.retryable ? 'Tentar novamente' : 'Enviar foto'")
                .contains("validation?.status !== 'valid' || Boolean(error && !error.retryable)")
                .contains("!error.retryable ? <p")
                .contains("Remova ou substitua o arquivo para continuar.")
                .contains("Verificando foto…")
                .contains("accept={PHOTO_UPLOAD_ACCEPT}")
                .contains("{PHOTO_UPLOAD_GUIDANCE}")
                .contains("onSelect={selectArquivo}")
                .contains("onRemove={removeArquivo}")
                .contains("onClick={() => void submit()}")
                .doesNotContain("error ? 'Tentar novamente' : 'Enviar foto'");
        assertThat(validacaoFotos)
                .contains("PHOTO_UPLOAD_ACCEPT = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp'");
        assertThat(moderacao)
                .contains("<AdminAnuncioMidiaUploader")
                .contains("anuncioId={ad.id}")
                .contains("onReload={() => load(undefined, false)}");
    }

    private static String recorte(String conteudo, String inicio, String fim) {
        int inicioIndex = conteudo.indexOf(inicio);
        int fimIndex = inicioIndex < 0 ? -1 : conteudo.indexOf(fim, inicioIndex + inicio.length());
        assertThat(inicioIndex).as("inicio do contrato").isGreaterThanOrEqualTo(0);
        assertThat(fimIndex).as("fim do contrato").isGreaterThan(inicioIndex);
        return conteudo.substring(inicioIndex, fimIndex);
    }

    private static void assertOrdem(String conteudo, String... trechos) {
        int proximoInicio = 0;
        for (String trecho : trechos) {
            int indice = conteudo.indexOf(trecho, proximoInicio);
            assertThat(indice).as("contrato em ordem: %s", trecho).isGreaterThanOrEqualTo(proximoInicio);
            proximoInicio = indice + trecho.length();
        }
    }

    private static int contarOcorrencias(String conteudo, String trecho) {
        int total = 0;
        int indice = 0;
        while ((indice = conteudo.indexOf(trecho, indice)) >= 0) {
            total++;
            indice += trecho.length();
        }
        return total;
    }
}
