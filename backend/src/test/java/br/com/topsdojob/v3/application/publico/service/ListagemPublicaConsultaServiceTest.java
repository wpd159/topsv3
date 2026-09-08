package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class ListagemPublicaConsultaServiceTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
    private int leiturasEsperadas = 1;

    @BeforeEach
    void fabricaDoProxyNaoExigeConexao() {
        when(entityManager.getEntityManagerFactory()).thenReturn(entityManagerFactory);
    }

    @AfterEach
    void limpaContextoDePersistenciaAntesDaLeitura() {
        try {
            verify(entityManager, times(leiturasEsperadas)).clear();
        } finally {
            // Only this test's mock factory key can have been bound by the fixtures below.
            TransactionSynchronizationManager.unbindResourceIfPossible(entityManagerFactory);
        }
    }

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
                mock(PoliticaContatoPublicoService.class),
                mock(OrdemSeedPublicaService.class),
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        assertThatThrownBy(() -> service.porEstado("SP", -1, 20, null))
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
                mock(PoliticaContatoPublicoService.class),
                mock(OrdemSeedPublicaService.class),
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        assertThatThrownBy(() -> service.porEstado("sp", 0, 20, null))
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
                mock(PoliticaContatoPublicoService.class),
                mock(OrdemSeedPublicaService.class),
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        assertThatThrownBy(() -> service.porCidade("sp", "cidade-ausente", 0, 20, null))
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
                mock(PoliticaContatoPublicoService.class),
                mock(OrdemSeedPublicaService.class),
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        assertThatThrownBy(() -> service.porBairro("go", "goiania", "bairro-ausente", 0, 20, null))
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
        when(anuncioRepository.findPublicosPorLocalidadeOrdenados(
                eq(estadoId),
                eq(cidadeId),
                eq(null),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(anuncioPublicado), PageRequest.of(0, 20), 1));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(anuncioPublicadoId)))
                .thenReturn(List.of(localizacaoPublicada));
        when(premiumMapper.flagsPorAnuncios(List.of(anuncioPublicado)))
                .thenReturn(Map.of(anuncioPublicadoId, premiumVazio));
        when(anuncioConsultaService.midiasParaCardsPorAnuncios(
                List.of(anuncioPublicadoId),
                Map.of(anuncioPublicadoId, premiumVazio))).thenReturn(Map.of(anuncioPublicadoId, List.of()));
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
                mock(PoliticaContatoPublicoService.class),
                mock(OrdemSeedPublicaService.class),
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        ListaAnunciosPublicaDto dto = service.porCidade("sp", "sao-paulo", 0, 20, null);

        assertThat(dto.itens()).hasSize(1);
        assertThat(dto.itens().get(0).slug()).isEqualTo("anuncio-publicado");
        assertThat(dto.itens().get(0).anunciaDesde()).isEqualTo(anunciaDesde);
        assertThat(dto.itens().get(0).comLocal()).isTrue();
        assertThat(dto.itens().get(0).fazAnal()).isTrue();
        assertThat(dto.paginacao().totalItens()).isEqualTo(1);
        verify(anuncioRepository).findPublicosPorLocalidadeOrdenados(
                eq(estadoId),
                eq(cidadeId),
                eq(null),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(PageRequest.of(0, 20)));
    }

    @Test
    void listaComoSexoVirtualSemTrocarACategoriaBaseDoAnuncio() {
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
        set(anuncio, "categoria", "ACOMPANHANTE_FEMININA");
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        set(anuncio, "locaisAtendimento", Set.of());
        set(anuncio, "servicos", Set.of(ServicoAnuncio.VIDEOCHAMADA));

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

        when(anuncioRepository.findPublicosOrdenados(
                eq("VENDA_DE_CONTEUDO"),
                eq(null),
                eq(null),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(anuncio), PageRequest.of(0, 20), 1));
        when(premiumMapper.flagsPorAnuncios(List.of(anuncio))).thenReturn(Map.of(anuncioId, premium));
        when(localizacaoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of(localizacao));
        when(estadoRepository.findAllById(List.of(estadoId))).thenReturn(List.of(estado));
        when(cidadeRepository.findAllById(List.of(cidadeId))).thenReturn(List.of(cidade));
        when(bairroRepository.findAllById(List.of())).thenReturn(List.of());
        when(anuncioRepository.findPrimeiraPublicacaoByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of());
        when(anuncioConsultaService.midiasParaCardsPorAnuncios(
                List.of(anuncioId),
                Map.of(anuncioId, premium))).thenReturn(Map.of(anuncioId, List.of()));
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
                contatoService,
                mock(OrdemSeedPublicaService.class),
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        ListaAnunciosCategoriaPublicaDto resposta =
                service.listar("VENDA_DE_CONTEUDO", null, null, 0, 20, null);

        assertThat(resposta.categoria()).isEqualTo("VENDA_DE_CONTEUDO");
        assertThat(resposta.itens()).singleElement().satisfies(item -> {
            assertThat(item.slug()).isEqualTo("conteudo-publico");
            assertThat(item.categoria()).isEqualTo("ACOMPANHANTE_FEMININA");
        });
    }

    @Test
    void reutilizaSeedInformadaNaQueryENaMetadataDaPaginacao() {
        AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
        PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
        OrdemSeedPublicaService ordemSeedService = mock(OrdemSeedPublicaService.class);
        long seed = -765432109876543210L;
        String seedSegura = Long.toString(seed);
        PageRequest pageable = PageRequest.of(1, 20);

        when(ordemSeedService.resolver(seedSegura)).thenReturn(seed);
        when(anuncioRepository.findPublicosOrdenados(
                eq(null),
                eq(null),
                eq(null),
                org.mockito.ArgumentMatchers.any(),
                eq(seed),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 21));
        when(premiumMapper.flagsPorAnuncios(List.of())).thenReturn(Map.of());

        ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
                mock(EstadoRepository.class),
                mock(CidadeRepository.class),
                mock(BairroRepository.class),
                mock(AnuncioLocalizacaoRepository.class),
                anuncioRepository,
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
                mock(AnuncioPublicoConsultaService.class),
                mock(SeoPublicoConsultaService.class),
                premiumMapper,
                mock(PoliticaContatoPublicoService.class),
                ordemSeedService,
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        ListaAnunciosCategoriaPublicaDto resposta =
                service.listar(null, null, null, 1, 20, seedSegura);

        assertThat(resposta.paginacao().ordemSeed()).isEqualTo(seedSegura);
        assertThat(resposta.paginacao().pagina()).isEqualTo(1);
        assertThat(resposta.paginacao().totalItens()).isEqualTo(21);
        verify(ordemSeedService).resolver(seedSegura);
        verify(anuncioRepository).findPublicosOrdenados(
                eq(null),
                eq(null),
                eq(null),
                org.mockito.ArgumentMatchers.any(),
                eq(seed),
                eq(pageable));
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
                mock(PoliticaContatoPublicoService.class),
                mock(OrdemSeedPublicaService.class),
                visualizacoesCanonicas(),
                mock(IdadeAnunciantePublicaService.class),
                transacoesSomenteLeitura(),
                entityManager);

        assertThatThrownBy(() -> service.listar("ENCONTROS_CASUAIS", null, null, 0, 20, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void restauraMesmoHolderOsivDepoisDaLeituraSemTocarNoEntityManagerExterno() {
        EntityManager externo = mock(EntityManager.class);
        EntityManagerHolder holder = new EntityManagerHolder(externo);
        AnuncioRepository repository = mock(AnuncioRepository.class);
        when(repository.findPublicosOrdenados(eq(null), eq(null), eq(null), any(), anyLong(), any()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isNull();
                    return Page.empty(PageRequest.of(0, 20));
                });
        PlatformTransactionManager manager = transacoesSomenteLeitura();
        ListagemPublicaConsultaService service = serviceParaLeituraPrivada(repository, manager);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);

        assertThat(service.listar(null, null, null, 0, 20, "27").itens()).isEmpty();

        assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(holder);
        assertThat(holder.isSynchronizedWithTransaction()).isFalse();
        verifyNoInteractions(externo);
        verify(manager).commit(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inicio", "consulta", "commit"})
    void restauraHolderOsivEPreservaFalhaOriginalDaLeitura(String etapa) {
        EntityManager externo = mock(EntityManager.class);
        EntityManagerHolder holder = new EntityManagerHolder(externo);
        IllegalStateException falha = new IllegalStateException("falha sintetica em " + etapa);
        AnuncioRepository repository = mock(AnuncioRepository.class);
        when(repository.findPublicosOrdenados(eq(null), eq(null), eq(null), any(), anyLong(), any()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isNull();
                    if (etapa.equals("consulta")) throw falha;
                    return Page.empty(PageRequest.of(0, 20));
                });
        PlatformTransactionManager manager = transacoesSomenteLeitura();
        if (etapa.equals("inicio")) {
            leiturasEsperadas = 0;
            doThrow(falha).when(manager).getTransaction(any());
        } else if (etapa.equals("commit")) {
            doThrow(falha).when(manager).commit(any());
        }
        ListagemPublicaConsultaService service = serviceParaLeituraPrivada(repository, manager);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);

        assertThatThrownBy(() -> service.listar(null, null, null, 0, 20, "27")).isSameAs(falha);

        assertThat(falha.getSuppressed()).isEmpty();
        assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(holder);
        verifyNoInteractions(externo);
        if (etapa.equals("inicio")) verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"desconhecido", "sincronizado"})
    void rejeitaHolderExternoDesconhecidoOuSincronizadoSemDesvincular(String tipo) {
        leiturasEsperadas = 0;
        EntityManager externo = mock(EntityManager.class);
        EntityManagerHolder holder = new EntityManagerHolder(externo);
        holder.setSynchronizedWithTransaction(true);
        Object recurso = tipo.equals("sincronizado") ? holder : new Object();
        AnuncioRepository repository = mock(AnuncioRepository.class);
        PlatformTransactionManager manager = transacoesSomenteLeitura();
        ListagemPublicaConsultaService service = serviceParaLeituraPrivada(repository, manager);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, recurso);

        assertThatThrownBy(() -> service.listar(null, null, null, 0, 20, "27"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(recurso);
        verifyNoInteractions(manager, repository, externo);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void conflitoDeRestauracaoNaoSobrescreveRecursoNemMascaraFalhaPrincipal(boolean consultaFalha) {
        EntityManager externo = mock(EntityManager.class);
        EntityManagerHolder holder = new EntityManagerHolder(externo);
        Object conflito = new Object();
        IllegalStateException falha = new IllegalStateException("falha sintetica da consulta");
        AnuncioRepository repository = mock(AnuncioRepository.class);
        when(repository.findPublicosOrdenados(eq(null), eq(null), eq(null), any(), anyLong(), any()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isNull();
                    // A deliberately broken unit collaborator simulates cleanup leaving another binding.
                    TransactionSynchronizationManager.bindResource(entityManagerFactory, conflito);
                    if (consultaFalha) throw falha;
                    return Page.empty(PageRequest.of(0, 20));
                });
        ListagemPublicaConsultaService service = serviceParaLeituraPrivada(repository, transacoesSomenteLeitura());
        TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);

        if (consultaFalha) {
            assertThatThrownBy(() -> service.listar(null, null, null, 0, 20, "27")).isSameAs(falha);
            assertThat(falha.getSuppressed()).singleElement()
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        } else {
            assertThatThrownBy(() -> service.listar(null, null, null, 0, 20, "27"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
        assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(conflito);
        verifyNoInteractions(externo);
    }

    @Test
    void guardaDeTransacaoExternaContinuaAntesDeQualquerSuspensaoOuLeitura() {
        leiturasEsperadas = 0;
        EntityManager externo = mock(EntityManager.class);
        EntityManagerHolder holder = new EntityManagerHolder(externo);
        AnuncioRepository repository = mock(AnuncioRepository.class);
        PlatformTransactionManager manager = transacoesSomenteLeitura();
        ListagemPublicaConsultaService service = serviceParaLeituraPrivada(repository, manager);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);
        boolean ativaAntes = TransactionSynchronizationManager.isActualTransactionActive();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.listar(null, null, null, 0, 20, "27"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isSameAs(holder);
            verifyNoInteractions(manager, repository, externo);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(ativaAntes);
        }
    }

    private ListagemPublicaConsultaService serviceParaLeituraPrivada(
            AnuncioRepository repository, PlatformTransactionManager manager) {
        PremiumPublicoMapper premium = mock(PremiumPublicoMapper.class);
        when(premium.flagsPorAnuncios(List.of())).thenReturn(Map.of());
        OrdemSeedPublicaService seed = mock(OrdemSeedPublicaService.class);
        when(seed.resolver("27")).thenReturn(27L);
        return new ListagemPublicaConsultaService(
                mock(EstadoRepository.class), mock(CidadeRepository.class), mock(BairroRepository.class),
                mock(AnuncioLocalizacaoRepository.class), repository,
                new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()), mock(AnuncioPublicoConsultaService.class),
                mock(SeoPublicoConsultaService.class), premium, mock(PoliticaContatoPublicoService.class),
                seed, visualizacoesCanonicas(), mock(IdadeAnunciantePublicaService.class), manager, entityManager);
    }

    private static VisualizacaoTotalCanonicaService visualizacoesCanonicas() {
        VisualizacaoTotalCanonicaService service = mock(VisualizacaoTotalCanonicaService.class);
        when(service.calcularEmLote(any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(0);
            Map<UUID, VisualizacoesCanonicasDto> totais = new LinkedHashMap<>();
            ids.forEach(id -> totais.put(id, VisualizacoesCanonicasDto.total(0)));
            return totais;
        });
        return service;
    }

    /** Unit-test transaction collaborator; real JDBC boundaries are covered by the HTTP/PG17 suite. */
    private PlatformTransactionManager transacoesSomenteLeitura() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> {
            TransactionDefinition definition = call.getArgument(0);
            assertThat(definition.isReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getResource(entityManagerFactory)).isNull();
            return new SimpleTransactionStatus();
        });
        return manager;
    }
}
