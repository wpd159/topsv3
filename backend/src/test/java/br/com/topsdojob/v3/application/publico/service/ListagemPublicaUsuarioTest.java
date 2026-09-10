package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosCategoriaPublicaDto;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class ListagemPublicaUsuarioTest {

  private final EntityManager entityManager = mock(EntityManager.class);

  @BeforeEach
  void fabricaDoProxyNaoExigeConexao() {
    when(entityManager.getEntityManagerFactory()).thenReturn(mock(EntityManagerFactory.class));
  }

  @AfterEach
  void limpaContextoDePersistenciaAntesDaLeitura() {
    verify(entityManager).clear();
  }

  @Test
  void contaSemAnuncioPublicoRetornaEstadoVazioSemRegistrarVisualizacao() {
    AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
    AnuncioPublicoConsultaService anuncioConsultaService = mock(AnuncioPublicoConsultaService.class);
    PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
    OrdemSeedPublicaService ordemSeedService = mock(OrdemSeedPublicaService.class);
    VisualizacaoTotalCanonicaService visualizacaoService = mock(VisualizacaoTotalCanonicaService.class);
    PageRequest pagina = PageRequest.of(0, 20);
    long seed = 42L;
    UUID usuarioId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    String username = "wesley";
    AnuncioRepository.UsuarioPublicoProjection usuario =
        mock(AnuncioRepository.UsuarioPublicoProjection.class);

    when(ordemSeedService.resolver("42")).thenReturn(seed);
    when(usuario.getUsuarioId()).thenReturn(usuarioId);
    when(anuncioRepository.findUsuarioPublicoPorUsername(username))
        .thenReturn(Optional.of(usuario));
    when(anuncioRepository.findPublicosOrdenados(
        eq(null), eq(null), eq(usuarioId), any(), eq(seed), eq(pagina)))
        .thenReturn(new PageImpl<>(List.of(), pagina, 0));
    when(premiumMapper.flagsPorAnuncios(List.of())).thenReturn(Map.of());

    ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
        mock(EstadoRepository.class),
        mock(CidadeRepository.class),
        mock(BairroRepository.class),
        localizacaoRepository,
        anuncioRepository,
        new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
        anuncioConsultaService,
        mock(SeoPublicoConsultaService.class),
        premiumMapper,
        mock(PoliticaContatoPublicoService.class),
        ordemSeedService,
        visualizacaoService,
        mock(IdadeAnunciantePublicaService.class),
        transacoesSomenteLeitura(),
        entityManager);

    ListaAnunciosCategoriaPublicaDto resposta = service.listar(
        null, null, username, 0, 20, "42");

    assertThat(resposta.itens()).isEmpty();
    assertThat(resposta.paginacao().totalItens()).isZero();
    verify(anuncioRepository).findPublicosOrdenados(
        eq(null), eq(null), eq(usuarioId), any(), eq(seed), eq(pagina));
    verifyNoInteractions(localizacaoRepository, anuncioConsultaService, visualizacaoService);
  }

  @Test
  void anuncioElegivelNoCatalogoCanonicoApareceNaListagemDaAnuncianteSemNMaisUm() {
    UUID usuarioId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID anuncioId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    UUID estadoId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    UUID cidadeId = UUID.fromString("40000000-0000-0000-0000-000000000001");
    long seed = 42L;
    PageRequest pagina = PageRequest.of(0, 20);

    AnuncioEntity anuncio = entity(AnuncioEntity.class);
    set(anuncio, "id", anuncioId);
    set(anuncio, "usuarioId", usuarioId);
    set(anuncio, "slug", "anuncio-publico-wesley");
    set(anuncio, "titulo", "Anuncio publico Wesley");
    set(anuncio, "descricao", "Descricao publica");
    set(anuncio, "categoria", "ACOMPANHANTE_FEMININA");
    set(anuncio, "status", StatusAnuncio.PUBLICADO);
    set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
    set(anuncio, "publicadoEm", OffsetDateTime.parse("2026-08-01T12:00:00Z"));
    set(anuncio, "locaisAtendimento", java.util.Set.of());
    set(anuncio, "servicos", java.util.Set.of());

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

    AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
    EstadoRepository estadoRepository = mock(EstadoRepository.class);
    CidadeRepository cidadeRepository = mock(CidadeRepository.class);
    BairroRepository bairroRepository = mock(BairroRepository.class);
    AnuncioPublicoConsultaService anuncioConsultaService = mock(AnuncioPublicoConsultaService.class);
    PremiumPublicoMapper premiumMapper = mock(PremiumPublicoMapper.class);
    PoliticaContatoPublicoService contatoService = mock(PoliticaContatoPublicoService.class);
    OrdemSeedPublicaService ordemSeedService = mock(OrdemSeedPublicaService.class);
    VisualizacaoTotalCanonicaService visualizacaoService = mock(VisualizacaoTotalCanonicaService.class);
    IdadeAnunciantePublicaService idadeService = mock(IdadeAnunciantePublicaService.class);
    AnuncioRepository.UsuarioPublicoProjection usuario = mock(AnuncioRepository.UsuarioPublicoProjection.class);
    PremiumPublicoFlagsDto premium = PremiumPublicoFlagsDto.vazio();

    when(ordemSeedService.resolver("42")).thenReturn(seed);
    when(usuario.getUsuarioId()).thenReturn(usuarioId);
    when(anuncioRepository.findUsuarioPublicoPorUsername("wesley")).thenReturn(Optional.of(usuario));
    when(anuncioRepository.findPublicosOrdenados(
        eq(null), eq(null), eq(usuarioId), any(), eq(seed), eq(pagina)))
        .thenReturn(new PageImpl<>(List.of(anuncio), pagina, 1));
    when(localizacaoRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of(localizacao));
    when(estadoRepository.findAllById(List.of(estadoId))).thenReturn(List.of(estado));
    when(cidadeRepository.findAllById(List.of(cidadeId))).thenReturn(List.of(cidade));
    when(bairroRepository.findAllById(List.of())).thenReturn(List.of());
    when(anuncioRepository.findPrimeiraPublicacaoByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of());
    when(premiumMapper.flagsPorAnuncios(List.of(anuncio))).thenReturn(Map.of(anuncioId, premium));
    when(anuncioConsultaService.midiasParaCardsPorAnuncios(List.of(anuncioId), Map.of(anuncioId, premium)))
        .thenReturn(Map.of(anuncioId, List.of()));
    when(visualizacaoService.calcularEmLote(List.of(anuncioId)))
        .thenReturn(Map.of(anuncioId, VisualizacoesCanonicasDto.total(0)));
    when(idadeService.resolverPorAnuncios(List.of(anuncio), Map.of(anuncioId, premium))).thenReturn(Map.of());
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
        ordemSeedService,
        visualizacaoService,
        idadeService,
        transacoesSomenteLeitura(),
        entityManager);

    ListaAnunciosCategoriaPublicaDto resposta =
        service.listar(null, null, "wesley", 0, 20, "42");

    assertThat(resposta.itens()).singleElement().satisfies(item ->
        assertThat(item.slug()).isEqualTo("anuncio-publico-wesley"));
    verify(anuncioRepository).findPublicosOrdenados(
        eq(null), eq(null), eq(usuarioId), any(), eq(seed), eq(pagina));
    verify(localizacaoRepository).findByAnuncioIdIn(List.of(anuncioId));
    verify(anuncioConsultaService).midiasParaCardsPorAnuncios(List.of(anuncioId), Map.of(anuncioId, premium));
    verify(visualizacaoService).calcularEmLote(List.of(anuncioId));
  }

  @Test
  void usernamePublicoEDiretamenteCanonicoEContaInexistenteRetorna404() {
    assertThat(RotaPublicaGuard.username("  Wesley  ")).isEqualTo("wesley");

    AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    OrdemSeedPublicaService ordemSeedService = mock(OrdemSeedPublicaService.class);
    when(ordemSeedService.resolver(null)).thenReturn(11L);
    when(anuncioRepository.findUsuarioPublicoPorUsername("wesley")).thenReturn(Optional.empty());
    ListagemPublicaConsultaService service = new ListagemPublicaConsultaService(
        mock(EstadoRepository.class),
        mock(CidadeRepository.class),
        mock(BairroRepository.class),
        mock(AnuncioLocalizacaoRepository.class),
        anuncioRepository,
        new AnuncioPublicoMapper(new MidiaPublicaSeguraPolicy()),
        mock(AnuncioPublicoConsultaService.class),
        mock(SeoPublicoConsultaService.class),
        mock(PremiumPublicoMapper.class),
        mock(PoliticaContatoPublicoService.class),
        ordemSeedService,
        mock(VisualizacaoTotalCanonicaService.class),
        mock(IdadeAnunciantePublicaService.class),
        transacoesSomenteLeitura(),
        entityManager);

    assertThatThrownBy(() -> service.listar(null, null, "Wesley", 0, 20, null))
        .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  /** Unit-test transaction collaborator; real JDBC boundaries are covered by the HTTP/PG17 suite. */
  private static PlatformTransactionManager transacoesSomenteLeitura() {
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenAnswer(call -> {
      TransactionDefinition definition = call.getArgument(0);
      assertThat(definition.isReadOnly()).isTrue();
      return new SimpleTransactionStatus();
    });
    return manager;
  }
}
