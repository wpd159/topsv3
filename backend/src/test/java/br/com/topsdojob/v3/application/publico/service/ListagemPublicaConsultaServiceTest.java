package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ListagemPublicaConsultaServiceTest {

    @Test
    void paginaDepoisDeFiltrarAnunciosPublicadosAprovados() {
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID anuncioPublicadoId = UUID.randomUUID();
        UUID anuncioRascunhoId = UUID.randomUUID();

        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "SP");

        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "estadoId", estadoId);
        set(cidade, "nome", "Sao Paulo");
        set(cidade, "slug", "sao-paulo");

        AnuncioLocalizacaoEntity localizacaoPublicada = entity(AnuncioLocalizacaoEntity.class);
        set(localizacaoPublicada, "anuncioId", anuncioPublicadoId);
        set(localizacaoPublicada, "cidadeId", cidadeId);

        AnuncioLocalizacaoEntity localizacaoNaoPublicada = entity(AnuncioLocalizacaoEntity.class);
        set(localizacaoNaoPublicada, "anuncioId", anuncioRascunhoId);
        set(localizacaoNaoPublicada, "cidadeId", cidadeId);

        AnuncioEntity anuncioPublicado = entity(AnuncioEntity.class);
        set(anuncioPublicado, "id", anuncioPublicadoId);
        set(anuncioPublicado, "slug", "anuncio-publicado");
        set(anuncioPublicado, "titulo", "Anuncio publicado");
        set(anuncioPublicado, "status", StatusAnuncio.PUBLICADO);
        set(anuncioPublicado, "statusModeracao", StatusModeracaoAnuncio.APROVADO);

        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioPublicoConsultaService anuncioConsultaService = mock(AnuncioPublicoConsultaService.class);
        SeoPublicoConsultaService seoService = mock(SeoPublicoConsultaService.class);

        when(estadoRepository.findByUfIgnoreCase("SP")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "sao-paulo")).thenReturn(Optional.of(cidade));
        when(localizacaoRepository.findByCidadeId(cidadeId))
                .thenReturn(List.of(localizacaoPublicada, localizacaoNaoPublicada));
        when(anuncioRepository.findByIdInAndStatusAndStatusModeracaoAndClassificacaoConteudoAndRemovidoEmIsNull(
                eq(List.of(anuncioPublicadoId, anuncioRascunhoId)),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO),
                eq(ClassificacaoConteudo.LIVRE),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(anuncioPublicado), PageRequest.of(0, 20), 1));
        when(anuncioConsultaService.midias(anuncioPublicadoId)).thenReturn(List.of());
        when(seoService.paraCidade("SP", "sao-paulo"))
                .thenReturn(new SeoRotaPublicaDto(
                        "Sao Paulo",
                        "Listagem local",
                        "/acompanhantes/sp/sao-paulo",
                        "NOINDEX_FOLLOW",
                        "CIDADE",
                        false));

        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                localizacaoRepository,
                anuncioRepository,
                new AnuncioPublicoMapper(),
                anuncioConsultaService,
                seoService);

        ListaAnunciosPublicaDto dto = service.porCidade("sp", "sao-paulo", 0, 20);

        assertThat(dto.itens()).hasSize(1);
        assertThat(dto.itens().get(0).slug()).isEqualTo("anuncio-publicado");
        assertThat(dto.paginacao().totalItens()).isEqualTo(1);
        verify(localizacaoRepository).findByCidadeId(cidadeId);
        verify(anuncioRepository).findByIdInAndStatusAndStatusModeracaoAndClassificacaoConteudoAndRemovidoEmIsNull(
                eq(List.of(anuncioPublicadoId, anuncioRascunhoId)),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO),
                eq(ClassificacaoConteudo.LIVRE),
                any(Pageable.class));
    }
}
