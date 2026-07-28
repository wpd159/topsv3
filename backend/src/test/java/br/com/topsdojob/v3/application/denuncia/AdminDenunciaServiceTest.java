package br.com.topsdojob.v3.application.denuncia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminDenunciaServiceTest {

    private static final UUID DENUNCIA = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID ANUNCIO = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ADMIN = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final String REQUEST_ID = "request-denuncia-admin-001";

    private DenunciaJdbcRepository repository;
    private AuditoriaEventoRepository auditoriaRepository;
    private AdminDenunciaService service;
    private AdminUserPrincipal admin;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(DenunciaJdbcRepository.class);
        auditoriaRepository = org.mockito.Mockito.mock(AuditoriaEventoRepository.class);
        service = new AdminDenunciaService(repository, auditoriaRepository);
        admin = new AdminUserPrincipal(
                ADMIN,
                "Admin QA",
                "admin@example.invalid",
                "hash",
                List.of(PapelUsuario.ADMIN),
                List.of(new AdminPermissionDto("ANUNCIO_MODERAR", "Moderar")),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ANUNCIO_LER"),
                        new SimpleGrantedAuthority("ANUNCIO_MODERAR")),
                true);
    }

    @Test
    void registraProvidenciaSemAlterarAnuncio() {
        when(repository.porIdComLock(DENUNCIA)).thenReturn(Optional.of(row("PENDENTE", null)));
        when(repository.porId(DENUNCIA)).thenReturn(Optional.of(row("PUNIDA", "Analise concluida")));
        when(repository.historico(DENUNCIA)).thenReturn(List.of());

        var detalhe = service.alterarStatus(
                DENUNCIA,
                "PUNIDA",
                "<b>Analise concluida</b>",
                admin,
                REQUEST_ID);

        verify(repository).atualizarStatus(
                eq(DENUNCIA),
                eq("PUNIDA"),
                eq("Analise concluida"),
                eq(ADMIN),
                any());
        assertThat(detalhe.denuncia().status()).isEqualTo("PUNIDA");
        assertThat(detalhe.anuncio().status()).isEqualTo("PUBLICADO");
    }

    @Test
    void retryIdenticoNaoDuplicaAuditoriaNemAtualizacao() {
        DenunciaJdbcRepository.DenunciaRow finalizada = row("IGNORADA", "Sem evidencia suficiente");
        when(repository.porIdComLock(DENUNCIA)).thenReturn(Optional.of(finalizada));
        when(repository.porId(DENUNCIA)).thenReturn(Optional.of(finalizada));
        when(repository.historico(DENUNCIA)).thenReturn(List.of());

        var detalhe = service.alterarStatus(
                DENUNCIA,
                "IGNORADA",
                "Sem evidencia suficiente",
                admin,
                REQUEST_ID);

        assertThat(detalhe.denuncia().status()).isEqualTo("IGNORADA");
        verify(repository, never()).atualizarStatus(any(), anyString(), anyString(), any(), any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void decisaoDiferenteEmDenunciaFinalizadaRetorna409() {
        when(repository.porIdComLock(DENUNCIA))
                .thenReturn(Optional.of(row("PUNIDA", "Providencia anterior")));

        assertThatThrownBy(() -> service.alterarStatus(
                DENUNCIA,
                "IGNORADA",
                "Outra decisao",
                admin,
                REQUEST_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).atualizarStatus(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void listaVaziaEhEstadoLegitimo() {
        when(repository.contar(any(), any(), any(), any(), any())).thenReturn(0L);
        when(repository.listar(any(), any(), any(), any(), any(), eq(20), eq(0L)))
                .thenReturn(List.of());

        var pagina = service.listar(null, "TODOS", "TODOS", null, null, 0, 20);

        assertThat(pagina.itens()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }

    private DenunciaJdbcRepository.DenunciaRow row(String status, String providencia) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        return new DenunciaJdbcRepository.DenunciaRow(
                DENUNCIA,
                ANUNCIO,
                null,
                "a".repeat(64),
                "OUTROS",
                "Relato QA",
                status,
                providencia,
                "PENDENTE".equals(status) ? null : ADMIN,
                "denuncia-test-0001",
                REQUEST_ID,
                "b".repeat(64),
                "c".repeat(64),
                now,
                now,
                "PENDENTE".equals(status) ? null : now,
                0,
                "Anuncio QA",
                "anuncio-qa",
                "PUBLICADO",
                "APROVADO",
                null,
                null,
                "PENDENTE".equals(status) ? null : "Admin QA");
    }
}
