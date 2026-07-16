package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosCategoriaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoFlagsDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ListagemPublicaConsultaServiceTest {

    @Test
    void rejeitaPaginacaoInvalidaCom400() {
        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                mock(EstadoRepository.class),
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                mock(AnuncioPublicoConsultaService.class),
                mock(SeoPublicoConsultaService.class),
                mock(PremiumPublicoMapper.class),
                mock(PoliticaContatoPublicoService.class));

        assertThatThrownBy(() -> service.porEstado("SP", -1, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void retorna404ParaEstadoInexistente() {
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        when(estadoRepository.findByUfIgnoreCase("SP")).thenReturn(Optional.empty());
        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                estadoRepository,
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                mock(AnuncioPublicoConsultaService.class),
                mock(SeoPublicoConsultaService.class),
                mock(PremiumPublicoMapper.class),
                mock(PoliticaContatoPublicoService.class));

        assertThatThrownBy(() -> service.porEstado("sp", 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void retorna404ParaCidadeInexistente() {
        UUID estadoId = UUID.randomUUID();
        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "SP");
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        when(estadoRepository.findByUfIgnoreCase("SP")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "cidade-ausente"))
                .thenReturn(Optional.empty());

        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                estadoRepository,
                cidadeRepository,
                mock(BairroRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                mock(AnuncioPublicoConsultaService.class),
                mock(SeoPublicoConsultaService.class),
                mock(PremiumPublicoMapper.class),
                mock(PoliticaContatoPublicoService.class));

        assertThatThrownBy(() -> service.porCidade("sp", "cidade-ausente", 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void retorna404ParaBairroInexistente() {
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "GO");
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "estadoId", estadoId);
        set(cidade, "slug", "goiania");
        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        when(estadoRepository.findByUfIgnoreCase("GO")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "goiania")).thenReturn(Optional.of(cidade));
        when(bairroRepository.findByCidadeIdAndSlug(cidadeId, "bairro-ausente"))
                .thenReturn(Optional.empty());

        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                mock(AnuncioPublicoConsultaService.class),
                mock(SeoPublicoConsultaService.class),
                mock(PremiumPublicoMapper.class),
                mock(PoliticaContatoPublicoService.class));

        assertThatThrownBy(() -> service.porBairro("go", "goiania", "bairro-ausente", 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void paginaDepoisDeFiltrarAnunciosPublicadosAprovados() {
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID anuncioPublicadoId = UUID.randomUUID();
        UUID anuncioRascunhoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

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
        set(anuncioPublicado, "usuarioId", usuarioId);
        set(anuncioPublicado, "slug", "anuncio-publicado");
        set(anuncioPublicado, "titulo", "Anuncio publicado");
        set(anuncioPublicado, "status", StatusAnuncio.PUBLICADO);
        set(anuncioPublicado, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncioPublicado, "locaisAtendimento", Set.of(LocalAtendimentoAnuncio.MEU_LOCAL));
        set(anuncioPublicado, "servicos", Set.of(ServicoAnuncio.ANAL));

        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioPublicoConsultaService anuncioConsultaService = mock(AnuncioPublicoConsultaService.class);
        SeoPublicoConsultaService seoService = mock(SeoPublicoConsultaService.class);
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
        PremiumPublicoFlagsDto premiumVazio = PremiumPublicoFlagsDto.vazio();

        when(estadoRepository.findByUfIgnoreCase("SP")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "sao-paulo")).thenReturn(Optional.of(cidade));
        when(localizacaoRepository.findByCidadeId(cidadeId))
                .thenReturn(List.of(localizacaoPublicada, localizacaoNaoPublicada));
        when(anuncioRepository.findByIdInAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq(List.of(anuncioPublicadoId, anuncioRascunhoId)),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO)))
                .thenReturn(List.of(anuncioPublicado));
        when(premiumMapper.flagsPorAnuncios(List.of(anuncioPublicado)))
                .thenReturn(Map.of(anuncioPublicadoId, premiumVazio));
        when(anuncioConsultaService.midias(anuncioPublicadoId, premiumVazio)).thenReturn(List.of());
        AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection primeiraPublicacao =
                mock(AnuncioRepository.PrimeiraPublicacaoAnuncianteProjection.class);
        OffsetDateTime anunciaDesde = OffsetDateTime.parse("2023-11-10T10:00:00Z");
        when(primeiraPublicacao.getUsuarioId()).thenReturn(usuarioId);
        when(primeiraPublicacao.getPrimeiraPublicacaoEm()).thenReturn(anunciaDesde);
        when(anuncioRepository.findPrimeiraPublicacaoByUsuarioIdIn(List.of(usuarioId)))
                .thenReturn(List.of(primeiraPublicacao));
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
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                anuncioConsultaService,
                seoService,
                premiumMapper,
                mock(PoliticaContatoPublicoService.class));

        ListaAnunciosPublicaDto dto = service.porCidade("sp", "sao-paulo", 0, 20);

        assertThat(dto.itens()).hasSize(1);
        assertThat(dto.itens().get(0).slug()).isEqualTo("anuncio-publicado");
        assertThat(dto.itens().get(0).anunciaDesde()).isEqualTo(anunciaDesde);
        assertThat(dto.itens().get(0).comLocal()).isTrue();
        assertThat(dto.itens().get(0).fazAnal()).isTrue();
        assertThat(dto.paginacao().totalItens()).isEqualTo(1);
        verify(localizacaoRepository).findByCidadeId(cidadeId);
        verify(anuncioRepository).findByIdInAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                eq(List.of(anuncioPublicadoId, anuncioRascunhoId)),
                eq(StatusAnuncio.PUBLICADO),
                eq(StatusModeracaoAnuncio.APROVADO));
    }

    @Test
    void listaSomenteAnunciosDaCategoriaVendaDeConteudo() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();

        AnuncioEntity anuncio = entity(AnuncioEntity.class);
        set(anuncio, "id", anuncioId);
        set(anuncio, "usuarioId", usuarioId);
        set(anuncio, "slug", "conteudo-publico");
        set(anuncio, "titulo", "Conteudo online");
        set(anuncio, "descricao", "Videochamadas e conteudo exclusivo");
        set(anuncio, "categoria", "VENDA_DE_CONTEUDO");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncio, "locaisAtendimento", Set.of());
        set(anuncio, "servicos", Set.of());

        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        set(localizacao, "estadoId", estadoId);
        set(localizacao, "cidadeId", cidadeId);

        EstadoEntity estado = entity(EstadoEntity.class);
        set(estado, "id", estadoId);
        set(estado, "uf", "GO");
        set(estado, "nome", "Goias");
        CidadeEntity cidade = entity(CidadeEntity.class);
        set(cidade, "id", cidadeId);
        set(cidade, "nome", "Goiania");
        set(cidade, "slug", "goiania");

        EstadoRepository estadoRepository = mock(EstadoRepository.class);
        CidadeRepository cidadeRepository = mock(CidadeRepository.class);
        BairroRepository bairroRepository = mock(BairroRepository.class);
        AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        AnuncioPublicoConsultaService anuncioConsultaService = mock(AnuncioPublicoConsultaService.class);
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
        PoliticaContatoPublicoService contatoService = mock(PoliticaContatoPublicoService.class);
        PremiumPublicoFlagsDto premium = PremiumPublicoFlagsDto.vazio();

        when(anuncioRepository.findByCategoriaAndStatusAndStatusModeracaoAndRemovidoEmIsNull(
                "VENDA_DE_CONTEUDO", StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO))
                .thenReturn(List.of(anuncio));
        when(premiumMapper.flagsPorAnuncios(List.of(anuncio))).thenReturn(Map.of(anuncioId, premium));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of(localizacao));
        when(estadoRepository.findAllById(List.of(estadoId))).thenReturn(List.of(estado));
        when(cidadeRepository.findAllById(List.of(cidadeId))).thenReturn(List.of(cidade));
        when(bairroRepository.findAllById(List.of())).thenReturn(List.of());
        when(anuncioRepository.findPrimeiraPublicacaoByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of());
        when(anuncioConsultaService.midias(anuncioId, premium)).thenReturn(List.of());
        when(contatoService.podeExporContato(anuncio)).thenReturn(false);

        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                estadoRepository,
                cidadeRepository,
                bairroRepository,
                localizacaoRepository,
                anuncioRepository,
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                anuncioConsultaService,
                mock(SeoPublicoConsultaService.class),
                premiumMapper,
                contatoService);

        ListaAnunciosCategoriaPublicaDto resposta = service.listar("VENDA_DE_CONTEUDO", null, 0, 20);

        assertThat(resposta.categoria()).isEqualTo("VENDA_DE_CONTEUDO");
        assertThat(resposta.itens()).singleElement().satisfies(item -> {
            assertThat(item.slug()).isEqualTo("conteudo-publico");
            assertThat(item.categoria()).isEqualTo("VENDA_DE_CONTEUDO");
        });
    }

    @Test
    void rejeitaCategoriaForaDaTaxonomiaCanonica() {
        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                mock(EstadoRepository.class),
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                mock(AnuncioRepository.class),
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                mock(AnuncioPublicoConsultaService.class),
                mock(SeoPublicoConsultaService.class),
                mock(PremiumPublicoMapper.class),
                mock(PoliticaContatoPublicoService.class));

        assertThatThrownBy(() -> service.listar("ENCONTROS_CASUAIS", null, 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
