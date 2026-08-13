package br.com.topsdojob.v3.importacao.anuncio;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.AnuncioLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.LocalidadeMapeada;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanejadorAnuncioImportacaoTest {
  private final PlanejadorAnuncioImportacao planejador = new PlanejadorAnuncioImportacao();
  private final LocalidadeMapeada localidade = new LocalidadeMapeada(
      AnunciosFaseUmFixture.ESTADO_ID,
      AnunciosFaseUmFixture.CIDADE_ID,
      AnunciosFaseUmFixture.BAIRRO_ID,
      true);

  @Test
  void publicaSomenteComTodasAsEvidenciasEPreservaVirtualVerdadeiro() {
    AnuncioLegado anuncio = AnunciosFaseUmFixture.anuncioBase("ATIVO", true);
    var plano = planejar(anuncio, true, true);

    assertThat(plano.publicavel()).isTrue();
    assertThat(plano.atendimentoExclusivamenteVirtual()).isTrue();
    assertThat(plano.servicos()).contains("VIDEOCHAMADA");
    assertThat(plano.statusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
  }

  @Test
  void preservaFalseSemIncluirVideochamada() {
    var plano = planejar(AnunciosFaseUmFixture.anuncioBase("PAUSADO", false), true, false);

    assertThat(plano.status()).isEqualTo(StatusAnuncio.PAUSADO);
    assertThat(plano.atendimentoExclusivamenteVirtual()).isFalse();
    assertThat(plano.servicos()).doesNotContain("VIDEOCHAMADA");
  }

  @Test
  void nuloSegueRegraExplicitaConservadoraSemInferencia() {
    var plano = planejar(AnunciosFaseUmFixture.anuncioBase("ATIVO", null), true, true);

    assertThat(plano.status()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
    assertThat(plano.statusModeracao()).isEqualTo(StatusModeracaoAnuncio.PENDENTE);
    assertThat(plano.atendimentoExclusivamenteVirtual()).isFalse();
    assertThat(plano.pendencias()).contains(
        CodigoPendenciaImportacao.ANUNCIO_ATENDIMENTO_VIRTUAL_INDETERMINADO);
  }

  @Test
  void ativoSemMidiaValidaVaiParaRevisao() {
    var plano = planejar(AnunciosFaseUmFixture.anuncioBase("ATIVO", false), true, false);

    assertThat(plano.status()).isEqualTo(StatusAnuncio.PENDENTE_REVISAO);
    assertThat(plano.pendencias()).contains(
        CodigoPendenciaImportacao.ANUNCIO_SEM_MIDIA_VALIDA);
  }

  @Test
  void estadosRestritivosNuncaSaoPromovidos() {
    assertThat(planejar(AnunciosFaseUmFixture.anuncioBase("PAUSADO", false), true, true)
        .status()).isEqualTo(StatusAnuncio.PAUSADO);
    assertThat(planejar(AnunciosFaseUmFixture.anuncioBase("REJEITADO", false), true, true)
        .status()).isEqualTo(StatusAnuncio.REJEITADO);
    assertThat(planejar(AnunciosFaseUmFixture.anuncioBase("REMOVIDO", false), true, true)
        .status()).isEqualTo(StatusAnuncio.REMOVIDO);
    assertThat(planejar(AnunciosFaseUmFixture.anuncioBase("BLOQUEADO", false), true, true)
        .status()).isEqualTo(StatusAnuncio.BLOQUEADO);
  }

  @Test
  void inconsistenciasEstruturaisGeramQuarentena() {
    AnuncioLegado anuncio = AnunciosFaseUmFixture.anuncioBase("ATIVO", false);

    assertThat(planejador.planejar(anuncio, null, false, localidade, List.of()).quarentena())
        .isTrue();
    assertThat(planejador.planejar(
        anuncio, AnunciosFaseUmFixture.PROPRIETARIO_ATIVO_ID, true,
        new LocalidadeMapeada(null, null, null, false), List.of()).quarentena()).isTrue();
  }

  @Test
  void contaInativaImpedeCatalogoMesmoComMidia() {
    var plano = planejar(AnunciosFaseUmFixture.anuncioBase("ATIVO", false), false, true);

    assertThat(plano.status()).isEqualTo(StatusAnuncio.PAUSADO);
    assertThat(plano.pendencias()).contains(
        CodigoPendenciaImportacao.ANUNCIO_PROPRIETARIO_INATIVO);
  }


  @Test
  void moderacaoBloqueadaPreservaEstadoRestritivo() {
    var plano = planejar(
        AnunciosFaseUmFixture.anuncioBaseComModeracao("ATIVO", "BLOQUEADA"), true, true);

    assertThat(plano.status()).isEqualTo(StatusAnuncio.BLOQUEADO);
    assertThat(plano.pendencias()).contains(
        CodigoPendenciaImportacao.ANUNCIO_MODERACAO_NAO_APROVADA);
  }
  private PlanejadorAnuncioImportacao.Plano planejar(
      AnuncioLegado anuncio, boolean proprietarioAtivo, boolean comMidia) {
    return planejador.planejar(
        anuncio,
        AnunciosFaseUmFixture.PROPRIETARIO_ATIVO_ID,
        proprietarioAtivo,
        localidade,
        comMidia ? List.of(AnunciosFaseUmFixture.midiaValida(anuncio.idOrigem())) : List.of());
  }
}
