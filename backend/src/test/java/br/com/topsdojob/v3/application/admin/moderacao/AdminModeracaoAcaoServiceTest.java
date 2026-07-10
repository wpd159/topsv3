package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.DecisaoModeracaoRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.OutboxEventoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminModeracaoAcaoServiceTest {

    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
    private final DecisaoModeracaoRepository decisaoRepository = mock(DecisaoModeracaoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final OutboxEventoRepository outboxRepository = mock(OutboxEventoRepository.class);
    private AdminModeracaoAcaoService service;

    @BeforeEach
    void setUp() {
        service = new AdminModeracaoAcaoService(
                revisaoRepository,
                anuncioRepository,
                midiaRepository,
                arquivoRepository,
                documentoRepository,
                decisaoRepository,
                auditoriaRepository,
                outboxRepository,
                new ObjectMapper());
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void fotoAprovadaExigeVisibilidadeExplicita() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        assertThatThrownBy(() -> decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("visibilidade obrigatoria");
    }

    @Test
    void fotoPodeSerLivreOuRestritaIndividualmente() {
        Fixture livre = fixture(TipoAnuncioMidia.FOTO, null);
        var responseLivre = decidir(livre.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null);

        Fixture restrita = fixture(TipoAnuncioMidia.FOTO, null);
        var responseRestrita = decidir(restrita.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null);

        assertThat(responseLivre.visibilidadeMidia()).isEqualTo("LIVRE");
        assertThat(responseRestrita.visibilidadeMidia()).isEqualTo("RESTRITA_18");
        assertThat(livre.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.LIVRE);
        assertThat(restrita.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
    }

    @Test
    void videoNuncaAceitaLivre() {
        Fixture fixture = fixture(TipoAnuncioMidia.VIDEO, null);

        assertThatThrownBy(() -> decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        assertThat(decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, null, null).visibilidadeMidia())
                .isEqualTo("RESTRITA_18");
    }

    @Test
    void storyNuncaAceitaLivre() {
        Fixture fixture = fixture(TipoAnuncioMidia.STORY, null);

        assertThatThrownBy(() -> decidir(fixture.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void solicitarAjusteMantemArquivoPendenteERegistraStatusReal() {
        Fixture fixture = fixture(TipoAnuncioMidia.FOTO, null);

        decidir(fixture.id(), AdminDecisaoModeracaoAcao.SOLICITAR_AJUSTE, null, "ajustar enquadramento");

        assertThat(fixture.midia().getStatus()).isEqualTo(StatusAnuncioMidia.AJUSTE_SOLICITADO);
        assertThat(fixture.arquivo().getStatusArquivo()).isEqualTo(StatusArquivoMidia.PENDENTE);
    }

    @Test
    void alterarUmaMidiaNaoAlteraOutra() {
        Fixture primeira = fixture(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE);
        AnuncioMidiaEntity segunda = entity(AnuncioMidiaEntity.class);
        ReflectionTestUtils.setField(segunda, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);

        decidir(primeira.id(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.RESTRITA_18, null);

        assertThat(primeira.midia().getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
        assertThat(segunda.getVisibilidadeMidia()).isEqualTo(VisibilidadeMidia.RESTRITA_18);
    }

    private br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto decidir(
            UUID id,
            AdminDecisaoModeracaoAcao decisao,
            VisibilidadeMidia visibilidade,
            String motivo) {
        return service.decidirMidia(
                id,
                new AdminDecidirMidiaRequestDto(decisao, visibilidade, motivo, null, null),
                principal(),
                "req-local-123456");
    }

    private Fixture fixture(TipoAnuncioMidia tipo, VisibilidadeMidia visibilidade) {
        UUID id = UUID.randomUUID();
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = entity(AnuncioMidiaEntity.class);
        ReflectionTestUtils.setField(midia, "id", id);
        ReflectionTestUtils.setField(midia, "anuncioId", UUID.randomUUID());
        ReflectionTestUtils.setField(midia, "arquivoMidiaId", arquivoId);
        ReflectionTestUtils.setField(midia, "tipo", tipo);
        ReflectionTestUtils.setField(midia, "status", StatusAnuncioMidia.PENDENTE);
        ReflectionTestUtils.setField(midia, "visibilidadeMidia", visibilidade);
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        ReflectionTestUtils.setField(arquivo, "id", arquivoId);
        ReflectionTestUtils.setField(arquivo, "statusArquivo", StatusArquivoMidia.PENDENTE);
        when(midiaRepository.findById(id)).thenReturn(Optional.of(midia));
        when(arquivoRepository.findById(arquivoId)).thenReturn(Optional.of(arquivo));
        when(documentoRepository.existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(arquivoId)).thenReturn(false);
        return new Fixture(id, midia, arquivo);
    }

    private AdminUserPrincipal principal() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin Local",
                "admin.local@example.invalid",
                "hash-local",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MIDIA_REVISAR")),
                true);
    }

    private <T> T entity(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(UUID id, AnuncioMidiaEntity midia, ArquivoMidiaEntity arquivo) {
    }
}
