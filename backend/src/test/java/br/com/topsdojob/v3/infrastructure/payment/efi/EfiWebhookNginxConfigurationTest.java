package br.com.topsdojob.v3.infrastructure.payment.efi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EfiWebhookNginxConfigurationTest {

    private static final Pattern WEBHOOK_LOCATION = Pattern.compile(
            "location = /api/public/webhooks/efi/pix \\{(?<body>.*?)\\n    \\}",
            Pattern.DOTALL);

    @Test
    void rotaExataNaoRegistraQueryEUsaAllowlistExterna() throws IOException {
        for (String relative : List.of(
                "deploy/hml/nginx-v3-esle-cloud.conf",
                "deploy/preprod/nginx-preprod-local.conf")) {
            String config = Files.readString(projectFile(relative));
            Matcher matcher = WEBHOOK_LOCATION.matcher(config);

            assertThat(matcher.find()).as(relative).isTrue();
            String block = matcher.group("body");
            assertThat(block)
                    .contains("access_log off;")
                    .contains("error_log /dev/null crit;")
                    .contains("include /etc/nginx/efi-webhook-allowlist.conf;")
                    .doesNotContain("$request_uri", "$args", "$query_string")
                    .doesNotMatch("(?s).*\\ballow\\s+\\d{1,3}(?:\\.\\d{1,3}){3}.*");
            assertThat(matcher.find()).as("rota duplicada em " + relative).isFalse();
        }

        String compose = Files.readString(projectFile("deploy/preprod/docker-compose.yml"));
        String environment = Files.readString(projectFile("deploy/preprod/preprod.env.example"));
        assertThat(compose)
                .contains("EFI_WEBHOOK_ALLOWLIST_FILE")
                .contains(":/etc/nginx/efi-webhook-allowlist.conf:ro");
        assertThat(environment)
                .contains("EFI_WEBHOOK_ALLOWLIST_FILE=/opt/topsv3/secrets/efi/webhook-allowlist.conf");
    }

    private Path projectFile(String relative) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path direct = current.resolve(relative).normalize();
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path parent = current.resolve("..").resolve(relative).normalize();
        assertThat(parent).as(relative).isRegularFile();
        return parent;
    }
}
