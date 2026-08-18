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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
            new AdminModeracaoFotosLoteService(prevalidacao, itemService, auditoria, Runnable::run);
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
                .thenReturn(new Prevalidacao(anuncioId, List.of(livre, restrita, excluir), 0L));
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
            assertThat(item.codigo()).isEqualTo("FALHA_OPERACIONAL_R2");
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
                .thenReturn(new Prevalidacao(anuncioId, List.of(livre, excluir), 2L));

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

    @Test
    void aprovacoesIndependentesUsamConcorrenciaLimitadaEPreservamOrdem() throws Exception {
        List<ItemValidado> itens = List.of(
                item(AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.LIVRE, false),
                item(AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, false),
                item(AdminDecisaoFotoLoteAcao.APROVAR, VisibilidadeMidia.LIVRE, false));
        when(prevalidacao.validar(any(), any(), any()))
                .thenReturn(new Prevalidacao(anuncioId, itens, 1L));

        AtomicInteger ativos = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        CountDownLatch iniciados = new CountDownLatch(itens.size());
        CountDownLatch liberar = new CountDownLatch(1);
        when(itemService.executar(any(), any(), any(), any())).thenAnswer(invocation -> {
            ItemValidado item = invocation.getArgument(1);
            int atuais = ativos.incrementAndGet();
            maximo.accumulateAndGet(atuais, Math::max);
            iniciados.countDown();
            if (iniciados.getCount() == 0) {
                liberar.countDown();
            }
            try {
                if (!liberar.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("onda de moderacao nao iniciou em paralelo");
                }
                return resultado(item, "APROVADA", "PUBLICAVEL");
            } finally {
                ativos.decrementAndGet();
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            var concorrente = new AdminModeracaoFotosLoteService(
                    prevalidacao,
                    itemService,
                    auditoria,
                    executor::execute);
            var resposta = concorrente.decidir(
                    anuncioId,
                    new AdminDecidirFotosLoteRequestDto(List.of()),
                    ator,
                    "request-concorrente");

            assertThat(maximo).hasValue(3);
            assertThat(resposta.resultados())
                    .extracting(AdminResultadoFotoLoteItemDto::mediaId)
                    .containsExactlyElementsOf(itens.stream().map(ItemValidado::mediaId).toList());
            assertThat(resposta.aprovadas()).isEqualTo(3);
            assertThat(resposta.falhas()).isZero();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void quintaFotoConcluiAntesDaOndaQueDependeDoBeneficioPremium() throws Exception {
        ItemValidado quinta = item(
                AdminDecisaoFotoLoteAcao.APROVAR,
                VisibilidadeMidia.LIVRE,
                false);
        ItemValidado sexta = item(
                AdminDecisaoFotoLoteAcao.APROVAR,
                VisibilidadeMidia.RESTRITA_18,
                false);
        ItemValidado setima = item(
                AdminDecisaoFotoLoteAcao.APROVAR,
                VisibilidadeMidia.LIVRE,
                false);
        List<ItemValidado> itens = List.of(quinta, sexta, setima);
        when(prevalidacao.validar(any(), any(), any()))
                .thenReturn(new Prevalidacao(anuncioId, itens, 4L));
        AtomicBoolean quintaConcluida = new AtomicBoolean();
        when(itemService.executar(any(), any(), any(), any())).thenAnswer(invocation -> {
            ItemValidado item = invocation.getArgument(1);
            if (item.mediaId().equals(quinta.mediaId())) {
                quintaConcluida.set(true);
            } else if (!quintaConcluida.get()) {
                throw new IllegalStateException("foto extra iniciou antes da ativacao Premium");
            }
            return resultado(item, "APROVADA", "PUBLICAVEL");
        });

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            var concorrente = new AdminModeracaoFotosLoteService(
                    prevalidacao,
                    itemService,
                    auditoria,
                    executor::execute);
            var resposta = concorrente.decidir(
                    anuncioId,
                    new AdminDecidirFotosLoteRequestDto(List.of()),
                    ator,
                    "request-premium");

            assertThat(quintaConcluida).isTrue();
            assertThat(resposta.aprovadas()).isEqualTo(3);
            assertThat(resposta.resultados())
                    .extracting(AdminResultadoFotoLoteItemDto::mediaId)
                    .containsExactlyElementsOf(itens.stream().map(ItemValidado::mediaId).toList());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
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
                null,
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
