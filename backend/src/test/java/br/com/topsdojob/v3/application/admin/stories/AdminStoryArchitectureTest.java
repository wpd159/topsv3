package br.com.topsdojob.v3.application.admin.stories;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminStoryArchitectureTest {

    @Test
    void administracaoNaoDependeDeStoryPagoCreditoOuPremium() throws Exception {
        String admin = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "application", "admin", "stories", "AdminStoriesGestaoService.java"));
        String feed = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "application", "publico", "service", "StoryFeedPublicoService.java"));

        assertThat(admin)
                .contains("StoryAnuncioRepository")
                .doesNotContain("Credito")
                .doesNotContain("Premium")
                .doesNotContain("Beneficio");
        assertThat(feed)
                .doesNotContain("storyRepository.save")
                .doesNotContain("anuncioMidiaRepository.save")
                .doesNotContain("arquivoMidiaRepository.save");
    }
}
