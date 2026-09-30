package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioMidiaUploadDto;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService.ItemUpload;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService.ResultadoUpload;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoContaUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioMidiaUploadService {

    private static final Set<String> AUTORIDADES_OBRIGATORIAS = Set.of(
            "ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR");

    private final AnuncioRepository anuncioRepository;
    private final UsuarioRepository usuarioRepository;
    private final AnuncioBloqueioJuridicoRepository bloqueioRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final MidiaUploadValidator uploadValidator;
    private final AnuncioMidiaUploadCoreService uploadCoreService;

    public AdminAnuncioMidiaUploadService(
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            AnuncioBloqueioJuridicoRepository bloqueioRepository,
            RevisaoAnuncioRepository revisaoRepository,
            MidiaUploadValidator uploadValidator,
            AnuncioMidiaUploadCoreService uploadCoreService) {
        this.anuncioRepository = anuncioRepository;
        this.usuarioRepository = usuarioRepository;
        this.bloqueioRepository = bloqueioRepository;
        this.revisaoRepository = revisaoRepository;
        this.uploadValidator = uploadValidator;
        this.uploadCoreService = uploadCoreService;
    }

    @Transactional
    public AdminAnuncioMidiaUploadDto enviar(
            UUID anuncioId,
            MultipartFile arquivo,
            String idempotencyKey,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAtor(administrador);
        if (anuncioId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "anuncioId obrigatorio");
        }
        MidiaValidada validada = uploadValidator.validar(arquivo);
        UUID proprietarioId = null;
        if (validada.video()) {
            proprietarioId = anuncioRepository.findUsuarioIdById(anuncioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
            UsuarioEntity proprietario = usuarioRepository.findByIdForUpdate(proprietarioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "proprietario do anuncio nao encontrado"));
            if (proprietario.getStatus() != StatusUsuario.ATIVO
                    || proprietario.getTipoConta() != TipoContaUsuario.ANUNCIANTE
                    || proprietario.getDesativadoEm() != null
                    || proprietario.getExcluidoEm() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "proprietario do anuncio nao pode receber midia");
            }
        }
        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .filter(item -> item.getRemovidoEm() == null)
                .filter(item -> item.getStatus() != StatusAnuncio.REMOVIDO)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "anuncio nao encontrado"));
        if (validada.video()) {
            if (!proprietarioId.equals(anuncio.getUsuarioId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado");
            }
            if (anuncio.getStatus() == StatusAnuncio.BLOQUEADO
                    || bloqueioRepository.findAtivoPorAnuncioForUpdate(anuncioId).isPresent()
                    || bloqueioRepository.findAtivoPorUsuarioForUpdate(
                            proprietarioId, EscopoBloqueioJuridico.ANUNCIO_E_USUARIO).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "bloqueio juridico impede alteracao do anuncio");
            }
            if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                    anuncioId, List.of(StatusRevisaoAnuncio.EM_ANALISE))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "anuncio possui revisao em analise");
            }
        }

        ResultadoUpload resultado = uploadCoreService.enviarAdministrativo(
                anuncio, validada, idempotencyKey);
        ItemUpload item = resultado.itemUnico();
        return new AdminAnuncioMidiaUploadDto(
                item.midiaId(),
                item.anuncioId(),
                item.tipo().name(),
                item.finalidade().name(),
                item.ordem(),
                item.status().name(),
                item.statusArquivo().name(),
                resultado.idempotente(),
                requestId == null ? "" : requestId);
    }

    private void validarAtor(AdminUserPrincipal administrador) {
        if (administrador == null || administrador.usuarioId() == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
        Set<String> concedidas = administrador.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (!concedidas.containsAll(AUTORIDADES_OBRIGATORIAS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "permissoes administrativas insuficientes");
        }
    }
}
