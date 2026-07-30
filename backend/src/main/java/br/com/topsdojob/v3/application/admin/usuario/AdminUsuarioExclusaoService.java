package br.com.topsdojob.v3.application.admin.usuario;

import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoElegibilidadeDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioExclusaoJdbcRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUsuarioExclusaoService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,160}$");

    private final UsuarioRepository usuarioRepository;
    private final AdminUsuarioExclusaoJdbcRepository exclusaoRepository;
    private final AuditoriaEventoRepository auditoriaRepository;

    public AdminUsuarioExclusaoService(
            UsuarioRepository usuarioRepository,
            AdminUsuarioExclusaoJdbcRepository exclusaoRepository,
            AuditoriaEventoRepository auditoriaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.exclusaoRepository = exclusaoRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional(readOnly = true)
    public AdminUsuarioExclusaoElegibilidadeDto elegibilidade(UUID usuarioId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "usuario nao encontrado"));
        List<String> bloqueios = bloqueios(usuario);
        return new AdminUsuarioExclusaoElegibilidadeDto(bloqueios.isEmpty(), bloqueios);
    }

    @Transactional
    public AdminUsuarioExclusaoResultadoDto excluir(
            UUID usuarioId,
            AdminUsuarioExclusaoRequestDto request,
            String idempotencyKey,
            AdminUserPrincipal ator,
            String requestId) {
        validarAtor(ator);
        if (request == null || !"EXCLUIR".equals(request.confirmacao())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Digite EXCLUIR para confirmar a operacao.");
        }
        String chave = Objects.requireNonNullElse(idempotencyKey, "").trim();
        if (!IDEMPOTENCY_KEY.matcher(chave).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key invalida");
        }
        String chaveHash = sha256(chave);
        if (exclusaoRepository.exclusaoConcluida(
                usuarioId,
                ator.usuarioId(),
                chaveHash)) {
            return new AdminUsuarioExclusaoResultadoDto(usuarioId, true);
        }

        UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElse(null);
        if (usuario == null) {
            if (exclusaoRepository.exclusaoConcluida(
                    usuarioId,
                    ator.usuarioId(),
                    chaveHash)) {
                return new AdminUsuarioExclusaoResultadoDto(usuarioId, true);
            }
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "usuario nao encontrado");
        }
        List<String> bloqueios = bloqueios(usuario);
        if (!bloqueios.isEmpty()) {
            throw new AdminUsuarioExclusaoBloqueadaException(bloqueios);
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        auditoriaRepository.saveAndFlush(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                ator.usuarioId(),
                "USUARIO_EXCLUIDO_FISICAMENTE",
                "USUARIO",
                usuarioId,
                "{\"status\":\"PRESENTE\",\"dadosPessoaisOcultos\":true}",
                "{\"status\":\"EXCLUIDO\",\"dadosPessoaisOcultos\":true,"
                        + "\"idempotencyHash\":\"" + chaveHash + "\"}",
                requestId,
                agora));
        exclusaoRepository.deleteTechnicalLinks(usuarioId);
        usuarioRepository.delete(usuario);
        usuarioRepository.flush();
        return new AdminUsuarioExclusaoResultadoDto(usuarioId, true);
    }

    private List<String> bloqueios(UsuarioEntity usuario) {
        List<String> bloqueios = new ArrayList<>();
        if (usuario.getTipoConta() != TipoContaUsuario.ANUNCIANTE) {
            bloqueios.add("CONTA_STAFF");
        }
        if (usuario.getStatus() == StatusUsuario.IMPORTADO) {
            bloqueios.add("USUARIO_IMPORTADO");
        }
        exclusaoRepository.bloqueios(usuario.getId()).stream()
                .filter(codigo -> !bloqueios.contains(codigo))
                .forEach(bloqueios::add);
        return List.copyOf(bloqueios);
    }

    private void validarAtor(AdminUserPrincipal ator) {
        if (ator == null
                || !ator.isEnabled()
                || !ator.papeis().contains(PapelUsuario.ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "operacao restrita a ADMIN");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }
}
