package br.com.topsdojob.v3.security.admin;

import br.com.topsdojob.v3.application.admin.auth.AdminRbacService;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusUsuario;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private static final String ALGORITMO_BCRYPT = "BCRYPT";

    private final UsuarioRepository usuarioRepository;
    private final CredencialUsuarioRepository credencialRepository;
    private final AdminRbacService rbacService;

    public AdminUserDetailsService(
            UsuarioRepository usuarioRepository,
            CredencialUsuarioRepository credencialRepository,
            AdminRbacService rbacService) {
        this.usuarioRepository = usuarioRepository;
        this.credencialRepository = credencialRepository;
        this.rbacService = rbacService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String login = normalizarLogin(username);
        UsuarioEntity usuario = usuarioRepository.findByEmailNormalizado(login)
                .orElseThrow(() -> new UsernameNotFoundException("credenciais invalidas"));
        boolean enabled = usuario.getStatus() == StatusUsuario.ATIVO && usuario.getDesativadoEm() == null;
        if (!enabled) {
            throw new BadCredentialsException("credenciais invalidas");
        }
        CredencialUsuarioEntity credencial = credencialRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new BadCredentialsException("credenciais invalidas"));
        if (!ALGORITMO_BCRYPT.equalsIgnoreCase(credencial.getAlgoritmo())) {
            throw new BadCredentialsException("credenciais invalidas");
        }
        List<PapelUsuario> papeis = rbacService.papeis(usuario.getId());
        if (papeis.isEmpty()) {
            throw new BadCredentialsException("credenciais invalidas");
        }
        List<AdminPermissionDto> permissoes = rbacService.permissoes(papeis);
        return new AdminUserPrincipal(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmailNormalizado(),
                credencial.getSenhaHash(),
                papeis,
                permissoes,
                authorities(papeis, permissoes),
                true);
    }

    private List<GrantedAuthority> authorities(List<PapelUsuario> papeis, List<AdminPermissionDto> permissoes) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        papeis.forEach(papel -> authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name())));
        permissoes.forEach(permissao -> authorities.add(new SimpleGrantedAuthority(permissao.codigo())));
        return List.copyOf(authorities);
    }

    public static String normalizarLogin(String login) {
        if (login == null) {
            return "";
        }
        return login.trim().toLowerCase(Locale.ROOT);
    }
}
