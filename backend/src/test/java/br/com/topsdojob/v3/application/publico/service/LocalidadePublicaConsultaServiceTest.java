package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalidadePublicaConsultaServiceTest {

    @Test
    void catalogoCompletoIncluiLocalidadesSemAnuncioEOrdenaComAcentos() {
        UUID goId = UUID.randomUUID();
        UUID spId = UUID.randomUUID();
        UUID goianiaId = UUID.randomUUID();
        UUID aguasId = UUID.randomUUID();

        EstadoEntity go = entity(EstadoEntity.class);
        set(go, "id", goId);
        set(go, "uf", "GO");
        set(go, "nome", "Goiás");
        EstadoEntity sp = entity(EstadoEntity.class);
        set(sp, "id", spId);
        set(sp, "uf", "SP");
        set(sp, "nome", "São Paulo");

        CidadeEntity goiania = entity(CidadeEntity.class);
        set(goiania, "id", goianiaId);
        set(goiania, "estadoId", goId);
        set(goiania, "nome", "Goiânia");
        set(goiania, "slug", "goiania");
        CidadeEntity aguas = entity(CidadeEntity.class);
        set(aguas, "id", aguasId);
        set(aguas, "estadoId", goId);
        set(aguas, "nome", "Águas Lindas de Goiás");
        set(aguas, "slug", "aguas-lindas-de-goias");

        BairroEntity setor = entity(BairroEntity.class);
        set(setor, "id", UUID.randomUUID());
        set(setor, "cidadeId", goianiaId);
        set(setor, "nome", "Setor Bueno");
        set(setor, "slug", "setor-bueno");

        EstadoRepository estados = mock(EstadoRepository.class);
        CidadeRepository cidades = mock(CidadeRepository.class);
        BairroRepository bairros = mock(BairroRepository.class);
        when(estados.findAll()).thenReturn(List.of(sp, go));
        when(cidades.findAll()).thenReturn(List.of(goiania, aguas));
        when(bairros.findAll()).thenReturn(List.of(setor));

        var catalogo = new LocalidadePublicaConsultaService(
                estados,
                cidades,
                bairros,
                mock(AnuncioRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioSeoElegibilidadeConsultaService.class),
                new LocalidadeSeoIndexabilidadePolicy()).catalogoCompleto();

        assertThat(catalogo.estados()).extracting(item -> item.nome())
                .containsExactly("Goiás", "São Paulo");
        assertThat(catalogo.estados().get(0).cidades()).extracting(item -> item.nome())
                .containsExactly("Águas Lindas de Goiás", "Goiânia");
        assertThat(catalogo.estados().get(0).cidades().get(1).bairros())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.nome()).isEqualTo("Setor Bueno");
                    assertThat(item.slug()).isEqualTo("setor-bueno");
                    assertThat(item.totalAnunciosAtivos()).isZero();
                });
    }

    @Test
    void descobreSomenteLocalidadesDeAnunciosPublicos() {
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID bairroId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        UUID anuncioExclusivoId = UUID.randomUUID();

        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "GO");
        set(estado, "nome", "Goias");
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "estadoId", estadoId);
        set(cidade, "nome", "Goiania");
        set(cidade, "slug", "goiania");
        BairroEntity bairro = entity(BairroEntity.class);
        set(bairro, "id", bairroId);
        set(bairro, "cidadeId", cidadeId);
        set(bairro, "nome", "Setor Bueno");
        set(bairro, "slug", "setor-bueno");
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        AnuncioEntity anuncioExclusivo = entity(AnuncioEntity.class);
        set(anuncioExclusivo, "id", anuncioExclusivoId);
        set(anuncioExclusivo, "status", StatusAnuncio.PUBLICADO);
        set(anuncioExclusivo, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncioExclusivo, "atendimentoExclusivamenteVirtual", true);
        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        set(localizacao, "estadoId", estadoId);
        set(localizacao, "cidadeId", cidadeId);
        set(localizacao, "bairroId", bairroId);
        AnuncioLocalizacaoEntity localizacaoExclusiva = entity(AnuncioLocalizacaoEntity.class);
        set(localizacaoExclusiva, "anuncioId", anuncioExclusivoId);
        set(localizacaoExclusiva, "estadoId", estadoId);
        set(localizacaoExclusiva, "cidadeId", cidadeId);
        set(localizacaoExclusiva, "bairroId", bairroId);

        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioSeoElegibilidadeConsultaService elegibilidadeService = mock(
                AnuncioSeoElegibilidadeConsultaService.class);
        when(anuncioRepository.findPublicosComProprietarioAtivo())
                .thenReturn(List.of(anuncio, anuncioExclusivo));
        when(localizacaoRepository.findByAnuncioIdIn(any()))
                .thenReturn(List.of(localizacao, localizacaoExclusiva));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidade));
        when(bairroRepository.findAllById(any())).thenReturn(List.of(bairro));
        when(elegibilidadeService.avaliar(any(), any())).thenReturn(Map.of(
                anuncioId,
                new AnuncioSeoElegibilidadeConsultaService.Resultado(true, null)));

        var descoberta = new LocalidadePublicaConsultaService(
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                anuncioRepository,
                localizacaoRepository,
                elegibilidadeService,
                new LocalidadeSeoIndexabilidadePolicy()).descobrir();

        assertThat(descoberta.estados()).hasSize(1);
        assertThat(descoberta.estados().get(0).uf()).isEqualTo("GO");
        assertThat(descoberta.estados().get(0).totalAnunciosAtivos()).isEqualTo(1);
        assertThat(descoberta.estados().get(0).indexacao().indexavel()).isFalse();
        assertThat(descoberta.estados().get(0).indexacao().anunciosElegiveisUnicos()).isEqualTo(1);
        assertThat(descoberta.estados().get(0).cidades()).singleElement()
                .satisfies(item -> {
                    assertThat(item.slug()).isEqualTo("goiania");
                    assertThat(item.totalAnunciosAtivos()).isEqualTo(1);
                    assertThat(item.indexacao().indexavel()).isFalse();
                    assertThat(item.indexacao().anunciosElegiveisUnicos()).isEqualTo(1);
                    assertThat(item.bairros()).singleElement()
                            .satisfies(value -> {
                                assertThat(value.slug()).isEqualTo("setor-bueno");
                                assertThat(value.totalAnunciosAtivos()).isEqualTo(1);
                                assertThat(value.indexacao().anunciosElegiveisUnicos()).isEqualTo(1);
                            });
                });
    }

    @Test
    void contaAnunciosElegiveisPorIdSemDeduplicarProprietario() {
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID bairroId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioUmId = UUID.randomUUID();
        UUID anuncioDoisId = UUID.randomUUID();
        UUID anuncioInelegivelId = UUID.randomUUID();

        EstadoEntity estado = entidadeEstado(estadoId);
        CidadeEntity cidade = entidadeCidade(cidadeId, estadoId);
        BairroEntity bairro = entidadeBairro(bairroId, cidadeId);
        AnuncioEntity anuncioUm = entidadeAnuncio(anuncioUmId, usuarioId);
        AnuncioEntity anuncioDois = entidadeAnuncio(anuncioDoisId, usuarioId);
        AnuncioEntity anuncioInelegivel = entidadeAnuncio(anuncioInelegivelId, usuarioId);

        AnuncioLocalizacaoEntity localizacaoUm = entidadeLocalizacao(
                anuncioUmId, estadoId, cidadeId, bairroId);
        AnuncioLocalizacaoEntity localizacaoUmDuplicada = entidadeLocalizacao(
                anuncioUmId, estadoId, cidadeId, bairroId);
        AnuncioLocalizacaoEntity localizacaoDois = entidadeLocalizacao(
                anuncioDoisId, estadoId, cidadeId, bairroId);
        AnuncioLocalizacaoEntity localizacaoInelegivel = entidadeLocalizacao(
                anuncioInelegivelId, estadoId, cidadeId, bairroId);

        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioSeoElegibilidadeConsultaService elegibilidadeService = mock(
                AnuncioSeoElegibilidadeConsultaService.class);

        when(anuncioRepository.findPublicosComProprietarioAtivo())
                .thenReturn(List.of(anuncioUm, anuncioDois, anuncioInelegivel));
        when(localizacaoRepository.findByAnuncioIdIn(any())).thenReturn(List.of(
                localizacaoUm,
                localizacaoUmDuplicada,
                localizacaoDois,
                localizacaoInelegivel));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidade));
        when(bairroRepository.findAllById(any())).thenReturn(List.of(bairro));
        when(elegibilidadeService.avaliar(any(), any())).thenReturn(Map.of(
                anuncioUmId, new AnuncioSeoElegibilidadeConsultaService.Resultado(true, null),
                anuncioDoisId, new AnuncioSeoElegibilidadeConsultaService.Resultado(true, null),
                anuncioInelegivelId, new AnuncioSeoElegibilidadeConsultaService.Resultado(false, null)));

        var descoberta = new LocalidadePublicaConsultaService(
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                anuncioRepository,
                localizacaoRepository,
                elegibilidadeService,
                new LocalidadeSeoIndexabilidadePolicy()).descobrir();

        var estadoDto = descoberta.estados().get(0);
        var cidadeDto = estadoDto.cidades().get(0);
        var bairroDto = cidadeDto.bairros().get(0);
        assertThat(estadoDto.totalAnunciosAtivos()).isEqualTo(3);
        assertThat(cidadeDto.totalAnunciosAtivos()).isEqualTo(3);
        assertThat(bairroDto.totalAnunciosAtivos()).isEqualTo(3);
        assertThat(cidadeDto.indexacao().anunciosElegiveisUnicos()).isEqualTo(2);
        assertThat(bairroDto.indexacao().anunciosElegiveisUnicos()).isEqualTo(2);
        assertThat(cidadeDto.indexacao().indexavel()).isFalse();
        assertThat(bairroDto.indexacao().indexavel()).isFalse();
    }

    private EstadoEntity entidadeEstado(UUID estadoId) {
        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "GO");
        set(estado, "nome", "Goias");
        return estado;
    }

    private CidadeEntity entidadeCidade(UUID cidadeId, UUID estadoId) {
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "estadoId", estadoId);
        set(cidade, "nome", "Goiania");
        set(cidade, "slug", "goiania");
        return cidade;
    }

    private BairroEntity entidadeBairro(UUID bairroId, UUID cidadeId) {
        BairroEntity bairro = entity(BairroEntity.class);
        set(bairro, "id", bairroId);
        set(bairro, "cidadeId", cidadeId);
        set(bairro, "nome", "Centro");
        set(bairro, "slug", "centro");
        return bairro;
    }

    private AnuncioEntity entidadeAnuncio(UUID anuncioId, UUID usuarioId) {
        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        set(anuncio, "usuarioId", usuarioId);
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        return anuncio;
    }

    private AnuncioLocalizacaoEntity entidadeLocalizacao(
            UUID anuncioId,
            UUID estadoId,
            UUID cidadeId,
            UUID bairroId) {
        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        set(localizacao, "estadoId", estadoId);
        set(localizacao, "cidadeId", cidadeId);
        set(localizacao, "bairroId", bairroId);
        return localizacao;
    }
}
