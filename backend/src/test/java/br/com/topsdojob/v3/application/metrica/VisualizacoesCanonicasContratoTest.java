package br.com.topsdojob.v3.application.metrica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.dto.AnuncioCardPublicoDto;
import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import br.com.topsdojob.v3.persistence.repository.EventoVisualizacaoRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class VisualizacoesCanonicasContratoTest {

  @Test
  void contratosExistentesTransportamAMesmaMetricaCanonica() {
    assertThat(tipoDoComponente(AnuncioCardPublicoDto.class, "visualizacoes"))
        .isEqualTo(VisualizacoesCanonicasDto.class);
    assertThat(tipoDoComponente(AnuncioDetalhePublicoDto.class, "visualizacoes"))
        .isEqualTo(VisualizacoesCanonicasDto.class);
    assertThat(tipoDoComponente(MeuAnuncioDto.class, "visualizacoes"))
        .isEqualTo(VisualizacoesCanonicasDto.class);
  }

  @Test
  void openApiDistingueTotalDisponivelZeroLegitimoEHistoricoPendente() throws Exception {
    String openApi = Files.readString(Path.of(
        "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

    assertThat(openApi)
        .contains("VisualizacoesCanonicas:")
        .contains("enum: [DISPONIVEL, ZERO_LEGITIMO, HISTORICO_PENDENTE]")
        .contains("Total canonico completo. Nulo somente enquanto o historico inicial legado estiver pendente.");
    assertThat(ocorrencias(openApi, "$ref: \"#/components/schemas/VisualizacoesCanonicas\"")).isEqualTo(3);
  }

  @Test
  void historicoPendenteNuncaAceitaTotalParcial() {
    assertThat(VisualizacoesCanonicasDto.historicoPendente().total()).isNull();
    assertThatThrownBy(() -> new VisualizacoesCanonicasDto(
        1L, VisualizacoesCanonicasDto.Situacao.HISTORICO_PENDENTE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VisualizacoesCanonicasDto(
        0L, VisualizacoesCanonicasDto.Situacao.DISPONIVEL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VisualizacoesCanonicasDto(
        1L, VisualizacoesCanonicasDto.Situacao.ZERO_LEGITIMO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void consultaEmLoteAplicaOCorteSemUsarAgregadoDiario() throws Exception {
    Query query = EventoVisualizacaoRepository.class
        .getMethod("countCanonicosByAnuncioIdIn", List.class)
        .getAnnotation(Query.class);

    assertThat(query).isNotNull();
    assertThat(query.nativeQuery()).isTrue();
    assertThat(query.value())
        .contains("e.anuncio_id in (:anuncioIds)")
        .contains("e.criado_em > i.snapshot_corte_em")
        .doesNotContain("agregado_visualizacao_diaria");
    assertThat(EventoVisualizacaoRepository.ContagemCanonicaProjection.class
        .getMethod("getAnuncioId").getReturnType()).isEqualTo(UUID.class);
  }

  private Class<?> tipoDoComponente(Class<?> record, String nome) {
    return Arrays.stream(record.getRecordComponents())
        .filter(componente -> componente.getName().equals(nome))
        .findFirst()
        .orElseThrow()
        .getType();
  }

  private int ocorrencias(String texto, String trecho) {
    return (texto.length() - texto.replace(trecho, "").length()) / trecho.length();
  }
}
