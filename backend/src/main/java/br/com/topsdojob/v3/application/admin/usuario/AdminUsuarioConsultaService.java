package br.com.topsdojob.v3.application.admin.usuario;

import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAnuncioDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioHistoricoDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioIndicadoresDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioResumoDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioConsultaJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioConsultaJdbcRepository.UsuarioRow;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUsuarioConsultaService {

    private static final Set<String> KYC_STATUS = Set.of(
            "SEM_ENVIO",
            "PENDENTE",
            "EM_ANALISE",
            "APROVADO",
            "REJEITADO",
            "AJUSTE_SOLICITADO");

    private final AdminUsuarioConsultaJdbcRepository consultaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioBloqueioJuridicoRepository bloqueioRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final AdminKycService kycService;

    public AdminUsuarioConsultaService(
            AdminUsuarioConsultaJdbcRepository consultaRepository,
            UsuarioRepository usuarioRepository,
            AnuncioRepository anuncioRepository,
            AnuncioBloqueioJuridicoRepository bloqueioRepository,
            AuditoriaEventoRepository auditoriaRepository,
            AdminKycService kycService) {
        this.consultaRepository = consultaRepository;
        this.usuarioRepository = usuarioRepository;
        this.anuncioRepository = anuncioRepository;
        this.bloqueioRepository = bloqueioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.kycService = kycService;
    }

    @Transactional(readOnly = true)
    public AdminPaginaDto<AdminUsuarioResumoDto> listar(
            String termo,
            String status,
            String kyc,
            String grupo,
            String uf,
            String cidade,
            String ordenacao,
            int page,
            int size) {
        String termoSeguro = textoOpcional(termo, 120);
        String statusSeguro = status(status);
        String kycSeguro = kyc(kyc);
        String grupoSeguro = grupo(grupo);
        String ufSegura = uf(uf);
        String cidadeSegura = cidade(cidade, ufSegura);
        String ordenacaoSegura = ordenacao(ordenacao);
        int paginaSegura = Math.max(page, 0);
        int tamanhoSeguro = Math.max(1, Math.min(size, 100));
        String digitos = digitos(termoSeguro);
        String cpfSufixo = cpfSufixo(termoSeguro);
        var resultado = consultaRepository.listar(
                termoSeguro,
                digitos,
                cpfSufixo,
                statusSeguro,
                kycSeguro,
                grupoSeguro,
                ufSegura,
                cidadeSegura,
                ordenacaoSegura,
                PageRequest.of(paginaSegura, tamanhoSeguro));
        return new AdminPaginaDto<>(
                resultado.getContent().stream().map(this::resumo).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isLast());
    }

    @Transactional(readOnly = true)
    public AdminUsuarioIndicadoresDto indicadores() {
        var row = consultaRepository.indicadores();
        return new AdminUsuarioIndicadoresDto(
                row.totalUsuarios(),
                row.novosHoje(),
                row.comAnuncios(),
                row.semAnuncios());
    }

    @Transactional(readOnly = true)
    public AdminUsuarioDetalheDto detalhar(UUID usuarioId, AdminUserPrincipal ator) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
        List<AnuncioEntity> anuncios = anuncioRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
        List<AdminKycEnvioDto> envios = kycService.listarPorUsuario(usuarioId);
        var bloqueio = bloqueioRepository
                .findFirstByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNullOrderByBloqueadoEmDesc(
                        usuarioId,
                        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO);
        boolean admin = ator != null && ator.papeis().contains(PapelUsuario.ADMIN);
        UUID anuncioAncora = bloqueio.map(item -> item.getAnuncioId())
                .orElseGet(() -> anuncios.stream()
                        .filter(item -> item.getRemovidoEm() == null
                                && item.getStatus() != StatusAnuncio.BLOQUEADO
                                && item.getStatus() != StatusAnuncio.REMOVIDO)
                        .map(AnuncioEntity::getId)
                        .findFirst()
                        .orElse(null));
        String cpf = admin ? usuario.getCpfNormalizado() : mascararCpf(usuario.getCpfNormalizado());

        return new AdminUsuarioDetalheDto(
                usuario.getId(),
                usuario.getNome(),
                usuario.getNomeCivil(),
                usuario.getEmailNormalizado(),
                usuario.getTelefoneNormalizado(),
                cpf,
                !admin && cpf != null,
                usuario.getDataNascimento(),
                usuario.getStatus().name(),
                usuario.getTipoConta().name(),
                kyc(envios),
                bloqueio.isPresent(),
                usuario.getCriadoEm(),
                usuario.getAtualizadoEm(),
                anuncioAncora,
                admin && bloqueio.isEmpty() && anuncioAncora != null
                        && usuario.getStatus() != StatusUsuario.SUSPENSO
                        && usuario.getStatus() != StatusUsuario.DESATIVADO,
                admin && bloqueio.isPresent() && anuncioAncora != null,
                usuario.getVersao(),
                anuncios.stream().map(this::anuncio).toList(),
                envios,
                auditoriaRepository.findByRecursoTipoAndRecursoIdOrderByCriadoEmDesc(
                                "USUARIO",
                                usuarioId,
                                PageRequest.of(0, 50))
                        .stream()
                        .map(evento -> new AdminUsuarioHistoricoDto(
                                evento.getId(),
                                evento.getAcao(),
                                evento.getResultado().name(),
                                evento.getRequestId(),
                                evento.getCriadoEm()))
                        .toList());
    }

    private AdminUsuarioResumoDto resumo(UsuarioRow row) {
        return new AdminUsuarioResumoDto(
                row.id(),
                nome(row),
                row.email(),
                row.telefone(),
                mascararCpf(row.cpf()),
                row.status(),
                row.kycStatus(),
                row.totalAnuncios(),
                row.bloqueado(),
                row.totalAnuncios() == 0
                        && "SEM_ENVIO".equals(row.kycStatus())
                        && "ANUNCIANTE".equals(row.tipoConta())
                        && !"IMPORTADO".equals(row.status()),
                row.ufPrincipal(),
                row.cidadePrincipal(),
                row.criadoEm());
    }

    private String nome(UsuarioRow row) {
        return row.nomeCivil() == null || row.nomeCivil().isBlank() ? row.nome() : row.nomeCivil();
    }

    private AdminUsuarioAnuncioDto anuncio(AnuncioEntity anuncio) {
        return new AdminUsuarioAnuncioDto(
                anuncio.getId(),
                anuncio.getSlug(),
                anuncio.getTitulo(),
                anuncio.getStatus().name(),
                anuncio.getStatusModeracao().name(),
                anuncio.getCriadoEm(),
                anuncio.getAtualizadoEm());
    }

    private String kyc(List<AdminKycEnvioDto> envios) {
        return envios.isEmpty() ? "SEM_ENVIO" : envios.get(0).status();
    }

    private String status(String value) {
        String normalizado = enumOpcional(value);
        if (normalizado == null || "TODOS".equals(normalizado)) {
            return null;
        }
        try {
            return StatusUsuario.valueOf(normalizado).name();
        } catch (IllegalArgumentException exception) {
            throw invalido("status de usuario invalido");
        }
    }

    private String kyc(String value) {
        String normalizado = enumOpcional(value);
        if (normalizado == null || "TODOS".equals(normalizado)) {
            return null;
        }
        if (!KYC_STATUS.contains(normalizado)) {
            throw invalido("status de KYC invalido");
        }
        return normalizado;
    }

    private String ordenacao(String value) {
        String normalizado = enumOpcional(value);
        if (normalizado == null || "RECENTES".equals(normalizado)) {
            return "RECENTES";
        }
        if ("ANTIGOS".equals(normalizado)) {
            return "ANTIGOS";
        }
        throw invalido("ordenacao de usuarios invalida");
    }

    private String grupo(String value) {
        String normalizado = enumOpcional(value);
        if (normalizado == null || "TODOS".equals(normalizado)) {
            return null;
        }
        if (Set.of("ATIVOS", "INATIVOS", "COM_ANUNCIOS", "SEM_ANUNCIOS").contains(normalizado)) {
            return normalizado;
        }
        throw invalido("grupo de usuarios invalido");
    }

    private String uf(String value) {
        String normalizado = enumOpcional(value);
        if (normalizado == null || "TODAS".equals(normalizado)) {
            return null;
        }
        if (!normalizado.matches("[A-Z]{2}")) {
            throw invalido("UF invalida");
        }
        return normalizado;
    }

    private String cidade(String value, String uf) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (uf == null) {
            throw invalido("selecione a UF antes da cidade");
        }
        String normalizado = value.trim().toLowerCase(Locale.ROOT);
        if (normalizado.length() > 160 || !normalizado.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw invalido("cidade invalida");
        }
        return normalizado;
    }

    private String enumOpcional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String textoOpcional(String value, int maximo) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizado = value.trim().replaceAll("\\s+", " ");
        if (normalizado.length() > maximo) {
            throw invalido("termo de busca muito longo");
        }
        return normalizado;
    }

    private String digitos(String value) {
        if (value == null) {
            return null;
        }
        String digitos = value.replaceAll("\\D", "");
        return digitos.length() >= 3 ? digitos : null;
    }

    private String cpfSufixo(String value) {
        if (value == null || !value.contains("*")) {
            return null;
        }
        String digitos = value.replaceAll("\\D", "");
        return digitos.length() == 2 ? digitos : null;
    }

    private String mascararCpf(String cpf) {
        return cpf == null || cpf.length() != 11 ? null : "***.***.***-" + cpf.substring(9);
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
