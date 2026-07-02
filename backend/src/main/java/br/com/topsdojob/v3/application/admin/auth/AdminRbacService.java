package br.com.topsdojob.v3.application.admin.auth;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelPermissaoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PermissaoEntity;
import br.com.topsdojob.v3.persistence.repository.PapelPermissaoRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PermissaoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRbacService {

    private final PapelUsuarioRepository papelUsuarioRepository;
    private final PapelPermissaoRepository papelPermissaoRepository;
    private final PermissaoRepository permissaoRepository;

    public AdminRbacService(
            PapelUsuarioRepository papelUsuarioRepository,
            PapelPermissaoRepository papelPermissaoRepository,
            PermissaoRepository permissaoRepository) {
        this.papelUsuarioRepository = papelUsuarioRepository;
        this.papelPermissaoRepository = papelPermissaoRepository;
        this.permissaoRepository = permissaoRepository;
    }

    @Transactional(readOnly = true)
    public List<PapelUsuario> papeis(UUID usuarioId) {
        return papelUsuarioRepository.findByUsuarioId(usuarioId).stream()
                .map(PapelUsuarioEntity::getPapel)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionDto> permissoes(List<PapelUsuario> papeis) {
        if (papeis == null || papeis.isEmpty()) {
            return List.of();
        }
        List<PapelPermissaoEntity> vinculos = papelPermissaoRepository.findByPapelIn(papeis);
        List<UUID> permissaoIds = vinculos.stream()
                .map(PapelPermissaoEntity::getPermissaoId)
                .distinct()
                .toList();
        if (permissaoIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, PermissaoEntity> permissoesPorId = permissaoRepository.findByIdIn(permissaoIds).stream()
                .collect(Collectors.toMap(PermissaoEntity::getId, Function.identity()));
        return permissaoIds.stream()
                .map(permissoesPorId::get)
                .filter(java.util.Objects::nonNull)
                .map(permissao -> new AdminPermissionDto(permissao.getCodigo(), permissao.getDescricao()))
                .sorted(Comparator.comparing(AdminPermissionDto::codigo))
                .toList();
    }
}
