package br.com.topsdojob.v3.application.admin.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioBloqueioJuridicoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioConsultaJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminUsuarioConsultaJdbcRepository.UsuarioRow;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class AdminUsuarioConsultaServiceTest {

    private final AdminUsuarioConsultaJdbcRepository consultaRepository =
            mock(AdminUsuarioConsultaJdbcRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioBloqueioJuridicoRepository bloqueioRepository =
            mock(AnuncioBloqueioJuridicoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository =
            mock(AuditoriaEventoRepository.class);
    private final AdminKycService kycService = mock(AdminKycService.class);
    private final AdminUsuarioConsultaService service = new AdminUsuarioConsultaService(
            consultaRepository,
            usuarioRepository,
            anuncioRepository,
            bloqueioRepository,
            auditoriaRepository,
            kycService);
    private UUID usuarioId;
    private UsuarioEntity usuario;
    private AnuncioEntity anuncio;

    @BeforeEach
    void preparar() {
        usuarioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.parse("2026-07-27T12:00:00Z");
        usuario = UsuarioEntity.criarCadastroPublico(
                usuarioId,
                "Nome publico",
                "qa@example.invalid",
                "+5562999999999",
                LocalDate.of(1990, 1, 2),
                agora.minusYears(1));
        usuario.aplicarDadosKyc(
                "Nome Civil QA",
                "12345678909",
                LocalDate.of(1990, 1, 2),
                agora.minusMonths(6));
        usuario.confirmarEmail(agora.minusMonths(6));
        anuncio = AnuncioEntity.criarSolicitacaoLocal(
                UUID.randomUUID(),
                usuarioId,
                "anuncio-qa-usuario",
                "Anuncio QA",
                "Descricao valida para o anuncio QA",
                "MASSAGENS",
                new BigDecimal("100.00"),
                "+5562999999999",
                agora.minusDays(10));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(anuncioRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)).thenReturn(List.of(anuncio));
        when(kycService.listarPorUsuario(usuarioId)).thenReturn(List.of(envio()));
        when(bloqueioRepository
                .findFirstByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNullOrderByBloqueadoEmDesc(
                        usuarioId,
                        EscopoBloqueioJuridico.ANUNCIO_E_USUARIO))
                .thenReturn(Optional.empty());
        when(auditoriaRepository.findByRecursoTipoAndRecursoIdOrderByCriadoEmDesc(
                eq("USUARIO"),
                eq(usuarioId),
                any())).thenReturn(List.of());
    }

    @Test
    void listaMascaraCpfEAplicaBuscaFiltrosOrdenacaoEPaginacao() {
        UsuarioRow row = new UsuarioRow(
                usuarioId,
                "Nome publico",
                "Nome Civil QA",
                "qa@example.invalid",
                "+5562999999999",
                "12345678909",
                "ATIVO",
                "ANUNCIANTE",
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                OffsetDateTime.parse("2026-07-27T10:00:00Z"),
                "APROVADO",
                2,
                false,
                "GO",
                "Goiânia");
        when(consultaRepository.listar(
                eq("qa@example.invalid"),
                any(),
                any(),
                eq("ATIVO"),
                eq("APROVADO"),
                eq("COM_ANUNCIOS"),
                eq("GO"),
                eq("goiania"),
                eq("ANTIGOS"),
                any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        var result = service.listar(
                " qa@example.invalid ",
                "ATIVO",
                "APROVADO",
                "COM_ANUNCIOS",
                "GO",
                "goiania",
                "ANTIGOS",
                0,
                20);

        assertThat(result.itens()).singleElement().satisfies(item -> {
            assertThat(item.nome()).isEqualTo("Nome Civil QA");
            assertThat(item.cpfMascarado()).isEqualTo("***.***.***-09");
            assertThat(item.totalAnuncios()).isEqualTo(2);
            assertThat(item.podeExcluir()).isTrue();
            assertThat(item.ufPrincipal()).isEqualTo("GO");
            assertThat(item.cidadePrincipal()).isEqualTo("Goiânia");
        });
        verify(consultaRepository).listar(
                eq("qa@example.invalid"),
                any(),
                any(),
                eq("ATIVO"),
                eq("APROVADO"),
                eq("COM_ANUNCIOS"),
                eq("GO"),
                eq("goiania"),
                eq("ANTIGOS"),
                any());
    }

    @Test
    void adminRecebeCpfIntegralDocumentosEAnunciosVinculados() {
        var result = service.detalhar(usuarioId, principal(PapelUsuario.ADMIN));

        assertThat(result.cpf()).isEqualTo("12345678909");
        assertThat(result.cpfMascarado()).isFalse();
        assertThat(result.kycStatus()).isEqualTo("APROVADO");
        assertThat(result.kycEnvios()).hasSize(1);
        assertThat(result.anuncios()).singleElement().satisfies(item ->
                assertThat(item.id()).isEqualTo(anuncio.getId()));
        assertThat(result.podeBloquear()).isTrue();
    }

    @Test
    void moderadorRecebeCpfMascaradoENaoRecebeAcaoJuridica() {
        var result = service.detalhar(usuarioId, principal(PapelUsuario.MODERADOR));

        assertThat(result.cpf()).isEqualTo("***.***.***-09");
        assertThat(result.cpfMascarado()).isTrue();
        assertThat(result.podeBloquear()).isFalse();
        assertThat(result.podeDesbloquear()).isFalse();
    }

    @Test
    void contaExcluidaRetornaSomenteIdentidadeTecnicaEHistoricoPermitido() {
        OffsetDateTime agora = OffsetDateTime.parse("2026-07-30T10:00:00Z");
        usuario.anonimizarDefinitivamente(UUID.randomUUID(), agora);

        var result = service.detalhar(usuarioId, principal(PapelUsuario.ADMIN));

        assertThat(result.nome()).isEqualTo("Conta excluida");
        assertThat(result.nomeCivil()).isNull();
        assertThat(result.email()).isNull();
        assertThat(result.telefone()).isNull();
        assertThat(result.cpf()).isNull();
        assertThat(result.dataNascimento()).isNull();
        assertThat(result.kycStatus()).isEqualTo("PRESERVADO_PRIVADO");
        assertThat(result.kycEnvios()).isEmpty();
        assertThat(result.exclusaoTipo()).isEqualTo("EXCLUSAO_COM_ANONIMIZACAO");
        assertThat(result.excluidoEm()).isEqualTo(agora);
        assertThat(result.podeBloquear()).isFalse();
        assertThat(result.podeDesbloquear()).isFalse();
        verify(kycService, never()).listarPorUsuario(usuarioId);
    }

    private AdminKycEnvioDto envio() {
        return new AdminKycEnvioDto(
                UUID.randomUUID(),
                usuarioId,
                "Nome Civil QA",
                "***.***.***-09",
                "1990-01-02",
                "APROVADO",
                null,
                OffsetDateTime.parse("2026-07-20T10:00:00Z"),
                OffsetDateTime.parse("2026-07-21T10:00:00Z"),
                List.of());
    }

    private AdminUserPrincipal principal(PapelUsuario papel) {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Operador",
                "operador@example.invalid",
                "hash",
                List.of(papel),
                List.of(new AdminPermissionDto("ANUNCIO_LER", "Leitura administrativa")),
                List.of(),
                true);
    }
}
