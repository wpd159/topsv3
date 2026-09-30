package br.com.topsdojob.v3.application.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.usuario.PapelPermissaoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
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
    void exportacaoExigeVinculoIndividualDoPapelTecnicoSemExpandirAdmin() {
        UUID responsavelId = UUID.randomUUID();
        UUID outroAdminId = UUID.randomUUID();
        UUID leituraId = UUID.randomUUID();
        UUID exportacaoId = UUID.randomUUID();
        PapelUsuarioRepository papeis = mock(PapelUsuarioRepository.class);
        PapelPermissaoRepository vinculos = mock(PapelPermissaoRepository.class);
        PermissaoRepository permissoes = mock(PermissaoRepository.class);
        PapelUsuarioEntity papelAdmin = mock(PapelUsuarioEntity.class);
        PapelUsuarioEntity papelExportador = mock(PapelUsuarioEntity.class);
        when(papelAdmin.getPapel()).thenReturn(PapelUsuario.ADMIN);
        when(papelExportador.getPapel()).thenReturn(PapelUsuario.ARQUIVO_EXPORTADOR);
        when(papeis.findByUsuarioId(responsavelId)).thenReturn(List.of(papelAdmin, papelExportador));
        when(papeis.findByUsuarioId(outroAdminId)).thenReturn(List.of(papelAdmin));

        PapelPermissaoEntity leitura = mock(PapelPermissaoEntity.class);
        PapelPermissaoEntity exportacao = mock(PapelPermissaoEntity.class);
        when(leitura.getPermissaoId()).thenReturn(leituraId);
        when(exportacao.getPermissaoId()).thenReturn(exportacaoId);
        when(vinculos.findByPapelIn(List.of(PapelUsuario.ADMIN, PapelUsuario.ARQUIVO_EXPORTADOR)))
                .thenReturn(List.of(leitura, exportacao));
        when(vinculos.findByPapelIn(List.of(PapelUsuario.ADMIN))).thenReturn(List.of(leitura));

        PermissaoEntity ler = mock(PermissaoEntity.class);
        PermissaoEntity exportar = mock(PermissaoEntity.class);
        when(ler.getId()).thenReturn(leituraId);
        when(ler.getCodigo()).thenReturn("ARQUIVO_PUBLICIDADE_LER");
        when(ler.getDescricao()).thenReturn("Consultar arquivo");
        when(exportar.getId()).thenReturn(exportacaoId);
        when(exportar.getCodigo()).thenReturn("ARQUIVO_PUBLICIDADE_EXPORTAR");
        when(exportar.getDescricao()).thenReturn("Exportar arquivo");
        when(permissoes.findByIdIn(List.of(leituraId, exportacaoId)))
                .thenReturn(List.of(ler, exportar));
        when(permissoes.findByIdIn(List.of(leituraId))).thenReturn(List.of(ler));

        AdminRbacService service = new AdminRbacService(papeis, vinculos, permissoes);
        assertThat(service.permissoes(service.papeis(responsavelId)))
                .extracting(item -> item.codigo())
                .containsExactly("ARQUIVO_PUBLICIDADE_EXPORTAR", "ARQUIVO_PUBLICIDADE_LER");
        assertThat(service.permissoes(service.papeis(outroAdminId)))
                .extracting(item -> item.codigo())
                .containsExactly("ARQUIVO_PUBLICIDADE_LER");
    }

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
