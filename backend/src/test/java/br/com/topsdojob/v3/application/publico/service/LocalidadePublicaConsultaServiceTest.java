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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalidadePublicaConsultaServiceTest {

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
        when(anuncioRepository.findByStatusAndStatusModeracaoAndRemovidoEmIsNull(
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO)).thenReturn(List.of(anuncio, anuncioExclusivo));
        when(localizacaoRepository.findByAnuncioIdIn(any()))
                .thenReturn(List.of(localizacao, localizacaoExclusiva));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidade));
        when(bairroRepository.findAllById(any())).thenReturn(List.of(bairro));

        var descoberta = new LocalidadePublicaConsultaService(
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                anuncioRepository,
                localizacaoRepository).descobrir();

        assertThat(descoberta.estados()).hasSize(1);
        assertThat(descoberta.estados().get(0).uf()).isEqualTo("GO");
        assertThat(descoberta.estados().get(0).totalAnunciosAtivos()).isEqualTo(1);
        assertThat(descoberta.estados().get(0).cidades()).singleElement()
                .satisfies(item -> {
                    assertThat(item.slug()).isEqualTo("goiania");
                    assertThat(item.totalAnunciosAtivos()).isEqualTo(1);
                    assertThat(item.bairros()).singleElement()
                            .satisfies(value -> {
                                assertThat(value.slug()).isEqualTo("setor-bueno");
                                assertThat(value.totalAnunciosAtivos()).isEqualTo(1);
                            });
                });
    }
}
