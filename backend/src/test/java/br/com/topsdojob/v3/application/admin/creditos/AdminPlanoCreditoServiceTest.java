package br.com.topsdojob.v3.application.admin.creditos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.AtualizarRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.CriarRequest;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminPlanoCreditoDtos.StatusRequest;
import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminPlanoCreditoConsultaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminPlanoCreditoServiceTest {

    private final PlanoCreditoRepository planos = mock(PlanoCreditoRepository.class);
    private final AdminPlanoCreditoConsultaRepository consultas = mock(AdminPlanoCreditoConsultaRepository.class);
    private final AdminCreditoOperacaoService auditoria = mock(AdminCreditoOperacaoService.class);
    private AdminPlanoCreditoService service;

    @BeforeEach
    void setUp() {
        service = new AdminPlanoCreditoService(planos, consultas, auditoria);
    }

    @Test
    void listaFiltraStatusBuscaEContaSomenteComprasConfirmadasDaConsulta() {
        PlanoCreditoEntity ativo = plano("PACOTE_ATIVO", true);
        PlanoCreditoEntity inativo = plano("PACOTE_INATIVO", false);
        when(planos.findAllByOrderByOrdemExibicaoAscCodigoAsc()).thenReturn(List.of(ativo, inativo));
        when(consultas.comprasConfirmadas(List.of(ativo.getId()))).thenReturn(Map.of(ativo.getId(), 3L));

        var resultado = service.listar("ATIVO", "ATIVOS");

        assertThat(resultado).singleElement().satisfies(item -> {
            assertThat(item.codigo()).isEqualTo("PACOTE_ATIVO");
            assertThat(item.comprasConfirmadas()).isEqualTo(3);
        });
    }

    @Test
    void criaPlanoInativoComPrecoMonetarioSemTocarLedger() {
        when(planos.existsByCodigoIgnoreCase("PACOTE_QA_100")).thenReturn(false);
        when(planos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.criar(
                new CriarRequest(
                        "pacote_qa_100",
                        "Plano QA",
                        "Somente homologacao",
                        100,
                        new BigDecimal("49.90"),
                        false,
                        20),
                admin(),
                "req-plano-create");

        assertThat(resultado.codigo()).isEqualTo("PACOTE_QA_100");
        assertThat(resultado.valor()).isEqualByComparingTo("49.90");
        assertThat(resultado.ativo()).isFalse();
        verify(auditoria).auditar(
                eq(admin().usuarioId()),
                eq("CREDITO_PACOTE_CRIAR"),
                eq("PLANO_CREDITO"),
                any(),
                any(),
                any(),
                eq("req-plano-create"));
    }

    @Test
    void rejeitaPrecoOuCreditosInvalidosAntesDePersistir() {
        assertThatThrownBy(() -> service.criar(
                new CriarRequest("PACOTE_QA", "Plano QA", "", 0, BigDecimal.ZERO, false, 0),
                admin(),
                "req-invalid"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(planos, never()).saveAndFlush(any());
    }

    @Test
    void retryDeCriacaoComMesmoCodigoNaoDuplicaPlano() {
        when(planos.existsByCodigoIgnoreCase("PACOTE_QA")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(
                new CriarRequest(
                        "PACOTE_QA",
                        "Plano QA",
                        "",
                        100,
                        new BigDecimal("49.90"),
                        false,
                        10),
                admin(),
                "req-duplicate"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(planos, never()).saveAndFlush(any());
    }

    @Test
    void versaoDesatualizadaRetornaConflitoSemEditar() {
        PlanoCreditoEntity plano = plano("PACOTE_QA", false);
        when(planos.findByIdForUpdate(plano.getId())).thenReturn(Optional.of(plano));

        assertThatThrownBy(() -> service.atualizar(
                plano.getId(),
                new AtualizarRequest(
                        "Plano alterado",
                        "",
                        200,
                        new BigDecimal("79.90"),
                        10,
                        plano.getAtualizadoEm().minusSeconds(1)),
                admin(),
                "req-stale"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(planos, never()).saveAndFlush(any());
    }

    @Test
    void edicaoPreservaCodigoStatusEHistoricoDeCompras() {
        PlanoCreditoEntity plano = plano("PACOTE_QA", true);
        when(planos.findByIdForUpdate(plano.getId())).thenReturn(Optional.of(plano));
        when(planos.saveAndFlush(plano)).thenReturn(plano);
        when(consultas.comprasConfirmadas(List.of(plano.getId()))).thenReturn(Map.of(plano.getId(), 4L));

        var resultado = service.atualizar(
                plano.getId(),
                new AtualizarRequest(
                        "Plano QA editado",
                        "Descricao editada",
                        150,
                        new BigDecimal("59.90"),
                        30,
                        plano.getAtualizadoEm()),
                admin(),
                "req-update");

        assertThat(resultado.codigo()).isEqualTo("PACOTE_QA");
        assertThat(resultado.ativo()).isTrue();
        assertThat(resultado.comprasConfirmadas()).isEqualTo(4);
        assertThat(resultado.valor()).isEqualByComparingTo("59.90");
    }

    @Test
    void ativaEDesativaPreservandoMesmoRegistro() {
        PlanoCreditoEntity plano = plano("PACOTE_QA", false);
        when(planos.findByIdForUpdate(plano.getId())).thenReturn(Optional.of(plano));
        when(planos.saveAndFlush(plano)).thenReturn(plano);
        when(consultas.comprasConfirmadas(List.of(plano.getId()))).thenReturn(Map.of(plano.getId(), 2L));

        var ativo = service.ativar(
                plano.getId(),
                new StatusRequest(plano.getAtualizadoEm()),
                admin(),
                "req-activate");
        var inativo = service.desativar(
                plano.getId(),
                new StatusRequest(plano.getAtualizadoEm()),
                admin(),
                "req-deactivate");

        assertThat(ativo.ativo()).isTrue();
        assertThat(inativo.ativo()).isFalse();
        assertThat(inativo.comprasConfirmadas()).isEqualTo(2);
        verify(planos, never()).deleteById(any());
    }

    @Test
    void retryDeStatusJaAplicadoNaoCriaNovaAuditoria() {
        PlanoCreditoEntity plano = plano("PACOTE_QA", true);
        when(planos.findByIdForUpdate(plano.getId())).thenReturn(Optional.of(plano));
        when(consultas.comprasConfirmadas(List.of(plano.getId()))).thenReturn(Map.of());

        var resultado = service.ativar(
                plano.getId(),
                new StatusRequest(plano.getAtualizadoEm().minusDays(1)),
                admin(),
                "req-retry");

        assertThat(resultado.ativo()).isTrue();
        verify(planos, never()).saveAndFlush(any());
        verify(auditoria, never()).auditar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void moderadorNaoPodeAlterarPlanoMesmoChamandoServicoDiretamente() {
        assertThatThrownBy(() -> service.criar(
                new CriarRequest("PACOTE_QA", "Plano QA", "", 10, new BigDecimal("10.00"), false, 0),
                moderador(),
                "req-forbidden"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private PlanoCreditoEntity plano(String codigo, boolean ativo) {
        return PlanoCreditoEntity.criar(
                UUID.randomUUID(),
                codigo,
                codigo.replace('_', ' '),
                "Plano de teste",
                100,
                new BigDecimal("49.90"),
                ativo,
                10,
                OffsetDateTime.parse("2026-07-28T12:00:00Z"));
    }

    private AdminUserPrincipal admin() {
        return principal(PapelUsuario.ADMIN, true);
    }

    private AdminUserPrincipal moderador() {
        return principal(PapelUsuario.MODERADOR, false);
    }

    private AdminUserPrincipal principal(PapelUsuario papel, boolean gerenciar) {
        var authorities = new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
        if (gerenciar) {
            authorities.add(new SimpleGrantedAuthority("FINANCEIRO_GERENCIAR"));
        }
        return new AdminUserPrincipal(
                UUID.fromString("00000000-0000-4000-8000-000000000901"),
                "Operador QA",
                "operador.qa@example.invalid",
                "hash",
                List.of(papel),
                authorities.stream()
                        .map(authority -> new AdminPermissionDto(authority.getAuthority(), authority.getAuthority()))
                        .toList(),
                authorities,
                true);
    }
}
