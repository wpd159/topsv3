package br.com.topsdojob.v3.application.admin.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.Filtros;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.Ordenacao;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.RelatorioRequest;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.Situacao;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeConsulta.Tipo;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminArquivoPublicidadeConsultaTest {
  private final OffsetDateTime inicio = OffsetDateTime.parse("2026-09-01T00:00:00-03:00");
  private final OffsetDateTime fim = inicio.plusDays(1);

  @Test
  void buscaParametrosLiteralmenteEmTodasVersoesSemConsultarCadastroAtual() {
    UUID anuncio = UUID.randomUUID();
    UUID anunciante = UUID.randomUUID();
    UUID selecionado = UUID.randomUUID();
    String termo = "%' OR 1=1 --";
    Filtros filtros = new Filtros(termo, anuncio, anunciante, "anuncio_topo",
        Situacao.ENCERRADA, inicio, fim, Ordenacao.ANTIGOS);
    for (Tipo tipo : Tipo.values()) {
      var sql = AdminArquivoPublicidadeConsulta.consulta(tipo, filtros, List.of(selecionado), fim);
      assertThat(sql.fromWhere()).doesNotContain(termo, "join usuario", "join anuncio ", "like ?");
      assertThat(sql.fromWhere()).contains("busca.veiculacao_id = v.id", "busca.conteudo_json ->> 'titulo'",
          "busca.conteudo_json ->> 'slug'", "busca.conteudo_json ->> 'nomePublico'", "unaccent(?)");
      assertThat(sql.argumentos()).contains(termo.toLowerCase(), anuncio, anunciante,
          "ANUNCIO_TOPO", inicio, fim, selecionado);
      assertThat(sql.fromWhere()).doesNotContain("contratante_json", "->> 'cpf'", "->> 'email'", "->> 'telefone'");
      assertThat(sql.comPagina(20, 40)).endsWith(20, 40L);
      assertThat(AdminArquivoPublicidadeConsulta.ordem(filtros))
          .isEqualTo(" order by v.inicio_em asc, v.id asc ");
    }
  }

  @Test
  void periodoUsaSobreposicaoComFimExclusivoEStatusUsaMesmoInstante() {
    var filtros = new Filtros(null, null, null, null, Situacao.EM_VEICULACAO,
        inicio, fim, null);
    var sql = AdminArquivoPublicidadeConsulta.consulta(Tipo.ANUNCIO, filtros, List.of(), fim);
    assertThat(sql.fromWhere()).contains("v.fim_em > ?", "v.inicio_em < ?");
    assertThat(sql.argumentos()).containsExactly(fim, inicio, fim);
    assertThat(AdminArquivoPublicidadeConsulta.ordem(filtros))
        .isEqualTo(" order by v.inicio_em desc, v.id desc ");
    assertThatThrownBy(() -> new Filtros(null, null, null, null, null, fim, inicio, null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> new Filtros(null, null, null, null, null, fim, fim, null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void parametrosInvalidosNaoViraramBuscaSemFiltro() {
    assertThatThrownBy(() -> new Filtros("x".repeat(201), null, null, null, null, null, null, null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> new Filtros("nome\ncontrole", null, null, null, null, null, null, null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> new Filtros(null, null, null, "STORIES'", null, null, null, null))
        .isInstanceOf(ResponseStatusException.class);
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> new RelatorioRequest(null, List.of(id, id), null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> new RelatorioRequest(null, Arrays.asList(id, null), null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> new RelatorioRequest(null, null, "Fuso/Inexistente"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void relatorioNaoTruncaEscopoNemAceitaSelecaoAusenteOuDuplicada() {
    assertThatCode(() -> AdminArquivoPublicidadeConsulta.conferirEscopo(100, List.of()))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> AdminArquivoPublicidadeConsulta.conferirEscopo(101, List.of()))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    assertThatThrownBy(() -> AdminArquivoPublicidadeConsulta.conferirEscopo(0, List.of(UUID.randomUUID())))
        .isInstanceOf(ResponseStatusException.class);
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> AdminArquivoPublicidadeConsulta.conferirCompletude(2, List.of(id)))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> AdminArquivoPublicidadeConsulta.conferirCompletude(2, List.of(id, id)))
        .isInstanceOf(ResponseStatusException.class);
    var request = new RelatorioRequest(null, List.of(id), null);
    assertThat(request.fusoHorario()).isEqualTo("America/Sao_Paulo");
    assertThat(AdminArquivoPublicidadeConsulta.escopoSha256(request)).hasSize(64)
        .isEqualTo(AdminArquivoPublicidadeConsulta.escopoSha256(request));
    assertThat(AdminArquivoPublicidadeConsulta.escopoSha256(new RelatorioRequest(
        new Filtros("outra busca", null, null, null, null, null, null, null), List.of(id), null)))
        .isNotEqualTo(AdminArquivoPublicidadeConsulta.escopoSha256(request));
  }

  @Test
  void fimPrevistoNaoEInventadoComoEncerramentoObservado() {
    assertThat(AdminArquivoPublicidadeConsulta.fimTipo(null, null)).isEqualTo("SEM_TERMINO_REGISTRADO");
    assertThat(AdminArquivoPublicidadeConsulta.fimTipo(fim, "LIMITE_AUTOMATICO_BENEFICIO"))
        .isEqualTo("LIMITE_PREVISTO");
    assertThat(AdminArquivoPublicidadeConsulta.fimTipo(fim, "REMOVIDO_PELO_USUARIO"))
        .isEqualTo("ENCERRAMENTO_REGISTRADO");
    assertThat(AdminArquivoPublicidadeConsulta.resumo(Tipo.STORY))
        .contains("arquivo_publicidade_story_hold", "h.encerrado_em is null")
        .doesNotContain("h.revisar_em >");
  }
}
