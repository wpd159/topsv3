package br.com.topsdojob.v3.importacao.comercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.Decisao;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.LinhaBeneficio;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.PoliticaDuracao;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.PoliticaValor;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfiguracaoComercialFaseTresPolicyTest {

  @Test
  void matrizExplicitaPreservaAutoridadeDaV3ESeparaStories() {
    MatrizMapeamentoComercialImportacao matriz =
        ConfiguracaoComercialFaseTresFixture.matriz();

    var topo = matriz.beneficio("anuncio topo").orElseThrow();
    assertThat(topo.codigoV3()).isEqualTo("ANUNCIO_TOPO");
    assertThat(topo.escopo()).isEqualTo(EscopoBeneficioPremium.ANUNCIO);
    assertThat(topo.politicaDuracao()).isEqualTo(PoliticaDuracao.OPCOES_1_7_14_30);
    assertThat(topo.politicaValor()).isEqualTo(PoliticaValor.CUSTO_CREDITOS_POSITIVO);

    var conta = matriz.beneficio("DESTAQUE_CONTA").orElseThrow();
    assertThat(conta.escopo()).isEqualTo(EscopoBeneficioPremium.USUARIO);
    assertThat(conta.decisao()).isEqualTo(Decisao.PRESERVAR_HISTORICO);

    var stories = matriz.beneficio("STORIES").orElseThrow();
    assertThat(stories.storiesSeparado()).isTrue();
    assertThat(stories.politicaDuracao()).isEqualTo(PoliticaDuracao.FIXA_24_HORAS);
    assertThat(stories.codigoV3()).isNotEqualTo("VIDEO_1");

    assertThat(matriz.duracaoPremiumCanonica(1)).isTrue();
    assertThat(matriz.duracaoPremiumCanonica(7)).isTrue();
    assertThat(matriz.duracaoPremiumCanonica(14)).isTrue();
    assertThat(matriz.duracaoPremiumCanonica(30)).isTrue();
    assertThat(matriz.duracaoPremiumCanonica(3)).isFalse();
    assertThat(matriz.beneficio("SEM_MAPEAMENTO")).isEmpty();
  }

  @Test
  void matrizRecusaCodigoLegadoDuplicado() {
    LinhaBeneficio duplicada = new LinhaBeneficio(
        "ANUNCIO_TOPO",
        "OUTRO_DESTINO",
        EscopoBeneficioPremium.ANUNCIO,
        Decisao.QUARENTENA,
        PoliticaDuracao.NAO_APLICAVEL,
        PoliticaValor.HISTORICO_SEM_EXECUCAO,
        false,
        "Duplicado",
        "Linha duplicada para teste",
        false,
        99);

    assertThatThrownBy(() ->
        MatrizMapeamentoComercialImportacao.comLinhasAdicionais(
            List.of(duplicada),
            List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicado");
  }

  @Test
  void sanitizadorRemoveHtmlExecutavelSemInventarDescricao() {
    SanitizadorDescricaoComercialLegada sanitizador =
        new SanitizadorDescricaoComercialLegada();

    assertThat(sanitizador.sanitizar(
        "<b>Pacote inicial</b><script>segredoNaoReal</script>", 500))
        .isEqualTo("Pacote inicial");
  }
}
