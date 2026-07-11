package br.com.topsdojob.v3.application.publico.auth;

import br.com.topsdojob.v3.application.publico.auth.dto.PublicAuthStatusDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicDuplicidadeDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicLoginRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicRegisterRequestDto;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicUserDto;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicAuthenticationService {

    private static final String STATUS_LOGOUT_OK = "LOGOUT_OK";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    private static final Pattern COMMON_CREDENTIAL_PATTERN = Pattern.compile("(1234|abcd|senha|password|qwerty)", Pattern.CASE_INSENSITIVE);

    private final UsuarioRepository usuarioRepository;
    private final CredencialUsuarioRepository credencialRepository;
    private final PapelUsuarioRepository papelRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    public PublicAuthenticationService(
            UsuarioRepository usuarioRepository,
            CredencialUsuarioRepository credencialRepository,
            PapelUsuarioRepository papelRepository,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository) {
        this.usuarioRepository = usuarioRepository;
        this.credencialRepository = credencialRepository;
        this.papelRepository = papelRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
    }

    @Transactional
    public PublicUserDto register(PublicRegisterRequestDto request) {
        RegistrationData data = validateRegistration(request);
        PublicDuplicidadeDto duplicidade = duplicidade(data.email(), data.username(), data.telefone());
        if (duplicidade.emailExistente() || duplicidade.usernameExistente() || duplicidade.telefoneExistente()) {
            throw conflict("E-mail, telefone ou nome de usuario ja cadastrado.");
        }

        UUID usuarioId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UsuarioEntity usuario = UsuarioEntity.criarCadastroPublico(
                usuarioId,
                data.username(),
                data.email(),
                data.telefone(),
                now);
        CredencialUsuarioEntity credencial = CredencialUsuarioEntity.criar(
                UUID.randomUUID(),
                usuarioId,
                passwordEncoder.encode(data.senha()),
                now);

        try {
            usuarioRepository.saveAndFlush(usuario);
            credencialRepository.save(credencial);
            papelRepository.save(PapelUsuarioEntity.criarUsuarioPublico(usuarioId, now));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("E-mail, telefone ou nome de usuario ja cadastrado.");
        }
        return toDto(usuario);
    }

    @Transactional(readOnly = true)
    public PublicUserDto login(
            PublicLoginRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (request == null || isBlank(request.email()) || isBlank(request.senha())) {
            throw unauthorized();
        }
        String email = normalizeEmail(request.email());
        UsuarioEntity usuario = usuarioRepository.findByEmailNormalizado(email).orElseThrow(this::unauthorized);
        if (usuario.getStatus() != StatusUsuario.ATIVO
                || usuario.getTipoConta() != TipoContaUsuario.ANUNCIANTE
                || usuario.getDesativadoEm() != null) {
            throw unauthorized();
        }
        CredencialUsuarioEntity credencial = credencialRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(this::unauthorized);
        if (!"BCRYPT".equalsIgnoreCase(credencial.getAlgoritmo())
                || !passwordEncoder.matches(request.senha(), credencial.getSenhaHash())) {
            throw unauthorized();
        }

        rotateSessionId(httpRequest);
        PublicUserPrincipal principal = new PublicUserPrincipal(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmailNormalizado());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return toDto(usuario);
    }

    @Transactional(readOnly = true)
    public PublicUserDto me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PublicUserPrincipal principal)) {
            throw unauthorized();
        }
        UsuarioEntity usuario = usuarioRepository.findById(principal.usuarioId()).orElseThrow(this::unauthorized);
        if (usuario.getStatus() != StatusUsuario.ATIVO || usuario.getDesativadoEm() != null) {
            throw unauthorized();
        }
        return toDto(usuario);
    }

    public PublicAuthStatusDto logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request != null) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        return new PublicAuthStatusDto(false, STATUS_LOGOUT_OK);
    }

    @Transactional(readOnly = true)
    public PublicDuplicidadeDto duplicidade(String email, String username, String telefone) {
        boolean hasEmail = !isBlank(email);
        boolean hasUsername = !isBlank(username);
        boolean hasTelefone = !isBlank(telefone);
        if (!hasEmail && !hasUsername && !hasTelefone) {
            throw badRequest("Informe ao menos um campo para verificar duplicidade.");
        }
        String normalizedPhone = hasTelefone ? normalizePhone(telefone) : null;
        return new PublicDuplicidadeDto(
                hasEmail && usuarioRepository.existsByEmailNormalizado(normalizeEmail(email)),
                hasUsername && usuarioRepository.existsByNomeIgnoreCase(username.trim()),
                hasTelefone && usuarioRepository.existsByTelefoneNormalizado(normalizedPhone));
    }

    private RegistrationData validateRegistration(PublicRegisterRequestDto request) {
        if (request == null) {
            throw badRequest("Dados de cadastro obrigatorios.");
        }
        String username = request.username() == null ? "" : request.username().trim();
        String email = normalizeEmail(request.email());
        String telefone = normalizePhone(request.telefone());
        if (username.length() < 3 || username.length() > 120) {
            throw badRequest("Nome de usuario invalido.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 320) {
            throw badRequest("E-mail invalido.");
        }
        if (!PHONE_PATTERN.matcher(telefone).matches()) {
            throw badRequest("Telefone invalido.");
        }
        validateBirthDate(request.dataNascimento());
        validateCredential(request.senha(), request.confirmarSenha());
        if (!Boolean.TRUE.equals(request.acceptedTermsOfUse())
                || !Boolean.TRUE.equals(request.acceptedPrivacyPolicy())) {
            throw badRequest("Aceite dos termos de uso e da politica de privacidade e obrigatorio.");
        }
        return new RegistrationData(username, email, telefone, request.senha());
    }

    private void validateBirthDate(String value) {
        try {
            LocalDate birthDate = LocalDate.parse(value);
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            if (birthDate.isAfter(today) || Period.between(birthDate, today).getYears() < 18) {
                throw badRequest("Cadastro permitido apenas para maiores de 18 anos.");
            }
        } catch (DateTimeParseException exception) {
            throw badRequest("Data de nascimento invalida.");
        }
    }

    private void validateCredential(String credential, String confirmation) {
        if (isBlank(credential)
                || credential.length() < 8
                || !credential.matches(".*[A-Z].*")
                || !credential.matches(".*[a-z].*")
                || !credential.matches(".*[0-9].*")
                || !SYMBOL_PATTERN.matcher(credential).find()
                || COMMON_CREDENTIAL_PATTERN.matcher(credential).find()) {
            throw badRequest("Senha nao atende aos requisitos de seguranca.");
        }
        if (!credential.equals(confirmation)) {
            throw badRequest("Confirmacao de senha invalida.");
        }
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 10 || digits.length() == 11) {
            digits = "55" + digits;
        }
        return digits.isEmpty() ? "" : "+" + digits;
    }

    private PublicUserDto toDto(UsuarioEntity usuario) {
        return new PublicUserDto(
                true,
                usuario.getId(),
                usuario.getNome(),
                usuario.getNome(),
                usuario.getEmailNormalizado(),
                usuario.getTelefoneNormalizado(),
                usuario.getStatus().name(),
                "USUARIO");
    }

    private void rotateSessionId(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        if (request.getSession(false) == null) {
            request.getSession(true);
        } else {
            request.changeSessionId();
        }
    }

    private PublicAuthException badRequest(String message) {
        return new PublicAuthException(HttpStatus.BAD_REQUEST, message);
    }

    private PublicAuthException conflict(String message) {
        return new PublicAuthException(HttpStatus.CONFLICT, message);
    }

    private PublicAuthException unauthorized() {
        return new PublicAuthException(HttpStatus.UNAUTHORIZED, "E-mail ou senha invalidos.");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record RegistrationData(String username, String email, String telefone, String senha) {
    }
}
