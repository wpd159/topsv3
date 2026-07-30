package br.com.topsdojob.v3.application.admin.usuario;

import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoElegibilidadeDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.application.publico.auth.PublicSessionRegistry;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioEncerramentoConteudoService.Resultado;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AdminUsuarioExclusaoService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,160}$");
    private static final Pattern DADO_PESSOAL_NO_MOTIVO = Pattern.compile(
            "(?i)([^\\s@]+@[^\\s@]+|\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b|\\+?\\d[\\d ()-]{8,}\\d)");
    private static final List<String> CONSEQUENCIAS_FISICA = List.of(
            "CONTA_REMOVIDA_DEFINITIVAMENTE",
            "EMAIL_CPF_TELEFONE_LIBERADOS",
            "NOVA_CONTA_NAO_HERDA_DADOS_OU_SALDO");
    private static final List<String> CONSEQUENCIAS_ANONIMIZACAO = List.of(
            "CONTA_ENCERRADA_E_DADOS_PESSOAIS_ANONIMIZADOS",
            "HISTORICOS_FINANCEIROS_E_OPERACIONAIS_PRESERVADOS",
            "ANUNCIOS_RETIRADOS_DO_PUBLICO",
            "EMAIL_CPF_TELEFONE_LIBERADOS",
            "NOVA_CONTA_NAO_HERDA_DADOS_OU_SALDO");

    private final UsuarioRepository usuarioRepository;
    private final AdminUsuarioExclusaoJdbcRepository exclusaoRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final AdminUsuarioEncerramentoConteudoService conteudoService;
    private final PublicSessionRegistry sessions;
    private final ObjectMapper objectMapper;

    public AdminUsuarioExclusaoService(
            UsuarioRepository usuarioRepository,
            AdminUsuarioExclusaoJdbcRepository exclusaoRepository,
            AuditoriaEventoRepository auditoriaRepository,
            AdminUsuarioEncerramentoConteudoService conteudoService,
            PublicSessionRegistry sessions,
            ObjectMapper objectMapper) {
        this.usuarioRepository = usuarioRepository;
        this.exclusaoRepository = exclusaoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.conteudoService = conteudoService;
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminUsuarioExclusaoElegibilidadeDto elegibilidade(UUID usuarioId) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "usuario nao encontrado"));
        var analise = exclusaoRepository.analisar(usuarioId);
        List<String> bloqueios = bloqueios(usuario, analise);
        AdminUsuarioExclusaoTipo tipo = tipo(usuario, analise);
        return new AdminUsuarioExclusaoElegibilidadeDto(
                bloqueios.isEmpty(),
                tipo.name(),
                tipo == AdminUsuarioExclusaoTipo.EXCLUSAO_COM_ANONIMIZACAO,
                analise.vinculosDuraveis(),
                tipo == AdminUsuarioExclusaoTipo.EXCLUSAO_FISICA
                        ? CONSEQUENCIAS_FISICA
                        : CONSEQUENCIAS_ANONIMIZACAO,
                bloqueios);
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
        String motivo = motivo(request.motivo());
        String chave = Objects.requireNonNullElse(idempotencyKey, "").trim();
        if (!IDEMPOTENCY_KEY.matcher(chave).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key invalida");
        }
        String chaveHash = sha256(chave);
        var concluida = exclusaoRepository.exclusaoConcluida(
                usuarioId,
                ator.usuarioId(),
                chaveHash);
        if (concluida.isPresent()) {
            return resultado(usuarioId, AdminUsuarioExclusaoTipo.valueOf(concluida.get()));
        }

        UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElse(null);
        if (usuario == null) {
            concluida = exclusaoRepository.exclusaoConcluida(
                    usuarioId,
                    ator.usuarioId(),
                    chaveHash);
            if (concluida.isPresent()) {
                return resultado(usuarioId, AdminUsuarioExclusaoTipo.valueOf(concluida.get()));
            }
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "usuario nao encontrado");
        }
        var analise = exclusaoRepository.analisar(usuarioId);
        List<String> bloqueios = bloqueios(usuario, analise);
        if (!bloqueios.isEmpty()) {
            throw new AdminUsuarioExclusaoBloqueadaException(bloqueios);
        }
        if (usuario.getStatus() == StatusUsuario.EXCLUIDO) {
            throw new AdminUsuarioExclusaoBloqueadaException(List.of("CONTA_JA_EXCLUIDA"));
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AdminUsuarioExclusaoTipo tipo = tipo(usuario, analise);
        if (tipo == AdminUsuarioExclusaoTipo.EXCLUSAO_FISICA) {
            auditar(
                    usuarioId,
                    ator.usuarioId(),
                    "USUARIO_EXCLUIDO_FISICAMENTE",
                    tipo,
                    chaveHash,
                    motivo,
                    0,
                    0,
                    requestId,
                    agora);
            exclusaoRepository.deleteTechnicalLinks(usuarioId);
            usuarioRepository.delete(usuario);
            usuarioRepository.flush();
        } else {
            Resultado conteudo = conteudoService.encerrar(usuarioId, ator.usuarioId(), agora);
            exclusaoRepository.deleteTechnicalLinks(usuarioId);
            exclusaoRepository.anonymizeAuxiliaryData(usuarioId, conteudo.anuncioIds(), agora);
            usuario.anonimizarDefinitivamente(ator.usuarioId(), agora);
            usuarioRepository.saveAndFlush(usuario);
            auditar(
                    usuarioId,
                    ator.usuarioId(),
                    "USUARIO_EXCLUIDO_COM_ANONIMIZACAO",
                    tipo,
                    chaveHash,
                    motivo,
                    conteudo.anunciosRemovidos(),
                    conteudo.storiesEncerrados(),
                    requestId,
                    agora);
        }
        invalidarSessoesAposCommit(usuarioId);
        return resultado(usuarioId, tipo);
    }

    @Transactional
    public AdminUsuarioExclusaoResultadoDto excluirPeloProprioUsuario(
            UUID usuarioId,
            String idempotencyKey,
            String requestId) {
        String chave = Objects.requireNonNullElse(idempotencyKey, "").trim();
        if (!IDEMPOTENCY_KEY.matcher(chave).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key invalida");
        }
        String chaveHash = sha256(chave);
        var concluida = exclusaoRepository.exclusaoConcluidaPorRecurso(usuarioId, chaveHash);
        if (concluida.isPresent()) {
            return resultado(usuarioId, AdminUsuarioExclusaoTipo.valueOf(concluida.get()));
        }

        UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(usuarioId).orElse(null);
        if (usuario == null) {
            concluida = exclusaoRepository.exclusaoConcluidaPorRecurso(usuarioId, chaveHash);
            if (concluida.isPresent()) {
                return resultado(usuarioId, AdminUsuarioExclusaoTipo.valueOf(concluida.get()));
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado");
        }

        var analise = exclusaoRepository.analisar(usuarioId);
        List<String> bloqueios = bloqueios(usuario, analise);
        if (!bloqueios.isEmpty()) {
            throw new AdminUsuarioExclusaoBloqueadaException(bloqueios);
        }
        if (usuario.getStatus() == StatusUsuario.EXCLUIDO) {
            throw new AdminUsuarioExclusaoBloqueadaException(List.of("CONTA_JA_EXCLUIDA"));
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AdminUsuarioExclusaoTipo tipo = tipo(usuario, analise);
        String motivo = "Solicitacao autenticada do titular da conta";
        if (tipo == AdminUsuarioExclusaoTipo.EXCLUSAO_FISICA) {
            auditar(
                    usuarioId,
                    null,
                    "USUARIO_AUTOEXCLUIDO_FISICAMENTE",
                    tipo,
                    chaveHash,
                    motivo,
                    0,
                    0,
                    requestId,
                    agora);
            exclusaoRepository.deleteTechnicalLinks(usuarioId);
            usuarioRepository.delete(usuario);
            usuarioRepository.flush();
        } else {
            Resultado conteudo = conteudoService.encerrar(usuarioId, usuarioId, agora);
            exclusaoRepository.deleteTechnicalLinks(usuarioId);
            exclusaoRepository.anonymizeAuxiliaryData(usuarioId, conteudo.anuncioIds(), agora);
            usuario.anonimizarDefinitivamente(usuarioId, agora);
            usuarioRepository.saveAndFlush(usuario);
            auditar(
                    usuarioId,
                    usuarioId,
                    "USUARIO_AUTOEXCLUIDO_COM_ANONIMIZACAO",
                    tipo,
                    chaveHash,
                    motivo,
                    conteudo.anunciosRemovidos(),
                    conteudo.storiesEncerrados(),
                    requestId,
                    agora);
        }
        invalidarSessoesAposCommit(usuarioId);
        return resultado(usuarioId, tipo);
    }

    private List<String> bloqueios(
            UsuarioEntity usuario,
            AdminUsuarioExclusaoJdbcRepository.DependencyAnalysis analise) {
        List<String> bloqueios = new ArrayList<>();
        if (usuario.getTipoConta() != TipoContaUsuario.ANUNCIANTE || analise.contaStaff()) {
            bloqueios.add("CONTA_STAFF");
        }
        if (analise.operacaoConcorrente()) {
            bloqueios.add("OPERACAO_CONCORRENTE");
        }
        return List.copyOf(bloqueios);
    }

    private AdminUsuarioExclusaoTipo tipo(
            UsuarioEntity usuario,
            AdminUsuarioExclusaoJdbcRepository.DependencyAnalysis analise) {
        return usuario.getStatus() == StatusUsuario.IMPORTADO || analise.exigeAnonimizacao()
                ? AdminUsuarioExclusaoTipo.EXCLUSAO_COM_ANONIMIZACAO
                : AdminUsuarioExclusaoTipo.EXCLUSAO_FISICA;
    }

    private AdminUsuarioExclusaoResultadoDto resultado(
            UUID usuarioId,
            AdminUsuarioExclusaoTipo tipo) {
        return new AdminUsuarioExclusaoResultadoDto(
                usuarioId,
                true,
                tipo.name(),
                tipo == AdminUsuarioExclusaoTipo.EXCLUSAO_COM_ANONIMIZACAO);
    }

    private String motivo(String value) {
        String normalizado = Objects.requireNonNullElse(value, "").trim().replaceAll("\\s+", " ");
        if (normalizado.length() < 5 || normalizado.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo obrigatorio");
        }
        if (DADO_PESSOAL_NO_MOTIVO.matcher(normalizado).find()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "o motivo nao pode conter e-mail, CPF ou telefone");
        }
        return normalizado;
    }

    private void auditar(
            UUID usuarioId,
            UUID atorId,
            String acao,
            AdminUsuarioExclusaoTipo tipo,
            String chaveHash,
            String motivo,
            int anunciosRemovidos,
            int storiesEncerrados,
            String requestId,
            OffsetDateTime agora) {
        Map<String, Object> antes = Map.of(
                "status", "PRESENTE",
                "dadosPessoaisOcultos", true);
        Map<String, Object> depois = new LinkedHashMap<>();
        depois.put("status", "EXCLUIDO");
        depois.put("estrategia", tipo.name());
        depois.put("motivoSanitizado", motivo);
        depois.put("dadosPessoaisOcultos", true);
        depois.put("idempotencyHash", chaveHash);
        depois.put("anunciosRemovidos", anunciosRemovidos);
        depois.put("storiesEncerrados", storiesEncerrados);
        auditoriaRepository.saveAndFlush(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                atorId,
                acao,
                "USUARIO",
                usuarioId,
                json(antes),
                json(depois),
                requestId,
                agora));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria sanitizada", exception);
        }
    }

    private void invalidarSessoesAposCommit(UUID usuarioId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sessions.invalidateAll(usuarioId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessions.invalidateAll(usuarioId);
            }
        });
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
