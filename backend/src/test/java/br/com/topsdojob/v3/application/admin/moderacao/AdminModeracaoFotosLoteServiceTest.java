package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLotePrevalidacaoService.ItemValidado;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLotePrevalidacaoService.Prevalidacao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoFotoLoteAcao;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminResultadoFotoLoteItemDto;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminModeracaoFotosLoteServiceTest {

    private final AdminModeracaoFotosLotePrevalidacaoService prevalidacao =
            mock(AdminModeracaoFotosLotePrevalidacaoService.class);
    private final AdminModeracaoFotosLoteItemService itemService =
            mock(AdminModeracaoFotosLoteItemService.class);
    private final AdminModeracaoFotosLoteAuditoriaService auditoria =
            mock(AdminModeracaoFotosLoteAuditoriaService.class);
    private final AdminModeracaoFotosLoteService service =
            new AdminModeracaoFotosLoteService(prevalidacao, itemService, auditoria);
    private final UUID anuncioId = UUID.randomUUID();
    private final AdminUserPrincipal ator = principal();

    @Test
    void loteMistoMantemSucessosEIdentificaFalhaR2Individual() {
        ItemValidado livre = item(AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.LIVRE, false);
        ItemValidado restrita = item(
                AdminDecisaoFotoLoteAcao.APROVAR,
                VisibilidadeMidia.RESTRITA_18,
                false);
        ItemValidado excluir = item(AdminDecisaoFotoLoteAcao.EXCLUIR, null, false);
        when(prevalidacao.validar(any(), any(), any()))
                .thenReturn(new Prevalidacao(anuncioId, List.of(livre, restrita, excluir)));
        when(itemService.executar(anuncioId, livre, ator, "request-lote"))
                .thenReturn(resultado(livre, "APROVADA", "PUBLICAVEL"));
        when(itemService.executar(anuncioId, restrita, ator, "request-lote"))
                .thenReturn(resultado(restrita, "APROVADA", "PUBLICAVEL"));
        when(itemService.executar(anuncioId, excluir, ator, "request-lote"))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "storage indisponivel"));

        var resposta = service.decidir(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of()),
                ator,
                "request-lote");

        assertThat(resposta.aprovadas()).isEqualTo(2);
        assertThat(resposta.excluidas()).isZero();
        assertThat(resposta.falhas()).isEqualTo(1);
        assertThat(resposta.concluido()).isFalse();
        assertThat(resposta.resultados()).last().satisfies(item -> {
            assertThat(item.resultado()).isEqualTo("FALHA");
            assertThat(item.motivo()).contains("armazenamento").contains("Tente novamente");
        });
        verify(auditoria).registrarFalha(
                ator.usuarioId(),
                excluir.mediaId(),
                "request-lote",
                "FALHA_OPERACIONAL_R2");
        verify(auditoria).registrarLote(ator.usuarioId(), resposta);
    }

    @Test
    void retryIntegralmenteProcessadoNaoExecutaItensNemDuplicaAuditoria() {
        ItemValidado livre = item(AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.LIVRE, true);
        ItemValidado excluir = item(AdminDecisaoFotoLoteAcao.EXCLUIR, null, true);
        when(prevalidacao.validar(any(), any(), any()))
                .thenReturn(new Prevalidacao(anuncioId, List.of(livre, excluir)));

        var resposta = service.decidir(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of()),
                ator,
                "request-retry");

        assertThat(resposta.jaProcessadas()).isEqualTo(2);
        assertThat(resposta.concluido()).isTrue();
        verify(itemService, never()).executar(any(), any(), any(), any());
        verify(auditoria, never()).registrarLote(any(), any());
        verify(auditoria, never()).registrarFalha(any(), any(), any(), any());
    }

    @Test
    void identificadorDeRequisicaoAusenteFalhaFechadoAntesDaPrevalidacao() {
        assertThatThrownBy(() -> service.decidir(
                anuncioId,
                new AdminDecidirFotosLoteRequestDto(List.of()),
                ator,
                " "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("identificador");

        verify(prevalidacao, never()).validar(any(), any(), any());
    }

    private ItemValidado item(
            AdminDecisaoFotoLoteAcao decisao,
            VisibilidadeMidia classificacao,
            boolean jaProcessada) {
        return new ItemValidado(
                UUID.randomUUID(),
                decisao,
                classificacao,
                classificacao == VisibilidadeMidia.RESTRITA_18 ? "observacao individual" : null,
                jaProcessada);
    }

    private AdminResultadoFotoLoteItemDto resultado(
            ItemValidado item,
            String resultado,
            String status) {
        return new AdminResultadoFotoLoteItemDto(
                item.mediaId(),
                item.decisao().name(),
                item.classificacao() == null ? null : item.classificacao().name(),
                resultado,
                status,
                null);
    }

    private static AdminUserPrincipal principal() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin",
                "admin@example.invalid",
                "hash",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("MIDIA_REVISAR")),
                true);
    }
}
