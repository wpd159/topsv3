package br.com.topsdojob.v3.application.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import java.time.OffsetDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminLocalizacaoConsultaSupportTest {

    private final AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
    private final EstadoRepository estadoRepository = mock(EstadoRepository.class);
    private final CidadeRepository cidadeRepository = mock(CidadeRepository.class);
    private final BairroRepository bairroRepository = mock(BairroRepository.class);
    private final AdminLocalizacaoConsultaSupport support = new AdminLocalizacaoConsultaSupport(
            localizacaoRepository,
            estadoRepository,
            cidadeRepository,
            bairroRepository,
            new AnuncioAtualizacaoCanonicaValidator());
    private UUID anuncioId;
    private UUID estadoId;
    private UUID cidadeId;
    private UUID bairroId;

    @BeforeEach
    void setUp() {
        OffsetDateTime agora = OffsetDateTime.parse("2026-07-22T12:00:00Z");
        anuncioId = UUID.randomUUID();
        estadoId = UUID.randomUUID();
        cidadeId = UUID.randomUUID();
        bairroId = UUID.randomUUID();
        EstadoEntity estado = EstadoEntity.criarFixtureHomologacao(estadoId, "GO", "Goias", "goias", agora);
        CidadeEntity cidade = CidadeEntity.criarFixtureHomologacao(
                cidadeId, estadoId, "Goiania", "goiania", "goiania", agora);
        BairroEntity bairro = BairroEntity.criarFixtureHomologacao(
                bairroId, cidadeId, "Setor Bueno", "setor bueno", "setor-bueno", agora);
        AnuncioLocalizacaoEntity localizacao = AnuncioLocalizacaoEntity.criarFixtureHomologacao(
                anuncioId, estadoId, cidadeId, bairroId, agora);
        when(estadoRepository.findByUfIgnoreCase("GO")).thenReturn(Optional.of(estado));
        when(estadoRepository.findByUfIgnoreCase("go")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "goiania")).thenReturn(Optional.of(cidade));
        when(bairroRepository.findByCidadeIdAndSlug(cidadeId, "setor-bueno")).thenReturn(Optional.of(bairro));
        when(localizacaoRepository.findByEstadoId(estadoId)).thenReturn(List.of(localizacao));
        when(localizacaoRepository.findByCidadeId(cidadeId)).thenReturn(List.of(localizacao));
        when(localizacaoRepository.findByBairroId(bairroId)).thenReturn(List.of(localizacao));
    }

    @Test
    void filtrosDependentesAceitamUfCidadeEBairroCanonicos() {
        assertThat(support.filtrarAnuncioIds("go", null, null)).containsExactly(anuncioId);
        assertThat(support.filtrarAnuncioIds("GO", "Goiânia", null)).containsExactly(anuncioId);
        assertThat(support.filtrarAnuncioIds("GO", "GOIANIA", "SETOR BUENO")).containsExactly(anuncioId);
    }

    @Test
    void cidadeInexistenteEBairroIncompativelNaoFabricamResultado() {
        assertThat(support.filtrarAnuncioIds("GO", "cidade-inexistente", null)).isEmpty();
        assertThat(support.filtrarAnuncioIds("GO", "goiania", "bairro-inexistente")).isEmpty();
    }

    @Test
    void hierarquiaIncompletaEhRecusadaExplicitamente() {
        assertThatThrownBy(() -> support.filtrarAnuncioIds(null, "goiania", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> support.filtrarAnuncioIds("GO", null, "setor-bueno"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void catalogoRetornaRotulosHumanosESlugsCanonicos() {
        AnuncioLocalizacaoRepository.LocalidadeFiltroProjection projection =
                mock(AnuncioLocalizacaoRepository.LocalidadeFiltroProjection.class);
        when(projection.getUf()).thenReturn("GO");
        when(projection.getEstado()).thenReturn("Goias");
        when(projection.getCidade()).thenReturn("Goiania");
        when(projection.getCidadeSlug()).thenReturn("goiania");
        when(projection.getBairro()).thenReturn("Setor Bueno");
        when(projection.getBairroSlug()).thenReturn("setor-bueno");
        when(localizacaoRepository.findLocalidadesDaFilaAdministrativa()).thenReturn(List.of(projection));

        assertThat(support.localidadesFiltro()).singleElement().satisfies(item -> {
            assertThat(item.uf()).isEqualTo("GO");
            assertThat(item.cidade()).isEqualTo("Goiania");
            assertThat(item.cidadeSlug()).isEqualTo("goiania");
            assertThat(item.bairro()).isEqualTo("Setor Bueno");
            assertThat(item.bairroSlug()).isEqualTo("setor-bueno");
        });
    }

    @Test
    void inventarioDeLocalidadesIncluiAnunciosAdministrativosNaoRemovidos() throws Exception {
        String repository = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "persistence", "repository", "AnuncioLocalizacaoRepository.java"));
        String query = repository.substring(
                repository.indexOf("select distinct"),
                repository.indexOf("\"\"\", nativeQuery = true)",
                        repository.indexOf("select distinct")));

        assertThat(query)
                .contains("join anuncio a on a.id = al.anuncio_id and a.removido_em is null")
                .contains("left join bairro")
                .doesNotContain("a.status = 'PUBLICADO'")
                .doesNotContain("a.status_moderacao = 'APROVADO'");
    }
}
