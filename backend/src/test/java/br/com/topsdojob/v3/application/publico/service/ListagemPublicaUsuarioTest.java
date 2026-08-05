package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosUsuarioPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.AnuncioPublicoMapper;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaSeguraPolicy;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ListagemPublicaUsuarioTest {

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
    String username = "e9cc4ee1817a3f9a4c15d5bd2b82bde0";
    AnuncioRepository.UsuarioPublicoProjection usuario =
        mock(AnuncioRepository.UsuarioPublicoProjection.class);

    when(ordemSeedService.resolver("42")).thenReturn(seed);
    when(usuario.getUsuarioId()).thenReturn(usuarioId);
    when(usuario.getDisplayUsername()).thenReturn("qa_publica");
    when(anuncioRepository.findUsuarioPublicoPorUsername(username))
        .thenReturn(Optional.of(usuario));
    when(anuncioRepository.findPublicosPorUsuarioOrdenados(
        eq(usuarioId), any(), eq(seed), eq(pagina)))
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
        mock(IdadeAnunciantePublicaService.class));

    ListaAnunciosUsuarioPublicaDto resposta = service.porUsuario(
        username, 0, 20, "42");

    assertThat(resposta.username()).isEqualTo(username);
    assertThat(resposta.displayUsername()).isEqualTo("qa_publica");
    assertThat(resposta.itens()).isEmpty();
    assertThat(resposta.paginacao().totalItens()).isZero();
    verify(anuncioRepository).findPublicosPorUsuarioOrdenados(
        eq(usuarioId), any(), eq(seed), eq(pagina));
    verifyNoInteractions(localizacaoRepository, anuncioConsultaService, visualizacaoService);
  }
}
