package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioProprietarioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioProprietarioAtualizacaoService {

    private final AnuncioRepository anuncioRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdminUsuarioAtualizacaoService usuarioAtualizacaoService;

    public AdminAnuncioProprietarioAtualizacaoService(
            AnuncioRepository anuncioRepository,
            UsuarioRepository usuarioRepository,
            AdminUsuarioAtualizacaoService usuarioAtualizacaoService) {
        this.anuncioRepository = anuncioRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioAtualizacaoService = usuarioAtualizacaoService;
    }

    @Transactional
    public AdminUsuarioDetalheDto atualizar(
            UUID anuncioId,
            AdminAnuncioProprietarioAtualizacaoRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload obrigatorio");
        }
        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        UUID usuarioId = anuncio.getUsuarioId();
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "anuncio sem proprietario canonico");
        }
        UsuarioEntity usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));

        AdminUsuarioAtualizacaoRequestDto atualizacao = new AdminUsuarioAtualizacaoRequestDto();
        atualizacao.setVersao(usuario.getVersao());
        atualizacao.setNomeCivil(request.nome());
        atualizacao.setCpf(request.cpf());
        return usuarioAtualizacaoService.atualizar(
                usuarioId,
                atualizacao,
                administrador,
                requestId);
    }
}
