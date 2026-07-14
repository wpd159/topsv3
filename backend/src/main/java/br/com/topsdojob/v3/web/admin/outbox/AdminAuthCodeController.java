package br.com.topsdojob.v3.web.admin.outbox;

import br.com.topsdojob.v3.application.publico.auth.HmlAuthCodeVault;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test", "homologacao"})
@ConditionalOnProperty(prefix = "app.auth", name = "hml-code-vault-enabled", havingValue = "true")
public class AdminAuthCodeController {
    private final HmlAuthCodeVault vault;
    public AdminAuthCodeController(HmlAuthCodeVault vault) { this.vault = vault; }

    @PostMapping("/api/admin/outbox/{id}/auth-test-code")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthTestCodeDto take(@PathVariable UUID id) {
        return new AuthTestCodeDto(vault.take(id));
    }

    public record AuthTestCodeDto(String codigo) {}
}
