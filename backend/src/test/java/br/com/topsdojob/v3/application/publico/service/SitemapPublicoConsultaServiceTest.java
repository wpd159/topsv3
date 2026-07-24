package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService.ResultadoUrlPublica;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SitemapPublicoConsultaServiceTest {

    @Test
    void listaSomenteIndexaveisComConsultasEmLoteESemDadosInternos() {
        UUID anuncioId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-07-11T12:00:00-03:00");

        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        set(anuncio, "slug", "perfil-publico-goiania");
        set(anuncio, "titulo", "Perfil publico Goiania");
        set(anuncio, "descricao", "Descricao publica consistente para indexacao, com informacoes suficientes para apresentar o perfil sem texto generico e com mais de cento e vinte caracteres.");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncio, "atualizadoEm", atualizadoEm);

        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        set(localizacao, "estadoId", estadoId);
        set(localizacao, "cidadeId", cidadeId);
        set(localizacao, "atualizadoEm", atualizadoEm.minusDays(1));

        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "GO");
        set(estado, "nome", "Goias");
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "estadoId", estadoId);
        set(cidade, "nome", "Goiania");
        set(cidade, "slug", "goiania");

        List<AnuncioMidiaEntity> vinculos = new ArrayList<>();
        List<ArquivoMidiaEntity> arquivos = new ArrayList<>();
        for (int ordem = 0; ordem < 1; ordem++) {
            UUID arquivoId = UUID.randomUUID();
            AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
            set(vinculo, "id", UUID.randomUUID());
            set(vinculo, "anuncioId", anuncioId);
            set(vinculo, "arquivoMidiaId", arquivoId);
            set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
            set(vinculo, "finalidade", FinalidadeAnuncioMidia.GALERIA);
            set(vinculo, "ordem", ordem);
            set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
            set(vinculo, "visibilidadeMidia", VisibilidadeMidia.LIVRE);
            set(vinculo, "atualizadoEm", atualizadoEm.minusHours(ordem));
            vinculos.add(vinculo);

            ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
            set(arquivo, "id", arquivoId);
            set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
            arquivos.add(arquivo);
        }

        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
        ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        MidiaPublicaUrlService urlService = mock(MidiaPublicaUrlService.class);

        when(anuncioRepository.findByStatusAndStatusModeracaoAndRemovidoEmIsNull(
                StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO)).thenReturn(List.of(anuncio));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of(localizacao));
        when(estadoRepository.findAllById(any())).thenReturn(List.of(estado));
        when(cidadeRepository.findAllById(any())).thenReturn(List.of(cidade));
        when(bairroRepository.findAllById(any())).thenReturn(List.of());
        when(midiaRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(vinculos);
        when(arquivoRepository.findByIdIn(any())).thenReturn(arquivos);
        when(urlService.resolver(any(), any())).thenReturn(new ResultadoUrlPublica("/midia/publica.jpg", null));

        SitemapPublicoConsultaService service = new SitemapPublicoConsultaService(
                anuncioRepository,
                localizacaoRepository,
                midiaRepository,
                arquivoRepository,
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                new MidiaPublicaMapper(urlService),
                new AnuncioSeoIndexabilidadePolicy());

        var entradas = service.listarAnunciosIndexaveis();

        assertThat(entradas).singleElement().satisfies(entrada -> {
            assertThat(entrada.slug()).isEqualTo("perfil-publico-goiania");
            assertThat(entrada.estadoUf()).isEqualTo("GO");
            assertThat(entrada.cidadeSlug()).isEqualTo("goiania");
            assertThat(entrada.atualizadoEm()).isEqualTo(atualizadoEm);
            assertThat(entrada.publico()).isTrue();
            assertThat(entrada.indexavel()).isTrue();
            assertThat(entrada.toString())
                    .doesNotContain("storage")
                    .doesNotContain("bucket")
                    .doesNotContain("contato")
                    .doesNotContain("whatsapp")
                    .doesNotContain("midia/publica.jpg");
        });
        verify(localizacaoRepository).findByAnuncioIdIn(List.of(anuncioId));
        verify(midiaRepository).findByAnuncioIdIn(List.of(anuncioId));
        verify(arquivoRepository).findByIdIn(any());
    }

    @Test
    void rejeitaAnuncioComMidiaRestritaMesmoQuePublicado() {
        AnuncioEntity anuncio = mock(AnuncioEntity.class);
        when(anuncio.getSlug()).thenReturn("perfil-publico");
        when(anuncio.getTitulo()).thenReturn("Perfil publico completo");
        when(anuncio.getDescricao()).thenReturn("x".repeat(130));
        when(anuncio.getStatus()).thenReturn(StatusAnuncio.PUBLICADO);
        when(anuncio.getStatusModeracao()).thenReturn(StatusModeracaoAnuncio.APROVADO);
        var localizacao = new br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto(
                "GO", "Goias", "Goiania", "goiania", null, null, null);
        var restritas = java.util.stream.IntStream.range(0, 1)
                .mapToObj(ordem -> new br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto(
                        UUID.randomUUID(), "FOTO", "GALERIA", ordem, "RESTRITA_18", false, null,
                        "/midia/borrada.jpg", "MIDIA_RESTRITA_IDADE", 1080, 1920, "image/jpeg"))
                .toList();

        assertThat(new AnuncioSeoIndexabilidadePolicy().indexavel(anuncio, localizacao, restritas)).isFalse();
    }
}
