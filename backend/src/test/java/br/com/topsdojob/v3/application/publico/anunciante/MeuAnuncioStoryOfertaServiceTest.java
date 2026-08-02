package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import br.com.topsdojob.v3.application.premium.dto.PremiumOpcaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class MeuAnuncioStoryOfertaServiceTest {

  private MeusAnunciosConsultaService anuncios;
  private MeuAnuncioStoryConsultaService storiesConsulta;
  private CreditoLedgerOperacaoService ledger;
  private PremiumCatalogoService catalogo;
  private BeneficioPremiumRepository beneficios;
  private BeneficioPremiumOpcaoRepository opcoes;
  private AtivacaoBeneficioRepository ativacoes;
  private StoryAnuncioRepository stories;
  private MeuAnuncioStoryOfertaService service;
  private UUID anuncioId;
  private UUID usuarioId;
  private Authentication authentication;

  @BeforeEach
  void setUp() {
    anuncios = mock(MeusAnunciosConsultaService.class);
    storiesConsulta = mock(MeuAnuncioStoryConsultaService.class);
    ledger = mock(CreditoLedgerOperacaoService.class);
    catalogo = mock(PremiumCatalogoService.class);
    beneficios = mock(BeneficioPremiumRepository.class);
    opcoes = mock(BeneficioPremiumOpcaoRepository.class);
    ativacoes = mock(AtivacaoBeneficioRepository.class);
    stories = mock(StoryAnuncioRepository.class);
    authentication = mock(Authentication.class);
    anuncioId = UUID.randomUUID();
    usuarioId = UUID.randomUUID();
    AnuncioEntity anuncio = mock(AnuncioEntity.class);
    UsuarioEntity usuario = mock(UsuarioEntity.class);
    when(anuncio.getId()).thenReturn(anuncioId);
    when(usuario.getId()).thenReturn(usuarioId);
    when(anuncios.anuncioDoUsuario("qa-story", authentication)).thenReturn(anuncio);
    when(anuncios.usuarioAutenticado(authentication)).thenReturn(usuario);
    when(storiesConsulta.consultarAtivos(any())).thenReturn(Map.of());
    when(ledger.consultarSaldo(usuarioId)).thenReturn(8);
    service = new MeuAnuncioStoryOfertaService(
        anuncios, storiesConsulta, ledger, catalogo, beneficios, opcoes, ativacoes, stories);
  }

  @Test
  void direitoAdquiridoPulaCatalogoMesmoQuandoProdutoFoiDesativado() {
    BeneficioPremiumEntity beneficio = mock(BeneficioPremiumEntity.class);
    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    UUID beneficioId = UUID.randomUUID();
    UUID opcaoId = UUID.randomUUID();
    when(beneficio.getId()).thenReturn(beneficioId);
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(beneficio));
    when(ativacao.getId()).thenReturn(UUID.randomUUID());
    when(ativacao.getUsuarioId()).thenReturn(usuarioId);
    when(ativacao.getBeneficioId()).thenReturn(beneficioId);
    when(ativacao.getOpcaoId()).thenReturn(opcaoId);
    when(ativacao.getStatus()).thenReturn(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    when(ativacao.getCustoCreditosSnapshot()).thenReturn(5);
    when(ativacoes.findByAnuncioId(anuncioId)).thenReturn(List.of(ativacao));
    when(stories.findByAnuncioIds(List.of(anuncioId))).thenReturn(List.of());
    BeneficioPremiumOpcaoEntity opcao = mock(BeneficioPremiumOpcaoEntity.class);
    when(opcao.getDuracaoDias()).thenReturn(1);
    when(opcoes.findById(opcaoId)).thenReturn(Optional.of(opcao));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("DIREITO_DISPONIVEL");
    assertThat(resultado.direitoDisponivel().status()).isEqualTo("AGUARDANDO_MODERACAO");
    assertThat(resultado.direitoDisponivel().duracaoDias()).isEqualTo(1);
    assertThat(resultado.saldoCreditos()).isNull();
    verify(ledger, never()).consultarSaldo(any());
    verify(catalogo, never()).catalogoAtivo();
  }

  @Test
  void ativacaoVigenteTemPrioridadeEInformaPeriodoRealRestante() {
    BeneficioPremiumEntity beneficio = mock(BeneficioPremiumEntity.class);
    AtivacaoBeneficioEntity aguardando = mock(AtivacaoBeneficioEntity.class);
    AtivacaoBeneficioEntity ativa = mock(AtivacaoBeneficioEntity.class);
    UUID beneficioId = UUID.randomUUID();
    UUID opcaoId = UUID.randomUUID();
    UUID ativaId = UUID.randomUUID();
    OffsetDateTime inicio = OffsetDateTime.now().minusHours(3);
    OffsetDateTime fim = OffsetDateTime.now().plusHours(5);
    when(beneficio.getId()).thenReturn(beneficioId);
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(beneficio));
    when(aguardando.getId()).thenReturn(UUID.randomUUID());
    when(aguardando.getUsuarioId()).thenReturn(usuarioId);
    when(aguardando.getBeneficioId()).thenReturn(beneficioId);
    when(aguardando.getStatus()).thenReturn(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    when(aguardando.getCriadoEm()).thenReturn(inicio.minusDays(1));
    when(ativa.getId()).thenReturn(ativaId);
    when(ativa.getUsuarioId()).thenReturn(usuarioId);
    when(ativa.getBeneficioId()).thenReturn(beneficioId);
    when(ativa.getOpcaoId()).thenReturn(opcaoId);
    when(ativa.getStatus()).thenReturn(StatusAtivacaoBeneficio.ATIVA);
    when(ativa.getInicioEm()).thenReturn(inicio);
    when(ativa.getFimEm()).thenReturn(fim);
    when(ativa.getCriadoEm()).thenReturn(inicio.minusHours(1));
    when(ativacoes.findByAnuncioId(anuncioId)).thenReturn(List.of(aguardando, ativa));
    when(stories.findByAnuncioIds(List.of(anuncioId))).thenReturn(List.of());
    BeneficioPremiumOpcaoEntity opcao = mock(BeneficioPremiumOpcaoEntity.class);
    when(opcao.getDuracaoDias()).thenReturn(1);
    when(opcoes.findById(opcaoId)).thenReturn(Optional.of(opcao));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.direitoDisponivel().ativacaoId()).isEqualTo(ativaId);
    assertThat(resultado.direitoDisponivel().inicioEm()).isEqualTo(inicio);
    assertThat(resultado.direitoDisponivel().fimEm()).isEqualTo(fim);
    verify(ledger, never()).consultarSaldo(any());
    verify(catalogo, never()).catalogoAtivo();
  }

  @Test
  void ativacaoConsumidaNaoPulaOfertaAdministrativa() {
    BeneficioPremiumEntity beneficio = mock(BeneficioPremiumEntity.class);
    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    StoryAnuncioEntity story = mock(StoryAnuncioEntity.class);
    UUID beneficioId = UUID.randomUUID();
    UUID ativacaoId = UUID.randomUUID();
    UUID opcaoId = UUID.randomUUID();
    when(beneficio.getId()).thenReturn(beneficioId);
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(beneficio));
    when(ativacao.getId()).thenReturn(ativacaoId);
    when(ativacao.getUsuarioId()).thenReturn(usuarioId);
    when(ativacao.getBeneficioId()).thenReturn(beneficioId);
    when(ativacao.getStatus()).thenReturn(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    when(story.getAtivacaoBeneficioId()).thenReturn(ativacaoId);
    when(ativacoes.findByAnuncioId(anuncioId)).thenReturn(List.of(ativacao));
    when(stories.findByAnuncioIds(List.of(anuncioId))).thenReturn(List.of(story));
    when(catalogo.catalogoAtivo()).thenReturn(List.of(new PremiumCatalogoDto(
        UUID.randomUUID(),
        "STORIES",
        "Stories",
        "Publicação temporária",
        "ANUNCIO",
        false,
        true,
        1,
        List.of(new PremiumOpcaoDto(opcaoId, 7, 10, true, 2)))));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("OPCOES_DISPONIVEIS");
    assertThat(resultado.opcoes()).singleElement().satisfies(item -> {
      assertThat(item.opcaoId()).isEqualTo(opcaoId);
      assertThat(item.duracaoDias()).isEqualTo(7);
      assertThat(item.custoCreditos()).isEqualTo(10);
      assertThat(item.saldoAtual()).isEqualTo(8);
      assertThat(item.creditosFaltantes()).isEqualTo(2);
    });
  }

  @Test
  void opcaoAdministrativaAusenteNaoInventaCondicaoComercial() {
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.empty());
    when(catalogo.catalogoAtivo()).thenReturn(List.of());

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("NOVAS_ATIVACOES_INDISPONIVEIS");
    assertThat(resultado.opcoes()).isEmpty();
  }

  @Test
  void opcaoRetornaSaldoProjetadoCalculadoNoBackend() {
    UUID opcaoId = UUID.randomUUID();
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.empty());
    when(catalogo.catalogoAtivo()).thenReturn(List.of(new PremiumCatalogoDto(
        UUID.randomUUID(), "STORIES", "Stories", "Descrição", "ANUNCIO", false, true, 1,
        List.of(new PremiumOpcaoDto(opcaoId, 1, 5, true, 1)))));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.opcoes()).singleElement().satisfies(item -> {
      assertThat(item.saldoAtual()).isEqualTo(8);
      assertThat(item.saldoAposCompra()).isEqualTo(3);
      assertThat(item.creditosFaltantes()).isZero();
    });
  }

  @Test
  void storyAtivoPulaDireitoECatalogo() {
    MeuAnuncioStoryDto storyAtivo = mock(MeuAnuncioStoryDto.class);
    when(storiesConsulta.consultarAtivos(List.of(anuncioId))).thenReturn(Map.of(anuncioId, storyAtivo));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("STORY_ATIVO");
    assertThat(resultado.storyAtivo()).isSameAs(storyAtivo);
    assertThat(resultado.saldoCreditos()).isNull();
    verify(ledger, never()).consultarSaldo(any());
    verify(beneficios, never()).findByCodigo(any());
    verify(catalogo, never()).catalogoAtivo();
  }

  @Test
  void opcaoInativaNaoEOferecida() {
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.empty());
    when(catalogo.catalogoAtivo()).thenReturn(List.of(new PremiumCatalogoDto(
        UUID.randomUUID(), "STORIES", "Stories", "Descrição", "ANUNCIO", false, true, 1,
        List.of(new PremiumOpcaoDto(UUID.randomUUID(), 1, 5, false, 1)))));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("NOVAS_ATIVACOES_INDISPONIVEIS");
    assertThat(resultado.opcoes()).isEmpty();
  }
}
