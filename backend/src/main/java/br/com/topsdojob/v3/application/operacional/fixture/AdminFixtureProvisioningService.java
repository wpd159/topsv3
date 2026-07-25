package br.com.topsdojob.v3.application.operacional.fixture;

import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("homologacao")
public class AdminFixtureProvisioningService {

    private static final Pattern EMAIL_FICTICIO = Pattern.compile("^[a-z0-9._-]+@example\\.invalid$");

    private final String appEnv;
    private final UsuarioRepository usuarioRepository;
    private final CredencialUsuarioRepository credencialRepository;
    private final PapelUsuarioRepository papelRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminFixtureProvisioningService(
            @Value("${app.env:nao_configurado}") String appEnv,
            UsuarioRepository usuarioRepository,
            CredencialUsuarioRepository credencialRepository,
            PapelUsuarioRepository papelRepository,
            PasswordEncoder passwordEncoder) {
        this.appEnv = appEnv;
        this.usuarioRepository = usuarioRepository;
        this.credencialRepository = credencialRepository;
        this.papelRepository = papelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ProvisioningResult provisionar(String emailInformado, String runtimeValue) {
        validarAmbiente();
        String email = normalizarEmail(emailInformado);
        validarEmail(email);
        validarCredencial(runtimeValue);

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UsuarioEntity usuario = usuarioRepository.findByEmailNormalizado(email).orElse(null);
        boolean criado = usuario == null;
        if (criado) {
            usuario = UsuarioEntity.criarStaffHomologacao(
                    UUID.randomUUID(),
                    "Administrador ficticio de homologacao",
                    email,
                    agora);
            usuarioRepository.saveAndFlush(usuario);
        } else {
            validarUsuarioExistente(usuario);
        }

        String hash = passwordEncoder.encode(runtimeValue);
        CredencialUsuarioEntity credencial = credencialRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (credencial == null) {
            credencial = CredencialUsuarioEntity.criar(UUID.randomUUID(), usuario.getId(), hash, agora);
        } else {
            credencial.atualizarHashHomologacao(hash, agora);
        }
        credencialRepository.save(credencial);

        boolean possuiAdmin = papelRepository.findByUsuarioId(usuario.getId()).stream()
                .anyMatch(vinculo -> vinculo.getPapel() == PapelUsuario.ADMIN);
        if (!possuiAdmin) {
            papelRepository.save(PapelUsuarioEntity.criarAdminHomologacao(usuario.getId(), agora));
        }
        return new ProvisioningResult(criado, possuiAdmin);
    }

    private void validarAmbiente() {
        if (!"homologacao".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim())) {
            throw new IllegalStateException("provisionamento recusado fora de homologacao");
        }
    }

    private String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void validarEmail(String email) {
        if (!EMAIL_FICTICIO.matcher(email).matches() || email.length() > 160) {
            throw new IllegalArgumentException("use somente e-mail ficticio do dominio example.invalid");
        }
    }

    private void validarCredencial(String runtimeValue) {
        if (runtimeValue == null
                || runtimeValue.length() < 16
                || !runtimeValue.matches(".*[A-Z].*")
                || !runtimeValue.matches(".*[a-z].*")
                || !runtimeValue.matches(".*[0-9].*")
                || !runtimeValue.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("credencial de runtime nao atende a politica minima");
        }
    }

    private void validarUsuarioExistente(UsuarioEntity usuario) {
        if (usuario.getTipoConta() != TipoContaUsuario.STAFF
                || usuario.getStatus() != StatusUsuario.ATIVO
                || usuario.getDesativadoEm() != null) {
            throw new IllegalStateException("identidade ficticia existente nao e um STAFF ativo de homologacao");
        }
    }

    public record ProvisioningResult(boolean usuarioCriado, boolean papelAdminJaExistia) {
    }
}
