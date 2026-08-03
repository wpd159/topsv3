package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioStoryDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryConfiguracaoComercialEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryConfiguracaoComercialRepository;
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
  private StoryConfiguracaoComercialRepository configuracoes;
  private BeneficioPremiumRepository beneficios;
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
    configuracoes = mock(StoryConfiguracaoComercialRepository.class);
    beneficios = mock(BeneficioPremiumRepository.class);
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
    when(ativacoes.findByAnuncioId(anuncioId)).thenReturn(List.of());
    when(stories.findByAnuncioIds(List.of(anuncioId))).thenReturn(List.of());
    service = new MeuAnuncioStoryOfertaService(
        anuncios, storiesConsulta, ledger, configuracoes, beneficios, ativacoes, stories);
  }

  @Test
  void storyAtivoTemPrioridadeSemConsultarPrecoOuSaldo() {
    MeuAnuncioStoryDto storyAtivo = mock(MeuAnuncioStoryDto.class);
    when(storiesConsulta.consultarAtivos(List.of(anuncioId))).thenReturn(Map.of(anuncioId, storyAtivo));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("STORY_ATIVO");
    assertThat(resultado.storyAtivo()).isSameAs(storyAtivo);
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    assertThat(resultado.saldoAtual()).isNull();
    verify(configuracoes, never()).findById(any());
    verify(ledger, never()).consultarSaldo(any());
  }

  @Test
  void direitoAdquiridoTemPrioridadeMesmoDepoisDaDesativacao() {
    BeneficioPremiumEntity beneficio = mock(BeneficioPremiumEntity.class);
    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    UUID beneficioId = UUID.randomUUID();
    UUID ativacaoId = UUID.randomUUID();
    when(beneficio.getId()).thenReturn(beneficioId);
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(beneficio));
    when(ativacao.getId()).thenReturn(ativacaoId);
    when(ativacao.getUsuarioId()).thenReturn(usuarioId);
    when(ativacao.getBeneficioId()).thenReturn(beneficioId);
    when(ativacao.getStatus()).thenReturn(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    when(ativacao.getCustoCreditosSnapshot()).thenReturn(7);
    when(ativacoes.findByAnuncioId(anuncioId)).thenReturn(List.of(ativacao));
    when(configuracoes.findById(StoryConfiguracaoComercialEntity.SINGLETON_ID))
        .thenReturn(Optional.of(configuracao(false, 9)));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("DIREITO_DISPONIVEL");
    assertThat(resultado.direitoDisponivel().ativacaoId()).isEqualTo(ativacaoId);
    assertThat(resultado.direitoDisponivel().status()).isEqualTo("DISPONIVEL_PARA_PUBLICAR");
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    verify(configuracoes, never()).findById(any());
    verify(ledger, never()).consultarSaldo(any());
  }

  @Test
  void ausenciaDeConfiguracaoNaoInventaOfertaNemSaldoZero() {
    when(configuracoes.findById(StoryConfiguracaoComercialEntity.SINGLETON_ID))
        .thenReturn(Optional.empty());

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("NOVAS_ATIVACOES_INDISPONIVEIS");
    assertThat(resultado.configurada()).isFalse();
    assertThat(resultado.ativo()).isFalse();
    assertThat(resultado.custoCreditos()).isNull();
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    verify(ledger, never()).consultarSaldo(any());
  }

  @Test
  void configuracaoInativaRetornaIndisponibilidadeSemConsultarSaldo() {
    when(configuracoes.findById(StoryConfiguracaoComercialEntity.SINGLETON_ID))
        .thenReturn(Optional.of(configuracao(false, 9)));

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("NOVAS_ATIVACOES_INDISPONIVEIS");
    assertThat(resultado.configurada()).isTrue();
    assertThat(resultado.ativo()).isFalse();
    verify(ledger, never()).consultarSaldo(any());
  }

  @Test
  void configuracaoAtivaRetornaOfertaUnicaComDuracaoFixaESaldosCalculados() {
    when(configuracoes.findById(StoryConfiguracaoComercialEntity.SINGLETON_ID))
        .thenReturn(Optional.of(configuracao(true, 11)));
    when(ledger.consultarSaldo(usuarioId)).thenReturn(8);

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("OFERTA_DISPONIVEL");
    assertThat(resultado.ativo()).isTrue();
    assertThat(resultado.duracaoHoras()).isEqualTo(24);
    assertThat(resultado.custoCreditos()).isEqualTo(11);
    assertThat(resultado.saldoAtual()).isEqualTo(8);
    assertThat(resultado.saldoProjetado()).isZero();
    assertThat(resultado.deficit()).isEqualTo(3);
  }

  @Test
  void custoZeroExplicitamenteConfiguradoNaoGeraDeficit() {
    when(configuracoes.findById(StoryConfiguracaoComercialEntity.SINGLETON_ID))
        .thenReturn(Optional.of(configuracao(true, 0)));
    when(ledger.consultarSaldo(usuarioId)).thenReturn(8);

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.custoCreditos()).isZero();
    assertThat(resultado.saldoAtual()).isEqualTo(8);
    assertThat(resultado.saldoProjetado()).isEqualTo(8);
    assertThat(resultado.deficit()).isZero();
  }

  @Test
  void direitoJaConsumidoNaoOcultaNovaOferta() {
    BeneficioPremiumEntity beneficio = mock(BeneficioPremiumEntity.class);
    AtivacaoBeneficioEntity ativacao = mock(AtivacaoBeneficioEntity.class);
    StoryAnuncioEntity story = mock(StoryAnuncioEntity.class);
    UUID beneficioId = UUID.randomUUID();
    UUID ativacaoId = UUID.randomUUID();
    when(beneficio.getId()).thenReturn(beneficioId);
    when(beneficios.findByCodigo("STORIES")).thenReturn(Optional.of(beneficio));
    when(ativacao.getId()).thenReturn(ativacaoId);
    when(ativacao.getUsuarioId()).thenReturn(usuarioId);
    when(ativacao.getBeneficioId()).thenReturn(beneficioId);
    when(ativacao.getStatus()).thenReturn(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
    when(ativacoes.findByAnuncioId(anuncioId)).thenReturn(List.of(ativacao));
    when(story.getAtivacaoBeneficioId()).thenReturn(ativacaoId);
    when(stories.findByAnuncioIds(List.of(anuncioId))).thenReturn(List.of(story));
    when(configuracoes.findById(StoryConfiguracaoComercialEntity.SINGLETON_ID))
        .thenReturn(Optional.of(configuracao(true, 3)));
    when(ledger.consultarSaldo(usuarioId)).thenReturn(8);

    var resultado = service.consultar("qa-story", authentication);

    assertThat(resultado.estado()).isEqualTo("OFERTA_DISPONIVEL");
    assertThat(resultado.custoCreditos()).isEqualTo(3);
  }

  private StoryConfiguracaoComercialEntity configuracao(boolean ativa, int custo) {
    return StoryConfiguracaoComercialEntity.criar(
        ativa, custo, UUID.randomUUID(), OffsetDateTime.parse("2026-08-03T12:00:00Z"));
  }
}
