package br.com.topsdojob.v3.importacao.anuncio;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatrizStatusAnuncioImportacaoTest {
  private final MatrizStatusAnuncioImportacao matriz = new MatrizStatusAnuncioImportacao();

  @Test
  void centralizaTodosOsEstadosLegadosComPrecedenciaConservadora() {
    Map<String, StatusAnuncio> esperados = Map.ofEntries(
        Map.entry("RASCUNHO", StatusAnuncio.RASCUNHO),
        Map.entry("PENDENTE", StatusAnuncio.PENDENTE_REVISAO),
        Map.entry("EM_REVISAO", StatusAnuncio.PENDENTE_REVISAO),
        Map.entry("ATIVO", StatusAnuncio.PUBLICADO),
        Map.entry("APROVADO", StatusAnuncio.PUBLICADO),
        Map.entry("PUBLICADO", StatusAnuncio.PUBLICADO),
        Map.entry("PAUSADO", StatusAnuncio.PAUSADO),
        Map.entry("INATIVO", StatusAnuncio.PAUSADO),
        Map.entry("REJEITADO", StatusAnuncio.REJEITADO),
        Map.entry("BLOQUEADO", StatusAnuncio.BLOQUEADO),
        Map.entry("REMOVIDO", StatusAnuncio.REMOVIDO));

    assertThat(matriz.valores()).hasSize(esperados.size());
    esperados.forEach((origem, destino) -> assertThat(matriz.mapear(origem))
        .get().extracting(MatrizStatusAnuncioImportacao.DestinoStatus::status)
        .isEqualTo(destino));
    assertThat(matriz.mapear("status-desconhecido")).isEmpty();
  }

  @Test
  void somenteEstadosPublicosSaoCandidatosERejeicaoPreservaModeracao() {
    assertThat(matriz.mapear("ATIVO")).get()
        .extracting(MatrizStatusAnuncioImportacao.DestinoStatus::candidatoPublicacao)
        .isEqualTo(true);
    assertThat(matriz.mapear("PAUSADO")).get()
        .extracting(MatrizStatusAnuncioImportacao.DestinoStatus::candidatoPublicacao)
        .isEqualTo(false);
    assertThat(matriz.mapear("REJEITADO")).get()
        .extracting(MatrizStatusAnuncioImportacao.DestinoStatus::statusModeracao)
        .isEqualTo(StatusModeracaoAnuncio.REJEITADO);
  }
}
