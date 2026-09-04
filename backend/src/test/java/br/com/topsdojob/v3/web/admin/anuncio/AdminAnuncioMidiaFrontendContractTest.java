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
        String upload = recorte(api, "export function uploadAdminAdMedia", "export async function listAdminAdHistory");

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
        String catchUpload = recorte(uploader, "} catch (uploadError) {", "} finally {");

        int upload = uploader.indexOf("await uploadAdminAdMedia(anuncioId, arquivo, idempotencyKey)");
        int reload = uploader.indexOf("await onReload()", upload);
        int limparArquivo = uploader.indexOf("setArquivo(null)", reload);
        int sucesso = uploader.indexOf("setSuccess('Foto enviada", limparArquivo);
        assertThat(upload).isGreaterThanOrEqualTo(0);
        assertThat(reload).isGreaterThan(upload);
        assertThat(limparArquivo).isGreaterThan(reload);
        assertThat(sucesso).isGreaterThan(limparArquivo);

        assertThat(catchUpload)
                .contains("setError(normalizeApiError(uploadError))")
                .doesNotContain("setArquivo(null)", "setIdempotencyKey(null)", "setSuccess(");
        assertThat(uploader)
                .contains("const [arquivo, setArquivo] = useState<File | null>(null)")
                .contains("const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null)")
                .contains("selected ? crypto.randomUUID() : null")
                .contains("error.message")
                .contains("error.code")
                .contains("error.requestId")
                .contains("error ? 'Tentar novamente' : 'Enviar foto'")
                .contains("accept={ALLOWED_IMAGE_ACCEPT}");
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
