package br.com.topsdojob.v3.application.admin.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService.ItemUpload;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService.ResultadoUpload;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class AdminAnuncioMidiaUploadServiceTest {

    private static final UUID ANUNCIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MIDIA_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioMidiaUploadCoreService coreService = mock(AnuncioMidiaUploadCoreService.class);
    private final MultipartFile arquivo = mock(MultipartFile.class);
    private final AdminAnuncioMidiaUploadService service =
            new AdminAnuncioMidiaUploadService(anuncioRepository, coreService);
    private AnuncioEntity anuncio;

    @BeforeEach
    void setUp() {
        anuncio = AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID, UUID.randomUUID(), "anuncio", "Anuncio", "Descricao",
                StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO,
                OffsetDateTime.now(ZoneOffset.UTC));
        when(anuncioRepository.findByIdForModeration(ANUNCIO_ID))
                .thenReturn(java.util.Optional.of(anuncio));
        when(coreService.enviarAdministrativo(anuncio, arquivo, "chave-1"))
                .thenReturn(resultado(false));
    }

    @Test
    void exigeAsTresAutoridadesSimultaneamente() {
        for (String[] authorities : List.of(
                new String[] {"ANUNCIO_MODERAR", "MIDIA_REVISAR"},
                new String[] {"ROLE_ADMIN", "MIDIA_REVISAR"},
                new String[] {"ROLE_ADMIN", "ANUNCIO_MODERAR"})) {
            assertThatThrownBy(() -> service.enviar(
                    ANUNCIO_ID, arquivo, "chave-1", principal(authorities), "req-1"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(anuncioRepository, never()).findByIdForModeration(any());
        verify(coreService, never()).enviarAdministrativo(any(), any(), any());
    }

    @Test
    void bloqueiaAnuncioAntesDoCoreEMapeiaRespostaSemLocalizadorInterno() {
        var dto = service.enviar(
                ANUNCIO_ID, arquivo, "chave-1",
                principal("ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR"), "req-1");

        var ordem = inOrder(anuncioRepository, coreService);
        ordem.verify(anuncioRepository).findByIdForModeration(ANUNCIO_ID);
        ordem.verify(coreService).enviarAdministrativo(anuncio, arquivo, "chave-1");
        assertThat(dto.midiaId()).isEqualTo(MIDIA_ID);
        assertThat(dto.anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(dto.tipo()).isEqualTo("FOTO");
        assertThat(dto.finalidade()).isEqualTo("GALERIA");
        assertThat(dto.status()).isEqualTo("PENDENTE");
        assertThat(dto.statusArquivo()).isEqualTo("PENDENTE");
        assertThat(dto.idempotente()).isFalse();
        assertThat(dto.requestId()).isEqualTo("req-1");
        assertThat(dto.toString()).doesNotContain("bucket", "chaveObjeto", "url", "foto.jpg");
    }

    @Test
    void rejeitaAnuncioRemovidoSemAcionarCore() {
        anuncio.removerLogicamente(OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> service.enviar(
                ANUNCIO_ID, arquivo, "chave-1",
                principal("ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR"), "req-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(coreService, never()).enviarAdministrativo(any(), any(), any());
    }

    @Test
    void retryPropagaEstadosAtuaisEFlagIdempotente() {
        when(coreService.enviarAdministrativo(anuncio, arquivo, "chave-1"))
                .thenReturn(new ResultadoUpload(List.of(new ItemUpload(
                        MIDIA_ID, ANUNCIO_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA,
                        3, StatusAnuncioMidia.PUBLICAVEL, StatusArquivoMidia.VALIDADO, true)), true));

        var dto = service.enviar(
                ANUNCIO_ID, arquivo, "chave-1",
                principal("ROLE_ADMIN", "ANUNCIO_MODERAR", "MIDIA_REVISAR"), "req-2");

        assertThat(dto.status()).isEqualTo("PUBLICAVEL");
        assertThat(dto.statusArquivo()).isEqualTo("VALIDADO");
        assertThat(dto.idempotente()).isTrue();
    }

    private ResultadoUpload resultado(boolean idempotente) {
        return new ResultadoUpload(List.of(new ItemUpload(
                MIDIA_ID, ANUNCIO_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA,
                0, StatusAnuncioMidia.PENDENTE, StatusArquivoMidia.PENDENTE, idempotente)), idempotente);
    }

    private AdminUserPrincipal principal(String... authorities) {
        List<GrantedAuthority> granted = Arrays.stream(authorities)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        return new AdminUserPrincipal(
                UUID.randomUUID(), "Admin", "admin@example.invalid", "hash",
                List.of(PapelUsuario.ADMIN), List.<AdminPermissionDto>of(), granted, true);
    }
}
