package br.com.topsdojob.v3.security.admin;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AdminUserPrincipal implements UserDetails {

    private final UUID usuarioId;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final List<PapelUsuario> papeis;
    private final List<AdminPermissionDto> permissoes;
    private final List<GrantedAuthority> authorities;
    private final boolean enabled;

    public AdminUserPrincipal(
            UUID usuarioId,
            String nome,
            String email,
            String senhaHash,
            List<PapelUsuario> papeis,
            List<AdminPermissionDto> permissoes,
            List<GrantedAuthority> authorities,
            boolean enabled) {
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papeis = List.copyOf(papeis);
        this.permissoes = List.copyOf(permissoes);
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public String nome() {
        return nome;
    }

    public String email() {
        return email;
    }

    public List<PapelUsuario> papeis() {
        return papeis;
    }

    public List<AdminPermissionDto> permissoes() {
        return permissoes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
