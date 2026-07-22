package br.com.topsdojob.v3.web.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import jakarta.persistence.LockModeType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class MeusAnunciosCicloVidaContratoTest {

    @Test
    void openApiDocumentaCicloDeVidaNaApiExistente() throws Exception {
        String openApi = Files.readString(Path.of(
                "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

        assertThat(openApi)
                .contains("/api/public/minha-conta/anuncios/{slug}/pausar:")
                .contains("/api/public/minha-conta/anuncios/{slug}/reativar:")
                .contains("operationId: removeCurrentUserAd")
                .contains("operationId: pauseCurrentUserAd")
                .contains("operationId: reactivateCurrentUserAd")
                .contains("MeuAnuncioAcoesPermitidas")
                .contains("MeuAnuncioCicloVida")
                .contains("preserva dados, midias, historico e objetos de storage")
                .contains("Nao restaura beneficio expirado");

        String resposta = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3", "application",
                "publico", "anunciante", "dto", "MeuAnuncioCicloVidaDto.java"));
        assertThat(resposta)
                .doesNotContain("usuarioId")
                .doesNotContain("email");
    }

    @Test
    void alteracaoUsaLockPessimistaParaSerializarDuploClique() throws Exception {
        Lock lock = AnuncioRepository.class
                .getMethod("findBySlugForLifecycle", String.class)
                .getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
