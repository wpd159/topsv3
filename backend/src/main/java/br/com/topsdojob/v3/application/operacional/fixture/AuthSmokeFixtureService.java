package br.com.topsdojob.v3.application.operacional.fixture;

import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("homologacao")
public class AuthSmokeFixtureService {

    static final String ACTIVE_EMAIL = "auth-smoke-active@hml.example.invalid";
    static final String PENDING_EMAIL = "auth-smoke-pending@hml.example.invalid";
    static final String DISABLED_EMAIL = "auth-smoke-disabled@hml.example.invalid";

    private static final List<AccountSpec> ACCOUNTS = List.of(
            new AccountSpec(
                    uuid("a1000000-0000-4000-8000-000000000001"),
                    uuid("a2000000-0000-4000-8000-000000000001"),
                    "Auth Smoke Ativo",
                    ACTIVE_EMAIL,
                    StatusUsuario.ATIVO,
                    true),
            new AccountSpec(
                    uuid("a1000000-0000-4000-8000-000000000002"),
                    uuid("a2000000-0000-4000-8000-000000000002"),
                    "Auth Smoke Pendente",
                    PENDING_EMAIL,
                    StatusUsuario.ATIVO,
                    false),
            new AccountSpec(
                    uuid("a1000000-0000-4000-8000-000000000003"),
                    uuid("a2000000-0000-4000-8000-000000000003"),
                    "Auth Smoke Desativado",
                    DISABLED_EMAIL,
                    StatusUsuario.DESATIVADO,
                    true));

    private final String appEnv;
    private final UsuarioRepository usuarioRepository;
    private final CredencialUsuarioRepository credencialRepository;
    private final PapelUsuarioRepository papelRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthSmokeFixtureService(
            @Value("${APP_ENV:}") String appEnv,
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
    public FixtureResult reconciliar(String segredoRuntime) {
        if (!"homologacao".equals(normalize(appEnv))) {
            throw new IllegalStateException("fixture de autenticacao recusada fora de homologacao");
        }
        if (segredoRuntime == null || segredoRuntime.length() < 16) {
            throw new IllegalArgumentException(
                    "FIXTURE_AUTH_SMOKE_RUNTIME_VALUE obrigatoria e com minimo de 16 caracteres");
        }

        int criadas = 0;
        int atualizadas = 0;
        int preservadas = 0;
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        for (AccountSpec spec : ACCOUNTS) {
            ReconcileStatus status = reconciliarConta(spec, segredoRuntime, agora);
            switch (status) {
                case CRIADA -> criadas++;
                case ATUALIZADA -> atualizadas++;
                case PRESERVADA -> preservadas++;
            }
        }
        return new FixtureResult(criadas, atualizadas, preservadas);
    }

    private ReconcileStatus reconciliarConta(
            AccountSpec spec,
            String segredoRuntime,
            OffsetDateTime agora) {
        Optional<UsuarioEntity> porId = usuarioRepository.findById(spec.usuarioId());
        Optional<UsuarioEntity> porEmail = usuarioRepository.findByEmailNormalizado(spec.email());
        if (porId.isPresent() && !spec.email().equals(porId.orElseThrow().getEmailNormalizado())) {
            throw new IllegalStateException("ID canonico da fixture Auth esta associado a outra conta");
        }
        if (porEmail.isPresent() && !spec.usuarioId().equals(porEmail.orElseThrow().getId())) {
            throw new IllegalStateException("e-mail sintetico da fixture Auth esta associado a outro ID");
        }

        boolean criada = porId.isEmpty() && porEmail.isEmpty();
        UsuarioEntity usuario = porId.or(() -> porEmail).orElseGet(() -> UsuarioEntity.criarCadastroPublico(
                spec.usuarioId(),
                spec.nome(),
                spec.email(),
                null,
                null,
                agora));
        boolean alterada = usuario.reconciliarAutenticacaoHomologacao(
                spec.status(),
                spec.emailConfirmado(),
                agora);
        if (criada || alterada) {
            usuarioRepository.saveAndFlush(usuario);
        }

        List<PapelUsuarioEntity> papeis = papelRepository.findByUsuarioId(spec.usuarioId());
        if (papeis.stream().anyMatch(item -> item.getPapel() != PapelUsuario.USUARIO) || papeis.size() > 1) {
            throw new IllegalStateException("fixture Auth aceita exclusivamente o papel USUARIO");
        }
        if (papeis.isEmpty()) {
            papelRepository.save(PapelUsuarioEntity.criarUsuarioPublico(spec.usuarioId(), agora));
            alterada = true;
        }

        Optional<CredencialUsuarioEntity> credencialExistente =
                credencialRepository.findByUsuarioId(spec.usuarioId());
        if (credencialExistente.isEmpty()) {
            credencialRepository.save(CredencialUsuarioEntity.criar(
                    spec.credencialId(),
                    spec.usuarioId(),
                    passwordEncoder.encode(segredoRuntime),
                    agora));
            alterada = true;
        } else if (!credencialConfere(credencialExistente.orElseThrow(), segredoRuntime)) {
            CredencialUsuarioEntity credencial = credencialExistente.orElseThrow();
            credencial.atualizarHashHomologacao(passwordEncoder.encode(segredoRuntime), agora);
            credencialRepository.save(credencial);
            alterada = true;
        }

        if (criada) {
            return ReconcileStatus.CRIADA;
        }
        return alterada ? ReconcileStatus.ATUALIZADA : ReconcileStatus.PRESERVADA;
    }

    private boolean credencialConfere(CredencialUsuarioEntity credencial, String segredoRuntime) {
        if (!"BCRYPT".equalsIgnoreCase(credencial.getAlgoritmo())) {
            return false;
        }
        try {
            return passwordEncoder.matches(segredoRuntime, credencial.getSenhaHash());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record AccountSpec(
            UUID usuarioId,
            UUID credencialId,
            String nome,
            String email,
            StatusUsuario status,
            boolean emailConfirmado) {
    }

    private enum ReconcileStatus {
        CRIADA,
        ATUALIZADA,
        PRESERVADA
    }

    public record FixtureResult(int contasCriadas, int contasAtualizadas, int contasPreservadas) {
    }
}
