package br.com.topsdojob.v3.application.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.usuario.PapelPermissaoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PermissaoEntity;
import br.com.topsdojob.v3.persistence.repository.PapelPermissaoRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PermissaoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRbacServiceTest {

    @Test
    void adminRecebeMidiaRevisarSomentePeloVinculoCanonicoDoBanco() {
        UUID permissaoId = UUID.randomUUID();
        PapelPermissaoRepository papelPermissaoRepository = mock(PapelPermissaoRepository.class);
        PermissaoRepository permissaoRepository = mock(PermissaoRepository.class);
        PapelPermissaoEntity vinculo = mock(PapelPermissaoEntity.class);
        PermissaoEntity permissao = mock(PermissaoEntity.class);
        when(vinculo.getPermissaoId()).thenReturn(permissaoId);
        when(permissao.getId()).thenReturn(permissaoId);
        when(permissao.getCodigo()).thenReturn("MIDIA_REVISAR");
        when(permissao.getDescricao()).thenReturn("Revisar midias");
        when(papelPermissaoRepository.findByPapelIn(List.of(PapelUsuario.ADMIN))).thenReturn(List.of(vinculo));
        when(permissaoRepository.findByIdIn(List.of(permissaoId))).thenReturn(List.of(permissao));
        AdminRbacService service = new AdminRbacService(
                mock(PapelUsuarioRepository.class), papelPermissaoRepository, permissaoRepository);

        assertThat(service.permissoes(List.of(PapelUsuario.ADMIN)))
                .singleElement()
                .satisfies(item -> assertThat(item.codigo()).isEqualTo("MIDIA_REVISAR"));
    }

    @Test
    void perfilSemVinculoNaoRecebeMidiaRevisar() {
        PapelPermissaoRepository papelPermissaoRepository = mock(PapelPermissaoRepository.class);
        when(papelPermissaoRepository.findByPapelIn(List.of(PapelUsuario.USUARIO))).thenReturn(List.of());
        AdminRbacService service = new AdminRbacService(
                mock(PapelUsuarioRepository.class), papelPermissaoRepository, mock(PermissaoRepository.class));

        assertThat(service.permissoes(List.of(PapelUsuario.USUARIO))).isEmpty();
    }
}
